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

        fun normalizeOrder(values: List<AccountGroupType>): List<AccountGroupType> {
            val deduped = values.distinct()
            return deduped + entries.filterNot { it in deduped }
        }

        fun fromStoredOrder(value: String?): List<AccountGroupType> {
            val parsed = value
                ?.split(",")
                ?.mapNotNull { raw -> entries.firstOrNull { it.value == raw.trim() } }
                .orEmpty()
            return normalizeOrder(parsed)
        }

        fun toStoredOrder(values: List<AccountGroupType>): String {
            return normalizeOrder(values).joinToString(",") { it.value }
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

