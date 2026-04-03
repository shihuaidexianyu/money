package com.shihuaidexianyu.money.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.shihuaidexianyu.money.domain.model.ThemeMode

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
    primary = Teal300,
    onPrimary = Night950,
    primaryContainer = Teal700,
    onPrimaryContainer = Teal200,
    secondary = Amber400,
    onSecondary = Night950,
    secondaryContainer = Color(0xFF4B3A1E),
    onSecondaryContainer = Amber300,
    tertiary = Color(0xFF6FD3AC),
    onTertiary = Night950,
    tertiaryContainer = Color(0xFF153B34),
    onTertiaryContainer = Color(0xFFA4ECDC),
    background = Color(0xFF0B1216),
    onBackground = Color(0xFFE7EEF2),
    surface = Color(0xFF111A20),
    onSurface = Color(0xFFE7EEF2),
    surfaceVariant = Night700,
    onSurfaceVariant = Color(0xFFB4C1C9),
    outline = Night650,
    outlineVariant = BorderDark,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun MoneyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MoneyTypography,
        content = content,
    )
}

