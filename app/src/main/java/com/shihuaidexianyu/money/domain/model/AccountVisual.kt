package com.shihuaidexianyu.money.domain.model

const val DEFAULT_ACCOUNT_ICON_NAME = "wallet"
const val DEFAULT_ACCOUNT_COLOR_NAME = "blue"

val ACCOUNT_ICON_NAMES = listOf(
    "wallet",
    "bank",
    "card",
    "cash",
    "savings",
    "chart",
)

val ACCOUNT_COLOR_NAMES = listOf(
    "blue",
    "green",
    "orange",
    "purple",
    "red",
    "teal",
    "gray",
)

fun normalizeAccountIconName(value: String?): String {
    return value?.takeIf { it in ACCOUNT_ICON_NAMES } ?: DEFAULT_ACCOUNT_ICON_NAME
}

fun normalizeAccountColorName(value: String?): String {
    return value?.takeIf { it in ACCOUNT_COLOR_NAMES } ?: DEFAULT_ACCOUNT_COLOR_NAME
}
