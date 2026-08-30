package com.aesthetic.spotify.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AuraPurple,
    secondary = AuraCyan,
    tertiary = AuraPink,
    background = AuraBg,
    onBackground = Color.White,
    surface = Color(0xFF0E0E13),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF16161D),
    onSurfaceVariant = Color(0xFFB9B9C6),
    primaryContainer = Color(0xFF2A1F4D),
    onPrimaryContainer = Color.White,
    secondaryContainer = Color(0xFF0E2A31),
    onSecondaryContainer = Color.White,
    outline = Color(0xFF2C2C36)
)

private val LightColorScheme = DarkColorScheme

@Composable
fun AuraSonicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
