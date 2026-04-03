package com.shihuaidexianyu.money.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shihuaidexianyu.money.data.db.appSettingsDataStore
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepositoryImpl(
    private val context: Context,
) : SettingsRepository {
    override fun observeSettings(): Flow<AppSettings> {
        return context.appSettingsDataStore.data.map(::preferencesToSettings)
    }

    override suspend fun updateHomePeriod(period: HomePeriod) {
        context.appSettingsDataStore.edit { it[Keys.HomePeriod] = period.value }
    }

    override suspend fun updateCurrencySymbol(symbol: String) {
        context.appSettingsDataStore.edit { it[Keys.CurrencySymbol] = symbol.trim().ifEmpty { "¥" } }
    }

    override suspend fun updateShowStaleMark(show: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.ShowStaleMark] = show }
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        context.appSettingsDataStore.edit { it[Keys.ThemeMode] = themeMode.value }
    }

    override suspend fun updateAccountGroupOrder(order: List<AccountGroupType>) {
        context.appSettingsDataStore.edit {
            it[Keys.AccountGroupOrder] = AccountGroupType.toStoredOrder(order)
        }
    }

    private fun preferencesToSettings(preferences: Preferences): AppSettings {
        return AppSettings(
            homePeriod = HomePeriod.fromValue(preferences[Keys.HomePeriod]),
            currencySymbol = preferences[Keys.CurrencySymbol] ?: "¥",
            showStaleMark = preferences[Keys.ShowStaleMark] ?: true,
            themeMode = ThemeMode.fromValue(preferences[Keys.ThemeMode]),
            accountGroupOrder = AccountGroupType.fromStoredOrder(preferences[Keys.AccountGroupOrder]),
        )
    }

    private object Keys {
        val HomePeriod = stringPreferencesKey("home_period")
        val CurrencySymbol = stringPreferencesKey("currency_symbol")
        val ShowStaleMark = booleanPreferencesKey("show_stale_mark")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val AccountGroupOrder = stringPreferencesKey("account_group_order")
    }
}

