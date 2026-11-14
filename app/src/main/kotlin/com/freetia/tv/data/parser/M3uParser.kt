package com.freetia.tv.data.parser

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Streaming M3U/M3U8 parser: channels are emitted one at a time so a 10,000+ channel
 * playlist never sits in memory.
 */
class M3uParser {

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

        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
            while (true) {
                val raw = reader.readLine() ?: break
                val line = raw.trim().removePrefix("﻿")
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
                    }
                    line.startsWith("#EXTGRP:", ignoreCase = true) -> pendingGroup = line.substring(8).trim()
                    line.startsWith("#") -> Unit
                    else -> {
                        val attrs = pendingAttrs ?: emptyMap()
                        count++
                        onChannel(buildChannel(attrs, pendingTitle, pendingGroup, line, count))
                        pendingAttrs = null
                        pendingTitle = ""
                        pendingGroup = null
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
        ATTR_REGEX.findAll(text).forEach { m ->
            val value = m.groupValues[2].ifEmpty { m.groupValues[3] }
            attrs[m.groupValues[1].lowercase()] = value.trim()
        }
        return attrs
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
            tvgLogo = attrs["tvg-logo"].orEmpty(),
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

    companion object {
        private val ATTR_REGEX = Regex("""([A-Za-z0-9_-]+)=(?:"([^"]*)"|(\S+))""")
    }
}
