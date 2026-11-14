package com.freetia.tv.data.parser

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset

data class XmltvStats(val channelCount: Int, val programmeCount: Int, val skipped: Int)

class XmltvParser {

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
                setInput(input, null)
            }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "channel" -> {
                            readChannel(parser)?.let { onChannel(it); channels++ } ?: skipped++
                        }
                        "programme" -> {
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
        val id = parser.getAttributeValue(null, "id")
        var displayName = ""
        var icon = ""
        val depth = parser.depth
        while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) break
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "display-name" -> if (displayName.isEmpty()) displayName = readText(parser) else skip(parser)
                "icon" -> { icon = parser.getAttributeValue(null, "src").orEmpty(); skip(parser) }
                else -> skip(parser)
            }
        }
        if (id.isNullOrBlank()) return null
        return XmltvChannel(id = id, displayName = displayName, icon = icon)
    }

    private fun readProgramme(parser: XmlPullParser): XmltvProgramme? {
        val start = parseTime(parser.getAttributeValue(null, "start"))
        val stop = parseTime(parser.getAttributeValue(null, "stop"))
        val channel = parser.getAttributeValue(null, "channel")
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
                "title" -> if (title.isEmpty()) title = readText(parser) else skip(parser)
                "desc" -> if (desc.isEmpty()) desc = readText(parser) else skip(parser)
                "category" -> if (category.isEmpty()) category = readText(parser) else skip(parser)
                "icon" -> { icon = parser.getAttributeValue(null, "src").orEmpty(); skip(parser) }
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

    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        val depth = parser.depth
        while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
            when (parser.eventType) {
                XmlPullParser.TEXT -> sb.append(parser.text)
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return sb.toString().trim()
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
