package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.domain.model.DEFAULT_BALANCE_UPDATE_REMINDER_DAYS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryAccountReminderSettingsRepository : AccountReminderSettingsRepository {
    private val reminderDays = MutableStateFlow<Map<Long, Int>>(emptyMap())

    override fun observeReminderDays(): Flow<Map<Long, Int>> = reminderDays

    override suspend fun getReminderDays(accountId: Long): Int {
        return reminderDays.value[accountId] ?: DEFAULT_BALANCE_UPDATE_REMINDER_DAYS
    }

    override suspend fun updateReminderDays(accountId: Long, days: Int) {
        val normalizedDays = days.coerceAtLeast(1)
        reminderDays.value = if (normalizedDays == DEFAULT_BALANCE_UPDATE_REMINDER_DAYS) {
            reminderDays.value - accountId
        } else {
            reminderDays.value + (accountId to normalizedDays)
        }
    }
}
