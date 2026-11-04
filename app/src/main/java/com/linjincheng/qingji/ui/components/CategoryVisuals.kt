package com.linjincheng.qingji.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.linjincheng.qingji.R
import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.ui.theme.DarkCategoryDaily
import com.linjincheng.qingji.ui.theme.DarkCategoryFood
import com.linjincheng.qingji.ui.theme.DarkCategoryOther
import com.linjincheng.qingji.ui.theme.DarkCategoryShopping
import com.linjincheng.qingji.ui.theme.DarkCategoryTransport
import com.linjincheng.qingji.ui.theme.LightCategoryDaily
import com.linjincheng.qingji.ui.theme.LightCategoryFood
import com.linjincheng.qingji.ui.theme.LightCategoryOther
import com.linjincheng.qingji.ui.theme.LightCategoryShopping
import com.linjincheng.qingji.ui.theme.LightCategoryTransport

@Composable
fun CategoryIcon(
    category: ExpenseCategory,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    contentDescription: String? = category.label,
) {
    when (category) {
        ExpenseCategory.FOOD -> Icon(
            painter = painterResource(R.drawable.ic_category_restaurant),
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            tint = categoryAccent(category),
        )

        ExpenseCategory.TRANSPORT -> Icon(
            painter = painterResource(R.drawable.ic_category_bus),
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            tint = categoryAccent(category),
        )

        ExpenseCategory.SHOPPING,
        ExpenseCategory.DAILY,
        ExpenseCategory.OTHER,
        -> Icon(
            imageVector = when (category) {
                ExpenseCategory.SHOPPING -> Icons.Filled.ShoppingCart
                ExpenseCategory.DAILY -> Icons.Filled.Home
                else -> Icons.Filled.MoreVert
            },
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            tint = categoryAccent(category),
        )
    }
}

@Composable
fun CategoryIconBadge(
    category: ExpenseCategory,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accent = categoryAccent(category)

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = if (isDark) 0.20f else 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        CategoryIcon(category = category, size = size * 0.5f, contentDescription = null)
    }
}

@Composable
fun categoryAccent(category: ExpenseCategory): Color {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (category) {
        ExpenseCategory.FOOD -> if (isDark) DarkCategoryFood else LightCategoryFood
        ExpenseCategory.TRANSPORT -> if (isDark) DarkCategoryTransport else LightCategoryTransport
        ExpenseCategory.SHOPPING -> if (isDark) DarkCategoryShopping else LightCategoryShopping
        ExpenseCategory.DAILY -> if (isDark) DarkCategoryDaily else LightCategoryDaily
        ExpenseCategory.OTHER -> if (isDark) DarkCategoryOther else LightCategoryOther
    }
}
