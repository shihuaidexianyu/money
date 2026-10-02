package com.shihuaidexianyu.money

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import com.shihuaidexianyu.money.navigation.MoneyDestination
import com.shihuaidexianyu.money.navigation.RetiredFeatureDestination
import com.shihuaidexianyu.money.notification.MoneyNotificationScheduler
import com.shihuaidexianyu.money.notification.NotificationWorkContract
import com.shihuaidexianyu.money.notification.WorkManagerNotificationSyncRequester
import com.shihuaidexianyu.money.domain.notification.NotificationSyncReason
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MinimalFeatureRetirementTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun restoredRetiredRouteReturnsToAccountsWithoutLeavingADeadBackStack() {
        var remainingPreviousRoute: String? = "not_ready"
        composeRule.setContent {
            val controller = rememberNavController()
            MoneyTheme {
                NavHost(controller, startDestination = MoneyDestination.Home.route) {
                    composable(MoneyDestination.Home.route) { RetiredFeatureDestination(controller) }
                    composable(MoneyDestination.Accounts.route) {
                        remainingPreviousRoute = controller.previousBackStackEntry?.destination?.route
                        Text("主线账户")
                    }
                }
            }
        }
        composeRule.onNodeWithText("主线账户").assertExists()
        composeRule.runOnIdle { assertEquals(null, remainingPreviousRoute) }
    }

    @Test
    fun startupAndMutationRequestsCannotLeaveLiveReminderWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val requester = WorkManagerNotificationSyncRequester(context)
        MoneyNotificationScheduler.scheduleAfterReady(context, emptyList(), emptyList(), requester)
        NotificationSyncReason.entries.forEach(requester::request)
        val manager = WorkManager.getInstance(context)
        val names = NotificationWorkContract.LEGACY_UNIQUE_WORK_NAMES +
            setOf(NotificationWorkContract.PERIODIC_WORK_NAME, NotificationWorkContract.IMMEDIATE_WORK_NAME)
        names.forEach { name ->
            val jobs = manager.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS)
            assertTrue("Retired work remains active: $name", jobs.all { it.state.isFinished })
        }
    }
}
