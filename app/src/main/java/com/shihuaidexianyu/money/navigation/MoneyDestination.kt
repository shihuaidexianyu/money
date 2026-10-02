package com.shihuaidexianyu.money.navigation
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.annotation.StringRes
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.CashFlowDirection

sealed class MoneyDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
) {
    data object Home : MoneyDestination("home", R.string.home_title)
    data object History : MoneyDestination("history", R.string.nav_history)
    data object Accounts : MoneyDestination("accounts", R.string.accounts_title)
    data object Settings : MoneyDestination("settings", R.string.settings_title)

    companion object {
        val topLevel: List<MoneyDestination>
            get() = listOf(Accounts, History)

        const val CreateAccountRoute = "accounts/create"
        const val ReorderAccountsRoute = "accounts/reorder"
        const val EditAccountRoute = "accounts/{accountId}/edit"
        const val AccountDetailRoute = "accounts/{accountId}"
        const val RecordCashFlowRoute = "records/cashflow/{direction}/{accountId}"
        const val LedgerEntryRoute = "records/new"
        const val RecordTransferRoute = "records/transfer/{fromAccountId}"
        const val EditCashFlowRoute = "history/cashflow/{recordId}"
        const val AccountHistoryRoute = "history/account/{accountId}"
        const val EditTransferRoute = "history/transfer/{recordId}"
        const val BalanceUpdateDetailRoute = "history/balance-update/{recordId}"
        const val EditBalanceUpdateRoute = "history/balance-update/{recordId}/edit"
        const val BalanceAdjustmentDetailRoute = "history/balance-adjustment/{recordId}"
        const val UpdateBalanceRoute = "balance/update/{accountId}"
        const val BalanceUpdateResultRoute = "balance/update/{accountId}/result"
        const val BatchReconcileRoute = "balance/reconcile"
        const val ReminderListRoute = "reminders"
        const val LanMcpRoute = "settings/lan-ai"
        const val CreateReminderRoute = "reminders/create"
        const val EditReminderRoute = "reminders/{reminderId}/edit"
        fun accountDetailRoute(accountId: Long): String = "accounts/$accountId"
        fun editAccountRoute(accountId: Long): String = "accounts/$accountId/edit"
        fun recordCashFlowRoute(direction: CashFlowDirection, accountId: Long): String {
            return "records/cashflow/${direction.value}/$accountId"
        }

        fun recordCashFlowRoute(
            direction: CashFlowDirection,
            accountId: Long,
            amount: Long?,
            note: String?,
            reminderId: Long?,
            expectedDueAt: Long?,
            occurredAt: Long? = null,
        ): String {
            val baseRoute = recordCashFlowRoute(direction, accountId)
            val query = buildList {
                amount?.takeIf { it > 0 }?.let { add("amount=$it") }
                note?.takeIf { it.isNotBlank() }?.let { add("purpose=${NavigationQueryCodec.encode(it)}") }
                reminderId?.takeIf { it > 0 }?.let { add("reminderId=$it") }
                expectedDueAt?.takeIf { it > 0 }?.let { add("expectedDueAt=$it") }
                occurredAt?.let { add("occurredAt=$it") }
            }
            return if (query.isEmpty()) baseRoute else "$baseRoute?${query.joinToString("&")}"
        }

        fun recordTransferRoute(fromAccountId: Long = 0L): String = "records/transfer/$fromAccountId"
        fun editCashFlowRoute(recordId: Long): String = "history/cashflow/$recordId"
        fun accountHistoryRoute(accountId: Long): String = "history/account/$accountId"
        fun editTransferRoute(recordId: Long): String = "history/transfer/$recordId"
        fun balanceUpdateDetailRoute(recordId: Long): String = "history/balance-update/$recordId"
        fun editBalanceUpdateRoute(recordId: Long): String = "history/balance-update/$recordId/edit"
        fun balanceAdjustmentDetailRoute(recordId: Long): String = "history/balance-adjustment/$recordId"

        fun updateBalanceRoute(accountId: Long = 0L): String = "balance/update/$accountId"

        fun balanceUpdateResultRoute(accountId: Long): String = "balance/update/$accountId/result"

        fun editReminderRoute(reminderId: Long): String = "reminders/$reminderId/edit"
    }
}

/**
 * The cross-tab navigation contract. EVERY programmatic jump to a bottom-bar tab (Home, History,
 * Accounts) must go through this — the same options the bar itself uses. A plain navigate() to a
 * tab route pushes a second instance of that tab outside the bar's save/restore system; from then
 * on the bar's own taps misbehave until the duplicate is popped with the back gesture.
 */
fun NavHostController.navigateToTopLevelTab(destination: MoneyDestination) {
    navigate(destination.route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
    }
}
