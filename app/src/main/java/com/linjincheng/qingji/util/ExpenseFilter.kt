package com.linjincheng.qingji.util

import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.data.ExpenseEntry
import java.time.LocalDate
import java.time.YearMonth

/** Epoch-day interval with an inclusive start and exclusive end. */
data class EpochDayRange(val startInclusive: Long, val endExclusive: Long) {
    init {
        require(startInclusive < endExclusive) { "日期范围必须至少包含一天" }
    }

    fun contains(epochDay: Long): Boolean = epochDay >= startInclusive && epochDay < endExclusive

    fun asYearMonthOrNull(): YearMonth? {
        val start = LocalDate.ofEpochDay(startInclusive)
        val end = LocalDate.ofEpochDay(endExclusive)
        if (start.dayOfMonth != 1 || end != start.plusMonths(1)) return null
        return YearMonth.from(start)
    }

    fun label(): String {
        val start = LocalDate.ofEpochDay(startInclusive)
        val end = LocalDate.ofEpochDay(endExclusive)
        val month = asYearMonthOrNull()
        return when {
            month != null -> "${month.year}年${month.monthValue}月"
            end == start.plusDays(1) -> "${start.monthValue}月${start.dayOfMonth}日"
            else -> "${start.monthValue}月${start.dayOfMonth}日—${end.minusDays(1).monthValue}月${end.minusDays(1).dayOfMonth}日"
        }
    }

    companion object {
        fun day(epochDay: Long): EpochDayRange = EpochDayRange(epochDay, epochDay + 1L)

        fun month(month: YearMonth): EpochDayRange = EpochDayRange(
            startInclusive = month.atDay(1).toEpochDay(),
            endExclusive = month.plusMonths(1).atDay(1).toEpochDay(),
        )
    }
}

/** One shared AND-combined filter for charts, calendar and ledger navigation. */
data class ExpenseFilter(
    val dateRange: EpochDayRange? = null,
    val category: ExpenseCategory? = null,
    val keyword: String = "",
) {
    val isEmpty: Boolean
        get() = dateRange == null && category == null && keyword.isBlank()

    fun apply(entries: List<ExpenseEntry>): List<ExpenseEntry> {
        val dateMatched = dateRange?.let { range -> entries.filter { range.contains(it.expenseDate) } } ?: entries
        return ExpenseAnalytics.filterEntries(
            entries = dateMatched,
            searchText = keyword,
            categoryFilter = category,
        )
    }
}
