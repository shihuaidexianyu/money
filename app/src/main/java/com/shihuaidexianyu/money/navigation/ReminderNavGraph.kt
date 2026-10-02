package com.shihuaidexianyu.money.navigation

import androidx.navigation.NavGraphBuilder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.ui.reminder.ReminderUiModel

internal fun NavGraphBuilder.addReminderGraph(
    navController: NavHostController,
    container: MoneyAppContainer,
) {
    composable(MoneyDestination.ReminderListRoute) { RetiredFeatureDestination(navController) }
    composable(MoneyDestination.CreateReminderRoute) { RetiredFeatureDestination(navController) }
    composable(
        route = MoneyDestination.EditReminderRoute,
        arguments = listOf(navArgument("reminderId") { type = NavType.LongType }),
    ) { RetiredFeatureDestination(navController) }
}

/** Kept for old notification payloads and legacy backup workflows. */
internal fun reminderCashFlowRoute(reminder: ReminderUiModel): String =
    MoneyDestination.recordCashFlowRoute(
        direction = CashFlowDirection.fromValue(reminder.direction),
        accountId = reminder.accountId,
        amount = reminder.amount,
        note = reminder.name,
        reminderId = reminder.id,
        expectedDueAt = reminder.nextDueAt,
    )
