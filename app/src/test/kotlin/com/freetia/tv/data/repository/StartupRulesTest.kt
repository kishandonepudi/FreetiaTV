package com.freetia.tv.data.repository

import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.PlaylistEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class StartupRulesTest {
    private fun playlist(id: Long, priority: Int, default: Boolean = false) =
        PlaylistEntity(id = id, name = "P$id", sourceUrl = "playlist_url:$id", priority = priority, isDefault = default)

    private fun channel(id: Long, number: Int) =
        ChannelEntity(id = id, playlistId = 1, name = "C$id", number = number, streamUrl = "http://s/$id")

    @Test
    fun singlePlaylistOpensDirectlyEvenIfNotDefault() {
        val only = playlist(7, 3)
        assertEquals(7L, StartupRules.select(listOf(only), settingsStartupId = 99)?.id)
    }

    @Test
    fun noPlaylistsSelectsNothing() {
        assertNull(StartupRules.select(emptyList()))
        assertEquals(emptyList<PlaylistEntity>(), StartupRules.candidates(emptyList()))
    }

    @Test
    fun defaultPlaylistWinsOverPriorityOne() {
        val list = listOf(playlist(1, 1), playlist(2, 2, default = true), playlist(3, 3))
        assertEquals(2L, StartupRules.select(list)?.id)
    }

    @Test
    fun withoutDefaultPriorityOneOpens() {
        val list = listOf(playlist(5, 3), playlist(6, 1), playlist(7, 2))
        assertEquals(6L, StartupRules.select(list)?.id)
    }

    @Test
    fun settingsStartupPlaylistBeatsDefaultFlag() {
        val list = listOf(playlist(1, 1), playlist(2, 2, default = true), playlist(3, 3))
        assertEquals(3L, StartupRules.select(list, settingsStartupId = 3)?.id)
    }

    @Test
    fun unknownSettingsIdFallsBackToDefault() {
        val list = listOf(playlist(1, 1), playlist(2, 2, default = true))
        assertEquals(2L, StartupRules.select(list, settingsStartupId = 42)?.id)
    }

    @Test
    fun reorderingChangesWhichPlaylistOpens() {
        val list = listOf(playlist(1, 1), playlist(2, 2))
        val moved = StartupRules.move(list, -1) { it.id == 2L }.mapIndexed { i, p -> p.copy(priority = i + 1) }
        assertEquals(2L, StartupRules.select(moved)?.id)
    }

    @Test
    fun fallbackOrderIsNextPriorityThenWrapsToHigherOnes() {
        val list = listOf(playlist(1, 1), playlist(2, 2, default = true), playlist(3, 3), playlist(4, 4))
        assertEquals(listOf(2L, 3L, 4L, 1L), StartupRules.candidates(list).map { it.id })
    }

    @Test
    fun fallbackFromPriorityOneIsSimplyPriorityOrder() {
        val list = listOf(playlist(9, 3), playlist(8, 1), playlist(7, 2))
        assertEquals(listOf(8L, 7L, 9L), StartupRules.candidates(list).map { it.id })
    }

    @Test
    fun resumeOnTunesLastChannel() {
        val channels = listOf(channel(1, 1), channel(2, 2), channel(3, 3))
        assertEquals(3L, StartupRules.startupChannel(channels, resume = true, lastChannelId = 3, lastChannelNumber = 3)?.id)
    }

    @Test
    fun resumeOffTunesFirstChannel() {
        val channels = listOf(channel(1, 1), channel(2, 2))
        assertEquals(1L, StartupRules.startupChannel(channels, resume = false, lastChannelId = 2, lastChannelNumber = 2)?.id)
    }

    @Test
    fun missingLastChannelFallsBackToNumberThenFirst() {
        val channels = listOf(channel(10, 1), channel(11, 2), channel(12, 3))
        assertEquals(11L, StartupRules.startupChannel(channels, true, lastChannelId = 99, lastChannelNumber = 2)?.id)
        assertEquals(10L, StartupRules.startupChannel(channels, true, lastChannelId = 99, lastChannelNumber = 0)?.id)
    }

    @Test
    fun emptyListHasNoStartupChannel() {
        assertNull(StartupRules.startupChannel(emptyList(), true, 1, 1))
    }

    @Test
    fun autoUpdateIsOnOnlyForTheFirstPlaylist() {
        assertEquals(true, StartupRules.autoUpdateDefault(0))
        assertEquals(false, StartupRules.autoUpdateDefault(1))
        assertEquals(false, StartupRules.autoUpdateDefault(12))
    }

    @Test
    fun moveSwapsNeighboursAndIgnoresEnds() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), StartupRules.move(list, -1) { it == "b" })
        assertEquals(listOf("a", "c", "b"), StartupRules.move(list, 1) { it == "b" })
        assertSame(list, StartupRules.move(list, -1) { it == "a" })
        assertSame(list, StartupRules.move(list, 1) { it == "c" })
        assertSame(list, StartupRules.move(list, 1) { it == "zzz" })
    }

    @Test
    fun reorderingChangesWhichOpensWhenNoDefaultIsSet() {
        val list = listOf(playlist(1, 1), playlist(2, 2))
        assertEquals(1L, StartupRules.select(list)?.id)
        val moved = StartupRules.move(list, -1) { it.id == 2L }.mapIndexed { i, p -> p.copy(priority = i + 1) }
        assertEquals(2L, StartupRules.select(moved)?.id)
    }
}
