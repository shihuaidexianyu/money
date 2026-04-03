package com.shihuaidexianyu.money.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.ui.balance.BalanceAdjustmentDetailScreen
import com.shihuaidexianyu.money.ui.balance.BalanceAdjustmentDetailViewModel
import com.shihuaidexianyu.money.ui.balance.BalanceUpdateDetailScreen
import com.shihuaidexianyu.money.ui.balance.BalanceUpdateDetailViewModel
import com.shihuaidexianyu.money.ui.balance.BalanceUpdateResultScreen
import com.shihuaidexianyu.money.ui.balance.UpdateBalanceScreen
import com.shihuaidexianyu.money.ui.balance.UpdateBalanceViewModel

internal fun NavGraphBuilder.addBalanceGraph(
    navController: NavHostController,
    container: MoneyAppContainer,
) {
    composable(
        route = MoneyDestination.BalanceUpdateDetailRoute,
        arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
    ) { entry ->
        val recordId = entry.arguments?.getLong("recordId") ?: return@composable
        val viewModel = viewModel<BalanceUpdateDetailViewModel>(
            key = "balance_update_detail_$recordId",
            factory = moneyViewModelFactory {
                BalanceUpdateDetailViewModel(
                    recordId = recordId,
                    accountRepository = container.accountRepository,
                    transactionRepository = container.transactionRepository,
                )
            },
        )
        val settingsViewModel = rememberSettingsViewModel(
            container = container,
            key = "settings_for_balance_update_detail",
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
            factory = moneyViewModelFactory {
                BalanceAdjustmentDetailViewModel(
                    recordId = recordId,
                    accountRepository = container.accountRepository,
                    transactionRepository = container.transactionRepository,
                )
            },
        )
        val settingsViewModel = rememberSettingsViewModel(
            container = container,
            key = "settings_for_balance_adjustment_detail",
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
            factory = moneyViewModelFactory {
                UpdateBalanceViewModel(
                    initialAccountId = accountId.takeIf { it > 0 },
                    accountRepository = container.accountRepository,
                    calculateCurrentBalanceUseCase = container.calculateCurrentBalanceUseCase,
                    updateBalanceUseCase = container.updateBalanceUseCase,
                )
            },
        )
        val settingsViewModel = rememberSettingsViewModel(
            container = container,
            key = "settings_for_update_balance",
        )
        val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        UpdateBalanceScreen(
            viewModel = viewModel,
            settings = settingsState.settings,
            onShowResult = { navController.navigate(MoneyDestination.balanceUpdateResultRoute(accountId)) },
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
            factory = moneyViewModelFactory {
                UpdateBalanceViewModel(
                    initialAccountId = accountId.takeIf { it > 0 },
                    accountRepository = container.accountRepository,
                    calculateCurrentBalanceUseCase = container.calculateCurrentBalanceUseCase,
                    updateBalanceUseCase = container.updateBalanceUseCase,
                )
            },
        )
        val settingsViewModel = rememberSettingsViewModel(
            container = container,
            key = "settings_for_balance_result",
        )
        val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        val updateState by viewModel.uiState.collectAsStateWithLifecycle()
        val result = updateState.latestResult ?: return@composable
        BalanceUpdateResultScreen(
            result = result,
            settings = settingsState.settings,
            onDone = { navController.popBackStack(MoneyDestination.updateBalanceRoute(accountId), true) },
            onOpenAccount = { targetAccountId ->
                navController.popBackStack(MoneyDestination.updateBalanceRoute(accountId), true)
                navController.navigate(MoneyDestination.accountDetailRoute(targetAccountId))
            },
        )
    }
}
