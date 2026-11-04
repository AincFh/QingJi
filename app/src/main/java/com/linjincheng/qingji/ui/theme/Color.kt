package com.linjincheng.qingji.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf

/** Deep persimmon for small button labels; the brighter tertiary remains for large data accents. */
internal val LightActionColor = Color(0xFFC45118)
internal val LightOnActionColor = Color(0xFFFFFFFF)
internal val DarkActionColor = Color(0xFFFFB181)
internal val DarkOnActionColor = Color(0xFF552006)

val LocalQingJiActionColor = staticCompositionLocalOf { LightActionColor }
val LocalQingJiOnActionColor = staticCompositionLocalOf { LightOnActionColor }

// QingJi's warm everyday identity: forest green structure, persimmon spending accent,
// and paper-white surfaces. Fixed values keep the product recognizable across devices.
internal val LightPrimary = Color(0xFF245B43)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFE2EBE3)
internal val LightOnPrimaryContainer = Color(0xFF203C2C)
internal val LightInversePrimary = Color(0xFFB5D1B8)

internal val LightSecondary = Color(0xFF666B58)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFE8E8D9)
internal val LightOnSecondaryContainer = Color(0xFF24291F)

internal val LightTertiary = Color(0xFFDE601C)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFFBE6D5)
internal val LightOnTertiaryContainer = Color(0xFF4B2108)

internal val LightBackground = Color(0xFFF8F4E9)
internal val LightOnBackground = Color(0xFF20231F)
internal val LightSurface = Color(0xFFFFFDF7)
internal val LightOnSurface = Color(0xFF20231F)
internal val LightSurfaceVariant = Color(0xFFEEE9DE)
internal val LightOnSurfaceVariant = Color(0xFF5B625B)
internal val LightOutline = Color(0xFF777D73)
internal val LightOutlineVariant = Color(0xFFD3D1C8)
internal val LightInverseSurface = Color(0xFF303630)
internal val LightInverseOnSurface = Color(0xFFF2F1E9)
internal val LightError = Color(0xFFB3261E)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFFFDAD6)
internal val LightOnErrorContainer = Color(0xFF410002)

// Fixed semantic category accents shared by home, form, and detail.
internal val LightCategoryFood = Color(0xFFB75D38)
internal val LightCategoryTransport = Color(0xFF44798A)
internal val LightCategoryShopping = Color(0xFF987936)
internal val LightCategoryDaily = Color(0xFF53775B)
internal val LightCategoryOther = Color(0xFF6B7269)

internal val DarkPrimary = Color(0xFFB6D2B9)
internal val DarkOnPrimary = Color(0xFF193A26)
internal val DarkPrimaryContainer = Color(0xFF315A40)
internal val DarkOnPrimaryContainer = Color(0xFFDDEBDD)
internal val DarkInversePrimary = Color(0xFF496F52)

internal val DarkSecondary = Color(0xFFC6C9C1)
internal val DarkOnSecondary = Color(0xFF292B28)
internal val DarkSecondaryContainer = Color(0xFF393B38)
internal val DarkOnSecondaryContainer = Color(0xFFE3E5E0)

internal val DarkTertiary = Color(0xFFFFB181)
internal val DarkOnTertiary = Color(0xFF552006)
internal val DarkTertiaryContainer = Color(0xFF783E1B)
internal val DarkOnTertiaryContainer = Color(0xFFFFDCC8)

internal val DarkBackground = Color(0xFF171817)
internal val DarkOnBackground = Color(0xFFE8E8E6)
internal val DarkSurface = Color(0xFF202120)
internal val DarkOnSurface = Color(0xFFE8E8E6)
internal val DarkSurfaceVariant = Color(0xFF2C2D2B)
internal val DarkOnSurfaceVariant = Color(0xFFC2C4BF)
internal val DarkOutline = Color(0xFF868983)
internal val DarkOutlineVariant = Color(0xFF3F403E)
internal val DarkInverseSurface = Color(0xFFE8E8E6)
internal val DarkInverseOnSurface = Color(0xFF292A28)
internal val DarkError = Color(0xFFFFB4AB)
internal val DarkOnError = Color(0xFF690005)
internal val DarkErrorContainer = Color(0xFF93000A)
internal val DarkOnErrorContainer = Color(0xFFFFDAD6)

internal val DarkCategoryFood = Color(0xFFFFAE8C)
internal val DarkCategoryTransport = Color(0xFF9FC9D7)
internal val DarkCategoryShopping = Color(0xFFD9C27E)
internal val DarkCategoryDaily = Color(0xFFA9CEA9)
internal val DarkCategoryOther = Color(0xFFC2C9C0)
