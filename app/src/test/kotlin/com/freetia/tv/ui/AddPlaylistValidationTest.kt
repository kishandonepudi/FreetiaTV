package com.freetia.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AddPlaylistValidationTest {

    @Test
    fun accepts_whenHttpsOrHttpLink() {
        assertNull(playlistUrlError("https://example.com/list.m3u"))
        assertNull(playlistUrlError("  http://example.com:8080/get.php?user=a&type=m3u_plus  "))
        assertNull(playlistUrlError("HTTPS://EXAMPLE.COM/list.m3u8"))
    }

    @Test
    fun rejects_whenBlankOrOtherScheme() {
        assertNotNull(playlistUrlError(""))
        assertNotNull(playlistUrlError("   "))
        assertNotNull(playlistUrlError("example.com/list.m3u"))
        assertNotNull(playlistUrlError("file:///sdcard/list.m3u"))
        assertNotNull(playlistUrlError("content://media/list.m3u"))
        assertNotNull(playlistUrlError("ftp://example.com/list.m3u"))
        assertNotNull(playlistUrlError("https://"))
        assertNotNull(playlistUrlError("https://exa mple.com/list.m3u"))
    }

    @Test
    fun rejects_whenLongerThanCap() {
        val base = "https://example.com/"
        assertNull(playlistUrlError(base + "a".repeat(MAX_PLAYLIST_URL_LENGTH - base.length)))
        assertNotNull(playlistUrlError(base + "a".repeat(MAX_PLAYLIST_URL_LENGTH)))
    }

    @Test
    fun defaultName_isHostWithoutWww() {
        assertEquals("example.com", defaultPlaylistName("https://www.example.com/list.m3u"))
        assertEquals("tv.example.org", defaultPlaylistName("http://tv.example.org:8080/x?token=1"))
        assertEquals("My playlist", defaultPlaylistName("not a link"))
    }
}
