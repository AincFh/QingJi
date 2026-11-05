package com.linjincheng.qingji.ui

import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.ui.components.visibleYuanText
import com.linjincheng.qingji.util.DailyExpenseTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ChartSelectionTest {

    private val days = listOf(
        DailyExpenseTotal(epochDay = 20_755L, totalCents = 1_800L, transactionCount = 1),
        DailyExpenseTotal(epochDay = 20_756L, totalCents = 0L, transactionCount = 0),
        DailyExpenseTotal(epochDay = 20_757L, totalCents = 2_400L, transactionCount = 2),
    )

    @Test
    fun trendSelectionPreservesVisibleDate() {
        assertEquals(20_756L, resolveTrendSelection(days, 20_756L))
    }

    @Test
    fun trendSelectionDefaultsToNewestDateWhenRequestedDateIsOutsideWindow() {
        assertEquals(20_757L, resolveTrendSelection(days, 19_000L))
        assertEquals(20_757L, resolveTrendSelection(days, null))
    }

    @Test
    fun emptyTrendHasNoSelection() {
        assertNull(resolveTrendSelection(emptyList(), 19_000L))
    }

    @Test
    fun categorySelectionPreservesAvailableCategoryOrUsesFirstAvailable() {
        val available = listOf(ExpenseCategory.FOOD, ExpenseCategory.TRANSPORT)

        assertEquals(ExpenseCategory.TRANSPORT, resolveCategorySelection(available, ExpenseCategory.TRANSPORT))
        assertEquals(ExpenseCategory.FOOD, resolveCategorySelection(available, null))
        assertEquals(ExpenseCategory.FOOD, resolveCategorySelection(available, ExpenseCategory.SHOPPING))
        assertNull(resolveCategorySelection(emptyList(), ExpenseCategory.FOOD))
    }

    @Test
    fun hiddenChartAmountDoesNotExposeDigits() {
        val hidden = visibleYuanText(123_456L, hidden = true)

        assertEquals("¥••••", hidden)
        assertFalse(hidden.any(Char::isDigit))
        assertEquals("¥1,234.56", visibleYuanText(123_456L, hidden = false))
    }
}
