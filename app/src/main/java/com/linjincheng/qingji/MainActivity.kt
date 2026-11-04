package com.linjincheng.qingji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linjincheng.qingji.ui.ExpenseDetailScreen
import com.linjincheng.qingji.ui.ExpenseFormScreen
import com.linjincheng.qingji.ui.HomeScreen
import com.linjincheng.qingji.ui.theme.QingJiTheme
import com.linjincheng.qingji.viewmodel.ExpenseViewModel
import com.linjincheng.qingji.viewmodel.Screen

/**
 * 单 Activity 入口（契约 §0/§8）：导航即状态，按 ViewModel 的 Screen 三态装配三屏。
 * 集成版由主对话写入，替换 C1 骨架桩。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = ExpenseViewModel.provideFactory(applicationContext)
        setContent {
            QingJiTheme {
                val viewModel: ExpenseViewModel = viewModel(factory = factory)
                val screen by viewModel.screen.collectAsStateWithLifecycle()
                // 系统返回键：非首页时走 ViewModel 返回栈；首页保持默认（退出应用）
                BackHandler(enabled = screen !is Screen.Home) { viewModel.back() }
                when (val current = screen) {
                    is Screen.Home -> HomeScreen(
                        state = viewModel.home.collectAsStateWithLifecycle().value,
                        onOpenAdd = viewModel::openAdd,
                        onOpenDetail = viewModel::openDetail,
                        onMonthChange = viewModel::changeMonth,
                    )

                    is Screen.Form -> ExpenseFormScreen(
                        state = viewModel.form.collectAsStateWithLifecycle().value,
                        onAmountChange = viewModel::onAmountChange,
                        onCategorySelect = viewModel::onCategorySelect,
                        onDateChange = viewModel::onDateChange,
                        onNoteChange = viewModel::onNoteChange,
                        onSave = viewModel::saveExpense,
                        onBack = viewModel::back,
                    )

                    is Screen.Detail -> ExpenseDetailScreen(
                        state = viewModel.detail.collectAsStateWithLifecycle().value,
                        onEdit = viewModel::openEdit,
                        onDeleteClick = viewModel::onDeleteClick,
                        onDismissDeleteDialog = viewModel::onDismissDeleteDialog,
                        onConfirmDelete = viewModel::onConfirmDelete,
                        onBack = viewModel::back,
                    )
                }
            }
        }
    }
}
