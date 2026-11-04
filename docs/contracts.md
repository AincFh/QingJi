# QingJi v5 architecture and data contracts

This file records the interfaces currently present in the working tree. It is an integration boundary, not evidence that every screen or acceptance check is complete.

## Product and navigation

- Runtime: native Android, Kotlin, Jetpack Compose, SQLite. Do not replace the existing project or database.
- Main navigation is owned by `MainActivity.kt` and `PrimaryTabs.kt`: four destinations (`HOME`, `RECORDS`, `INSIGHTS`, `SETTINGS`) plus a center global add action. Add and detail remain secondary screens.
- `ExpenseViewModel.Screen` is the navigation state. `MainActivity` is the only owner allowed to change root routing or bottom navigation.
- The selected UI baseline uses light background `#F8F4E9`, deep pine `#245B43`, and bright persimmon data accent `#DE601C`. Small button text uses a deeper persimmon action token for contrast; components adapt to the dedicated dark palette.
- Income, budgets, CSV export, cloud sync, accounts, and AI are out of current scope unless backed by a real model, persistence, and tests.

## Data model and persistence

- `ExpenseEntry.amountCents: Long` is integer cents; `expenseDate: Long` is `LocalDate.toEpochDay()`.
- Expense categories are `ExpenseCategory` (`FOOD`, `TRANSPORT`, `SHOPPING`, `DAILY`, `OTHER`).
- `ExpenseRepository` exposes insert, update, delete, by-id lookup, all-history lookup, month lookup, monthly total, and monthly category totals. All methods are `suspend`; the SQLite implementation switches to `Dispatchers.IO`.
- `getAllExpenses()` sorts by expense date descending and creation time descending. It is the source for all-history search/filtering.
- `ExpensePreferences` stores theme mode, global amount visibility, and default category in local Android `SharedPreferences`. Transaction data stays in SQLite.
- Android Auto Backup is disabled, and explicit XML rules exclude app-private data from cloud backup and device transfer (Android 12+) and legacy backup flows. Confirm target-device behavior before promising manufacturer-specific migration behavior.

## ViewModel and page state

- `HomeUiState` is the shared read model for Home, Ledger, and Analytics. It includes the selected year/month/title, total and count, category stats, month entries, recent entries, all-history mode, comparison totals/label, seven daily trend points, search text/category filter, loading/error/empty flags, and feedback.
- `CategoryStat.percent` is an integer from 0 through 100; `totalCents` remains exact cents.
- `ExpenseViewModel.onHistoryTabSelected(true)` loads the ledger view from all history. Search and one category filter are held in ViewModel state and applied together. Both persist while the detail screen is opened and closed.
- `changeMonth(delta)` updates the selected month and refreshes month totals, categories, recent entries, and comparison values from SQLite.
- Current month comparison is month-to-date versus the previous month through the same day (clamped to that month's last day); historical months compare full adjacent months. The label is carried in state.
- The ViewModel provides seven-day and 30-day trend series. Both end on today for the current month or on the selected historical month's last day. The all-history ledger exposes a persistent month filter; search, category, and month are combined with AND.
- Existing callbacks used by `MainActivity`:
  - Home: `onOpenDetail`, `onMonthChange`, `onOpenAllRecords`, `onRetryHome`, `onClearFeedback`, `onOpenSettings`, `amountsHidden`.
  - Ledger: `onOpenAdd`, `onOpenDetail`, `onSearchTextChange`, `onCategoryFilterChange`, `onMonthFilterChange`, `onRetry`, `onOpenSettings`, `amountsHidden`.
  - Analytics: `onMonthChange`, `onRetry`, `onOpenSettings`, `amountsHidden`; may select the `dailyTrend` or `dailyTrend30` series.
  - Settings: `preferences`, theme/visibility/default-category change callbacks, `onBack`, and `showBackButton` (tab integration).

## Implementation boundaries

- `MainActivity.kt` and `PrimaryTabs.kt` own root routing and bottom navigation. Screen composables receive state and callbacks from the integration layer.
- Do not add fake values or controls that do not call a real callback/API. Keep statistics derived from repository data.
- Preserve the existing database and user records when changing UI or navigation.
- Keep Android backup disabled as configured in the manifest and extraction rules; do not claim vendor-specific device-transfer behavior without device verification.
