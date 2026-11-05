package com.linjincheng.qingji.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

/** SQLiteOpenHelper for the local ledger. [databaseName] is injectable for isolated database tests. */
class ExpenseDatabaseHelper(
    context: Context,
    databaseName: String = DATABASE_NAME,
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, DATABASE_VERSION) {

    init {
        require(databaseName.isNotBlank()) { "databaseName 不能为空" }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_EXPENSES)
        db.execSQL(SQL_CREATE_INDEX)
        db.execSQL(SQL_CREATE_BACKUP_ID_INDEX)
        db.execSQL(SQL_CREATE_MONTHLY_BUDGETS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2 && newVersion >= 2) {
            // SQLiteOpenHelper wraps onUpgrade in a transaction. Adding a nullable column avoids
            // rewriting any legacy expense values; each existing row then gets its own stable ID.
            db.execSQL("ALTER TABLE expenses ADD COLUMN backup_id TEXT")
            populateMissingBackupIds(db)
            db.execSQL(SQL_CREATE_BACKUP_ID_INDEX)
            db.execSQL(SQL_CREATE_MONTHLY_BUDGETS)
        }
    }

    private fun populateMissingBackupIds(db: SQLiteDatabase) {
        val ids = ArrayList<Long>()
        db.query(TABLE_EXPENSES, arrayOf(COL_ID), "$COL_BACKUP_ID IS NULL OR $COL_BACKUP_ID = ''", null,
            null, null, COL_ID).use { cursor ->
            while (cursor.moveToNext()) ids += cursor.getLong(0)
        }
        ids.forEach { id ->
            val values = ContentValues().apply { put(COL_BACKUP_ID, UUID.randomUUID().toString()) }
            val rows = db.update(TABLE_EXPENSES, values, "$COL_ID = ?", arrayOf(id.toString()))
            check(rows == 1) { "无法为历史流水 $id 生成备份身份" }
        }
    }

    companion object {
        const val DATABASE_NAME = "qingji.db"
        const val DATABASE_VERSION = 2

        const val TABLE_EXPENSES = "expenses"
        const val TABLE_MONTHLY_BUDGETS = "monthly_budgets"
        const val COL_ID = "id"
        const val COL_AMOUNT_CENTS = "amount_cents"
        const val COL_CATEGORY = "category"
        const val COL_NOTE = "note"
        const val COL_EXPENSE_DATE = "expense_date"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_BACKUP_ID = "backup_id"

        /** Expenses schema for a fresh v2 database. */
        const val SQL_CREATE_EXPENSES = """CREATE TABLE expenses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
    category TEXT NOT NULL,
    note TEXT NOT NULL DEFAULT '',
    expense_date INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    backup_id TEXT NOT NULL
)"""

        const val SQL_CREATE_INDEX = "CREATE INDEX idx_expenses_date ON expenses(expense_date)"
        const val SQL_CREATE_BACKUP_ID_INDEX =
            "CREATE UNIQUE INDEX idx_expenses_backup_id ON expenses(backup_id)"

        const val SQL_CREATE_MONTHLY_BUDGETS = """CREATE TABLE monthly_budgets (
    year INTEGER NOT NULL CHECK (year BETWEEN 1 AND 9999),
    month INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
    amount_cents INTEGER NOT NULL CHECK (amount_cents >= 0),
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (year, month)
)"""
    }
}
