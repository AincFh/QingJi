package com.linjincheng.qingji

import com.linjincheng.qingji.util.AmountParseResult
import com.linjincheng.qingji.util.MoneyUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MoneyUtils 单元测试（JUnit4，依赖仅契约白名单 junit:junit:4.13.2）。
 * 覆盖：金额解析全部分支（EMPTY / NOT_NUMBER / NOT_POSITIVE / TOO_MANY_DECIMALS / OUT_OF_RANGE / 合法值）
 * 与 formatCents（含 0.01、62、999999.99）。
 *
 * 【未编译执行，待集成阶段验证】C2 并行阶段无 Gradle 编译环境，
 * 本文件尚未编译与运行；测试结论以集成阶段真实输出为准。
 */
class MoneyUtilsTest {

    // ---------- 解析：合法输入 ----------

    @Test
    fun parseIntegerYuan() {
        assertEquals(2800L, validCents("28"))
    }

    @Test
    fun parseTrimsWhitespace() {
        assertEquals(2850L, validCents("  28.50  "))
    }

    @Test
    fun parseOneDecimal() {
        assertEquals(2850L, validCents("28.5"))
    }

    @Test
    fun parseLowerBoundOneCent() {
        assertEquals(1L, validCents("0.01"))
    }

    @Test
    fun parseUpperBound() {
        assertEquals(99999999L, validCents("999999.99"))
    }

    @Test
    fun parseLeadingZeros() {
        assertEquals(700L, validCents("007"))
        assertEquals(2805L, validCents("028.05"))
    }

    @Test
    fun parseTypicalLargeValue() {
        assertEquals(12345678L, validCents("123456.78"))
    }

    // ---------- 解析：EMPTY ----------

    @Test
    fun emptyStringIsEmpty() {
        assertEquals("EMPTY", invalidReason(""))
    }

    @Test
    fun blankStringIsEmpty() {
        assertEquals("EMPTY", invalidReason("   "))
    }

    // ---------- 解析：NOT_NUMBER ----------

    @Test
    fun lettersAreNotNumber() {
        assertEquals("NOT_NUMBER", invalidReason("abc"))
    }

    @Test
    fun twoDotsAreNotNumber() {
        assertEquals("NOT_NUMBER", invalidReason("12.3.4"))
    }

    @Test
    fun missingIntegerPartIsNotNumber() {
        assertEquals("NOT_NUMBER", invalidReason(".5"))
    }

    @Test
    fun trailingDotIsNotNumber() {
        assertEquals("NOT_NUMBER", invalidReason("28."))
    }

    @Test
    fun scientificNotationIsNotNumber() {
        assertEquals("NOT_NUMBER", invalidReason("1e5"))
    }

    @Test
    fun commaDecimalIsNotNumber() {
        assertEquals("NOT_NUMBER", invalidReason("28,50"))
    }

    // ---------- 解析：NOT_POSITIVE（0 或负数；按契约顺序先于小数位判定） ----------

    @Test
    fun zeroIsNotPositive() {
        assertEquals("NOT_POSITIVE", invalidReason("0"))
    }

    @Test
    fun zeroWithDecimalsIsNotPositive() {
        assertEquals("NOT_POSITIVE", invalidReason("0.00"))
    }

    @Test
    fun allZeroDigitsIsNotPositive() {
        assertEquals("NOT_POSITIVE", invalidReason("000"))
    }

    @Test
    fun negativeIntegerIsNotPositive() {
        assertEquals("NOT_POSITIVE", invalidReason("-3"))
    }

    @Test
    fun negativeDecimalIsNotPositive() {
        assertEquals("NOT_POSITIVE", invalidReason("-28.50"))
    }

    @Test
    fun negativeSubCentIsNotPositive() {
        assertEquals("NOT_POSITIVE", invalidReason("-0.01"))
    }

    // ---------- 解析：TOO_MANY_DECIMALS ----------

    @Test
    fun threeDecimalsTooMany() {
        assertEquals("TOO_MANY_DECIMALS", invalidReason("1.234"))
    }

    @Test
    fun longDecimalsTooMany() {
        assertEquals("TOO_MANY_DECIMALS", invalidReason("28.505"))
    }

    @Test
    fun subCentValueTooMany() {
        // 0.001 数值大于 0，因此先过 NOT_POSITIVE 判定，再判小数位
        assertEquals("TOO_MANY_DECIMALS", invalidReason("0.001"))
    }

    // ---------- 解析：OUT_OF_RANGE ----------

    @Test
    fun oneMillionYuanOutOfRange() {
        assertEquals("OUT_OF_RANGE", invalidReason("1000000"))
    }

    @Test
    fun oneMillionYuanWithCentsOutOfRange() {
        assertEquals("OUT_OF_RANGE", invalidReason("1000000.00"))
    }

    // ---------- formatCents ----------

    @Test
    fun formatOneCent() {
        assertEquals("0.01", MoneyUtils.formatCents(1L))
    }

    @Test
    fun formatSixtyTwoYuan() {
        assertEquals("62.00", MoneyUtils.formatCents(6200L))
    }

    @Test
    fun formatUpperBound() {
        assertEquals("999999.99", MoneyUtils.formatCents(99999999L))
    }

    @Test
    fun formatZero() {
        assertEquals("0.00", MoneyUtils.formatCents(0L))
    }

    @Test
    fun formatKeepsTwoDecimals() {
        assertEquals("28.50", MoneyUtils.formatCents(2850L))
        assertEquals("0.05", MoneyUtils.formatCents(5L))
        assertEquals("999999.90", MoneyUtils.formatCents(99999990L))
    }

    // ---------- 助手 ----------

    private fun validCents(text: String): Long {
        val result = MoneyUtils.parseAmountToCents(text)
        assertTrue("期望 Valid，实际 $result", result is AmountParseResult.Valid)
        return (result as AmountParseResult.Valid).cents
    }

    private fun invalidReason(text: String): String {
        val result = MoneyUtils.parseAmountToCents(text)
        assertTrue("期望 Invalid，实际 $result", result is AmountParseResult.Invalid)
        return (result as AmountParseResult.Invalid).reason
    }
}
