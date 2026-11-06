package com.linjincheng.qingji.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.linjincheng.qingji.util.BudgetStatus
import com.linjincheng.qingji.util.MonthlyBudget
import com.linjincheng.qingji.util.MoneyUtils

/** Read-only budget card; actual spent comes from HomeUiState.totalCents.
 * It reflects selected months, respects Hide Amounts, and never modifies data.
 */
@Composable
fun MonthlyBudgetCard(
    budgetCents: Long?,
    spentCents: Long,
    monthTitle: String,
    amountsHidden: Boolean,
) {
    val state = MonthlyBudget.calculate(budgetCents, spentCents) ?: return
    val statusColor = when (state.status) {
        BudgetStatus.EXCEEDED, BudgetStatus.REACHED -> MaterialTheme.colorScheme.error
        BudgetStatus.APPROACHING -> MaterialTheme.colorScheme.tertiary
        BudgetStatus.WITHIN -> MaterialTheme.colorScheme.primary
    }
    val message = when (state.status) {
        BudgetStatus.WITHIN -> "预算充足，可继续关注日常支出"
        BudgetStatus.APPROACHING -> "本月预算已使用超过 80%，建议留意后续开销"
        BudgetStatus.REACHED -> "本月预算刚好用完"
        BudgetStatus.EXCEEDED -> "本月已超出预算"
    }
    val show = { cents: Long ->
        if (amountsHidden) "••••" else "¥" + MoneyUtils.formatCentsGrouped(cents)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .36f)),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "月度预算",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = monthTitle + " · 每月重复目标",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = (state.progress * 100f).toInt().coerceAtMost(100).toString() + "%",
                    style = MaterialTheme.typography.titleLarge,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("预算上限", style = MaterialTheme.typography.labelSmall)
                    Text(show(state.budgetCents), style = MaterialTheme.typography.titleSmall)
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("实际支出", style = MaterialTheme.typography.labelSmall)
                    Text(show(state.spentCents), style = MaterialTheme.typography.titleSmall)
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (state.exceededCents > 0) "已超支" else "剩余额度",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        show(if (state.exceededCents > 0) state.exceededCents else state.remainingCents),
                        style = MaterialTheme.typography.titleSmall,
                        color = statusColor,
                    )
                }
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
            )
        }
    }
}
