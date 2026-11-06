package com.linjincheng.qingji.data

import com.linjincheng.qingji.util.MoneyUtils
import java.time.LocalDate

/** Pure input validation shared by direct repository writes and backup merges. */
internal object ExpenseRepositoryValidation {
    fun validateExpense(entry: ExpenseEntry) {
        require(entry.amountCents in 1L..MoneyUtils.MAX_CENTS) {
            "流水金额必须在 1 至 ${MoneyUtils.MAX_CENTS} 分之间"
        }
        require(entry.note.length <= 120) { "备注不能超过 120 字" }
        val date = try {
            LocalDate.ofEpochDay(entry.expenseDate)
        } catch (error: Exception) {
            throw IllegalArgumentException("流水日期无效", error)
        }
        require(date.year in 1..9999) { "流水年份必须在 1..9999" }
        require(entry.createdAt >= 0L && entry.updatedAt >= 0L) { "流水时间戳不能为负数" }
    }

    fun validateBudget(budget: MonthlyBudget) {
        require(budget.amountCents in 0L..MoneyUtils.MAX_CENTS) {
            "预算金额必须在 0 至 ${MoneyUtils.MAX_CENTS} 分之间"
        }
        require(budget.createdAt >= 0L && budget.updatedAt >= 0L) { "预算时间戳不能为负数" }
    }
}
