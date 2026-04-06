package com.shihuaidexianyu.money.ui.reminder

import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.usecase.ReminderScheduleValidator

internal data class ReminderScheduleInput(
    val periodValue: Int,
    val periodMonth: Int?,
)

internal fun parseReminderScheduleInput(
    periodType: ReminderPeriodType,
    periodDayText: String,
    periodMonthText: String,
    periodCustomDaysText: String,
): Result<ReminderScheduleInput> = runCatching {
    when (periodType) {
        ReminderPeriodType.MONTHLY -> {
            val periodValue = periodDayText.toIntOrNull()
                ?: throw IllegalArgumentException("请输入有效的每月日期")
            ReminderScheduleValidator.validate(periodType, periodValue, null)
            ReminderScheduleInput(periodValue = periodValue, periodMonth = null)
        }

        ReminderPeriodType.YEARLY -> {
            val periodMonth = periodMonthText.toIntOrNull()
                ?: throw IllegalArgumentException("请输入有效的月份")
            val periodValue = periodDayText.toIntOrNull()
                ?: throw IllegalArgumentException("请输入有效的日期")
            ReminderScheduleValidator.validate(periodType, periodValue, periodMonth)
            ReminderScheduleInput(periodValue = periodValue, periodMonth = periodMonth)
        }

        ReminderPeriodType.CUSTOM_DAYS -> {
            val periodValue = periodCustomDaysText.toIntOrNull()
                ?: throw IllegalArgumentException("请输入有效的间隔天数")
            ReminderScheduleValidator.validate(periodType, periodValue, null)
            ReminderScheduleInput(periodValue = periodValue, periodMonth = null)
        }
    }
}
