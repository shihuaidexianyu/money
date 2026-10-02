package com.shihuaidexianyu.money.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect
import com.shihuaidexianyu.money.ui.accounts.AccountsScreen
import com.shihuaidexianyu.money.ui.accounts.AccountsViewModel
import com.shihuaidexianyu.money.ui.history.HistoryRecordKind
import com.shihuaidexianyu.money.ui.history.HistoryScreen
import com.shihuaidexianyu.money.ui.history.HistoryViewModel
import com.shihuaidexianyu.money.ui.settings.SettingsScreen
import com.shihuaidexianyu.money.ui.reminder.NotificationPermissionUiState

internal fun NavGraphBuilder.addTopLevelGraph(
    navController: NavHostController,
    container: MoneyAppContainer,
    onBiometricLockChange: (Boolean) -> Unit,
    onHistoryScrolledChange: (Boolean) -> Unit = {},
) {
    // A retained Home route from an older task redirects to the new root without losing data.
    composable(MoneyDestination.Home.route) { RetiredFeatureDestination(navController) }

    composable(MoneyDestination.History.route) {
        HistoryScreenHost(navController = navController, container = container, onScrolledChange = onHistoryScrolledChange)
    }

    // Drill-down from account detail "查看全部": the same History surface with the account
    // scope fixed by the route — back returns to the detail page, and the tab's persisted
    // filters are neither read nor written here.
    composable(
        route = MoneyDestination.AccountHistoryRoute,
        arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
    ) { entry ->
        val accountId = entry.arguments?.getLong("accountId") ?: return@composable
        HistoryScreenHost(
            navController = navController,
            container = container,
            lockedAccountId = accountId,
            onBack = { navController.popBackStack() },
        )
    }

    composable(MoneyDestination.Accounts.route) {
        val viewModel = viewModel<AccountsViewModel>(
            factory = moneyViewModelFactory {
                AccountsViewModel(
                    accountReminderSettingsRepository = container.accountReminderSettingsRepository,
                    accountRepository = container.accountRepository,
                    portableSettingsRepository = container.portableSettingsRepository,
                    transactionRepository = container.transactionRepository,
                    calculateAccountBalancesUseCase = container.calculateAccountBalancesUseCase,
                )
            },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val privacyScope = rememberCoroutineScope()
        val privacySnackbar = LocalRootSnackbarDispatcher.current
        val privacyError = stringResource(R.string.amount_visibility_update_failed)
        AccountsScreen(
                state = state,
                modifier = Modifier.padding(LocalTopLevelContentPadding.current)
                    .consumeWindowInsets(LocalTopLevelContentPadding.current),
                onCreateAccount = { navController.navigate(MoneyDestination.CreateAccountRoute) },
                onAccountClick = { navController.navigate(MoneyDestination.accountDetailRoute(it)) },
                onToggleClosedVisibility = viewModel::toggleClosedVisibility,
                onOpenSettings = { navController.navigate(MoneyDestination.Settings.route) },
                onToggleAmountVisibility = {
                    privacyScope.launch {
                        try {
                            val preferences = container.devicePreferencesRepository.query()
                            container.devicePreferencesRepository.updateHideInAppAmounts(!preferences.hideInAppAmounts)
                        } catch (error: kotlinx.coroutines.CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            privacySnackbar?.dispatch(rootSnackbarEffect(privacyError))
                        }
                    }
                },
                onRetry = viewModel::retry,
            )
    }

    composable(MoneyDestination.Settings.route) {
        val viewModel = rememberSettingsViewModel(
            container = container,
        )
        // SettingsViewModel is Activity-scoped; recompute the current canonical content hash on
        // every Settings entry so rollback eligibility cannot survive an intervening mutation.
        LifecycleResumeEffect(viewModel) {
            viewModel.refreshImportHistory()
            onPauseOrDispose { }
        }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        SettingsScreen(
            state = state,
            effectFlow = viewModel.effectFlow,
            onBack = { navController.popBackStack() },
            onThemeModeChange = viewModel::updateThemeMode,
            onAmountColorModeChange = viewModel::updateAmountColorMode,
            onCurrencySymbolChange = viewModel::updateCurrencySymbol,
            onBiometricLockChange = onBiometricLockChange,
            onRelockDelayChange = viewModel::updateRelockDelay,
            onHideNotificationAmountsChange = viewModel::updateHideNotificationAmounts,
            onHideRecentTasksChange = viewModel::updateHideRecentTasks,
            onHideInAppAmountsChange = viewModel::updateHideInAppAmounts,
            notificationPermissionState = NotificationPermissionUiState.NotRequested,
            onRequestNotificationPermission = {},
            onOpenNotificationSettings = {},
            onManageReminders = {},
            onManageAccountReminderConfigs = {},
            onOpenLanAi = {},
            onExportData = viewModel::exportData,
            onImportData = viewModel::previewImport,
            onConfirmImport = viewModel::confirmImport,
            onRollbackImport = viewModel::rollbackImport,
            onShareBackup = viewModel::shareData,
            onSaveExportToDocument = viewModel::saveExportToDocument,
            onResumeExport = viewModel::resumeExport,
            onDismissImportPreview = viewModel::dismissImportPreview,
            onRefreshImportHistory = viewModel::refreshImportHistory,
            onExportSafetySnapshot = viewModel::exportSafetySnapshot,
        )
    }

    composable(MoneyDestination.LanMcpRoute) { RetiredFeatureDestination(navController) }
}

/**
 * Shared wiring for the History tab and the account drill-down
 * (`history/account/{accountId}`). Both surfaces run the full History screen — search, filter
 * sheets and record editing — the drill-down just pins [lockedAccountId] into the
 * view model so the account scope, title, and back affordance come from the route.
 */
@Composable
private fun HistoryScreenHost(
    navController: NavHostController,
    container: MoneyAppContainer,
    lockedAccountId: Long? = null,
    onBack: (() -> Unit)? = null,
    onScrolledChange: (Boolean) -> Unit = {},
) {
    val viewModel = viewModel<HistoryViewModel>(
        key = lockedAccountId?.let { "account_history_$it" },
        factory = moneyViewModelFactory {
            HistoryViewModel(
                accountRepository = container.accountRepository,
                transactionRepository = container.transactionRepository,
                portableSettingsRepository = container.portableSettingsRepository,
                devicePreferencesRepository = container.devicePreferencesRepository,
                lockedAccountId = lockedAccountId,
                simplified = true,
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rootSnackbarDispatcher = LocalRootSnackbarDispatcher.current
    val closedAccountReadOnlyMessage = stringResource(R.string.account_closed_readonly_description)
    HistoryScreen(
        state = state,
        modifier = if (lockedAccountId == null) {
            Modifier.padding(LocalTopLevelContentPadding.current)
                .consumeWindowInsets(LocalTopLevelContentPadding.current)
        } else Modifier,
        onKeywordChange = viewModel::updateKeyword,
        onExcludeKeywordChange = viewModel::updateExcludeKeyword,
        onRecordTypesChange = viewModel::updateRecordTypes,
        onBusinessSemanticChange = viewModel::updateBusinessSemantic,
        onAccountChange = viewModel::updateAccount,
        onDateRangeChange = viewModel::updateDateRange,
        onMinAmountChange = viewModel::updateMinAmount,
        onMaxAmountChange = viewModel::updateMaxAmount,
        onAmountDirectionChange = viewModel::updateAmountDirectionFilter,
        onClearAllFilters = viewModel::clearFilters,
        onApplyFilters = viewModel::applyFilterDraft,
        onLoadMore = viewModel::loadMore,
        onRetryLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        onScrolledChange = onScrolledChange,
        lockedAccountId = lockedAccountId,
        onBack = onBack,
        // Quick-record from the drill-down pre-selects the scoped account; the tab keeps the
        // "ask me" default (0L).
        onRecordIncome = {
            navController.navigate(
                MoneyDestination.recordCashFlowRoute(
                    CashFlowDirection.INFLOW,
                    accountId = lockedAccountId ?: 0L,
                ),
            )
        },
        onRecordExpense = {
            navController.navigate(
                MoneyDestination.recordCashFlowRoute(
                    CashFlowDirection.OUTFLOW,
                    accountId = lockedAccountId ?: 0L,
                ),
            )
        },
        onRecordClick = { record ->
            if (!record.canMutate) {
                rootSnackbarDispatcher?.dispatch(rootSnackbarEffect(closedAccountReadOnlyMessage))
            } else {
                when (record.kind) {
                    HistoryRecordKind.CASH_FLOW -> navController.navigate(MoneyDestination.editCashFlowRoute(record.recordId))
                    HistoryRecordKind.TRANSFER -> navController.navigate(MoneyDestination.editTransferRoute(record.recordId))
                    HistoryRecordKind.BALANCE_UPDATE -> navController.navigate(MoneyDestination.balanceUpdateDetailRoute(record.recordId))
                    HistoryRecordKind.BALANCE_ADJUSTMENT -> navController.navigate(MoneyDestination.balanceAdjustmentDetailRoute(record.recordId))
                }
            }
        },
    )
}
