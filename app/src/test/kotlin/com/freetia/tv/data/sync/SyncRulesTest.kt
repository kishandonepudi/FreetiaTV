package com.freetia.tv.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRulesTest {
    private val minute = 60_000L
    private val now = 1_000_000_000L

    @Test
    fun skipsWhenLastSuccessIsUnderTenMinutesOld() {
        assertTrue(SyncPolicy.shouldSkip(false, true, now - 9 * minute, now))
        assertTrue(SyncPolicy.shouldSkip(false, true, now, now))
    }

    @Test
    fun doesNotSkipAtTenMinutesOrOlder() {
        assertFalse(SyncPolicy.shouldSkip(false, true, now - 10 * minute, now))
        assertFalse(SyncPolicy.shouldSkip(false, true, now - 3 * 60 * minute, now))
    }

    @Test
    fun forceAndFailedUpdatesAreNeverSkipped() {
        assertFalse(SyncPolicy.shouldSkip(true, true, now - minute, now))
        assertFalse(SyncPolicy.shouldSkip(false, false, now - minute, now))
    }

    @Test
    fun neverUpdatedPlaylistIsNotSkipped() {
        assertFalse(SyncPolicy.shouldSkip(false, true, 0L, now))
    }

    @Test
    fun clockGoingBackwardsDoesNotSkip() {
        assertFalse(SyncPolicy.shouldSkip(false, true, now + minute, now))
    }

    @Test
    fun backgroundRefreshNeedsAutoUpdateIntervalAndStaleCopy() {
        val day = 24 * 60
        assertTrue(SyncPolicy.dueInBackground(true, day, now - day * minute, now))
        assertFalse(SyncPolicy.dueInBackground(false, day, 0, now))
        assertFalse(SyncPolicy.dueInBackground(true, 0, 0, now))
        assertFalse(SyncPolicy.dueInBackground(true, day, now - 60 * minute, now))
    }

    @Test
    fun diffCountsAddedAndRemoved() {
        val diff = ChannelDiff(mapOf("a" to 1L, "b" to 2L, "c" to 3L))
        assertEquals(1L, diff.match("a"))
        assertNull(diff.match("new1"))
        assertEquals(3L, diff.match("c"))
        assertNull(diff.match("new2"))
        assertEquals(2, diff.added)
        assertEquals(1, diff.removed)
        assertEquals(listOf(2L), diff.removedIds.toList())
    }

    @Test
    fun identicalPlaylistChangesNothing() {
        val diff = ChannelDiff(mapOf("a" to 1L, "b" to 2L))
        diff.match("a"); diff.match("b")
        assertEquals(0, diff.added)
        assertEquals(0, diff.removed)
    }

    @Test
    fun duplicateStreamUrlInNewPlaylistCountsOnceAsMatchedThenAdded() {
        val diff = ChannelDiff(mapOf("a" to 1L))
        assertEquals(1L, diff.match("a"))
        assertNull(diff.match("a"))
        assertEquals(1, diff.added)
        assertEquals(0, diff.removed)
    }

    @Test
    fun emptyCacheAddsEverything() {
        val diff = ChannelDiff(emptyMap())
        listOf("x", "y", "z").forEach { diff.match(it) }
        assertEquals(3, diff.added)
        assertEquals(0, diff.removed)
    }

    @Test
    fun repeatedStreamUrlInCacheMatchesEachRowOnce() {
        val diff = ChannelDiff(listOf("a" to 1L, "a" to 2L, "b" to 3L))
        assertEquals(1L, diff.match("a"))
        assertEquals(2L, diff.match("a"))
        assertEquals(3L, diff.match("b"))
        assertEquals(0, diff.added)
        assertEquals(0, diff.removed)
    }

    @Test
    fun unmatchedDuplicateRowsAreRemoved() {
        val diff = ChannelDiff(listOf("a" to 1L, "a" to 2L))
        assertEquals(1L, diff.match("a"))
        assertEquals(1, diff.removed)
        assertEquals(listOf(2L), diff.removedIds.toList())
    }
}
