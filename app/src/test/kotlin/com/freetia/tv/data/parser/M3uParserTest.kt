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

    @Test
    fun bomIsStripped_whenPlaylistStartsWithBom() = runTest {
        val (header, channels) = parse(
            "\uFEFF#EXTM3U x-tvg-url=\"https://example.com/guide.xml\"\n#EXTINF:-1,One\nhttp://x/1.ts\n"
        )
        assertEquals("https://example.com/guide.xml", header?.tvgUrl)
        assertEquals("One", channels.single().name)
    }

    @Test
    fun fileAndContentUrisAreDropped() = runTest {
        val (_, channels) = parse(
            """
            #EXTM3U
            #EXTINF:-1,Local File
            file:///sdcard/secret.ts
            #EXTINF:-1,Content
            content://com.example.provider/item
            #EXTINF:-1,Upper Case File
            FILE:///data/data/x
            #EXTINF:-1,Rtmp
            rtmp://host/live
            #EXTINF:-1,Good
            HTTPS://example.com/good.m3u8
            """.trimIndent()
        )
        assertEquals("Good", channels.single().name)
        assertEquals(1, channels.single().number)
    }

    @Test
    fun dropsLogo_whenSchemeIsNotHttp() = runTest {
        val (_, channels) = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-logo="file:///data/x.png",File Logo
            http://x/1.ts
            #EXTINF:-1 tvg-logo="content://a/b",Content Logo
            http://x/2.ts
            #EXTINF:-1 tvg-logo="https://example.com/logo.png",Web Logo
            http://x/3.ts
            """.trimIndent()
        )
        assertEquals(3, channels.size)
        assertEquals("", channels[0].tvgLogo)
        assertEquals("", channels[1].tvgLogo)
        assertEquals("https://example.com/logo.png", channels[2].tvgLogo)
    }

    @Test
    fun overlongLinesAreIgnored() = runTest {
        val longTitle = "A".repeat(M3uParser.MAX_LINE_CHARS + 10)
        val longUrl = "http://x/" + "b".repeat(M3uParser.MAX_LINE_CHARS)
        val (_, channels) = parse(
            "#EXTM3U\n" +
                "#EXTINF:-1,$longTitle\nhttp://x/dropped-with-its-extinf.ts\n" +
                "#EXTINF:-1,Long Url\n$longUrl\n" +
                "#EXTINF:-1,Kept\r\nhttp://x/kept.ts\r\n"
        )
        assertEquals(1, channels.size)
        assertEquals("Kept", channels[0].name)
        assertEquals("http://x/kept.ts", channels[0].streamUrl)
    }

    @Test
    fun attributeCountIsCapped() = runTest {
        val attrs = (0 until 200).joinToString(" ") { "a$it=\"v$it\"" }
        val (_, channels) = parse("#EXTM3U\n#EXTINF:-1 tvg-id=\"first\" $attrs,Many\nhttp://x/1.ts\n")
        with(channels.single()) {
            assertEquals(M3uParser.MAX_ATTRIBUTES, rawAttributes.size)
            assertEquals("first", tvgId)
            assertEquals("Many", name)
        }
    }

    @Test
    fun stopsAtChannelCap() = runTest {
        val text = buildString {
            appendLine("#EXTM3U")
            repeat(10) { appendLine("#EXTINF:-1,Ch $it"); appendLine("http://example.com/$it.m3u8") }
        }
        val channels = mutableListOf<M3uChannel>()
        val total = M3uParser(maxChannels = 3).parse(text.byteInputStream()) { channels += it }
        assertEquals(3, total)
        assertEquals(listOf("Ch 0", "Ch 1", "Ch 2"), channels.map { it.name })
    }

    @Test
    fun defaultCapIsOneHundredThousand() {
        assertEquals(100_000, M3uParser.MAX_CHANNELS)
    }
}
