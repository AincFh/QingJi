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
import com.linjincheng.qingji.util.MoneyUtils
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 屏幕路由（契约 §6 冻结）：不使用 navigation-compose，导航即状态。
 */
sealed interface Screen {
    data object Home : Screen

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
)

/** 详情状态：notFound=true 表示记录不存在（界面显示提示并可返回，不崩溃）。 */
data class DetailUiState(
    val isLoading: Boolean,
    val entry: ExpenseEntry?,
    val notFound: Boolean,
    val confirmDelete: Boolean,
    val deleted: Boolean,
)

/**
 * 轻记全局 ViewModel（契约 §6 冻结公开签名，私有成员为实现细节）。
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

    /** 防止「确认删除」重复触发。 */
    private var deleteInFlight = false

    init {
        refreshHome()
    }

    // ---------- 导航事件 ----------

    /** 新增：screen=Form(null)，表单重置为金额空 / 分类 FOOD / 日期今天 / 备注空。 */
    fun openAdd() {
        backStack.addLast(_screen.value)
        _form.value = initialForm()
        _screen.value = Screen.Form(null)
    }

    /** 编辑：screen=Form(id)，异步载入已有数据；记录不存在时保持空表单（保存会失败提示，不崩溃）。 */
    fun openEdit(id: Long) {
        backStack.addLast(_screen.value)
        _form.value = initialForm().copy(editingId = id)
        _screen.value = Screen.Form(id)
        viewModelScope.launch {
            val entry = repository.getExpenseById(id) ?: return@launch
            _form.update { current ->
                if (current.editingId != id) {
                    current
                } else {
                    current.copy(
                        amountText = MoneyUtils.formatCents(entry.amountCents),
                        selectedCategory = entry.category,
                        epochDay = entry.expenseDate,
                        note = entry.note,
                    )
                }
            }
        }
    }

    /** 详情：screen=Detail(id) 并异步加载；记录不存在 → notFound=true，不崩溃。 */
    fun openDetail(id: Long) {
        backStack.addLast(_screen.value)
        _screen.value = Screen.Detail(id)
        loadDetail(id)
    }

    /** 返回上一屏（Form→回 Detail 或 Home，按进入路径；Detail→Home）。 */
    fun back() {
        val target = backStack.removeLastOrNull() ?: Screen.Home
        _screen.value = target
        if (target is Screen.Detail) loadDetail(target.expenseId)
    }

    // ---------- 首页事件 ----------

    /** 切月：只改查询条件并重算统计，不写库。 */
    fun changeMonth(delta: Int) {
        val current = _home.value
        val (year, month) = DateUtils.adjustMonth(current.year, current.month, delta)
        _home.value = current.copy(
            year = year,
            month = month,
            monthTitle = DateUtils.monthTitle(year, month),
            totalCents = 0L,
            categoryStats = emptyCategoryStats(),
            entries = emptyList(),
            isLoading = true,
        )
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
        _form.update { it.copy(epochDay = epochDay) }
    }

    fun onNoteChange(text: String) {
        _form.update { it.copy(note = text) }
    }

    /**
     * 保存（契约 §6）：
     * 校验失败 → amountError 置原因键（同 AmountParseResult.reason）；
     * saving=true 期间忽略重复提交；
     * 成功 → saved=true，按任务书 §6 导航树返回（新增→首页，编辑→详情），并重算当月统计；
     * 失败（写库异常或原记录已不存在）→ saveFailed=true。
     */
    fun saveExpense() {
        val form = _form.value
        if (form.saving) return
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
                        if (editingId == null) {
                            backStack.clear()
                            _screen.value = Screen.Home
                        } else {
                            val popped = backStack.removeLastOrNull()
                            _screen.value = if (popped is Screen.Detail && popped.expenseId == editingId) {
                                popped
                            } else {
                                Screen.Detail(editingId)
                            }
                            loadDetail(editingId)
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
        if (state.isLoading || state.entry == null) return
        _detail.update { it.copy(confirmDelete = true) }
    }

    fun onDismissDeleteDialog() {
        _detail.update { it.copy(confirmDelete = false) }
    }

    /** 删除：成功 → deleted=true、回首页、重算当月统计；失败或记录已不存在 → notFound=true，不崩溃。 */
    fun onConfirmDelete() {
        val entry = _detail.value.entry ?: return
        if (deleteInFlight) return
        deleteInFlight = true
        viewModelScope.launch {
            val success = runCatching { repository.deleteExpense(entry.id) }.getOrDefault(false)
            deleteInFlight = false
            if (success) {
                _detail.update { it.copy(confirmDelete = false, deleted = true, entry = null) }
                backStack.clear()
                _screen.value = Screen.Home
                refreshHome()
            } else {
                _detail.update { it.copy(confirmDelete = false, notFound = true) }
            }
        }
    }

    // ---------- 内部实现 ----------

    private fun loadDetail(id: Long) {
        _detail.value = DetailUiState(
            isLoading = true,
            entry = null,
            notFound = false,
            confirmDelete = false,
            deleted = false,
        )
        viewModelScope.launch {
            val entry = runCatching { repository.getExpenseById(id) }.getOrNull()
            val currentScreen = _screen.value
            // 仅当仍停留在该详情页时才落状态，避免慢请求覆盖后续页面
            if (currentScreen is Screen.Detail && currentScreen.expenseId == id) {
                _detail.value = DetailUiState(
                    isLoading = false,
                    entry = entry,
                    notFound = entry == null,
                    confirmDelete = false,
                    deleted = false,
                )
            }
        }
    }

    private fun refreshHome() {
        val year = _home.value.year
        val month = _home.value.month
        viewModelScope.launch {
            val result = runCatching {
                HomeQuery(
                    entries = repository.getExpensesByMonth(year, month),
                    totalCents = repository.getMonthlyTotal(year, month),
                    categoryTotals = repository.getMonthlyCategoryTotals(year, month),
                )
            }.getOrNull()
            _home.update { current ->
                // 月份已被切换时丢弃过期结果
                if (current.year != year || current.month != month) return@update current
                if (result == null) {
                    // 查询失败：停止加载并保留空统计，不崩溃
                    current.copy(isLoading = false)
                } else {
                    current.copy(
                        totalCents = result.totalCents,
                        categoryStats = buildCategoryStats(result.categoryTotals),
                        entries = result.entries,
                        isLoading = false,
                    )
                }
            }
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
        val monthTotal = totals.values.sum()
        val positive = ExpenseCategory.entries.mapNotNull { category ->
            val total = totals[category] ?: return@mapNotNull null
            if (total <= 0L) return@mapNotNull null
            CategoryStat(category, total, percentOf(total, monthTotal))
        }.sortedWith(
            compareByDescending<CategoryStat> { it.totalCents }.thenBy { it.category.ordinal },
        )
        val zeros = ExpenseCategory.entries
            .filter { (totals[it] ?: 0L) <= 0L }
            .map { CategoryStat(it, 0L, 0) }
        return positive + zeros
    }

    /** 占比四舍五入取整，纯整数运算，不引入 Float/Double。 */
    private fun percentOf(partCents: Long, totalCents: Long): Int =
        if (totalCents <= 0L || partCents <= 0L) {
            0
        } else {
            ((partCents * 200L + totalCents) / (totalCents * 2L)).toInt()
        }

    companion object {
        /**
         * 契约 §6：内部组装
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
    val totalCents: Long,
    val categoryTotals: Map<ExpenseCategory, Long>,
)
