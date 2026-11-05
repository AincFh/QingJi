@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.linjincheng.qingji.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.ui.components.CategoryIconBadge
import com.linjincheng.qingji.ui.components.SectionTitle
import com.linjincheng.qingji.ui.components.categoryAccent
import com.linjincheng.qingji.ui.theme.QingJiSpacing
import com.linjincheng.qingji.ui.theme.LocalQingJiActionColor
import com.linjincheng.qingji.ui.theme.LocalQingJiOnActionColor
import com.linjincheng.qingji.util.DateUtils
import com.linjincheng.qingji.viewmodel.FormUiState
import java.time.LocalDate

/** 一天的毫秒数；epochDay 与 DatePicker 的 UTC 毫秒互转用（纯 UTC 运算，无时区漂移）。 */
private const val MS_PER_DAY = 24L * 60L * 60L * 1000L
private const val NOTE_MAX_CODE_POINTS = 120

/** amountError 原因键 → 面向用户的中文提示。 */
private val AMOUNT_ERROR_MESSAGES = mapOf(
    "EMPTY" to "请输入金额",
    "NOT_NUMBER" to "金额格式不正确",
    "NOT_POSITIVE" to "金额必须大于 0",
    "TOO_MANY_DECIMALS" to "最多两位小数",
    "OUT_OF_RANGE" to "单笔金额限 0.01–999999.99 元",
)

private fun amountErrorMessage(reason: String): String =
    AMOUNT_ERROR_MESSAGES[reason] ?: "金额不正确，请检查后重试"

/** 按 Unicode code point 限长，避免截断补充平面字符的代理项。 */
private fun limitNoteLength(note: String): String {
    val codePointCount = Character.codePointCount(note, 0, note.length)
    if (codePointCount <= NOTE_MAX_CODE_POINTS) return note
    val endIndex = Character.offsetByCodePoints(note, 0, NOTE_MAX_CODE_POINTS)
    return note.substring(0, endIndex)
}

/** 新增和编辑共用的纯状态表单；输入、保存与离开行为全部由回调交给 ViewModel。 */
@Composable
fun ExpenseFormScreen(
    state: FormUiState,
    onAmountChange: (String) -> Unit,
    onCategorySelect: (ExpenseCategory) -> Unit,
    onDateChange: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onBackRequested: () -> Unit,
    onDismissDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onRetryEditLoad: () -> Unit = {},
    recentCategories: List<ExpenseCategory> = emptyList(),
    copyMode: Boolean = false,
) {
    // 仅日期选择器开合属于短暂的界面状态；表单数据和离开确认均来自 UiState。
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val amountFocusRequester = remember { FocusRequester() }
    LaunchedEffect(state.editingId, state.isLoading) {
        if (state.editingId == null && !state.isLoading) {
            amountFocusRequester.requestFocus()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        bottomBar = {
            Surface(
                modifier = Modifier.navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = QingJiSpacing.md, vertical = QingJiSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
                ) {
                    val feedbackMessage = when {
                        state.amountError != null -> amountErrorMessage(state.amountError)
                        state.saveFailed -> "保存失败，请重试；当前填写内容仍保留。"
                        state.saved -> if (copyMode) "已保存为新记录" else "保存成功"
                        else -> null
                    }
                    if (feedbackMessage != null) {
                        FormFeedback(
                            message = feedbackMessage,
                            isError = state.amountError != null || state.saveFailed,
                        )
                    }
                    Button(
                        onClick = onSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp),
                        enabled = !state.saving && !state.saved && !state.isLoading &&
                            !state.loadFailed && !state.notFound,
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LocalQingJiActionColor.current,
                            contentColor = LocalQingJiOnActionColor.current,
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.size(10.dp))
                        }
                        Text(
                            text = when {
                                state.saving -> "正在保存…"
                                state.saved -> "已保存"
                                copyMode -> "保存为新记录"
                                state.editingId == null -> "保存消费"
                                else -> "保存修改"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackRequested, enabled = !state.saving) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = when {
                        copyMode -> "复制为新记录"
                        state.editingId == null -> "记一笔"
                        else -> "编辑消费"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (copyMode) {
                Text(
                    text = "已沿用原金额、分类和备注；日期默认为今天，可修改后保存。",
                    modifier = Modifier.padding(
                        start = QingJiSpacing.md,
                        end = QingJiSpacing.md,
                        bottom = QingJiSpacing.xs,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                state.isLoading -> FormStatusContent(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    title = if (copyMode) "正在准备复制记录…" else "正在读取消费记录…",
                    message = if (copyMode) "读取完成后会保留原记录，并创建一条新记录。" else "读取完成后即可继续编辑。",
                    showProgress = true,
                )

                state.loadFailed -> FormStatusContent(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    title = "暂时无法读取这条消费",
                    message = "原记录没有被修改，请重试读取或返回。",
                    onAction = onRetryEditLoad,
                    actionLabel = "重试读取",
                )

                state.notFound -> FormStatusContent(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    title = "这条消费记录已不存在",
                    message = "返回上一页后可以查看当前记录。",
                    onAction = onBackRequested,
                    actionLabel = "返回上一页",
                )

                else -> Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = QingJiSpacing.md)
                        .padding(top = QingJiSpacing.xs, bottom = QingJiSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(QingJiSpacing.lg),
                ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle(text = "支出金额")
                    OutlinedTextField(
                        value = state.amountText,
                        onValueChange = onAmountChange,
                        enabled = !state.saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(amountFocusRequester),
                            label = { Text("金额（元）") },
                            placeholder = { Text("0.00") },
                        leadingIcon = {
                            Text(
                                text = "¥",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        textStyle = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        isError = state.amountError != null,
                        supportingText = {
                            val message = state.amountError?.let(::amountErrorMessage)
                                ?: "填写 0.01 至 999999.99 元"
                            Text(
                                text = message,
                                color = if (state.amountError != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val recentCategoryList = remember(recentCategories) {
                        recentCategories.distinct().take(3)
                    }
                    if (recentCategoryList.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs)) {
                            Text(
                                text = "最近使用",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
                                verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
                            ) {
                                recentCategoryList.forEach { category ->
                                    RecentCategoryOption(
                                        category = category,
                                        selected = state.selectedCategory == category,
                                        onClick = { onCategorySelect(category) },
                                        enabled = !state.saving,
                                    )
                                }
                            }
                        }
                    }
                    SectionTitle(text = "消费分类")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
                    ) {
                        ExpenseCategory.entries.forEach { category ->
                            CategoryOption(
                                category = category,
                                selected = state.selectedCategory == category,
                                onClick = { onCategorySelect(category) },
                                enabled = !state.saving,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle(text = "消费日期")
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !state.saving, role = Role.Button) { showDatePicker = true },
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(QingJiSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Filled.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = DateUtils.formatDate(state.epochDay),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "点按修改日期",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (state.dateError) {
                        Text(
                            text = "日期不能晚于今天，请调整后再保存。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(text = "备注（选填）")
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = { onNoteChange(limitNoteLength(it)) },
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如：午餐、打车") },
                        supportingText = {
                            val count = Character.codePointCount(state.note, 0, state.note.length)
                            Text("可留空 · $count/$NOTE_MAX_CODE_POINTS 字")
                        },
                        minLines = 2,
                        maxLines = 3,
                        shape = MaterialTheme.shapes.large,
                    )
                }
            }
            }
        }
    }

    if (showDatePicker) {
        val todayEpochDay = DateUtils.todayEpochDay()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.epochDay * MS_PER_DAY,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis / MS_PER_DAY <= todayEpochDay

                override fun isSelectableYear(year: Int): Boolean =
                    year <= LocalDate.ofEpochDay(todayEpochDay).year
            },
        )
        val datePickerColors = DatePickerDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            headlineContentColor = MaterialTheme.colorScheme.onSurface,
            weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            subheadContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            navigationContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            yearContentColor = MaterialTheme.colorScheme.onSurface,
            currentYearContentColor = MaterialTheme.colorScheme.primary,
            selectedYearContentColor = MaterialTheme.colorScheme.onPrimary,
            selectedYearContainerColor = MaterialTheme.colorScheme.primary,
            dayContentColor = MaterialTheme.colorScheme.onSurface,
            disabledDayContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
            selectedDayContainerColor = MaterialTheme.colorScheme.primary,
            todayContentColor = MaterialTheme.colorScheme.primary,
            todayDateBorderColor = MaterialTheme.colorScheme.primary,
            dividerColor = MaterialTheme.colorScheme.outlineVariant,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onDateChange(millis / MS_PER_DAY)
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
            colors = datePickerColors,
        ) {
            DatePicker(
                state = datePickerState,
                colors = datePickerColors,
            )
        }
    }

    if (state.confirmDiscard) {
        AlertDialog(
            onDismissRequest = onDismissDiscard,
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("放弃未保存的更改？") },
            text = { Text("离开后，本次填写的内容将不会保存。") },
            confirmButton = {
                TextButton(onClick = onConfirmDiscard) {
                    Text("放弃更改", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDiscard) { Text("继续编辑") }
            },
        )
    }
}

@Composable
private fun FormStatusContent(
    modifier: Modifier = Modifier,
    title: String,
    message: String,
    showProgress: Boolean = false,
    onAction: (() -> Unit)? = null,
    actionLabel: String = "",
) {
    Column(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .padding(horizontal = QingJiSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showProgress) {
            CircularProgressIndicator()
            Spacer(Modifier.size(QingJiSpacing.md))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(QingJiSpacing.xs))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onAction != null) {
            Spacer(Modifier.size(QingJiSpacing.sm))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun RecentCategoryOption(
    category: ExpenseCategory,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val accent = categoryAccent(category)
    Surface(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CategoryIconBadge(
                category = category,
                modifier = Modifier.size(26.dp).clearAndSetSemantics { },
                size = 26.dp,
            )
            Text(
                text = category.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoryOption(
    category: ExpenseCategory,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val accent = categoryAccent(category)
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.47f)
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(17.dp),
        color = if (selected) accent.copy(alpha = 0.11f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            CategoryIconBadge(
                category = category,
                modifier = Modifier.size(30.dp).clearAndSetSemantics { },
                size = 30.dp,
            )
            Text(
                text = category.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun FormFeedback(
    message: String,
    isError: Boolean,
) {
    val container = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val content = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview(showBackground = true, name = "表单 · 新增")
@Composable
private fun ExpenseFormScreenPreview() {
    MaterialTheme {
        ExpenseFormScreen(
            state = FormUiState(
                editingId = null,
                amountText = "28.50",
                selectedCategory = ExpenseCategory.FOOD,
                epochDay = 20761L,
                note = "午饭",
                saving = false,
                amountError = null,
                saveFailed = false,
                saved = false,
            ),
            onAmountChange = {},
            onCategorySelect = {},
            onDateChange = {},
            onNoteChange = {},
            onSave = {},
            onBackRequested = {},
            onDismissDiscard = {},
            onConfirmDiscard = {},
        )
    }
}
