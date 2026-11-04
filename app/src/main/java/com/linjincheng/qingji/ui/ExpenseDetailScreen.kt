@file:OptIn(ExperimentalMaterial3Api::class)

package com.linjincheng.qingji.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.data.ExpenseEntry
import com.linjincheng.qingji.ui.components.AmountText
import com.linjincheng.qingji.util.DateUtils
import com.linjincheng.qingji.viewmodel.DetailUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 创建时间展示格式：yyyy-MM-dd HH:mm（任务书 §6 页面 C）。 */
private val CREATED_AT_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** createdAt 毫秒 → 本地时区 "yyyy-MM-dd HH:mm"。minSdk 26，可直接用 java.time。 */
private fun formatCreatedAt(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(CREATED_AT_FORMATTER)

/**
 * 页面 C：消费详情（任务书 §6、契约 §7）。
 * 纯 stateless：数据全部来自 [DetailUiState]，行为只通过回调上抛。
 * notFound 时显示「记录不存在」并可返回，不得崩溃。
 */
@Composable
fun ExpenseDetailScreen(
    state: DetailUiState,
    onEdit: (Long) -> Unit,
    onDeleteClick: () -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onConfirmDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val entry = state.entry

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 顶栏：返回 + 标题
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
                    text = "消费详情",
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            when {
                state.notFound -> NotFoundContent(onBack = onBack)
                entry == null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
                else -> DetailContent(
                    entry = entry,
                    onEdit = onEdit,
                    onDeleteClick = onDeleteClick,
                )
            }
        }
    }

    // 删除确认对话框：标题原文来自任务书 §6
    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = onDismissDeleteDialog,
            title = { Text(text = "确认删除这条消费吗？") },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(
                        text = "删除",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteDialog) { Text(text = "取消") }
            },
        )
    }
}

/** 详情正文：金额卡片 + 字段卡片 + 编辑/删除操作。 */
@Composable
private fun DetailContent(
    entry: ExpenseEntry,
    onEdit: (Long) -> Unit,
    onDeleteClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "金额",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                AmountText(
                    cents = entry.amountCents,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DetailRow(label = "分类", value = entry.category.label)
                DetailRow(label = "备注", value = entry.note.ifBlank { "无备注" })
                DetailRow(label = "消费日期", value = DateUtils.formatDate(entry.expenseDate))
                DetailRow(label = "创建时间", value = formatCreatedAt(entry.createdAt))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onEdit(entry.id) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(text = "编辑")
            }
            Button(
                onClick = onDeleteClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(text = "删除")
            }
        }
    }
}

/** 详情字段行：左侧标签、右侧取值。 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            modifier = Modifier.width(88.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 记录不存在：居中提示 + 返回，不崩溃。 */
@Composable
private fun NotFoundContent(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "记录不存在",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack) {
            Text(text = "返回")
        }
    }
}

@Preview(showBackground = true, name = "详情 · 正常")
@Composable
private fun ExpenseDetailScreenPreview() {
    MaterialTheme {
        ExpenseDetailScreen(
            state = DetailUiState(
                isLoading = false,
                entry = ExpenseEntry(
                    id = 1L,
                    amountCents = 2850,
                    category = ExpenseCategory.FOOD,
                    note = "午饭",
                    expenseDate = 20761L,
                    createdAt = 1793764800000L,
                    updatedAt = 1793764800000L,
                ),
                notFound = false,
                confirmDelete = false,
                deleted = false,
            ),
            onEdit = {},
            onDeleteClick = {},
            onDismissDeleteDialog = {},
            onConfirmDelete = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true, name = "详情 · 记录不存在")
@Composable
private fun ExpenseDetailScreenNotFoundPreview() {
    MaterialTheme {
        ExpenseDetailScreen(
            state = DetailUiState(
                isLoading = false,
                entry = null,
                notFound = true,
                confirmDelete = false,
                deleted = false,
            ),
            onEdit = {},
            onDeleteClick = {},
            onDismissDeleteDialog = {},
            onConfirmDelete = {},
            onBack = {},
        )
    }
}
