package com.shihuaidexianyu.money.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Teal600,
    onPrimary = SurfaceWhite,
    primaryContainer = Teal100,
    onPrimaryContainer = Ink900,
    secondary = WarningMuted,
    onSecondary = SurfaceWhite,
    tertiary = SuccessTeal,
    onTertiary = SurfaceWhite,
    background = BackgroundWarm,
    onBackground = Ink900,
    surface = SurfaceWhite,
    onSurface = Ink900,
    surfaceVariant = SurfaceSoft,
    onSurfaceVariant = Ink700,
    outline = BorderSoft,
    outlineVariant = BorderSoft,
)

private val DarkColors = darkColorScheme(
    primary = Teal500,
    onPrimary = Night950,
    primaryContainer = Teal700,
    onPrimaryContainer = SurfaceWhite,
    secondary = WarningMuted,
    onSecondary = Night950,
    tertiary = SuccessTeal,
    onTertiary = Night950,
    background = Night950,
    onBackground = SurfaceWhite,
    surface = Night900,
    onSurface = SurfaceWhite,
    surfaceVariant = Night800,
    onSurfaceVariant = BorderSoft,
    outline = BorderDark,
    outlineVariant = BorderDark,
)

@Composable
fun MoneyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MoneyTypography,
        content = content,
    )
}
