@file:OptIn(ExperimentalMaterial3Api::class)

package com.linjincheng.qingji.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import com.linjincheng.qingji.data.MonthlyBudgetSummary
import com.linjincheng.qingji.ui.components.visibleYuanText
import com.linjincheng.qingji.ui.theme.QingJiSpacing
import com.linjincheng.qingji.util.AmountParseResult
import com.linjincheng.qingji.util.MoneyUtils
import java.time.YearMonth

/**
 * Monthly budget destination. The caller owns persistence, routing, hidden amount preference, and
 * the selected month; this screen reports all changes through callbacks.
 */
@Composable
fun BudgetScreen(
    summary: MonthlyBudgetSummary,
    onSaveBudget: (year: Int, month: Int, amountCents: Long) -> Unit,
    onClearBudget: (year: Int, month: Int) -> Unit,
    onMonthChange: (year: Int, month: Int) -> Unit,
    onViewExpenses: (year: Int, month: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    amountsHidden: Boolean = false,
    isLoading: Boolean = false,
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {},
    feedbackMessage: String? = null,
    feedbackIsError: Boolean = false,
    onDismissFeedback: () -> Unit = {},
) {
    val display = summary.toBudgetDisplayModel()
    var showEditor by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var amountInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    BackHandler(
        enabled = !showEditor && !showClearConfirmation,
        onBack = onBack,
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QingJiSpacing.md, vertical = QingJiSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(QingJiSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Spacer(Modifier.width(QingJiSpacing.xs))
                Text(
                    text = "月预算",
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            when {
                isLoading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "正在读取 ${summary.year} 年 ${summary.month} 月预算…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                loadFailed -> {
                    Text(
                        text = "暂时无法读取本月预算和消费汇总。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("重试")
                    }
                }

                else -> {
                    BudgetMonthSelector(
                        year = summary.year,
                        month = summary.month,
                        onMonthChange = onMonthChange,
                    )

                    BudgetSummaryCard(
                        summary = summary,
                        display = display,
                        amountsHidden = amountsHidden,
                        onEdit = {
                            amountInput = if (amountsHidden) {
                                ""
                            } else {
                                summary.budgetCents?.let(MoneyUtils::formatCents).orEmpty()
                            }
                            inputError = null
                            showEditor = true
                        },
                        onClear = { showClearConfirmation = true },
                    )

                    OutlinedButton(
                        onClick = { onViewExpenses(summary.year, summary.month) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("查看本月流水")
                    }
                }
            }

            feedbackMessage?.let { message ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = if (feedbackIsError) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(QingJiSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.weight(1f),
                            color = if (feedbackIsError) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        TextButton(onClick = onDismissFeedback) { Text("关闭") }
                    }
                }
            }
        }
    }

    if (showEditor) {
        val parsedAmount = parseBudgetAmountInput(amountInput)
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (summary.budgetCents == null) "设置本月预算" else "修改本月预算") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs)) {
                    Text(
                        text = "${summary.year} 年 ${summary.month} 月 · 金额单位为元",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = {
                            amountInput = it
                            inputError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("预算金额") },
                        placeholder = { Text("例如 3000 或 3000.00") },
                        prefix = { Text("¥") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = {
                            val message = inputError
                            if (message != null) {
                                Text(message, color = MaterialTheme.colorScheme.error)
                            } else if (amountsHidden && summary.budgetCents != null) {
                                Text("当前金额已隐藏；修改时请重新输入完整金额")
                            } else {
                                Text("可设置为 0 元；最多两位小数")
                            }
                        },
                        isError = inputError != null,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (parsedAmount) {
                            is BudgetAmountInputResult.Valid -> {
                                onSaveBudget(summary.year, summary.month, parsedAmount.amountCents)
                                showEditor = false
                            }

                            is BudgetAmountInputResult.Invalid -> {
                                inputError = amountInputError(parsedAmount.reason)
                            }
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("保存预算")
                }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (summary.budgetCents != null) {
                        TextButton(
                            onClick = {
                                showEditor = false
                                showClearConfirmation = true
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("清除预算")
                        }
                    }
                    TextButton(
                        onClick = { showEditor = false },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("取消")
                    }
                }
            },
        )
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("清除本月预算？") },
            text = {
                Text("清除后，本月会恢复为“未设置预算”。已有流水不会改变。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearBudget(summary.year, summary.month)
                        showClearConfirmation = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("清除预算")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmation = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("保留预算")
                }
            },
        )
    }
}

@Composable
private fun BudgetMonthSelector(
    year: Int,
    month: Int,
    onMonthChange: (year: Int, month: Int) -> Unit,
) {
    val selectedMonth = YearMonth.of(year, month)
    val canMovePrevious = year > 1 || month > 1
    val canMoveNext = year < 9999 || month < 12
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = QingJiSpacing.xs, vertical = QingJiSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (canMovePrevious) {
                        val previous = selectedMonth.minusMonths(1)
                        onMonthChange(previous.year, previous.monthValue)
                    }
                },
                enabled = canMovePrevious,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
            }
            Text(
                text = "${year}年${month}月",
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(
                onClick = {
                    if (canMoveNext) {
                        val next = selectedMonth.plusMonths(1)
                        onMonthChange(next.year, next.monthValue)
                    }
                },
                enabled = canMoveNext,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
            }
        }
    }
}

@Composable
private fun BudgetSummaryCard(
    summary: MonthlyBudgetSummary,
    display: BudgetDisplayModel,
    amountsHidden: Boolean,
    onEdit: () -> Unit,
    onClear: () -> Unit,
) {
    val statusColor = when (display.state) {
        BudgetDisplayState.NOT_SET, BudgetDisplayState.ZERO_BUDGET -> MaterialTheme.colorScheme.secondary
        BudgetDisplayState.WITHIN_BUDGET -> MaterialTheme.colorScheme.primary
        BudgetDisplayState.EXACTLY_USED -> MaterialTheme.colorScheme.tertiary
        BudgetDisplayState.OVER_BUDGET -> MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(QingJiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(QingJiSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "本月预算",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(QingJiSpacing.xxs))
                    Text(
                        text = if (amountsHidden) "金额已隐藏" else budgetStatusMessage(summary, display),
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor,
                        modifier = Modifier.semantics {
                            if (amountsHidden) contentDescription = "预算状态已隐藏"
                        },
                    )
                }
            }

            if (summary.budgetCents != null) {
                Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xxs)) {
                    Text(
                        text = if (amountsHidden) "预算金额" else "预算额度",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BudgetAmountText(
                        cents = summary.budgetCents,
                        label = "预算额度",
                        amountsHidden = amountsHidden,
                        style = MaterialTheme.typography.headlineMedium,
                        color = statusColor,
                    )
                }
            } else {
                Text(
                    text = "还没有设置本月预算",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "设置后可查看已用金额、剩余预算和使用进度。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (display.state == BudgetDisplayState.NOT_SET) {
                BudgetMetricRow(
                    summary = summary,
                    amountsHidden = amountsHidden,
                )
                Button(
                    onClick = onEdit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("设置本月预算")
                }
            } else {
                LinearProgressIndicator(
                    progress = {
                        if (amountsHidden) 0f else (display.usagePercent ?: 0).coerceIn(0, 100) / 100f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (amountsHidden) {
                                Modifier.clearAndSetSemantics {
                                    contentDescription = "预算进度已隐藏"
                                }
                            } else {
                                Modifier.semantics {
                                    contentDescription = "预算使用进度"
                                    stateDescription = "${display.usagePercent ?: 0}%"
                                    progressBarRangeInfo = ProgressBarRangeInfo(
                                        current = (display.usagePercent ?: 0).coerceIn(0, 100) / 100f,
                                        range = 0f..1f,
                                    )
                                }
                            },
                        ),
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                BudgetMetricRow(summary = summary, amountsHidden = amountsHidden)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onEdit,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("修改预算")
                    }
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        Text("清除预算")
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetMetricRow(
    summary: MonthlyBudgetSummary,
    amountsHidden: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        BudgetMetric(
            label = "本月已用",
            value = null,
            amountCents = summary.spentCents,
            amountsHidden = amountsHidden,
            modifier = Modifier.weight(1f),
        )
        BudgetMetric(
            label = "剩余预算",
            value = summary.remainingCents?.let { visibleYuanText(it, amountsHidden) } ?: "—",
            hiddenValue = summary.remainingCents != null,
            amountsHidden = amountsHidden,
            modifier = Modifier.weight(1f),
        )
        BudgetMetric(
            label = "使用比例",
            value = summary.usagePercent?.let { "$it%" } ?: "—",
            hiddenValue = summary.usagePercent != null,
            amountsHidden = amountsHidden,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BudgetMetric(
    label: String,
    value: String?,
    amountsHidden: Boolean,
    modifier: Modifier = Modifier,
    amountCents: Long? = null,
    hiddenValue: Boolean = true,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = QingJiSpacing.sm, vertical = QingJiSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xxs),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (amountCents != null) {
                BudgetAmountText(
                    cents = amountCents,
                    label = label,
                    amountsHidden = amountsHidden,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    text = if (amountsHidden && hiddenValue) "••••" else value.orEmpty(),
                    modifier = if (amountsHidden && hiddenValue) {
                        Modifier.clearAndSetSemantics {
                            contentDescription = "$label，金额已隐藏"
                        }
                    } else {
                        Modifier
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun BudgetAmountText(
    cents: Long,
    label: String,
    amountsHidden: Boolean,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
) {
    Text(
        text = visibleYuanText(cents, amountsHidden),
        modifier = if (amountsHidden) {
            Modifier.clearAndSetSemantics {
                contentDescription = "$label，金额已隐藏"
            }
        } else {
            Modifier
        },
        style = style,
        fontWeight = FontWeight.SemiBold,
        color = color,
        lineHeight = (style.fontSize.value * 1.22f).sp,
    )
}

private fun budgetStatusMessage(
    summary: MonthlyBudgetSummary,
    display: BudgetDisplayModel,
): String = when (display.state) {
    BudgetDisplayState.NOT_SET -> "尚未设置预算"
    BudgetDisplayState.ZERO_BUDGET -> "预算为 0 元，本月暂未支出。"
    BudgetDisplayState.WITHIN_BUDGET -> if (summary.spentCents == 0L) {
        "预算为正数，本月暂未支出。"
    } else {
        "预算使用中，支出仍在预算范围内。"
    }
    BudgetDisplayState.EXACTLY_USED -> "本月预算刚好用完。"
    BudgetDisplayState.OVER_BUDGET -> if (display.budgetCents == 0L) {
        "预算为 0 元，本月已有支出，已超出预算。"
    } else {
        "本月支出已超出预算。"
    }
}

private fun amountInputError(reason: String): String = when (reason) {
    "EMPTY" -> "请输入预算金额"
    "NOT_NUMBER" -> "请输入有效的金额数字"
    "NOT_POSITIVE" -> "预算不能为负数"
    "TOO_MANY_DECIMALS" -> "金额最多保留两位小数"
    "OUT_OF_RANGE" -> "预算金额不能超过 ¥999,999.99"
    else -> "金额格式不正确"
}

internal enum class BudgetDisplayState {
    NOT_SET,
    ZERO_BUDGET,
    WITHIN_BUDGET,
    EXACTLY_USED,
    OVER_BUDGET,
}

/** Pure presentation values derived from the persisted budget summary. */
internal data class BudgetDisplayModel(
    val state: BudgetDisplayState,
    val budgetCents: Long?,
    val spentCents: Long,
    val remainingCents: Long?,
    val usagePercent: Int?,
)

internal fun MonthlyBudgetSummary.toBudgetDisplayModel(): BudgetDisplayModel {
    val state = when {
        budgetCents == null -> BudgetDisplayState.NOT_SET
        isOverBudget -> BudgetDisplayState.OVER_BUDGET
        budgetCents == 0L -> BudgetDisplayState.ZERO_BUDGET
        spentCents == budgetCents -> BudgetDisplayState.EXACTLY_USED
        else -> BudgetDisplayState.WITHIN_BUDGET
    }
    val displayPercent = if (
        budgetCents != null && budgetCents > 0L && spentCents < budgetCents
    ) {
        // MonthlyBudgetSummary rounds to the nearest integer. Keep a nearly-full budget from
        // looking exhausted when its rounded label reaches 100% with a positive balance.
        (usagePercent ?: 0).coerceAtMost(99)
    } else {
        usagePercent
    }
    return BudgetDisplayModel(
        state = state,
        budgetCents = budgetCents,
        spentCents = spentCents,
        remainingCents = remainingCents,
        usagePercent = displayPercent,
    )
}

internal sealed interface BudgetAmountInputResult {
    data class Valid(val amountCents: Long) : BudgetAmountInputResult
    data class Invalid(val reason: String) : BudgetAmountInputResult
}

/**
 * Budget input permits explicit zero, while all positive values and validation rules continue to
 * come from MoneyUtils. Amounts remain integer cents after parsing.
 */
internal fun parseBudgetAmountInput(input: String): BudgetAmountInputResult {
    val parsed = MoneyUtils.parseAmountToCents(input)
    val trimmed = input.trim()
    if (
        parsed is AmountParseResult.Invalid && parsed.reason == "NOT_POSITIVE" &&
        ZERO_BUDGET_NUMERIC.matches(trimmed) && trimmed.filter { it != '.' }.all { it == '0' }
    ) {
        return if (trimmed.substringAfter('.', "").length <= 2) {
            BudgetAmountInputResult.Valid(0L)
        } else {
            BudgetAmountInputResult.Invalid("TOO_MANY_DECIMALS")
        }
    }
    return when (parsed) {
        is AmountParseResult.Valid -> BudgetAmountInputResult.Valid(parsed.cents)
        is AmountParseResult.Invalid -> BudgetAmountInputResult.Invalid(parsed.reason)
    }
}

private val ZERO_BUDGET_NUMERIC = Regex("""\d+(?:\.\d+)?""")
