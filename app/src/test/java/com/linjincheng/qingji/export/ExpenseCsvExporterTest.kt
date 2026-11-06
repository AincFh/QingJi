package com.linjincheng.qingji.export

import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.data.ExpenseEntry
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExpenseCsvExporterTest {
    private fun entry(
        id: Long = 1L,
        cents: Long = 1234L,
        category: ExpenseCategory = ExpenseCategory.FOOD,
        note: String = "午饭",
        day: Long = 0L,
        createdAt: Long = 0L,
        updatedAt: Long = 1000L,
    ) = ExpenseEntry(
        id = id, amountCents = cents, category = category, note = note,
        expenseDate = day, createdAt = createdAt, updatedAt = updatedAt,
    )

    @Test fun centsUseExactDecimalPrecision() {
        assertEquals("0.01", ExpenseCsvExporter.centsToYuan(1))
        assertEquals("0.09", ExpenseCsvExporter.centsToYuan(9))
        assertEquals("0.10", ExpenseCsvExporter.centsToYuan(10))
        assertEquals("10.05", ExpenseCsvExporter.centsToYuan(1005))
        assertEquals("1000000.01", ExpenseCsvExporter.centsToYuan(100000001))
        assertEquals("92233720368547758.07", ExpenseCsvExporter.centsToYuan(Long.MAX_VALUE))
    }

    @Test fun nonPositiveAmountsAreNeverSilentlyExported() {
        for (cents in listOf(0L, -1L, Long.MIN_VALUE)) {
            try {
                ExpenseCsvExporter.centsToYuan(cents)
                fail("Should reject invalid money input")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun emptyLedgerExportsOnlyReadableHeader() {
        assertEquals(
            "日期,分类,分类代码,金额(元),备注,记录ID,创建时间(UTC),更新时间(UTC)\r\n",
            ExpenseCsvExporter.buildCsv(emptyList()),
        )
    }

    @Test fun datesAndTimestampsUseUnambiguousIsoUtcFormat() {
        val csv = ExpenseCsvExporter.buildCsv(listOf(entry(day = 0, createdAt = 0, updatedAt = 1000)))
        assertTrue(csv.contains("\"1970-01-01\""))
        assertTrue(csv.contains("\"1970-01-01T00:00:00Z\""))
        assertTrue(csv.contains("\"1970-01-01T00:00:01Z\""))
    }

    @Test fun allCategoryCodesAndChineseLabelsArePreserved() {
        val entries = ExpenseCategory.entries.mapIndexed { index, category ->
            entry(id = index.toLong() + 1, category = category)
        }
        val csv = ExpenseCsvExporter.buildCsv(entries)
        for (category in ExpenseCategory.entries) {
            assertTrue(csv.contains("\"" + category.label + "\",\"" + category.code + "\""))
        }
        assertEquals(5, csv.split("\r\n").size - 2)
    }

    @Test fun quotesCommasCarriageReturnsAndMultilineNotesRoundTripAsQuotedCsv() {
        val note = "午饭,\"牛肉\"\r\n第二行"
        val csv = ExpenseCsvExporter.buildCsv(listOf(entry(note = note)))
        assertTrue(csv.contains("\"午饭,\"\"牛肉\"\"\r\n第二行\""))
        assertTrue(csv.endsWith("\"1970-01-01T00:00:01Z\"\r\n"))
    }

    @Test fun formulaInjectionIsNeutralizedWithoutDroppingNoteText() {
        for (dangerous in listOf(
            "=2+2", "+100", "-5+3", "@SUM(A1)", "\t=HYPERLINK(1)", "  =1+1",
        )) {
            val quoted = ExpenseCsvExporter.safeCell(dangerous)
            assertTrue(quoted, quoted.startsWith("\"'"))
            assertTrue(quoted, quoted.contains(dangerous))
        }
    }

    @Test fun normalTextIsNotModifiedBeyondCsvQuoting() {
        assertEquals("\"普通备注\"", ExpenseCsvExporter.safeCell("普通备注"))
        assertEquals("\"\"\"\"", ExpenseCsvExporter.safeCell("\""))
        assertEquals("\"  中文\"", ExpenseCsvExporter.safeCell("  中文"))

    @Test fun utf8BomIsIncludedForChineseExcelCompatibility() {
        val bytes = ExpenseCsvExporter.toUtf8Csv(listOf(entry()))
        assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            bytes.copyOfRange(0, 3))
        assertTrue(String(bytes.copyOfRange(3, bytes.size), StandardCharsets.UTF_8)
            .contains("午饭"))
    }

    @Test fun deterministicExportDoesNotMutateOrReorderLedger() {
        val entries = listOf(
            entry(2, 9999, ExpenseCategory.TRANSPORT, "打车", day = 1),
            entry(1, 100, ExpenseCategory.FOOD, "饭店", day = 0),
        )
        val before = entries.toList()
        val a = ExpenseCsvExporter.buildCsv(entries)
        val b = ExpenseCsvExporter.buildCsv(entries)
        assertEquals(a, b)
        assertEquals(before, entries)
        assertTrue(a.indexOf("\"打车\"") < a.indexOf("\"饭店\""))
        assertFalse(a.contains("NaN"))
        assertFalse(a.contains("E+"))
    }

    @Test fun allRecordsAreExportedRegardlessOfUiSearchOrPrivacySettings() {
        val entries = (1..150).map {
            entry(id = it.toLong(), cents = it.toLong(), note = "消费 $it")
        }
        val csv = ExpenseCsvExporter.buildCsv(entries)
        assertEquals(151, csv.split("\r\n").size - 1)
        assertTrue(csv.contains("\"消费 150\""))
        assertTrue(csv.contains("\"0.01\""))
        assertTrue(csv.contains("\"1.50\""))
    }
}
