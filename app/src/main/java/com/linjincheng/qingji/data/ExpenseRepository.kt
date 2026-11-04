package com.linjincheng.qingji.data

import android.content.ContentValues
import android.database.Cursor
import com.linjincheng.qingji.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository interface for expense operations. The SQLite implementation dispatches database work to IO.
 */
interface ExpenseRepository {
    suspend fun insertExpense(entry: ExpenseEntry): Long
    suspend fun updateExpense(entry: ExpenseEntry): Boolean
    suspend fun deleteExpense(id: Long): Boolean
    suspend fun getExpenseById(id: Long): ExpenseEntry?

    /** 全部流水：按消费日期降序、同日按创建时间降序；仅用于跨月搜索与筛选。 */
    suspend fun getAllExpenses(): List<ExpenseEntry>

    /** 排序：expense_date 降序，同日 createdAt 降序。 */
    suspend fun getExpensesByMonth(year: Int, month: Int): List<ExpenseEntry>

    /** 单位：分。 */
    suspend fun getMonthlyTotal(year: Int, month: Int): Long

    /** 只含金额 > 0 的分类。 */
    suspend fun getMonthlyCategoryTotals(year: Int, month: Int): Map<ExpenseCategory, Long>
}

/**
 * SQLite 实现。
 * 月度查询边界 [当月首日, 次月首日)，由 util.DateUtils 用 java.time 推导（跨年正确）；
 * 所有查询一律参数绑定（selectionArgs / ContentValues），无任何字符串拼接 SQL。
 */
class ExpenseRepositoryImpl(private val databaseHelper: ExpenseDatabaseHelper) : ExpenseRepository {

    override suspend fun insertExpense(entry: ExpenseEntry): Long = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        db.insert(TABLE_EXPENSES, null, entryToValues(entry, includeCreatedAt = true))
    }

    override suspend fun updateExpense(entry: ExpenseEntry): Boolean = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        // 编辑不得改变 created_at：更新的 ContentValues 不包含该列
        val rows = db.update(
            TABLE_EXPENSES,
            entryToValues(entry, includeCreatedAt = false),
            "$COL_ID = ?",
            arrayOf(entry.id.toString()),
        )
        rows > 0
    }

    override suspend fun deleteExpense(id: Long): Boolean = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        db.delete(TABLE_EXPENSES, "$COL_ID = ?", arrayOf(id.toString())) > 0
    }

    override suspend fun getExpenseById(id: Long): ExpenseEntry? = withContext(Dispatchers.IO) {
        val db = databaseHelper.readableDatabase
        db.query(
            TABLE_EXPENSES,
            ALL_COLUMNS,
            "$COL_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toEntry() else null
        }
    }

    override suspend fun getAllExpenses(): List<ExpenseEntry> = withContext(Dispatchers.IO) {
        val db = databaseHelper.readableDatabase
        db.query(
            TABLE_EXPENSES,
            ALL_COLUMNS,
            null,
            null,
            null,
            null,
            "$COL_EXPENSE_DATE DESC, $COL_CREATED_AT DESC",
        ).use { cursor ->
            val result = ArrayList<ExpenseEntry>(cursor.count)
            while (cursor.moveToNext()) result.add(cursor.toEntry())
            result
        }
    }

    override suspend fun getExpensesByMonth(year: Int, month: Int): List<ExpenseEntry> {
        require(month in 1..12) { "month 必须在 1..12，实际为 $month" }
        return withContext(Dispatchers.IO) {
            val db = databaseHelper.readableDatabase
            val (start, end) = monthBounds(year, month)
            db.query(
                TABLE_EXPENSES,
                ALL_COLUMNS,
                "$COL_EXPENSE_DATE >= ? AND $COL_EXPENSE_DATE < ?", // [当月首日, 次月首日)
                arrayOf(start.toString(), end.toString()),
                null,
                null,
                "$COL_EXPENSE_DATE DESC, $COL_CREATED_AT DESC",
                null,
            ).use { cursor ->
                val result = ArrayList<ExpenseEntry>(cursor.count)
                while (cursor.moveToNext()) result.add(cursor.toEntry())
                result
            }
        }
    }

    override suspend fun getMonthlyTotal(year: Int, month: Int): Long {
        require(month in 1..12) { "month 必须在 1..12，实际为 $month" }
        return withContext(Dispatchers.IO) {
            val db = databaseHelper.readableDatabase
            val (start, end) = monthBounds(year, month)
            db.query(
                TABLE_EXPENSES,
                arrayOf("SUM($COL_AMOUNT_CENTS) AS total"),
                "$COL_EXPENSE_DATE >= ? AND $COL_EXPENSE_DATE < ?",
                arrayOf(start.toString(), end.toString()),
                null,
                null,
                null,
                null,
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
            }
        }
    }

    override suspend fun getMonthlyCategoryTotals(year: Int, month: Int): Map<ExpenseCategory, Long> {
        require(month in 1..12) { "month 必须在 1..12，实际为 $month" }
        return withContext(Dispatchers.IO) {
            val db = databaseHelper.readableDatabase
            val (start, end) = monthBounds(year, month)
            db.query(
                TABLE_EXPENSES,
                arrayOf(COL_CATEGORY, "SUM($COL_AMOUNT_CENTS) AS total"),
                "$COL_EXPENSE_DATE >= ? AND $COL_EXPENSE_DATE < ?",
                arrayOf(start.toString(), end.toString()),
                COL_CATEGORY,
                null,
                null,
                null,
            ).use { cursor ->
                val totals = LinkedHashMap<ExpenseCategory, Long>()
                while (cursor.moveToNext()) {
                    // 只保留金额 > 0 的分类；未知分类代码归入 OTHER，避免统计丢钱
                    val total = if (cursor.isNull(1)) 0L else cursor.getLong(1)
                    if (total > 0) {
                        val category = ExpenseCategory.fromCode(cursor.getString(0) ?: "")
                        totals[category] = (totals[category] ?: 0L) + total
                    }
                }
                totals
            }
        }
    }

    /** [当月首日, 次月首日) 两个 epochDay 边界，java.time 推导，跨年由日期运算保证。 */
    private fun monthBounds(year: Int, month: Int): Pair<Long, Long> =
        DateUtils.monthStartEpochDay(year, month) to DateUtils.nextMonthStartEpochDay(year, month)

    /** includeCreatedAt=false 用于 UPDATE：不触碰 created_at。 */
    private fun entryToValues(entry: ExpenseEntry, includeCreatedAt: Boolean): ContentValues =
        ContentValues().apply {
            put(COL_AMOUNT_CENTS, entry.amountCents)
            put(COL_CATEGORY, entry.category.code)
            put(COL_NOTE, entry.note)
            put(COL_EXPENSE_DATE, entry.expenseDate)
            if (includeCreatedAt) put(COL_CREATED_AT, entry.createdAt)
            put(COL_UPDATED_AT, entry.updatedAt)
        }

    private fun Cursor.toEntry(): ExpenseEntry = ExpenseEntry(
        id = getLong(getColumnIndexOrThrow(COL_ID)),
        amountCents = getLong(getColumnIndexOrThrow(COL_AMOUNT_CENTS)),
        category = ExpenseCategory.fromCode(getString(getColumnIndexOrThrow(COL_CATEGORY)) ?: ""),
        note = getString(getColumnIndexOrThrow(COL_NOTE)) ?: "",
        expenseDate = getLong(getColumnIndexOrThrow(COL_EXPENSE_DATE)),
        createdAt = getLong(getColumnIndexOrThrow(COL_CREATED_AT)),
        updatedAt = getLong(getColumnIndexOrThrow(COL_UPDATED_AT)),
    )

    companion object {
        private const val TABLE_EXPENSES = "expenses"
        private const val COL_ID = "id"
        private const val COL_AMOUNT_CENTS = "amount_cents"
        private const val COL_CATEGORY = "category"
        private const val COL_NOTE = "note"
        private const val COL_EXPENSE_DATE = "expense_date"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_UPDATED_AT = "updated_at"
        private val ALL_COLUMNS = arrayOf(
            COL_ID,
            COL_AMOUNT_CENTS,
            COL_CATEGORY,
            COL_NOTE,
            COL_EXPENSE_DATE,
            COL_CREATED_AT,
            COL_UPDATED_AT,
        )
    }
}
