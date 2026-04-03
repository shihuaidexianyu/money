package com.shihuaidexianyu.money.data.repository

import kotlinx.coroutines.flow.Flow

interface AccountReminderSettingsRepository {
    fun observeReminderDays(): Flow<Map<Long, Int>>
    suspend fun getReminderDays(accountId: Long): Int
    suspend fun updateReminderDays(accountId: Long, days: Int)
}
