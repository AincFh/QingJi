package com.linjincheng.qingji.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.linjincheng.qingji.util.MoneyUtils

/**
 * 统一金额文案：人民币符号 + MoneyUtils.formatCents（单位分 → "62.00"）。
 * 全应用金额展示一律经过这里，保证「¥ + formatCents」口径一致（契约 §7）。
 */
fun yuanText(cents: Long): String = "¥" + MoneyUtils.formatCents(cents)

/** 金额文本组件：默认读 MaterialTheme 正文样式，可用 fontSize/fontWeight/color 覆盖。 */
@Composable
fun AmountText(
    cents: Long,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
) {
    Text(
        text = yuanText(cents),
        modifier = modifier,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
    )
}

/**
 * 分类占比进度条：percent 取 0..100（契约 §6，由 C2 四舍五入给出）。
 * 占比为 0 时进度条仍然可见、进度为 0，不隐藏（任务书 §6 第二块）。
 */
@Composable
fun StatBar(percent: Int, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { percent.coerceIn(0, 100) / 100f },
        modifier = modifier,
    )
}

/** 分区标题（首页「分类统计」「消费流水」等）。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

/** 空态/引导提示：整行居中、次级色。 */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
