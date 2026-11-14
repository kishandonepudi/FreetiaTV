package com.freetia.tv.playback

import com.freetia.tv.data.settings.BufferMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferValuesTest {
    @Test fun presets() {
        assertEquals(2_000, bufferValues(BufferMode.LOW_LATENCY).minMs)
        assertEquals(10_000, bufferValues(BufferMode.BALANCED).minMs)
        assertEquals(30_000, bufferValues(BufferMode.STABLE).minMs)
    }

    @Test fun customIsClampedToLoadControlRules() {
        val v = bufferValues(BufferMode.CUSTOM, customMinMs = 500, customMaxMs = 100)
        assertEquals(1_000, v.minMs)
        assertTrue(v.maxMs >= v.minMs)
        assertTrue(v.playbackMs <= v.minMs && v.rebufferMs <= v.minMs)
        assertEquals(20_000, bufferValues(BufferMode.CUSTOM, 15_000, 20_000).maxMs)
    }
}
