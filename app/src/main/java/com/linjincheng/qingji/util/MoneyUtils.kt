package com.linjincheng.qingji.util

/**
 * 金额解析结果（契约 §5 冻结）。
 * reason 稳定键取值："EMPTY" | "NOT_NUMBER" | "NOT_POSITIVE" | "TOO_MANY_DECIMALS" | "OUT_OF_RANGE"。
 */
sealed interface AmountParseResult {
    /** 解析成功：cents 为金额（单位分），恒在 1..99999999。 */
    data class Valid(val cents: Long) : AmountParseResult

    /** 解析失败：reason 为稳定错误键，供界面映射中文提示。 */
    data class Invalid(val reason: String) : AmountParseResult
}

/**
 * 金额工具：全部基于整数分运算，严禁 Float/Double。
 * 有效区间 0.01–999999.99 元，即 1..99999999 分。
 */
object MoneyUtils {

    /** 稳定原因键（私有常量，值与契约 §5 完全一致）。 */
    private const val REASON_EMPTY = "EMPTY"
    private const val REASON_NOT_NUMBER = "NOT_NUMBER"
    private const val REASON_NOT_POSITIVE = "NOT_POSITIVE"
    private const val REASON_TOO_MANY_DECIMALS = "TOO_MANY_DECIMALS"
    private const val REASON_OUT_OF_RANGE = "OUT_OF_RANGE"

    /** 单笔金额上限：999999.99 元 = 99999999 分。 */
    const val MAX_CENTS = 99_999_999L

    /** 允许的金额文本形态：可选负号 + 纯数字 + 可选小数部分（matches() 全串匹配，无需锚点）。 */
    private val AMOUNT_REGEX = Regex("""-?\d+(\.\d+)?""")

    /**
     * 元文本 → 分（契约 §5 规则，按序判定）：
     * 先 trim；"28"→2800；"28.50"→2850；""→EMPTY；
     * 0/负数→NOT_POSITIVE；小数超两位→TOO_MANY_DECIMALS；
     * 有效但不在 1..99999999 分（即 0.01–999999.99 元）→OUT_OF_RANGE。
     */
    fun parseAmountToCents(text: String): AmountParseResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return AmountParseResult.Invalid(REASON_EMPTY)
        if (!AMOUNT_REGEX.matches(trimmed)) return AmountParseResult.Invalid(REASON_NOT_NUMBER)

        val negative = trimmed.startsWith("-")
        val unsigned = trimmed.removePrefix("-")
        // 无小数点时整数部分=整个无符号串（missingDelimiterValue 传 unsigned，不能传 ""）
        val intPart = if ('.' in unsigned) unsigned.substringBefore('.') else unsigned
        val fracPart = if ('.' in unsigned) unsigned.substringAfter('.') else ""

        // 0 或负数 → NOT_POSITIVE（按契约列出顺序，先于小数位判定）
        val hasNonZeroDigit = (intPart + fracPart).any { it != '0' }
        if (negative || !hasNonZeroDigit) return AmountParseResult.Invalid(REASON_NOT_POSITIVE)

        // 小数超过两位 → TOO_MANY_DECIMALS
        if (fracPart.length > 2) return AmountParseResult.Invalid(REASON_TOO_MANY_DECIMALS)

        // 整数部分超过 999999 元必然越界，先拦截以免大数转 Long 出错
        val intDigits = intPart.trimStart('0').ifEmpty { "0" }
        if (intDigits.length > 6) return AmountParseResult.Invalid(REASON_OUT_OF_RANGE)

        val intCents = intDigits.toLong() * 100L
        val fracCents = if (fracPart.isEmpty()) 0L else fracPart.padEnd(2, '0').toLong()
        val cents = intCents + fracCents
        if (cents < 1L || cents > MAX_CENTS) return AmountParseResult.Invalid(REASON_OUT_OF_RANGE)
        return AmountParseResult.Valid(cents)
    }

    /** 分 → 元文本：6200 → "62.00"，1 → "0.01"，不加千分位。负数分支仅为健壮性兜底。 */
    fun formatCents(cents: Long): String {
        val negative = cents < 0L
        val absolute = if (negative) -cents else cents
        val yuan = absolute / 100L
        val rem = absolute % 100L
        val text = "$yuan.${rem.toString().padStart(2, '0')}"
        return if (negative) "-$text" else text
    }
}
