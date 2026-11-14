package com.freetia.tv.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Brand colour and focus tokens (standards/BRAND_KIT.md §3, §6); keep in sync with res/values/colors.xml. */
@Immutable
data class BrandColors(
    val ink: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val blue: Color,
    val violet: Color,
    val liveRed: Color,
    val error: Color,
    val focusBorderWidth: Dp,
    // §6 elevation tints: white (or blue) at low alpha over ink, so surfaces read by tone rather than shadow.
    val tintSubtle: Color,
    val hairline: Color,
    val tintChip: Color,
    val tintKey: Color,
    val tintTrack: Color,
    val selectedTint: Color,
    val scrim: Color,
    val scrimStrong: Color,
    /** False in high contrast, where §3 asks for a pure black background without glows. */
    val ambientGlow: Boolean,
) {
    val focusGradient: Brush = Brush.horizontalGradient(listOf(blue, violet))
    val panel: Color = surface.copy(alpha = 0.6f)
    /** Secondary text on a white (focused) pill, where text_muted would fail contrast. */
    val inkMuted: Color = ink.copy(alpha = 0.7f)
}

val DefaultBrand = BrandColors(
    ink = Color(0xFF0B0E14),
    surface = Color(0xFF161B26),
    surfaceVariant = Color(0xFF1F2633),
    textPrimary = Color(0xFFFFFFFF),
    textMuted = Color(0xFF9AA3B2),
    blue = Color(0xFF4C8DFF),
    violet = Color(0xFF8B5CF6),
    liveRed = Color(0xFFFF3B47),
    error = Color(0xFFFF5A5F),
    focusBorderWidth = 2.dp,
    tintSubtle = Color(0x0DFFFFFF),
    hairline = Color(0x14FFFFFF),
    tintChip = Color(0x1FFFFFFF),
    tintKey = Color(0x33FFFFFF),
    tintTrack = Color(0x22FFFFFF),
    selectedTint = Color(0x2E4C8DFF),
    scrim = Color(0xCC000000),
    scrimStrong = Color(0xE6000000),
    ambientGlow = true,
)

val HighContrastBrand = DefaultBrand.copy(
    ink = Color(0xFF000000),
    surface = Color(0xFF0B0E14),
    surfaceVariant = Color(0xFF161B26),
    textMuted = Color(0xFFFFFFFF),
    focusBorderWidth = 4.dp,
    tintSubtle = Color(0x1FFFFFFF),
    hairline = Color(0x33FFFFFF),
    tintKey = Color(0x4DFFFFFF),
    tintTrack = Color(0x4DFFFFFF),
    selectedTint = Color(0x664C8DFF),
    scrim = Color(0xE6000000),
    scrimStrong = Color(0xF2000000),
    ambientGlow = false,
)

val LocalBrand = staticCompositionLocalOf { DefaultBrand }
