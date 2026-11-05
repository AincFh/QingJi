package com.linjincheng.qingji.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.data.QingJiPreferences
import com.linjincheng.qingji.data.ThemeMode
import com.linjincheng.qingji.ui.components.CategoryIconBadge
import com.linjincheng.qingji.ui.theme.QingJiSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: QingJiPreferences,
    onThemeModeChange: (ThemeMode) -> Unit,
    onHideAmountsChange: (Boolean) -> Unit,
    onDefaultCategoryChange: (ExpenseCategory) -> Unit,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    onOpenBudget: (() -> Unit)? = null,
    onExportBackup: (() -> Unit)? = null,
    onRestoreBackup: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val versionName = remember(context) { readAppVersionName(context) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = QingJiSpacing.md, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showBackButton) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (showBackButton) 4.dp else QingJiSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "按你的习惯调整轻记",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = QingJiSpacing.md)
                    .padding(top = QingJiSpacing.xs, bottom = QingJiSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xl),
            ) {
                SettingsGroup(title = "外观与显示") {
                    SettingsCard {
                        Column(
                            modifier = Modifier.padding(QingJiSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(QingJiSpacing.sm),
                        ) {
                            Text(
                                text = "主题模式",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "跟随系统外观，或固定使用浅色、深色。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
                                verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
                            ) {
                                ThemeMode.entries.forEach { mode ->
                                    SettingsThemeOption(
                                        label = mode.label,
                                        selected = preferences.themeMode == mode,
                                        onClick = { onThemeModeChange(mode) },
                                    )
                                }
                            }

                            SettingsDivider()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = preferences.hideAmounts,
                                        role = Role.Switch,
                                        onValueChange = onHideAmountsChange,
                                    )
                                    .heightIn(min = 68.dp)
                                    .padding(vertical = QingJiSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "隐藏金额",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = if (preferences.hideAmounts) {
                                            "当前已开启：首页、账单、统计和详情中的精确金额及 TalkBack 读屏金额文本会被遮挡；趋势形状、活动密度、分类占比与笔数仍保留，记账输入仍可见。"
                                        } else {
                                            "当前未开启。开启后会遮挡首页、账单、统计和详情中的精确金额及 TalkBack 读屏金额文本；趋势形状、活动密度、分类占比与笔数仍保留，记账输入仍可见。"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.width(QingJiSpacing.sm))
                                Switch(
                                    checked = preferences.hideAmounts,
                                    onCheckedChange = null,
                                )
                            }
                        }
                    }
                }

                SettingsGroup(title = "记账偏好") {
                    SettingsCard {
                        Column {
                            Column(
                                modifier = Modifier.padding(QingJiSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
                            ) {
                                Text(
                                    text = "新记录的默认分类",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "保存后会自动选中，记账时仍可随时更改。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            SettingsDivider()
                            Column(modifier = Modifier.selectableGroup()) {
                                ExpenseCategory.entries.forEachIndexed { index, category ->
                                    val selected = preferences.defaultCategory == category
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 56.dp)
                                            .background(
                                                if (selected) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                                                } else {
                                                    MaterialTheme.colorScheme.surface
                                                },
                                            )
                                            .selectable(
                                                selected = selected,
                                                role = Role.RadioButton,
                                                onClick = { onDefaultCategoryChange(category) },
                                            )
                                            .padding(
                                                horizontal = QingJiSpacing.md,
                                                vertical = QingJiSpacing.xs,
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CategoryIconBadge(
                                            category = category,
                                            modifier = Modifier.size(32.dp),
                                            size = 32.dp,
                                        )
                                        Spacer(Modifier.width(QingJiSpacing.sm))
                                        Text(
                                            text = category.label,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                    }
                                    if (index < ExpenseCategory.entries.lastIndex) {
                                        SettingsDivider(startIndent = 64.dp)
                                    }
                                }
                            }
                        }
                    }
                }

                if (onOpenBudget != null) {
                    SettingsGroup(title = "消费规划") {
                        SettingsCard {
                            SettingsActionRow(
                                title = "月度预算",
                                description = "设置本月额度，查看已用和剩余。",
                                onClick = onOpenBudget,
                            )
                        }
                    }
                }

                SettingsGroup(title = "数据与存储") {
                    SettingsCard {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsInfoRow(
                                icon = Icons.Filled.Lock,
                                title = "数据保存在本机",
                                description = "消费记录保存在本机 SQLite，偏好保存在应用本地。轻记不提供云同步；卸载应用或更换设备时，本地数据可能无法保留。",
                            )
                            SettingsDivider()
                            Column(
                                modifier = Modifier.padding(QingJiSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
                            ) {
                                Text(
                                    text = "手动备份与恢复",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "导出通过系统文件选择器保存 JSON，包含消费记录和月预算，不包含主题及隐私偏好。恢复前会校验并展示预览；确认后按流水身份合并，相同流水会跳过，备份中的预算会覆盖对应月份设置，不会先清空本机数据。文件保存位置由你选择。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (onExportBackup != null || onRestoreBackup != null) {
                                SettingsDivider()
                                if (onExportBackup != null) {
                                    SettingsActionRow(
                                        title = "导出本地备份",
                                        description = "通过系统文件选择器保存 JSON 文件。",
                                        onClick = onExportBackup,
                                    )
                                }
                                if (onExportBackup != null && onRestoreBackup != null) {
                                    SettingsDivider(startIndent = QingJiSpacing.md)
                                }
                                if (onRestoreBackup != null) {
                                    SettingsActionRow(
                                        title = "恢复本地备份",
                                        description = "选择文件后先查看校验结果和合并预览。",
                                        onClick = onRestoreBackup,
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsGroup(title = "关于") {
                    SettingsCard {
                        Column(
                            modifier = Modifier.padding(QingJiSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
                        ) {
                            Text(
                                text = "轻记 · v$versionName",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "把每天的花费记下来，慢慢看清自己的消费习惯。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun readAppVersionName(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()?.takeIf(String::isNotBlank) ?: "1.0"

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.sm)) {
        SettingsSectionTitle(title)
        content()
    }
}

@Composable
private fun SettingsThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = QingJiSpacing.md, vertical = QingJiSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (selected) {
                Spacer(Modifier.width(QingJiSpacing.xs))
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(QingJiSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.width(QingJiSpacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = QingJiSpacing.md, vertical = QingJiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(QingJiSpacing.sm))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SettingsDivider(startIndent: Dp = QingJiSpacing.md) {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startIndent, end = QingJiSpacing.md)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
        content = content,
    )
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}
