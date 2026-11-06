package com.linjincheng.qingji.ui

import com.linjincheng.qingji.data.MonthlyBudget
import com.linjincheng.qingji.data.MonthlyBudgetSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetScreenModelTest {
    @Test
    fun displayModelKeepsUnsetBudgetDistinctFromZero() {
        val unset = summary(budgetCents = null, spentCents = 4_500L).toBudgetDisplayModel()
        val zero = summary(budgetCents = 0L, spentCents = 0L).toBudgetDisplayModel()

        assertEquals(BudgetDisplayState.NOT_SET, unset.state)
        assertNull(unset.remainingCents)
        assertNull(unset.usagePercent)
        assertEquals(BudgetDisplayState.ZERO_BUDGET, zero.state)
        assertEquals(0L, zero.budgetCents)
        assertEquals(0L, zero.remainingCents)
        assertEquals(0, zero.usagePercent)
    }

    @Test
    fun displayModelMarksAnExactlyUsedBudgetSeparately() {
        val result = summary(budgetCents = 10_000L, spentCents = 10_000L).toBudgetDisplayModel()

        assertEquals(BudgetDisplayState.EXACTLY_USED, result.state)
        assertEquals(0L, result.remainingCents)
        assertEquals(100, result.usagePercent)
    }

    @Test
    fun roundedHundredPercentDoesNotLookExhaustedWhileBalanceRemains() {
        val result = summary(budgetCents = 1_000L, spentCents = 999L).toBudgetDisplayModel()

        assertEquals(BudgetDisplayState.WITHIN_BUDGET, result.state)
        assertEquals(1L, result.remainingCents)
        assertEquals(99, result.usagePercent)
    }

    @Test
    fun displayModelKeepsOverBudgetAmountNegativeAndCapsProgress() {
        val result = summary(budgetCents = 10_000L, spentCents = 12_345L).toBudgetDisplayModel()

        assertEquals(BudgetDisplayState.OVER_BUDGET, result.state)
        assertEquals(-2_345L, result.remainingCents)
        assertEquals(100, result.usagePercent)
    }

    @Test
    fun zeroBudgetWithSpendingIsOverBudgetWithoutDivisionByZero() {
        val result = summary(budgetCents = 0L, spentCents = 1L).toBudgetDisplayModel()

        assertEquals(BudgetDisplayState.OVER_BUDGET, result.state)
        assertEquals(0L, result.budgetCents)
        assertEquals(-1L, result.remainingCents)
        assertEquals(100, result.usagePercent)
    }

    @Test
    fun parserAcceptsExplicitZeroAndUsesMoneyUtilsForPositiveValues() {
        assertEquals(BudgetAmountInputResult.Valid(0L), parseBudgetAmountInput("0"))
        assertEquals(BudgetAmountInputResult.Valid(0L), parseBudgetAmountInput(" 000.00 "))
        assertEquals(
            BudgetAmountInputResult.Invalid("TOO_MANY_DECIMALS"),
            parseBudgetAmountInput("0.000"),
        )
        assertEquals(BudgetAmountInputResult.Valid(2_850L), parseBudgetAmountInput("28.5"))
        assertEquals(BudgetAmountInputResult.Valid(99_999_999L), parseBudgetAmountInput("999999.99"))
    }

    @Test
    fun parserRejectsNegativeMalformedOverPrecisionAndOutOfRangeValues() {
        assertEquals(BudgetAmountInputResult.Invalid("NOT_POSITIVE"), parseBudgetAmountInput("-1"))
        assertEquals(BudgetAmountInputResult.Invalid("NOT_NUMBER"), parseBudgetAmountInput("1.2.3"))
        assertEquals(BudgetAmountInputResult.Invalid("TOO_MANY_DECIMALS"), parseBudgetAmountInput("1.234"))
        assertEquals(BudgetAmountInputResult.Invalid("OUT_OF_RANGE"), parseBudgetAmountInput("1000000"))
        assertEquals(BudgetAmountInputResult.Invalid("EMPTY"), parseBudgetAmountInput("  "))
    }

    private fun summary(budgetCents: Long?, spentCents: Long): MonthlyBudgetSummary {
        val budget = budgetCents?.let {
            MonthlyBudget(year = 2026, month = 11, amountCents = it, createdAt = 1L, updatedAt = 1L)
        }
        return MonthlyBudgetSummary.calculate(2026, 11, budget, spentCents)
    }
}
