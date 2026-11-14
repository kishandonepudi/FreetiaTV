package com.freetia.tv.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRoundTripTest {
    private val pass = "correct horse".toCharArray()
    private val link = "https://example.com/list.m3u?token=SECRET123"
    private val pinHash = "c2FsdA==:aGFzaA=="

    private val data = BackupData(
        settings = mapOf("text_size" to 125, "high_contrast" to true, "subtitle_bg_opacity" to 0.5f, "aspect_ratio" to "zoom"),
        playlists = listOf(
            BackupPlaylist("India", url = link, userEpgUrl = "https://example.com/guide.xml", isDefault = true, updateOnStartup = true),
            BackupPlaylist("News", url = "http://example.org/news.m3u", refreshMinutes = 360, allowBackgroundPlay = true),
        ),
        favourites = listOf(ChannelRef(0, "ndtv.in", "NDTV"), ChannelRef(1, "", "Local News")),
        groups = listOf(BackupGroup("Kids", listOf(ChannelRef(0, "toon.in", "Toons")))),
        lockedChannels = listOf(ChannelRef(1, "", "Local News")),
        categoryLocks = listOf(CategoryLockRef("group", "Adult")),
        epgSourceUrl = "https://example.com/all.xml",
        pinSecrets = mapOf("safety_pin_hash" to pinHash),
    )

    private fun ok(open: BackupOpen) = (open as BackupOpen.Ok).data

    @Test
    fun encryptedRoundTripKeepsEverything() {
        val text = BackupFiles.write(data, pass, iterations = 10_000)
        assertTrue(BackupFiles.isEncrypted(text))
        assertFalse("links must not appear in the clear", text.contains("SECRET123"))
        assertEquals(data, ok(BackupFiles.open(text, pass)))
    }

    @Test
    fun plainFileHasNoLinksOrSecrets() {
        val text = BackupFiles.write(data, passphrase = null)
        assertFalse(text.contains("SECRET123"))
        assertFalse(text.contains("example.com"))
        assertFalse(text.contains(pinHash))
        val back = ok(BackupFiles.open(text, null))
        assertEquals(data.withoutSecrets(), back)
        assertNull(back.playlists[0].url)
        assertNull(back.pinSecrets)
        assertNull(back.epgSourceUrl)
        assertEquals(listOf("India", "News"), back.playlists.map { it.name })
    }

    @Test
    fun emptyPassphraseCountsAsNone() {
        assertFalse(BackupFiles.isEncrypted(BackupFiles.write(data, CharArray(0))))
    }

    @Test
    fun pinHashIsExcludedUnlessIncludedAndEncrypted() {
        val withoutPin = data.copy(pinSecrets = null)
        val text = BackupFiles.write(withoutPin, pass, iterations = 10_000)
        assertNull(ok(BackupFiles.open(text, pass)).pinSecrets)
        val included = ok(BackupFiles.open(BackupFiles.write(data, pass, iterations = 10_000), pass))
        assertEquals(mapOf("safety_pin_hash" to pinHash), included.pinSecrets)
        // Even when the caller passes it, an unencrypted file never carries it.
        assertFalse(BackupFiles.write(data, null).contains("safety_pin_hash"))
    }

    @Test
    fun encryptedFileAsksForPassphraseAndRejectsWrongOne() {
        val text = BackupFiles.write(data, pass, iterations = 10_000)
        assertEquals(BackupOpen.NeedsPassphrase, BackupFiles.open(text, null))
        val wrong = BackupFiles.open(text, "nope nope".toCharArray())
        assertTrue(wrong is BackupOpen.Invalid)
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val text = BackupFiles.write(data, pass, iterations = 10_000)
        val marker = "\"payload\":\""
        val i = text.indexOf(marker) + marker.length
        val flipped = text.substring(0, i) + (if (text[i] == 'A') 'B' else 'A') + text.substring(i + 1)
        assertTrue(BackupFiles.open(flipped, pass) is BackupOpen.Invalid)
    }

    @Test
    fun hasSecretsReflectsContent() {
        assertTrue(data.hasSecrets)
        assertFalse(data.withoutSecrets().hasSecrets)
    }

    @Test
    fun summaryCountsWhatWillHappen() {
        val summary = ImportPlanner.summarize(data, listOf("india", "Other"), ImportMode.REPLACE)
        assertEquals(1, summary.newPlaylists)
        assertEquals(1, summary.existingPlaylists)
        assertEquals(1, summary.removedPlaylists)
        assertEquals(2, summary.withLinks)
        assertEquals(2, summary.favourites)
        assertEquals(2, summary.locks)
        assertTrue(summary.includesPin)
        assertEquals(0, ImportPlanner.summarize(data, listOf("Other"), ImportMode.MERGE).removedPlaylists)
        assertTrue(summary.lines().any { it.contains("PIN") })
    }

    @Test
    fun syncCodeRoundTripsAndStaysQrFriendly() {
        val compact = BackupFiles.write(data.copy(favourites = null, groups = null, lockedChannels = null, categoryLocks = null), null)
        val code = SyncCode.encode(compact)
        assertTrue(code.startsWith("FTV1"))
        assertTrue("QR alphanumeric charset", code.all { it in 'A'..'Z' || it in '2'..'7' || it in '0'..'9' })
        assertEquals(compact, SyncCode.decode(SyncCode.grouped(code).lowercase()))
        assertEquals(data.playlists.map { it.name }, ok(BackupFiles.open(SyncCode.decode(code)!!, null)).playlists.map { it.name })
    }

    @Test
    fun syncCodeRejectsGarbageAndInflateBombs() {
        assertNull(SyncCode.decode("hello"))
        assertNull(SyncCode.decode("FTV1!!!!"))
        assertNull(SyncCode.decode("FTV1ABCDEFGH"))
        val bomb = SyncCode.encode("a".repeat(MAX_IMPORT_CHARS + 10))
        assertNull(SyncCode.decode(bomb))
    }

    @Test
    fun boundedReadStopsAtTheCap() {
        assertEquals("hi", BackupFiles.readBounded("hi".byteInputStream()))
        assertNull(BackupFiles.readBounded(java.io.ByteArrayInputStream(ByteArray(MAX_IMPORT_CHARS + 1))))
    }
}
