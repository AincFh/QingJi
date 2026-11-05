package com.linjincheng.qingji.ui

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupScreenPresentationTest {
    @Test
    fun backupScreenIsBusyDuringEitherFileOperation() {
        assertFalse(BackupScreenState().isBusy)
        assertTrue(BackupScreenState(isExporting = true).isBusy)
        assertTrue(BackupScreenState(isImporting = true).isBusy)
    }

    @Test
    fun budgetMonthPreviewUsesZeroPaddedMonthLabels() {
        assertEquals("2026年01月", formatBudgetMonth(YearMonth.of(2026, 1)))
        assertEquals("2026年11月", formatBudgetMonth(YearMonth.of(2026, 11)))
    }
}
