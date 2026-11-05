package com.linjincheng.qingji.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.linjincheng.qingji.data.ExpenseBackupPreview
import com.linjincheng.qingji.ui.theme.QingJiSpacing
import java.time.YearMonth
import java.util.Locale

data class BackupScreenState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val importPreview: ExpenseBackupPreview? = null,
    val feedback: BackupScreenFeedback? = null,
)

data class BackupScreenFeedback(
    val message: String,
    val isError: Boolean,
)

/**
 * Local backup UI. File selection, codec/repository work, and lifecycle ownership stay with the caller.
 * The caller should expose SAF document pickers through [onExport] and [onSelectFile].
 */
@Composable
fun BackupScreen(
    state: BackupScreenState,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onSelectFile: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onDismissFeedback: () -> Unit,
) {
    val busy = state.isBusy
    BackHandler(enabled = busy || state.importPreview == null) {
        if (!busy) onBack()
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = QingJiSpacing.md, top = QingJiSpacing.xs, bottom = QingJiSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, enabled = !busy, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = "本地数据备份",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = QingJiSpacing.md)
                    .padding(top = QingJiSpacing.sm, bottom = QingJiSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(QingJiSpacing.md),
            ) {
                BackupInfoCard(
                    title = "导出 JSON 文件",
                    description = "把消费记录和每月预算保存为本地 JSON 文件。文件不包含主题、隐私偏好或设备信息。",
                ) {
                    Button(
                        onClick = onExport,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        if (state.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(QingJiSpacing.xs))
                        }
                        Text(if (state.isExporting) "正在准备备份…" else "导出备份")
                    }
                }

                BackupInfoCard(
                    title = "从备份合并",
                    description = "导入前会检查文件并显示预览。相同流水身份会跳过，新流水会合并；备份中的预算会覆盖对应月份。",
                ) {
                    OutlinedButton(
                        onClick = onSelectFile,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("选择 JSON 备份")
                    }
                }

                state.feedback?.let { feedback ->
                    BackupFeedbackCard(feedback = feedback, onDismiss = onDismissFeedback)
                }
            }
        }
    }

    state.importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!state.isImporting) onCancelImport() },
            title = { Text("确认合并备份") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(QingJiSpacing.sm),
                ) {
                    item { Text("新增流水：${preview.newExpenses} 笔") }
                    item { Text("跳过重复流水：${preview.skippedExpenses} 笔") }
                    item {
                        Text(
                            text = "将覆盖的预算月份（${preview.budgetMonths.size}）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (preview.budgetMonths.isEmpty()) {
                        item { Text("备份中没有预算数据。") }
                    } else {
                        items(preview.budgetMonths, key = { it.toString() }) { month ->
                            Text(
                                text = formatBudgetMonth(month),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    item {
                        Text(
                            text = "确认后才会写入。相同 backupId 的流水保持本地版本，预算按月份覆盖。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.isImporting) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(QingJiSpacing.xs))
                                Text("正在合并数据…")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmImport,
                    enabled = !state.isImporting,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(if (state.isImporting) "正在合并…" else "确认合并")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onCancelImport,
                    enabled = !state.isImporting,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun BackupInfoCard(
    title: String,
    description: String,
    action: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(QingJiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(QingJiSpacing.sm),
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action()
        }
    }
}

@Composable
private fun BackupFeedbackCard(feedback: BackupScreenFeedback, onDismiss: () -> Unit) {
    val container = if (feedback.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (feedback.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = QingJiSpacing.md, end = QingJiSpacing.xs, top = QingJiSpacing.xs, bottom = QingJiSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (feedback.isError) {
                Text(
                    text = "!",
                    modifier = Modifier
                        .size(24.dp)
                        .clearAndSetSemantics { contentDescription = "操作失败" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = content,
                )
            } else {
                Icon(Icons.Filled.CheckCircle, contentDescription = "操作成功", tint = content)
            }
            Text(
                text = feedback.message,
                modifier = Modifier.weight(1f).padding(horizontal = QingJiSpacing.sm),
                style = MaterialTheme.typography.bodyMedium,
                color = content,
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("关闭", color = content)
            }
        }
    }
}

internal fun formatBudgetMonth(month: YearMonth): String =
    String.format(Locale.CHINA, "%04d年%02d月", month.year, month.monthValue)

internal val BackupScreenState.isBusy: Boolean
    get() = isExporting || isImporting
