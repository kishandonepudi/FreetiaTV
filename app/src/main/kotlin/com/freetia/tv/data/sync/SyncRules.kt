package com.freetia.tv.data.sync

/** Pure sync decisions (§3.2), unit-tested on the JVM. */
object SyncPolicy {
    const val MIN_REFRESH_INTERVAL_MS = 10 * 60_000L

    /** True when the last successful update is under 10 minutes old and the user did not force a refresh. */
    fun shouldSkip(force: Boolean, lastUpdateSuccess: Boolean, lastUpdateTime: Long, now: Long): Boolean {
        if (force || !lastUpdateSuccess) return false
        return (now - lastUpdateTime) in 0 until MIN_REFRESH_INTERVAL_MS
    }

    /** Periodic background refresh: needs auto-update on, a non-zero interval and a stale enough copy. */
    fun dueInBackground(
        updateOnStartup: Boolean,
        intervalMinutes: Int,
        lastUpdateTime: Long,
        now: Long,
    ): Boolean = updateOnStartup && intervalMinutes > 0 && now - lastUpdateTime >= intervalMinutes * 60_000L
}

/**
 * Tracks which cached channels (keyed by stream URL) a streamed refresh matched, so it can report how many
 * channels were added and which ones disappeared without holding the new playlist in memory.
 */
class ChannelDiff(existing: Iterable<Pair<String, Long>>) {
    constructor(existing: Map<String, Long>) : this(existing.toList())

    // Playlists may repeat a stream URL on several channels; each cached row must match at most one new entry.
    private val remaining = HashMap<String, ArrayDeque<Long>>().also { m -> existing.forEach { (url, id) -> m.getOrPut(url) { ArrayDeque() }.addLast(id) } }
    var added = 0
        private set

    /** The cached row id for [streamUrl], or null when it is new (counted as added). */
    fun match(streamUrl: String): Long? {
        val queue = remaining[streamUrl]
        val id = queue?.removeFirstOrNull()
        if (queue != null && queue.isEmpty()) remaining.remove(streamUrl)
        if (id == null) added++
        return id
    }

    val removed: Int get() = remaining.values.sumOf { it.size }
    val removedIds: Collection<Long> get() = remaining.values.flatten()
}
