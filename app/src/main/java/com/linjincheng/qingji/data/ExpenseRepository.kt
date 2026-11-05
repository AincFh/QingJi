package com.linjincheng.qingji.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.linjincheng.qingji.util.DateUtils
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Repository boundary for local expense, budget, calendar and backup operations. */
interface ExpenseRepository {
    suspend fun insertExpense(entry: ExpenseEntry): Long
    suspend fun updateExpense(entry: ExpenseEntry): Boolean
    suspend fun deleteExpense(id: Long): Boolean
    suspend fun getExpenseById(id: Long): ExpenseEntry?
    suspend fun getAllExpenses(): List<ExpenseEntry>
    suspend fun getExpensesByMonth(year: Int, month: Int): List<ExpenseEntry>
    suspend fun getMonthlyTotal(year: Int, month: Int): Long
    suspend fun getMonthlyCategoryTotals(year: Int, month: Int): Map<ExpenseCategory, Long>
    suspend fun getMonthlyBudget(year: Int, month: Int): MonthlyBudget?
    suspend fun getAllMonthlyBudgets(): List<MonthlyBudget>
    suspend fun saveMonthlyBudget(budget: MonthlyBudget): Boolean
    suspend fun deleteMonthlyBudget(year: Int, month: Int): Boolean
    suspend fun getDailyExpenseAggregates(startInclusive: Long, endExclusive: Long): List<DailyExpenseAggregate>

    /** Applies a validated, user-confirmed merge atomically. Expense identity is [ExpenseEntry.backupId]. */
    suspend fun applyBackupMerge(
        expenses: List<ExpenseEntry>,
        budgets: List<MonthlyBudget>,
    ): BackupApplyResult
}

/**
 * SQLite implementation. Date ranges use half-open epoch-day boundaries; all variable SQL input is bound.
 */
class ExpenseRepositoryImpl(private val databaseHelper: ExpenseDatabaseHelper) : ExpenseRepository {

    override suspend fun insertExpense(entry: ExpenseEntry): Long = withContext(Dispatchers.IO) {
        validateExpense(entry)
        val db = databaseHelper.writableDatabase
        db.insertOrThrow(TABLE_EXPENSES, null, entryToValues(entry, includeCreatedAt = true, includeBackupId = true))
    }

    override suspend fun updateExpense(entry: ExpenseEntry): Boolean = withContext(Dispatchers.IO) {
        validateExpense(entry)
        val db = databaseHelper.writableDatabase
        // Editing a row never changes its local id, original creation time or backup identity.
        db.update(
            TABLE_EXPENSES,
            entryToValues(entry, includeCreatedAt = false, includeBackupId = false),
            "$COL_ID = ?",
            arrayOf(entry.id.toString()),
        ) > 0
    }

    override suspend fun deleteExpense(id: Long): Boolean = withContext(Dispatchers.IO) {
        val db = databaseHelper.writableDatabase
        db.delete(TABLE_EXPENSES, "$COL_ID = ?", arrayOf(id.toString())) > 0
    }

    override suspend fun getExpenseById(id: Long): ExpenseEntry? = withContext(Dispatchers.IO) {
        databaseHelper.readableDatabase.query(
            TABLE_EXPENSES,
            ALL_COLUMNS,
            "$COL_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toEntry() else null }
    }

    override suspend fun getAllExpenses(): List<ExpenseEntry> = withContext(Dispatchers.IO) {
        databaseHelper.readableDatabase.query(
            TABLE_EXPENSES,
            ALL_COLUMNS,
            null,
            null,
            null,
            null,
            "$COL_EXPENSE_DATE DESC, $COL_CREATED_AT DESC",
        ).use { cursor -> cursor.toEntryList() }
    }

    override suspend fun getExpensesByMonth(year: Int, month: Int): List<ExpenseEntry> {
        requireValidMonth(year, month)
        return withContext(Dispatchers.IO) {
            val (start, end) = monthBounds(year, month)
            databaseHelper.readableDatabase.query(
                TABLE_EXPENSES,
                ALL_COLUMNS,
                "$COL_EXPENSE_DATE >= ? AND $COL_EXPENSE_DATE < ?",
                arrayOf(start.toString(), end.toString()),
                null,
                null,
                "$COL_EXPENSE_DATE DESC, $COL_CREATED_AT DESC",
            ).use { cursor -> cursor.toEntryList() }
        }
    }

    override suspend fun getMonthlyTotal(year: Int, month: Int): Long {
        requireValidMonth(year, month)
        return withContext(Dispatchers.IO) {
            val (start, end) = monthBounds(year, month)
            databaseHelper.readableDatabase.query(
                TABLE_EXPENSES,
                arrayOf("SUM($COL_AMOUNT_CENTS) AS total"),
                "$COL_EXPENSE_DATE >= ? AND $COL_EXPENSE_DATE < ?",
                arrayOf(start.toString(), end.toString()),
                null,
                null,
                null,
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
            }
        }
    }

    override suspend fun getMonthlyCategoryTotals(year: Int, month: Int): Map<ExpenseCategory, Long> {
        requireValidMonth(year, month)
        return withContext(Dispatchers.IO) {
            val (start, end) = monthBounds(year, month)
            databaseHelper.readableDatabase.query(
                TABLE_EXPENSES,
                arrayOf(COL_CATEGORY, "SUM($COL_AMOUNT_CENTS) AS total"),
                "$COL_EXPENSE_DATE >= ? AND $COL_EXPENSE_DATE < ?",
                arrayOf(start.toString(), end.toString()),
                COL_CATEGORY,
                null,
                null,
            ).use { cursor ->
                val totals = LinkedHashMap<ExpenseCategory, Long>()
                while (cursor.moveToNext()) {
                    val total = if (cursor.isNull(1)) 0L else cursor.getLong(1)
                    if (total > 0L) {
                        val category = ExpenseCategory.fromCode(cursor.getString(0) ?: "")
                        totals[category] = (totals[category] ?: 0L) + total
                    }
                }
                totals
            }
        }
    }

    override suspend fun getMonthlyBudget(year: Int, month: Int): MonthlyBudget? {
        requireValidMonth(year, month)
        return withContext(Dispatchers.IO) {
            databaseHelper.readableDatabase.query(
                TABLE_MONTHLY_BUDGETS,
                BUDGET_COLUMNS,
                "$COL_YEAR = ? AND $COL_MONTH = ?",
                arrayOf(year.toString(), month.toString()),
                null,
                null,
                null,
                "1",
            ).use { cursor -> if (cursor.moveToFirst()) cursor.toBudget() else null }
        }
    }

    override suspend fun getAllMonthlyBudgets(): List<MonthlyBudget> = withContext(Dispatchers.IO) {
        databaseHelper.readableDatabase.query(
            TABLE_MONTHLY_BUDGETS,
            BUDGET_COLUMNS,
            null,
            null,
            null,
            null,
            "$COL_YEAR DESC, $COL_MONTH DESC",
        ).use { cursor -> cursor.toBudgetList() }
    }

    override suspend fun saveMonthlyBudget(budget: MonthlyBudget): Boolean = withContext(Dispatchers.IO) {
        ExpenseRepositoryValidation.validateBudget(budget)
        val db = databaseHelper.writableDatabase
        db.beginTransaction()
        try {
            val existingCreatedAt = db.query(
                TABLE_MONTHLY_BUDGETS,
                arrayOf(COL_CREATED_AT),
                "$COL_YEAR = ? AND $COL_MONTH = ?",
                arrayOf(budget.year.toString(), budget.month.toString()),
                null,
                null,
                null,
                "1",
            ).use { if (it.moveToFirst()) it.getLong(0) else null }
            val values = budgetToValues(budget.copy(createdAt = existingCreatedAt ?: budget.createdAt))
            val rows = if (existingCreatedAt == null) {
                (db.insert(TABLE_MONTHLY_BUDGETS, null, values) >= 0L).let { if (it) 1 else 0 }
            } else {
                db.update(
                    TABLE_MONTHLY_BUDGETS,
                    values,
                    "$COL_YEAR = ? AND $COL_MONTH = ?",
                    arrayOf(budget.year.toString(), budget.month.toString()),
                )
            }
            check(rows > 0) { "保存月预算失败" }
            db.setTransactionSuccessful()
            true
        } finally {
            db.endTransaction()
        }
    }

    override suspend fun deleteMonthlyBudget(year: Int, month: Int): Boolean {
        requireValidMonth(year, month)
        return withContext(Dispatchers.IO) {
            databaseHelper.writableDatabase.delete(
                TABLE_MONTHLY_BUDGETS,
                "$COL_YEAR = ? AND $COL_MONTH = ?",
                arrayOf(year.toString(), month.toString()),
            ) > 0
        }
    }

    override suspend fun getDailyExpenseAggregates(
        startInclusive: Long,
        endExclusive: Long,
    ): List<DailyExpenseAggregate> {
        require(startInclusive < endExclusive) { "日期范围必须非空且使用 [start,end)" }
        return withContext(Dispatchers.IO) {
            databaseHelper.readableDatabase.query(
                TABLE_EXPENSES,
                arrayOf(
                    COL_EXPENSE_DATE,
                    "SUM($COL_AMOUNT_CENTS) AS total",
                    "COUNT(*) AS transaction_count",
                ),
                "$COL_EXPENSE_DATE >= ? AND $COL_EXPENSE_DATE < ?",
                arrayOf(startInclusive.toString(), endExclusive.toString()),
                COL_EXPENSE_DATE,
                null,
                "$COL_EXPENSE_DATE ASC",
            ).use { cursor ->
                buildList(cursor.count) {
                    while (cursor.moveToNext()) {
                        add(
                            DailyExpenseAggregate(
                                epochDay = cursor.getLong(0),
                                totalCents = cursor.getLong(1),
                                transactionCount = cursor.getInt(2),
                            ),
                        )
                    }
                }
            }
        }
    }

    override suspend fun applyBackupMerge(
        expenses: List<ExpenseEntry>,
        budgets: List<MonthlyBudget>,
    ): BackupApplyResult = withContext(Dispatchers.IO) {
        validateBackupPayload(expenses, budgets)
        val db = databaseHelper.writableDatabase
        var insertedExpenses = 0
        var skippedExpenses = 0
        var upsertedBudgets = 0
        db.beginTransaction()
        try {
            expenses.forEach { entry ->
                val backupId = canonicalBackupId(entry.backupId)
                val exists = db.query(
                    TABLE_EXPENSES,
                    arrayOf(COL_ID),
                    "$COL_BACKUP_ID = ?",
                    arrayOf(backupId),
                    null,
                    null,
                    null,
                    "1",
                ).use { it.moveToFirst() }
                if (exists) {
                    skippedExpenses++
                } else {
                    val added = db.insertOrThrow(
                        TABLE_EXPENSES,
                        null,
                        entryToValues(entry.copy(backupId = backupId), includeCreatedAt = true, includeBackupId = true),
                    )
                    check(added > 0L) { "导入流水失败" }
                    insertedExpenses++
                }
            }
            budgets.forEach { budget ->
                val replaced = db.insertWithOnConflict(
                    TABLE_MONTHLY_BUDGETS,
                    null,
                    budgetToValues(budget),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                check(replaced > 0L) { "导入预算失败" }
                upsertedBudgets++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        BackupApplyResult(insertedExpenses, skippedExpenses, upsertedBudgets)
    }

    private fun validateBackupPayload(expenses: List<ExpenseEntry>, budgets: List<MonthlyBudget>) {
        expenses.forEach(::validateExpense)
        budgets.forEach(ExpenseRepositoryValidation::validateBudget)
        require(expenses.map { canonicalBackupId(it.backupId) }.distinct().size == expenses.size) {
            "备份内存在重复流水身份"
        }
        require(budgets.map { it.year to it.month }.distinct().size == budgets.size) {
            "备份内存在重复预算月份"
        }
    }

    private fun validateExpense(entry: ExpenseEntry) {
        ExpenseRepositoryValidation.validateExpense(entry)
        canonicalBackupId(entry.backupId)
    }

    private fun canonicalBackupId(value: String): String = try {
        UUID.fromString(value).toString()
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("流水备份身份必须为 UUID", error)
    }

    private fun requireValidMonth(year: Int, month: Int) {
        require(year in 1..9999 && month in 1..12) { "月份无效：$year-$month" }
    }

    private fun monthBounds(year: Int, month: Int): Pair<Long, Long> =
        DateUtils.monthStartEpochDay(year, month) to DateUtils.nextMonthStartEpochDay(year, month)

    private fun entryToValues(
        entry: ExpenseEntry,
        includeCreatedAt: Boolean,
        includeBackupId: Boolean,
    ): ContentValues = ContentValues().apply {
        put(COL_AMOUNT_CENTS, entry.amountCents)
        put(COL_CATEGORY, entry.category.code)
        put(COL_NOTE, entry.note)
        put(COL_EXPENSE_DATE, entry.expenseDate)
        if (includeCreatedAt) put(COL_CREATED_AT, entry.createdAt)
        put(COL_UPDATED_AT, entry.updatedAt)
        if (includeBackupId) put(COL_BACKUP_ID, canonicalBackupId(entry.backupId))
    }

    private fun budgetToValues(budget: MonthlyBudget): ContentValues = ContentValues().apply {
        put(COL_YEAR, budget.year)
        put(COL_MONTH, budget.month)
        put(COL_BUDGET_AMOUNT_CENTS, budget.amountCents)
        put(COL_CREATED_AT, budget.createdAt)
        put(COL_UPDATED_AT, budget.updatedAt)
    }

    private fun Cursor.toEntryList(): List<ExpenseEntry> = buildList(count) {
        while (moveToNext()) add(toEntry())
    }

    private fun Cursor.toEntry(): ExpenseEntry = ExpenseEntry(
        id = getLong(getColumnIndexOrThrow(COL_ID)),
        amountCents = getLong(getColumnIndexOrThrow(COL_AMOUNT_CENTS)),
        category = ExpenseCategory.fromCode(getString(getColumnIndexOrThrow(COL_CATEGORY)) ?: ""),
        note = getString(getColumnIndexOrThrow(COL_NOTE)) ?: "",
        expenseDate = getLong(getColumnIndexOrThrow(COL_EXPENSE_DATE)),
        createdAt = getLong(getColumnIndexOrThrow(COL_CREATED_AT)),
        updatedAt = getLong(getColumnIndexOrThrow(COL_UPDATED_AT)),
        backupId = getString(getColumnIndexOrThrow(COL_BACKUP_ID)),
    )

    private fun Cursor.toBudget(): MonthlyBudget = MonthlyBudget(
        year = getInt(getColumnIndexOrThrow(COL_YEAR)),
        month = getInt(getColumnIndexOrThrow(COL_MONTH)),
        amountCents = getLong(getColumnIndexOrThrow(COL_BUDGET_AMOUNT_CENTS)),
        createdAt = getLong(getColumnIndexOrThrow(COL_CREATED_AT)),
        updatedAt = getLong(getColumnIndexOrThrow(COL_UPDATED_AT)),
    )

    private fun Cursor.toBudgetList(): List<MonthlyBudget> = buildList(count) {
        while (moveToNext()) add(toBudget())
    }

    companion object {
        private const val TABLE_EXPENSES = ExpenseDatabaseHelper.TABLE_EXPENSES
        private const val TABLE_MONTHLY_BUDGETS = ExpenseDatabaseHelper.TABLE_MONTHLY_BUDGETS
        private const val COL_ID = ExpenseDatabaseHelper.COL_ID
        private const val COL_AMOUNT_CENTS = ExpenseDatabaseHelper.COL_AMOUNT_CENTS
        private const val COL_CATEGORY = ExpenseDatabaseHelper.COL_CATEGORY
        private const val COL_NOTE = ExpenseDatabaseHelper.COL_NOTE
        private const val COL_EXPENSE_DATE = ExpenseDatabaseHelper.COL_EXPENSE_DATE
        private const val COL_CREATED_AT = ExpenseDatabaseHelper.COL_CREATED_AT
        private const val COL_UPDATED_AT = ExpenseDatabaseHelper.COL_UPDATED_AT
        private const val COL_BACKUP_ID = ExpenseDatabaseHelper.COL_BACKUP_ID
        private const val COL_YEAR = "year"
        private const val COL_MONTH = "month"
        private const val COL_BUDGET_AMOUNT_CENTS = "amount_cents"
        private val ALL_COLUMNS = arrayOf(
            COL_ID,
            COL_AMOUNT_CENTS,
            COL_CATEGORY,
            COL_NOTE,
            COL_EXPENSE_DATE,
            COL_CREATED_AT,
            COL_UPDATED_AT,
            COL_BACKUP_ID,
        )
        private val BUDGET_COLUMNS = arrayOf(
            COL_YEAR,
            COL_MONTH,
            COL_BUDGET_AMOUNT_CENTS,
            COL_CREATED_AT,
            COL_UPDATED_AT,
        )
    }
}
