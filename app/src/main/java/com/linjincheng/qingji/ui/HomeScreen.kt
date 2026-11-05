@file:OptIn(ExperimentalMaterial3Api::class)

package com.linjincheng.qingji.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import com.linjincheng.qingji.R
import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.data.ExpenseEntry
import com.linjincheng.qingji.ui.components.CategoryIcon
import com.linjincheng.qingji.ui.components.CategoryIconBadge
import com.linjincheng.qingji.ui.components.categoryAccent
import com.linjincheng.qingji.ui.components.visibleYuanText
import com.linjincheng.qingji.ui.theme.QingJiSpacing
import com.linjincheng.qingji.util.DateUtils
import com.linjincheng.qingji.util.DailyExpenseTotal
import com.linjincheng.qingji.viewmodel.CategoryStat
import com.linjincheng.qingji.viewmodel.HomeUiState
import java.time.LocalDate
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 首页保持无状态：月份、汇总、筛选状态都来自 HomeUiState，所有操作只通过回调上抛。
 * 月度汇总不依赖 state.entries；该列表只用于显示已筛选的近期流水。
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenDetail: (Long) -> Unit,
    onMonthChange: (Int) -> Unit,
    onOpenAllRecords: () -> Unit,
    onRetryHome: () -> Unit = {},
    amountsHidden: Boolean = false,
    onOpenDateDetails: ((Long) -> Unit)? = null,
    onOpenCategoryDetails: ((ExpenseCategory) -> Unit)? = null,
    selectedTrendEpochDay: Long? = null,
    selectedCategory: ExpenseCategory? = null,
    onTrendDateSelected: (Long) -> Unit = {},
    onSelectedCategoryChanged: (ExpenseCategory?) -> Unit = {},
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                HomeHeader(
                    state = state,
                    onMonthChange = onMonthChange,
                )
            }

            if (state.isLoading) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "正在加载本月消费…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.loadFailed) {
                item { HomeLoadFailure(onRetry = onRetryHome) }
            }

            if (state.hasLoaded && !state.loadFailed) {
                item { MonthlyOverview(state = state, amountsHidden = amountsHidden) }
                item {
                    DailyExpenseTrend(
                        days = state.dailyTrend,
                        isLoading = state.isLoading,
                        amountsHidden = amountsHidden,
                        onOpenDateDetails = onOpenDateDetails,
                        selectedTrendEpochDay = selectedTrendEpochDay,
                        onTrendDateSelected = onTrendDateSelected,
                        title = if (YearMonth.of(state.year, state.month) == YearMonth.now()) {
                            "近 7 天支出"
                        } else {
                            "${state.monthTitle}末 7 天"
                        },
                    )
                }
                item {
                    CategoryShareSection(
                        state = state,
                        amountsHidden = amountsHidden,
                        maxRows = 3,
                        onOpenCategoryDetails = onOpenCategoryDetails,
                        selectedCategory = selectedCategory,
                        onSelectedCategoryChanged = onSelectedCategoryChanged,
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "最近流水",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        if (!state.isLoading && state.recentEntries.isNotEmpty()) {
                            TextButton(onClick = onOpenAllRecords) { Text("查看全部  ›") }
                        } else {
                            TextButton(onClick = onOpenAllRecords) { Text("查看全部") }
                        }
                    }
                }
                when {
                    state.isLoading -> {
                        item {
                            Text(
                                text = "正在读取流水…",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    state.recentEntries.isEmpty() && !state.hasMonthEntries -> {
                        item { NoEntriesThisMonth() }
                    }

                    else -> {
                        val todayEpochDay = DateUtils.todayEpochDay()
                        state.recentEntries.take(3).groupBy { it.expenseDate }
                            .forEach { (epochDay, entriesForDay) ->
                            item(key = "date-" + epochDay.toString()) {
                                Text(
                                    text = dateGroupLabel(epochDay, todayEpochDay),
                                    modifier = Modifier.padding(top = 2.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            items(
                                items = entriesForDay,
                                key = { entry -> entry.id },
                            ) { entry ->
                                ExpenseRow(
                                    entry = entry,
                                    onClick = { onOpenDetail(entry.id) },
                                    amountsHidden = amountsHidden,
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
internal fun HomeLoadFailure(onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(QingJiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "暂时无法读取消费数据",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "你的记录还在本机，重新读取后再查看统计。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onRetry) {
                Text("重新加载", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun HomeHeader(
    state: HomeUiState,
    onMonthChange: (Int) -> Unit,
) {
    val canAdvanceMonth = YearMonth.of(state.year, state.month).isBefore(YearMonth.now())
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        val stackHeader = maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.3f
        if (stackHeader) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HomeBrandBlock()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    HomeMonthPicker(state, canAdvanceMonth, onMonthChange)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HomeBrandBlock(modifier = Modifier.weight(1f))
                HomeMonthPicker(state, canAdvanceMonth, onMonthChange)
            }
        }
    }
}

@Composable
private fun HomeBrandBlock(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "轻记",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun HomeMonthPicker(
    state: HomeUiState,
    canAdvanceMonth: Boolean,
    onMonthChange: (Int) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        ),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onMonthChange(-1) }, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "上个月",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "${state.year}年${state.month}月",
                modifier = Modifier.padding(horizontal = 2.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            IconButton(
                onClick = { onMonthChange(1) },
                enabled = canAdvanceMonth,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "下个月",
                    tint = if (canAdvanceMonth) MaterialTheme.colorScheme.primary else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@Composable
internal fun MonthlyOverview(state: HomeUiState, amountsHidden: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = QingJiSpacing.md, vertical = QingJiSpacing.sm),
        ) {
            val showLeaf = maxWidth >= 320.dp
            val leafWidth = (maxWidth * 0.25f).coerceIn(80.dp, 112.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = if (YearMonth.of(state.year, state.month) == YearMonth.now()) {
                            "本月支出"
                        } else {
                            "${state.monthTitle}支出"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val amountFontSize = (maxWidth.value / 7.2f).coerceIn(25f, 36f).sp
                        Text(
                            text = visibleYuanText(state.totalCents, amountsHidden),
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Clip,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = amountFontSize,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.45).sp,
                            ),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${state.monthEntryCount} 笔",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Box(
                            Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)),
                        )
                        Text(
                            text = monthComparison(
                                currentCents = state.comparisonCurrentTotalCents,
                                previousCents = state.previousMonthTotalCents,
                                label = state.comparisonLabel,
                                amountsHidden = amountsHidden,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (showLeaf) {
                    Image(
                        painter = painterResource(R.drawable.qingji_leaf_branch),
                        contentDescription = null,
                        modifier = Modifier
                            .width(leafWidth)
                            .height(96.dp)
                            .padding(start = 3.dp),
                        alpha = 0.92f,
                    )
                }
            }
        }
    }
}

private fun monthComparison(
    currentCents: Long,
    previousCents: Long,
    label: String,
    amountsHidden: Boolean,
): String = when {
    previousCents <= 0L && currentCents > 0L -> "上月同期暂无支出"
    previousCents <= 0L -> "上月暂无支出"
    currentCents == previousCents -> "${label}持平"
    currentCents > previousCents -> "${label}多 ${visibleYuanText(currentCents - previousCents, amountsHidden)}"
    else -> "${label}少 ${visibleYuanText(previousCents - currentCents, amountsHidden)}"
}

@Composable
internal fun DailyExpenseTrend(
    days: List<DailyExpenseTotal>,
    isLoading: Boolean,
    amountsHidden: Boolean,
    title: String = "近 7 天支出",
    onOpenDateDetails: ((Long) -> Unit)? = null,
    selectedTrendEpochDay: Long? = null,
    onTrendDateSelected: (Long) -> Unit = {},
) {
    val hasSpending = days.any { it.totalCents > 0L }
    var fallbackSelectedEpochDay by remember(days.firstOrNull()?.epochDay, days.lastOrNull()?.epochDay) {
        mutableStateOf(days.lastOrNull()?.epochDay)
    }
    val selectedEpochDay = resolveTrendSelection(
        days = days,
        requestedEpochDay = selectedTrendEpochDay ?: fallbackSelectedEpochDay,
    )
    LaunchedEffect(selectedTrendEpochDay, days.firstOrNull()?.epochDay, days.lastOrNull()?.epochDay) {
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = when {
                    isLoading -> "正在读取…"
                    !hasSpending -> "暂无消费"
                    else -> "合计 ${visibleYuanText(days.sumOf { it.totalCents }, amountsHidden)}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (days.isEmpty()) {
            Text(
                text = if (isLoading) "正在读取趋势…" else "暂无可展示的日期数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (!hasSpending) {
                Text(
                    text = if (isLoading) "正在读取趋势…" else "这段时间还没有消费记录，可选择任一天查看",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            selectedDay?.let { day ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = buildString {
                                append(DateUtils.formatDate(day.epochDay))
                                append("，支出 ")
                                append(visibleYuanText(day.totalCents, amountsHidden))
                                append("，")
                                append(day.transactionCount)
                                append(" 笔")
                            }
                        },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = QingJiSpacing.sm, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = DateUtils.formatDate(day.epochDay),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "${visibleYuanText(day.totalCents, amountsHidden)} · ${day.transactionCount} 笔",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
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
            val maxCents = days.maxOfOrNull { it.totalCents.coerceAtLeast(0L) } ?: 0L
            val maxIndex = if (maxCents > 0L) days.indexOfLast { it.totalCents == maxCents } else -1
            val lineColor = MaterialTheme.colorScheme.primary
            val areaColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            val markerColor = MaterialTheme.colorScheme.tertiary
            val surfaceColor = MaterialTheme.colorScheme.surface
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val chartWidth = maxOf(maxWidth, 336.dp)
                    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Column(modifier = Modifier.width(chartWidth)) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .pointerInput(days, onTrendDateSelected) {
                                    detectTapGestures { position ->
                                        if (days.isNotEmpty()) {
                                            val pointStep = size.width.toFloat() / days.size
                                            val index = (position.x / pointStep)
                                                .toInt()
                                                .coerceIn(days.indices)
                                            selectTrendDate(days[index].epochDay)
                                        }
                                    }
                                }
                                .semantics {
                                    contentDescription = "$title 趋势图；逐日日期和笔数可在下方选择查看"
                                },
                        ) {
                            val baseY = size.height - 7.dp.toPx()
                            val topY = 7.dp.toPx()
                            val chartHeight = (baseY - topY).coerceAtLeast(1f)
                            val step = size.width / days.size
                            val points = days.mapIndexed { index, day ->
                                val ratio = if (maxCents > 0L) {
                                    (day.totalCents.coerceAtLeast(0L).toDouble() / maxCents.toDouble()).toFloat()
                                } else 0f
                                androidx.compose.ui.geometry.Offset(
                                    x = step * index + step / 2f,
                                    y = baseY - chartHeight * 0.86f * ratio,
                                )
                            }
                            if (points.isNotEmpty()) {
                                val line = Path().apply {
                                    moveTo(points.first().x, points.first().y)
                                    points.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                if (maxCents > 0L) {
                                    val area = Path().apply {
                                        moveTo(points.first().x, baseY)
                                        lineTo(points.first().x, points.first().y)
                                        points.drop(1).forEach { lineTo(it.x, it.y) }
                                        lineTo(points.last().x, baseY)
                                        close()
                                    }
                                    drawPath(area, areaColor)
                                }
                                drawPath(
                                    line,
                                    lineColor,
                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                                )
                                points.forEachIndexed { index, point ->
                                    if (index == maxIndex || days[index].epochDay == selectedEpochDay) {
                                        drawCircle(surfaceColor, radius = 7.dp.toPx(), center = point)
                                        drawCircle(markerColor, radius = 4.5.dp.toPx(), center = point)
                                    } else {
                                        drawCircle(lineColor.copy(alpha = 0.86f), radius = 3.dp.toPx(), center = point)
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().selectableGroup(),
                        ) {
                            days.forEach { day ->
                                val date = LocalDate.ofEpochDay(day.epochDay)
                                val isSelected = day.epochDay == selectedEpochDay
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
                                            },
                                        )
                                        .selectable(
                                            selected = isSelected,
                                            role = Role.RadioButton,
                                            onClick = { selectTrendDate(day.epochDay) },
                                        )
                                        .padding(vertical = 3.dp)
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = DateUtils.formatDate(day.epochDay) +
                                                "，支出 " + visibleYuanText(day.totalCents, amountsHidden) +
                                                "，${day.transactionCount} 笔"
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected || (day.totalCents == maxCents && maxCents > 0L)) {
                                            FontWeight.Bold
                                        } else FontWeight.Medium,
                                        color = if (isSelected || (day.totalCents == maxCents && maxCents > 0L)) {
                                            MaterialTheme.colorScheme.primary
                                        } else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = weekdayLabel(date.dayOfWeek.value),
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
        }
    }
}

private fun weekdayLabel(dayOfWeek: Int): String =
    "一二三四五六日".getOrElse(dayOfWeek - 1) { ' ' }.toString()

@Composable
internal fun CategoryShareSection(
    state: HomeUiState,
    amountsHidden: Boolean,
    maxRows: Int = Int.MAX_VALUE,
    onOpenCategoryDetails: ((ExpenseCategory) -> Unit)? = null,
    selectedCategory: ExpenseCategory? = null,
    onSelectedCategoryChanged: (ExpenseCategory?) -> Unit = {},
) {
    val allPositiveStats = state.categoryStats.filter { it.totalCents > 0L }
    var fallbackSelectedCategory by remember(state.year, state.month) {
        mutableStateOf(allPositiveStats.firstOrNull()?.category)
    }
    val resolvedSelectedCategory = resolveCategorySelection(
        availableCategories = allPositiveStats.map(CategoryStat::category),
        requestedCategory = selectedCategory ?: fallbackSelectedCategory,
    )
    LaunchedEffect(selectedCategory, state.year, state.month, allPositiveStats) {
        if (selectedCategory != resolvedSelectedCategory) {
            fallbackSelectedCategory = resolvedSelectedCategory
            onSelectedCategoryChanged(resolvedSelectedCategory)
        }
    }
    val topStats = allPositiveStats.take(maxRows.coerceAtLeast(0))
    val selectedStat = allPositiveStats.firstOrNull { it.category == resolvedSelectedCategory }
    val positiveStats = if (
        selectedStat != null && selectedStat !in topStats && maxRows > 0 && topStats.isNotEmpty()
    ) {
        topStats.dropLast(1) + selectedStat
    } else {
        topStats
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "分类支出",
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (state.isLoading && positiveStats.isEmpty()) {
            Text(
                text = "正在统计本月分类支出…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (positiveStats.isEmpty()) {
            Text(
                text = "记下一笔后，会在这里看到消费去向",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.selectableGroup()) {
            positiveStats.forEach { stat ->
                CategoryShareRow(
                    stat = stat,
                    amountsHidden = amountsHidden,
                    selected = stat.category == resolvedSelectedCategory,
                    onSelect = {
                        fallbackSelectedCategory = stat.category
                        onSelectedCategoryChanged(stat.category)
                    },
                )
            }
        }
        positiveStats.firstOrNull { it.category == resolvedSelectedCategory }?.let { stat ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = buildString {
                            append(stat.category.label)
                            append("，")
                            append(visibleYuanText(stat.totalCents, amountsHidden))
                            append("，占本月 ")
                            append(stat.percent)
                            append("%")
                        }
                    },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
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

@Composable
private fun CategoryShareRow(
    stat: CategoryStat,
    amountsHidden: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
) {
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
                else MaterialTheme.colorScheme.background,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(stat.category.label)
                    append("，")
                    append(visibleYuanText(stat.totalCents, amountsHidden))
                    append("，占比 ")
                    append(stat.percent)
                    append("%")
                }
            }
            .padding(horizontal = QingJiSpacing.xs, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIconBadge(
                category = stat.category,
                modifier = Modifier.size(32.dp),
                size = 32.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stat.category.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = visibleYuanText(stat.totalCents, amountsHidden),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stat.percent.toString() + "%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(stat.percent.coerceIn(0, 100) / 100f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(categoryAccent(stat.category)),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransactionControls(
    searchText: String,
    categoryFilter: ExpenseCategory?,
    onSearchTextChange: (String) -> Unit,
    onCategoryFilterChange: (ExpenseCategory?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            singleLine = true,
            placeholder = { Text("搜索全部流水：备注、金额或日期") },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = "搜索全部流水")
            },
            trailingIcon = if (searchText.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchTextChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "清除搜索")
                    }
                }
            } else {
                null
            },
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = categoryFilter == null,
                onClick = { onCategoryFilterChange(null) },
                label = { Text("全部") },
            )
            ExpenseCategory.entries.forEach { category ->
                FilterChip(
                    selected = categoryFilter == category,
                    onClick = {
                        onCategoryFilterChange(if (categoryFilter == category) null else category)
                    },
                    label = { Text(category.label) },
                    leadingIcon = {
                        CategoryIcon(
                            category = category,
                            modifier = Modifier.size(18.dp),
                            contentDescription = null,
                        )
                    },
                )
            }
        }
        if (searchText.isNotBlank() || categoryFilter != null) {
            TextButton(
                onClick = {
                    onSearchTextChange("")
                    onCategoryFilterChange(null)
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("清除搜索和分类")
            }
        }
    }
}

@Composable
internal fun NoEntriesThisMonth() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "还没有记录，记下第一笔消费吧",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "本月的消费记录会显示在这里",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun NoMatchingEntries(onClear: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "没有找到匹配的流水",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "试试金额、备注、日期或其他分类",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onClear != null) {
            TextButton(onClick = onClear) {
                Text("清除搜索和筛选")
            }
        }
    }
}

internal fun dateGroupLabel(epochDay: Long, todayEpochDay: Long): String {
    if (epochDay == todayEpochDay) return "今天"
    if (epochDay == todayEpochDay - 1L) return "昨天"
    val date = LocalDate.ofEpochDay(epochDay)
    return "${date.monthValue}月${date.dayOfMonth}日 · 周${weekdayLabel(date.dayOfWeek.value)}"
}

@Composable
internal fun ExpenseRow(
    entry: ExpenseEntry,
    onClick: () -> Unit,
    amountsHidden: Boolean,
) {
    val note = entry.note.trim()
    val time = Instant.ofEpochMilli(entry.createdAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    val supportingText = if (note.isBlank()) time else "$note · $time"
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = entry.category.label + "，" +
                        (entry.note.ifBlank { "无备注" }) + "，" +
                        visibleYuanText(entry.amountCents, amountsHidden) + "，" +
                        DateUtils.formatDate(entry.expenseDate)
                }
                .padding(horizontal = 2.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIconBadge(
                category = entry.category,
                modifier = Modifier.size(38.dp),
                size = 38.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = entry.category.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "-" + visibleYuanText(entry.amountCents, amountsHidden),
                modifier = Modifier.widthIn(max = 140.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Clip,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        )
    }
}

@Preview(showBackground = true, name = "首页 · 有流水")
@Composable
private fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen(
            state = HomeUiState(
                year = 2026,
                month = 11,
                monthTitle = "2026 年 11 月",
                totalCents = 6200L,
                categoryStats = listOf(
                    CategoryStat(ExpenseCategory.FOOD, 5000L, 81),
                    CategoryStat(ExpenseCategory.TRANSPORT, 1200L, 19),
                    CategoryStat(ExpenseCategory.SHOPPING, 0L, 0),
                    CategoryStat(ExpenseCategory.DAILY, 0L, 0),
                    CategoryStat(ExpenseCategory.OTHER, 0L, 0),
                ),
                entries = listOf(
                    ExpenseEntry(
                        id = 1L,
                        amountCents = 5000L,
                        category = ExpenseCategory.FOOD,
                        note = "午饭",
                        expenseDate = 20761L,
                        createdAt = 1793764800000L,
                        updatedAt = 1793764800000L,
                    ),
                    ExpenseEntry(
                        id = 2L,
                        amountCents = 1200L,
                        category = ExpenseCategory.TRANSPORT,
                        note = "",
                        expenseDate = 20761L,
                        createdAt = 1793720000000L,
                        updatedAt = 1793720000000L,
                    ),
                ),
                recentEntries = listOf(
                    ExpenseEntry(
                        id = 1L,
                        amountCents = 2850L,
                        category = ExpenseCategory.FOOD,
                        note = "午餐",
                        expenseDate = 20761L,
                        createdAt = 1793764800000L,
                        updatedAt = 1793764800000L,
                    ),
                    ExpenseEntry(
                        id = 2L,
                        amountCents = 1200L,
                        category = ExpenseCategory.TRANSPORT,
                        note = "",
                        expenseDate = 20761L,
                        createdAt = 1793720000000L,
                        updatedAt = 1793720000000L,
                    ),
                ),
                isLoading = false,
                previousMonthTotalCents = 7400L,
                dailyTrend = listOf(
                    DailyExpenseTotal(20755L, 1800L),
                    DailyExpenseTotal(20756L, 0L),
                    DailyExpenseTotal(20757L, 2400L),
                    DailyExpenseTotal(20758L, 0L),
                    DailyExpenseTotal(20759L, 900L),
                    DailyExpenseTotal(20760L, 0L),
                    DailyExpenseTotal(20761L, 1200L),
                ),
                hasMonthEntries = true,
                monthEntryCount = 2,
                hasLoaded = true,
            ),
            onOpenDetail = {},
            onMonthChange = {},
            onOpenAllRecords = {},
        )
    }
}

@Preview(showBackground = true, name = "首页 · 本月无流水")
@Composable
private fun HomeScreenEmptyPreview() {
    MaterialTheme {
        HomeScreen(
            state = HomeUiState(
                year = 2026,
                month = 11,
                monthTitle = "2026 年 11 月",
                totalCents = 0L,
                categoryStats = ExpenseCategory.entries.map { CategoryStat(it, 0L, 0) },
                entries = emptyList(),
                isLoading = false,
                previousMonthTotalCents = 0L,
                dailyTrend = (0L..6L).map { offset -> DailyExpenseTotal(20755L + offset, 0L) },
                hasMonthEntries = false,
                monthEntryCount = 0,
                hasLoaded = true,
            ),
            onOpenDetail = {},
            onMonthChange = {},
            onOpenAllRecords = {},
        )
    }
}
