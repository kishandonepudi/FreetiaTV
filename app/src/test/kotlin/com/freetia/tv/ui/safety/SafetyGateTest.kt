package com.freetia.tv.ui.safety

import com.freetia.tv.core.safety.PinCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyGateTest {

    @Test
    fun notSetRoutesToSetupInsteadOfPassing() {
        assertEquals(PinEntryOutcome.SETUP, pinEntryOutcome(PinCheck.NotSet))
        assertEquals(PinEntryOutcome.PASS, pinEntryOutcome(PinCheck.Ok))
        assertEquals(PinEntryOutcome.WRONG, pinEntryOutcome(PinCheck.Wrong(2)))
        assertEquals(PinEntryOutcome.LOCKED_OUT, pinEntryOutcome(PinCheck.LockedOut(1_000)))
    }

    @Test
    fun graceIsInactiveUntilGranted() {
        assertFalse(PinGrace(now = { 0L }).isActive())
    }

    @Test
    fun graceExpiresAfterTtl() {
        var now = 1_000L
        val grace = PinGrace(now = { now })
        grace.grant()
        now += PinGrace.TTL_MS - 1
        assertTrue(grace.isActive())
        now += 1
        assertFalse(grace.isActive())
        now -= 10
        assertFalse("an expired grant stays expired", grace.isActive())
    }

    @Test
    fun graceRejectsClockGoingBackwards() {
        var now = 10_000L
        val grace = PinGrace(now = { now })
        grace.grant()
        now = 5_000L
        assertFalse(grace.isActive())
    }
}
