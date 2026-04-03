package com.shihuaidexianyu.money

import androidx.compose.runtime.Composable
import com.shihuaidexianyu.money.navigation.MoneyNavGraph

@Composable
fun MoneyApp(container: MoneyAppContainer) {
    MoneyNavGraph(container = container)
}

