package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.dao.RecurringReminderDao
import com.shihuaidexianyu.money.data.entity.RecurringReminderEntity
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.util.minuteTickerFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class RecurringReminderRepositoryImpl(
    private val dao: RecurringReminderDao,
    private val tickerFlow: Flow<Long> = minuteTickerFlow(),
) : RecurringReminderRepository {
    override fun observeAllReminders(): Flow<List<RecurringReminderEntity>> = dao.observeAll()

    override fun observeDueReminders(): Flow<List<RecurringReminderEntity>> =
        combine(
            dao.observeAll(),
            tickerFlow,
        ) { reminders, now ->
            reminders.filter { it.isEnabled && it.nextDueAt <= now }
        }.distinctUntilChanged()

    override suspend fun getReminderById(id: Long): RecurringReminderEntity? = dao.queryById(id)

    override suspend fun queryAll(): List<RecurringReminderEntity> = dao.queryAll()

    override suspend fun queryDue(): List<RecurringReminderEntity> =
        dao.queryDue(System.currentTimeMillis())

    override suspend fun insertReminder(reminder: RecurringReminderEntity): Long =
        dao.insert(reminder)

    override suspend fun updateReminder(reminder: RecurringReminderEntity) = dao.update(reminder)

    override suspend fun deleteReminder(id: Long) = dao.delete(id)
}
