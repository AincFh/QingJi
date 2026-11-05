package com.linjincheng.qingji

import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.data.ExpenseEntry
import com.linjincheng.qingji.util.ExpenseAnalytics
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseAnalyticsTest {

    @Test
    fun dailyTotalsKeepZeroDaysAcrossMonthAndYearBoundary() {
        val endDay = LocalDate.of(2026, 1, 3).toEpochDay()
        val startDay = LocalDate.of(2025, 12, 28).toEpochDay()
        val entries = listOf(
            entry(id = 1L, amountCents = 125L, day = startDay),
            entry(id = 2L, amountCents = 75L, day = LocalDate.of(2025, 12, 30).toEpochDay()),
            entry(id = 3L, amountCents = 300L, day = LocalDate.of(2026, 1, 2).toEpochDay()),
            entry(id = 4L, amountCents = 999L, day = startDay - 1L),
        )

        val totals = ExpenseAnalytics.dailyTotals(entries, endDay)

        assertEquals(7, totals.size)
        assertEquals((0L..6L).map { startDay + it }, totals.map { it.epochDay })
        assertEquals(listOf(125L, 0L, 75L, 0L, 0L, 300L, 0L), totals.map { it.totalCents })
        assertEquals(listOf(1, 0, 1, 0, 0, 1, 0), totals.map { it.transactionCount })
    }

    @Test
    fun dailyTotalsReturnSevenZeroValuesWhenThereAreNoEntries() {
        val endDay = LocalDate.of(2026, 11, 4).toEpochDay()

        val totals = ExpenseAnalytics.dailyTotals(emptyList(), endDay)

        assertEquals(7, totals.size)
        assertEquals(List(7) { 0L }, totals.map { it.totalCents })
        assertEquals(List(7) { 0 }, totals.map { it.transactionCount })
    }

    @Test
    fun dailyTotalsCountMultipleEntriesAndIgnoreDatesOutsideTheWindow() {
        val firstDay = LocalDate.of(2026, 6, 10).toEpochDay()
        val entries = listOf(
            entry(1L, 125L, firstDay),
            entry(2L, 75L, firstDay),
            entry(3L, 300L, firstDay + 1L),
            entry(4L, 999L, firstDay - 1L),
            entry(5L, 888L, firstDay + 3L),
        )

        val totals = ExpenseAnalytics.dailyTotals(entries, endEpochDay = firstDay + 2L, dayCount = 3)

        assertEquals(listOf(200L, 300L, 0L), totals.map { it.totalCents })
        assertEquals(listOf(2, 1, 0), totals.map { it.transactionCount })
    }

    @Test
    fun dailyTotalsSupportsThirtyDaysAndIncludesBothWindowEdges() {
        val firstDay = LocalDate.of(2026, 11, 1).toEpochDay()
        val endDay = LocalDate.of(2026, 11, 30).toEpochDay()
        val entries = listOf(
            entry(1L, 999L, firstDay - 1L),
            entry(2L, 120L, firstDay),
            entry(3L, 350L, LocalDate.of(2026, 11, 15).toEpochDay()),
            entry(4L, 480L, endDay),
            entry(5L, 888L, endDay + 1L),
        )

        val totals = ExpenseAnalytics.dailyTotals(entries, endDay, dayCount = 30)

        assertEquals(30, totals.size)
        assertEquals((0L..29L).map { firstDay + it }, totals.map { it.epochDay })
        assertEquals(
            List(30) { index ->
                when (index) {
                    0 -> 120L
                    14 -> 350L
                    29 -> 480L
                    else -> 0L
                }
            },
            totals.map { it.totalCents },
        )
        assertEquals(
            List(30) { index -> if (index == 0 || index == 14 || index == 29) 1 else 0 },
            totals.map { it.transactionCount },
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun dailyTotalsRejectsAnEmptyWindow() {
        ExpenseAnalytics.dailyTotals(emptyList(), LocalDate.of(2026, 11, 30).toEpochDay(), dayCount = 0)
    }

    @Test
    fun categoryPercentagesAlwaysSumToOneHundredUsingLargestRemainder() {
        val percentages = ExpenseAnalytics.categoryPercentages(
            mapOf(
                ExpenseCategory.FOOD to 21L,
                ExpenseCategory.TRANSPORT to 21L,
                ExpenseCategory.SHOPPING to 21L,
                ExpenseCategory.DAILY to 21L,
                ExpenseCategory.OTHER to 116L,
            ),
        )

        assertEquals(100, percentages.values.sum())
        assertEquals(11, percentages[ExpenseCategory.FOOD])
        assertEquals(11, percentages[ExpenseCategory.TRANSPORT])
        assertEquals(10, percentages[ExpenseCategory.SHOPPING])
        assertEquals(10, percentages[ExpenseCategory.DAILY])
        assertEquals(58, percentages[ExpenseCategory.OTHER])
    }

    @Test
    fun categoryPercentagesHandleZeroAndSinglePositiveCategory() {
        assertEquals(
            ExpenseCategory.entries.associateWith { 0 },
            ExpenseAnalytics.categoryPercentages(emptyMap()),
        )
        assertEquals(
            ExpenseCategory.entries.associateWith { if (it == ExpenseCategory.TRANSPORT) 100 else 0 },
            ExpenseAnalytics.categoryPercentages(mapOf(ExpenseCategory.TRANSPORT to 5_000L)),
        )
    }

    @Test
    fun searchMatchesNoteCategoryDateAndFormattedAmount() {
        val entries = sampleEntries()

        assertEquals(listOf(1L), idsFor(entries, searchText = "午餐"))
        assertEquals(listOf(1L), idsFor(entries, searchText = "餐饮"))
        assertEquals(listOf(2L), idsFor(entries, searchText = "2026-11-05"))
        assertEquals(listOf(2L), idsFor(entries, searchText = "12.00"))
        assertEquals(listOf(1L), idsFor(entries, searchText = "28.5"))
    }

    @Test
    fun categoryFilterCanBeCombinedWithSearchAndCleared() {
        val entries = sampleEntries()

        assertEquals(listOf(2L), idsFor(entries, categoryFilter = ExpenseCategory.TRANSPORT))
        assertEquals(
            listOf(1L),
            idsFor(entries, searchText = "28.50", categoryFilter = ExpenseCategory.FOOD),
        )
        assertEquals(
            emptyList<Long>(),
            idsFor(entries, searchText = "28.50", categoryFilter = ExpenseCategory.SHOPPING),
        )
        assertEquals(listOf(1L, 2L, 3L), idsFor(entries))
    }

    @Test
    fun monthCategoryAndTextFiltersAreCombinedAcrossYears() {
        val entries = listOf(
            entry(
                id = 1L,
                amountCents = 100L,
                day = LocalDate.of(2025, 12, 4).toEpochDay(),
                category = ExpenseCategory.FOOD,
                note = "夜宵",
            ),
            entry(
                id = 2L,
                amountCents = 200L,
                day = LocalDate.of(2026, 1, 5).toEpochDay(),
                category = ExpenseCategory.FOOD,
                note = "早餐",
            ),
            entry(
                id = 3L,
                amountCents = 300L,
                day = LocalDate.of(2025, 1, 6).toEpochDay(),
                category = ExpenseCategory.FOOD,
                note = "早餐",
            ),
            entry(
                id = 4L,
                amountCents = 400L,
                day = LocalDate.of(2025, 12, 7).toEpochDay(),
                category = ExpenseCategory.TRANSPORT,
                note = "车票",
            ),
            entry(
                id = 5L,
                amountCents = 500L,
                day = LocalDate.of(2026, 12, 8).toEpochDay(),
                category = ExpenseCategory.FOOD,
                note = "夜宵",
            ),
        )
        val december2025 = YearMonth.of(2025, 12)

        assertEquals(
            listOf(1L, 4L),
            idsFor(entries, monthFilter = december2025),
        )
        assertEquals(
            listOf(1L),
            idsFor(
                entries,
                searchText = "夜宵",
                categoryFilter = ExpenseCategory.FOOD,
                monthFilter = december2025,
            ),
        )
        assertEquals(
            emptyList<Long>(),
            idsFor(
                entries,
                searchText = "车票",
                categoryFilter = ExpenseCategory.FOOD,
                monthFilter = december2025,
            ),
        )
    }

    @Test
    fun availableMonthsAreUniqueAndSortedNewestFirst() {
        val entries = listOf(
            entry(1L, 100L, LocalDate.of(2025, 12, 4).toEpochDay()),
            entry(2L, 200L, LocalDate.of(2026, 1, 5).toEpochDay()),
            entry(3L, 300L, LocalDate.of(2025, 12, 20).toEpochDay()),
            entry(4L, 400L, LocalDate.of(2025, 1, 6).toEpochDay()),
        )

        assertEquals(
            listOf(YearMonth.of(2026, 1), YearMonth.of(2025, 12), YearMonth.of(2025, 1)),
            ExpenseAnalytics.availableMonths(entries),
        )
    }

    @Test
    fun availableMonthsReturnsEmptyListForNoEntries() {
        assertEquals(emptyList<YearMonth>(), ExpenseAnalytics.availableMonths(emptyList()))
    }

    @Test
    fun currentMonthComparisonUsesSameElapsedDaysAndExcludesFutureEntries() {
        val comparison = ExpenseAnalytics.compareMonths(
            currentEntries = listOf(
                entry(1L, 100L, LocalDate.of(2026, 11, 1).toEpochDay()),
                entry(2L, 200L, LocalDate.of(2026, 11, 5).toEpochDay()),
                entry(3L, 500L, LocalDate.of(2026, 11, 6).toEpochDay()),
                entry(4L, 900L, LocalDate.of(2026, 10, 31).toEpochDay()),
            ),
            previousEntries = listOf(
                entry(5L, 50L, LocalDate.of(2026, 10, 1).toEpochDay()),
                entry(6L, 25L, LocalDate.of(2026, 10, 5).toEpochDay()),
                entry(7L, 900L, LocalDate.of(2026, 10, 6).toEpochDay()),
                entry(8L, 1_000L, LocalDate.of(2026, 9, 30).toEpochDay()),
            ),
            selectedMonth = YearMonth.of(2026, 11),
            today = LocalDate.of(2026, 11, 5),
        )

        assertEquals(300L, comparison.currentCents)
        assertEquals(75L, comparison.previousCents)
        assertEquals("与上月同期", comparison.label)
    }

    @Test
    fun currentMonthComparisonClampsPreviousPeriodAtMonthEnd() {
        val comparison = ExpenseAnalytics.compareMonths(
            currentEntries = listOf(
                entry(1L, 300L, LocalDate.of(2026, 3, 31).toEpochDay()),
            ),
            previousEntries = listOf(
                entry(2L, 250L, LocalDate.of(2026, 2, 28).toEpochDay()),
                entry(3L, 100L, LocalDate.of(2026, 2, 27).toEpochDay()),
                entry(4L, 999L, LocalDate.of(2026, 1, 31).toEpochDay()),
            ),
            selectedMonth = YearMonth.of(2026, 3),
            today = LocalDate.of(2026, 3, 31),
        )

        assertEquals(300L, comparison.currentCents)
        assertEquals(350L, comparison.previousCents)
    }

    @Test
    fun historicalMonthComparisonUsesBothCompleteMonthRanges() {
        val comparison = ExpenseAnalytics.compareMonths(
            currentEntries = listOf(
                entry(1L, 400L, LocalDate.of(2026, 5, 5).toEpochDay()),
                entry(2L, 600L, LocalDate.of(2026, 5, 31).toEpochDay()),
                entry(3L, 300L, LocalDate.of(2026, 4, 1).toEpochDay()),
            ),
            previousEntries = listOf(
                entry(4L, 100L, LocalDate.of(2026, 4, 1).toEpochDay()),
                entry(5L, 250L, LocalDate.of(2026, 4, 30).toEpochDay()),
                entry(6L, 800L, LocalDate.of(2026, 3, 31).toEpochDay()),
            ),
            selectedMonth = YearMonth.of(2026, 5),
            today = LocalDate.of(2026, 8, 10),
        )

        assertEquals(1_000L, comparison.currentCents)
        assertEquals(350L, comparison.previousCents)
        assertEquals("较上月", comparison.label)
    }

    @Test
    fun searchMatchesLocalizedYearMonthAndDayDateForms() {
        val entries = sampleEntries()

        assertEquals(listOf(2L), idsFor(entries, searchText = "2026年11月5日"))
        assertEquals(listOf(2L), idsFor(entries, searchText = "11月5日"))
        assertEquals(listOf(1L, 2L, 3L), idsFor(entries, searchText = "2026年11月"))
    }

    private fun idsFor(
        entries: List<ExpenseEntry>,
        searchText: String = "",
        categoryFilter: ExpenseCategory? = null,
        monthFilter: YearMonth? = null,
    ): List<Long> = ExpenseAnalytics.filterEntries(
        entries,
        searchText,
        categoryFilter,
        monthFilter,
    ).map { it.id }

    private fun sampleEntries(): List<ExpenseEntry> = listOf(
        entry(
            id = 1L,
            amountCents = 2_850L,
            day = LocalDate.of(2026, 11, 4).toEpochDay(),
            category = ExpenseCategory.FOOD,
            note = "午餐",
        ),
        entry(
            id = 2L,
            amountCents = 1_200L,
            day = LocalDate.of(2026, 11, 5).toEpochDay(),
            category = ExpenseCategory.TRANSPORT,
            note = "地铁",
        ),
        entry(
            id = 3L,
            amountCents = 6_500L,
            day = LocalDate.of(2026, 11, 6).toEpochDay(),
            category = ExpenseCategory.SHOPPING,
            note = "收纳用品",
        ),
    )

    private fun entry(
        id: Long,
        amountCents: Long,
        day: Long,
        category: ExpenseCategory = ExpenseCategory.FOOD,
        note: String = "",
    ): ExpenseEntry = ExpenseEntry(
        id = id,
        amountCents = amountCents,
        category = category,
        note = note,
        expenseDate = day,
        createdAt = id,
        updatedAt = id,
    )
}
