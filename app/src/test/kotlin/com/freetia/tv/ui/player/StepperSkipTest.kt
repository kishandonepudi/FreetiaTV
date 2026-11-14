package com.freetia.tv.ui.player

import com.freetia.tv.core.safety.ChannelFilter
import com.freetia.tv.data.database.ChannelRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepperSkipTest {
    private fun ch(id: Long, adult: Boolean = false) =
        ChannelRef(id = id, name = "C$id", number = id.toInt(), isMarkedAdult = adult)

    private val list = listOf(ch(1), ch(2), ch(3, adult = true), ch(4), ch(5))
    private val locked = setOf(2L, 4L)

    private fun skip(childSafe: Boolean, unlocked: Boolean): (ChannelRef) -> Boolean =
        { ChannelFilter.skipWhenStepping(it, childSafe, locked, unlocked) }

    @Test fun upSkipsLockedAndAdult() {
        assertEquals(5L, nextChannel(list, 1, +1, skip(childSafe = true, unlocked = false))?.id)
        assertEquals(1L, nextChannel(list, 5, +1, skip(childSafe = true, unlocked = false))?.id)
    }

    @Test fun downSkipsAndWraps() {
        assertEquals(5L, nextChannel(list, 1, -1, skip(childSafe = true, unlocked = false))?.id)
        assertEquals(1L, nextChannel(list, 5, -1, skip(childSafe = true, unlocked = false))?.id)
    }

    @Test fun sessionUnlockStopsSkippingLocked() {
        assertEquals(2L, nextChannel(list, 1, +1, skip(childSafe = true, unlocked = true))?.id)
        assertEquals(4L, nextChannel(list, 2, +1, skip(childSafe = true, unlocked = true))?.id)
    }

    @Test fun adultReachableWhenChildSafeOff() {
        assertEquals(3L, nextChannel(list, 2, +1, skip(childSafe = false, unlocked = true))?.id)
    }

    @Test fun allSkippedReturnsNull() {
        assertNull(nextChannel(listOf(ch(2), ch(4)), 2, +1, skip(childSafe = false, unlocked = false)))
    }

    @Test fun defaultSkipKeepsOldBehaviour() {
        assertEquals(2L, nextChannel(list, 1, +1)?.id)
        assertEquals(5L, nextChannel(list, 1, -1)?.id)
    }
}
