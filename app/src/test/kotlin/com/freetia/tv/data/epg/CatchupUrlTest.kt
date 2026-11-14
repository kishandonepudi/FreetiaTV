package com.freetia.tv.data.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchupUrlTest {
    private val start = 1_700_000_000_000L
    private val stop = start + 3_600_000L
    private val now = start + 2 * 3_600_000L
    private val stream = "http://h.example/live/user/pass/42.ts"

    @Test
    fun defaultMode_substitutesCommonPlaceholders() {
        val url = CatchupUrl.build(
            stream, "default", "http://h.example/c?s={utc}&e={utcend}&d={duration}&o=\${offset}&t={timestamp}",
            start, stop, now,
        )
        assertEquals("http://h.example/c?s=1700000000&e=1700003600&d=3600&o=7200&t=1700007200", url)
    }

    @Test
    fun startAndLutcAliases() {
        assertEquals("1700000000", CatchupUrl.substitute("\${start}", start, stop, now))
        assertEquals("1700000000", CatchupUrl.substitute("{start}", start, stop, now))
        assertEquals("1700007200", CatchupUrl.substitute("{lutc}", start, stop, now))
    }

    @Test
    fun durationDivisor() {
        assertEquals("60", CatchupUrl.substitute("{duration:60}", start, stop, now))
    }

    @Test
    fun dateComponentsAreUtc() {
        assertEquals("20231114-221320", CatchupUrl.substitute("{Y}{m}{d}-{H}{M}{S}", start, stop, now))
        assertEquals("20231114221320", CatchupUrl.substitute("{utc:YmdHMS}", start, stop, now))
    }

    @Test
    fun appendMode_addsTemplateToStreamUrl() {
        val url = CatchupUrl.build("http://h/s.m3u8", "append", "?utc={utc}&lutc={lutc}", start, stop, now)
        assertEquals("http://h/s.m3u8?utc=1700000000&lutc=1700007200", url)
    }

    @Test
    fun appendMode_withoutTemplate_isNull() {
        assertNull(CatchupUrl.build("http://h/s.m3u8", "append", "", start, stop, now))
    }

    @Test
    fun shiftMode_usesBuiltInTemplate() {
        assertEquals(
            "http://h/s?utc=1700000000&lutc=1700007200",
            CatchupUrl.build("http://h/s", "shift", "", start, stop, now),
        )
    }

    @Test
    fun flussonic_rewritesIndexPlaylist() {
        assertEquals(
            "http://h/chan/archive-1700000000-3600.m3u8",
            CatchupUrl.build("http://h/chan/index.m3u8", "flussonic", "", start, stop, now),
        )
        assertNull(CatchupUrl.build("http://h/chan/other.m3u8", "flussonic", "", start, stop, now))
    }

    @Test
    fun xtream_buildsTimeshiftPath() {
        assertEquals(
            "http://h.example/timeshift/user/pass/60/2023-11-14:22-13/42.ts",
            CatchupUrl.build(stream, "xc", "", start, stop, now),
        )
        assertNull(CatchupUrl.build("http://h/stream.ts", "xc", "", start, stop, now))
    }

    @Test
    fun nonHttpResult_isRejected() {
        assertNull(CatchupUrl.build(stream, "default", "rtsp://h/{utc}", start, stop, now))
        assertNull(CatchupUrl.build(stream, "default", "file:///sdcard/{utc}", start, stop, now))
    }

    @Test
    fun unknownPlaceholdersStayAsTheyAre() {
        assertEquals("{catchup-id}", CatchupUrl.substitute("{catchup-id}", start, stop, now))
    }

    @Test
    fun availability_needsTagFinishedStartAndWindow() {
        assertFalse(CatchupUrl.isAvailable("", false, 0, start, now))
        assertTrue(CatchupUrl.isAvailable("append", false, 0, start, now))
        assertTrue(CatchupUrl.isAvailable("", true, 3, start, now))
        assertFalse(CatchupUrl.isAvailable("append", false, 0, now + 1, now))
        assertFalse(CatchupUrl.isAvailable("append", false, 1, start, start + 2 * DAY_MS))
    }

    @Test
    fun request_isTakenOnceForItsChannel() {
        CatchupRequest.set(7, "http://x/y")
        assertNull(CatchupRequest.take(8))
        CatchupRequest.set(7, "http://x/y")
        assertEquals("http://x/y", CatchupRequest.take(7))
        assertNull(CatchupRequest.take(7))
    }

    // Android's ICU regex rejects a bare "}" that the JVM accepts, which crashed the guide dialog on a device.
    @Test
    fun placeholderPatternEscapesClosingBrace() {
        val outsideClass = CatchupUrl.PLACEHOLDER.pattern.replace(Regex("""\[[^\]]*]"""), "")
        val bare = outsideClass.indices.filter { outsideClass[it] == '}' && (it == 0 || outsideClass[it - 1] != '\\') }
        assertEquals(emptyList<Int>(), bare)
    }
}
