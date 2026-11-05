package com.linjincheng.qingji

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linjincheng.qingji.data.ExpensePreferences
import com.linjincheng.qingji.data.ExpenseBackupCodec
import com.linjincheng.qingji.data.QingJiPreferences
import com.linjincheng.qingji.data.ThemeMode
import com.linjincheng.qingji.ui.BackupScreen
import com.linjincheng.qingji.ui.BudgetScreen
import com.linjincheng.qingji.ui.CalendarDayUi
import com.linjincheng.qingji.ui.CalendarScreen
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
import com.linjincheng.qingji.viewmodel.BackupPickerAction
import com.linjincheng.qingji.viewmodel.Screen
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            val context = LocalContext.current
            val contentResolver = remember(context) { context.contentResolver }
            val appVersionName = remember(context) { readVersionName(context) }
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
            val homeState by viewModel.home.collectAsStateWithLifecycle()
            val formState by viewModel.form.collectAsStateWithLifecycle()
            val detailState by viewModel.detail.collectAsStateWithLifecycle()
            val calendarState by viewModel.calendar.collectAsStateWithLifecycle()
            val budgetState by viewModel.budget.collectAsStateWithLifecycle()
            val backupState by viewModel.backup.collectAsStateWithLifecycle()
            val backupPickerRequest by viewModel.backupPickerRequest.collectAsStateWithLifecycle()
            val homeSnackbarHostState = remember { SnackbarHostState() }
            val exportBackupLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                if (uri == null) {
                    viewModel.onBackupExportPickerCancelled()
                } else {
                    viewModel.exportBackup(appVersionName) { bytes ->
                        withContext(Dispatchers.IO) {
                            val output = contentResolver.openOutputStream(uri, "wt")
                                ?: error("系统无法打开所选备份文件进行写入")
                            output.use { stream ->
                                stream.write(bytes)
                                stream.flush()
                            }
                        }
                    }
                }
            }
            val importBackupLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri == null) {
                    viewModel.onBackupImportPickerCancelled()
                } else {
                    viewModel.onBackupFileSelected {
                        withContext(Dispatchers.IO) {
                            val input = contentResolver.openInputStream(uri)
                                ?: error("系统无法打开所选备份文件进行读取")
                            input.use { stream -> ExpenseBackupCodec.decode(stream) }
                        }
                    }
                }
            }
            LaunchedEffect(backupPickerRequest?.id) {
                val request = backupPickerRequest ?: return@LaunchedEffect
                if (viewModel.claimBackupPickerRequest(request.id) == null) return@LaunchedEffect
                when (request.action) {
                    BackupPickerAction.EXPORT -> {
                        val date = LocalDate.now()
                        exportBackupLauncher.launch(
                            "qingji_backup_${date.year}_${date.monthValue}_${date.dayOfMonth}.json",
                        )
                    }

                    BackupPickerAction.IMPORT -> importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                }
            }
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
                                        onOpenDateDetails = { epochDay ->
                                            viewModel.openDateDetails(epochDay)
                                            selectedTabIndex = MainTab.RECORDS.ordinal
                                        },
                                        onOpenCategoryDetails = { category ->
                                            viewModel.openCategoryDetails(category)
                                            selectedTabIndex = MainTab.RECORDS.ordinal
                                        },
                                        selectedTrendEpochDay = homeState.selectedTrendEpochDay,
                                        selectedCategory = homeState.selectedCategory,
                                        onTrendDateSelected = viewModel::selectTrendEpochDay,
                                        onSelectedCategoryChanged = viewModel::selectCategory,
                                    )

                                    MainTab.RECORDS -> RecordsScreen(
                                        state = homeState,
                                        onOpenAdd = { viewModel.openAdd(appPreferences.defaultCategory) },
                                        onOpenDetail = viewModel::openDetail,
                                        onSearchTextChange = viewModel::onSearchTextChange,
                                        onCategoryFilterChange = viewModel::onCategoryFilterChange,
                                        onMonthFilterChange = viewModel::onHistoryMonthFilterChange,
                                        onRetry = viewModel::retryHome,
                                        onOpenCalendar = viewModel::openCalendar,
                                        onOpenSettings = { selectedTabIndex = MainTab.SETTINGS.ordinal },
                                        amountsHidden = appPreferences.hideAmounts,
                                    )

                                    MainTab.INSIGHTS -> InsightsScreen(
                                        state = homeState,
                                        onMonthChange = viewModel::changeMonth,
                                        onRetry = viewModel::retryHome,
                                        onOpenSettings = { selectedTabIndex = MainTab.SETTINGS.ordinal },
                                        amountsHidden = appPreferences.hideAmounts,
                                        onOpenDateDetails = { epochDay ->
                                            viewModel.openDateDetails(epochDay)
                                            selectedTabIndex = MainTab.RECORDS.ordinal
                                        },
                                        onOpenCategoryDetails = { category ->
                                            viewModel.openCategoryDetails(category)
                                            selectedTabIndex = MainTab.RECORDS.ordinal
                                        },
                                        selectedTrendEpochDay = homeState.selectedTrendEpochDay,
                                        selectedCategory = homeState.selectedCategory,
                                        onTrendDateSelected = viewModel::selectTrendEpochDay,
                                        onSelectedCategoryChanged = viewModel::selectCategory,
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
                                        onOpenCalendar = viewModel::openCalendar,
                                        onOpenBudget = viewModel::openBudget,
                                        onExportBackup = viewModel::requestBackupExport,
                                        onRestoreBackup = viewModel::requestBackupImport,
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
                        onOpenCalendar = viewModel::openCalendar,
                        onOpenBudget = viewModel::openBudget,
                        onExportBackup = viewModel::requestBackupExport,
                        onRestoreBackup = viewModel::requestBackupImport,
                        onBack = viewModel::back,
                    )

                    is Screen.Calendar -> {
                        val calendarDays = remember(calendarState.dailyTotals) {
                            calendarState.dailyTotals.map { total ->
                                CalendarDayUi(
                                    epochDay = total.epochDay,
                                    totalCents = total.totalCents,
                                    transactionCount = total.transactionCount,
                                )
                            }
                        }
                        CalendarScreen(
                            month = calendarState.month,
                            days = calendarDays,
                            selectedEpochDay = calendarState.selectedEpochDay,
                            onMonthChange = viewModel::onCalendarMonthChange,
                            onDaySelected = viewModel::onCalendarDaySelected,
                            onOpenDayDetails = { epochDay ->
                                viewModel.openCalendarDayDetails(epochDay)
                                selectedTabIndex = MainTab.RECORDS.ordinal
                            },
                            amountsHidden = appPreferences.hideAmounts,
                            isLoading = calendarState.isLoading,
                            loadFailed = calendarState.loadFailed,
                            onRetry = viewModel::retryCalendarLoad,
                        )
                    }

                    is Screen.Budget -> BudgetScreen(
                        summary = budgetState.summary,
                        amountsHidden = appPreferences.hideAmounts,
                        isLoading = budgetState.isLoading,
                        loadFailed = budgetState.loadFailed,
                        onRetry = viewModel::retryBudgetLoad,
                        feedbackMessage = budgetState.feedbackMessage,
                        feedbackIsError = budgetState.feedbackIsError,
                        onDismissFeedback = viewModel::dismissBudgetFeedback,
                        onSaveBudget = viewModel::saveBudget,
                        onClearBudget = viewModel::clearBudget,
                        onMonthChange = viewModel::onBudgetMonthChange,
                        onViewExpenses = { year, month ->
                            viewModel.openBudgetExpenses(year, month)
                            selectedTabIndex = MainTab.RECORDS.ordinal
                        },
                        onBack = viewModel::back,
                    )

                    is Screen.Backup -> BackupScreen(
                        state = backupState,
                        onBack = viewModel::back,
                        onExport = viewModel::requestBackupExport,
                        onSelectFile = viewModel::requestBackupImport,
                        onConfirmImport = viewModel::confirmBackupImport,
                        onCancelImport = viewModel::cancelBackupImport,
                        onDismissFeedback = viewModel::dismissBackupFeedback,
                    )

                    is Screen.Form -> ExpenseFormScreen(
                        state = formState,
                        onAmountChange = viewModel::onAmountChange,
                        onCategorySelect = viewModel::onCategorySelect,
                        onDateChange = viewModel::onDateChange,
                        onNoteChange = viewModel::onNoteChange,
                        onSave = viewModel::saveExpense,
                        onBackRequested = viewModel::requestFormBack,
                        onDismissDiscard = viewModel::dismissDiscardDialog,
                        onConfirmDiscard = viewModel::confirmDiscardChanges,
                        onRetryEditLoad = viewModel::retryFormLoad,
                        recentCategories = homeState.recentCategories,
                        copyMode = formState.copyMode,
                    )

                    is Screen.Detail -> ExpenseDetailScreen(
                        state = detailState,
                        onEdit = viewModel::openEdit,
                        onDeleteClick = viewModel::onDeleteClick,
                        onDismissDeleteDialog = viewModel::onDismissDeleteDialog,
                        onConfirmDelete = viewModel::onConfirmDelete,
                        onBack = viewModel::back,
                        onRetryDetailLoad = viewModel::retryDetailLoad,
                        onRetryDelete = viewModel::onConfirmDelete,
                        onClearFeedback = viewModel::clearDetailFeedback,
                        amountsHidden = appPreferences.hideAmounts,
                        onCopy = viewModel::openCopy,
                    )
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun readVersionName(context: Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
