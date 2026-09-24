package com.piroman.elara.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AuraPurple,
    onPrimary = Color.White,
    // Violet, not cyan: the secondary slot is what most active/selected
    // controls pick up, so leaving it cyan is what made the app read as
    // "black with a purple accent" instead of purple.
    secondary = AuraViolet,
    onSecondary = Color(0xFF1B1030),
    tertiary = AuraLavender,
    onTertiary = Color(0xFF1B1030),
    background = AuraBg,
    onBackground = Color.White,
    surface = AuraScaffoldSurface,
    onSurface = Color.White,
    surfaceVariant = AuraContainer,
    onSurfaceVariant = Color(0xFFB9B9C6),
    // The two containers are the one place purple is allowed to fill a surface,
    // because they only ever back a *selected* control — a chip, a nav indicator
    // — where the colour is the signal.
    primaryContainer = Color(0xFF3B2A6E),
    onPrimaryContainer = Color.White,
    secondaryContainer = Color(0xFF2C2154),
    onSecondaryContainer = Color.White,
    outline = AuraOutline
)

/**
 * ELARA is dark-only: there is one scheme and it is the dark one.
 *
 * This used to read `darkTheme = isSystemInDarkTheme()` and pick between two
 * schemes that were *both* the dark palette — so the colours never changed, but
 * the status-bar icon tint did, because it was set to `!darkTheme`. On a device
 * with the system in light mode that produced **dark status-bar icons on the
 * near-black [AuraBg] background**, i.e. an invisible clock and battery. The
 * parameter is gone rather than ignored, and the icon appearance is now pinned
 * to light unconditionally to match the always-dark surface.
 */
@Composable
fun AuraSonicTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Keeps the bar readable on API levels where edge-to-edge does not
            // make it fully transparent; it matches the app background exactly.
            window.statusBarColor = DarkColorScheme.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            // false = light (white) icons.
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
