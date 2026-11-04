package com.linjincheng.qingji.util

import java.time.LocalDate
import java.time.YearMonth

/**
 * 日期工具（契约 §5 冻结）：全部基于 java.time（minSdk 26 直接可用）。
 * 月度边界一律用日期运算推导，跨年正确，禁止只比较月份数字。
 */
object DateUtils {

    /** 今天的 epochDay。 */
    fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    /** 当月 1 日的 epochDay。 */
    fun monthStartEpochDay(year: Int, month: Int): Long =
        LocalDate.of(year, month, 1).toEpochDay()

    /** 次月 1 日的 epochDay；12 月 → 次年 1 月由 plusMonths 保证跨年正确。 */
    fun nextMonthStartEpochDay(year: Int, month: Int): Long =
        YearMonth.of(year, month).plusMonths(1).atDay(1).toEpochDay()

    /** 月份平移（delta 可正可负），返回 (年, 月)；跨年由 YearMonth 日期运算保证。 */
    fun adjustMonth(year: Int, month: Int, delta: Int): Pair<Int, Int> {
        val target = YearMonth.of(year, month).plusMonths(delta.toLong())
        return target.year to target.monthValue
    }

    /** 首页标题："2026 年 11 月"。 */
    fun monthTitle(year: Int, month: Int): String = "$year 年 $month 月"

    /** epochDay → "2026-11-04"（LocalDate 的 ISO-8601 标准输出，月/日补零）。 */
    fun formatDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()
}
