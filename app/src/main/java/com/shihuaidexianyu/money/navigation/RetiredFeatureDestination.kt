package com.shihuaidexianyu.money.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController

/** Retain old route identities so restoring an older task cannot fail or expose retired tools. */
@Composable
internal fun RetiredFeatureDestination(navController: NavHostController) {
    LaunchedEffect(Unit) {
        navController.navigate(MoneyDestination.Accounts.route) {
            popUpTo(navController.graph.id)
            launchSingleTop = true
        }
    }
}
