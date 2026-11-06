package com.linjincheng.qingji.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** All database files in this suite are uniquely named; the app's qingji.db is never opened. */
@RunWith(AndroidJUnit4::class)
class ExpenseRepositoryDatabaseTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private var helper: ExpenseDatabaseHelper? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "qingji_v6_test_${UUID.randomUUID()}.db"
    }

    @After
    fun tearDown() {
        helper?.close()
        helper = null
        context.deleteDatabase(databaseName)
    }

    @Test
    fun freshDatabaseCreatesV2TablesAndBackupIdentity() {
        helper = ExpenseDatabaseHelper(context, databaseName)
        val db = helper!!.writableDatabase

        assertEquals(2, db.version)
        assertTrue(tableExists(db, ExpenseDatabaseHelper.TABLE_EXPENSES))
        assertTrue(tableExists(db, ExpenseDatabaseHelper.TABLE_MONTHLY_BUDGETS))
        assertTrue(columnExists(db, ExpenseDatabaseHelper.TABLE_EXPENSES, ExpenseDatabaseHelper.COL_BACKUP_ID))
        val backupIdColumn = expenseSchema(db).single { it.name == ExpenseDatabaseHelper.COL_BACKUP_ID }
        assertEquals("TEXT", backupIdColumn.type)
        assertEquals(0, backupIdColumn.notNull)
        assertNull(backupIdColumn.defaultValue)
        assertEquals(0, backupIdColumn.primaryKeyOrder)
        assertTrue(indexExists(db, "idx_expenses_date"))
        assertTrue(indexExists(db, "idx_expenses_backup_id"))
        assertTrue(indexIsUnique(db, "idx_expenses_backup_id"))
    }

    @Test
    fun v1MigrationPreservesEveryExpenseFieldAndBackfillsUniqueIds() {
        createV1Fixture(
            listOf(
                LegacyExpense(41L, 12_345L, "SHOPPING", "旧行 A", 20_000L, 101L, 102L),
                LegacyExpense(73L, 9_876L, "FOOD", "旧行 B", 20_001L, 201L, 202L),
            ),
        )
        helper = ExpenseDatabaseHelper(context, databaseName)
        val db = helper!!.writableDatabase

        assertEquals(2, db.version)
        val freshDatabaseName = "qingji_v6_schema_reference_${UUID.randomUUID()}.db"
        val freshHelper = ExpenseDatabaseHelper(context, freshDatabaseName)
        try {
            val freshDb = freshHelper.writableDatabase
            assertEquals(expenseSchema(freshDb), expenseSchema(db))
            val backupIdColumn = expenseSchema(db).single { it.name == ExpenseDatabaseHelper.COL_BACKUP_ID }
            assertEquals("TEXT", backupIdColumn.type)
            assertEquals(0, backupIdColumn.notNull)
            assertNull(backupIdColumn.defaultValue)
            assertEquals(0, backupIdColumn.primaryKeyOrder)
        } finally {
            freshHelper.close()
            context.deleteDatabase(freshDatabaseName)
        }
        val migrated = db.query(
            ExpenseDatabaseHelper.TABLE_EXPENSES,
            arrayOf("id", "amount_cents", "category", "note", "expense_date", "created_at", "updated_at", "backup_id"),
            null,
            null,
            null,
            null,
            "id ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        LegacyExpense(
                            id = cursor.getLong(0),
                            amountCents = cursor.getLong(1),
                            category = cursor.getString(2),
                            note = cursor.getString(3),
                            expenseDate = cursor.getLong(4),
                            createdAt = cursor.getLong(5),
                            updatedAt = cursor.getLong(6),
                        ) to cursor.getString(7),
                    )
                }
            }
        }

        assertEquals(2, migrated.size)
        assertEquals(
            listOf(
                LegacyExpense(41L, 12_345L, "SHOPPING", "旧行 A", 20_000L, 101L, 102L),
                LegacyExpense(73L, 9_876L, "FOOD", "旧行 B", 20_001L, 201L, 202L),
            ),
            migrated.map { it.first },
        )
        assertTrue(migrated.all { it.second.isNotBlank() })
        assertEquals(migrated.size, migrated.map { it.second }.toSet().size)
        migrated.forEach { (_, backupId) -> assertEquals(backupId, UUID.fromString(backupId).toString()) }
        assertNotEquals(migrated[0].second, migrated[1].second)
        assertTrue(indexExists(db, "idx_expenses_date"))
        assertTrue(indexExists(db, "idx_expenses_backup_id"))
        assertTrue(indexIsUnique(db, "idx_expenses_backup_id"))
        assertTrue(tableExists(db, ExpenseDatabaseHelper.TABLE_MONTHLY_BUDGETS))
    }

    @Test
    fun repositoryWritesUniqueBackupIdsAndEditingKeepsIdentity() = runBlocking {
        helper = ExpenseDatabaseHelper(context, databaseName)
        val repository = ExpenseRepositoryImpl(helper!!)
        val firstEntry = entry(1_200L, 20_000L)
        val secondEntry = entry(2_300L, 20_001L)

        val firstId = repository.insertExpense(firstEntry)
        val secondId = repository.insertExpense(secondEntry)
        val firstStored = repository.getExpenseById(firstId)!!
        val secondStored = repository.getExpenseById(secondId)!!

        assertEquals(firstEntry.backupId, firstStored.backupId)
        assertEquals(UUID.fromString(firstStored.backupId).toString(), firstStored.backupId)
        assertEquals(UUID.fromString(secondStored.backupId).toString(), secondStored.backupId)
        assertNotEquals(firstStored.backupId, secondStored.backupId)

        val editedValues = firstStored.copy(
            amountCents = 1_500L,
            note = "edited",
            updatedAt = 3L,
            backupId = UUID.randomUUID().toString(),
        )
        assertTrue(repository.updateExpense(editedValues))
        val edited = repository.getExpenseById(firstId)!!
        assertEquals(1_500L, edited.amountCents)
        assertEquals("edited", edited.note)
        assertEquals(firstStored.backupId, edited.backupId)
    }

    @Test
    fun failedMigrationRollsBackSchemaAndKeepsVersionOne() {
        createV1Fixture(listOf(LegacyExpense(9L, 321L, "OTHER", "rollback", 10L, 1L, 2L)))
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TRIGGER reject_backup_id BEFORE UPDATE ON expenses BEGIN SELECT RAISE(ABORT, 'forced migration error'); END")
        }

        helper = ExpenseDatabaseHelper(context, databaseName)
        var failed = false
        try {
            helper!!.writableDatabase
        } catch (_: RuntimeException) {
            failed = true
        }
        assertTrue("fixture trigger must reject the migration", failed)
        helper!!.close()
        helper = null

        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            assertEquals(1, db.version)
            assertFalse(columnExists(db, ExpenseDatabaseHelper.TABLE_EXPENSES, ExpenseDatabaseHelper.COL_BACKUP_ID))
            assertEquals(1, db.query("expenses", arrayOf("id"), null, null, null, null, null).use { it.count })
        }
    }

    @Test
    fun budgetsDailyAggregatesAndBackupMergeAreConsistent() = runBlocking {
        helper = ExpenseDatabaseHelper(context, databaseName)
        val repository = ExpenseRepositoryImpl(helper!!)
        val feb28 = LocalDate.of(2024, 2, 28).toEpochDay()
        val feb29 = LocalDate.of(2024, 2, 29).toEpochDay()
        val mar1 = LocalDate.of(2024, 3, 1).toEpochDay()
        repository.insertExpense(entry(100L, feb28))
        repository.insertExpense(entry(200L, feb29))
        repository.insertExpense(entry(300L, feb29))
        repository.insertExpense(entry(400L, mar1))

        assertNull(repository.getMonthlyBudget(2024, 2))
        val zeroBudget = MonthlyBudget(2024, 2, 0L, 10L, 11L)
        assertTrue(repository.saveMonthlyBudget(zeroBudget))
        assertEquals(0L, repository.getMonthlyBudget(2024, 2)?.amountCents)
        assertTrue(repository.saveMonthlyBudget(zeroBudget.copy(amountCents = 50_000L, updatedAt = 12L)))
        assertEquals(50_000L, repository.getMonthlyBudget(2024, 2)?.amountCents)
        assertEquals(10L, repository.getMonthlyBudget(2024, 2)?.createdAt)

        val leapDay = repository.getDailyExpenseAggregates(feb29, mar1)
        assertEquals(listOf(DailyExpenseAggregate(feb29, 500L, 2)), leapDay)
        val february = repository.getDailyExpenseAggregates(feb28, mar1)
        assertEquals(listOf(DailyExpenseAggregate(feb28, 100L, 1), DailyExpenseAggregate(feb29, 500L, 2)), february)

        val imported = entry(700L, feb29, id = UUID.randomUUID().toString())
        val importedBudget = MonthlyBudget(2025, 1, 90_000L, 20L, 21L)
        val first = repository.applyBackupMerge(listOf(imported), listOf(importedBudget))
        assertEquals(1, first.insertedExpenses)
        assertEquals(0, first.skippedExpenses)
        assertEquals(1, first.upsertedBudgets)
        val repeated = repository.applyBackupMerge(listOf(imported), listOf(importedBudget))
        assertEquals(0, repeated.insertedExpenses)
        assertEquals(1, repeated.skippedExpenses)
        assertEquals(1, repeated.upsertedBudgets)
        assertEquals(90_000L, repository.getMonthlyBudget(2025, 1)?.amountCents)
        assertEquals(5, repository.getAllExpenses().size)
    }

    @Test
    fun deletingSavedMonthlyBudgetMakesItUnset() = runBlocking {
        helper = ExpenseDatabaseHelper(context, databaseName)
        val repository = ExpenseRepositoryImpl(helper!!)
        val budget = MonthlyBudget(2025, 4, 68_000L, 10L, 11L)

        assertTrue(repository.saveMonthlyBudget(budget))
        assertEquals(budget, repository.getMonthlyBudget(2025, 4))
        assertTrue(repository.deleteMonthlyBudget(2025, 4))

        assertNull(repository.getMonthlyBudget(2025, 4))
    }

    @Test
    fun backupMergeReplacesExistingBudgetAndRepeatedImportKeepsMergeSemantics() = runBlocking {
        helper = ExpenseDatabaseHelper(context, databaseName)
        val repository = ExpenseRepositoryImpl(helper!!)
        val localBudget = MonthlyBudget(2025, 3, 42_000L, 20L, 21L)
        val importedBudget = MonthlyBudget(2025, 3, 96_000L, 30L, 31L)
        val importedExpense = entry(1_700L, LocalDate.of(2025, 3, 12).toEpochDay())

        assertTrue(repository.saveMonthlyBudget(localBudget))
        assertEquals(localBudget, repository.getMonthlyBudget(2025, 3))

        val first = repository.applyBackupMerge(listOf(importedExpense), listOf(importedBudget))

        assertEquals(1, first.insertedExpenses)
        assertEquals(0, first.skippedExpenses)
        assertEquals(1, first.upsertedBudgets)
        assertEquals(importedBudget, repository.getMonthlyBudget(2025, 3))
        assertEquals(importedExpense.backupId, repository.getAllExpenses().single().backupId)

        val repeated = repository.applyBackupMerge(listOf(importedExpense), listOf(importedBudget))

        assertEquals(0, repeated.insertedExpenses)
        assertEquals(1, repeated.skippedExpenses)
        assertEquals(1, repeated.upsertedBudgets)
        assertEquals(importedBudget, repository.getMonthlyBudget(2025, 3))
        assertEquals(1, repository.getAllExpenses().size)
    }

    @Test
    fun insertedAndMergedDataPersistWithBackupIdsAfterReopeningSameDatabase() = runBlocking {
        helper = ExpenseDatabaseHelper(context, databaseName)
        val repository = ExpenseRepositoryImpl(helper!!)
        val insertedExpense = entry(2_400L, LocalDate.of(2024, 8, 2).toEpochDay())
        val insertedExpenseId = repository.insertExpense(insertedExpense)
        val savedBudget = MonthlyBudget(2024, 8, 72_000L, 40L, 41L)
        assertTrue(repository.saveMonthlyBudget(savedBudget))

        val importedExpense = entry(3_100L, LocalDate.of(2024, 9, 3).toEpochDay())
        val importedBudget = MonthlyBudget(2024, 9, 83_000L, 50L, 51L)
        val mergeResult = repository.applyBackupMerge(listOf(importedExpense), listOf(importedBudget))
        assertEquals(1, mergeResult.insertedExpenses)
        assertEquals(0, mergeResult.skippedExpenses)
        assertEquals(1, mergeResult.upsertedBudgets)

        helper!!.close()
        helper = null
        helper = ExpenseDatabaseHelper(context, databaseName)
        val reopenedRepository = ExpenseRepositoryImpl(helper!!)

        val reopenedInsertedExpense = reopenedRepository.getExpenseById(insertedExpenseId)!!
        val reopenedExpenses = reopenedRepository.getAllExpenses()
        val reopenedImportedExpense = reopenedExpenses.single { it.backupId == importedExpense.backupId }
        assertEquals(2, reopenedExpenses.size)
        assertEquals(insertedExpense.backupId, reopenedInsertedExpense.backupId)
        assertEquals(importedExpense.backupId, reopenedImportedExpense.backupId)
        assertEquals(UUID.fromString(insertedExpense.backupId).toString(), reopenedInsertedExpense.backupId)
        assertEquals(UUID.fromString(importedExpense.backupId).toString(), reopenedImportedExpense.backupId)
        assertEquals(savedBudget, reopenedRepository.getMonthlyBudget(2024, 8))
        assertEquals(importedBudget, reopenedRepository.getMonthlyBudget(2024, 9))
    }

    @Test
    fun failedBackupBudgetWriteRollsBackExpenseInsert() = runBlocking {
        helper = ExpenseDatabaseHelper(context, databaseName)
        val repository = ExpenseRepositoryImpl(helper!!)
        val db = helper!!.writableDatabase
        db.execSQL("CREATE TRIGGER reject_budget_insert BEFORE INSERT ON monthly_budgets BEGIN SELECT RAISE(ABORT, 'forced import error'); END")
        val newEntry = entry(1_234L, 25_000L)
        var failed = false
        try {
            repository.applyBackupMerge(
                expenses = listOf(newEntry),
                budgets = listOf(MonthlyBudget(2025, 3, 5_000L, 1L, 1L)),
            )
        } catch (_: RuntimeException) {
            failed = true
        }

        assertTrue("injected budget write failure must be surfaced", failed)
        assertTrue(repository.getAllExpenses().isEmpty())
        assertNull(repository.getMonthlyBudget(2025, 3))
    }

    private fun createV1Fixture(expenses: List<LegacyExpense>) {
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                """CREATE TABLE expenses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
                    category TEXT NOT NULL,
                    note TEXT NOT NULL DEFAULT '',
                    expense_date INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )""".trimIndent(),
            )
            db.execSQL("CREATE INDEX idx_expenses_date ON expenses(expense_date)")
            expenses.forEach { entry ->
                val values = ContentValues().apply {
                    put("id", entry.id)
                    put("amount_cents", entry.amountCents)
                    put("category", entry.category)
                    put("note", entry.note)
                    put("expense_date", entry.expenseDate)
                    put("created_at", entry.createdAt)
                    put("updated_at", entry.updatedAt)
                }
                db.insertOrThrow("expenses", null, values)
            }
            db.version = 1
        }
    }

    private fun entry(amountCents: Long, expenseDate: Long, id: String = UUID.randomUUID().toString()) =
        ExpenseEntry(
            amountCents = amountCents,
            category = ExpenseCategory.FOOD,
            note = "qa",
            expenseDate = expenseDate,
            createdAt = 1L,
            updatedAt = 2L,
            backupId = id,
        )

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean = db.query(
        "sqlite_master",
        arrayOf("name"),
        "type = 'table' AND name = ?",
        arrayOf(table),
        null,
        null,
        null,
    ).use { it.moveToFirst() }

    private fun indexExists(db: SQLiteDatabase, index: String): Boolean = db.query(
        "sqlite_master",
        arrayOf("name"),
        "type = 'index' AND name = ?",
        arrayOf(index),
        null,
        null,
        null,
    ).use { it.moveToFirst() }

    private fun columnExists(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) if (cursor.getString(nameColumn) == column) return@use true
            false
        }

    private fun expenseSchema(db: SQLiteDatabase): List<ExpenseColumnInfo> =
        db.rawQuery("PRAGMA table_info(${ExpenseDatabaseHelper.TABLE_EXPENSES})", null).use { cursor ->
            val cidColumn = cursor.getColumnIndexOrThrow("cid")
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            val typeColumn = cursor.getColumnIndexOrThrow("type")
            val notNullColumn = cursor.getColumnIndexOrThrow("notnull")
            val defaultColumn = cursor.getColumnIndexOrThrow("dflt_value")
            val primaryKeyColumn = cursor.getColumnIndexOrThrow("pk")
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ExpenseColumnInfo(
                            cid = cursor.getInt(cidColumn),
                            name = cursor.getString(nameColumn),
                            type = cursor.getString(typeColumn),
                            notNull = cursor.getInt(notNullColumn),
                            defaultValue = if (cursor.isNull(defaultColumn)) null else cursor.getString(defaultColumn),
                            primaryKeyOrder = cursor.getInt(primaryKeyColumn),
                        ),
                    )
                }
            }
        }

    private fun indexIsUnique(db: SQLiteDatabase, index: String): Boolean =
        db.rawQuery("PRAGMA index_list(${ExpenseDatabaseHelper.TABLE_EXPENSES})", null).use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            val uniqueColumn = cursor.getColumnIndexOrThrow("unique")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == index) return@use cursor.getInt(uniqueColumn) == 1
            }
            false
        }

    private data class ExpenseColumnInfo(
        val cid: Int,
        val name: String,
        val type: String,
        val notNull: Int,
        val defaultValue: String?,
        val primaryKeyOrder: Int,
    )

    private data class LegacyExpense(
        val id: Long,
        val amountCents: Long,
        val category: String,
        val note: String,
        val expenseDate: Long,
        val createdAt: Long,
        val updatedAt: Long,
    )
}
