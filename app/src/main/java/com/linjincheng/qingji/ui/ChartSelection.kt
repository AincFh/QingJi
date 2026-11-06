package com.linjincheng.qingji.ui

import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.util.DailyExpenseTotal

/** Keep a date selection when it is visible; otherwise use the newest day in the current chart window. */
internal fun resolveTrendSelection(
    days: List<DailyExpenseTotal>,
    requestedEpochDay: Long?,
): Long? = days.firstOrNull { it.epochDay == requestedEpochDay }?.epochDay ?: days.lastOrNull()?.epochDay

/** Keep a category selection when it is available; otherwise use the first visible category. */
internal fun resolveCategorySelection(
    availableCategories: List<ExpenseCategory>,
    requestedCategory: ExpenseCategory?,
): ExpenseCategory? =
    requestedCategory?.takeIf(availableCategories::contains) ?: availableCategories.firstOrNull()
