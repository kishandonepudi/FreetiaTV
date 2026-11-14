package com.freetia.tv.ui.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportTest {
    private val device = DeviceInfo("1.0.0", "release/foss", "14 (API 34)", "Amazon AFTSS")

    @Test
    fun labelsCommonCodecs() {
        assertEquals("H.264", DiagnosticsReport.codecLabel("video/avc"))
        assertEquals("H.265 (HEVC)", DiagnosticsReport.codecLabel("video/hevc"))
        assertEquals("Unknown", DiagnosticsReport.codecLabel(null))
        assertEquals("foo", DiagnosticsReport.codecLabel("video/foo"))
        assertEquals("1920x1080", DiagnosticsReport.resolution(1920, 1080))
        assertEquals("Unknown", DiagnosticsReport.resolution(0, 0))
    }

    @Test
    fun reportHasTheNumbersAndNoLinks() {
        val text = DiagnosticsReport.build(device, PlaybackStats("H.264", "1280x720", 2500, 3, 12_500))
        assertTrue(text.contains("Video codec: H.264"))
        assertTrue(text.contains("Resolution: 1280x720"))
        assertTrue(text.contains("Bitrate: 2500 kbps"))
        assertTrue(text.contains("Dropped frames: 3"))
        assertTrue(text.contains("Buffer: 12.5 s"))
        assertTrue(text.contains("App version: 1.0.0 (release/foss)"))
        assertTrue(text.contains("Device: Amazon AFTSS"))
        assertFalse(text.contains("http"))
    }

    @Test
    fun reportWithoutPlayback() {
        assertTrue(DiagnosticsReport.build(device, null).contains("nothing was playing"))
    }
}
