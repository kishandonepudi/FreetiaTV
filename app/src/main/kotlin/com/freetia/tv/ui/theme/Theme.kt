package com.freetia.tv.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF2C3E50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF34495E),
    onPrimaryContainer = Color(0xFFECF0F1),
    secondary = Color(0xFF3498DB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2980B9),
    onSecondaryContainer = Color(0xFFECF0F1),
    tertiary = Color(0xFF1ABC9C),
    onTertiary = Color.White,
    error = Color(0xFFE74C3C),
    onError = Color.White,
    background = Color(0xFF1A1A1A),
    onBackground = Color.White,
    surface = Color(0xFF262626),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF3F3F3F),
    onSurfaceVariant = Color(0xFFB0B0B0)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2C3E50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECF0F1),
    onPrimaryContainer = Color(0xFF2C3E50),
    secondary = Color(0xFF3498DB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3F2FD),
    onSecondaryContainer = Color(0xFF2980B9),
    tertiary = Color(0xFF1ABC9C),
    onTertiary = Color.White,
    error = Color(0xFFE74C3C),
    onError = Color.White,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFECECEC),
    onSurfaceVariant = Color(0xFF505050)
)

@Composable
fun FreetiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view)?.isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
