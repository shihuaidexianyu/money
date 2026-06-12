package com.shihuaidexianyu.money.domain.model

const val DEFAULT_ACCOUNT_COLOR_NAME = "blue"
const val DEFAULT_ACCOUNT_ICON_NAME = "wallet"

val ACCOUNT_ICON_NAMES = listOf(
    "wallet",
    "bank",
    "cash",
    "credit_card",
    "savings",
    "investment",
    "home",
    "phone",
    "shopping",
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

fun normalizeAccountColorName(value: String?): String {
    return value?.takeIf { it in ACCOUNT_COLOR_NAMES } ?: DEFAULT_ACCOUNT_COLOR_NAME
}

fun normalizeAccountIconName(value: String?): String {
    return value?.takeIf { it in ACCOUNT_ICON_NAMES } ?: DEFAULT_ACCOUNT_ICON_NAME
}
