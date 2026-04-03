package com.shihuaidexianyu.money.domain.model

enum class AccountGroupType(
    val value: String,
    val displayName: String,
) {
    PAYMENT("payment", "支付类"),
    BANK("bank", "银行类"),
    INVESTMENT("investment", "投资类"),
    ;

    companion object {
        fun fromValue(value: String?): AccountGroupType {
            return entries.firstOrNull { it.value == value } ?: PAYMENT
        }
    }
}

const val DEFAULT_BALANCE_UPDATE_REMINDER_WEEKDAY = "friday"
const val DEFAULT_BALANCE_UPDATE_REMINDER_HOUR = 22
const val DEFAULT_BALANCE_UPDATE_REMINDER_MINUTE = 0

enum class BalanceUpdateReminderWeekday(
    val value: String,
    val displayName: String,
) {
    MONDAY("monday", "周一"),
    TUESDAY("tuesday", "周二"),
    WEDNESDAY("wednesday", "周三"),
    THURSDAY("thursday", "周四"),
    FRIDAY("friday", "周五"),
    SATURDAY("saturday", "周六"),
    SUNDAY("sunday", "周日"),
    ;

    companion object {
        fun fromValue(value: String?): BalanceUpdateReminderWeekday {
            return entries.firstOrNull { it.value == value } ?: FRIDAY
        }
    }
}

data class BalanceUpdateReminderConfig(
    val weekday: BalanceUpdateReminderWeekday = BalanceUpdateReminderWeekday.FRIDAY,
    val hour: Int = DEFAULT_BALANCE_UPDATE_REMINDER_HOUR,
    val minute: Int = DEFAULT_BALANCE_UPDATE_REMINDER_MINUTE,
) {
    init {
        require(hour in 0..23) { "hour out of range" }
        require(minute in 0..59) { "minute out of range" }
    }

    val timeText: String
        get() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    val displayText: String
        get() = "${weekday.displayName} $timeText"
}

enum class CashFlowDirection(
    val value: String,
    val displayName: String,
) {
    INFLOW("inflow", "入账"),
    OUTFLOW("outflow", "出账"),
    ;

    companion object {
        fun fromValue(value: String?): CashFlowDirection {
            return entries.firstOrNull { it.value == value } ?: INFLOW
        }
    }
}

enum class HomePeriod(
    val value: String,
    val displayName: String,
) {
    WEEK("week", "本周"),
    MONTH("month", "本月"),
    ;

    companion object {
        fun fromValue(value: String?): HomePeriod {
            return entries.firstOrNull { it.value == value } ?: WEEK
        }
    }
}

enum class WeekStart(
    val value: String,
    val displayName: String,
) {
    MONDAY("monday", "周一"),
    ;

    companion object {
        fun fromValue(value: String?): WeekStart {
            return entries.firstOrNull { it.value == value } ?: MONDAY
        }
    }
}

enum class AmountDisplayStyle(
    val value: String,
    val displayName: String,
) {
    SYMBOL_BEFORE("symbol_before", "符号前置"),
    SYMBOL_AFTER("symbol_after", "符号后置"),
    ;

    companion object {
        fun fromValue(value: String?): AmountDisplayStyle {
            return entries.firstOrNull { it.value == value } ?: SYMBOL_BEFORE
        }
    }
}

enum class AccountSortMode(
    val value: String,
    val displayName: String,
) {
    RECENT_USED("recent_used", "最近使用"),
    MANUAL("manual", "手动排序"),
    BALANCE_DESC("balance_desc", "余额降序"),
    ;

    companion object {
        fun fromValue(value: String?): AccountSortMode {
            return entries.firstOrNull { it.value == value } ?: RECENT_USED
        }
    }
}
