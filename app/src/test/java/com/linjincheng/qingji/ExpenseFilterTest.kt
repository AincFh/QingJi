package com.linjincheng.qingji

import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.data.ExpenseEntry
import com.linjincheng.qingji.util.EpochDayRange
import com.linjincheng.qingji.util.ExpenseFilter
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseFilterTest {

    @Test
    fun dayRangeIncludesStartAndExcludesNextDay() {
        val day = LocalDate.of(2024, 2, 29).toEpochDay()
        val range = EpochDayRange.day(day)

        assertTrue(range.contains(day))
        assertFalse(range.contains(day + 1L))
        assertEquals("2月29日", range.label())
    }

    @Test
    fun monthRangeUsesHalfOpenLeapYearAndYearBoundary() {
        val leapFebruary = EpochDayRange.month(YearMonth.of(2024, 2))
        val december = EpochDayRange.month(YearMonth.of(2025, 12))

        assertEquals(29L, leapFebruary.endExclusive - leapFebruary.startInclusive)
        assertEquals(YearMonth.of(2024, 2), leapFebruary.asYearMonthOrNull())
        assertTrue(leapFebruary.contains(LocalDate.of(2024, 2, 29).toEpochDay()))
        assertFalse(leapFebruary.contains(LocalDate.of(2024, 3, 1).toEpochDay()))
        assertEquals(YearMonth.of(2025, 12), december.asYearMonthOrNull())
        assertTrue(december.contains(LocalDate.of(2026, 1, 1).minusDays(1).toEpochDay()))
        assertFalse(december.contains(LocalDate.of(2026, 1, 1).toEpochDay()))
    }

    @Test
    fun dateCategoryAndKeywordConditionsAreCombined() {
        val entries = listOf(
            entry(1, LocalDate.of(2026, 11, 4), ExpenseCategory.FOOD, "午餐"),
            entry(2, LocalDate.of(2026, 11, 5), ExpenseCategory.FOOD, "午餐外卖"),
            entry(3, LocalDate.of(2026, 11, 5), ExpenseCategory.TRANSPORT, "午餐路线"),
            entry(4, LocalDate.of(2026, 11, 6), ExpenseCategory.FOOD, "午餐"),
        )
        val filter = ExpenseFilter(
            dateRange = EpochDayRange.day(LocalDate.of(2026, 11, 5).toEpochDay()),
            category = ExpenseCategory.FOOD,
            keyword = "午餐",
        )

        assertEquals(listOf(2L), filter.apply(entries).map { it.id })
        assertTrue(ExpenseFilter().isEmpty)
        assertEquals(listOf(1L, 2L, 3L, 4L), ExpenseFilter().apply(entries).map { it.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyOrReversedRange() {
        EpochDayRange(10L, 10L)
    }

    private fun entry(id: Long, date: LocalDate, category: ExpenseCategory, note: String) = ExpenseEntry(
        id = id,
        amountCents = id * 100L,
        category = category,
        note = note,
        expenseDate = date.toEpochDay(),
        createdAt = id,
        updatedAt = id,
    )
}
