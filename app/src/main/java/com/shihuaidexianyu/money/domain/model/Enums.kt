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

const val DEFAULT_BALANCE_UPDATE_REMINDER_DAYS = 7

enum class BalanceUpdateReminderInterval(
    val days: Int,
    val displayName: String,
) {
    EVERY_DAY(1, "每天"),
    EVERY_3_DAYS(3, "每 3 天"),
    EVERY_WEEK(7, "每 7 天"),
    EVERY_14_DAYS(14, "每 14 天"),
    EVERY_30_DAYS(30, "每 30 天"),
    ;

    companion object {
        fun fromDays(days: Int): BalanceUpdateReminderInterval {
            val normalizedDays = days.coerceAtLeast(1)
            return entries.firstOrNull { it.days == normalizedDays } ?: EVERY_WEEK
        }
    }
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
