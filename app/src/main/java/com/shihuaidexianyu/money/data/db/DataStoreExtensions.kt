package com.shihuaidexianyu.money.data.db

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")
val Context.accountReminderSettingsDataStore by preferencesDataStore(name = "account_reminder_settings")
