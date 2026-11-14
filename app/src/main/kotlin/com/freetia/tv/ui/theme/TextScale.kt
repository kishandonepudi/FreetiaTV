package com.freetia.tv.ui.theme

import kotlin.math.abs

/** The four text sizes of Settings > Display (§3.14), stored as a percent of the base type scale. */
enum class TextSize(val label: String, val percent: Int) {
    SMALL("Small", 85),
    DEFAULT("Default", 100),
    LARGE("Large", 125),
    EXTRA_LARGE("Extra large", 150);

    companion object {
        /** The closest size, so a value restored from a backup always maps to one of the four. */
        fun fromPercent(percent: Int): TextSize = entries.minBy { abs(it.percent - percent) }
    }
}

/**
 * The font scale to apply: the system accessibility scale times the app's own choice, so both are honoured.
 * Capped so one extreme combination cannot make the UI unusable.
 */
fun effectiveFontScale(systemFontScale: Float, percent: Int): Float =
    (systemFontScale * percent / 100f).coerceIn(0.5f, 3f)
