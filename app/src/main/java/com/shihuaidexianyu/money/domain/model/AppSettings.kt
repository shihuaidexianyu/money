package com.shihuaidexianyu.money.domain.model

data class AppSettings(
    val homePeriod: HomePeriod = HomePeriod.WEEK,
    val weekStart: WeekStart = WeekStart.MONDAY,
    val currencySymbol: String = "¥",
    val amountDisplayStyle: AmountDisplayStyle = AmountDisplayStyle.SYMBOL_BEFORE,
    val showStaleMark: Boolean = true,
    val accountSortMode: AccountSortMode = AccountSortMode.RECENT_USED,
)
