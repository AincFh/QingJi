package com.linjincheng.qingji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linjincheng.qingji.ui.components.visibleYuanText
import com.linjincheng.qingji.ui.theme.QingJiSpacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Local expense calendar. The caller owns the selected month/date and supplies real SQLite
 * aggregates; this screen only renders them and reports user choices through callbacks.
 */
@Composable
fun CalendarScreen(
    month: YearMonth,
    days: List<CalendarDayUi>,
    selectedEpochDay: Long?,
    onMonthChange: (YearMonth) -> Unit,
    onDaySelected: (Long) -> Unit,
    onOpenDayDetails: (Long) -> Unit,
    amountsHidden: Boolean,
    modifier: Modifier = Modifier,
) {
    val monthDays = remember(month, days) { CalendarUiModel.buildMonthDays(month, days) }
    val monthGrid = remember(month) { CalendarUiModel.buildMonthGrid(month) }
    val dayByEpochDay = remember(monthDays) { monthDays.associateBy(CalendarDayUi::epochDay) }
    val selectedDate = selectedEpochDay
        ?.let(LocalDate::ofEpochDay)
        ?.takeIf { YearMonth.from(it) == month }
    val selectedDay = selectedDate?.let { dayByEpochDay[it.toEpochDay()] }
    val maximumDailyTotal = monthDays.maxOfOrNull(CalendarDayUi::totalCents) ?: 0L
    val hasActivity = monthDays.any { it.totalCents > 0L || it.transactionCount > 0 }
    val totalTransactionCount = monthDays.sumOf { it.transactionCount.toLong() }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(top = QingJiSpacing.sm, bottom = QingJiSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(QingJiSpacing.md),
        ) {
            Text(
                text = "消费日历",
                modifier = Modifier
                    .padding(horizontal = QingJiSpacing.md)
                    .semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            MonthNavigator(
                month = month,
                onMonthChange = onMonthChange,
                modifier = Modifier.padding(horizontal = QingJiSpacing.md),
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = QingJiSpacing.xs),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = QingJiSpacing.xxs, vertical = QingJiSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xxs),
                ) {
                    WeekdayHeader()
                    monthGrid.chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            week.forEach { epochDay ->
                                if (epochDay == null) {
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 52.dp),
                                    )
                                } else {
                                    val day = dayByEpochDay.getValue(epochDay)
                                    CalendarDayCell(
                                        day = day,
                                        month = month,
                                        selected = selectedEpochDay == epochDay,
                                        maximumDailyTotal = maximumDailyTotal,
                                        amountsHidden = amountsHidden,
                                        onClick = { onDaySelected(epochDay) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = when {
                            !hasActivity -> "本月暂无消费记录"
                            amountsHidden -> "本月有 $totalTransactionCount 笔消费 · 橙点表示有消费"
                            else -> "橙点越明显，表示当日支出越高"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = QingJiSpacing.md)
                            .padding(top = QingJiSpacing.xs)
                            .semantics {
                                if (amountsHidden) contentDescription =
                                    if (!hasActivity) "本月暂无消费记录" else
                                        "本月有 $totalTransactionCount 笔消费，橙点表示有消费"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            SelectedDaySummary(
                date = selectedDate,
                day = selectedDay,
                amountsHidden = amountsHidden,
                onOpenDayDetails = onOpenDayDetails,
                modifier = Modifier.padding(horizontal = QingJiSpacing.md),
            )

            Text(
                text = "数据来自本机已保存的消费记录",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = QingJiSpacing.md)
                    .padding(top = QingJiSpacing.xs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MonthNavigator(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = QingJiSpacing.xs, vertical = QingJiSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = { onMonthChange(month.minusMonths(1)) },
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "上一月"
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${month.year}年${month.monthValue}月",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "点击日期查看当日消费",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                )
            }
            IconButton(
                onClick = { onMonthChange(month.plusMonths(1)) },
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "下一月"
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    Row(modifier = Modifier.fillMaxWidth()) {
        weekdays.forEach { weekday ->
            Text(
                text = weekday,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .wrapContentHeight(align = Alignment.CenterVertically),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDayUi,
    month: YearMonth,
    selected: Boolean,
    maximumDailyTotal: Long,
    amountsHidden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = LocalDate.ofEpochDay(day.epochDay)
    val hasActivity = day.totalCents > 0L || day.transactionCount > 0
    val dateLabel = "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    val accessibilityLabel = buildString {
        append(dateLabel)
        append("，")
        if (selected) append("已选中，")
        if (amountsHidden) {
            append("金额已隐藏")
        } else {
            append("支出 ")
            append(visibleYuanText(day.totalCents, hidden = false))
        }
        append("，${day.transactionCount}笔消费")
    }
    val intensity = if (maximumDailyTotal > 0L) {
        (day.totalCents.toFloat() / maximumDailyTotal.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val dotSize = if (amountsHidden) 5.dp else (3f + 3f * intensity).dp
    val dotAlpha = if (amountsHidden) 1f else (0.32f + 0.68f * intensity)
    val dayShape = RoundedCornerShape(12.dp)
    val dayColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val numberColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(dayShape)
            .background(dayColor)
            .clickable(
                role = Role.Button,
                onClickLabel = "选择${month.monthValue}月${date.dayOfMonth}日",
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = numberColor,
        )
        if (hasActivity) {
            Spacer(modifier = Modifier.size(3.dp))
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = dotAlpha)),
            )
        } else {
            Spacer(modifier = Modifier.size(6.dp))
        }
    }
}

@Composable
private fun SelectedDaySummary(
    date: LocalDate?,
    day: CalendarDayUi?,
    amountsHidden: Boolean,
    onOpenDayDetails: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(QingJiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(QingJiSpacing.sm),
        ) {
            if (date == null || day == null) {
                Text(
                    text = "选择一天查看消费",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "可选择没有消费的日期，金额和笔数会显示为零。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = date.format(SELECTED_DATE_FORMATTER),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(QingJiSpacing.xl),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "当日支出",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = visibleYuanText(day.totalCents, amountsHidden),
                            modifier = Modifier.semantics {
                                if (amountsHidden) contentDescription = "金额已隐藏"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "消费笔数",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${day.transactionCount} 笔",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (day.transactionCount == 0) {
                    Text(
                        text = "当天没有消费记录，仍可打开当天流水查看空结果。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { onOpenDayDetails(day.epochDay) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("查看流水")
                }
            }
        }
    }
}

private val SELECTED_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
