package com.linjincheng.qingji

import com.linjincheng.qingji.ui.CalendarDayUi
import com.linjincheng.qingji.ui.CalendarUiModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarUiModelTest {
    @Test
    fun buildMonthDaysFillsZeroDaysAndKeepsChronologicalOrder() {
        val month = YearMonth.of(2026, 11)
        val result = CalendarUiModel.buildMonthDays(
            month,
            listOf(CalendarDayUi(LocalDate.of(2026, 11, 5).toEpochDay(), 1_250L, 2)),
        )

        assertEquals(30, result.size)
        assertEquals(LocalDate.of(2026, 11, 1).toEpochDay(), result.first().epochDay)
        assertEquals(CalendarDayUi(LocalDate.of(2026, 11, 5).toEpochDay(), 1_250L, 2), result[4])
        assertEquals(CalendarDayUi(LocalDate.of(2026, 11, 6).toEpochDay(), 0L, 0), result[5])
    }

    @Test
    fun buildMonthDaysIgnoresOutsideDatesAndCombinesRepeatedDays() {
        val month = YearMonth.of(2027, 1)
        val firstDay = LocalDate.of(2027, 1, 1).toEpochDay()
        val result = CalendarUiModel.buildMonthDays(
            month,
            listOf(
                CalendarDayUi(LocalDate.of(2026, 12, 31).toEpochDay(), 5_000L, 1),
                CalendarDayUi(firstDay, 700L, 1),
                CalendarDayUi(firstDay, 300L, 2),
                CalendarDayUi(LocalDate.of(2027, 2, 1).toEpochDay(), 9_000L, 3),
            ),
        )

        assertEquals(31, result.size)
        assertEquals(CalendarDayUi(firstDay, 1_000L, 3), result.first())
        assertEquals(CalendarDayUi(LocalDate.of(2027, 1, 31).toEpochDay(), 0L, 0), result.last())
    }

    @Test
    fun buildMonthGridIsMondayFirstAndPadsToCompleteWeeks() {
        val grid = CalendarUiModel.buildMonthGrid(YearMonth.of(2026, 11))

        assertEquals(42, grid.size)
        assertNull(grid[0])
        assertNull(grid[1])
        assertEquals(LocalDate.of(2026, 11, 1).toEpochDay(), grid[6])
        assertEquals(LocalDate.of(2026, 11, 30).toEpochDay(), grid[35])
        assertNull(grid[36])
        assertNull(grid[41])
    }

    @Test
    fun buildMonthGridSupportsDifferentWeekStart() {
        val grid = CalendarUiModel.buildMonthGrid(
            month = YearMonth.of(2026, 11),
            firstDayOfWeek = DayOfWeek.SUNDAY,
        )

        assertEquals(LocalDate.of(2026, 11, 1).toEpochDay(), grid.first())
        assertEquals(LocalDate.of(2026, 11, 7).toEpochDay(), grid[6])
    }

    @Test
    fun buildMonthGridHasSixWeeksWhenNeededAndLeapMonthDaysArePresent() {
        val sixWeekGrid = CalendarUiModel.buildMonthGrid(YearMonth.of(2026, 8))
        val leapMonth = CalendarUiModel.buildMonthDays(YearMonth.of(2024, 2), emptyList())

        assertEquals(42, sixWeekGrid.size)
        assertEquals(29, leapMonth.size)
        assertEquals(LocalDate.of(2024, 2, 29).toEpochDay(), leapMonth.last().epochDay)
        assertTrue(leapMonth.all { it.totalCents == 0L && it.transactionCount == 0 })
    }
}
