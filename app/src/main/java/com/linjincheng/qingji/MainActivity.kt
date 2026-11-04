package com.linjincheng.qingji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linjincheng.qingji.data.ExpensePreferences
import com.linjincheng.qingji.data.QingJiPreferences
import com.linjincheng.qingji.data.ThemeMode
import com.linjincheng.qingji.ui.ExpenseDetailScreen
import com.linjincheng.qingji.ui.ExpenseFormScreen
import com.linjincheng.qingji.ui.HomeScreen
import com.linjincheng.qingji.ui.InsightsScreen
import com.linjincheng.qingji.ui.MainNavigationBar
import com.linjincheng.qingji.ui.MainTab
import com.linjincheng.qingji.ui.RecordsScreen
import com.linjincheng.qingji.ui.SettingsScreen
import com.linjincheng.qingji.ui.theme.QingJiTheme
import com.linjincheng.qingji.viewmodel.ExpenseViewModel
import com.linjincheng.qingji.viewmodel.Screen

/**
 * Single-activity host for the tab shell and secondary form/detail routes.
 * ExpenseViewModel.Screen tracks the active secondary screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = ExpenseViewModel.provideFactory(applicationContext)
        val preferences = ExpensePreferences(applicationContext)
        setContent {
            val viewModel: ExpenseViewModel = viewModel(factory = factory)
            var appPreferences by remember { mutableStateOf(preferences.read()) }
            var selectedTabIndex by rememberSaveable { mutableIntStateOf(MainTab.HOME.ordinal) }
            val selectedTab = MainTab.entries.getOrElse(selectedTabIndex) { MainTab.HOME }
            val darkTheme = when (appPreferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val savePreferences: (QingJiPreferences) -> Unit = { next ->
                preferences.write(next)
                appPreferences = next
            }
            val screen by viewModel.screen.collectAsStateWithLifecycle()
            val homeSnackbarHostState = remember { SnackbarHostState() }
            LaunchedEffect(selectedTab) {
                viewModel.onHistoryTabSelected(showAllHistory = selectedTab == MainTab.RECORDS)
            }
            QingJiTheme(darkTheme = darkTheme) {
                DisposableEffect(darkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(
                            lightScrim = 0xFFF8F4E9.toInt(),
                            darkScrim = 0xFF151A16.toInt(),
                            detectDarkMode = { darkTheme },
                        ),
                        navigationBarStyle = SystemBarStyle.auto(
                            lightScrim = 0xFFF8F4E9.toInt(),
                            darkScrim = 0xFF151A16.toInt(),
                            detectDarkMode = { darkTheme },
                        ),
                    )
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                    onDispose { }
                }
                // 系统返回键：表单先检查未保存修改；详情沿用导航返回栈。
                BackHandler(enabled = screen !is Screen.Home || selectedTab != MainTab.HOME) {
                    when (screen) {
                        is Screen.Form -> viewModel.requestFormBack()
                        is Screen.Home -> {
                            selectedTabIndex = MainTab.HOME.ordinal
                            viewModel.onHistoryTabSelected(showAllHistory = false)
                        }
                        else -> viewModel.back()
                    }
                }
                when (val current = screen) {
                    is Screen.Home -> {
                        val homeState by viewModel.home.collectAsStateWithLifecycle()
                        LaunchedEffect(homeState.feedbackMessage) {
                            homeState.feedbackMessage?.let { message ->
                                homeSnackbarHostState.showSnackbar(message)
                                viewModel.clearHomeFeedback()
                            }
                        }
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            snackbarHost = { SnackbarHost(hostState = homeSnackbarHostState) },
                            bottomBar = {
                                MainNavigationBar(
                                    selectedTab = selectedTab,
                                    onSelectTab = { nextTab ->
                                        selectedTabIndex = nextTab.ordinal
                                    },
                                    onQuickAdd = { viewModel.openAdd(appPreferences.defaultCategory) },
                                )
                            },
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .consumeWindowInsets(innerPadding),
                            ) {
                                when (selectedTab) {
                                    MainTab.HOME -> HomeScreen(
                                        state = homeState,
                                        onOpenDetail = viewModel::openDetail,
                                        onMonthChange = viewModel::changeMonth,
                                        onOpenAllRecords = {
                                            selectedTabIndex = MainTab.RECORDS.ordinal
                                        },
                                        onRetryHome = viewModel::retryHome,
                                        amountsHidden = appPreferences.hideAmounts,
                                    )

                                    MainTab.RECORDS -> RecordsScreen(
                                        state = homeState,
                                        onOpenAdd = { viewModel.openAdd(appPreferences.defaultCategory) },
                                        onOpenDetail = viewModel::openDetail,
                                        onSearchTextChange = viewModel::onSearchTextChange,
                                        onCategoryFilterChange = viewModel::onCategoryFilterChange,
                                        onMonthFilterChange = viewModel::onHistoryMonthFilterChange,
                                        onRetry = viewModel::retryHome,
                                        onOpenSettings = { selectedTabIndex = MainTab.SETTINGS.ordinal },
                                        amountsHidden = appPreferences.hideAmounts,
                                    )

                                    MainTab.INSIGHTS -> InsightsScreen(
                                        state = homeState,
                                        onMonthChange = viewModel::changeMonth,
                                        onRetry = viewModel::retryHome,
                                        onOpenSettings = { selectedTabIndex = MainTab.SETTINGS.ordinal },
                                        amountsHidden = appPreferences.hideAmounts,
                                    )

                                    MainTab.SETTINGS -> SettingsScreen(
                                        preferences = appPreferences,
                                        onThemeModeChange = {
                                            savePreferences(appPreferences.copy(themeMode = it))
                                        },
                                        onHideAmountsChange = {
                                            savePreferences(appPreferences.copy(hideAmounts = it))
                                        },
                                        onDefaultCategoryChange = {
                                            savePreferences(appPreferences.copy(defaultCategory = it))
                                        },
                                        onBack = { selectedTabIndex = MainTab.HOME.ordinal },
                                        showBackButton = false,
                                    )
                                }
                            }
                        }
                    }

                    is Screen.Settings -> SettingsScreen(
                        preferences = appPreferences,
                        onThemeModeChange = { savePreferences(appPreferences.copy(themeMode = it)) },
                        onHideAmountsChange = { savePreferences(appPreferences.copy(hideAmounts = it)) },
                        onDefaultCategoryChange = {
                            savePreferences(appPreferences.copy(defaultCategory = it))
                        },
                        onBack = viewModel::back,
                    )

                    is Screen.Form -> ExpenseFormScreen(
                        state = viewModel.form.collectAsStateWithLifecycle().value,
                        onAmountChange = viewModel::onAmountChange,
                        onCategorySelect = viewModel::onCategorySelect,
                        onDateChange = viewModel::onDateChange,
                        onNoteChange = viewModel::onNoteChange,
                        onSave = viewModel::saveExpense,
                        onBackRequested = viewModel::requestFormBack,
                        onDismissDiscard = viewModel::dismissDiscardDialog,
                        onConfirmDiscard = viewModel::confirmDiscardChanges,
                        onRetryEditLoad = viewModel::retryEditLoad,
                    )

                    is Screen.Detail -> ExpenseDetailScreen(
                        state = viewModel.detail.collectAsStateWithLifecycle().value,
                        onEdit = viewModel::openEdit,
                        onDeleteClick = viewModel::onDeleteClick,
                        onDismissDeleteDialog = viewModel::onDismissDeleteDialog,
                        onConfirmDelete = viewModel::onConfirmDelete,
                        onBack = viewModel::back,
                        onRetryDetailLoad = viewModel::retryDetailLoad,
                        onRetryDelete = viewModel::onConfirmDelete,
                        onClearFeedback = viewModel::clearDetailFeedback,
                        amountsHidden = appPreferences.hideAmounts,
                    )
                }
            }
        }
    }
}
