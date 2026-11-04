@file:OptIn(ExperimentalMaterial3Api::class)

package com.linjincheng.qingji.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.data.ExpenseEntry
import com.linjincheng.qingji.ui.components.AmountText
import com.linjincheng.qingji.ui.components.EmptyHint
import com.linjincheng.qingji.ui.components.SectionTitle
import com.linjincheng.qingji.ui.components.StatBar
import com.linjincheng.qingji.ui.components.yuanText
import com.linjincheng.qingji.util.DateUtils
import com.linjincheng.qingji.viewmodel.CategoryStat
import com.linjincheng.qingji.viewmodel.HomeUiState

/**
 * 页面 A：首页 / 消费流水（任务书 §6、契约 §7）。
 * 纯 stateless：数据全部来自 [HomeUiState]，行为只通过回调上抛；
 * 不创建 ViewModel、不引用 repository。
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenAdd: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onMonthChange: (Int) -> Unit,
) {
    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    Button(
                        onClick = onOpenAdd,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(text = "记一笔", fontSize = 18.sp)
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
            HomeHeader(state = state, onMonthChange = onMonthChange)
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { MonthlyTotalCard(totalCents = state.totalCents) }
                item {
                    CategoryStatsCard(
                        stats = state.categoryStats,
                        totalCents = state.totalCents,
                        isLoading = state.isLoading,
                    )
                }
                item { SectionTitle(text = "消费流水") }
                if (state.entries.isEmpty()) {
                    if (!state.isLoading) {
                        item { EmptyHint(text = "还没有记录，记下第一笔消费吧") }
                    }
                } else {
                    items(state.entries, key = { it.id }) { entry ->
                        ExpenseRow(entry = entry, onClick = { onOpenDetail(entry.id) })
                    }
                }
            }
        }
    }
}

/** 顶栏：应用名「轻记」+ 月份（来自 state.monthTitle）+ 上月/下月切换。 */
@Composable
private fun HomeHeader(
    state: HomeUiState,
    onMonthChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "轻记",
            modifier = Modifier.weight(1f),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = { onMonthChange(-1) }) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上个月")
        }
        Text(
            text = state.monthTitle,
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = { onMonthChange(1) }) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下个月")
        }
    }
}

/** 第一块：本月支出，明显大字号；无数据时由 formatCents(0) 显示 ¥0.00。 */
@Composable
private fun MonthlyTotalCard(totalCents: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "本月支出",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            AmountText(
                cents = totalCents,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 第二块：分类统计。按 state.categoryStats 顺序渲染（恒含 5 类，0 元自然排后）。 */
@Composable
private fun CategoryStatsCard(
    stats: List<CategoryStat>,
    totalCents: Long,
    isLoading: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle(text = "分类统计")
            val hasData = totalCents > 0 && stats.any { it.totalCents > 0 }
            if (!hasData) {
                // 无任何数据：引导文案；percent 由契约预计算，界面不做除法，无除零。
                if (!isLoading) {
                    Text(
                        text = "本月还没有分类支出，记下一笔看看钱花在哪里吧",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                stats.forEach { stat ->
                    CategoryStatRow(stat = stat)
                }
            }
        }
    }
}

/** 单个分类统计行：名称 / 金额 / 占比 + 进度条（占比 0 时进度条可见为 0）。 */
@Composable
private fun CategoryStatRow(stat: CategoryStat) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stat.category.label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = yuanText(stat.totalCents),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${stat.percent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        StatBar(percent = stat.percent, modifier = Modifier.fillMaxWidth())
    }
}

/** 第三块：单条流水（分类、备注、金额、日期），点击进详情。 */
@Composable
private fun ExpenseRow(
    entry: ExpenseEntry,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.category.label,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entry.note.ifBlank { "无备注" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                AmountText(cents = entry.amountCents, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = DateUtils.formatDate(entry.expenseDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "首页 · 有数据")
@Composable
private fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen(
            state = HomeUiState(
                year = 2026,
                month = 11,
                monthTitle = "2026 年 11 月",
                totalCents = 6200,
                categoryStats = listOf(
                    CategoryStat(ExpenseCategory.FOOD, 5000, 81),
                    CategoryStat(ExpenseCategory.TRANSPORT, 1200, 19),
                    CategoryStat(ExpenseCategory.SHOPPING, 0, 0),
                    CategoryStat(ExpenseCategory.DAILY, 0, 0),
                    CategoryStat(ExpenseCategory.OTHER, 0, 0),
                ),
                entries = listOf(
                    ExpenseEntry(
                        id = 1L,
                        amountCents = 5000,
                        category = ExpenseCategory.FOOD,
                        note = "午饭",
                        expenseDate = 20761L,
                        createdAt = 1793764800000L,
                        updatedAt = 1793764800000L,
                    ),
                    ExpenseEntry(
                        id = 2L,
                        amountCents = 1200,
                        category = ExpenseCategory.TRANSPORT,
                        note = "",
                        expenseDate = 20761L,
                        createdAt = 1793720000000L,
                        updatedAt = 1793720000000L,
                    ),
                ),
                isLoading = false,
            ),
            onOpenAdd = {},
            onOpenDetail = {},
            onMonthChange = {},
        )
    }
}

@Preview(showBackground = true, name = "首页 · 空数据")
@Composable
private fun HomeScreenEmptyPreview() {
    MaterialTheme {
        HomeScreen(
            state = HomeUiState(
                year = 2026,
                month = 11,
                monthTitle = "2026 年 11 月",
                totalCents = 0,
                categoryStats = listOf(
                    CategoryStat(ExpenseCategory.FOOD, 0, 0),
                    CategoryStat(ExpenseCategory.TRANSPORT, 0, 0),
                    CategoryStat(ExpenseCategory.SHOPPING, 0, 0),
                    CategoryStat(ExpenseCategory.DAILY, 0, 0),
                    CategoryStat(ExpenseCategory.OTHER, 0, 0),
                ),
                entries = emptyList(),
                isLoading = false,
            ),
            onOpenAdd = {},
            onOpenDetail = {},
            onMonthChange = {},
        )
    }
}
