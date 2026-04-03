package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.domain.model.AccountSortMode
import com.shihuaidexianyu.money.domain.model.AmountDisplayStyle
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.WeekStart
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun updateHomePeriod(period: HomePeriod)
    suspend fun updateWeekStart(weekStart: WeekStart)
    suspend fun updateCurrencySymbol(symbol: String)
    suspend fun updateAmountDisplayStyle(style: AmountDisplayStyle)
    suspend fun updateShowStaleMark(show: Boolean)
    suspend fun updateAccountSortMode(mode: AccountSortMode)
}
