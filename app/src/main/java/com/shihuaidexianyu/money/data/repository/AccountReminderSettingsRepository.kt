package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import kotlinx.coroutines.flow.Flow

interface AccountReminderSettingsRepository {
    fun observeReminderConfigs(): Flow<Map<Long, BalanceUpdateReminderConfig>>
    suspend fun getReminderConfig(accountId: Long): BalanceUpdateReminderConfig
    suspend fun updateReminderConfig(accountId: Long, config: BalanceUpdateReminderConfig)
}
