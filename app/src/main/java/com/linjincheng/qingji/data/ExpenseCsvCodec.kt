package com.linjincheng.qingji.data

import java.nio.charset.StandardCharsets
import java.time.LocalDate

/**
 * One-way, offline report for the Android system's CreateDocument picker.
 *
 * CSV is NOT a restorable backup: it deliberately excludes local row IDs,
 * backup UUIDs, device identifiers and app preferences. Use JSON backup to
 * migrate data. UTF-8 BOM helps Windows Excel recognize Chinese correctly.
 */
object ExpenseCsvCodec {
    const val MIME_TYPE = "text/csv"
    private const val MAX_ROWS = 100_000
    private const val MAX_NOTE_CHARS = 10_000

    fun encode(expenses: List<ExpenseEntry>): ByteArray {
        require(expenses.size <= MAX_ROWS) { "账单数量超过单次导出上限" }
        val rows = expenses.sortedWith(
            compareByDescending<ExpenseEntry> { it.expenseDate }
                .thenByDescending { it.createdAt }
                .thenByDescending { it.id },
        )
        val output = StringBuilder("\uFEFF日期,分类,金额(元),备注\r\n")
        for (expense in rows) {
            require(expense.amountCents > 0) { "账单包含不合法的金额" }
            require(expense.note.length <= MAX_NOTE_CHARS) { "账单备注超出安全导出长度" }
            // Database is epoch-day / integer cents; no floating point math.
            val day = LocalDate.ofEpochDay(expense.expenseDate).toString()
            val amount = "${expense.amountCents / 100}.${(expense.amountCents % 100).toString().padStart(2, '0')}"
            output.append(csvCell(day)).append(',')
                .append(csvCell(expense.category.label)).append(',')
                .append(amount).append(',')
                .append(csvCell(preventFormulaExecution(expense.note)))
                .append("\r\n")
        }
        return output.toString().toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * Spreadsheet software may evaluate cells beginning with =, +, -, @,
     * including when preceded by spaces/tabs/newlines. A leading apostrophe
     * keeps user-authored notes as TEXT. Always quote every text field.
     */
    internal fun preventFormulaExecution(value: String): String {
        val leading = value.firstOrNull()
        val trimmed = value.dropWhile { it == ' ' || it == '\t' || it == '\r' || it == '\n' }
        val first = trimmed.firstOrNull()
        val suspect = leading == '\t' || leading == '\r' || leading == '\n' ||
            first == '=' || first == '+' || first == '-' || first == '@'
        return if (suspect) "'$value" else value
    }

    private fun csvCell(value: String): String = "\""+value.replace("\"", "\"\"")+"\""
}
