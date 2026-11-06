package com.linjincheng.qingji

import com.linjincheng.qingji.data.ExpenseCategory
import com.linjincheng.qingji.data.ExpenseEntry
import com.linjincheng.qingji.data.ExpenseRepositoryValidation
import com.linjincheng.qingji.data.MonthlyBudget
import com.linjincheng.qingji.util.DateUtils
import com.linjincheng.qingji.util.MoneyUtils
import java.time.LocalDate
import org.junit.Assert.assertThrows
import org.junit.Test

class ExpenseRepositoryValidationTest {
    @Test
    fun expenseAmountAndDateMustMatchBackupLimits() {
        ExpenseRepositoryValidation.validateExpense(expense(amountCents = MoneyUtils.MAX_CENTS))
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseRepositoryValidation.validateExpense(expense(amountCents = MoneyUtils.MAX_CENTS + 1L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseRepositoryValidation.validateExpense(expense(amountCents = 0L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseRepositoryValidation.validateExpense(
                expense(expenseDate = LocalDate.of(10_000, 1, 1).toEpochDay()),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseRepositoryValidation.validateExpense(expense(expenseDate = Long.MAX_VALUE))
        }
    }

    @Test
    fun budgetAmountAndTimestampsMustMatchBackupLimits() {
        ExpenseRepositoryValidation.validateBudget(MonthlyBudget(2026, 11, 0L, 0L, 0L))
        ExpenseRepositoryValidation.validateBudget(
            MonthlyBudget(2026, 11, MoneyUtils.MAX_CENTS, 0L, 0L),
        )
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseRepositoryValidation.validateBudget(
                MonthlyBudget(2026, 11, MoneyUtils.MAX_CENTS + 1L, 1L, 1L),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseRepositoryValidation.validateBudget(MonthlyBudget(2026, 11, 0L, -1L, 1L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseRepositoryValidation.validateBudget(MonthlyBudget(2026, 11, 0L, 1L, -1L))
        }
    }

    private fun expense(
        amountCents: Long = 1L,
        expenseDate: Long = DateUtils.todayEpochDay(),
    ) = ExpenseEntry(
        amountCents = amountCents,
        category = ExpenseCategory.FOOD,
        expenseDate = expenseDate,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
