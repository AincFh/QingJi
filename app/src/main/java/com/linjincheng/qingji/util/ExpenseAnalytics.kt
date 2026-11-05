package com.linjincheng.qingji.util

import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.data.ExpenseEntry
import java.math.BigInteger
import java.util.Locale
import java.time.LocalDate
import java.time.YearMonth

/** 单日趋势点；epochDay 使用 LocalDate.toEpochDay()，金额单位为分。 */
data class DailyExpenseTotal(
    val epochDay: Long,
    val totalCents: Long,
    val transactionCount: Int = 0,
)

data class MonthComparison(
    val currentCents: Long,
    val previousCents: Long,
    val label: String,
)

/** 纯内存的流水趋势与筛选逻辑；金额全程以 Long 分为单位处理。 */
object ExpenseAnalytics {

    const val TREND_DAY_COUNT = 7

    /** 用最大余数法分配整数百分比，确保所有正金额分类加总恰好为 100%。 */
    fun categoryPercentages(categoryTotals: Map<ExpenseCategory, Long>): Map<ExpenseCategory, Int> {
        val positive = ExpenseCategory.entries.mapNotNull { category ->
            val cents = categoryTotals[category] ?: return@mapNotNull null
            if (cents <= 0L) return@mapNotNull null
            category to BigInteger.valueOf(cents)
        }
        if (positive.isEmpty()) return ExpenseCategory.entries.associateWith { 0 }

        val denominator = positive.fold(BigInteger.ZERO) { total, (_, cents) -> total + cents }
        val hundred = BigInteger.valueOf(100L)
        val allocated = positive.map { (category, cents) ->
            val (whole, remainder) = cents.multiply(hundred).divideAndRemainder(denominator)
            CategoryShare(category, whole.toInt(), remainder)
        }
        val remainingPoints = 100 - allocated.sumOf(CategoryShare::wholePercent)
        val roundedUp = allocated
            .sortedWith(compareByDescending<CategoryShare> { it.remainder }.thenBy { it.category.ordinal })
            .take(remainingPoints)
            .mapTo(HashSet()) { it.category }
        val percentages = allocated.associate { share ->
            share.category to (share.wholePercent + if (share.category in roundedUp) 1 else 0)
        }
        return ExpenseCategory.entries.associateWith { percentages[it] ?: 0 }
    }

    private data class CategoryShare(
        val category: ExpenseCategory,
        val wholePercent: Int,
        val remainder: BigInteger,
    )

    /**
     * 当前月按截至今天与上月相同日期比较，避免月中拿半个月支出与上月整月误比；
     * 历史月份按两个完整月份比较。若上月没有对应日（如 3 月 31 日），取上月末日。
     */
    fun compareMonths(
        currentEntries: Iterable<ExpenseEntry>,
        previousEntries: Iterable<ExpenseEntry>,
        selectedMonth: YearMonth,
        today: LocalDate,
    ): MonthComparison {
        val currentStart = selectedMonth.atDay(1).toEpochDay()
        val isCurrentMonth = selectedMonth == YearMonth.from(today)
        if (!isCurrentMonth) {
            val currentEnd = selectedMonth.atEndOfMonth().toEpochDay()
            val previousMonth = selectedMonth.minusMonths(1)
            val previousStart = previousMonth.atDay(1).toEpochDay()
            val previousEnd = previousMonth.atEndOfMonth().toEpochDay()
            return MonthComparison(
                currentCents = currentEntries
                    .asSequence()
                    .filter { it.expenseDate in currentStart..currentEnd }
                    .sumOf { it.amountCents },
                previousCents = previousEntries
                    .asSequence()
                    .filter { it.expenseDate in previousStart..previousEnd }
                    .sumOf { it.amountCents },
                label = "较上月",
            )
        }

        val currentEnd = today.toEpochDay()
        val previousMonth = selectedMonth.minusMonths(1)
        val previousDay = minOf(today.dayOfMonth, previousMonth.lengthOfMonth())
        val previousEnd = previousMonth.atDay(previousDay).toEpochDay()
        val previousStart = previousMonth.atDay(1).toEpochDay()
        return MonthComparison(
            currentCents = currentEntries
                .asSequence()
                .filter { it.expenseDate in currentStart..currentEnd }
                .sumOf { it.amountCents },
            previousCents = previousEntries
                .asSequence()
                .filter { it.expenseDate in previousStart..previousEnd }
                .sumOf { it.amountCents },
            label = "与上月同期",
        )
    }

    /**
     * 汇总以 endEpochDay 为末日的连续 dayCount 天（默认 7 天）。没有流水的日期会返回 0，
     * 窗口边界外的记录会被忽略。
     */
    fun dailyTotals(
        entries: Iterable<ExpenseEntry>,
        endEpochDay: Long,
        dayCount: Int = TREND_DAY_COUNT,
    ): List<DailyExpenseTotal> {
        require(dayCount > 0) { "dayCount must be positive" }
        val firstEpochDay = endEpochDay - (dayCount - 1).toLong()
        val totals = LongArray(dayCount)
        val transactionCounts = IntArray(dayCount)
        entries.forEach { entry ->
            val offset = entry.expenseDate - firstEpochDay
            if (offset in 0L until dayCount.toLong()) {
                totals[offset.toInt()] += entry.amountCents
                transactionCounts[offset.toInt()]++
            }
        }
        return (0 until dayCount).map { index ->
            DailyExpenseTotal(
                epochDay = firstEpochDay + index,
                totalCents = totals[index],
                transactionCount = transactionCounts[index],
            )
        }
    }

    /** 以 AND 关系应用单分类筛选与自由文本搜索，保留仓储提供的排序。 */
    fun filterEntries(
        entries: List<ExpenseEntry>,
        searchText: String,
        categoryFilter: ExpenseCategory?,
        monthFilter: YearMonth? = null,
    ): List<ExpenseEntry> {
        val query = searchText.trim().lowercase(Locale.ROOT)
        return entries.filter { entry ->
            (categoryFilter == null || entry.category == categoryFilter) &&
                (monthFilter == null || YearMonth.from(LocalDate.ofEpochDay(entry.expenseDate)) == monthFilter) &&
                (query.isEmpty() || matchesSearch(entry, query))
        }
    }

    /** 全部记录中实际出现过的月份，按新到旧排列，供账单页组合筛选。 */
    fun availableMonths(entries: Iterable<ExpenseEntry>): List<YearMonth> =
        entries.asSequence()
            .map { YearMonth.from(LocalDate.ofEpochDay(it.expenseDate)) }
            .distinct()
            .sortedDescending()
            .toList()

    private fun matchesSearch(entry: ExpenseEntry, query: String): Boolean {
        val amount = MoneyUtils.formatCents(entry.amountCents)
        val date = LocalDate.ofEpochDay(entry.expenseDate)
        val searchableDates = listOf(
            DateUtils.formatDate(entry.expenseDate),
            "${date.year}年${date.monthValue}月${date.dayOfMonth}日",
            "${date.monthValue}月${date.dayOfMonth}日",
            "${date.year}年${date.monthValue}月",
            "${date.monthValue}月",
        )
        return entry.note.contains(query, ignoreCase = true) ||
            entry.category.label.contains(query, ignoreCase = true) ||
            searchableDates.any { it.contains(query, ignoreCase = true) } ||
            amount.contains(query) ||
            "¥$amount".contains(query)
    }
}
