package com.freetia.tv.data.epg

import com.freetia.tv.data.parser.XmltvLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class EpgRulesTest {

    @Test
    fun url_onlyHttpAndHttps() {
        assertNotNull(EpgRules.parseUrl("https://example.com/epg.xml.gz"))
        assertNotNull(EpgRules.parseUrl("  http://example.com/epg.xml "))
        assertNull(EpgRules.parseUrl("file:///sdcard/epg.xml"))
        assertNull(EpgRules.parseUrl("ftp://example.com/epg.xml"))
        assertNull(EpgRules.parseUrl("javascript:alert(1)"))
        assertNull(EpgRules.parseUrl("content://media/external/file"))
        assertNull(EpgRules.parseUrl(""))
        assertNull(EpgRules.parseUrl("https://example.com/" + "a".repeat(3_000)))
    }

    @Test
    fun fieldCaps_clipAndDropBadIcons() {
        assertEquals("abc", XmltvLimits.clip("abcdef", 3))
        assertEquals("abc", XmltvLimits.clip("abc", 3))
        assertEquals("", XmltvLimits.safeIcon("javascript:alert(1)", 500))
        assertEquals("", XmltvLimits.safeIcon("file:///x.png", 500))
        assertEquals("", XmltvLimits.safeIcon("http://x/" + "a".repeat(600), 500))
        assertEquals("https://x/i.png", XmltvLimits.safeIcon(" https://x/i.png ", 500))
    }

    @Test
    fun boundedStream_failsPastTheLimit() {
        val stream = BoundedInputStream(ByteArrayInputStream(ByteArray(100)), 50)
        try {
            stream.readBytes()
            fail("expected EpgLimitException")
        } catch (e: EpgLimitException) {
            assertTrue(e.message!!.isNotEmpty())
        }
    }

    @Test
    fun boundedStream_allowsExactlyTheLimit() {
        assertEquals(50, BoundedInputStream(ByteArrayInputStream(ByteArray(50)), 50).readBytes().size)
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    @Test
    fun open_readsPlainAndGzipByMagicBytes() {
        val xml = "<tv><channel id=\"a\"/></tv>".toByteArray()
        assertEquals(String(xml), String(EpgStreams.open(ByteArrayInputStream(xml)).readBytes()))
        assertEquals(String(xml), String(EpgStreams.open(ByteArrayInputStream(gzip(xml))).readBytes()))
    }

    @Test
    fun open_boundsInflatedSize() {
        val bomb = gzip(ByteArray(1_000_000))
        val stream = EpgStreams.open(ByteArrayInputStream(bomb), maxDownload = 10_000, maxXml = 100_000)
        try {
            stream.readBytes()
            fail("expected EpgLimitException")
        } catch (e: EpgLimitException) {
            assertTrue(true)
        }
    }

    @Test
    fun open_boundsDownloadSize() {
        // The first buffered read already exceeds the cap, so opening itself fails.
        try {
            EpgStreams.open(ByteArrayInputStream(ByteArray(5_000) { 120 }), maxDownload = 1_000, maxXml = 100_000).readBytes()
            fail("expected EpgLimitException")
        } catch (e: EpgLimitException) {
            assertTrue(true)
        }
    }

    @Test
    fun open_emptyStreamIsFine() {
        assertEquals(0, EpgStreams.open(ByteArrayInputStream(ByteArray(0))).readBytes().size)
    }

    @Test
    fun window_keepsPastDayAndConfiguredDaysAhead() {
        val now = 10 * DAY_MS
        val w = EpgWindow(now, 7)
        assertEquals(now - DAY_MS, w.from)
        assertEquals(now + 7 * DAY_MS, w.to)
        assertTrue(w.keeps(now - 1_000, now + 1_000))
        assertTrue(w.keeps(now - DAY_MS - 1_000, now - DAY_MS + 1_000))
        assertFalse(w.keeps(now - 2 * DAY_MS, now - DAY_MS))
        assertFalse(w.keeps(now + 7 * DAY_MS, now + 7 * DAY_MS + 1_000))
        assertTrue(w.keeps(now + 7 * DAY_MS - 1, now + 7 * DAY_MS + 3_600_000))
    }

    @Test
    fun window_clampsDays() {
        val now = DAY_MS
        assertEquals(now + DAY_MS, EpgWindow(now, 0).to)
        assertEquals(now + 14 * DAY_MS, EpgWindow(now, 90).to)
    }

    @Test
    fun refreshDue_whenNeverOrOlderThanInterval() {
        val h = 3_600_000L
        assertTrue(EpgRules.isDue(0, 12, 100 * h))
        assertFalse(EpgRules.isDue(95 * h, 12, 100 * h))
        assertTrue(EpgRules.isDue(88 * h, 12, 100 * h))
        assertTrue(EpgRules.isDue(99 * h, 0, 100 * h))
    }

    @Test
    fun nameMatching_ignoresCaseAndPunctuation() {
        assertEquals(EpgMatch.normalizeName("Star Plus HD"), EpgMatch.normalizeName("star-plus  hd"))
        assertEquals("abc.in", EpgMatch.normalizeId(" ABC.in "))
    }

    @Test
    fun reminderTime_subtractsLead() {
        val start = 1_000_000_000L
        assertEquals(start - 120_000, ReminderMath.reminderTime(start, 2))
        assertEquals(start, ReminderMath.reminderTime(start, 0))
        assertEquals(start - 3_600_000, ReminderMath.reminderTime(start, 500))
    }

    @Test
    fun fireAt_futureLateAndStarted() {
        val start = 10_000_000L
        val reminder = ReminderMath.reminderTime(start, 2)
        assertEquals(reminder, ReminderMath.fireAt(reminder, start, now = reminder - 1))
        assertEquals(reminder + 1_000 + ReminderMath.MIN_DELAY_MS, ReminderMath.fireAt(reminder, start, now = reminder + 1_000))
        assertNull(ReminderMath.fireAt(reminder, start, now = start))
    }

    @Test
    fun missedReminder_isRelevantWhileProgrammeIsOn() {
        assertTrue(ReminderMath.isStillRelevant(1_000, stopTime = 5_000_000, now = 2_000_000))
        assertFalse(ReminderMath.isStillRelevant(1_000, stopTime = 5_000_000, now = 6_000_000))
        assertTrue(ReminderMath.isStillRelevant(1_000_000, stopTime = 0, now = 1_000_000 + 60_000))
        assertFalse(ReminderMath.isStillRelevant(1_000_000, stopTime = 0, now = 1_000_000 + 3_600_000))
    }

    @Test
    fun gridCells_fillGapsAndClipToWindow() {
        val starts = longArrayOf(0, 100, 300)
        val stops = longArrayOf(100, 200, 500)
        val cells = GridLayout.cells(starts, stops, from = 50, to = 400)
        assertEquals(
            listOf(GridCell(50, 100, 0), GridCell(100, 200, 1), GridCell(200, 300, -1), GridCell(300, 400, 2)),
            cells,
        )
        assertTrue(cells[2].isGap)
    }

    @Test
    fun gridCells_emptyRowIsOneGap() {
        assertEquals(listOf(GridCell(0, 10, -1)), GridLayout.cells(LongArray(0), LongArray(0), 0, 10))
    }

    @Test
    fun gridCells_overlappingProgrammeDoesNotGoBackwards() {
        val cells = GridLayout.cells(longArrayOf(0, 50), longArrayOf(100, 150), 0, 200)
        assertEquals(listOf(GridCell(0, 100, 0), GridCell(100, 150, 1), GridCell(150, 200, -1)), cells)
    }

    @Test
    fun likeEscape_neutralisesWildcards() {
        assertEquals("100\\%\\_x\\\\", EpgRepository.escapeLike("100%_x\\"))
    }
}
