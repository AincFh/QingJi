package com.linjincheng.qingji.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linjincheng.qingji.ui.components.CategoryIconBadge
import com.linjincheng.qingji.ui.components.categoryAccent
import com.linjincheng.qingji.ui.components.visibleYuanText
import com.linjincheng.qingji.ui.theme.QingJiSpacing
import com.linjincheng.qingji.util.DailyExpenseTotal
import com.linjincheng.qingji.util.DateUtils
import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.viewmodel.CategoryStat
import com.linjincheng.qingji.viewmodel.HomeUiState
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * 消费统计 Tab。金额、分类、同期对比和趋势均直接使用 ViewModel 查询结果；
 * 本页只负责呈现，不生成样例数据或推算尚未查询的日期范围。
 */
@Composable
fun InsightsScreen(
    state: HomeUiState,
    onMonthChange: (Int) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    amountsHidden: Boolean,
    onOpenDateDetails: ((Long) -> Unit)? = null,
    onOpenCategoryDetails: ((ExpenseCategory) -> Unit)? = null,
    selectedTrendEpochDay: Long? = null,
    selectedCategory: ExpenseCategory? = null,
    onTrendDateSelected: (Long) -> Unit = {},
    onSelectedCategoryChanged: (ExpenseCategory?) -> Unit = {},
) {
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    val selectedMonth = remember(state.year, state.month) {
        YearMonth.of(state.year, state.month)
    }
    val thisMonth = YearMonth.now()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AnalyticsHeader(
                state = state,
                canAdvance = selectedMonth.isBefore(thisMonth),
                onMonthChange = onMonthChange,
                onOpenSettings = onOpenSettings,
            )

            if (state.isLoading && state.hasLoaded) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when {
                state.loadFailed -> AnalyticsLoadFailure(onRetry = onRetry)
                !state.hasLoaded && state.isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = QingJiSpacing.md, vertical = QingJiSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(QingJiSpacing.sm),
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "正在整理本地消费记录…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        AnalyticsSectionTabs(
                            selectedIndex = selectedSection,
                            onSelect = { selectedSection = it },
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = QingJiSpacing.md)
                                .padding(bottom = QingJiSpacing.xl),
                            verticalArrangement = Arrangement.spacedBy(QingJiSpacing.lg),
                        ) {
                            when (selectedSection) {
                                0 -> {
                                    MonthlySummary(state = state, amountsHidden = amountsHidden)
                                    CategoryBreakdown(
                                        state = state,
                                        amountsHidden = amountsHidden,
                                        onOpenCategoryDetails = onOpenCategoryDetails,
                                        selectedCategory = selectedCategory,
                                        onSelectedCategoryChanged = onSelectedCategoryChanged,
                                    )
                                }

                                1 -> ExpenseTrend(
                                    state = state,
                                    selectedMonth = selectedMonth,
                                    amountsHidden = amountsHidden,
                                    onOpenDateDetails = onOpenDateDetails,
                                    selectedTrendEpochDay = selectedTrendEpochDay,
                                    onTrendDateSelected = onTrendDateSelected,
                                )

                                else -> ComparisonSummary(
                                    state = state,
                                    selectedMonth = selectedMonth,
                                    amountsHidden = amountsHidden,
                                )
                            }
                            Text(
                                text = "统计依据本机已保存的消费记录自动更新",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = QingJiSpacing.xs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsSectionTabs(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val labels = listOf("月度", "趋势", "对比")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = QingJiSpacing.md, vertical = QingJiSpacing.xs),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            labels.forEachIndexed { index, label ->
                val selected = selectedIndex == index
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = selected,
                            onClick = { onSelect(index) },
                            role = Role.Tab,
                        ),
                    shape = MaterialTheme.shapes.medium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = QingJiSpacing.xs, vertical = QingJiSpacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsHeader(
    state: HomeUiState,
    canAdvance: Boolean,
    onMonthChange: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = QingJiSpacing.md, vertical = QingJiSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "消费统计",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "看清每一笔消费的去向",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "打开设置" },
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { onMonthChange(-1) },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "上一个月",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = "${state.year}年${state.month}月",
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(
                        onClick = { if (canAdvance) onMonthChange(1) },
                        enabled = canAdvance,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "下一个月",
                            tint = if (canAdvance) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlySummary(state: HomeUiState, amountsHidden: Boolean) {
    val monthLabel = "${state.year}年${state.month}月"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(QingJiSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(QingJiSpacing.sm),
        ) {
            Text(
                text = "$monthLabel 消费总额",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = visibleYuanText(state.totalCents, amountsHidden),
                modifier = Modifier.semantics {
                    contentDescription = if (amountsHidden) {
                        "消费总额"
                    } else {
                        visibleYuanText(state.totalCents, hidden = false)
                    }
                },
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                ),
                color = MaterialTheme.colorScheme.tertiary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${state.monthEntryCount} 笔消费",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (amountsHidden) {
                    Text(
                        text = "金额已隐藏",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonSummary(
    state: HomeUiState,
    selectedMonth: YearMonth,
    amountsHidden: Boolean,
) {
    val comparison = comparisonText(state, amountsHidden)
    val currentCents = state.comparisonCurrentTotalCents.coerceAtLeast(0L)
    val previousCents = state.previousMonthTotalCents.coerceAtLeast(0L)
    val scale = maxOf(currentCents, previousCents, 1L)
    val currentLabel = if (selectedMonth == YearMonth.now()) "本月截至今日" else "所选月份"
    val previousLabel = if (selectedMonth == YearMonth.now()) "上月同期" else "上月"
    Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs)) {
        SectionHeading(title = "消费对比", supporting = state.comparisonLabel)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = QingJiSpacing.md, vertical = QingJiSpacing.md),
                verticalArrangement = Arrangement.spacedBy(QingJiSpacing.md),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(QingJiSpacing.sm),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(comparison.accent),
                        )
                        Text(
                            text = comparison.title,
                            modifier = Modifier.semantics {
                                contentDescription = if (amountsHidden) {
                                    comparison.title.replace("¥••••", "金额已隐藏")
                                } else {
                                    comparison.title
                                }
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = comparison.detail,
                        modifier = Modifier.semantics {
                            contentDescription = if (amountsHidden) {
                                comparison.detail.replace("¥••••", "金额已隐藏")
                            } else {
                                comparison.detail
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                )
                Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.md)) {
                    ComparisonAmountRow(
                        label = currentLabel,
                        amountCents = currentCents,
                        scaleCents = scale,
                        color = MaterialTheme.colorScheme.tertiary,
                        amountsHidden = amountsHidden,
                    )
                    ComparisonAmountRow(
                        label = previousLabel,
                        amountCents = previousCents,
                        scaleCents = scale,
                        color = MaterialTheme.colorScheme.primary,
                        amountsHidden = amountsHidden,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonAmountRow(
    label: String,
    amountCents: Long,
    scaleCents: Long,
    color: androidx.compose.ui.graphics.Color,
    amountsHidden: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = visibleYuanText(amountCents, amountsHidden),
                modifier = Modifier.semantics {
                    contentDescription = amountContentDescription(amountCents, amountsHidden)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        val fraction = (amountCents.toDouble() / scaleCents.coerceAtLeast(1L).toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

private data class ComparisonCopy(
    val title: String,
    val detail: String,
    val accent: androidx.compose.ui.graphics.Color,
)

private fun amountContentDescription(cents: Long, amountsHidden: Boolean): String =
    if (amountsHidden) "金额已隐藏" else visibleYuanText(cents, hidden = false)

@Composable
private fun comparisonText(state: HomeUiState, amountsHidden: Boolean): ComparisonCopy {
    val current = state.comparisonCurrentTotalCents
    val previous = state.previousMonthTotalCents
    val previousPeriod = if (state.comparisonLabel == "与上月同期") "上月同期" else "上月"
    val currentPeriod = if (state.comparisonLabel == "与上月同期") "本月截至今日" else "所选月份"
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val lowerSpend = MaterialTheme.colorScheme.primary
    val higherSpend = MaterialTheme.colorScheme.tertiary

    return when {
        previous <= 0L && current <= 0L -> ComparisonCopy(
            title = "暂无可比较的消费数据",
            detail = "${currentPeriod}和${previousPeriod}都没有消费记录",
            accent = neutral,
        )

        previous <= 0L -> ComparisonCopy(
            title = "${previousPeriod}暂无记录",
            detail = "目前还无法计算消费变化",
            accent = neutral,
        )

        current <= 0L -> ComparisonCopy(
            title = "${currentPeriod}暂无支出",
            detail = "$previousPeriod ${visibleYuanText(previous, amountsHidden)}",
            accent = lowerSpend,
        )

        current == previous -> ComparisonCopy(
            title = "与${previousPeriod}持平",
            detail = "${currentPeriod}与${previousPeriod}均为 ${visibleYuanText(current, amountsHidden)}",
            accent = neutral,
        )

        current > previous -> {
            val difference = current - previous
            ComparisonCopy(
                title = "比${previousPeriod}多 ${visibleYuanText(difference, amountsHidden)}",
                detail = "增加 ${percentageChange(difference, previous)}% · 仅按当前已记录消费计算",
                accent = higherSpend,
            )
        }

        else -> {
            val difference = previous - current
            ComparisonCopy(
                title = "比${previousPeriod}少 ${visibleYuanText(difference, amountsHidden)}",
                detail = "减少 ${percentageChange(difference, previous)}% · 仅按当前已记录消费计算",
                accent = lowerSpend,
            )
        }
    }
}

private fun percentageChange(difference: Long, previous: Long): String =
    BigDecimal.valueOf(difference)
        .multiply(BigDecimal.valueOf(100L))
        .divide(BigDecimal.valueOf(previous), 0, RoundingMode.HALF_UP)
        .toPlainString()

@Composable
private fun CategoryBreakdown(
    state: HomeUiState,
    amountsHidden: Boolean,
    onOpenCategoryDetails: ((ExpenseCategory) -> Unit)?,
    selectedCategory: ExpenseCategory?,
    onSelectedCategoryChanged: (ExpenseCategory?) -> Unit,
) {
    val categories = state.categoryStats
        .filter { it.totalCents > 0L }
        .sortedByDescending(CategoryStat::totalCents)
    var fallbackSelectedCategory by remember(state.year, state.month) {
        mutableStateOf(categories.firstOrNull()?.category)
    }
    val resolvedSelectedCategory = resolveCategorySelection(
        availableCategories = categories.map(CategoryStat::category),
        requestedCategory = selectedCategory ?: fallbackSelectedCategory,
    )
    LaunchedEffect(selectedCategory, state.year, state.month, state.hasLoaded, categories) {
        if (state.hasLoaded && selectedCategory != resolvedSelectedCategory) {
            fallbackSelectedCategory = resolvedSelectedCategory
            onSelectedCategoryChanged(resolvedSelectedCategory)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs)) {
        SectionHeading(
            title = "分类构成",
            supporting = if (categories.isEmpty()) null else "${categories.size} 个分类",
        )
        if (categories.isEmpty()) {
            EmptyAnalyticsSection(
                text = if (state.hasMonthEntries) {
                    "当前月份没有可统计的消费分类"
                } else {
                    "记下一笔消费后，这里会显示分类占比"
                },
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = QingJiSpacing.md, vertical = QingJiSpacing.xs)
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
                ) {
                    categories.forEachIndexed { index, stat ->
                        CategoryBreakdownRow(
                            stat = stat,
                            amountsHidden = amountsHidden,
                            selected = resolvedSelectedCategory == stat.category,
                            onSelect = {
                                fallbackSelectedCategory = stat.category
                                onSelectedCategoryChanged(stat.category)
                            },
                        )
                        if (index != categories.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
            }
            categories.firstOrNull { it.category == resolvedSelectedCategory }?.let { stat ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = buildString {
                                append(stat.category.label)
                                append("，金额 ")
                                append(amountContentDescription(stat.totalCents, amountsHidden))
                                append("，占比 ")
                                append(stat.percent)
                                append("%")
                            }
                        },
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = QingJiSpacing.sm, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${stat.category.label} · ${visibleYuanText(stat.totalCents, amountsHidden)} · ${stat.percent}%",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (onOpenCategoryDetails != null) {
                            TextButton(
                                onClick = { onOpenCategoryDetails(stat.category) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text("查看流水")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownRow(
    stat: CategoryStat,
    amountsHidden: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val category = stat.category
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
                else MaterialTheme.colorScheme.surface,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(category.label)
                    append("，金额 ")
                    append(amountContentDescription(stat.totalCents, amountsHidden))
                    append("，占比 ")
                    append(stat.percent)
                    append("%")
                }
            }
            .padding(vertical = QingJiSpacing.xs, horizontal = QingJiSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIconBadge(
                category = category,
                modifier = Modifier.size(40.dp),
                size = 40.dp,
            )
            Spacer(Modifier.width(QingJiSpacing.sm))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = visibleYuanText(stat.totalCents, amountsHidden),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(QingJiSpacing.sm))
            Text(
                text = "${stat.percent}%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(
            modifier = Modifier.padding(start = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QingJiSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(stat.percent.coerceIn(0, 100) / 100f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(categoryAccent(category)),
                )
            }
            Text(
                text = "占比",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExpenseTrend(
    state: HomeUiState,
    selectedMonth: YearMonth,
    amountsHidden: Boolean,
    onOpenDateDetails: ((Long) -> Unit)?,
    selectedTrendEpochDay: Long?,
    onTrendDateSelected: (Long) -> Unit,
) {
    var selectedDayCount by rememberSaveable { mutableIntStateOf(7) }
    val isCurrentMonth = selectedMonth == YearMonth.now()
    val days = if (selectedDayCount == 7) state.dailyTrend else state.dailyTrend30
    var fallbackSelectedEpochDay by rememberSaveable(
        selectedMonth.toString(),
        selectedDayCount,
        days.firstOrNull()?.epochDay ?: 0L,
        days.lastOrNull()?.epochDay ?: 0L,
    ) { mutableStateOf<Long?>(days.lastOrNull()?.epochDay) }
    val selectedEpochDay = resolveTrendSelection(
        days = days,
        requestedEpochDay = selectedTrendEpochDay ?: fallbackSelectedEpochDay,
    )
    LaunchedEffect(
        selectedTrendEpochDay,
        selectedDayCount,
        selectedMonth,
        days.firstOrNull()?.epochDay,
        days.lastOrNull()?.epochDay,
    ) {
        selectedEpochDay?.let { resolvedEpochDay ->
            if (selectedTrendEpochDay != resolvedEpochDay) {
                fallbackSelectedEpochDay = resolvedEpochDay
                onTrendDateSelected(resolvedEpochDay)
            }
        }
    }
    val selectTrendDate: (Long) -> Unit = { epochDay ->
        fallbackSelectedEpochDay = epochDay
        onTrendDateSelected(epochDay)
    }
    val selectedDay = days.firstOrNull { it.epochDay == selectedEpochDay }
    val title = when {
        isCurrentMonth && selectedDayCount == 7 -> "近 7 天趋势"
        isCurrentMonth -> "近 30 天趋势"
        selectedDayCount == 7 -> "所选月末 7 天趋势"
        else -> "所选月末 30 天趋势"
    }
    val total = days.sumOf(DailyExpenseTotal::totalCents)
    val daysWithSpending = days.count { it.totalCents > 0L }
    val transactionCount = days.sumOf(DailyExpenseTotal::transactionCount)
    val dateRangeSummary = if (days.isNotEmpty()) {
        buildString {
            append(DateUtils.formatDate(days.first().epochDay))
            append(" 至 ")
            append(DateUtils.formatDate(days.last().epochDay))
            append(" · ")
            append(daysWithSpending)
            append(" 天有消费")
            append(" · ")
            append(transactionCount)
            append(" 笔")
            if (amountsHidden) {
                append(" · 金额已隐藏")
            } else {
                append(" · 合计 ")
                append(visibleYuanText(total, amountsHidden))
            }
        }
    } else {
        null
    }

    Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs)) {
        SectionHeading(
            title = title,
            supporting = null,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
        ) {
            listOf(7, 30).forEach { dayCount ->
                FilterChip(
                    selected = selectedDayCount == dayCount,
                    onClick = { selectedDayCount = dayCount },
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = { Text(if (dayCount == 7) "7 天" else "30 天") },
                )
            }
        }
        if (days.isEmpty()) {
            EmptyAnalyticsSection(
                text = if (state.isLoading) "正在读取趋势…" else "暂无可展示的日期数据",
            )
        } else {
            dateRangeSummary?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TrendChart(
                days = days,
                amountsHidden = amountsHidden,
                selectedEpochDay = selectedDay?.epochDay,
                onSelectDate = selectTrendDate,
            )
            selectedDay?.let { day ->
                SelectedDaySummary(
                    day = day,
                    amountsHidden = amountsHidden,
                    onOpenDateDetails = onOpenDateDetails,
                )
            }
            DaySelector(
                days = days,
                selectedEpochDay = selectedDay?.epochDay,
                amountsHidden = amountsHidden,
                onSelect = selectTrendDate,
            )
        }
    }
}

@Composable
private fun TrendChart(
    days: List<DailyExpenseTotal>,
    amountsHidden: Boolean,
    selectedEpochDay: Long?,
    onSelectDate: (Long) -> Unit,
) {
    val maxCents = days.maxOfOrNull { it.totalCents.coerceAtLeast(0L) } ?: 0L
    val maxIndex = days.indexOfLast { it.totalCents == maxCents }
    val lineColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val chartDescription = days.joinToString(separator = "；") { day ->
        "${DateUtils.formatDate(day.epochDay)}支出${amountContentDescription(day.totalCents, amountsHidden)}，${day.transactionCount}笔"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(QingJiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(QingJiSpacing.sm),
        ) {
            val peak = days.getOrNull(maxIndex)
            val peakText = when {
                peak == null || maxCents <= 0L -> "暂无消费"
                amountsHidden -> "最高消费日 ${DateUtils.formatDate(peak.epochDay)}"
                else -> "最高 ${DateUtils.formatDate(peak.epochDay)} · ${visibleYuanText(maxCents, amountsHidden)}"
            }
            Text(
                text = peakText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val chartWidth = maxOf(maxWidth, (days.size * 48).dp)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Canvas(
                        modifier = Modifier
                            .width(chartWidth)
                            .height(112.dp)
                            .pointerInput(days) {
                                detectTapGestures { position ->
                                    if (days.isNotEmpty()) {
                                        val step = size.width.toFloat() / days.size
                                        val index = (position.x / step)
                                            .toInt()
                                            .coerceIn(days.indices)
                                        onSelectDate(days[index].epochDay)
                                    }
                                }
                            }
                            .semantics { contentDescription = chartDescription },
                    ) {
                val inset = 6.dp.toPx()
                val top = 10.dp.toPx()
                val baseline = size.height - 10.dp.toPx()
                val chartHeight = (baseline - top).coerceAtLeast(1f)
                val step = if (days.size > 1) {
                    (size.width - inset * 2f) / (days.size - 1)
                } else {
                    0f
                }
                val points = days.mapIndexed { index, day ->
                    val ratio = if (maxCents > 0L) {
                        day.totalCents.coerceAtLeast(0L).toDouble().div(maxCents.toDouble()).toFloat()
                    } else {
                        0f
                    }
                    androidx.compose.ui.geometry.Offset(
                        x = inset + step * index,
                        y = baseline - chartHeight * 0.82f * ratio,
                    )
                }
                if (points.isNotEmpty()) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                    points.forEachIndexed { index, point ->
                        val isPeak = index == maxIndex && maxCents > 0L
                        val isSelected = days[index].epochDay == selectedEpochDay
                        drawCircle(
                            color = if (isPeak || isSelected) surfaceColor else lineColor,
                            radius = when {
                                isSelected -> 8.dp.toPx()
                                isPeak -> 7.dp.toPx()
                                else -> 4.dp.toPx()
                            },
                            center = point,
                        )
                        drawCircle(
                            color = if (isPeak || isSelected) markerColor else lineColor,
                            radius = when {
                                isSelected -> 5.dp.toPx()
                                isPeak -> 4.dp.toPx()
                                else -> 2.dp.toPx()
                            },
                            center = point,
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
private fun SelectedDaySummary(
    day: DailyExpenseTotal,
    amountsHidden: Boolean,
    onOpenDateDetails: ((Long) -> Unit)?,
) {
    val date = LocalDate.ofEpochDay(day.epochDay)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${DateUtils.formatDate(day.epochDay)}，支出 " +
                    amountContentDescription(day.totalCents, amountsHidden) + "，${day.transactionCount} 笔"
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = QingJiSpacing.md, vertical = QingJiSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QingJiSpacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${date.monthValue}月${date.dayOfMonth}日",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "${day.transactionCount} 笔 · " +
                        if (day.transactionCount > 0) "当日消费" else "这一天暂无消费记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = visibleYuanText(day.totalCents, amountsHidden),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
            )
            if (onOpenDateDetails != null) {
                TextButton(
                    onClick = { onOpenDateDetails(day.epochDay) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("查看流水")
                }
            }
        }
    }
}

@Composable
private fun DaySelector(
    days: List<DailyExpenseTotal>,
    selectedEpochDay: Long?,
    amountsHidden: Boolean,
    onSelect: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        days.forEach { day ->
            val selected = day.epochDay == selectedEpochDay
            val date = LocalDate.ofEpochDay(day.epochDay)
            val dateDescription = buildString {
                append(DateUtils.formatDate(day.epochDay))
                append("，支出 ")
                append(amountContentDescription(day.totalCents, amountsHidden))
                append("，")
                append(day.transactionCount)
                append(" 笔")
            }
            Surface(
                modifier = Modifier
                    .width(48.dp)
                    .heightIn(min = 52.dp)
                    .selectable(
                        selected = selected,
                        onClick = { onSelect(day.epochDay) },
                        role = Role.RadioButton,
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = dateDescription
                    },
                shape = MaterialTheme.shapes.medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = QingJiSpacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = weekdayLabel(date.dayOfWeek.value),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, supporting: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
    ) {
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyAnalyticsSection(text: String, compact: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = QingJiSpacing.md, vertical = if (compact) QingJiSpacing.md else QingJiSpacing.lg),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AnalyticsLoadFailure(onRetry: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = QingJiSpacing.md, vertical = QingJiSpacing.lg),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(QingJiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
        ) {
            Text(
                text = "暂时无法读取统计数据",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = "记录仍保存在本机，请重新读取后再查看。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onRetry) {
                Text("重新加载", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

private fun weekdayLabel(dayOfWeek: Int): String =
    "一二三四五六日".getOrElse(dayOfWeek - 1) { ' ' }.toString()
