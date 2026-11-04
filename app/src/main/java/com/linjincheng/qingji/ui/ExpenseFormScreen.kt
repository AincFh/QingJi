@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.linjincheng.qingji.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.ui.components.SectionTitle
import com.linjincheng.qingji.util.DateUtils
import com.linjincheng.qingji.viewmodel.FormUiState

/** 一天的毫秒数；epochDay 与 DatePicker 的 UTC 毫秒互转用（纯 UTC 运算，无时区漂移）。 */
private const val MS_PER_DAY = 24L * 60L * 60L * 1000L

/** 契约 §7：amountError 原因键 → 中文提示的固定映射。 */
private val AMOUNT_ERROR_MESSAGES: Map<String, String> = mapOf(
    "EMPTY" to "请输入金额",
    "NOT_NUMBER" to "金额格式不正确",
    "NOT_POSITIVE" to "金额必须大于 0",
    "TOO_MANY_DECIMALS" to "最多两位小数",
    "OUT_OF_RANGE" to "单笔金额限 0.01–999999.99 元",
)

private fun amountErrorMessage(reason: String): String =
    AMOUNT_ERROR_MESSAGES[reason] ?: "金额不正确，请检查后重试"

/**
 * 页面 B：新增 / 编辑消费（任务书 §6、契约 §7）。
 * 纯 stateless：数据全部来自 [FormUiState]，行为只通过回调上抛；
 * 对话框开合属于界面局部状态，用 rememberSaveable 保存，不进 UiState。
 */
@Composable
fun ExpenseFormScreen(
    state: FormUiState,
    onAmountChange: (String) -> Unit,
    onCategorySelect: (ExpenseCategory) -> Unit,
    onDateChange: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 顶栏：返回 + 标题（新增 / 编辑）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = if (state.editingId == null) "记一笔" else "编辑消费",
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 金额：数字键盘，校验错误显示在辅助位
                OutlinedTextField(
                    value = state.amountText,
                    onValueChange = onAmountChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "金额（元）") },
                    placeholder = { Text(text = "如 28.50") },
                    leadingIcon = {
                        Text(
                            text = "¥",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    isError = state.amountError != null,
                    supportingText = {
                        state.amountError?.let { reason ->
                            Text(
                                text = amountErrorMessage(reason),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                // 分类：五个固定分类单选（默认餐饮由 state.selectedCategory 决定）
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(text = "分类")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ExpenseCategory.entries.forEach { category ->
                            CategoryChip(
                                label = category.label,
                                selected = state.selectedCategory == category,
                                onClick = { onCategorySelect(category) },
                            )
                        }
                    }
                }

                // 消费日期：只读字段，点击弹 material3 DatePicker
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = DateUtils.formatDate(state.epochDay),
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        label = { Text(text = "消费日期") },
                        trailingIcon = {
                            Icon(Icons.Filled.DateRange, contentDescription = "选择日期")
                        },
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showDatePicker = true },
                    )
                }

                // 备注：可不填
                OutlinedTextField(
                    value = state.note,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "备注（选填）") },
                    placeholder = { Text(text = "最多 120 字") },
                )

                // 保存失败：明确提示
                if (state.saveFailed) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "保存失败，请重试；若持续失败，请检查金额与日期后重新保存。",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                // 保存：saving 时禁用，防止重复提交
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !state.saving,
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(text = if (state.saving) "保存中…" else "保存")
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.epochDay * MS_PER_DAY,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        // selectedDateMillis 为所选日期的 UTC 零点毫秒；整除换回 epochDay。
                        datePickerState.selectedDateMillis?.let { millis ->
                            onDateChange(millis / MS_PER_DAY)
                        }
                        showDatePicker = false
                    },
                ) { Text(text = "确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(text = "取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** 分类单选项：选中主色填充，未选中次级面，无阴影。 */
@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = if (selected) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = label)
    }
}

@Preview(showBackground = true, name = "表单 · 新增")
@Composable
private fun ExpenseFormScreenPreview() {
    MaterialTheme {
        ExpenseFormScreen(
            state = FormUiState(
                editingId = null,
                amountText = "",
                selectedCategory = ExpenseCategory.FOOD,
                epochDay = 20761L,
                note = "",
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
            onBack = {},
        )
    }
}
