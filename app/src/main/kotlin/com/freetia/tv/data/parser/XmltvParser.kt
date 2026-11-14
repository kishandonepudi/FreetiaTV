package com.freetia.tv.data.parser

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset

data class XmltvStats(val channelCount: Int, val programmeCount: Int, val skipped: Int)

/** Untrusted-input bounds. Text is clipped, never rejected, so one long field cannot drop a programme. */
data class XmltvLimits(
    val maxId: Int = 200,
    val maxName: Int = 200,
    val maxTitle: Int = 200,
    val maxDescription: Int = 600,
    val maxCategory: Int = 80,
    val maxIcon: Int = 500,
    val maxChannels: Int = 30_000,
    val maxProgrammes: Int = 3_000_000,
) {
    companion object {
        val DEFAULT = XmltvLimits()

        fun clip(text: String, max: Int): String = if (text.length <= max) text else text.substring(0, max)

        /** Icons are only loaded over http(s); anything else, or an oversize URL, is dropped. */
        fun safeIcon(src: String?, max: Int): String {
            val value = src?.trim().orEmpty()
            if (value.isEmpty() || value.length > max) return ""
            return if (value.startsWith("http://", true) || value.startsWith("https://", true)) value else ""
        }
    }
}

class XmlDoctypeException(message: String) : IOException(message)

/**
 * XMLTV needs no DTD, so a DOCTYPE (and with it any entity declaration) is refused before the XML parser sees it:
 * no external entities and no entity-expansion bombs. Only the prolog is scanned, up to [MAX_PROLOG_BYTES]; NUL
 * bytes are skipped so a UTF-16 file is read the same way.
 */
internal object XmlDoctypeGuard {
    const val MAX_PROLOG_BYTES = 64 * 1024

    fun wrap(input: InputStream): InputStream {
        val buffered = BufferedInputStream(input, 8 * 1024)
        buffered.mark(MAX_PROLOG_BYTES + 16)
        check(buffered)
        buffered.reset()
        return buffered
    }

    private fun check(input: InputStream) {
        var read = 0
        fun next(): Int {
            while (true) {
                val b = input.read()
                if (b < 0) return b
                if (++read > MAX_PROLOG_BYTES) throw XmlDoctypeException("Guide file header is too long")
                if (b != 0) return b
            }
        }
        fun skipPast(end: String) {
            var matched = 0
            while (matched < end.length) {
                val b = next()
                if (b < 0) return
                matched = when {
                    b == end[matched].code -> matched + 1
                    // "--->" still ends a comment: an extra '-' keeps the "--" already seen.
                    matched == 2 && end == "-->" && b == '-'.code -> 2
                    b == end[0].code -> 1
                    else -> 0
                }
            }
        }
        while (true) {
            val b = next()
            when {
                b < 0 -> return
                b == '<'.code -> when (next()) {
                    '?'.code -> skipPast("?>")
                    '!'.code -> {
                        if (next() == '-'.code && next() == '-'.code) skipPast("-->")
                        else throw XmlDoctypeException("Guide files with a DOCTYPE are not supported")
                    }
                    else -> return
                }
                // BOM bytes and whitespace before the first tag.
                b == 0xEF || b == 0xBB || b == 0xBF || b == 0xFE || b == 0xFF || b.toChar().isWhitespace() -> Unit
                else -> return
            }
        }
    }
}

class XmltvParser(private val limits: XmltvLimits = XmltvLimits.DEFAULT) {

    suspend fun parse(
        input: InputStream,
        onChannel: suspend (XmltvChannel) -> Unit,
        onProgramme: suspend (XmltvProgramme) -> Unit,
        onProgress: (programmesSoFar: Int) -> Unit = {}
    ): ParseResult<XmltvStats> = withContext(Dispatchers.IO) {
        var channels = 0
        var programmes = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        try {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(XmlDoctypeGuard.wrap(input), null)
            }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "channel" -> {
                            if (channels >= limits.maxChannels) {
                                errors.add("Channel limit reached")
                                break
                            }
                            readChannel(parser)?.let { onChannel(it); channels++ } ?: skipped++
                        }
                        "programme" -> {
                            if (programmes >= limits.maxProgrammes) {
                                errors.add("Programme limit reached")
                                break
                            }
                            val p = readProgramme(parser)
                            if (p != null) {
                                onProgramme(p)
                                programmes++
                                if (programmes % 1000 == 0) {
                                    ensureActive()
                                    onProgress(programmes)
                                }
                            } else {
                                skipped++
                                if (errors.size < 20) errors.add("Skipped programme near line ${parser.lineNumber}")
                            }
                        }
                    }
                }
                event = parser.next()
            }
            val stats = XmltvStats(channels, programmes, skipped)
            if (errors.isEmpty()) ParseResult.Success(stats) else ParseResult.Partial(stats, errors)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ParseResult.Error(e, XmltvStats(channels, programmes, skipped))
        }
    }

    private fun readChannel(parser: XmlPullParser): XmltvChannel? {
        val id = parser.getAttributeValue(null, "id")?.let { XmltvLimits.clip(it.trim(), limits.maxId) }
        var displayName = ""
        var icon = ""
        val depth = parser.depth
        while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) break
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "display-name" -> if (displayName.isEmpty()) displayName = readText(parser, limits.maxName) else skip(parser)
                "icon" -> { icon = XmltvLimits.safeIcon(parser.getAttributeValue(null, "src"), limits.maxIcon); skip(parser) }
                else -> skip(parser)
            }
        }
        if (id.isNullOrBlank()) return null
        return XmltvChannel(id = id, displayName = displayName, icon = icon)
    }

    private fun readProgramme(parser: XmlPullParser): XmltvProgramme? {
        val start = parseTime(parser.getAttributeValue(null, "start"))
        val stop = parseTime(parser.getAttributeValue(null, "stop"))
        val channel = parser.getAttributeValue(null, "channel")?.let { XmltvLimits.clip(it.trim(), limits.maxId) }
        var title = ""
        var desc = ""
        var category = ""
        var icon = ""
        var previouslyShown = false
        val depth = parser.depth
        while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) break
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "title" -> if (title.isEmpty()) title = readText(parser, limits.maxTitle) else skip(parser)
                "desc" -> if (desc.isEmpty()) desc = readText(parser, limits.maxDescription) else skip(parser)
                "category" -> if (category.isEmpty()) category = readText(parser, limits.maxCategory) else skip(parser)
                "icon" -> { icon = XmltvLimits.safeIcon(parser.getAttributeValue(null, "src"), limits.maxIcon); skip(parser) }
                "previously-shown" -> { previouslyShown = true; skip(parser) }
                else -> skip(parser)
            }
        }
        if (start == null || stop == null || channel.isNullOrBlank() || stop <= start) return null
        return XmltvProgramme(
            start = start,
            stop = stop,
            channel = channel,
            title = title,
            description = desc,
            category = category,
            icon = icon,
            length = ((stop - start) / 60_000).toInt(),
            previouslyShown = previouslyShown
        )
    }

    private fun readText(parser: XmlPullParser, max: Int): String {
        val sb = StringBuilder()
        val depth = parser.depth
        while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
            when (parser.eventType) {
                XmlPullParser.TEXT -> if (sb.length < max) sb.append(parser.text)
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return XmltvLimits.clip(sb.toString().trim(), max)
    }

    private fun skip(parser: XmlPullParser) {
        var level = 1
        while (level > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> level++
                XmlPullParser.END_TAG -> level--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    companion object {
        /** Parses XMLTV times such as "20261114183000 +0530"; a missing offset is treated as UTC. */
        fun parseTime(raw: String?): Long? {
            val value = raw?.trim() ?: return null
            if (value.length < 12) return null
            val digits = value.takeWhile { it.isDigit() }
            if (digits.length < 12) return null
            return try {
                val dt = LocalDateTime.of(
                    digits.substring(0, 4).toInt(),
                    digits.substring(4, 6).toInt(),
                    digits.substring(6, 8).toInt(),
                    digits.substring(8, 10).toInt(),
                    digits.substring(10, 12).toInt(),
                    if (digits.length >= 14) digits.substring(12, 14).toInt() else 0
                )
                val offsetPart = value.substring(digits.length).trim()
                val offset = if (offsetPart.length >= 5 && (offsetPart[0] == '+' || offsetPart[0] == '-')) {
                    val sign = if (offsetPart[0] == '-') -1 else 1
                    val h = offsetPart.substring(1, 3).toInt()
                    val m = offsetPart.substring(3, 5).toInt()
                    ZoneOffset.ofHoursMinutes(sign * h, sign * m)
                } else {
                    ZoneOffset.UTC
                }
                dt.toInstant(offset).toEpochMilli()
            } catch (e: Exception) {
                null
            }
        }
    }
}
