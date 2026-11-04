package com.linjincheng.qingji

import com.linjincheng.qingji.util.DateUtils
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DateUtils 单元测试。
 * 覆盖：月份边界 [当月首日, 次月首日)（2026-12→2027-01 跨年、2026-01 上月→2025-12 跨年、
 * 闰年 2024-02→2024-03 与平年对照）、adjustMonth 跨年、monthTitle/formatDate 展示格式。
 *
 * 【未编译执行，待集成阶段验证】C2 并行阶段无 Gradle 编译环境，
 * 本文件尚未编译与运行；测试结论以集成阶段真实输出为准。
 */
class DateUtilsTest {

    // ---------- 月份首日 / 次月首日（月度查询边界） ----------

    @Test
    fun monthStartOfNovember2026() {
        assertEquals(LocalDate.of(2026, 11, 1).toEpochDay(), DateUtils.monthStartEpochDay(2026, 11))
    }

    @Test
    fun nextMonthStartCrossesYear2026To2027() {
        // 2026-12 的「次月首日」= 2027-01-01，跨年正确
        assertEquals(LocalDate.of(2027, 1, 1).toEpochDay(), DateUtils.nextMonthStartEpochDay(2026, 12))
    }

    @Test
    fun january2027StartEqualsNextMonthStartOfDecember2026() {
        assertEquals(
            DateUtils.nextMonthStartEpochDay(2026, 12),
            DateUtils.monthStartEpochDay(2027, 1),
        )
    }

    @Test
    fun nextMonthStartOfJanuaryIsFebruary() {
        assertEquals(LocalDate.of(2026, 2, 1).toEpochDay(), DateUtils.nextMonthStartEpochDay(2026, 1))
    }

    @Test
    fun decemberToJanuaryIs31Days() {
        // 2025-12 → 2026-01 相邻月边界
        assertEquals(
            DateUtils.monthStartEpochDay(2025, 12) + 31L,
            DateUtils.monthStartEpochDay(2026, 1),
        )
    }

    // ---------- 闰年 2 月 ----------

    @Test
    fun leapYearFebruaryHas29Days() {
        // 闰年 2024-02 → 2024-03 边界
        assertEquals(
            29L,
            DateUtils.nextMonthStartEpochDay(2024, 2) - DateUtils.monthStartEpochDay(2024, 2),
        )
        assertEquals(LocalDate.of(2024, 3, 1).toEpochDay(), DateUtils.nextMonthStartEpochDay(2024, 2))
    }

    @Test
    fun nonLeapYearFebruaryHas28Days() {
        assertEquals(
            28L,
            DateUtils.nextMonthStartEpochDay(2026, 2) - DateUtils.monthStartEpochDay(2026, 2),
        )
    }

    // ---------- adjustMonth 跨年 ----------

    @Test
    fun adjustMonthDecemberForwardCrossesYear() {
        assertEquals(Pair(2027, 1), DateUtils.adjustMonth(2026, 12, 1))
    }

    @Test
    fun adjustMonthJanuaryBackwardCrossesYear() {
        // 2026-01 的上月 → 2025-12
        assertEquals(Pair(2025, 12), DateUtils.adjustMonth(2026, 1, -1))
    }

    @Test
    fun adjustMonthLeapFebruaryForward() {
        assertEquals(Pair(2024, 3), DateUtils.adjustMonth(2024, 2, 1))
    }

    @Test
    fun adjustMonthMultipleSteps() {
        assertEquals(Pair(2027, 3), DateUtils.adjustMonth(2026, 11, 4))
        assertEquals(Pair(2025, 12), DateUtils.adjustMonth(2026, 11, -11))
    }

    // ---------- 展示格式 ----------

    @Test
    fun monthTitleFormat() {
        assertEquals("2026 年 11 月", DateUtils.monthTitle(2026, 11))
        assertEquals("2026 年 1 月", DateUtils.monthTitle(2026, 1))
    }

    @Test
    fun formatDatePadsMonthAndDay() {
        assertEquals("2026-11-04", DateUtils.formatDate(LocalDate.of(2026, 11, 4).toEpochDay()))
        assertEquals("2027-01-01", DateUtils.formatDate(LocalDate.of(2027, 1, 1).toEpochDay()))
    }

    @Test
    fun todayMatchesLocalDateNow() {
        assertEquals(LocalDate.now().toEpochDay(), DateUtils.todayEpochDay())
    }
}
