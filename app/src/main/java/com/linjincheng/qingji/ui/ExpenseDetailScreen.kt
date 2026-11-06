@file:OptIn(ExperimentalMaterial3Api::class)

package com.linjincheng.qingji.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.data.ExpenseEntry
import com.linjincheng.qingji.ui.components.CategoryIconBadge
import com.linjincheng.qingji.ui.components.visibleYuanText
import com.linjincheng.qingji.ui.theme.QingJiSpacing
import com.linjincheng.qingji.util.DateUtils
import com.linjincheng.qingji.viewmodel.DetailUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** createdAt 毫秒转换为设备本地时间；按当前 Locale 构造格式器。 */
private fun formatCreatedAt(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault()))

/** 消费详情纯无状态页面；显示状态只读自 UiState，所有操作只通过回调发出。 */
@Composable
fun ExpenseDetailScreen(
    state: DetailUiState,
    onEdit: (Long) -> Unit,
    onDeleteClick: () -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onConfirmDelete: () -> Unit,
    onBack: () -> Unit,
    onRetryDetailLoad: () -> Unit = {},
    onRetryDelete: () -> Unit = onConfirmDelete,
    onClearFeedback: () -> Unit = {},
    amountsHidden: Boolean = false,
    onCopy: ((Long) -> Unit)? = null,
) {
    val entry = state.entry
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onClearFeedback()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (entry != null && !state.isLoading && !state.notFound && !state.loadFailed) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = QingJiSpacing.md, vertical = QingJiSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
                    ) {
                        if (onCopy != null) {
                            OutlinedButton(
                                onClick = { onCopy(entry.id) },
                                enabled = !state.isDeleting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text("复制为新记录")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(QingJiSpacing.sm)) {
                            Button(
                                onClick = { onEdit(entry.id) },
                                enabled = !state.isDeleting,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("编辑")
                            }
                            OutlinedButton(
                                onClick = onDeleteClick,
                                enabled = !state.isDeleting,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp),
                                shape = MaterialTheme.shapes.large,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                if (state.isDeleting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                } else {
                                    Icon(Icons.Filled.Delete, contentDescription = null)
                                }
                                Spacer(Modifier.size(8.dp))
                                Text(if (state.isDeleting) "正在删除…" else "删除")
                            }
                        }
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
                IconButton(onClick = onBack, enabled = !state.isDeleting) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = "消费详情",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    state.isLoading -> StatusContent(
                        title = "正在加载消费记录…",
                        showProgress = true,
                    )

                    state.loadFailed -> DetailLoadFailure(
                        onRetry = onRetryDetailLoad,
                        onBack = onBack,
                    )

                    state.notFound -> NotFoundContent(onBack = onBack)

                    state.deleted -> StatusContent(
                        title = "消费已删除",
                        showProgress = false,
                        onBack = onBack,
                    )

                    entry != null -> DetailContent(
                        entry = entry,
                        deleteFailed = state.deleteFailed,
                        onRetryDelete = onRetryDelete,
                        amountsHidden = amountsHidden,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = QingJiSpacing.md)
                            .padding(top = QingJiSpacing.sm, bottom = QingJiSpacing.lg),
                    )

                    else -> StatusContent(
                        title = "暂时无法显示这条消费",
                        showProgress = false,
                        onBack = onBack,
                    )
                }
            }
        }
    }

    if (state.confirmDelete && entry != null) {
        AlertDialog(
            onDismissRequest = onDismissDeleteDialog,
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("确认删除这条消费吗？") },
            text = {
                Text("删除后，这条消费记录将从本机移除。")
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteDialog) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DetailContent(
    entry: ExpenseEntry,
    deleteFailed: Boolean,
    onRetryDelete: () -> Unit,
    amountsHidden: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(QingJiSpacing.lg),
    ) {
        if (deleteFailed) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Column(modifier = Modifier.padding(QingJiSpacing.md)) {
                    Text(
                        text = "删除没有完成",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = "这条记录仍保留在本机，可以重试。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    TextButton(onClick = onRetryDelete) {
                        Text("重试删除", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.padding(QingJiSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(QingJiSpacing.md),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(QingJiSpacing.sm),
                ) {
                    CategoryIconBadge(
                        category = entry.category,
                        modifier = Modifier.clearAndSetSemantics { },
                        size = 44.dp,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "消费分类",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                        )
                        Text(
                            text = entry.category.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "消费金额",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                    )
                    Text(
                        text = visibleYuanText(entry.amountCents, amountsHidden),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = if (amountsHidden) {
                                    "金额已隐藏"
                                } else {
                                    visibleYuanText(entry.amountCents, hidden = false)
                                }
                            },
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = QingJiSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(QingJiSpacing.md),
        ) {
            if (entry.note.isNotBlank()) {
                DetailField(label = "备注", value = entry.note)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                )
            }
            DetailField(
                label = "消费日期",
                value = DateUtils.formatDate(entry.expenseDate),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )
            DetailField(
                label = "创建时间",
                value = formatCreatedAt(entry.createdAt),
            )
        }
    }
}

@Composable
private fun DetailLoadFailure(
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "暂时无法读取这条消费",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "记录仍保存在本机。你可以重试，或返回流水。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("重新读取") }
        TextButton(onClick = onBack) { Text("返回") }
    }
}

@Composable
private fun DetailField(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun StatusContent(
    title: String,
    showProgress: Boolean,
    message: String? = null,
    onBack: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showProgress) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onBack != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onBack) { Text("返回") }
        }
    }
}

@Composable
private fun NotFoundContent(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "未找到这条消费",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "记录可能已被移除，或暂时无法读取。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onBack) { Text("返回") }
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
                    amountCents = 2850L,
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

@Preview(showBackground = true, name = "详情 · 加载中")
@Composable
private fun ExpenseDetailLoadingPreview() {
    MaterialTheme {
        ExpenseDetailScreen(
            state = DetailUiState(
                isLoading = true,
                entry = null,
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
private fun ExpenseDetailNotFoundPreview() {
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
