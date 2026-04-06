package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository

class ConfirmReminderUseCase(
    private val reminderRepository: RecurringReminderRepository,
) {
    suspend operator fun invoke(reminderId: Long) {
        val reminder = requireNotNull(reminderRepository.getReminderById(reminderId)) { "提醒不存在" }
        val now = System.currentTimeMillis()
        val periodType = ReminderPeriodType.fromValue(reminder.periodType)
        var nextDueAt = reminder.nextDueAt
        do {
            val previousDueAt = nextDueAt
            nextDueAt = ReminderNextDueCalculator.calculateNextDue(
                currentDueAt = previousDueAt,
                periodType = periodType,
                periodValue = reminder.periodValue,
                periodMonth = reminder.periodMonth,
            )
            require(nextDueAt > previousDueAt) { "提醒下次时间计算失败" }
        } while (nextDueAt <= now)
        reminderRepository.updateReminder(
            reminder.copy(
                nextDueAt = nextDueAt,
                lastConfirmedAt = now,
                updatedAt = now,
            ),
        )
    }
}
