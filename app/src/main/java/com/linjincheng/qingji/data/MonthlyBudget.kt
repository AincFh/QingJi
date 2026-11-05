package com.linjincheng.qingji.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

/** A persisted monthly spending limit. Absence from SQLite means "not set"; zero is an explicit budget. */
data class MonthlyBudget(
    val year: Int,
    val month: Int,
    val amountCents: Long,
    val createdAt: Long,
    val updatedAt: Long,
) {
    init {
        require(year in 1..9999) { "预算年份超出范围" }
        require(month in 1..12) { "预算月份必须在 1..12" }
        YearMonth.of(year, month)
        require(amountCents >= 0L) { "预算不能为负数" }
        require(createdAt >= 0L && updatedAt >= 0L) { "预算时间戳不能为负数" }
    }
}

/** A derived view of the budget for one month. Actual spend is always queried from expense rows. */
data class MonthlyBudgetSummary(
    val year: Int,
    val month: Int,
    val budgetCents: Long?,
    val spentCents: Long,
    val remainingCents: Long?,
    /** Null when no budget is set; capped at 100 for progress UI, with [isOverBudget] carrying overflow. */
    val usagePercent: Int?,
    val isOverBudget: Boolean,
) {
    val hasBudget: Boolean get() = budgetCents != null

    companion object {
        fun calculate(year: Int, month: Int, budget: MonthlyBudget?, spentCents: Long): MonthlyBudgetSummary {
            require(year in 1..9999 && month in 1..12)
            YearMonth.of(year, month)
            require(budget == null || (budget.year == year && budget.month == month)) {
                "预算月份与汇总月份不一致"
            }
            require(spentCents >= 0L) { "支出不能为负数" }
            val amount = budget?.amountCents
            if (amount == null) {
                return MonthlyBudgetSummary(year, month, null, spentCents, null, null, false)
            }

            val overBudget = spentCents > amount
            val percent = when {
                amount == 0L -> if (spentCents == 0L) 0 else 100
                else -> {
                    val rounded = BigDecimal.valueOf(spentCents)
                        .multiply(BigDecimal.valueOf(100L))
                        .divide(BigDecimal.valueOf(amount), 0, RoundingMode.HALF_UP)
                        .min(BigDecimal.valueOf(Int.MAX_VALUE.toLong()))
                        .toInt()
                    rounded.coerceIn(0, 100)
                }
            }
            return MonthlyBudgetSummary(
                year = budget.year,
                month = budget.month,
                budgetCents = amount,
                spentCents = spentCents,
                remainingCents = amount - spentCents,
                usagePercent = percent,
                isOverBudget = overBudget,
            )
        }
    }
}

/** SQLite grouped aggregate for one expense day. Empty days are filled by the calendar/view layer. */
data class DailyExpenseAggregate(
    val epochDay: Long,
    val totalCents: Long,
    val transactionCount: Int,
) {
    init {
        require(totalCents >= 0L)
        require(transactionCount >= 0)
    }
}

/** Result of one confirmed backup merge transaction. */
data class BackupApplyResult(
    val insertedExpenses: Int,
    val skippedExpenses: Int,
    val upsertedBudgets: Int,
)
