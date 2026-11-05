@file:OptIn(ExperimentalLayoutApi::class)

package com.linjincheng.qingji.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.data.ExpenseEntry
import com.linjincheng.qingji.ui.components.CategoryIcon
import com.linjincheng.qingji.ui.components.visibleYuanText
import com.linjincheng.qingji.ui.theme.QingJiSpacing
import com.linjincheng.qingji.util.DateUtils
import com.linjincheng.qingji.viewmodel.HomeUiState
import java.time.YearMonth

/**
 * 全账本流水页。搜索和分类筛选由 ViewModel 持有，详情返回后仍保留；
 * 日期分组和合计只根据当前真实筛选结果即时计算。
 */
@Composable
fun RecordsScreen(
    state: HomeUiState,
    onOpenAdd: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onSearchTextChange: (String) -> Unit,
    onCategoryFilterChange: (ExpenseCategory?) -> Unit,
    onMonthFilterChange: (YearMonth?) -> Unit,
    onRetry: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    amountsHidden: Boolean,
) {
    val dayGroups = remember(state.entries) {
        state.entries
            .sortedByDescending(ExpenseEntry::expenseDate)
            .groupBy(ExpenseEntry::expenseDate)
            .toList()
            .sortedByDescending { it.first }
    }
    val resultTotalCents = remember(state.entries) { state.entries.sumOf(ExpenseEntry::amountCents) }
    val hasActiveFilter = state.searchText.isNotBlank() ||
        state.categoryFilter != null ||
        state.expenseFilter.dateRange != null

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = QingJiSpacing.md,
                top = QingJiSpacing.xs,
                end = QingJiSpacing.md,
                bottom = QingJiSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(QingJiSpacing.sm),
        ) {
            item {
                LedgerHeader(
                    resultCount = state.entries.size,
                    resultTotalCents = resultTotalCents,
                    hasActiveFilter = hasActiveFilter,
                    showSummary = state.hasLoaded && !state.loadFailed,
                    amountsHidden = amountsHidden,
                    onOpenCalendar = onOpenCalendar,
                    onOpenSettings = onOpenSettings,
                )
            }

            item {
                OutlinedTextField(
                    value = state.searchText,
                    onValueChange = onSearchTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    placeholder = { Text("搜金额、备注、日期或分类") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    trailingIcon = if (state.searchText.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = { onSearchTextChange("") },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(Icons.Filled.Clear, contentDescription = "清除搜索")
                            }
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { defaultKeyboardAction(ImeAction.Search) },
                    ),
                )
            }

            if (state.expenseFilter.dateRange != null && state.historyMonthFilter == null) {
                item {
                    FilterChip(
                        modifier = Modifier.heightIn(min = 48.dp),
                        selected = true,
                        onClick = { onMonthFilterChange(null) },
                        label = { Text("日期 · ${state.activeDateFilterLabel}") },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "分类",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (hasActiveFilter) {
                            TextButton(
                                onClick = {
                                    onSearchTextChange("")
                                    onCategoryFilterChange(null)
                                    onMonthFilterChange(null)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text("清除筛选")
                            }
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        CategoryFilterChip(
                            label = "全部",
                            category = null,
                            selected = state.categoryFilter == null,
                            onClick = { onCategoryFilterChange(null) },
                        )
                        ExpenseCategory.entries.forEach { category ->
                            CategoryFilterChip(
                                label = category.label,
                                category = category,
                                selected = state.categoryFilter == category,
                                onClick = {
                                    onCategoryFilterChange(
                                        if (state.categoryFilter == category) null else category,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs)) {
                    Text(
                        text = "月份",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = QingJiSpacing.xs),
                    ) {
                        item(key = "history-month-all") {
                            MonthFilterChip(
                                label = "全部月份",
                                selected = state.expenseFilter.dateRange == null,
                                onClick = { onMonthFilterChange(null) },
                            )
                        }
                        items(
                            items = state.historyMonthOptions,
                            key = { month -> "history-month-${month.year}-${month.monthValue}" },
                        ) { month ->
                            MonthFilterChip(
                                label = "${month.year}年${month.monthValue}月",
                                selected = state.historyMonthFilter == month,
                                onClick = {
                                    onMonthFilterChange(
                                        if (state.historyMonthFilter == month) null else month,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            if (state.isLoading && !state.hasLoaded && !state.loadFailed) {
                item { LedgerStatusCard("正在读取本地流水…") }
            }

            if (state.isLoading && state.hasLoaded && !state.loadFailed) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "正在更新流水…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.loadFailed) {
                item { HomeLoadFailure(onRetry = onRetry) }
            }

            if (state.hasLoaded && !state.loadFailed) {
                when {
                    state.entries.isNotEmpty() -> {
                        dayGroups.forEach { (epochDay, entriesForDay) ->
                            item(key = "ledger-date-$epochDay") {
                                LedgerDateHeader(
                                    epochDay = epochDay,
                                    entries = entriesForDay,
                                    amountsHidden = amountsHidden,
                                )
                            }
                            items(
                                items = entriesForDay,
                                key = { entry -> "ledger-entry-${entry.id}" },
                            ) { entry ->
                                ExpenseRow(
                                    entry = entry,
                                    onClick = { onOpenDetail(entry.id) },
                                    amountsHidden = amountsHidden,
                                )
                            }
                        }
                    }

                    hasActiveFilter -> {
                        item {
                            LedgerEmptyState(
                                title = "没有找到匹配的流水",
                                message = "试试金额、备注、日期，或换一个分类。",
                                actionLabel = "清除搜索和筛选",
                                onAction = {
                                    onSearchTextChange("")
                                    onCategoryFilterChange(null)
                                    onMonthFilterChange(null)
                                },
                            )
                        }
                    }

                    state.hasAnyEntries -> {
                        item {
                            LedgerEmptyState(
                                title = "当前没有可显示的记录",
                                message = "重新加载本地流水，或稍后再试。",
                                actionLabel = "重新加载",
                                onAction = onRetry,
                            )
                        }
                    }

                    else -> {
                        item {
                            LedgerEmptyState(
                                title = "还没有消费记录",
                                message = "从一笔日常消费开始，轻记会帮你整理分类和趋势。",
                                actionLabel = "记一笔",
                                onAction = onOpenAdd,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerHeader(
    resultCount: Int,
    resultTotalCents: Long,
    hasActiveFilter: Boolean,
    showSummary: Boolean,
    amountsHidden: Boolean,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "账单",
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "查找和回看每一笔日常消费",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onOpenCalendar,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("日历")
            }
            // 入口保留在顶部方便从账单页快速调整全局隐私和显示偏好。
            androidx.compose.material3.IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "打开设置",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (showSummary) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.large,
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = QingJiSpacing.md, vertical = QingJiSpacing.sm),
                ) {
                    val stackSummary = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.3f
                    if (stackSummary) {
                        Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs)) {
                            Text(
                                text = "${if (hasActiveFilter) "筛选结果" else "全部记录"} · $resultCount 笔",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "当前结果支出",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = visibleYuanText(resultTotalCents, amountsHidden),
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.End,
                                    softWrap = true,
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasActiveFilter) "筛选结果" else "全部记录",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "$resultCount 笔",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Spacer(Modifier.width(QingJiSpacing.md))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "当前结果支出",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = visibleYuanText(resultTotalCents, amountsHidden),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    softWrap = true,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterChip(
    label: String,
    category: ExpenseCategory?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        modifier = Modifier.heightIn(min = 48.dp),
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = {
            if (category == null) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                CategoryIcon(
                    category = category,
                    size = 18.dp,
                    contentDescription = null,
                )
            }
        },
    )
}

@Composable
private fun MonthFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        modifier = Modifier.heightIn(min = 48.dp),
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun LedgerDateHeader(
    epochDay: Long,
    entries: List<ExpenseEntry>,
    amountsHidden: Boolean,
) {
    val dateLabel = dateGroupLabel(epochDay, DateUtils.todayEpochDay())
    val dayTotalCents = entries.sumOf(ExpenseEntry::amountCents)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = QingJiSpacing.sm, bottom = QingJiSpacing.xxs)
            .semantics(mergeDescendants = true) {
                contentDescription = "$dateLabel，${entries.size} 笔，支出 ${visibleYuanText(dayTotalCents, amountsHidden)}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${entries.size} 笔",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "支出 ${visibleYuanText(dayTotalCents, amountsHidden)}",
            modifier = Modifier.widthIn(max = 160.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun LedgerStatusCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(QingJiSpacing.md),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LedgerEmptyState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = QingJiSpacing.lg, vertical = QingJiSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(18.dp),
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(QingJiSpacing.xxs))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}
