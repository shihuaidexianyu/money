package com.shihuaidexianyu.money.domain.model

enum class ThemeMode(val value: String, val displayName: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun fromValue(value: String?): ThemeMode {
            return entries.firstOrNull { it.value == value } ?: SYSTEM
        }
    }
}
