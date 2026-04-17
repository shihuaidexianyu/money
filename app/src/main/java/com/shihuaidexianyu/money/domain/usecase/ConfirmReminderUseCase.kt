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
            if (nextDueAt <= previousDueAt) {
                // 计算异常时，使用一天后的时间作为安全降级，避免崩溃或死循环
                nextDueAt = previousDueAt + 24L * 60L * 60L * 1000L
                break
            }
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
