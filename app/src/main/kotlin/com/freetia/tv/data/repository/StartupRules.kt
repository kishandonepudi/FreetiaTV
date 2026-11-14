package com.freetia.tv.data.repository

import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.PlaylistEntity

/** Pure rules from §3.1, kept free of Android types so they are unit-tested on the JVM. */
object StartupRules {

    /**
     * One playlist opens directly. Otherwise: the Settings startup playlist, then the default flag, then priority 1.
     */
    fun select(playlists: List<PlaylistEntity>, settingsStartupId: Long = 0L): PlaylistEntity? {
        val ordered = playlists.sortedBy { it.priority }
        if (ordered.size <= 1) return ordered.firstOrNull()
        return ordered.firstOrNull { it.id == settingsStartupId }
            ?: ordered.firstOrNull { it.isDefault }
            ?: ordered.first()
    }

    /**
     * The chosen playlist first, then the failure fallback: each next priority after it, wrapping to the higher
     * priorities that come before it.
     */
    fun candidates(playlists: List<PlaylistEntity>, settingsStartupId: Long = 0L): List<PlaylistEntity> {
        val ordered = playlists.sortedBy { it.priority }
        val first = select(ordered, settingsStartupId) ?: return emptyList()
        val at = ordered.indexOf(first)
        return ordered.drop(at) + ordered.take(at)
    }

    /**
     * Resume the last channel when the setting is on and it still exists; otherwise (or when it is gone) the first
     * channel. null only for an empty list.
     */
    fun startupChannel(
        channels: List<ChannelEntity>,
        resume: Boolean,
        lastChannelId: Long,
        lastChannelNumber: Int,
    ): ChannelEntity? {
        if (resume) {
            channels.firstOrNull { it.id == lastChannelId }?.let { return it }
            if (lastChannelNumber > 0) channels.firstOrNull { it.number == lastChannelNumber }?.let { return it }
        }
        return channels.firstOrNull()
    }

    /** §3.2: on for the first playlist, off for the rest. */
    fun autoUpdateDefault(existingPlaylistCount: Int): Boolean = existingPlaylistCount == 0

    /** Moves the matching item by [delta] places; unchanged at either end or when nothing matches. */
    fun <T> move(list: List<T>, delta: Int, matches: (T) -> Boolean): List<T> {
        val from = list.indexOfFirst(matches)
        if (from < 0) return list
        val to = from + delta
        if (to !in list.indices) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }
}
