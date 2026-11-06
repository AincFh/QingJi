package com.linjincheng.qingji

import com.linjincheng.qingji.data.BackupApplyResult
import com.linjincheng.qingji.data.DailyExpenseAggregate
import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.data.ExpenseBackupCodec
import com.linjincheng.qingji.data.ExpenseEntry
import com.linjincheng.qingji.data.ExpenseBackupPayload
import com.linjincheng.qingji.data.ExpenseRepository
import com.linjincheng.qingji.data.MonthlyBudget
import com.linjincheng.qingji.util.DateUtils
import com.linjincheng.qingji.util.EpochDayRange
import com.linjincheng.qingji.util.ExpenseFilter
import com.linjincheng.qingji.viewmodel.BackupPickerAction
import com.linjincheng.qingji.viewmodel.ExpenseViewModel
import com.linjincheng.qingji.viewmodel.Screen
import java.time.YearMonth
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun chartRoutesBuildSingleDayAndCurrentMonthAndKeepAndFiltersOnReturn() = runTest {
        val today = DateUtils.todayEpochDay()
        val currentMonth = YearMonth.now()
        val repo = FakeExpenseRepository(
            listOf(
                expense(1, ExpenseCategory.FOOD, "coffee", today, updatedAt = 30),
                expense(2, ExpenseCategory.TRANSPORT, "bus", today, updatedAt = 20),
                expense(3, ExpenseCategory.FOOD, "coffee yesterday", today - 1, updatedAt = 10),
                expense(
                    4,
                    ExpenseCategory.FOOD,
                    "coffee last month",
                    currentMonth.minusMonths(1).atDay(15).toEpochDay(),
                    updatedAt = 5,
                ),
            ),
        )
        val viewModel = createViewModel(repo)

        viewModel.onSearchTextChange("coffee")
        viewModel.onCategoryFilterChange(ExpenseCategory.FOOD)
        viewModel.openDateDetails(today)

        assertEquals(ExpenseFilter(EpochDayRange.day(today), ExpenseCategory.FOOD, "coffee"), viewModel.home.value.expenseFilter)
        assertEquals(listOf(1L), viewModel.home.value.entries.map(ExpenseEntry::id))

        viewModel.onHistoryTabSelected(showAllHistory = false)
        viewModel.onHistoryTabSelected(showAllHistory = true)
        viewModel.openDetail(1)
        advanceUntilIdle()
        viewModel.back()
        advanceUntilIdle()

        assertEquals(Screen.Home, viewModel.screen.value)
        assertEquals(ExpenseFilter(EpochDayRange.day(today), ExpenseCategory.FOOD, "coffee"), viewModel.home.value.expenseFilter)
        assertEquals(listOf(1L), viewModel.home.value.entries.map(ExpenseEntry::id))

        viewModel.openCategoryDetails(ExpenseCategory.FOOD)
        val monthFilter = ExpenseFilter(EpochDayRange.month(currentMonth), ExpenseCategory.FOOD, "coffee")
        assertEquals(monthFilter, viewModel.home.value.expenseFilter)
        assertEquals(listOf(1L, 3L), viewModel.home.value.entries.map(ExpenseEntry::id).sorted())
    }

    @Test
    fun chartSelectionsRemainInViewModelWhenSwitchingTabs() = runTest {
        val viewModel = createViewModel(FakeExpenseRepository(emptyList()))
        val selectedHomeDay = DateUtils.todayEpochDay() - 2

        viewModel.selectTrendEpochDay(selectedHomeDay)
        viewModel.selectCategory(ExpenseCategory.SHOPPING)
        viewModel.onHistoryTabSelected(showAllHistory = true)
        viewModel.onHistoryTabSelected(showAllHistory = false)
        viewModel.openDateDetails(selectedHomeDay)
        viewModel.onHistoryTabSelected(showAllHistory = true)
        viewModel.onHistoryTabSelected(showAllHistory = false)

        assertEquals(selectedHomeDay, viewModel.home.value.selectedTrendEpochDay)
        assertEquals(ExpenseCategory.SHOPPING, viewModel.home.value.selectedCategory)
        assertEquals(EpochDayRange.day(selectedHomeDay), viewModel.home.value.expenseFilter.dateRange)
    }

    @Test
    fun copyInsertsNewIdDefaultsToTodayAndReturnsToSourceDetail() = runTest {
        val source = expense(
            id = 41,
            category = ExpenseCategory.SHOPPING,
            note = "original note",
            epochDay = DateUtils.todayEpochDay() - 12,
            amountCents = 1234,
            updatedAt = 400,
        )
        val repo = FakeExpenseRepository(listOf(source))
        val viewModel = createViewModel(repo)

        viewModel.openDetail(source.id)
        advanceUntilIdle()
        viewModel.openCopy(source.id)
        advanceUntilIdle()

        val copyForm = viewModel.form.value
        assertTrue(copyForm.copyMode)
        assertNull(copyForm.editingId)
        assertEquals(source.amountCents, com.linjincheng.qingji.util.MoneyUtils.parseAmountToCents(copyForm.amountText).let {
            (it as com.linjincheng.qingji.util.AmountParseResult.Valid).cents
        })
        assertEquals(source.category, copyForm.selectedCategory)
        assertEquals(source.note, copyForm.note)
        assertEquals(DateUtils.todayEpochDay(), copyForm.epochDay)

        viewModel.saveExpense()
        viewModel.saveExpense()
        assertTrue(viewModel.form.value.saving)
        advanceUntilIdle()

        val copy = repo.entries.single { it.id != source.id }
        assertNotEquals(source.id, copy.id)
        assertEquals(source.amountCents, copy.amountCents)
        assertEquals(source.category, copy.category)
        assertEquals(source.note, copy.note)
        assertEquals(DateUtils.todayEpochDay(), copy.expenseDate)
        assertNotEquals(source.backupId, copy.backupId)
        assertEquals(source, repo.getExpenseById(source.id))
        assertEquals(1, repo.insertCalls)
        assertEquals(Screen.Detail(source.id), viewModel.screen.value)
        assertEquals("已复制为新记录", viewModel.detail.value.feedbackMessage)
    }

    @Test
    fun copyLoadFailureCanRetryAndMissingSourceCanReturnToDetail() = runTest {
        val source = expense(7, ExpenseCategory.DAILY, "source", DateUtils.todayEpochDay() - 2)
        val repo = FakeExpenseRepository(listOf(source))
        val viewModel = createViewModel(repo)
        viewModel.openDetail(source.id)
        advanceUntilIdle()

        repo.readFailuresRemaining = 1
        viewModel.openCopy(source.id)
        advanceUntilIdle()
        assertTrue(viewModel.form.value.loadFailed)
        assertTrue(viewModel.form.value.copyMode)

        viewModel.retryFormLoad()
        advanceUntilIdle()
        assertFalse(viewModel.form.value.loadFailed)
        assertFalse(viewModel.form.value.notFound)
        assertEquals(DateUtils.todayEpochDay(), viewModel.form.value.epochDay)

        viewModel.requestFormBack()
        advanceUntilIdle()
        assertEquals(Screen.Detail(source.id), viewModel.screen.value)

        repo.entries.removeAll { it.id == source.id }
        viewModel.openCopy(source.id)
        advanceUntilIdle()
        assertTrue(viewModel.form.value.notFound)
        viewModel.requestFormBack()
        advanceUntilIdle()
        assertEquals(Screen.Detail(source.id), viewModel.screen.value)
        assertTrue(viewModel.detail.value.notFound)
    }

    @Test
    fun failedCopyWriteCanRetryAndDuplicateSubmitWritesOnlyOnce() = runTest {
        val source = expense(8, ExpenseCategory.OTHER, "keep", DateUtils.todayEpochDay() - 1)
        val repo = FakeExpenseRepository(listOf(source))
        val viewModel = createViewModel(repo)
        viewModel.openDetail(source.id)
        advanceUntilIdle()
        viewModel.openCopy(source.id)
        advanceUntilIdle()

        repo.insertFailuresRemaining = 1
        viewModel.saveExpense()
        viewModel.saveExpense()
        advanceUntilIdle()
        assertTrue(viewModel.form.value.saveFailed)
        assertEquals(1, repo.insertCalls)
        assertEquals(listOf(source), repo.entries)

        viewModel.saveExpense()
        advanceUntilIdle()
        assertEquals(2, repo.insertCalls)
        assertEquals(2, repo.entries.size)
        assertEquals(source, repo.getExpenseById(source.id))
        assertEquals(Screen.Detail(source.id), viewModel.screen.value)
    }

    @Test
    fun addEditDeleteRefreshTheSameCombinedFilteredResultAndRecentCategoriesDoNotOverrideDefault() = runTest {
        val today = DateUtils.todayEpochDay()
        val repo = FakeExpenseRepository(
            listOf(
                expense(1, ExpenseCategory.FOOD, "coffee", today, updatedAt = 50),
                expense(2, ExpenseCategory.TRANSPORT, "bus", today, updatedAt = 40),
                expense(3, ExpenseCategory.SHOPPING, "bag", today - 1, updatedAt = 30),
                expense(4, ExpenseCategory.DAILY, "soap", today - 2, updatedAt = 20),
                expense(5, ExpenseCategory.OTHER, "misc", today - 3, updatedAt = 10),
                expense(6, ExpenseCategory.FOOD, "snack", today - 1, updatedAt = 55),
            ),
        )
        val viewModel = createViewModel(repo)

        assertEquals(
            listOf(ExpenseCategory.FOOD, ExpenseCategory.TRANSPORT, ExpenseCategory.SHOPPING),
            viewModel.home.value.recentCategories,
        )
        viewModel.openAdd(defaultCategory = ExpenseCategory.DAILY)
        assertEquals(ExpenseCategory.DAILY, viewModel.form.value.selectedCategory)
        viewModel.back()

        viewModel.openFilteredLedger(
            ExpenseFilter(EpochDayRange.day(today), ExpenseCategory.FOOD, "coffee"),
        )
        assertEquals(listOf(1L), viewModel.home.value.entries.map(ExpenseEntry::id))

        viewModel.openAdd(defaultCategory = ExpenseCategory.FOOD)
        viewModel.onAmountChange("2.00")
        viewModel.onNoteChange("coffee second")
        viewModel.saveExpense()
        advanceUntilIdle()
        val addedId = repo.entries.maxOf(ExpenseEntry::id)
        assertEquals(listOf(1L, addedId).sorted(), viewModel.home.value.entries.map(ExpenseEntry::id).sorted())

        viewModel.openDetail(addedId)
        advanceUntilIdle()
        viewModel.openEdit(addedId)
        advanceUntilIdle()
        viewModel.onNoteChange("tea")
        viewModel.saveExpense()
        advanceUntilIdle()
        assertEquals(listOf(1L), viewModel.home.value.entries.map(ExpenseEntry::id))

        viewModel.openDetail(1)
        advanceUntilIdle()
        viewModel.onConfirmDelete()
        advanceUntilIdle()
        assertTrue(viewModel.home.value.entries.isEmpty())
        assertEquals(ExpenseFilter(EpochDayRange.day(today), ExpenseCategory.FOOD, "coffee"), viewModel.home.value.expenseFilter)
    }

    @Test
    fun calendarLoadsRealDailyTotalsKeepsSelectionAndDrillsIntoSharedLedgerFilter() = runTest {
        val today = DateUtils.todayEpochDay()
        val repo = FakeExpenseRepository(
            listOf(
                expense(1, ExpenseCategory.FOOD, "coffee", today, amountCents = 450L),
                expense(2, ExpenseCategory.TRANSPORT, "bus", today, amountCents = 700L),
                expense(3, ExpenseCategory.FOOD, "tea", today - 1, amountCents = 300L),
            ),
        )
        val viewModel = createViewModel(repo)

        viewModel.openCalendar()
        advanceUntilIdle()

        assertEquals(Screen.Calendar, viewModel.screen.value)
        assertEquals(2, viewModel.calendar.value.dailyTotals.size)
        assertEquals(1_150L, viewModel.calendar.value.dailyTotals.first { it.epochDay == today }.totalCents)
        assertEquals(2, viewModel.calendar.value.dailyTotals.first { it.epochDay == today }.transactionCount)

        viewModel.onCalendarDaySelected(today)
        viewModel.onCalendarMonthChange(YearMonth.now().minusMonths(1))
        advanceUntilIdle()
        assertEquals(YearMonth.now().minusMonths(1), viewModel.calendar.value.month)
        assertEquals(YearMonth.now().minusMonths(1).atDay(1).toEpochDay(), viewModel.calendar.value.selectedEpochDay)
        viewModel.onCalendarMonthChange(YearMonth.now())
        advanceUntilIdle()
        viewModel.onSearchTextChange("coffee")
        viewModel.openCalendarDayDetails(today)

        assertEquals(Screen.Home, viewModel.screen.value)
        assertEquals(ExpenseFilter(EpochDayRange.day(today), keyword = "coffee"), viewModel.home.value.expenseFilter)
        assertEquals(listOf(1L), viewModel.home.value.entries.map(ExpenseEntry::id))
        assertEquals(today, viewModel.calendar.value.selectedEpochDay)

        val rememberedMonth = YearMonth.now().minusMonths(1)
        val rememberedDay = rememberedMonth.atDay(12).toEpochDay()
        viewModel.onCalendarMonthChange(rememberedMonth)
        advanceUntilIdle()
        viewModel.onCalendarDaySelected(rememberedDay)
        viewModel.openCalendarDayDetails(rememberedDay)
        viewModel.openCalendar()
        advanceUntilIdle()
        assertEquals(rememberedMonth, viewModel.calendar.value.month)
        assertEquals(rememberedDay, viewModel.calendar.value.selectedEpochDay)
    }

    @Test
    fun budgetReadsChangesMonthSavesClearsAndOpensFilteredExpenses() = runTest {
        val currentMonth = YearMonth.now()
        val previousMonth = currentMonth.minusMonths(1)
        val today = DateUtils.todayEpochDay()
        val repo = FakeExpenseRepository(
            listOf(expense(1, ExpenseCategory.FOOD, "lunch", today, amountCents = 2_500L)),
        )
        repo.budgets += MonthlyBudget(currentMonth.year, currentMonth.monthValue, 10_000L, 1L, 2L)
        repo.budgets += MonthlyBudget(previousMonth.year, previousMonth.monthValue, 7_000L, 3L, 4L)
        val viewModel = createViewModel(repo)

        viewModel.openBudget()
        advanceUntilIdle()
        assertEquals(Screen.Budget, viewModel.screen.value)
        assertEquals(10_000L, viewModel.budget.value.summary.budgetCents)
        assertEquals(2_500L, viewModel.budget.value.summary.spentCents)

        viewModel.onBudgetMonthChange(previousMonth.year, previousMonth.monthValue)
        advanceUntilIdle()
        assertEquals(7_000L, viewModel.budget.value.summary.budgetCents)

        viewModel.saveBudget(previousMonth.year, previousMonth.monthValue, 9_500L)
        advanceUntilIdle()
        assertEquals(9_500L, viewModel.budget.value.summary.budgetCents)
        assertEquals("预算已保存。", viewModel.budget.value.feedbackMessage)

        viewModel.clearBudget(previousMonth.year, previousMonth.monthValue)
        advanceUntilIdle()
        assertNull(viewModel.budget.value.summary.budgetCents)
        assertEquals("本月预算已清除。", viewModel.budget.value.feedbackMessage)

        viewModel.openBudgetExpenses(currentMonth.year, currentMonth.monthValue)
        assertEquals(Screen.Home, viewModel.screen.value)
        assertEquals(ExpenseFilter(EpochDayRange.month(currentMonth)), viewModel.home.value.expenseFilter)
        assertEquals(listOf(1L), viewModel.home.value.entries.map(ExpenseEntry::id))
    }

    @Test
    fun backupExportAndImportRequirePickerResultsAndExplicitConfirmation() = runTest {
        val existingId = UUID.randomUUID().toString()
        val importedId = UUID.randomUUID().toString()
        val existing = expense(1, ExpenseCategory.FOOD, "local", DateUtils.todayEpochDay())
            .copy(backupId = existingId)
        val repo = FakeExpenseRepository(listOf(existing))
        val month = YearMonth.now()
        val currentBudget = MonthlyBudget(month.year, month.monthValue, 4_000L, 1L, 2L)
        repo.budgets += currentBudget
        val viewModel = createViewModel(repo)
        viewModel.openBackup()
        viewModel.requestBackupExport()
        val exportRequest = requireNotNull(viewModel.backupPickerRequest.value)
        assertEquals(BackupPickerAction.EXPORT, viewModel.claimBackupPickerRequest(exportRequest.id)?.action)
        assertNull(viewModel.claimBackupPickerRequest(exportRequest.id))
        viewModel.requestBackupExport()
        assertNull(viewModel.backupPickerRequest.value)

        var exportedBytes: ByteArray? = null
        viewModel.exportBackup("1.0") { exportedBytes = it }
        advanceUntilIdle()
        val decodedExport = ExpenseBackupCodec.decode(requireNotNull(exportedBytes))
        assertEquals(listOf(existingId), decodedExport.expenses.map(ExpenseEntry::backupId))
        assertEquals(listOf(currentBudget), decodedExport.budgets)
        assertFalse(viewModel.backup.value.isExporting)

        val duplicateExpense = existing.copy(id = 0L, note = "duplicate identity")
        val newExpense = expense(0, ExpenseCategory.TRANSPORT, "new", DateUtils.todayEpochDay())
            .copy(backupId = importedId)
        val replacementBudget = currentBudget.copy(amountCents = 8_000L, updatedAt = 10L)
        val payload = ExpenseBackupPayload(
            exportedAt = Instant.now(),
            appVersion = "1.0",
            expenses = listOf(duplicateExpense, newExpense),
            budgets = listOf(replacementBudget),
        )
        viewModel.requestBackupImport()
        val importRequest = requireNotNull(viewModel.backupPickerRequest.value)
        assertEquals(BackupPickerAction.IMPORT, viewModel.claimBackupPickerRequest(importRequest.id)?.action)
        assertNull(viewModel.claimBackupPickerRequest(importRequest.id))
        viewModel.onBackupFileSelected { payload }
        advanceUntilIdle()

        assertEquals(1, viewModel.backup.value.importPreview?.newExpenses)
        assertEquals(1, viewModel.backup.value.importPreview?.skippedExpenses)
        assertEquals(listOf(month), viewModel.backup.value.importPreview?.budgetMonths)
        assertEquals(0, repo.backupApplyCalls)

        viewModel.confirmBackupImport()
        viewModel.confirmBackupImport()
        advanceUntilIdle()
        assertEquals(1, repo.backupApplyCalls)
        assertEquals(2, repo.entries.size)
        assertEquals(8_000L, repo.budgets.single().amountCents)
        assertNull(viewModel.backup.value.importPreview)
        assertTrue(viewModel.backup.value.feedback?.message?.contains("新增 1 笔") == true)
        assertFalse(viewModel.backup.value.isImporting)
    }

    @Test
    fun backupCancellationAndInvalidFileNeverApplyDatabaseChanges() = runTest {
        val repo = FakeExpenseRepository(emptyList())
        val viewModel = createViewModel(repo)
        viewModel.openBackup()

        viewModel.requestBackupImport()
        viewModel.onBackupImportPickerCancelled()
        assertEquals("已取消恢复。", viewModel.backup.value.feedback?.message)
        assertEquals(0, repo.backupApplyCalls)

        viewModel.requestBackupImport()
        viewModel.onBackupFileSelected { throw IllegalArgumentException("不支持的备份版本：9") }
        advanceUntilIdle()
        assertTrue(viewModel.backup.value.feedback?.isError == true)
        assertNull(viewModel.backup.value.importPreview)
        assertEquals(0, repo.backupApplyCalls)
        assertTrue(repo.entries.isEmpty())
    }

    @Test
    fun failedBackupConfirmationKeepsPreviewAndDoesNotChangeRepositoryData() = runTest {
        val existingId = UUID.randomUUID().toString()
        val existing = expense(1, ExpenseCategory.FOOD, "local", DateUtils.todayEpochDay())
            .copy(backupId = existingId)
        val repo = FakeExpenseRepository(listOf(existing))
        val month = YearMonth.now()
        val originalBudget = MonthlyBudget(month.year, month.monthValue, 3_000L, 1L, 2L)
        repo.budgets += originalBudget
        val viewModel = createViewModel(repo)
        viewModel.openBackup()

        val payload = ExpenseBackupPayload(
            exportedAt = Instant.now(),
            appVersion = "1.0",
            expenses = listOf(
                expense(0, ExpenseCategory.TRANSPORT, "new", DateUtils.todayEpochDay())
                    .copy(backupId = UUID.randomUUID().toString()),
            ),
            budgets = listOf(originalBudget.copy(amountCents = 9_000L, updatedAt = 3L)),
        )
        viewModel.requestBackupImport()
        viewModel.onBackupFileSelected { payload }
        advanceUntilIdle()
        repo.backupApplyFailuresRemaining = 1

        viewModel.confirmBackupImport()
        advanceUntilIdle()

        assertEquals(1, repo.backupApplyCalls)
        assertEquals(listOf(existing), repo.entries)
        assertEquals(listOf(originalBudget), repo.budgets)
        assertNotNull(viewModel.backup.value.importPreview)
        assertTrue(viewModel.backup.value.feedback?.isError == true)
        assertFalse(viewModel.backup.value.isImporting)
    }

    private suspend fun TestScope.createViewModel(repository: FakeExpenseRepository): ExpenseViewModel {
        val viewModel = ExpenseViewModel(repository)
        advanceUntilIdle()
        return viewModel
    }

    private fun expense(
        id: Long,
        category: ExpenseCategory,
        note: String,
        epochDay: Long,
        amountCents: Long = 1000L,
        updatedAt: Long = id,
    ) = ExpenseEntry(
        id = id,
        amountCents = amountCents,
        category = category,
        note = note,
        expenseDate = epochDay,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        backupId = "backup-$id",
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    private val dispatcher = StandardTestDispatcher()

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeExpenseRepository(initialEntries: List<ExpenseEntry>) : ExpenseRepository {
    val entries = initialEntries.toMutableList()
    val budgets = mutableListOf<MonthlyBudget>()
    var insertCalls = 0
        private set
    var backupApplyCalls = 0
        private set
    var backupApplyFailuresRemaining = 0
    var insertFailuresRemaining = 0
    var readFailuresRemaining = 0
    private var nextId = (initialEntries.maxOfOrNull(ExpenseEntry::id) ?: 0L) + 1L

    override suspend fun insertExpense(entry: ExpenseEntry): Long {
        insertCalls += 1
        if (insertFailuresRemaining > 0) {
            insertFailuresRemaining -= 1
            error("injected insert failure")
        }
        val id = nextId++
        entries += entry.copy(id = id)
        return id
    }

    override suspend fun updateExpense(entry: ExpenseEntry): Boolean {
        val index = entries.indexOfFirst { it.id == entry.id }
        if (index < 0) return false
        entries[index] = entry
        return true
    }

    override suspend fun deleteExpense(id: Long): Boolean = entries.removeAll { it.id == id }

    override suspend fun getExpenseById(id: Long): ExpenseEntry? {
        if (readFailuresRemaining > 0) {
            readFailuresRemaining -= 1
            error("injected read failure")
        }
        return entries.firstOrNull { it.id == id }
    }

    override suspend fun getAllExpenses(): List<ExpenseEntry> =
        entries.sortedWith(compareByDescending<ExpenseEntry> { it.expenseDate }.thenByDescending { it.createdAt })

    override suspend fun getExpensesByMonth(year: Int, month: Int): List<ExpenseEntry> {
        val range = EpochDayRange.month(YearMonth.of(year, month))
        return entries.filter { range.contains(it.expenseDate) }
            .sortedWith(compareByDescending<ExpenseEntry> { it.expenseDate }.thenByDescending { it.createdAt })
    }

    override suspend fun getMonthlyTotal(year: Int, month: Int): Long =
        getExpensesByMonth(year, month).sumOf(ExpenseEntry::amountCents)

    override suspend fun getMonthlyCategoryTotals(year: Int, month: Int): Map<ExpenseCategory, Long> =
        getExpensesByMonth(year, month).groupBy(ExpenseEntry::category)
            .mapValues { (_, entriesForCategory) -> entriesForCategory.sumOf(ExpenseEntry::amountCents) }

    override suspend fun getMonthlyBudget(year: Int, month: Int): MonthlyBudget? =
        budgets.firstOrNull { it.year == year && it.month == month }

    override suspend fun getAllMonthlyBudgets(): List<MonthlyBudget> = budgets.toList()

    override suspend fun saveMonthlyBudget(budget: MonthlyBudget): Boolean {
        budgets.removeAll { it.year == budget.year && it.month == budget.month }
        budgets += budget
        return true
    }

    override suspend fun deleteMonthlyBudget(year: Int, month: Int): Boolean =
        budgets.removeAll { it.year == year && it.month == month }

    override suspend fun getDailyExpenseAggregates(
        startInclusive: Long,
        endExclusive: Long,
    ): List<DailyExpenseAggregate> = entries
        .filter { it.expenseDate in startInclusive until endExclusive }
        .groupBy(ExpenseEntry::expenseDate)
        .map { (epochDay, rows) ->
            DailyExpenseAggregate(epochDay, rows.sumOf(ExpenseEntry::amountCents), rows.size)
        }
        .sortedBy(DailyExpenseAggregate::epochDay)

    override suspend fun applyBackupMerge(
        expenses: List<ExpenseEntry>,
        budgets: List<MonthlyBudget>,
    ): BackupApplyResult {
        backupApplyCalls += 1
        if (backupApplyFailuresRemaining > 0) {
            backupApplyFailuresRemaining -= 1
            error("injected backup merge failure")
        }
        var inserted = 0
        var skipped = 0
        expenses.forEach { expense ->
            if (entries.any { it.backupId.equals(expense.backupId, ignoreCase = true) }) {
                skipped += 1
            } else {
                entries += expense.copy(id = nextId++)
                inserted += 1
            }
        }
        budgets.forEach { budget ->
            this.budgets.removeAll { it.year == budget.year && it.month == budget.month }
            this.budgets += budget
        }
        return BackupApplyResult(inserted, skipped, budgets.size)
    }
}
