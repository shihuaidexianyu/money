package com.shihuaidexianyu.money

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.ui.theme.MoneyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val container = (application as MoneyApplication).container
        setContent {
            val settings = container.settingsRepository.observeSettings().collectAsStateWithLifecycle(
                initialValue = com.shihuaidexianyu.money.domain.model.AppSettings(),
            )
            MoneyTheme(themeMode = settings.value.themeMode) {
                MoneyApp(container = container)
            }
        }
    }
}

