package com.shihuaidexianyu.money

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.shihuaidexianyu.money.ui.theme.MoneyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val container = (application as MoneyApplication).container
        setContent {
            MoneyTheme {
                MoneyApp(container = container)
            }
        }
    }
}

