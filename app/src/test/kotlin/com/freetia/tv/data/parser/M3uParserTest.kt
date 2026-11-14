package com.freetia.tv.data.parser

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class M3uParserTest {

    private suspend fun parse(text: String): Pair<M3uParser.Header?, List<M3uChannel>> {
        var header: M3uParser.Header? = null
        val channels = mutableListOf<M3uChannel>()
        M3uParser().parse(text.byteInputStream(), onHeader = { header = it }) { channels += it }
        return header to channels
    }

    @Test
    fun parsesIptvOrgStyleEntries() = runTest {
        val (header, channels) = parse(
            """
            #EXTM3U x-tvg-url="https://example.com/guide.xml"
            #EXTINF:-1 tvg-id="AajTak.in@SD" tvg-logo="https://i.imgur.com/a.png" group-title="News",Aaj Tak (720p)
            http://example.com/aajtak/index.m3u8
            #EXTINF:-1 tvg-id="DDNational.in" group-title="General;News",DD National (1080p) [Not 24/7]
            #EXTVLCOPT:http-user-agent=Mozilla/5.0
            https://example.com/dd.m3u8
            """.trimIndent()
        )
        assertEquals("https://example.com/guide.xml", header?.tvgUrl)
        assertEquals(2, channels.size)
        with(channels[0]) {
            assertEquals("Aaj Tak (720p)", name)
            assertEquals("AajTak.in@SD", tvgId)
            assertEquals("https://i.imgur.com/a.png", tvgLogo)
            assertEquals("News", groupTitle)
            assertEquals("http://example.com/aajtak/index.m3u8", streamUrl)
            assertEquals(1, number)
        }
        assertEquals("DD National (1080p) [Not 24/7]", channels[1].name)
        assertEquals(2, channels[1].number)
    }

    @Test
    fun commaInsideQuotedAttributeDoesNotSplitTitle() = runTest {
        val (_, channels) = parse(
            "#EXTM3U\n#EXTINF:-1 group-title=\"News, Info\" tvg-chno=\"42\",Channel One\nhttp://x/1.ts\n"
        )
        assertEquals("Channel One", channels.single().name)
        assertEquals("News, Info", channels.single().groupTitle)
        assertEquals(42, channels.single().number)
    }

    @Test
    fun urlWithoutExtinfStillBecomesChannel() = runTest {
        val (_, channels) = parse("#EXTM3U\nhttp://host/live/stream.m3u8?token=abc\n")
        assertEquals("stream.m3u8", channels.single().name)
    }
}
