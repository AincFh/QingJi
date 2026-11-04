package com.linjincheng.qingji.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLiteOpenHelper：数据库 qingji.db，version 1（契约 §3 冻结）。
 * 建表与索引 SQL 与契约 §3 逐字一致；只建结构，不插入任何预置/演示数据。
 */
class ExpenseDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_EXPENSES)
        db.execSQL(SQL_CREATE_INDEX)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 当前恒为 version 1，没有升级路径；不做任何破坏性操作。
    }

    companion object {
        const val DATABASE_NAME = "qingji.db"
        const val DATABASE_VERSION = 1

        /** 与契约 §3 逐字一致（表结构）。 */
        const val SQL_CREATE_EXPENSES = """CREATE TABLE expenses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
    category TEXT NOT NULL,
    note TEXT NOT NULL DEFAULT '',
    expense_date INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
)"""

        /** 与契约 §3 逐字一致（日期索引）。 */
        const val SQL_CREATE_INDEX = "CREATE INDEX idx_expenses_date ON expenses(expense_date)"
    }
}
