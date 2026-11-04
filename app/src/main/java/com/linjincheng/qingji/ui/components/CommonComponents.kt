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
import com.linjincheng.qingji.ui.theme.QingJiSpacing
import com.linjincheng.qingji.util.MoneyUtils

/**
 * 统一金额文案：人民币符号 + 带千分位的展示格式（单位分 → "1,234.00"）。
 * 输入表单和解析仍使用不带千分位的规范金额文本。
 */
fun yuanText(cents: Long): String = "¥" + MoneyUtils.formatCentsGrouped(cents)

fun visibleYuanText(cents: Long, hidden: Boolean): String =
    if (hidden) "¥••••" else yuanText(cents)

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
 * Category percentage bar. Percent is clamped to 0..100; a zero value remains visible with no fill.
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
            .padding(vertical = QingJiSpacing.xxl),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
