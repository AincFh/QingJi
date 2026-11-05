package com.linjincheng.qingji.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.data.ExpenseBackupCodec
import com.linjincheng.qingji.data.ExpenseBackupPayload
import com.linjincheng.qingji.data.ExpenseDatabaseHelper
import com.linjincheng.qingji.data.ExpenseEntry
import com.linjincheng.qingji.data.ExpenseRepository
import com.linjincheng.qingji.data.ExpenseRepositoryImpl
import com.linjincheng.qingji.data.MonthlyBudget
import com.linjincheng.qingji.data.MonthlyBudgetSummary
import com.linjincheng.qingji.ui.BackupScreenFeedback
import com.linjincheng.qingji.ui.BackupScreenState
import com.linjincheng.qingji.util.AmountParseResult
import com.linjincheng.qingji.util.DateUtils
import com.linjincheng.qingji.util.DailyExpenseTotal
import com.linjincheng.qingji.util.ExpenseAnalytics
import com.linjincheng.qingji.util.ExpenseFilter
import com.linjincheng.qingji.util.EpochDayRange
import com.linjincheng.qingji.util.MoneyUtils
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.util.Locale

/**
 * Secondary screen routes are represented as state; the app does not use Navigation Compose.
 */
sealed interface Screen {
    data object Home : Screen

    data object Settings : Screen

    data object Calendar : Screen

    data object Budget : Screen

    data object Backup : Screen

    /** 表单：expenseId = null 表示新增，否则为编辑该条记录。 */
    data class Form(val expenseId: Long? = null) : Screen

    data class Detail(val expenseId: Long) : Screen
}

/** 分类统计行：percent 为 0..100 四舍五入整数。 */
data class CategoryStat(val category: ExpenseCategory, val totalCents: Long, val percent: Int)

/** 首页状态：categoryStats 恒含全部 5 类（金额降序、0 元在后）。 */
data class HomeUiState(
    val year: Int,
    val month: Int,
    val monthTitle: String,
    val totalCents: Long,
    val categoryStats: List<CategoryStat>,
    val entries: List<ExpenseEntry>,
    val isLoading: Boolean,
    val recentEntries: List<ExpenseEntry> = emptyList(),
    val showAllHistory: Boolean = false,
    val hasAnyEntries: Boolean = false,
    val previousMonthTotalCents: Long = 0L,
    val comparisonCurrentTotalCents: Long = totalCents,
    val comparisonLabel: String = "较上月",
    val dailyTrend: List<DailyExpenseTotal> = emptyList(),
    val dailyTrend30: List<DailyExpenseTotal> = emptyList(),
    val expenseFilter: ExpenseFilter = ExpenseFilter(),
    val historyMonthOptions: List<YearMonth> = emptyList(),
    val hasMonthEntries: Boolean = false,
    val monthEntryCount: Int = 0,
    val loadFailed: Boolean = false,
    val feedbackMessage: String? = null,
    val hasLoaded: Boolean = false,
    val recentCategories: List<ExpenseCategory> = emptyList(),
    val selectedTrendEpochDay: Long? = null,
    val selectedCategory: ExpenseCategory? = null,
) {
    /** Compatibility projections for existing screen APIs; expenseFilter remains the source of truth. */
    val searchText: String get() = expenseFilter.keyword
    val categoryFilter: ExpenseCategory? get() = expenseFilter.category
    val historyMonthFilter: YearMonth? get() = expenseFilter.dateRange?.asYearMonthOrNull()
    val activeDateFilterLabel: String? get() = expenseFilter.dateRange?.label()
}

/** 表单状态：amountError 存稳定原因键（同 AmountParseResult.reason），由界面映射中文。 */
data class FormUiState(
    val editingId: Long?,
    val amountText: String,
    val selectedCategory: ExpenseCategory,
    val epochDay: Long,
    val note: String,
    val saving: Boolean,
    val amountError: String?,
    val saveFailed: Boolean,
    val saved: Boolean,
    val confirmDiscard: Boolean = false,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val notFound: Boolean = false,
    val dateError: Boolean = false,
    val copyMode: Boolean = false,
    val copySourceId: Long? = null,
)

/** 详情状态：notFound=true 表示记录不存在（界面显示提示并可返回，不崩溃）。 */
data class DetailUiState(
    val isLoading: Boolean,
    val entry: ExpenseEntry?,
    val notFound: Boolean,
    val confirmDelete: Boolean,
    val deleted: Boolean,
    val loadFailed: Boolean = false,
    val deleteFailed: Boolean = false,
    val isDeleting: Boolean = false,
    val feedbackMessage: String? = null,
)

data class CalendarUiState(
    val month: YearMonth,
    val dailyTotals: List<com.linjincheng.qingji.data.DailyExpenseAggregate> = emptyList(),
    val selectedEpochDay: Long? = null,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val hasLoaded: Boolean = false,
    val hasOpened: Boolean = false,
)

data class BudgetUiState(
    val month: YearMonth,
    val summary: MonthlyBudgetSummary,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val hasLoaded: Boolean = false,
    val isSaving: Boolean = false,
    val feedbackMessage: String? = null,
    val feedbackIsError: Boolean = false,
)

enum class BackupPickerAction { EXPORT, IMPORT }

data class BackupPickerRequest(val id: Long, val action: BackupPickerAction)

/**
 * App-level ViewModel for navigation and expense screen state.
 * 金额一律整数分；统计只在内存重算；数据库写入仅经 ExpenseRepository（内部 Dispatchers.IO）。
 */
class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _home = MutableStateFlow(initialHome())
    val home: StateFlow<HomeUiState> = _home.asStateFlow()

    private val _form = MutableStateFlow(initialForm())
    val form: StateFlow<FormUiState> = _form.asStateFlow()

    private val _detail = MutableStateFlow(initialDetail())
    val detail: StateFlow<DetailUiState> = _detail.asStateFlow()

    private val _calendar = MutableStateFlow(initialCalendar())
    val calendar: StateFlow<CalendarUiState> = _calendar.asStateFlow()

    private val _budget = MutableStateFlow(initialBudget())
    val budget: StateFlow<BudgetUiState> = _budget.asStateFlow()

    private val _backup = MutableStateFlow(BackupScreenState())
    val backup: StateFlow<BackupScreenState> = _backup.asStateFlow()

    private val _backupPickerRequest = MutableStateFlow<BackupPickerRequest?>(null)
    val backupPickerRequest: StateFlow<BackupPickerRequest?> = _backupPickerRequest.asStateFlow()

    /** 返回栈：Form → Detail 或 Home 按进入路径回退；Detail → Home。 */
    private val backStack = ArrayDeque<Screen>()

    /** 当前月流水；未启用搜索/分类时用于首页最近流水。 */
    private var monthEntries: List<ExpenseEntry> = emptyList()

    /** 全部本地流水；搜索或分类筛选启用时用于跨月结果。 */
    private var allEntries: List<ExpenseEntry> = emptyList()

    /** 递增序号用于忽略同月重复刷新时先返回的旧结果。 */
    private var homeRefreshGeneration = 0L

    /** 表单进入时的业务字段快照，用于检测是否有未保存修改。 */
    private var originalFormValues: FormValues? = null

    /** 防止「确认删除」重复触发。 */
    private var deleteInFlight = false

    private var calendarRefreshGeneration = 0L
    private var budgetRefreshGeneration = 0L
    private var budgetWriteInFlight = false
    private var backupPickerRequestId = 0L
    private var backupExportInFlight = false
    private var backupImportReadInFlight = false
    private var backupApplyInFlight = false
    private var pendingBackupPayload: ExpenseBackupPayload? = null

    init {
        refreshHome()
    }

    // ---------- 导航事件 ----------

    /** 新增：screen=Form(null)，表单重置为金额空 / 分类 FOOD / 日期今天 / 备注空。 */
    fun openAdd(defaultCategory: ExpenseCategory = ExpenseCategory.FOOD) {
        backStack.addLast(_screen.value)
        val initial = initialForm().copy(selectedCategory = defaultCategory)
        _form.value = initial
        originalFormValues = initial.toFormValues()
        _screen.value = Screen.Form(null)
    }

    fun openSettings() {
        backStack.addLast(_screen.value)
        _screen.value = Screen.Settings
    }

    fun openCalendar() {
        if (_screen.value != Screen.Calendar) backStack.addLast(_screen.value)
        val current = _calendar.value
        val month = if (current.hasOpened) current.month else YearMonth.of(_home.value.year, _home.value.month)
        val selectedDay = current.selectedEpochDay
            ?.takeIf { YearMonth.from(LocalDate.ofEpochDay(it)) == month }
            ?: month.atDay(1).toEpochDay()
        _calendar.value = current.copy(month = month, selectedEpochDay = selectedDay, hasOpened = true)
        _screen.value = Screen.Calendar
        refreshCalendar()
    }

    fun onCalendarMonthChange(month: YearMonth) {
        if (month.isAfter(YearMonth.now())) return
        val current = _calendar.value
        if (month == current.month && !current.loadFailed) return
        val selectedDay = current.selectedEpochDay
            ?.takeIf { YearMonth.from(LocalDate.ofEpochDay(it)) == month }
            ?: month.atDay(1).toEpochDay()
        _calendar.value = current.copy(
            month = month,
            dailyTotals = emptyList(),
            selectedEpochDay = selectedDay,
            isLoading = true,
            loadFailed = false,
            hasLoaded = false,
        )
        refreshCalendar()
    }

    fun onCalendarDaySelected(epochDay: Long) {
        val date = runCatching { LocalDate.ofEpochDay(epochDay) }.getOrNull() ?: return
        if (YearMonth.from(date) != _calendar.value.month) return
        _calendar.update { it.copy(selectedEpochDay = epochDay) }
    }

    fun retryCalendarLoad() {
        refreshCalendar()
    }

    /** Calendar drill-down writes the day range into the same filter used by charts and the ledger. */
    fun openCalendarDayDetails(epochDay: Long) {
        val date = runCatching { LocalDate.ofEpochDay(epochDay) }.getOrNull() ?: return
        if (YearMonth.from(date) != _calendar.value.month) return
        onCalendarDaySelected(epochDay)
        setExpenseFilter(
            _home.value.expenseFilter.copy(dateRange = EpochDayRange.day(epochDay)),
            forceAllHistory = true,
        )
        backStack.clear()
        _screen.value = Screen.Home
    }

    fun openBudget() {
        if (_screen.value != Screen.Budget) backStack.addLast(_screen.value)
        val month = YearMonth.of(_home.value.year, _home.value.month)
        _screen.value = Screen.Budget
        loadBudgetSummary(month)
    }

    fun onBudgetMonthChange(year: Int, month: Int) {
        val target = runCatching { YearMonth.of(year, month) }.getOrNull() ?: return
        if (budgetWriteInFlight || target == _budget.value.month && !_budget.value.loadFailed) return
        loadBudgetSummary(target)
    }

    fun retryBudgetLoad() {
        loadBudgetSummary(_budget.value.month)
    }

    fun saveBudget(year: Int, month: Int, amountCents: Long) {
        val target = runCatching { YearMonth.of(year, month) }.getOrNull() ?: return
        if (budgetWriteInFlight || target != _budget.value.month) return
        if (amountCents !in 0L..MoneyUtils.MAX_CENTS) {
            _budget.update {
                it.copy(feedbackMessage = "预算金额超出允许范围。", feedbackIsError = true)
            }
            return
        }
        budgetWriteInFlight = true
        _budget.update { it.copy(isSaving = true, feedbackMessage = null, feedbackIsError = false) }
        viewModelScope.launch {
            val result = captureResult {
                val existing = repository.getMonthlyBudget(year, month)
                val now = System.currentTimeMillis().coerceAtLeast(0L)
                val saved = repository.saveMonthlyBudget(
                    MonthlyBudget(
                        year = year,
                        month = month,
                        amountCents = amountCents,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                    ),
                )
                check(saved) { "保存预算失败，请重试。" }
            }
            budgetWriteInFlight = false
            if (result.isFailure) {
                _budget.update {
                    it.copy(
                        isSaving = false,
                        feedbackMessage = "预算保存失败：${result.exceptionOrNull()?.message ?: "请重试"}",
                        feedbackIsError = true,
                    )
                }
            } else if (_budget.value.month == target) {
                loadBudgetSummary(target, feedbackMessage = "预算已保存。")
            }
        }
    }

    fun clearBudget(year: Int, month: Int) {
        val target = runCatching { YearMonth.of(year, month) }.getOrNull() ?: return
        if (budgetWriteInFlight || target != _budget.value.month) return
        budgetWriteInFlight = true
        _budget.update { it.copy(isSaving = true, feedbackMessage = null, feedbackIsError = false) }
        viewModelScope.launch {
            val result = captureResult {
                val deleted = repository.deleteMonthlyBudget(year, month)
                check(deleted) { "本月预算已不存在，请刷新后重试。" }
            }
            budgetWriteInFlight = false
            if (result.isFailure) {
                _budget.update {
                    it.copy(
                        isSaving = false,
                        feedbackMessage = "预算清除失败：${result.exceptionOrNull()?.message ?: "请重试"}",
                        feedbackIsError = true,
                    )
                }
            } else if (_budget.value.month == target) {
                loadBudgetSummary(target, feedbackMessage = "本月预算已清除。")
            }
        }
    }

    fun dismissBudgetFeedback() {
        _budget.update { it.copy(feedbackMessage = null, feedbackIsError = false) }
    }

    fun openBudgetExpenses(year: Int, month: Int) {
        val target = runCatching { YearMonth.of(year, month) }.getOrNull() ?: return
        setExpenseFilter(
            _home.value.expenseFilter.copy(dateRange = EpochDayRange.month(target)),
            forceAllHistory = true,
        )
        backStack.clear()
        _screen.value = Screen.Home
    }

    fun openBackup() {
        pendingBackupPayload = null
        _backup.update { BackupScreenState() }
        if (_screen.value != Screen.Backup) backStack.addLast(_screen.value)
        _screen.value = Screen.Backup
    }

    fun requestBackupExport() = requestBackupPicker(BackupPickerAction.EXPORT)

    fun requestBackupImport() = requestBackupPicker(BackupPickerAction.IMPORT)

    private fun requestBackupPicker(action: BackupPickerAction) {
        val current = if (_screen.value == Screen.Backup) _backup.value else BackupScreenState()
        if (current.isExporting || current.isImporting) return
        if (_screen.value != Screen.Backup) {
            pendingBackupPayload = null
            _backup.value = BackupScreenState()
            backStack.addLast(_screen.value)
            _screen.value = Screen.Backup
        }
        pendingBackupPayload = null
        backupPickerRequestId += 1L
        _backupPickerRequest.value = BackupPickerRequest(backupPickerRequestId, action)
        _backup.value = when (action) {
            BackupPickerAction.EXPORT -> current.copy(isExporting = true, importPreview = null, feedback = null)
            BackupPickerAction.IMPORT -> current.copy(isImporting = true, importPreview = null, feedback = null)
        }
    }

    fun claimBackupPickerRequest(id: Long): BackupPickerRequest? {
        val request = _backupPickerRequest.value ?: return null
        if (request.id != id) return null
        _backupPickerRequest.value = null
        return request
    }

    fun onBackupExportPickerCancelled() {
        if (backupExportInFlight || !prepareBackupPickerResult(BackupPickerAction.EXPORT)) return
        _backup.update {
            it.copy(isExporting = false, feedback = BackupScreenFeedback("已取消导出。", isError = false))
        }
    }

    fun onBackupImportPickerCancelled() {
        if (backupImportReadInFlight || !prepareBackupPickerResult(BackupPickerAction.IMPORT)) return
        _backup.update {
            it.copy(isImporting = false, feedback = BackupScreenFeedback("已取消恢复。", isError = false))
        }
    }

    fun exportBackup(appVersion: String, writeDocument: suspend (ByteArray) -> Unit) {
        if (backupExportInFlight || !prepareBackupPickerResult(BackupPickerAction.EXPORT)) return
        backupExportInFlight = true
        viewModelScope.launch {
            val result = captureResult {
                val expenses = repository.getAllExpenses()
                val budgets = repository.getAllMonthlyBudgets()
                val bytes = ExpenseBackupCodec.encode(
                    expenses = expenses,
                    budgets = budgets,
                    appVersion = appVersion,
                    exportedAt = Instant.now(),
                )
                writeDocument(bytes)
            }
            backupExportInFlight = false
            _backup.update {
                it.copy(
                    isExporting = false,
                    feedback = result.fold(
                        onSuccess = { BackupScreenFeedback("备份已导出。", isError = false) },
                        onFailure = { error -> BackupScreenFeedback("导出失败：${error.message ?: "无法写入文件"}", isError = true) },
                    ),
                )
            }
        }
    }

    fun onBackupFileSelected(readDocument: suspend () -> ExpenseBackupPayload) {
        if (backupImportReadInFlight || !prepareBackupPickerResult(BackupPickerAction.IMPORT)) return
        backupImportReadInFlight = true
        viewModelScope.launch {
            val result = captureResult {
                val payload = readDocument()
                val existingIds = repository.getAllExpenses().mapTo(HashSet()) { it.backupId.lowercase(Locale.ROOT) }
                payload to payload.preview(existingIds)
            }
            backupImportReadInFlight = false
            result.fold(
                onSuccess = { (payload, preview) ->
                    pendingBackupPayload = payload
                    _backup.value = _backup.value.copy(
                        isImporting = false,
                        importPreview = preview,
                        feedback = null,
                    )
                },
                onFailure = { error ->
                    pendingBackupPayload = null
                    _backup.value = _backup.value.copy(
                        isImporting = false,
                        importPreview = null,
                        feedback = BackupScreenFeedback(
                            "备份读取或校验失败：${error.message ?: "文件无效"}",
                            isError = true,
                        ),
                    )
                },
            )
        }
    }

    fun confirmBackupImport() {
        val payload = pendingBackupPayload ?: run {
            _backup.update {
                it.copy(feedback = BackupScreenFeedback("没有可确认的备份，请重新选择文件。", isError = true))
            }
            return
        }
        if (backupApplyInFlight || _backup.value.importPreview == null || _backup.value.isImporting) return
        backupApplyInFlight = true
        _backup.update { it.copy(isImporting = true, feedback = null) }
        viewModelScope.launch {
            val result = captureResult { repository.applyBackupMerge(payload.expenses, payload.budgets) }
            backupApplyInFlight = false
            result.fold(
                onSuccess = { applied ->
                    pendingBackupPayload = null
                    _backup.update {
                        it.copy(
                            isImporting = false,
                            importPreview = null,
                            feedback = BackupScreenFeedback(
                                "恢复完成：新增 ${applied.insertedExpenses} 笔，跳过 ${applied.skippedExpenses} 笔，覆盖 ${applied.upsertedBudgets} 个月预算。",
                                isError = false,
                            ),
                        )
                    }
                    refreshHome()
                    if (_calendar.value.hasLoaded) refreshCalendar()
                    if (_budget.value.hasLoaded) loadBudgetSummary(_budget.value.month)
                },
                onFailure = { error ->
                    _backup.update {
                        it.copy(
                            isImporting = false,
                            feedback = BackupScreenFeedback(
                                "恢复失败，数据未完成合并：${error.message ?: "请重试"}",
                                isError = true,
                            ),
                        )
                    }
                },
            )
        }
    }

    fun cancelBackupImport() {
        if (_backup.value.isImporting || backupApplyInFlight) return
        pendingBackupPayload = null
        _backup.update {
            it.copy(importPreview = null, feedback = BackupScreenFeedback("已取消本次恢复。", isError = false))
        }
    }

    fun dismissBackupFeedback() {
        _backup.update { it.copy(feedback = null) }
    }

    /** Activity Result may be redelivered after process recreation, when the ViewModel state is new. */
    private fun prepareBackupPickerResult(action: BackupPickerAction): Boolean {
        if (_screen.value != Screen.Backup) {
            backStack.addLast(_screen.value)
            _screen.value = Screen.Backup
            _backup.value = BackupScreenState()
            pendingBackupPayload = null
        }
        val current = _backup.value
        if (current.isExporting || current.isImporting) {
            return when (action) {
                BackupPickerAction.EXPORT -> current.isExporting
                BackupPickerAction.IMPORT -> current.isImporting
            }
        }
        _backup.value = when (action) {
            BackupPickerAction.EXPORT -> current.copy(isExporting = true, importPreview = null)
            BackupPickerAction.IMPORT -> current.copy(isImporting = true, importPreview = null)
        }
        return true
    }

    /** 编辑：screen=Form(id)，读取中不开放空表单，失败或记录缺失时给出可理解的状态。 */
    fun openEdit(id: Long) {
        if (_screen.value !is Screen.Detail || _detail.value.isDeleting) return
        backStack.addLast(_screen.value)
        val initial = initialForm().copy(editingId = id, isLoading = true)
        _form.value = initial
        originalFormValues = null
        _screen.value = Screen.Form(id)
        viewModelScope.launch {
            loadEditEntry(id)
        }
    }

    /** 复制从详情进入：保留来源记录作为返回目标，复制表单始终按新增处理。 */
    fun openCopy(id: Long) {
        val detail = _detail.value
        if (_screen.value !is Screen.Detail || detail.entry?.id != id || detail.isDeleting) return
        backStack.addLast(_screen.value)
        val initial = initialForm().copy(copyMode = true, copySourceId = id, isLoading = true)
        _form.value = initial
        originalFormValues = null
        _screen.value = Screen.Form(null)
        viewModelScope.launch { loadCopyEntry(id) }
    }

    /** 重试编辑记录的读取；保存仍不可用，直到数据库数据成功载入。 */
    fun retryEditLoad() {
        retryFormLoad()
    }

    /** 重试当前编辑或复制表单的源记录读取。 */
    fun retryFormLoad() {
        if (_form.value.copyMode) {
            retryCopyLoad()
            return
        }
        val id = _form.value.editingId ?: return
        if (_screen.value != Screen.Form(id)) return
        _form.update { it.copy(isLoading = true, loadFailed = false, notFound = false) }
        viewModelScope.launch { loadEditEntry(id) }
    }

    private fun retryCopyLoad() {
        val id = _form.value.copySourceId ?: return
        if (_screen.value != Screen.Form(null)) return
        _form.update { it.copy(isLoading = true, loadFailed = false, notFound = false) }
        viewModelScope.launch { loadCopyEntry(id) }
    }

    /** 详情：screen=Detail(id) 并异步加载；记录不存在 → notFound=true，不崩溃。 */
    fun openDetail(id: Long) {
        backStack.addLast(_screen.value)
        _screen.value = Screen.Detail(id)
        loadDetail(id)
    }

    /** 返回上一屏（Form→回 Detail 或 Home，按进入路径；Detail→Home）。 */
    fun back() {
        if (_screen.value is Screen.Detail && _detail.value.isDeleting) return
        if (_screen.value is Screen.Backup &&
            (_backup.value.isExporting || _backup.value.isImporting || backupApplyInFlight)
        ) return
        if (_screen.value is Screen.Form) {
            originalFormValues = null
            _form.update { it.copy(confirmDiscard = false) }
        }
        if (_screen.value is Screen.Backup) {
            pendingBackupPayload = null
            _backupPickerRequest.value = null
        }
        val target = backStack.removeLastOrNull() ?: Screen.Home
        _screen.value = target
        if (target is Screen.Detail) loadDetail(target.expenseId)
    }

    // ---------- 首页事件 ----------

    /** 搜索备注、分类、日期或金额文本；有搜索/筛选时在全部历史流水中匹配。 */
    fun onSearchTextChange(text: String) {
        setExpenseFilter(_home.value.expenseFilter.copy(keyword = text))
    }

    fun retryHome() {
        refreshHome()
    }

    fun clearHomeFeedback() {
        _home.update { it.copy(feedbackMessage = null) }
    }

    fun clearDetailFeedback() {
        _detail.update { it.copy(feedbackMessage = null) }
    }

    /** 单分类筛选；传 null 清除筛选。 */
    fun onCategoryFilterChange(category: ExpenseCategory?) {
        setExpenseFilter(_home.value.expenseFilter.copy(category = category))
    }

    /** 账单页月份过滤；条件保存在 ViewModel 中，返回详情时不会丢失。 */
    fun onHistoryMonthFilterChange(month: YearMonth?) {
        setExpenseFilter(
            _home.value.expenseFilter.copy(dateRange = month?.let(EpochDayRange::month)),
        )
    }

    /** 日历或图表将同一套日期/类别/关键词条件带入全历史账单。 */
    fun openFilteredLedger(filter: ExpenseFilter) {
        setExpenseFilter(filter, forceAllHistory = true)
    }

    /** 图表选日带入单日区间，并保留账单上已有的类别和关键词条件。 */
    fun openDateDetails(epochDay: Long) {
        openFilteredLedger(_home.value.expenseFilter.copy(dateRange = EpochDayRange.day(epochDay)))
    }

    /** 分类图表钻取限定在当前分析月份，并保留已有关键词条件。 */
    fun openCategoryDetails(category: ExpenseCategory) {
        val current = _home.value
        openFilteredLedger(
            ExpenseFilter(
                dateRange = EpochDayRange.month(YearMonth.of(current.year, current.month)),
                category = category,
                keyword = current.expenseFilter.keyword,
            ),
        )
    }

    fun selectTrendEpochDay(epochDay: Long?) {
        _home.update { it.copy(selectedTrendEpochDay = epochDay) }
    }

    fun selectCategory(category: ExpenseCategory?) {
        _home.update { it.copy(selectedCategory = category) }
    }

    fun onExpenseDateRangeChange(range: EpochDayRange?) {
        setExpenseFilter(_home.value.expenseFilter.copy(dateRange = range))
    }

    fun clearExpenseFilter() {
        setExpenseFilter(ExpenseFilter())
    }

    /** 全流水页按全历史展示；离开后无筛选时首页状态恢复按月范围，已有筛选保持。 */
    fun onHistoryTabSelected(showAllHistory: Boolean) {
        _home.update { current ->
            val useAllHistory = showAllHistory || !current.expenseFilter.isEmpty
            current.copy(
                showAllHistory = showAllHistory,
                entries = filteredEntries(current.expenseFilter, useAllHistory),
            )
        }
    }

    private fun setExpenseFilter(filter: ExpenseFilter, forceAllHistory: Boolean = false) {
        _home.update { current ->
            val useAllHistory = forceAllHistory || current.showAllHistory || !filter.isEmpty
            current.copy(
                expenseFilter = filter,
                showAllHistory = current.showAllHistory || forceAllHistory,
                entries = filteredEntries(filter, useAllHistory),
                historyMonthOptions = (
                    ExpenseAnalytics.availableMonths(allEntries) + listOfNotNull(filter.dateRange?.asYearMonthOrNull())
                ).distinct().sortedDescending(),
            )
        }
    }

    private fun filteredEntries(filter: ExpenseFilter, useAllHistory: Boolean): List<ExpenseEntry> =
        filter.apply(if (useAllHistory) allEntries else monthEntries)

    /** 切月：只改查询条件并重算统计，不写库。 */
    fun changeMonth(delta: Int) {
        val current = _home.value
        val targetMonth = YearMonth.of(current.year, current.month).plusMonths(delta.toLong())
        if (targetMonth.isAfter(YearMonth.now())) return
        val (year, month) = DateUtils.adjustMonth(current.year, current.month, delta)
        _home.value = current.copy(
            year = year,
            month = month,
            monthTitle = DateUtils.monthTitle(year, month),
            totalCents = 0L,
            categoryStats = emptyCategoryStats(),
            entries = emptyList(),
            recentEntries = emptyList(),
            previousMonthTotalCents = 0L,
            comparisonCurrentTotalCents = 0L,
            comparisonLabel = "较上月",
            dailyTrend = emptyList(),
            dailyTrend30 = emptyList(),
            selectedTrendEpochDay = null,
            selectedCategory = null,
            hasMonthEntries = false,
            monthEntryCount = 0,
            loadFailed = false,
            hasLoaded = false,
            isLoading = true,
        )
        monthEntries = emptyList()
        refreshHome()
    }

    // ---------- 表单事件 ----------

    fun onAmountChange(text: String) {
        _form.update { it.copy(amountText = text, amountError = null) }
    }

    fun onCategorySelect(category: ExpenseCategory) {
        _form.update { it.copy(selectedCategory = category) }
    }

    fun onDateChange(epochDay: Long) {
        val today = DateUtils.todayEpochDay()
        _form.update {
            if (epochDay > today) {
                it.copy(dateError = true)
            } else {
                it.copy(epochDay = epochDay, dateError = false)
            }
        }
    }

    fun onNoteChange(text: String) {
        _form.update { it.copy(note = text) }
    }

    /** 未修改直接返回；有任何业务字段变化时交由确认/取消回调决定。 */
    fun requestFormBack() {
        if (_screen.value !is Screen.Form) return
        val current = _form.value
        if (current.saving) return
        val original = originalFormValues
        if (original == null || current.toFormValues() == original) {
            back()
        } else {
            _form.update { it.copy(confirmDiscard = true) }
        }
    }

    fun dismissDiscardDialog() {
        _form.update { it.copy(confirmDiscard = false) }
    }

    fun confirmDiscardChanges() {
        if (_screen.value !is Screen.Form || _form.value.saving) return
        _form.update { it.copy(confirmDiscard = false) }
        back()
    }

    /**
     * Save the current form:
     * 校验失败 → amountError 置原因键（同 AmountParseResult.reason）；
     * saving=true 期间忽略重复提交；
     * On success, set saved=true, return to the appropriate screen, and refresh monthly statistics;
     * 失败（写库异常或原记录已不存在）→ saveFailed=true。
     */
    fun saveExpense() {
        val form = _form.value
        if (form.saving || form.isLoading || form.loadFailed || form.notFound) return
        if (form.epochDay > DateUtils.todayEpochDay()) {
            _form.update { it.copy(dateError = true) }
            return
        }
        val editingId = form.editingId
        when (val parsed = MoneyUtils.parseAmountToCents(form.amountText)) {
            is AmountParseResult.Invalid -> {
                _form.update { it.copy(amountError = parsed.reason) }
            }

            is AmountParseResult.Valid -> {
                val cents = parsed.cents
                _form.update { it.copy(saving = true, amountError = null, saveFailed = false) }
                viewModelScope.launch {
                    val now = System.currentTimeMillis()
                    val success = runCatching {
                        if (editingId == null) {
                            val entry = ExpenseEntry(
                                amountCents = cents,
                                category = form.selectedCategory,
                                note = form.note,
                                expenseDate = form.epochDay,
                                createdAt = now,
                                updatedAt = now,
                            )
                            repository.insertExpense(entry) > 0L
                        } else {
                            val existing = repository.getExpenseById(editingId)
                                ?: return@runCatching false
                            // 编辑不得改变 createdAt：copy 只覆盖内容字段，updatedAt 刷新
                            val updated = existing.copy(
                                amountCents = cents,
                                category = form.selectedCategory,
                                note = form.note,
                                expenseDate = form.epochDay,
                                updatedAt = now,
                            )
                            repository.updateExpense(updated)
                        }
                    }.getOrDefault(false)
                    if (success) {
                        _form.update { it.copy(saving = false, saved = true) }
                        originalFormValues = null
                        if (form.copyMode) {
                            val target = backStack.removeLastOrNull()
                            if (target is Screen.Detail && target.expenseId == form.copySourceId) {
                                _screen.value = target
                                loadDetail(target.expenseId, feedbackMessage = "已复制为新记录")
                            } else {
                                backStack.clear()
                                _screen.value = Screen.Home
                                _home.update { it.copy(feedbackMessage = "已复制为新记录") }
                            }
                        } else if (editingId == null) {
                            backStack.clear()
                            _screen.value = Screen.Home
                            _home.update { it.copy(feedbackMessage = "消费已保存") }
                        } else {
                            val popped = backStack.removeLastOrNull()
                            _screen.value = if (popped is Screen.Detail && popped.expenseId == editingId) {
                                popped
                            } else {
                                Screen.Detail(editingId)
                            }
                            loadDetail(editingId, feedbackMessage = "修改已保存")
                        }
                        refreshHome()
                        refreshLinkedData()
                    } else {
                        _form.update { it.copy(saving = false, saveFailed = true) }
                    }
                }
            }
        }
    }

    // ---------- 详情事件 ----------

    fun onDeleteClick() {
        val state = _detail.value
        if (state.isLoading || state.isDeleting || state.entry == null) return
        _detail.update { it.copy(confirmDelete = true) }
    }

    fun onDismissDeleteDialog() {
        _detail.update { it.copy(confirmDelete = false) }
    }

    /** 删除：成功 → deleted=true、回首页、重算当月统计；失败或记录已不存在 → notFound=true，不崩溃。 */
    fun onConfirmDelete() {
        val state = _detail.value
        val entry = state.entry ?: return
        if (deleteInFlight || state.isDeleting) return
        deleteInFlight = true
        _detail.update { it.copy(confirmDelete = false, isDeleting = true, deleteFailed = false) }
        viewModelScope.launch {
            val result = runCatching { repository.deleteExpense(entry.id) }
            deleteInFlight = false
            when {
                result.getOrNull() == true -> {
                    _detail.update { it.copy(isDeleting = false, deleted = true, entry = null) }
                    backStack.clear()
                    _screen.value = Screen.Home
                    _home.update { it.copy(feedbackMessage = "消费已删除") }
                    refreshHome()
                    refreshLinkedData()
                }

                result.isSuccess -> {
                    // 影响行数为 0 代表记录已被移除，重新读取以显示真实状态。
                    loadDetail(entry.id)
                }

                else -> {
                    _detail.update { it.copy(isDeleting = false, deleteFailed = true) }
                }
            }
        }
    }

    // ---------- 内部实现 ----------

    private fun refreshCalendar() {
        val month = _calendar.value.month
        val generation = ++calendarRefreshGeneration
        val range = EpochDayRange.month(month)
        _calendar.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            val result = captureResult {
                repository.getDailyExpenseAggregates(range.startInclusive, range.endExclusive)
            }
            if (generation != calendarRefreshGeneration || _calendar.value.month != month) return@launch
            result.fold(
                onSuccess = { totals ->
                    _calendar.update {
                        it.copy(dailyTotals = totals, isLoading = false, loadFailed = false, hasLoaded = true)
                    }
                },
                onFailure = {
                    _calendar.update { it.copy(dailyTotals = emptyList(), isLoading = false, loadFailed = true) }
                },
            )
        }
    }

    private fun loadBudgetSummary(month: YearMonth, feedbackMessage: String? = null) {
        val generation = ++budgetRefreshGeneration
        val placeholder = MonthlyBudgetSummary.calculate(month.year, month.monthValue, null, spentCents = 0L)
        _budget.value = BudgetUiState(
            month = month,
            summary = placeholder,
            isLoading = true,
            isSaving = budgetWriteInFlight,
            feedbackMessage = feedbackMessage,
        )
        viewModelScope.launch {
            val result = captureResult {
                val budget = repository.getMonthlyBudget(month.year, month.monthValue)
                val spentCents = repository.getMonthlyTotal(month.year, month.monthValue)
                MonthlyBudgetSummary.calculate(month.year, month.monthValue, budget, spentCents)
            }
            if (generation != budgetRefreshGeneration || _budget.value.month != month) return@launch
            result.fold(
                onSuccess = { summary ->
                    _budget.update {
                        it.copy(summary = summary, isLoading = false, loadFailed = false, hasLoaded = true)
                    }
                },
                onFailure = { error ->
                    _budget.update {
                        it.copy(
                            isLoading = false,
                            loadFailed = true,
                            feedbackMessage = "预算读取失败：${error.message ?: "请重试"}",
                            feedbackIsError = true,
                        )
                    }
                },
            )
        }
    }

    private fun refreshLinkedData() {
        if (_calendar.value.hasLoaded) refreshCalendar()
        if (_budget.value.hasLoaded) loadBudgetSummary(_budget.value.month)
    }

    private suspend fun <T> captureResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    fun retryDetailLoad() {
        val current = _screen.value as? Screen.Detail ?: return
        loadDetail(current.expenseId)
    }

    private suspend fun loadEditEntry(id: Long) {
        val result = runCatching { repository.getExpenseById(id) }
        if (_screen.value != Screen.Form(id) || _form.value.editingId != id) return
        val entry = result.getOrNull()
        if (result.isFailure) {
            _form.update { it.copy(isLoading = false, loadFailed = true) }
        } else if (entry == null) {
            _form.update { it.copy(isLoading = false, notFound = true) }
        } else {
            val values = entry.toFormValues()
            originalFormValues = values
            _form.update {
                it.copy(
                    amountText = values.amountText,
                    selectedCategory = values.selectedCategory,
                    epochDay = values.epochDay,
                    note = values.note,
                    isLoading = false,
                    loadFailed = false,
                    notFound = false,
                )
            }
        }
    }

    private suspend fun loadCopyEntry(id: Long) {
        val result = runCatching { repository.getExpenseById(id) }
        if (_screen.value != Screen.Form(null) ||
            !_form.value.copyMode ||
            _form.value.copySourceId != id
        ) return
        val entry = result.getOrNull()
        when {
            result.isFailure -> _form.update { it.copy(isLoading = false, loadFailed = true) }
            entry == null -> _form.update { it.copy(isLoading = false, notFound = true) }
            else -> {
                val todayValues = entry.toFormValues().copy(epochDay = DateUtils.todayEpochDay())
                originalFormValues = todayValues
                _form.update {
                    it.copy(
                        editingId = null,
                        amountText = todayValues.amountText,
                        selectedCategory = todayValues.selectedCategory,
                        epochDay = todayValues.epochDay,
                        note = todayValues.note,
                        isLoading = false,
                        loadFailed = false,
                        notFound = false,
                    )
                }
            }
        }
    }

    private fun loadDetail(id: Long, feedbackMessage: String? = null) {
        _detail.value = DetailUiState(
            isLoading = true,
            entry = null,
            notFound = false,
            confirmDelete = false,
            deleted = false,
            feedbackMessage = feedbackMessage,
        )
        viewModelScope.launch {
            val result = runCatching { repository.getExpenseById(id) }
            val currentScreen = _screen.value
            // 仅当仍停留在该详情页时才落状态，避免慢请求覆盖后续页面
            if (currentScreen is Screen.Detail && currentScreen.expenseId == id) {
                val entry = result.getOrNull()
                _detail.value = DetailUiState(
                    isLoading = false,
                    entry = entry,
                    notFound = result.isSuccess && entry == null,
                    confirmDelete = false,
                    deleted = false,
                    loadFailed = result.isFailure,
                    feedbackMessage = feedbackMessage,
                )
            }
        }
    }

    private fun refreshHome() {
        val requestedState = _home.value
        val year = requestedState.year
        val month = requestedState.month
        val generation = ++homeRefreshGeneration
        val today = LocalDate.now()
        val trendEndEpochDay = if (today.year == year && today.monthValue == month) {
            today.toEpochDay()
        } else {
            YearMonth.of(year, month).atEndOfMonth().toEpochDay()
        }
        val trendStartEpochDay = trendEndEpochDay - (ExpenseAnalytics.TREND_DAY_COUNT - 1L)
        val monthStartEpochDay = DateUtils.monthStartEpochDay(year, month)
        val (previousYear, previousMonth) = DateUtils.adjustMonth(year, month, -1)
        val pendingTrend = if (requestedState.dailyTrend.size == ExpenseAnalytics.TREND_DAY_COUNT) {
            requestedState.dailyTrend
        } else {
            ExpenseAnalytics.dailyTotals(emptyList(), trendEndEpochDay)
        }
        _home.value = requestedState.copy(isLoading = true, loadFailed = false, dailyTrend = pendingTrend)
        viewModelScope.launch {
            val result = runCatching {
                val entries = repository.getExpensesByMonth(year, month)
                val allEntries = repository.getAllExpenses()
                val previousEntries = allEntries.filter { it.expenseDate < monthStartEpochDay &&
                    it.expenseDate >= DateUtils.monthStartEpochDay(previousYear, previousMonth) }
                val needsPreviousMonthEntries = trendStartEpochDay < monthStartEpochDay
                val trendEntries = if (needsPreviousMonthEntries) previousEntries + entries else entries
                val comparison = ExpenseAnalytics.compareMonths(
                    currentEntries = entries,
                    previousEntries = previousEntries,
                    selectedMonth = YearMonth.of(year, month),
                    today = today,
                )
                HomeQuery(
                    entries = entries,
                    allEntries = allEntries,
                    totalCents = repository.getMonthlyTotal(year, month),
                    categoryTotals = repository.getMonthlyCategoryTotals(year, month),
                    previousMonthTotalCents = comparison.previousCents,
                    comparisonCurrentTotalCents = comparison.currentCents,
                    comparisonLabel = comparison.label,
                    trendEntries = trendEntries,
                    trendEndEpochDay = trendEndEpochDay,
                )
            }.getOrNull()
            if (generation != homeRefreshGeneration) return@launch
            val current = _home.value
            // 月份已切换或同月已有更新查询时，丢弃过期结果。
            if (current.year != year || current.month != month) return@launch
            if (result == null) {
                // 查询失败：显式标记失败，界面不得把未知状态伪装成空月账本。
                _home.value = current.copy(isLoading = false, loadFailed = true)
                return@launch
            }

            monthEntries = result.entries
            allEntries = result.allEntries
            val useAllHistory = current.showAllHistory || !current.expenseFilter.isEmpty
            _home.value = current.copy(
                totalCents = result.totalCents,
                categoryStats = buildCategoryStats(result.categoryTotals),
                entries = current.expenseFilter.apply(if (useAllHistory) result.allEntries else result.entries),
                recentEntries = result.entries,
                hasAnyEntries = result.allEntries.isNotEmpty(),
                historyMonthOptions = (
                    ExpenseAnalytics.availableMonths(result.allEntries) +
                        listOfNotNull(current.expenseFilter.dateRange?.asYearMonthOrNull())
                ).distinct().sortedDescending(),
                recentCategories = result.allEntries
                    .sortedByDescending(ExpenseEntry::updatedAt)
                    .distinctBy(ExpenseEntry::category)
                    .take(3)
                    .map(ExpenseEntry::category),
                previousMonthTotalCents = result.previousMonthTotalCents,
                comparisonCurrentTotalCents = result.comparisonCurrentTotalCents,
                comparisonLabel = result.comparisonLabel,
                dailyTrend = ExpenseAnalytics.dailyTotals(
                    entries = result.trendEntries,
                    endEpochDay = result.trendEndEpochDay,
                ),
                dailyTrend30 = ExpenseAnalytics.dailyTotals(
                    entries = result.allEntries,
                    endEpochDay = result.trendEndEpochDay,
                    dayCount = 30,
                ),
                hasMonthEntries = result.entries.isNotEmpty(),
                monthEntryCount = result.entries.size,
                loadFailed = false,
                hasLoaded = true,
                isLoading = false,
            )
        }
    }

    private fun initialHome(): HomeUiState {
        val today = LocalDate.ofEpochDay(DateUtils.todayEpochDay())
        return HomeUiState(
            year = today.year,
            month = today.monthValue,
            monthTitle = DateUtils.monthTitle(today.year, today.monthValue),
            totalCents = 0L,
            categoryStats = emptyCategoryStats(),
            entries = emptyList(),
            isLoading = true,
        )
    }

    private fun initialCalendar(): CalendarUiState {
        val today = LocalDate.now()
        return CalendarUiState(
            month = YearMonth.from(today),
            selectedEpochDay = today.toEpochDay(),
            isLoading = true,
        )
    }

    private fun initialBudget(): BudgetUiState {
        val month = YearMonth.from(LocalDate.now())
        return BudgetUiState(
            month = month,
            summary = MonthlyBudgetSummary.calculate(month.year, month.monthValue, null, spentCents = 0L),
        )
    }

    private fun initialForm(): FormUiState = FormUiState(
        editingId = null,
        amountText = "",
        selectedCategory = ExpenseCategory.FOOD,
        epochDay = DateUtils.todayEpochDay(),
        note = "",
        saving = false,
        amountError = null,
        saveFailed = false,
        saved = false,
    )

    private fun initialDetail(): DetailUiState = DetailUiState(
        isLoading = false,
        entry = null,
        notFound = false,
        confirmDelete = false,
        deleted = false,
    )

    private fun emptyCategoryStats(): List<CategoryStat> =
        ExpenseCategory.entries.map { CategoryStat(it, 0L, 0) }

    /** 全部 5 类：有金额的按金额降序（同额按分类固定顺序），0 元类排最后（保持固定顺序）。 */
    private fun buildCategoryStats(totals: Map<ExpenseCategory, Long>): List<CategoryStat> {
        val percentages = ExpenseAnalytics.categoryPercentages(totals)
        val positive = ExpenseCategory.entries.mapNotNull { category ->
            val total = totals[category] ?: return@mapNotNull null
            if (total <= 0L) return@mapNotNull null
            CategoryStat(category, total, percentages[category] ?: 0)
        }.sortedWith(
            compareByDescending<CategoryStat> { it.totalCents }.thenBy { it.category.ordinal },
        )
        val zeros = ExpenseCategory.entries
            .filter { (totals[it] ?: 0L) <= 0L }
            .map { CategoryStat(it, 0L, 0) }
        return positive + zeros
    }

    companion object {
        /**
         * Build the ViewModel with the app context's SQLite-backed repository:
         * ExpenseViewModel(ExpenseRepositoryImpl(ExpenseDatabaseHelper(context.applicationContext)))。
         */
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
                        val repository: ExpenseRepository =
                            ExpenseRepositoryImpl(ExpenseDatabaseHelper(appContext))
                        return ExpenseViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}

/** 首页一次查询的结果集合（私有装配结构）。 */
private data class HomeQuery(
    val entries: List<ExpenseEntry>,
    val allEntries: List<ExpenseEntry>,
    val totalCents: Long,
    val categoryTotals: Map<ExpenseCategory, Long>,
    val previousMonthTotalCents: Long,
    val comparisonCurrentTotalCents: Long,
    val comparisonLabel: String,
    val trendEntries: List<ExpenseEntry>,
    val trendEndEpochDay: Long,
)

/** 与编辑、搜索状态等 UI 字段无关的表单业务值快照。 */
private data class FormValues(
    val amountText: String,
    val selectedCategory: ExpenseCategory,
    val epochDay: Long,
    val note: String,
)

private fun FormUiState.toFormValues(): FormValues = FormValues(
    amountText = amountText,
    selectedCategory = selectedCategory,
    epochDay = epochDay,
    note = note,
)

private fun ExpenseEntry.toFormValues(): FormValues = FormValues(
    amountText = MoneyUtils.formatCents(amountCents),
    selectedCategory = category,
    epochDay = expenseDate,
    note = note,
)
