package com.shihuaidexianyu.money

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MoneyApplication : Application() {
    lateinit var container: MoneyAppContainer
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = MoneyAppContainer(this)
        appScope.launch {
            container.seedDebugSampleDataIfNeeded()
        }
    }
}
