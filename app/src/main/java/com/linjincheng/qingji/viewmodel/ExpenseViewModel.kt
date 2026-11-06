package com.linjincheng.qingji.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.data.ExpenseDatabaseHelper
import com.linjincheng.qingji.data.ExpenseEntry
import com.linjincheng.qingji.data.ExpenseRepository
import com.linjincheng.qingji.data.ExpenseRepositoryImpl
import com.linjincheng.qingji.util.AmountParseResult
import com.linjincheng.qingji.util.DateUtils
import com.linjincheng.qingji.util.DailyExpenseTotal
import com.linjincheng.qingji.util.ExpenseAnalytics
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

/**
 * Secondary screen routes are represented as state; the app does not use Navigation Compose.
 */
sealed interface Screen {
    data object Home : Screen

    data object Settings : Screen

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
    val searchText: String = "",
    val categoryFilter: ExpenseCategory? = null,
    val historyMonthFilter: YearMonth? = null,
    val historyMonthOptions: List<YearMonth> = emptyList(),
    val hasMonthEntries: Boolean = false,
    val monthEntryCount: Int = 0,
    val loadFailed: Boolean = false,
    val feedbackMessage: String? = null,
    val hasLoaded: Boolean = false,
)

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

    init {
        refreshHome()
    }

    /**
     * Fetch a fresh SQLite snapshot only after the user has chosen an export
     * destination. Do not export the filtered/masked UI list or cached state.
     */
    suspend fun getAllExpensesForExport(): List<ExpenseEntry> = repository.getAllExpenses()

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

    /** 重试编辑记录的读取；保存仍不可用，直到数据库数据成功载入。 */
    fun retryEditLoad() {
        val id = _form.value.editingId ?: return
        if (_screen.value != Screen.Form(id)) return
        _form.update { it.copy(isLoading = true, loadFailed = false, notFound = false) }
        viewModelScope.launch { loadEditEntry(id) }
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
        if (_screen.value is Screen.Form) {
            originalFormValues = null
            _form.update { it.copy(confirmDiscard = false) }
        }
        val target = backStack.removeLastOrNull() ?: Screen.Home
        _screen.value = target
        if (target is Screen.Detail) loadDetail(target.expenseId)
    }

    // ---------- 首页事件 ----------

    /** 搜索备注、分类、日期或金额文本；有搜索/筛选时在全部历史流水中匹配。 */
    fun onSearchTextChange(text: String) {
        _home.update { current ->
            val useAllHistory = current.showAllHistory || text.isNotBlank() ||
                current.categoryFilter != null || current.historyMonthFilter != null
            current.copy(
                searchText = text,
                showAllHistory = useAllHistory,
                entries = ExpenseAnalytics.filterEntries(
                    if (useAllHistory) allEntries else monthEntries,
                    text,
                    current.categoryFilter,
                    current.historyMonthFilter,
                ),
            )
        }
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
        _home.update { current ->
            val useAllHistory = current.showAllHistory || current.searchText.isNotBlank() ||
                category != null || current.historyMonthFilter != null
            current.copy(
                categoryFilter = category,
                showAllHistory = useAllHistory,
                entries = ExpenseAnalytics.filterEntries(
                    if (useAllHistory) allEntries else monthEntries,
                    current.searchText,
                    category,
                    current.historyMonthFilter,
                ),
            )
        }
    }

    /** 账单页月份过滤；条件保存在 ViewModel 中，返回详情时不会丢失。 */
    fun onHistoryMonthFilterChange(month: YearMonth?) {
        _home.update { current ->
            val useAllHistory = current.showAllHistory || current.searchText.isNotBlank() ||
                current.categoryFilter != null || month != null
            current.copy(
                historyMonthFilter = month,
                showAllHistory = useAllHistory,
                entries = ExpenseAnalytics.filterEntries(
                    if (useAllHistory) allEntries else monthEntries,
                    current.searchText,
                    current.categoryFilter,
                    month,
                ),
            )
        }
    }

    /** 全流水页按全历史展示；离开后无筛选时首页状态恢复按月范围，已有筛选保持。 */
    fun onHistoryTabSelected(showAllHistory: Boolean) {
        _home.update { current ->
            val useAllHistory = showAllHistory || current.searchText.isNotBlank() ||
                current.categoryFilter != null || current.historyMonthFilter != null
            current.copy(
                showAllHistory = showAllHistory,
                entries = ExpenseAnalytics.filterEntries(
                    if (useAllHistory) allEntries else monthEntries,
                    current.searchText,
                    current.categoryFilter,
                    current.historyMonthFilter,
                ),
            )
        }
    }

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
                        if (editingId == null) {
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
            val useAllHistory = current.showAllHistory ||
                current.searchText.isNotBlank() || current.categoryFilter != null ||
                current.historyMonthFilter != null
            _home.value = current.copy(
                totalCents = result.totalCents,
                categoryStats = buildCategoryStats(result.categoryTotals),
                entries = ExpenseAnalytics.filterEntries(
                    entries = if (useAllHistory) result.allEntries else result.entries,
                    searchText = current.searchText,
                    categoryFilter = current.categoryFilter,
                    monthFilter = current.historyMonthFilter,
                ),
                recentEntries = result.entries,
                hasAnyEntries = result.allEntries.isNotEmpty(),
                historyMonthOptions = (
                    ExpenseAnalytics.availableMonths(result.allEntries) + listOfNotNull(current.historyMonthFilter)
                ).distinct().sortedDescending(),
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
