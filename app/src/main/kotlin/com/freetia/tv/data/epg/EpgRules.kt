package com.freetia.tv.data.epg

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.GZIPInputStream

const val DAY_MS = 24L * 60 * 60 * 1000

/** Which programmes the guide keeps: [PAST_MS] back (catch-up, "what was on") to [days] ahead. */
class EpgWindow(val now: Long, val days: Int) {
    val from: Long get() = now - PAST_MS
    val to: Long get() = now + days.coerceIn(1, 14) * DAY_MS

    fun keeps(start: Long, stop: Long): Boolean = stop > from && start < to

    companion object {
        const val PAST_MS = DAY_MS
    }
}

object EpgRules {
    /** Due when never refreshed or older than the interval; a little slack so a periodic run is not skipped by seconds. */
    fun isDue(lastUpdate: Long, intervalHours: Int, now: Long): Boolean =
        lastUpdate <= 0 || now - lastUpdate >= (intervalHours.coerceAtLeast(1) * 3_600_000L - 5 * 60_000L)

    /** http and https only; XMLTV comes from an untrusted source. */
    fun parseUrl(raw: String): HttpUrl? {
        val value = raw.trim()
        if (value.isEmpty() || value.length > 2_048) return null
        return value.toHttpUrlOrNull()
    }
}

class EpgLimitException(message: String) : IOException(message)

/** Fails with [EpgLimitException] once more than [limit] bytes have been read, so a hostile feed cannot fill storage or RAM. */
class BoundedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var count = 0L

    private fun add(n: Int) {
        if (n > 0) {
            count += n
            if (count > limit) throw EpgLimitException("Guide file is too large")
        }
    }

    override fun read(): Int = super.read().also { if (it >= 0) add(1) }
    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { add(it) }
    override fun skip(n: Long): Long = super.skip(n).also { add(it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
}

object EpgStreams {
    const val MAX_DOWNLOAD_BYTES = 200L * 1024 * 1024
    // Inflated size. 7 days x 5,000 channels is well under this; more is not worth the time on a 1 GB TV.
    const val MAX_XML_BYTES = 300L * 1024 * 1024

    /** Sniffs the gzip magic bytes (a .gz name is not trusted) and bounds both the compressed and the inflated size. */
    fun open(raw: InputStream, maxDownload: Long = MAX_DOWNLOAD_BYTES, maxXml: Long = MAX_XML_BYTES): InputStream {
        val buffered = BufferedInputStream(BoundedInputStream(raw, maxDownload), 8 * 1024)
        buffered.mark(2)
        val b0 = buffered.read()
        val b1 = buffered.read()
        buffered.reset()
        val plain = if (b0 == 0x1f && b1 == 0x8b) GZIPInputStream(buffered, 8 * 1024) else buffered
        return BoundedInputStream(plain, maxXml)
    }
}

/** Maps XMLTV channels to playlist channels: tvg-id first, display name as the fallback. */
object EpgMatch {
    fun normalizeName(name: String): String =
        name.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    fun normalizeId(id: String): String = id.trim().lowercase(Locale.ROOT)
}

/** When a reminder fires: the programme start minus the lead time, never in the past for a programme still ahead. */
object ReminderMath {
    const val MIN_DELAY_MS = 5_000L

    fun reminderTime(startTime: Long, leadMinutes: Int): Long = startTime - leadMinutes.coerceIn(0, 60) * 60_000L

    /** Time to arm the alarm for, or null when the programme has already started. */
    fun fireAt(reminderTime: Long, startTime: Long, now: Long): Long? = when {
        startTime <= now -> null
        reminderTime <= now -> now + MIN_DELAY_MS
        else -> reminderTime
    }

    /** After a reboot: a missed reminder is still worth showing while the programme is on (or just about to start). */
    fun isStillRelevant(reminderTime: Long, stopTime: Long, now: Long): Boolean =
        if (stopTime > 0) stopTime > now else now - reminderTime < 15 * 60_000L
}

data class GridCell(val start: Long, val stop: Long, val programmeIndex: Int) {
    val isGap: Boolean get() = programmeIndex < 0
}

/** Programmes of one row clipped to a window, with gap fillers so D-pad selection can land on every moment. */
object GridLayout {
    fun cells(starts: LongArray, stops: LongArray, from: Long, to: Long): List<GridCell> {
        val out = ArrayList<GridCell>(starts.size + 4)
        var cursor = from
        for (i in starts.indices) {
            val s = starts[i]
            val e = stops[i]
            if (e <= from || s >= to) continue
            val cs = maxOf(s, cursor)
            if (e <= cs) continue
            if (cs > cursor) out += GridCell(cursor, cs, -1)
            out += GridCell(cs, minOf(e, to), i)
            cursor = minOf(e, to)
        }
        if (cursor < to) out += GridCell(cursor, to, -1)
        return out
    }
}
