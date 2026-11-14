package com.freetia.tv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.freetia.tv.FreetiaApp
import com.freetia.tv.core.safety.ChannelFilter
import com.freetia.tv.data.database.ChannelRef

/**
 * The single place channel Up/Down is decided. Wraps around the list and keeps moving past channels [skip] rejects
 * (adult under Child Safe Mode, locked without a session unlock: [rememberRefStepSkip]), so callers must
 * never index into the list themselves. null when every channel is skipped.
 */
fun nextChannel(
    channels: List<ChannelRef>,
    currentId: Long,
    delta: Int,
    skip: (ChannelRef) -> Boolean = { false },
): ChannelRef? {
    if (channels.isEmpty() || delta == 0) return null
    val i = channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val step = if (delta > 0) 1 else -1
    var pos = i + delta
    repeat(channels.size) {
        val candidate = channels[pos.mod(channels.size)]
        if (!skip(candidate)) return candidate
        pos += step
    }
    return null
}

object NumberEntry {
    const val MAX_DIGITS = 4

    fun append(digits: String, digit: Int): String = (digits + digit).takeLast(MAX_DIGITS)

    fun resolve(channels: List<ChannelRef>, digits: String): ChannelRef? {
        val n = digits.toIntOrNull() ?: return null
        return channels.firstOrNull { it.number == n }
    }
}

/** ChannelRef version of `ui.safety.rememberStepSkip`; same rules via [ChannelFilter.skipWhenStepping]. */
@Composable
fun rememberRefStepSkip(playlistId: Long): (ChannelRef) -> Boolean {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val locked by remember(playlistId) { app.safety.lockedIds(playlistId) }.collectAsState(emptySet())
    val childSafe by app.safety.childSafeMode.collectAsState(false)
    return { ChannelFilter.skipWhenStepping(it, childSafe, locked, app.safety.session.isUnlocked()) }
}
