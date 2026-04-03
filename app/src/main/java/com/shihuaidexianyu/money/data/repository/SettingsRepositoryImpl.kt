package com.shihuaidexianyu.money.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shihuaidexianyu.money.data.db.appSettingsDataStore
import com.shihuaidexianyu.money.domain.model.AccountSortMode
import com.shihuaidexianyu.money.domain.model.AmountDisplayStyle
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.WeekStart
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

    override suspend fun updateWeekStart(weekStart: WeekStart) {
        context.appSettingsDataStore.edit { it[Keys.WeekStart] = weekStart.value }
    }

    override suspend fun updateCurrencySymbol(symbol: String) {
        context.appSettingsDataStore.edit { it[Keys.CurrencySymbol] = symbol.trim().ifEmpty { "¥" } }
    }

    override suspend fun updateAmountDisplayStyle(style: AmountDisplayStyle) {
        context.appSettingsDataStore.edit { it[Keys.AmountDisplayStyle] = style.value }
    }

    override suspend fun updateShowStaleMark(show: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.ShowStaleMark] = show }
    }

    override suspend fun updateAccountSortMode(mode: AccountSortMode) {
        context.appSettingsDataStore.edit { it[Keys.AccountSortMode] = mode.value }
    }

    private fun preferencesToSettings(preferences: Preferences): AppSettings {
        return AppSettings(
            homePeriod = HomePeriod.fromValue(preferences[Keys.HomePeriod]),
            weekStart = WeekStart.fromValue(preferences[Keys.WeekStart]),
            currencySymbol = preferences[Keys.CurrencySymbol] ?: "¥",
            amountDisplayStyle = AmountDisplayStyle.fromValue(preferences[Keys.AmountDisplayStyle]),
            showStaleMark = preferences[Keys.ShowStaleMark] ?: true,
            accountSortMode = AccountSortMode.fromValue(preferences[Keys.AccountSortMode]),
        )
    }

    private object Keys {
        val HomePeriod = stringPreferencesKey("home_period")
        val WeekStart = stringPreferencesKey("week_start")
        val CurrencySymbol = stringPreferencesKey("currency_symbol")
        val AmountDisplayStyle = stringPreferencesKey("amount_display_style")
        val ShowStaleMark = booleanPreferencesKey("show_stale_mark")
        val AccountSortMode = stringPreferencesKey("account_sort_mode")
    }
}
