package com.linjincheng.qingji.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyBudgetTest {
    @Test fun disabledBudgetProducesNoCardData() {
        assertNull(MonthlyBudget.calculate(null, 3000))
        assertNull(MonthlyBudget.calculate(0, 3000))
        assertNull(MonthlyBudget.calculate(-5, 3000))
        assertNull(MonthlyBudget.calculate(MonthlyBudget.MAX_BUDGET_CENTS + 1, 3000))
    }

    @Test fun budgetSanitizationAcceptsOneCentAndMaximum() {
        assertEquals(1L, MonthlyBudget.sanitize(1L))
        assertEquals(MonthlyBudget.MAX_BUDGET_CENTS,
            MonthlyBudget.sanitize(MonthlyBudget.MAX_BUDGET_CENTS))
        assertNull(MonthlyBudget.sanitize(Long.MIN_VALUE))
        assertNull(MonthlyBudget.sanitize(Long.MAX_VALUE))
    }

    @Test fun zeroSpendingLeavesEntireBudget() {
        val state = MonthlyBudget.calculate(300000, 0)!!
        assertEquals(BudgetStatus.WITHIN, state.status)
        assertEquals(300000L, state.remainingCents)
        assertEquals(0L, state.exceededCents)
        assertEquals(0f, state.progress, 0.00001f)
    }

    @Test fun spendingExactlyOneCentRemainsLossless() {
        val state = MonthlyBudget.calculate(100, 1)!!
        assertEquals(99L, state.remainingCents)
        assertEquals(0L, state.exceededCents)
        assertEquals(.01f, state.progress, .00001f)
    }

    @Test fun eightyPercentTriggersApproaching() {
        val before = MonthlyBudget.calculate(10000, 7999)!!
        val threshold = MonthlyBudget.calculate(10000, 8000)!!
        assertEquals(BudgetStatus.WITHIN, before.status)
        assertEquals(BudgetStatus.APPROACHING, threshold.status)
        assertEquals(2000L, threshold.remainingCents)
        assertEquals(.8f, threshold.progress, .00001f)
    }

    @Test fun exactlyMeetingBudgetIsDistinctFromOverspending() {
        val equal = MonthlyBudget.calculate(10000, 10000)!!
        val over = MonthlyBudget.calculate(10000, 10001)!!
        assertEquals(BudgetStatus.REACHED, equal.status)
        assertEquals(0L, equal.exceededCents)
        assertEquals(0L, equal.remainingCents)
        assertEquals(BudgetStatus.EXCEEDED, over.status)
        assertEquals(1L, over.exceededCents)
        assertEquals(0L, over.remainingCents)
        assertEquals(1f, over.progress, 0.00001f)
    }

    @Test fun negativeOrInvalidAggregatedSpendingCannotInventRemainingDebt() {
        val state = MonthlyBudget.calculate(100, -99)!!
        assertEquals(0L, state.spentCents)
        assertEquals(100L, state.remainingCents)
    }

    @Test fun hugeMonthlyTotalsRemainFiniteAndBounded() {
        val state = MonthlyBudget.calculate(1, Long.MAX_VALUE)!!
        assertEquals(Long.MAX_VALUE - 1, state.exceededCents)
        assertEquals(1f, state.progress, 0.00001f)
        assertEquals(BudgetStatus.EXCEEDED, state.status)
    }

    @Test fun changingMonthTotalIsRecomputedWithoutMutatingTarget() {
        val limit = 140000L
        val october = MonthlyBudget.calculate(limit, 90000)!!
        val november = MonthlyBudget.calculate(limit, 150000)!!
        assertEquals(limit, october.budgetCents)
        assertEquals(limit, november.budgetCents)
        assertEquals(BudgetStatus.WITHIN, october.status)
        assertEquals(BudgetStatus.EXCEEDED, november.status)
        assertEquals(50000L, october.remainingCents)
        assertEquals(10000L, november.exceededCents)
    }

    @Test fun exactRemainderDoesNotUseFloatingPointMoneyArithmetic() {
        val state = MonthlyBudget.calculate(99999999, 33333333)!!
        assertEquals(66666666L, state.remainingCents)
        assertEquals(33333333L, state.spentCents)
        assertFalse(state.remainingCents < 0)
        assertTrue(state.progress > 0f && state.progress < 1f)
    }

    @Test fun beforeLimitStatusNeverReportsOverage() {
        for (spent in listOf(0L, 1L, 17L, 9999L)) {
            val state = MonthlyBudget.calculate(10000, spent)!!
            assertEquals(0L, state.exceededCents)
            assertEquals(10000L - spent, state.remainingCents)
        }
    }
}
