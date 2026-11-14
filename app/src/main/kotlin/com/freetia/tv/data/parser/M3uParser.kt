package com.freetia.tv.data.parser

import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

/**
 * Streaming M3U/M3U8 parser: channels are emitted one at a time so a 10,000+ channel
 * playlist never sits in memory.
 *
 * Playlists are hostile input (SECURITY_STANDARDS S7): lines longer than [MAX_LINE_CHARS] are
 * ignored without being buffered, each tag keeps at most [MAX_ATTRIBUTES] attributes, and only
 * http/https stream URLs are accepted (the app ships no RTMP/RTSP/UDP extensions, and file:// or
 * content:// would let a playlist read local data).
 */
class M3uParser(private val maxChannels: Int = MAX_CHANNELS) {

    data class Header(val tvgUrl: String = "", val attributes: Map<String, String> = emptyMap())

    suspend fun parse(
        input: InputStream,
        onHeader: suspend (Header) -> Unit = {},
        onChannel: suspend (M3uChannel) -> Unit,
    ): Int {
        var count = 0
        var pendingAttrs: Map<String, String>? = null
        var pendingTitle = ""
        var pendingGroup: String? = null
        // Set when an entry's #EXTINF was dropped, so its URL does not become an unnamed channel.
        var skipNextUrl = false

        fun resetEntry() {
            pendingAttrs = null
            pendingTitle = ""
            pendingGroup = null
        }

        BoundedLineReader(InputStreamReader(input, Charsets.UTF_8), MAX_LINE_CHARS).use { reader ->
            var first = true
            while (count < maxChannels) {
                val raw = reader.readLine() ?: break
                val line = (if (first) raw.removePrefix("\uFEFF") else raw).trim()
                first = false
                if (reader.lastLineTruncated) {
                    val isExtinf = line.startsWith("#EXTINF:", ignoreCase = true)
                    if (isExtinf || !line.startsWith("#")) {
                        resetEntry()
                        skipNextUrl = isExtinf
                    }
                    continue
                }
                when {
                    line.isEmpty() -> Unit
                    line.startsWith("#EXTM3U") -> {
                        val attrs = parseAttributes(line.removePrefix("#EXTM3U"))
                        onHeader(Header(attrs["x-tvg-url"] ?: attrs["url-tvg"] ?: "", attrs))
                    }
                    line.startsWith("#EXTINF:", ignoreCase = true) -> {
                        val (attrs, title) = splitExtinf(line.substring(8))
                        pendingAttrs = attrs
                        pendingTitle = title
                        pendingGroup = null
                        skipNextUrl = false
                    }
                    line.startsWith("#EXTGRP:", ignoreCase = true) -> pendingGroup = line.substring(8).trim()
                    line.startsWith("#") -> Unit
                    skipNextUrl || !isAllowedStreamUrl(line) -> {
                        resetEntry()
                        skipNextUrl = false
                    }
                    else -> {
                        val attrs = pendingAttrs ?: emptyMap()
                        count++
                        onChannel(buildChannel(attrs, pendingTitle, pendingGroup, line, count))
                        resetEntry()
                    }
                }
            }
        }
        return count
    }

    /** Splits `-1 tvg-id="a" group-title="News, Info",Channel Name` at the first comma outside quotes. */
    internal fun splitExtinf(body: String): Pair<Map<String, String>, String> {
        var inQuotes = false
        for (i in body.indices) {
            when (body[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) {
                    return parseAttributes(body.substring(0, i)) to body.substring(i + 1).trim()
                }
            }
        }
        return parseAttributes(body) to ""
    }

    internal fun parseAttributes(text: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        for (m in ATTR_REGEX.findAll(text)) {
            if (attrs.size >= MAX_ATTRIBUTES) break
            val value = m.groupValues[2].ifEmpty { m.groupValues[3] }
            attrs[m.groupValues[1].lowercase()] = value.trim()
        }
        return attrs
    }

    internal fun isAllowedStreamUrl(url: String): Boolean {
        val scheme = url.substringBefore("://", missingDelimiterValue = "").lowercase()
        return scheme == "http" || scheme == "https"
    }

    private fun buildChannel(
        attrs: Map<String, String>,
        title: String,
        extGroup: String?,
        url: String,
        index: Int,
    ): M3uChannel {
        val name = title.ifBlank { attrs["tvg-name"].orEmpty() }
            .ifBlank { url.substringAfterLast('/').substringBefore('?') }
        return M3uChannel(
            tvgId = attrs["tvg-id"].orEmpty(),
            tvgName = attrs["tvg-name"].orEmpty(),
            // Logos come from an untrusted playlist too; file:// or content:// would let it read local data via Coil.
            tvgLogo = attrs["tvg-logo"].orEmpty().takeIf(::isAllowedStreamUrl).orEmpty(),
            tvgCountry = attrs["tvg-country"].orEmpty(),
            tvgLanguage = attrs["tvg-language"].orEmpty(),
            tvgType = attrs["tvg-type"].orEmpty(),
            groupTitle = attrs["group-title"] ?: extGroup.orEmpty(),
            name = name,
            streamUrl = url,
            number = attrs["tvg-chno"]?.toIntOrNull() ?: index,
            catchupEnabled = attrs["catchup"].let { it != null && it != "none" },
            catchupDays = attrs["catchup-days"]?.toIntOrNull() ?: 0,
            rawAttributes = attrs,
        )
    }

    /**
     * Line reader that never holds more than [maxChars] of a line: BufferedReader.readLine would buffer a
     * hostile multi-megabyte line in full. Overlong lines return their first [maxChars] characters with
     * [lastLineTruncated] set. Line terminators match BufferedReader (\n, \r, \r\n).
     */
    private class BoundedLineReader(private val reader: Reader, private val maxChars: Int) : AutoCloseable {
        private val buf = CharArray(8 * 1024)
        private var pos = 0
        private var len = 0
        private val line = StringBuilder()
        private var skipLf = false

        var lastLineTruncated = false
            private set

        private fun next(): Int {
            if (pos == len) {
                len = reader.read(buf, 0, buf.size)
                pos = 0
                if (len <= 0) {
                    len = 0
                    return -1
                }
            }
            return buf[pos++].code
        }

        fun readLine(): String? {
            line.setLength(0)
            lastLineTruncated = false
            var sawAny = false
            while (true) {
                val c = next()
                if (c == -1) return if (sawAny) line.toString() else null
                if (skipLf) {
                    skipLf = false
                    if (c == '\n'.code) continue
                }
                sawAny = true
                when (c) {
                    '\n'.code -> return line.toString()
                    '\r'.code -> {
                        skipLf = true
                        return line.toString()
                    }
                    else -> {
                        if (line.length < maxChars) {
                            line.append(c.toChar())
                        } else {
                            lastLineTruncated = true
                        }
                    }
                }
            }
        }

        override fun close() = reader.close()
    }

    companion object {
        internal const val MAX_LINE_CHARS = 8 * 1024
        internal const val MAX_ATTRIBUTES = 64

        /** Hostile playlists are capped; entries after this many channels are ignored. */
        const val MAX_CHANNELS = 100_000
        private val ATTR_REGEX = Regex("""([A-Za-z0-9_-]+)=(?:"([^"]*)"|(\S+))""")
    }
}
