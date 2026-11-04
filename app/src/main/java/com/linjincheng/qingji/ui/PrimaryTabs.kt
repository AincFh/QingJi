package com.linjincheng.qingji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.linjincheng.qingji.R

enum class MainTab(val label: String) {
    HOME("首页"),
    RECORDS("账单"),
    INSIGHTS("统计"),
    SETTINGS("设置"),
}

/** 四个目的地加一个不参与选中状态的中央全局记账操作。 */
@Composable
fun MainNavigationBar(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    onQuickAdd: () -> Unit,
) {
    val separatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    NavigationBar(
        modifier = Modifier.drawWithContent {
            drawContent()
            drawLine(
                color = separatorColor,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx(),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        val tabs = listOf(MainTab.HOME, MainTab.RECORDS, MainTab.INSIGHTS, MainTab.SETTINGS)
        val tabColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = Color.Transparent,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        tabs.forEachIndexed { index, tab ->
            if (index == 2) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 80.dp)
                        .clickable(role = Role.Button, onClick = onQuickAdd)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "记一笔"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Text(
                        text = "记一笔",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                icon = {
                    when (tab) {
                        MainTab.HOME -> Icon(Icons.Filled.Home, contentDescription = null)
                        MainTab.RECORDS -> Icon(
                            painter = painterResource(R.drawable.ic_tab_records),
                            contentDescription = null,
                        )
                        MainTab.INSIGHTS -> Icon(
                            painter = painterResource(R.drawable.ic_tab_insights),
                            contentDescription = null,
                        )
                        MainTab.SETTINGS -> Icon(Icons.Filled.Settings, contentDescription = null)
                    }
                },
                label = { Text(tab.label) },
                alwaysShowLabel = true,
                colors = tabColors,
            )
        }
    }
}
