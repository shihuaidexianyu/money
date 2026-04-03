package com.shihuaidexianyu.money.domain.model

data class AppSettings(
    val homePeriod: HomePeriod = HomePeriod.WEEK,
    val currencySymbol: String = "¥",
    val showStaleMark: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accountGroupOrder: List<AccountGroupType> = AccountGroupType.entries,
)

