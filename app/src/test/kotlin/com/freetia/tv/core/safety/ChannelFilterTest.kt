package com.freetia.tv.core.safety

import com.freetia.tv.data.model.ChannelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelFilterTest {
    private fun ch(id: Long, adult: Boolean = false, marked: Boolean = false, dead: Boolean = false) =
        ChannelEntity(id = id, playlistId = 1, name = "C$id", streamUrl = "http://x/$id", isAdult = adult, isMarkedAdult = marked, isDead = dead)

    @Test fun refsFollowTheSameRules() {
        val refs = listOf(
            com.freetia.tv.data.database.ChannelRef(id = 1),
            com.freetia.tv.data.database.ChannelRef(id = 2, isAdult = true),
            com.freetia.tv.data.database.ChannelRef(id = 3, isMarkedAdult = true),
            com.freetia.tv.data.database.ChannelRef(id = 4, isDead = true),
        )
        assertEquals(listOf(1L, 4L), ChannelFilter.visible(refs, childSafe = true).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), ChannelFilter.visible(refs, childSafe = false, hideDead = true).map { it.id })
        assertEquals(refs, ChannelFilter.visible(refs, childSafe = false))
    }

    @Test fun detectsAdultGroupsAndKeywords() {
        assertTrue(ChannelFilter.isAdultText("Adult", "Channel"))
        assertTrue(ChannelFilter.isAdultText("Movies;XXX", "Night"))
        assertTrue(ChannelFilter.isAdultText("", "Late 18+ Movies"))
        assertTrue(ChannelFilter.isAdultText("+18", "Canal"))
        assertTrue(ChannelFilter.isAdultText("For Adults", "Late"))
        assertTrue(ChannelFilter.isAdultText("", "PORN HD"))
    }

    @Test fun conservativeMatchingKeepsOrdinaryNames() {
        assertFalse(ChannelFilter.isAdultText("Lifestyle", "Adulting Today"))
        assertFalse(ChannelFilter.isAdultText("Sports", "XXL Sports"))
        assertFalse(ChannelFilter.isAdultText("News", "Channel 118+"))
        assertFalse(ChannelFilter.isAdultText("Kids", "Cartoon 18"))
        assertFalse(ChannelFilter.isAdultText("", ""))
    }

    @Test fun manualFlagCountsAsAdult() {
        assertTrue(ChannelFilter.isAdult(ch(1, marked = true)))
        assertTrue(ChannelFilter.isAdult(ch(1, adult = true)))
        assertFalse(ChannelFilter.isAdult(ch(1)))
    }

    @Test fun childSafeHidesAdultFromLists() {
        val list = listOf(ch(1), ch(2, adult = true), ch(3, marked = true), ch(4, dead = true))
        assertEquals(listOf(1L, 4L), ChannelFilter.visible(list, childSafe = true).map { it.id })
        assertEquals(list, ChannelFilter.visible(list, childSafe = false))
        assertEquals(listOf(1L), ChannelFilter.visible(list, childSafe = true, hideDead = true).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), ChannelFilter.visible(list, childSafe = false, hideDead = true).map { it.id })
    }

    @Test fun steppingSkipsLockedUnlessSessionUnlocked() {
        val locked = setOf(5L)
        assertTrue(ChannelFilter.skipWhenStepping(ch(5), childSafe = false, lockedIds = locked, sessionUnlocked = false))
        assertFalse(ChannelFilter.skipWhenStepping(ch(5), childSafe = false, lockedIds = locked, sessionUnlocked = true))
        assertFalse(ChannelFilter.skipWhenStepping(ch(6), childSafe = false, lockedIds = locked, sessionUnlocked = false))
    }

    @Test fun steppingSkipsAdultOnlyInChildSafe() {
        assertTrue(ChannelFilter.skipWhenStepping(ch(1, marked = true), childSafe = true, lockedIds = emptySet(), sessionUnlocked = true))
        assertFalse(ChannelFilter.skipWhenStepping(ch(1, marked = true), childSafe = false, lockedIds = emptySet(), sessionUnlocked = false))
    }

    @Test fun sessionUnlockExpires() {
        var now = 0L
        val session = SessionUnlock { now }
        assertFalse(session.isUnlocked())
        session.unlock(30)
        assertTrue(session.isUnlocked())
        now += 29 * 60_000L
        assertEquals(60_000L, session.remainingMs())
        now += 60_000L
        assertFalse(session.isUnlocked())
        assertNull(session.remainingMs())
    }

    @Test fun sessionUnlockUntilExitAndManualLock() {
        var now = 0L
        val session = SessionUnlock { now }
        session.unlock(0)
        now += 365L * 24 * 3_600_000
        assertTrue(session.isUnlocked())
        assertEquals(Long.MAX_VALUE, session.remainingMs())
        session.lock()
        assertFalse(session.isUnlocked())
    }
}
