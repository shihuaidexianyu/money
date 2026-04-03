package com.shihuaidexianyu.money.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.shihuaidexianyu.money.data.db.accountReminderSettingsDataStore
import com.shihuaidexianyu.money.domain.model.DEFAULT_BALANCE_UPDATE_REMINDER_DAYS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AccountReminderSettingsRepositoryImpl(
    private val context: Context,
) : AccountReminderSettingsRepository {
    override fun observeReminderDays(): Flow<Map<Long, Int>> {
        return context.accountReminderSettingsDataStore.data.map(::preferencesToReminderDays)
    }

    override suspend fun getReminderDays(accountId: Long): Int {
        val preferences = context.accountReminderSettingsDataStore.data.first()
        return preferences[keyFor(accountId)] ?: DEFAULT_BALANCE_UPDATE_REMINDER_DAYS
    }

    override suspend fun updateReminderDays(accountId: Long, days: Int) {
        val normalizedDays = days.coerceAtLeast(1)
        context.accountReminderSettingsDataStore.edit { preferences ->
            val key = keyFor(accountId)
            if (normalizedDays == DEFAULT_BALANCE_UPDATE_REMINDER_DAYS) {
                preferences.remove(key)
            } else {
                preferences[key] = normalizedDays
            }
        }
    }

    private fun preferencesToReminderDays(preferences: Preferences): Map<Long, Int> {
        return buildMap {
            preferences.asMap().forEach { (key, value) ->
                if (key.name.startsWith(KEY_PREFIX) && value is Int) {
                    key.name.removePrefix(KEY_PREFIX).toLongOrNull()?.let { accountId ->
                        put(accountId, value.coerceAtLeast(1))
                    }
                }
            }
        }
    }

    private fun keyFor(accountId: Long) = intPreferencesKey("$KEY_PREFIX$accountId")

    private companion object {
        const val KEY_PREFIX = "account_reminder_days_"
    }
}
