package com.freetia.tv.data.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderNonceTest {
    private val now = 1_700_000_000_000L

    @Test
    fun matchingNonceGivesStoredChannel() {
        val nonce = ReminderNonce.newNonce()
        val stored = ReminderNonce.encode(nonce, 42, now)
        assertEquals(42L, ReminderNonce.channelFor(stored, nonce, now + 1_000))
    }

    @Test
    fun wrongOrMissingNonceIsIgnored() {
        val stored = ReminderNonce.encode(ReminderNonce.newNonce(), 42, now)
        assertNull(ReminderNonce.channelFor(stored, ReminderNonce.newNonce(), now))
        assertNull(ReminderNonce.channelFor(stored, null, now))
        assertNull(ReminderNonce.channelFor(stored, "", now))
        assertNull(ReminderNonce.channelFor(null, "abc", now))
    }

    @Test
    fun staleOrMalformedRecordIsIgnored() {
        val nonce = ReminderNonce.newNonce()
        val stored = ReminderNonce.encode(nonce, 42, now)
        assertNull(ReminderNonce.channelFor(stored, nonce, now + ReminderNonce.MAX_AGE_MS + 1))
        assertNull(ReminderNonce.channelFor(stored, nonce, now - 1))
        assertNull(ReminderNonce.channelFor("$nonce:x:$now", nonce, now))
        assertNull(ReminderNonce.channelFor(nonce, nonce, now))
    }

    @Test
    fun noncesAreRandomHex() {
        val a = ReminderNonce.newNonce()
        assertEquals(32, a.length)
        assertEquals(a, a.filter { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(a, ReminderNonce.newNonce())
    }
}
