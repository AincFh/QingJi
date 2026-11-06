package com.linjincheng.qingji.util

/** Monthly budget is an optional repeating, local-only spending target.
 * Actual spent cents always come from the SQLite month aggregation.
 * This calculator never modifies expenses or balances.
 */
data class MonthlyBudgetSnapshot(
    val budgetCents: Long,
    val spentCents: Long,
    val remainingCents: Long,
    val exceededCents: Long,
    val progress: Float,
    val status: BudgetStatus,
)
enum class BudgetStatus {
    WITHIN, APPROACHING, REACHED, EXCEEDED
}
object MonthlyBudget {
    const val MAX_BUDGET_CENTS = 99_999_999L

    /** Invalid saved preferences are treated as disabled, never as a zero budget. */
    fun sanitize(cents: Long?): Long? =
        cents?.takeIf { it in 1L..MAX_BUDGET_CENTS }

    fun calculate(limitCents: Long?, monthSpentCents: Long): MonthlyBudgetSnapshot? {
        val limit = sanitize(limitCents) ?: return null
        val spent = monthSpentCents.coerceAtLeast(0L)
        val left = if (spent >= limit) 0L else limit - spent
        val over = if (spent > limit) spent - limit else 0L
        val ratio = spent.toDouble() / limit.toDouble()
        val status = when {
            spent > limit -> BudgetStatus.EXCEEDED
            spent == limit -> BudgetStatus.REACHED
            ratio >= 0.8 -> BudgetStatus.APPROACHING
            else -> BudgetStatus.WITHIN
        }
        return MonthlyBudgetSnapshot(
            budgetCents = limit, spentCents = spent,
            remainingCents = left, exceededCents = over,
            progress = ratio.coerceIn(0.0, 1.0).toFloat(),
            status = status,
        )
    }
}
