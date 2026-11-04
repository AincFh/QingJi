package com.linjincheng.qingji.data

import android.content.Context

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

data class QingJiPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hideAmounts: Boolean = false,
    val defaultCategory: ExpenseCategory = ExpenseCategory.FOOD,
)

/** App-level preferences use Android SharedPreferences; no additional storage dependency is needed. */
class ExpensePreferences(context: Context) {
    private val store = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    fun read(): QingJiPreferences = QingJiPreferences(
        themeMode = ThemeMode.entries.firstOrNull {
            it.name == store.getString(KEY_THEME, ThemeMode.SYSTEM.name)
        } ?: ThemeMode.SYSTEM,
        hideAmounts = store.getBoolean(KEY_HIDE_AMOUNTS, false),
        defaultCategory = ExpenseCategory.fromCode(
            store.getString(KEY_DEFAULT_CATEGORY, ExpenseCategory.FOOD.code) ?: ExpenseCategory.FOOD.code,
        ),
    )

    fun write(preferences: QingJiPreferences) {
        store.edit()
            .putString(KEY_THEME, preferences.themeMode.name)
            .putBoolean(KEY_HIDE_AMOUNTS, preferences.hideAmounts)
            .putString(KEY_DEFAULT_CATEGORY, preferences.defaultCategory.code)
            .apply()
    }

    private companion object {
        const val PREFERENCES_FILE = "qingji_preferences"
        const val KEY_THEME = "theme_mode"
        const val KEY_HIDE_AMOUNTS = "hide_amounts"
        const val KEY_DEFAULT_CATEGORY = "default_category"
    }
}
