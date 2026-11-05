package com.linjincheng.qingji.ui

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** One locally aggregated day's expense total. Amounts are stored as integer cents. */
data class CalendarDayUi(
    val epochDay: Long,
    val totalCents: Long,
    val transactionCount: Int,
) {
    init {
        require(totalCents >= 0L) { "A daily expense total cannot be negative." }
        require(transactionCount >= 0) { "A daily transaction count cannot be negative." }
    }
}

/** Pure date helpers used by the calendar screen and JVM tests. */
object CalendarUiModel {
    /**
     * Returns one entry for every date in [month], filling missing dates with zero totals.
     * Totals outside the requested month are ignored. Repeated dates are combined defensively.
     */
    fun buildMonthDays(
        month: YearMonth,
        dailyTotals: Iterable<CalendarDayUi>,
    ): List<CalendarDayUi> {
        val firstEpochDay = month.atDay(1).toEpochDay()
        val nextMonthEpochDay = month.plusMonths(1).atDay(1).toEpochDay()
        val totalsByEpochDay = mutableMapOf<Long, CalendarDayUi>()

        dailyTotals.forEach { total ->
            if (total.epochDay in firstEpochDay until nextMonthEpochDay) {
                val previous = totalsByEpochDay[total.epochDay]
                totalsByEpochDay[total.epochDay] = if (previous == null) {
                    total
                } else {
                    CalendarDayUi(
                        epochDay = total.epochDay,
                        totalCents = Math.addExact(previous.totalCents, total.totalCents),
                        transactionCount = Math.addExact(previous.transactionCount, total.transactionCount),
                    )
                }
            }
        }

        return (0 until month.lengthOfMonth()).map { dayOffset ->
            val epochDay = firstEpochDay + dayOffset
            totalsByEpochDay[epochDay] ?: CalendarDayUi(
                epochDay = epochDay,
                totalCents = 0L,
                transactionCount = 0,
            )
        }
    }

    /**
     * Returns a Monday-first (by default) grid. Nulls are blank leading/trailing cells;
     * real dates are epoch days, so callers can use the same local-date key as the repository.
     */
    fun buildMonthGrid(
        month: YearMonth,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): List<Long?> {
        val firstDate = month.atDay(1)
        val leadingBlanks = Math.floorMod(
            firstDate.dayOfWeek.value - firstDayOfWeek.value,
            7,
        )
        val occupiedCells = leadingBlanks + month.lengthOfMonth()
        val cellCount = ((occupiedCells + 6) / 7) * 7
        val cells = MutableList<Long?>(cellCount) { null }

        for (dayOfMonth in 1..month.lengthOfMonth()) {
            val index = leadingBlanks + dayOfMonth - 1
            cells[index] = LocalDate.of(month.year, month.month, dayOfMonth).toEpochDay()
        }
        return cells
    }
}
