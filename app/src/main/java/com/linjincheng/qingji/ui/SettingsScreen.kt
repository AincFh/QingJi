package com.linjincheng.qingji.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
                Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.sm)) {
                    SettingsSectionTitle("外观与显示")
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
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
                                verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs),
                            ) {
                                ThemeMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = preferences.themeMode == mode,
                                        onClick = { onThemeModeChange(mode) },
                                        label = { Text(mode.label) },
                                    )
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.sm)) {
                    SettingsSectionTitle("隐私")
                    SettingsCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = preferences.hideAmounts,
                                    role = Role.Switch,
                                    onValueChange = onHideAmountsChange,
                                )
                                .heightIn(min = 68.dp)
                                .padding(QingJiSpacing.md),
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
                                    text = "在首页、账单、统计和详情中隐藏金额；记账输入时仍可见。",
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

                Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.sm)) {
                    SettingsSectionTitle("记账习惯")
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
                    SettingsCard {
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
                                        .padding(horizontal = QingJiSpacing.md, vertical = QingJiSpacing.xs),
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
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 64.dp)
                                            .height(1.dp)
                                            .background(
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.sm)) {
                    SettingsSectionTitle("数据与关于")
                    SettingsCard {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(QingJiSpacing.md),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(QingJiSpacing.sm))
                                Column(verticalArrangement = Arrangement.spacedBy(QingJiSpacing.xs)) {
                                    Text(
                                        text = "数据保存在本机",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "消费记录保存在本机 SQLite，偏好设置保存在应用本地。轻记不需要登录，也不提供云同步；卸载应用或更换设备可能会清除本机数据。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = QingJiSpacing.md, end = QingJiSpacing.md)
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            )
                            Column(
                                modifier = Modifier.padding(QingJiSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
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
}

@Suppress("DEPRECATION")
private fun readAppVersionName(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()?.takeIf(String::isNotBlank) ?: "1.0"

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
