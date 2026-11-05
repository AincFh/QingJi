package com.linjincheng.qingji.data

import java.util.UUID

/**
 * A locally stored expense record.
 * 金额单位为「分」（Long），严禁 Float/Double；expenseDate 为 LocalDate.toEpochDay()。
 */
data class ExpenseEntry(
    val id: Long = 0L,
    val amountCents: Long,          // 金额，单位分，>0
    val category: ExpenseCategory,
    val note: String = "",          // 最长 120 字
    val expenseDate: Long,          // LocalDate.toEpochDay()
    val createdAt: Long,            // System.currentTimeMillis()
    val updatedAt: Long,
    /** Stable cross-install identity used for idempotent backup merge. Editing keeps this value. */
    val backupId: String = UUID.randomUUID().toString(),
)
