package com.freetia.tv.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun darkScheme(b: BrandColors): ColorScheme = darkColorScheme(
    primary = b.ink,
    onPrimary = b.textPrimary,
    primaryContainer = b.surfaceVariant,
    onPrimaryContainer = b.textPrimary,
    secondary = b.blue,
    onSecondary = b.textPrimary,
    secondaryContainer = b.surfaceVariant,
    onSecondaryContainer = b.textPrimary,
    tertiary = b.violet,
    onTertiary = b.textPrimary,
    error = b.error,
    onError = b.textPrimary,
    background = b.ink,
    onBackground = b.textPrimary,
    surface = b.surface,
    onSurface = b.textPrimary,
    surfaceVariant = b.surfaceVariant,
    onSurfaceVariant = b.textMuted,
)

// Light theme: brand ink becomes the text colour, accents stay brand blue/violet.
private val LightColorScheme = lightColorScheme(
    primary = DefaultBrand.blue,
    onPrimary = Color.White,
    primaryContainer = DefaultBrand.blue.copy(alpha = 0.12f),
    onPrimaryContainer = DefaultBrand.ink,
    secondary = DefaultBrand.violet,
    onSecondary = Color.White,
    secondaryContainer = DefaultBrand.violet.copy(alpha = 0.12f),
    onSecondaryContainer = DefaultBrand.ink,
    tertiary = DefaultBrand.violet,
    onTertiary = Color.White,
    error = DefaultBrand.error,
    onError = Color.White,
    background = Color.White,
    onBackground = DefaultBrand.ink,
    surface = Color.White,
    onSurface = DefaultBrand.ink,
    surfaceVariant = DefaultBrand.ink.copy(alpha = 0.06f),
    onSurfaceVariant = DefaultBrand.inkMuted,
)

@Composable
fun FreetiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = false,
    textSizePercent: Int = 100,
    content: @Composable () -> Unit
) {
    val brand = if (highContrast) HighContrastBrand else DefaultBrand
    val colorScheme = remember(darkTheme, highContrast) {
        if (darkTheme || highContrast) darkScheme(brand) else LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        // Bar colour comes from the window theme (colorPrimaryDark); window.statusBarColor is deprecated on API 35.
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme && !highContrast
        }
    }

    // Scales every sp (typography and fixed sizes alike) on top of the system font scale; rows size to content.
    val systemDensity = LocalDensity.current
    val density = remember(systemDensity, textSizePercent) {
        Density(systemDensity.density, effectiveFontScale(systemDensity.fontScale, textSizePercent))
    }
    CompositionLocalProvider(LocalBrand provides brand, LocalDensity provides density) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
