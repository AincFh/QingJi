package com.linjincheng.qingji.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    // Keep the display scale expressive without letting section headers dominate the screen.
    displayLarge = TextStyle(
        fontSize = 40.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.45).sp,
    ),
    displayMedium = TextStyle(
        fontSize = 36.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Medium,
    ),
    displaySmall = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Medium,
    ),
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Medium,
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Normal,
    ),
    headlineSmall = TextStyle(
        fontSize = 23.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleLarge = TextStyle(
        fontSize = 21.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
    ),
)
