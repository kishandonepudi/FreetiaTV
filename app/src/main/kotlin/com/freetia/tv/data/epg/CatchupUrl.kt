package com.freetia.tv.data.epg

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds a catch-up (timeshift) stream URL from a channel's `catchup` mode and `catchup-source` template (§3.9). */
object CatchupUrl {
    internal val PLACEHOLDER = Regex("""\$?\{([A-Za-z]+)(?::([^}]*))?\}""")
    private const val DEFAULT_DAYS = 7

    fun isAvailable(catchup: String, catchupEnabled: Boolean, catchupDays: Int, start: Long, now: Long): Boolean {
        if (catchup.isBlank() && !catchupEnabled) return false
        if (start > now) return false
        val days = if (catchupDays > 0) catchupDays else DEFAULT_DAYS
        return now - start <= days * DAY_MS
    }

    /** Null when the mode or template cannot make a playable http(s) URL. [startMs] is the programme start. */
    fun build(streamUrl: String, mode: String, source: String, startMs: Long, stopMs: Long, nowMs: Long): String? {
        val m = mode.trim().lowercase(Locale.ROOT)
        val src = source.trim()
        val url: String? = when {
            m == "append" || m == "shift" || (m.isEmpty() && src.isNotEmpty() && !src.startsWith("http")) -> {
                val tail = when {
                    src.isNotEmpty() -> src
                    m == "shift" -> "?utc={utc}&lutc={lutc}"
                    else -> null
                }
                tail?.let { streamUrl + substitute(it, startMs, stopMs, nowMs) }
            }
            m == "flussonic" || m == "fs" ->
                if (src.isNotEmpty()) substitute(src, startMs, stopMs, nowMs) else flussonic(streamUrl, startMs, stopMs)
            m == "xc" || m == "xtream" -> xtream(streamUrl, startMs, stopMs)
            src.isNotEmpty() -> substitute(src, startMs, stopMs, nowMs)
            else -> null
        }
        return url?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
    }

    private fun flussonic(streamUrl: String, startMs: Long, stopMs: Long): String? {
        val utc = startMs / 1000
        val duration = (stopMs - startMs) / 1000
        return when {
            streamUrl.contains("/index.m3u8") -> streamUrl.replace("/index.m3u8", "/archive-$utc-$duration.m3u8")
            streamUrl.contains("/mpegts") -> streamUrl.replace("/mpegts", "/archive-$utc-$duration.ts")
            else -> null
        }
    }

    /** `http://h/live/user/pass/123.ts` becomes `http://h/timeshift/user/pass/{minutes}/{yyyy-MM-dd:HH-mm}/123.ts`. */
    private fun xtream(streamUrl: String, startMs: Long, stopMs: Long): String? {
        val marker = "/live/"
        val i = streamUrl.indexOf(marker)
        if (i < 0) return null
        val rest = streamUrl.substring(i + marker.length).substringBefore('?')
        val parts = rest.split('/')
        if (parts.size < 3) return null
        val minutes = ((stopMs - startMs) / 60_000).coerceAtLeast(1)
        val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm", Locale.ROOT)
            .format(Instant.ofEpochMilli(startMs).atOffset(ZoneOffset.UTC))
        return streamUrl.substring(0, i) + "/timeshift/${parts[0]}/${parts[1]}/$minutes/$stamp/${parts.drop(2).joinToString("/")}"
    }

    /** `{utc}`, `${start}`, `{end}`, `{duration}`, `{duration:60}`, `{offset}`, `{lutc}`, `{timestamp}`, `{Y}`..`{S}` and `{utc:YmdHMS}`. */
    fun substitute(template: String, startMs: Long, stopMs: Long, nowMs: Long): String =
        PLACEHOLDER.replace(template) { match ->
            val name = match.groupValues[1]
            val arg = match.groupValues[2]
            when (name) {
                "utc", "start" -> if (arg.isEmpty()) (startMs / 1000).toString() else formatDate(startMs, arg)
                "end", "utcend" -> if (arg.isEmpty()) (stopMs / 1000).toString() else formatDate(stopMs, arg)
                "duration" -> {
                    val divisor = arg.toLongOrNull()?.takeIf { it > 0 } ?: 1L
                    (((stopMs - startMs) / 1000) / divisor).toString()
                }
                "offset" -> ((nowMs - startMs) / 1000).toString()
                "lutc", "timestamp", "now" -> (nowMs / 1000).toString()
                "Y", "m", "d", "H", "M", "S" -> formatDate(startMs, name)
                else -> match.value
            }
        }

    private fun formatDate(ms: Long, format: String): String {
        val t = Instant.ofEpochMilli(ms).atOffset(ZoneOffset.UTC)
        return buildString {
            for (c in format) append(
                when (c) {
                    'Y' -> "%04d".format(Locale.ROOT, t.year)
                    'm' -> "%02d".format(Locale.ROOT, t.monthValue)
                    'd' -> "%02d".format(Locale.ROOT, t.dayOfMonth)
                    'H' -> "%02d".format(Locale.ROOT, t.hour)
                    'M' -> "%02d".format(Locale.ROOT, t.minute)
                    'S' -> "%02d".format(Locale.ROOT, t.second)
                    else -> c.toString()
                },
            )
        }
    }
}

/** Hands a catch-up URL to the player for one channel; the player takes it once, on its next tune. */
object CatchupRequest {
    @Volatile private var pending: Pair<Long, String>? = null

    fun set(channelId: Long, url: String) { pending = channelId to url }

    /** Drops a request whose tune was cancelled (e.g. at the PIN gate), so a later tune of the channel plays live. */
    fun clear() { pending = null }

    fun take(channelId: Long): String? {
        val p = pending ?: return null
        pending = null
        return if (p.first == channelId) p.second else null
    }
}
