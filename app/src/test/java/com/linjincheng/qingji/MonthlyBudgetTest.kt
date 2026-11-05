package com.linjincheng.qingji

import com.linjincheng.qingji.data.MonthlyBudget
import com.linjincheng.qingji.data.MonthlyBudgetSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyBudgetTest {
    @Test
    fun unsetBudgetIsDifferentFromAnExplicitZeroBudget() {
        val unset = MonthlyBudgetSummary.calculate(2026, 11, null, spentCents = 1_200L)
        val zero = MonthlyBudgetSummary.calculate(2026, 11, budget(0L), spentCents = 1_200L)

        assertFalse(unset.hasBudget)
        assertNull(unset.remainingCents)
        assertNull(unset.usagePercent)
        assertFalse(unset.isOverBudget)
        assertTrue(zero.hasBudget)
        assertEquals(-1_200L, zero.remainingCents)
        assertEquals(100, zero.usagePercent)
        assertTrue(zero.isOverBudget)
    }

    @Test
    fun exactUsageAndOverspendingUseIntegerCents() {
        val exact = MonthlyBudgetSummary.calculate(2026, 12, budget(10_000L, month = 12), spentCents = 10_000L)
        val over = MonthlyBudgetSummary.calculate(2026, 12, budget(10_000L, month = 12), spentCents = 10_001L)

        assertEquals(0L, exact.remainingCents)
        assertEquals(100, exact.usagePercent)
        assertFalse(exact.isOverBudget)
        assertEquals(-1L, over.remainingCents)
        assertEquals(100, over.usagePercent)
        assertTrue(over.isOverBudget)
    }

    @Test
    fun noSpendUnderZeroBudgetDoesNotDivideByZero() {
        val result = MonthlyBudgetSummary.calculate(2027, 1, budget(0L, year = 2027, month = 1), spentCents = 0L)

        assertEquals(0L, result.remainingCents)
        assertEquals(0, result.usagePercent)
        assertFalse(result.isOverBudget)
    }

    @Test
    fun aNewYearMonthHasItsOwnBudget() {
        val oldMonth = budget(20_000L, year = 2026, month = 12)
        val nextMonth = MonthlyBudget(2027, 1, 5_000L, createdAt = 2L, updatedAt = 3L)

        assertEquals(2026, oldMonth.year)
        assertEquals(12, oldMonth.month)
        assertEquals(2027, nextMonth.year)
        assertEquals(1, nextMonth.month)
    }

    private fun budget(amount: Long, year: Int = 2026, month: Int = 11) =
        MonthlyBudget(year, month, amount, createdAt = 1L, updatedAt = 1L)
}
