package com.linjincheng.qingji.export

import com.linjincheng.qingji.data.ExpenseEntry
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate

/**
 * Lossless, offline CSV snapshot of the SQLite expense ledger.
 *
 * - Amounts are represented as integer cents throughout; no Float/Double.
 * - Dates and timestamps use explicit ISO-8601 / UTC to avoid device locale drift.
 * - Every cell is quoted; user notes beginning with spreadsheet formula trigger
 *   characters are prefixed as text to mitigate CSV formula injection.
 * - UTF-8 BOM improves compatibility when opening Chinese exports in Excel.
 * - Export does not alter stored expenses or application preferences.
 */
object ExpenseCsvExporter {
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private const val HEADER = "日期,分类,分类代码,金额(元),备注,记录ID,创建时间(UTC),更新时间(UTC)"

    /** Exact decimal currency conversion without floating point rounding. */
    fun centsToYuan(cents: Long): String {
        require(cents > 0L) { "账单金额必须大于零" }
        return (cents / 100L).toString() + "." +
            (cents % 100L).toString().padStart(2, '0')
    }

    /** Prevent Excel/Calc interpreting personal notes as formulas. */
    fun safeCell(value: String): String {
        val first = value.trimStart().firstOrNull()
        val valueAsText = if (first == '=' || first == '+' ||
            first == '-' || first == '@' || first == '\t' ||
            first == '\r' || first == '\n') {
            "'" + value
        } else {
            value
        }
        return "\"" + valueAsText.replace("\"", "\"\"") + "\""
    }

    /** Deterministic, ready for round-trip parsing by RFC 4180-compatible CSV readers. */
    fun buildCsv(entries: List<ExpenseEntry>): String = buildString {
        append(HEADER).append("\r\n")
        for (entry in entries) {
            val cells = listOf(
                LocalDate.ofEpochDay(entry.expenseDate).toString(),
                entry.category.label,
                entry.category.code,
                centsToYuan(entry.amountCents),
                entry.note,
                entry.id.toString(),
                Instant.ofEpochMilli(entry.createdAt).toString(),
                Instant.ofEpochMilli(entry.updatedAt).toString(),
            )
            append(cells.joinToString(",") { safeCell(it) })
            append("\r\n")
        }
    }

    fun toUtf8Csv(entries: List<ExpenseEntry>): ByteArray =
        UTF8_BOM + buildCsv(entries).toByteArray(StandardCharsets.UTF_8)
}
