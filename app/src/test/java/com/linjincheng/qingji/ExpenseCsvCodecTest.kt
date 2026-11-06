package com.linjincheng.qingji

import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.data.ExpenseCsvCodec
import com.linjincheng.qingji.data.ExpenseEntry
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExpenseCsvCodecTest {
    private fun row(
        amount: Long,
        note: String = "",
        date: String = "2026-11-06",
        category: ExpenseCategory = ExpenseCategory.FOOD,
        id: Long = 1L,
        createdAt: Long = 10L,
    ): ExpenseEntry = ExpenseEntry(
        id = id,
        amountCents = amount,
        category = category,
        note = note,
        expenseDate = LocalDate.parse(date).toEpochDay(),
        createdAt = createdAt,
        updatedAt = createdAt,
        backupId = "11111111-1111-4111-8111-111111111111",
    )

    private fun text(rows: List<ExpenseEntry>) =
        String(ExpenseCsvCodec.encode(rows), StandardCharsets.UTF_8)

    @Test fun utf8BomAndEmptyExportAreValid() {
        val csv = text(emptyList())
        assertEquals("\uFEFF日期,分类,金额(元),备注\r\n", csv)
        assertEquals("text/csv", ExpenseCsvCodec.MIME_TYPE)
    }

    @Test fun centsAreExactNoFloatingPointAndRowsAreNewestFirst() {
        val csv = text(
            listOf(
                row(5L, "早茶", date = "2026-11-05", id = 1),
                row(1_234_567_890_123L, "复合账单", date = "2026-11-06", id = 2),
                row(10L, "晚餐", date = "2026-11-06", id = 3, createdAt = 20),
            ),
        )
        assertEquals(
            "\uFEFF日期,分类,金额(元),备注\r\n" +
                "\"2026-11-06\",\"餐饮\",0.10,\"晚餐\"\r\n" +
                "\"2026-11-06\",\"餐饮\",12345678901.23,\"复合账单\"\r\n" +
                "\"2026-11-05\",\"餐饮\",0.05,\"早茶\"\r\n",
            csv,
        )
        assertFalse(csv.contains("11111111-1111"))
    }

    @Test fun commasQuotesNewlinesAndChineseNotesAreQuotedCorrectly() {
        val csv = text(listOf(row(1899, "便利店,买了\"奶茶\"\n第二行", category = ExpenseCategory.SHOPPING)))
        assertTrue(csv.contains(
            "\"2026-11-06\",\"购物\",18.99,\"便利店,买了\"\"奶茶\"\"\n第二行\"\r\n",
        ))
    }

    @Test fun spreadsheetFormulasRemainLiteralText() {
        val cases = listOf("=1+1", "+SUM(1,2)", "-42+42", "@SUM(1,2)",
            "  =HYPERLINK(\"https://example.com\")", "\t@CMD", "\n+1")
        for (candidate in cases) {
            val safe = ExpenseCsvCodec.preventFormulaExecution(candidate)
            assertTrue("must prefix apostrophe: $candidate", safe.startsWith("'"))
            val csv = text(listOf(row(100, candidate)))
            assertTrue(csv.contains(("\"" + safe.replace("\"", "\"\"") + "\"")))
        }
        assertEquals("普通文字", ExpenseCsvCodec.preventFormulaExecution("普通文字"))
        assertEquals("午饭 + 饮料", ExpenseCsvCodec.preventFormulaExecution("午饭 + 饮料"))
    }

    @Test fun invalidAmountsAndOversizedNotesCannotBeExportedAsNumbers() {
        for (value in listOf(0L, -1L)) {
            try {
                ExpenseCsvCodec.encode(listOf(row(value)))
                fail("Expected rejection of illegal cents: $value")
            } catch (_: IllegalArgumentException) {
                // fail closed; the ViewModel reports this failure to the user
            }
        }
        try {
            ExpenseCsvCodec.encode(listOf(row(25, "a".repeat(10_001))))
            fail("Expected oversized note rejection")
        } catch (_: IllegalArgumentException) {
        }
    }
}
