package com.freetia.tv.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {

    @Test
    fun homeIsAlwaysTheRootAndPopStopsThere() {
        val nav = Navigator(listOf(Route.Settings))
        assertEquals(listOf(Route.Home, Route.Settings), nav.stack)
        assertTrue(nav.pop())
        assertFalse(nav.pop())
        assertEquals(listOf(Route.Home), nav.stack)
    }

    @Test
    fun leavingThePlayerTakesOneBack() {
        val nav = Navigator()
        nav.play(1)
        assertTrue(nav.pop())
        assertEquals(Route.Home, nav.current)
    }

    @Test
    fun channelChangeReplacesThePlayerWithoutGrowingTheStack() {
        val nav = Navigator()
        nav.play(1)
        nav.replace(Route.Player(2))
        nav.replace(Route.Player(3))
        assertEquals(listOf(Route.Home, Route.Player(3)), nav.stack)
        assertEquals(Route.Player(1).stateKey, Route.Player(3).stateKey)
    }

    @Test
    fun replaceAtHomePushes() {
        val nav = Navigator()
        nav.replace(Route.Search)
        assertEquals(listOf(Route.Home, Route.Search), nav.stack)
    }

    @Test
    fun pushingARouteAlreadyOnTheStackPopsBackToIt() {
        val nav = Navigator()
        nav.push(Route.Settings)
        nav.push(Route.SettingsPage(SettingsSection.SAFETY))
        nav.push(Route.Settings)
        assertEquals(listOf(Route.Home, Route.Settings), nav.stack)
    }

    @Test
    fun addFormBackReturnsToTheListNotThePlayer() {
        val nav = Navigator()
        nav.play(5)
        nav.pop()
        nav.push(Route.AddPlaylist)
        assertTrue(nav.pop())
        assertEquals(Route.Home, nav.current)
    }

    @Test
    fun playKeepsASinglePlayer() {
        val nav = Navigator()
        nav.play(1)
        nav.openTopLevel(Route.Epg)
        nav.play(2)
        assertEquals(listOf(Route.Home, Route.Player(2)), nav.stack)
    }

    @Test
    fun colourButtonsOpenOnTopOfTheNearestBase() {
        val nav = Navigator()
        nav.play(1)
        nav.openTopLevel(Route.Favourites)
        nav.openTopLevel(Route.Epg)
        assertEquals(listOf(Route.Home, Route.Player(1), Route.Epg), nav.stack)
        nav.openTopLevel(Route.Epg)
        assertEquals(3, nav.stack.size)

        val fromSettingsPage = Navigator(listOf(Route.Settings, Route.SettingsPage(SettingsSection.CACHE)))
        fromSettingsPage.openTopLevel(Route.Search)
        assertEquals(listOf(Route.Home, Route.Search), fromSettingsPage.stack)
    }

    @Test
    fun popToHomeAndPopToType() {
        val nav = Navigator(listOf(Route.Settings, Route.SettingsPage(SettingsSection.ADVANCED), Route.Backup))
        nav.popTo<Route.Settings>()
        assertEquals(Route.Settings, nav.current)
        nav.popToHome()
        assertEquals(listOf(Route.Home), nav.stack)
    }

    @Test
    fun doubleBackExitsOnlyWithinTwoSeconds() {
        val gate = DoubleBackExit()
        assertFalse(gate.onBack(1_000))
        assertTrue(gate.onBack(2_500))
        assertFalse(gate.onBack(10_000))
        assertFalse(gate.onBack(12_001))
        assertTrue(gate.onBack(13_000))
    }
}
