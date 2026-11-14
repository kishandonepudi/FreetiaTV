package com.freetia.tv.ui.player

import com.freetia.tv.data.database.ChannelRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerLogicTest {
    private fun ch(id: Long, number: Int) = ChannelRef(id = id, name = "C$id", number = number)
    private val list = listOf(ch(1, 1), ch(2, 2), ch(3, 3))

    @Test fun stepperWrapsBothWays() {
        assertEquals(2L, nextChannel(list, 1, +1)?.id)
        assertEquals(1L, nextChannel(list, 3, +1)?.id)
        assertEquals(3L, nextChannel(list, 1, -1)?.id)
    }

    @Test fun stepperHandlesEmptyAndUnknown() {
        assertNull(nextChannel(emptyList(), 1, 1))
        assertEquals(2L, nextChannel(list, 99, +1)?.id)
    }

    @Test fun numberEntryKeepsLastFourDigits() {
        assertEquals("1234", NumberEntry.append("123", 4))
        assertEquals("2345", NumberEntry.append("1234", 5))
    }

    @Test fun numberEntryResolvesByNumber() {
        assertEquals(3L, NumberEntry.resolve(list, "3")?.id)
        assertNull(NumberEntry.resolve(list, "9"))
        assertNull(NumberEntry.resolve(list, ""))
    }

    @Test fun audioFallsBackChannelThenDefaultThenNull() {
        val avail = listOf("en", "hi")
        assertEquals("hi", chooseAudioLanguage(avail, "hi", "en"))
        assertEquals("en", chooseAudioLanguage(avail, "ta", "en"))
        assertNull(chooseAudioLanguage(avail, "ta", "fr"))
        assertNull(chooseAudioLanguage(avail, "", ""))
        assertEquals("en", chooseAudioLanguage(listOf("en"), "EN-US", ""))
    }

    @Test fun audioPreferenceOrder() {
        assertEquals("hi", audioPreferenceFor("hi", "en"))
        assertEquals("en", audioPreferenceFor("", "en"))
        assertNull(audioPreferenceFor("", ""))
    }

    @Test fun qualityToastsOnDropAndRecoveryOnly() {
        val w = QualityWatcher()
        assertNull(w.onHeight(480))
        assertNull(w.onHeight(720))
        assertEquals("Network slow. Switched to 480p.", w.onHeight(480))
        assertEquals("Network recovered. Back to 720p.", w.onHeight(720))
        assertNull(w.onHeight(1080))
    }

    @Test fun retryThenVariantThenGiveUpAndDrm() {
        assertEquals(RetryAction.RETRY, retryAction(2001, 0, false, true))
        assertEquals(RetryAction.NEXT_VARIANT, retryAction(2001, 3, false, true))
        assertEquals(RetryAction.GIVE_UP, retryAction(2001, 3, true, true))
        assertEquals(RetryAction.GIVE_UP, retryAction(2001, 3, false, false))
        assertEquals(RetryAction.PROTECTED, retryAction(6002, 0, false, true))
    }

    @Test fun initialsFallback() {
        assertEquals("BN", initialsOf("BBC News"))
        assertEquals("?", initialsOf("  "))
    }
}
