package com.shihuaidexianyu.money.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.ui.accounts.AccountDetailScreen
import com.shihuaidexianyu.money.ui.accounts.AccountDetailViewModel
import com.shihuaidexianyu.money.ui.accounts.AccountsScreen
import com.shihuaidexianyu.money.ui.accounts.AccountsViewModel
import com.shihuaidexianyu.money.ui.accounts.CreateAccountScreen
import com.shihuaidexianyu.money.ui.accounts.CreateAccountViewModel
import com.shihuaidexianyu.money.ui.accounts.EditAccountScreen
import com.shihuaidexianyu.money.ui.accounts.EditAccountViewModel
import com.shihuaidexianyu.money.ui.accounts.ReorderAccountsScreen
import com.shihuaidexianyu.money.ui.accounts.ReorderAccountsViewModel
import com.shihuaidexianyu.money.ui.balance.BalanceUpdateResultScreen
import com.shihuaidexianyu.money.ui.balance.BalanceAdjustmentDetailScreen
import com.shihuaidexianyu.money.ui.balance.BalanceAdjustmentDetailViewModel
import com.shihuaidexianyu.money.ui.balance.BalanceUpdateDetailScreen
import com.shihuaidexianyu.money.ui.balance.BalanceUpdateDetailViewModel
import com.shihuaidexianyu.money.ui.balance.UpdateBalanceScreen
import com.shihuaidexianyu.money.ui.balance.UpdateBalanceViewModel
import com.shihuaidexianyu.money.ui.history.HistoryScreen
import com.shihuaidexianyu.money.ui.history.HistoryViewModel
import com.shihuaidexianyu.money.ui.history.HistoryRecordKind
import com.shihuaidexianyu.money.ui.home.HomeScreen
import com.shihuaidexianyu.money.ui.home.HomeViewModel
import com.shihuaidexianyu.money.ui.record.EditCashFlowScreen
import com.shihuaidexianyu.money.ui.record.EditCashFlowViewModel
import com.shihuaidexianyu.money.ui.record.EditTransferScreen
import com.shihuaidexianyu.money.ui.record.EditTransferViewModel
import com.shihuaidexianyu.money.ui.record.RecordCashFlowScreen
import com.shihuaidexianyu.money.ui.record.RecordCashFlowViewModel
import com.shihuaidexianyu.money.ui.record.RecordTransferScreen
import com.shihuaidexianyu.money.ui.record.RecordTransferViewModel
import com.shihuaidexianyu.money.ui.common.MoneyGradientBackground
import com.shihuaidexianyu.money.ui.settings.SettingsScreen
import com.shihuaidexianyu.money.ui.settings.SettingsViewModel

@Composable
fun MoneyNavGraph(container: MoneyAppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevelRoutes = MoneyDestination.topLevel.map { it.route }.toSet()

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (currentRoute in topLevelRoutes) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                        ) {
                            MoneyDestination.topLevel.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentRoute == destination.route,
                                    onClick = {
                                        navController.navigate(destination.route) {
                                            launchSingleTop = true
                                            restoreState = true
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                                    label = { Text(destination.label) },
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        MoneyGradientBackground {
            NavHost(
                navController = navController,
                startDestination = MoneyDestination.Home.route,
                modifier = Modifier.padding(innerPadding),
            ) {
            composable(MoneyDestination.Home.route) {
                val viewModel = viewModel<HomeViewModel>(
                    factory = viewModelFactory {
                        initializer {
                            HomeViewModel(
                                accountReminderSettingsRepository = container.accountReminderSettingsRepository,
                                accountRepository = container.accountRepository,
                                settingsRepository = container.settingsRepository,
                                transactionRepository = container.transactionRepository,
                                calculateCurrentBalanceUseCase = container.calculateCurrentBalanceUseCase,
                            )
                        }
                    },
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onStartCashFlow = { direction, accountId ->
                        navController.navigate(MoneyDestination.recordCashFlowRoute(direction, accountId))
                    },
                    onStartTransfer = { navController.navigate(MoneyDestination.recordTransferRoute()) },
                    onStartUpdateBalance = {
                        navController.navigate(MoneyDestination.updateBalanceRoute(it))
                    },
                )
            }
            composable(MoneyDestination.History.route) {
                val viewModel = viewModel<HistoryViewModel>(
                    factory = viewModelFactory {
                        initializer {
                            HistoryViewModel(
                                accountRepository = container.accountRepository,
                                transactionRepository = container.transactionRepository,
                                settingsRepository = container.settingsRepository,
                            )
                        }
                    },
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HistoryScreen(
                    state = state,
                    onKeywordChange = viewModel::updateKeyword,
                    onAccountChange = viewModel::updateAccount,
                    onDateRangeChange = viewModel::updateDateRange,
                    onMinAmountChange = viewModel::updateMinAmount,
                    onMaxAmountChange = viewModel::updateMaxAmount,
                    onRecordClick = { record ->
                        when (record.kind) {
                            HistoryRecordKind.CASH_FLOW -> navController.navigate(MoneyDestination.editCashFlowRoute(record.recordId))
                            HistoryRecordKind.TRANSFER -> navController.navigate(MoneyDestination.editTransferRoute(record.recordId))
                            HistoryRecordKind.BALANCE_UPDATE -> navController.navigate(MoneyDestination.balanceUpdateDetailRoute(record.recordId))
                            HistoryRecordKind.BALANCE_ADJUSTMENT -> navController.navigate(MoneyDestination.balanceAdjustmentDetailRoute(record.recordId))
                        }
                    },
                )
            }
            composable(MoneyDestination.Accounts.route) {
                val viewModel = viewModel<AccountsViewModel>(
                    factory = viewModelFactory {
                        initializer {
                            AccountsViewModel(
                                accountReminderSettingsRepository = container.accountReminderSettingsRepository,
                                accountRepository = container.accountRepository,
                                settingsRepository = container.settingsRepository,
                                transactionRepository = container.transactionRepository,
                                calculateCurrentBalanceUseCase = container.calculateCurrentBalanceUseCase,
                            )
                        }
                    },
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                AccountsScreen(
                    state = state,
                    onCreateAccount = { navController.navigate(MoneyDestination.CreateAccountRoute) },
                    onAccountClick = { navController.navigate(MoneyDestination.accountDetailRoute(it)) },
                    onReorderAccounts = { navController.navigate(MoneyDestination.ReorderAccountsRoute) },
                    onToggleArchiveVisibility = viewModel::toggleArchiveVisibility,
                )
            }
            composable(MoneyDestination.Settings.route) {
                val viewModel = viewModel<SettingsViewModel>(
                    factory = viewModelFactory {
                        initializer {
                            SettingsViewModel(
                                settingsRepository = container.settingsRepository,
                                exportJsonUseCase = container.exportJsonUseCase,
                            )
                        }
                    },
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SettingsScreen(
                    state = state,
                    onHomePeriodChange = viewModel::updateHomePeriod,
                    onWeekStartChange = viewModel::updateWeekStart,
                    onCurrencySymbolChange = viewModel::updateCurrencySymbol,
                    onAmountDisplayStyleChange = viewModel::updateAmountDisplayStyle,
                    onShowStaleMarkChange = viewModel::updateShowStaleMark,
                    onAccountSortModeChange = viewModel::updateAccountSortMode,
                    onExportJson = viewModel::exportJson,
                )
            }
            composable(MoneyDestination.CreateAccountRoute) {
                val viewModel = viewModel<CreateAccountViewModel>(
                    factory = viewModelFactory {
                        initializer {
                            CreateAccountViewModel(container.createAccountUseCase)
                        }
                    },
                )
                CreateAccountScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(MoneyDestination.ReorderAccountsRoute) {
                val viewModel = viewModel<ReorderAccountsViewModel>(
                    factory = viewModelFactory {
                        initializer {
                            ReorderAccountsViewModel(
                                accountRepository = container.accountRepository,
                                settingsRepository = container.settingsRepository,
                                updateAccountDisplayOrderUseCase = container.updateAccountDisplayOrderUseCase,
                            )
                        }
                    },
                )
                ReorderAccountsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = MoneyDestination.AccountDetailRoute,
                arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
            ) { entry ->
                val accountId = entry.arguments?.getLong("accountId") ?: return@composable
                val viewModel = viewModel<AccountDetailViewModel>(
                    key = "account_detail_$accountId",
                    factory = viewModelFactory {
                        initializer {
                            AccountDetailViewModel(
                                accountId = accountId,
                                accountReminderSettingsRepository = container.accountReminderSettingsRepository,
                                accountRepository = container.accountRepository,
                                settingsRepository = container.settingsRepository,
                                transactionRepository = container.transactionRepository,
                                calculateCurrentBalanceUseCase = container.calculateCurrentBalanceUseCase,
                            )
                        }
                    },
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                AccountDetailScreen(
                    state = state,
                    onManageAccount = {
                        navController.navigate(MoneyDestination.editAccountRoute(accountId))
                    },
                    onStartUpdateBalance = {
                        navController.navigate(MoneyDestination.updateBalanceRoute(accountId))
                    },
                )
            }
            composable(
                route = MoneyDestination.EditAccountRoute,
                arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
            ) { entry ->
                val accountId = entry.arguments?.getLong("accountId") ?: return@composable
                val viewModel = viewModel<EditAccountViewModel>(
                    key = "edit_account_$accountId",
                    factory = viewModelFactory {
                        initializer {
                            EditAccountViewModel(
                                accountId = accountId,
                                accountRepository = container.accountRepository,
                                accountReminderSettingsRepository = container.accountReminderSettingsRepository,
                                updateAccountUseCase = container.updateAccountUseCase,
                            )
                        }
                    },
                )
                EditAccountScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = MoneyDestination.RecordCashFlowRoute,
                arguments = listOf(
                    navArgument("direction") { type = NavType.StringType },
                    navArgument("accountId") { type = NavType.LongType },
                ),
            ) { entry ->
                val direction = CashFlowDirection.fromValue(entry.arguments?.getString("direction"))
                val accountId = entry.arguments?.getLong("accountId") ?: 0L
                val viewModel = viewModel<RecordCashFlowViewModel>(
                    key = "cash_flow_${direction.value}_$accountId",
                    factory = viewModelFactory {
                        initializer {
                            RecordCashFlowViewModel(
                                direction = direction,
                                initialAccountId = accountId.takeIf { it > 0 },
                                accountRepository = container.accountRepository,
                                createCashFlowRecordUseCase = container.createCashFlowRecordUseCase,
                            )
                        }
                    },
                )
                RecordCashFlowScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = MoneyDestination.RecordTransferRoute,
                arguments = listOf(navArgument("fromAccountId") { type = NavType.LongType }),
            ) { entry ->
                val fromAccountId = entry.arguments?.getLong("fromAccountId") ?: 0L
                val viewModel = viewModel<RecordTransferViewModel>(
                    key = "transfer_$fromAccountId",
                    factory = viewModelFactory {
                        initializer {
                            RecordTransferViewModel(
                                initialFromAccountId = fromAccountId.takeIf { it > 0 },
                                accountRepository = container.accountRepository,
                                createTransferRecordUseCase = container.createTransferRecordUseCase,
                            )
                        }
                    },
                )
                RecordTransferScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = MoneyDestination.EditCashFlowRoute,
                arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
            ) { entry ->
                val recordId = entry.arguments?.getLong("recordId") ?: return@composable
                val viewModel = viewModel<EditCashFlowViewModel>(
                    key = "edit_cash_$recordId",
                    factory = viewModelFactory {
                        initializer {
                            EditCashFlowViewModel(
                                recordId = recordId,
                                accountRepository = container.accountRepository,
                                transactionRepository = container.transactionRepository,
                                updateCashFlowRecordUseCase = container.updateCashFlowRecordUseCase,
                                deleteCashFlowRecordUseCase = container.deleteCashFlowRecordUseCase,
                            )
                        }
                    },
                )
                EditCashFlowScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(
                route = MoneyDestination.EditTransferRoute,
                arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
            ) { entry ->
                val recordId = entry.arguments?.getLong("recordId") ?: return@composable
                val viewModel = viewModel<EditTransferViewModel>(
                    key = "edit_transfer_$recordId",
                    factory = viewModelFactory {
                        initializer {
                            EditTransferViewModel(
                                recordId = recordId,
                                accountRepository = container.accountRepository,
                                transactionRepository = container.transactionRepository,
                                updateTransferRecordUseCase = container.updateTransferRecordUseCase,
                                deleteTransferRecordUseCase = container.deleteTransferRecordUseCase,
                            )
                        }
                    },
                )
                EditTransferScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(
                route = MoneyDestination.BalanceUpdateDetailRoute,
                arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
            ) { entry ->
                val recordId = entry.arguments?.getLong("recordId") ?: return@composable
                val viewModel = viewModel<BalanceUpdateDetailViewModel>(
                    key = "balance_update_detail_$recordId",
                    factory = viewModelFactory {
                        initializer {
                            BalanceUpdateDetailViewModel(
                                recordId = recordId,
                                accountRepository = container.accountRepository,
                                transactionRepository = container.transactionRepository,
                            )
                        }
                    },
                )
                val settingsViewModel = viewModel<SettingsViewModel>(
                    key = "settings_for_balance_update_detail",
                    factory = viewModelFactory {
                        initializer {
                            SettingsViewModel(
                                settingsRepository = container.settingsRepository,
                                exportJsonUseCase = container.exportJsonUseCase,
                            )
                        }
                    },
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                BalanceUpdateDetailScreen(
                    state = state,
                    settings = settingsState.settings,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = MoneyDestination.BalanceAdjustmentDetailRoute,
                arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
            ) { entry ->
                val recordId = entry.arguments?.getLong("recordId") ?: return@composable
                val viewModel = viewModel<BalanceAdjustmentDetailViewModel>(
                    key = "balance_adjustment_detail_$recordId",
                    factory = viewModelFactory {
                        initializer {
                            BalanceAdjustmentDetailViewModel(
                                recordId = recordId,
                                accountRepository = container.accountRepository,
                                transactionRepository = container.transactionRepository,
                            )
                        }
                    },
                )
                val settingsViewModel = viewModel<SettingsViewModel>(
                    key = "settings_for_balance_adjustment_detail",
                    factory = viewModelFactory {
                        initializer {
                            SettingsViewModel(
                                settingsRepository = container.settingsRepository,
                                exportJsonUseCase = container.exportJsonUseCase,
                            )
                        }
                    },
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                BalanceAdjustmentDetailScreen(
                    state = state,
                    settings = settingsState.settings,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = MoneyDestination.UpdateBalanceRoute,
                arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
            ) { entry ->
                val accountId = entry.arguments?.getLong("accountId") ?: 0L
                val viewModel = viewModel<UpdateBalanceViewModel>(
                    key = "update_balance_$accountId",
                    factory = viewModelFactory {
                        initializer {
                            UpdateBalanceViewModel(
                                initialAccountId = accountId.takeIf { it > 0 },
                                accountRepository = container.accountRepository,
                                calculateCurrentBalanceUseCase = container.calculateCurrentBalanceUseCase,
                                updateBalanceUseCase = container.updateBalanceUseCase,
                            )
                        }
                    },
                )
                val settingsViewModel = viewModel<SettingsViewModel>(
                    key = "settings_for_update_balance",
                    factory = viewModelFactory {
                        initializer {
                            SettingsViewModel(
                                settingsRepository = container.settingsRepository,
                                exportJsonUseCase = container.exportJsonUseCase,
                            )
                        }
                    },
                )
                val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                UpdateBalanceScreen(
                    viewModel = viewModel,
                    settings = settingsState.settings,
                    onShowResult = {
                        navController.navigate(MoneyDestination.balanceUpdateResultRoute(accountId))
                    },
                )
            }
            composable(
                route = MoneyDestination.BalanceUpdateResultRoute,
                arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
            ) { entry ->
                val accountId = entry.arguments?.getLong("accountId") ?: return@composable
                val owner = navController.getBackStackEntry(MoneyDestination.updateBalanceRoute(accountId))
                val viewModel = viewModel<UpdateBalanceViewModel>(
                    viewModelStoreOwner = owner,
                    key = "update_balance_$accountId",
                    factory = viewModelFactory {
                        initializer {
                            UpdateBalanceViewModel(
                                initialAccountId = accountId.takeIf { it > 0 },
                                accountRepository = container.accountRepository,
                                calculateCurrentBalanceUseCase = container.calculateCurrentBalanceUseCase,
                                updateBalanceUseCase = container.updateBalanceUseCase,
                            )
                        }
                    },
                )
                val settingsViewModel = viewModel<SettingsViewModel>(
                    key = "settings_for_balance_result",
                    factory = viewModelFactory {
                        initializer {
                            SettingsViewModel(
                                settingsRepository = container.settingsRepository,
                                exportJsonUseCase = container.exportJsonUseCase,
                            )
                        }
                    },
                )
                val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                val updateState by viewModel.uiState.collectAsStateWithLifecycle()
                val result = updateState.latestResult ?: return@composable
                BalanceUpdateResultScreen(
                    result = result,
                    settings = settingsState.settings,
                    onDone = {
                        navController.popBackStack(MoneyDestination.updateBalanceRoute(accountId), true)
                    },
                    onOpenAccount = { targetAccountId ->
                        navController.popBackStack(MoneyDestination.updateBalanceRoute(accountId), true)
                        navController.navigate(MoneyDestination.accountDetailRoute(targetAccountId))
                    },
                )
            }
        }
    }
}
}
