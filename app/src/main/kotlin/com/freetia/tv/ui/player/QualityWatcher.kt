package com.freetia.tv.ui.player

/**
 * Turns video height changes under Auto quality into the two spec toasts. Feed it every reported height for the
 * current channel; the first one after tuning only sets the baseline, and ramp-up from a low start is not a "recovery".
 */
class QualityWatcher {
    private var last = 0
    private var dropped = false

    fun reset() { last = 0; dropped = false }

    fun onHeight(height: Int): String? {
        if (height <= 0) return null
        val prev = last
        last = height
        if (prev == 0) return null
        return when {
            height < prev -> { dropped = true; "Network slow. Switched to ${height}p." }
            height > prev && dropped -> { dropped = false; "Network recovered. Back to ${height}p." }
            else -> null
        }
    }
}
