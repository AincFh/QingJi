package com.linjincheng.qingji

import com.linjincheng.qingji.data.ExpenseBackupCodec
import com.linjincheng.qingji.data.ExpenseBackupPayload
import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.data.ExpenseEntry
import com.linjincheng.qingji.data.MonthlyBudget
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseBackupCodecTest {
    @Test
    fun v1RoundTripPreservesBackupDataAndOmitsLocalIdentityAndPreferences() {
        val expense = expense(id = 27L, note = "工作午餐")
        val budgets = listOf(
            MonthlyBudget(2026, 11, 0L, createdAt = 10L, updatedAt = 11L),
            MonthlyBudget(2026, 12, 99_999_999L, createdAt = 12L, updatedAt = 13L),
        )
        val exportedAt = Instant.parse("2026-11-05T08:30:00Z")

        val bytes = ExpenseBackupCodec.encode(listOf(expense), budgets, "6.0.0", exportedAt)
        val decoded = ExpenseBackupCodec.decode(bytes)

        assertEquals(exportedAt, decoded.exportedAt)
        assertEquals("6.0.0", decoded.appVersion)
        assertEquals(listOf(expense.copy(id = 0L)), decoded.expenses)
        assertEquals(budgets, decoded.budgets)
        val json = bytes.toString(Charsets.UTF_8)
        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertTrue(json.contains("\"exportedAt\""))
        assertTrue(json.contains("\"expenses\""))
        assertTrue(json.contains("\"budgets\""))
        assertFalse(json.contains("\"id\""))
        assertFalse(json.contains("theme"))
        assertFalse(json.contains("privacy"))
        assertFalse(json.contains("device"))
    }

    @Test
    fun previewCountsStableIdsAndListsBudgetMonthsToOverwrite() {
        val second = expense(backupId = "550e8400-e29b-41d4-a716-446655440001")
        val payload = ExpenseBackupPayload(
            exportedAt = Instant.parse(EXPORTED_AT),
            appVersion = "6.0",
            expenses = listOf(expense(), second),
            budgets = listOf(MonthlyBudget(2026, 11, 0L, 0L, 0L), MonthlyBudget(2027, 1, 5_000L, 0L, 0L)),
        )

        val preview = payload.preview(setOf(expense().backupId))

        assertEquals(1, preview.newExpenses)
        assertEquals(1, preview.skippedExpenses)
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2027, 1)), preview.budgetMonths)
    }

    @Test
    fun decoderRejectsUnknownSchemaMissingAndUnknownFieldsAndTrailingContent() {
        assertRejected(validJson().replace("\"schemaVersion\":1", "\"schemaVersion\":2"))
        assertRejected(validJson().replace("\"appVersion\":\"6.0\",", ""))
        assertRejected(validJson().replace("\"schemaVersion\":1,", "\"schemaVersion\":1,\"extra\":true,"))
        assertRejected(validJson() + " {}")
        assertRejected(validJson().replace("\"amountCents\":2850", "\"amountCents\":2850,\"amountCents\":1"))
    }

    @Test
    fun decoderRejectsInvalidExpenseFieldsAndRanges() {
        assertRejected(validJson().replace("\"backupId\":\"$UUID\"", "\"backupId\":\"not-a-uuid\""))
        assertRejected(validJson().replace("\"category\":\"FOOD\"", "\"category\":\"UNKNOWN\""))
        assertRejected(validJson().replace("\"amountCents\":2850", "\"amountCents\":0"))
        assertRejected(validJson().replace("\"amountCents\":2850", "\"amountCents\":100000000"))
        assertRejected(validJson().replace("\"amountCents\":2850", "\"amountCents\":28.5"))
        assertRejected(validJson().replace("\"expenseDate\":20000", "\"expenseDate\":9223372036854775807"))
        assertRejected(validJson().replace("\"createdAt\":10", "\"createdAt\":-1"))
        val longNote = "x".repeat(121)
        assertRejected(validJson().replace("\"note\":\"午餐\"", "\"note\":\"$longNote\""))
        assertRejected(validJson(expenses = "$EXPENSE,$EXPENSE"))
    }

    @Test
    fun decoderRejectsInvalidAndDuplicateBudgets() {
        assertRejected(validJson(budget = BUDGET.replace("\"year\":2026", "\"year\":0")))
        assertRejected(validJson(budget = BUDGET.replace("\"month\":11", "\"month\":13")))
        assertRejected(validJson(budget = BUDGET.replace("\"amountCents\":5000", "\"amountCents\":-1")))
        assertRejected(validJson(budget = BUDGET.replace("\"amountCents\":5000", "\"amountCents\":100000000")))
        assertRejected(validJson(budgets = "$BUDGET,$BUDGET"))
    }

    @Test
    fun fileSizeUtf8AndStreamLimitsAreCheckedBeforeParsing() {
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseBackupCodec.decode(ByteArray(ExpenseBackupCodec.MAX_BACKUP_BYTES + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseBackupCodec.decode(byteArrayOf(0xC3.toByte(), 0x28))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseBackupCodec.decode(ByteArrayInputStream(ByteArray(ExpenseBackupCodec.MAX_BACKUP_BYTES + 1)))
        }
    }

    private fun assertRejected(json: String) {
        assertThrows(IllegalArgumentException::class.java) { ExpenseBackupCodec.decode(json) }
    }

    private fun expense(
        id: Long = 0L,
        note: String = "午餐",
        backupId: String = UUID,
    ) = ExpenseEntry(
        id = id,
        amountCents = 2_850L,
        category = ExpenseCategory.FOOD,
        note = note,
        expenseDate = LocalDate.of(2026, 11, 5).toEpochDay(),
        createdAt = 10L,
        updatedAt = 20L,
        backupId = backupId,
    )

    private fun validJson(expense: String = EXPENSE, expenses: String = expense, budget: String = BUDGET, budgets: String = budget) =
        """{"schemaVersion":1,"exportedAt":"$EXPORTED_AT","appVersion":"6.0","expenses":[$expenses],"budgets":[$budgets]}"""

    companion object {
        private const val UUID = "550e8400-e29b-41d4-a716-446655440000"
        private const val EXPORTED_AT = "2026-11-05T08:30:00Z"
        private const val EXPENSE = """{"backupId":"$UUID","amountCents":2850,"category":"FOOD","note":"午餐","expenseDate":20000,"createdAt":10,"updatedAt":20}"""
        private const val BUDGET = """{"year":2026,"month":11,"amountCents":5000,"createdAt":10,"updatedAt":20}"""
    }
}
