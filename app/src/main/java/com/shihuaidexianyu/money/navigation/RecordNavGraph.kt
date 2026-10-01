package com.shihuaidexianyu.money.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.usecase.UuidLedgerOperationIdFactory
import com.shihuaidexianyu.money.ui.record.EditCashFlowScreen
import com.shihuaidexianyu.money.ui.record.EditCashFlowViewModel
import com.shihuaidexianyu.money.ui.record.EditTransferScreen
import com.shihuaidexianyu.money.ui.record.EditTransferViewModel
import com.shihuaidexianyu.money.ui.record.LedgerEntryKind
import com.shihuaidexianyu.money.ui.record.LedgerEntryScreen
import com.shihuaidexianyu.money.ui.record.LedgerEntryViewModel

internal fun NavGraphBuilder.addRecordGraph(
    navController: NavHostController,
    container: MoneyAppContainer,
) {
    val closeHistoryEditFlow = {
        if (!navController.popBackStack()) {
            navController.navigate(MoneyDestination.History.route) {
                launchSingleTop = true
                restoreState = true
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
            }
        }
    }

    composable(MoneyDestination.LedgerEntryRoute) { entry ->
        LedgerEntryDestination(entry, navController, container)
    }

    composable(
        route = MoneyDestination.RecordCashFlowRoute +
            "?amount={amount}&purpose={purpose}&reminderId={reminderId}&expectedDueAt={expectedDueAt}",
        arguments = listOf(
            navArgument("direction") { type = NavType.StringType },
            navArgument("accountId") { type = NavType.LongType },
            navArgument("amount") { type = NavType.LongType; defaultValue = 0L },
            navArgument("purpose") { type = NavType.StringType; defaultValue = "" },
            navArgument("reminderId") { type = NavType.LongType; defaultValue = 0L },
            navArgument("expectedDueAt") { type = NavType.LongType; defaultValue = 0L },
        ),
    ) { entry ->
        val direction = CashFlowDirection.fromValue(entry.arguments?.getString("direction"))
        val accountId = entry.arguments?.getLong("accountId") ?: 0L
        val prefillAmount = entry.arguments?.getLong("amount") ?: 0L
        val prefillNote = NavigationQueryCodec.decode(entry.arguments?.getString("purpose") ?: "")
        val reminderId = entry.arguments?.getLong("reminderId") ?: 0L
        val expectedDueAt = entry.arguments?.getLong("expectedDueAt") ?: 0L
        LedgerEntryDestination(
            entry, navController, container,
            initialKind = if (direction == CashFlowDirection.INFLOW) LedgerEntryKind.INCOME else LedgerEntryKind.EXPENSE,
            initialAccountId = accountId.takeIf { it > 0 },
            prefillAmount = prefillAmount.takeIf { it > 0 }, prefillNote = prefillNote.takeIf { it.isNotEmpty() },
            reminderId = reminderId.takeIf { it > 0 }, expectedDueAt = expectedDueAt.takeIf { it > 0 },
        )
    }

    composable(
        route = MoneyDestination.RecordTransferRoute,
        arguments = listOf(navArgument("fromAccountId") { type = NavType.LongType }),
    ) { entry ->
        val fromAccountId = entry.arguments?.getLong("fromAccountId") ?: 0L
        LedgerEntryDestination(
            entry, navController, container, initialKind = LedgerEntryKind.TRANSFER,
            initialAccountId = fromAccountId.takeIf { it > 0 },
        )
    }

    composable(
        route = MoneyDestination.EditCashFlowRoute,
        arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
    ) { entry ->
        val recordId = entry.arguments?.getLong("recordId") ?: return@composable
        val viewModel = viewModel<EditCashFlowViewModel>(
            key = "edit_cash_$recordId",
            factory = moneySavedStateViewModelFactory { savedStateHandle ->
                EditCashFlowViewModel(
                    recordId = recordId,
                    accountRepository = container.accountRepository,
                    transactionRepository = container.transactionRepository,
                    calculateAccountBalancesUseCase = container.calculateAccountBalancesUseCase,
                    updateCashFlowRecordUseCase = container.updateCashFlowRecordUseCase,
                    deleteCashFlowRecordUseCase = container.deleteCashFlowRecordUseCase,
                    savedStateHandle = savedStateHandle,
                )
            },
        )
        EditCashFlowScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onDeleted = closeHistoryEditFlow,
        )
    }

    composable(
        route = MoneyDestination.EditTransferRoute,
        arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
    ) { entry ->
        val recordId = entry.arguments?.getLong("recordId") ?: return@composable
        val viewModel = viewModel<EditTransferViewModel>(
            key = "edit_transfer_$recordId",
            factory = moneySavedStateViewModelFactory { savedStateHandle ->
                EditTransferViewModel(
                    recordId = recordId,
                    accountRepository = container.accountRepository,
                    transactionRepository = container.transactionRepository,
                    calculateAccountBalancesUseCase = container.calculateAccountBalancesUseCase,
                    updateTransferRecordUseCase = container.updateTransferRecordUseCase,
                    deleteTransferRecordUseCase = container.deleteTransferRecordUseCase,
                    savedStateHandle = savedStateHandle,
                )
            },
        )
        EditTransferScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onDeleted = closeHistoryEditFlow,
        )
    }
}

@Composable
internal fun LedgerEntryDestination(
    entry: NavBackStackEntry,
    navController: NavHostController,
    container: MoneyAppContainer,
    initialKind: LedgerEntryKind? = null,
    initialAccountId: Long? = null,
    prefillAmount: Long? = null,
    prefillNote: String? = null,
    reminderId: Long? = null,
    expectedDueAt: Long? = null,
) {
    val viewModel = viewModel<LedgerEntryViewModel>(
        key = "ledger_entry",
        factory = moneySavedStateViewModelFactory { savedStateHandle ->
            LedgerEntryViewModel(
                initialKind = initialKind, initialAccountId = initialAccountId,
                prefillAmount = prefillAmount, prefillNote = prefillNote,
                reminderId = reminderId, expectedDueAt = expectedDueAt,
                accountRepository = container.accountRepository,
                transactionRepository = container.transactionRepository,
                devicePreferencesRepository = container.devicePreferencesRepository,
                calculateAccountBalancesUseCase = container.calculateAccountBalancesUseCase,
                resolveBalanceUpdateContextUseCase = container.resolveBalanceUpdateContextUseCase,
                createCashFlowRecordUseCase = container.createCashFlowRecordUseCase,
                createTransferRecordUseCase = container.createTransferRecordUseCase,
                updateBalanceUseCase = container.updateBalanceUseCase,
                processDueReminderUseCase = container.processDueReminderUseCase,
                savedStateHandle = savedStateHandle, operationIdFactory = UuidLedgerOperationIdFactory,
            )
        },
    )
    val settings by rememberSettingsViewModel(container).uiState.collectAsStateWithLifecycle()
    val supplementalToken by entry.savedStateHandle.getStateFlow(SupplementalEntrySavedTokenKey, 0L).collectAsStateWithLifecycle()
    LaunchedEffect(supplementalToken) {
        if (supplementalToken != 0L) {
            viewModel.refreshPreview()
            entry.savedStateHandle.remove<Long>(SupplementalEntrySavedTokenKey)
        }
    }
    LedgerEntryScreen(
        viewModel = viewModel, settings = settings.portableSettings,
        onBack = { navController.popBackStack() },
        onSaved = {
            navController.previousBackStackEntry?.savedStateHandle?.set(SupplementalEntrySavedTokenKey, System.nanoTime())
            navController.popBackStack()
        },
        onManageAccounts = { navController.navigate(MoneyDestination.CreateAccountRoute) },
        onStartCashFlow = { direction, accountId, amount ->
            navController.navigate(MoneyDestination.recordCashFlowRoute(direction, accountId, amount, "余额核对补记", null, null))
        },
    )
}
