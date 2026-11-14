package com.freetia.tv.ui.settings

import com.freetia.tv.data.model.DEFAULT_PROFILE_ID
import com.freetia.tv.data.model.ProfileEntity
import com.freetia.tv.data.model.SafetyLevel
import com.freetia.tv.data.settings.ColorAction
import com.freetia.tv.data.settings.ColorButton
import com.freetia.tv.ui.nav.Route
import com.freetia.tv.ui.nav.route
import com.freetia.tv.ui.theme.TextSize
import com.freetia.tv.ui.theme.effectiveFontScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRulesTest {
    private val me = ProfileEntity(id = DEFAULT_PROFILE_ID, name = "Me")
    private val kid = ProfileEntity(id = 2, name = "Kid", isChild = true, safetyLevel = SafetyLevel.CHILD_SAFE)
    private val dad = ProfileEntity(id = 3, name = "Dad")

    @Test
    fun textSizeMapsToScalePercent() {
        assertEquals(listOf(85, 100, 125, 150), TextSize.entries.map { it.percent })
        assertEquals(TextSize.DEFAULT, TextSize.fromPercent(100))
        assertEquals(TextSize.LARGE, TextSize.fromPercent(120))
        assertEquals(TextSize.SMALL, TextSize.fromPercent(50))
        assertEquals(TextSize.EXTRA_LARGE, TextSize.fromPercent(200))
    }

    @Test
    fun fontScaleMultipliesSystemScaleAndIsCapped() {
        assertEquals(1f, effectiveFontScale(1f, 100), 0.001f)
        assertEquals(1.5f, effectiveFontScale(1f, 150), 0.001f)
        assertEquals(1.5f, effectiveFontScale(1.2f, 125), 0.001f)
        assertEquals(3f, effectiveFontScale(2f, 200), 0.001f)
        assertEquals(0.5f, effectiveFontScale(0.5f, 50), 0.001f)
    }

    @Test
    fun colourButtonOptionsAreTheSixSpecChoices() {
        assertEquals(
            listOf("Favourites", "Search", "TV guide", "Settings", "Playlists", "Off"),
            ColorButtonRules.options.map(ColorButtonRules::label),
        )
        assertEquals(7, ColorButtonRules.optionsFor(ColorAction.RECENTS).size)
        assertEquals(6, ColorButtonRules.optionsFor(ColorAction.EPG).size)
    }

    @Test
    fun everyColourOptionOpensItsScreenOrNothing() {
        assertEquals(Route.Favourites, ColorAction.FAVOURITES.route())
        assertEquals(Route.Search, ColorAction.SEARCH.route())
        assertEquals(Route.Epg, ColorAction.EPG.route())
        assertEquals(Route.Settings, ColorAction.SETTINGS.route())
        assertEquals(Route.Playlists, ColorAction.PLAYLISTS.route())
        assertNull(ColorAction.NONE.route())
        ColorButtonRules.options.filter { it != ColorAction.NONE }.forEach { assertNotNull(it.route()) }
    }

    @Test
    fun duplicateColourAssignmentsAreFound() {
        val ok = mapOf(
            ColorButton.RED to ColorAction.FAVOURITES, ColorButton.GREEN to ColorAction.SEARCH,
            ColorButton.YELLOW to ColorAction.NONE, ColorButton.BLUE to ColorAction.NONE,
        )
        assertTrue(ColorButtonRules.duplicates(ok).isEmpty())
        val clash = ok + (ColorButton.YELLOW to ColorAction.SEARCH)
        assertEquals(setOf(ColorAction.SEARCH), ColorButtonRules.duplicates(clash))
    }

    @Test
    fun defaultProfileCannotBeDeleted() {
        assertFalse(ProfileRules.canDelete(me))
        assertTrue(ProfileRules.canDelete(kid))
    }

    @Test
    fun childProfileForcesChildSafeMode() {
        val made = ProfileRules.normalized(ProfileEntity(id = 5, name = "Z", isChild = true, safetyLevel = SafetyLevel.OFF))
        assertEquals(SafetyLevel.CHILD_SAFE, made.safetyLevel)
        assertTrue(ProfileRules.childSafeAfterSwitch(made))
        assertTrue(ProfileRules.childSafeAfterSwitch(kid))
        assertFalse(ProfileRules.childSafeAfterSwitch(dad))
        assertTrue(ProfileRules.childSafeAfterSwitch(dad.copy(safetyLevel = SafetyLevel.CHILD_SAFE)))
        assertEquals(dad, ProfileRules.normalized(dad))
    }

    @Test
    fun leavingAChildProfileNeedsThePin() {
        assertTrue(ProfileSwitchRules.needsPin(kid, me, childSafeNow = true, hasPin = true))
        assertTrue(ProfileSwitchRules.needsPin(kid, dad, childSafeNow = true, hasPin = true))
        assertFalse("no PIN set yet", ProfileSwitchRules.needsPin(kid, me, childSafeNow = true, hasPin = false))
        assertFalse("entering a child profile is free", ProfileSwitchRules.needsPin(me, kid, childSafeNow = false, hasPin = true))
        assertFalse("same profile", ProfileSwitchRules.needsPin(kid, kid, childSafeNow = true, hasPin = true))
        assertFalse(ProfileSwitchRules.needsPin(me, dad, childSafeNow = false, hasPin = true))
        assertTrue("lowering safety needs the PIN", ProfileSwitchRules.needsPin(me, dad, childSafeNow = true, hasPin = true))
        assertTrue(ProfileRules.needsPin(kid, hasPin = true))
        assertFalse(ProfileRules.needsPin(me, hasPin = true))
        assertFalse(ProfileRules.needsPin(null, hasPin = true))
    }

    @Test
    fun profileNamesAreValidated() {
        val others = listOf(me, kid)
        assertNull(ProfileRules.nameError("Dad", others))
        assertEquals("Type a name.", ProfileRules.nameError("   ", others))
        assertEquals("Another profile already has that name.", ProfileRules.nameError(" kid ", others))
        assertTrue(ProfileRules.nameError("x".repeat(ProfileRules.MAX_NAME + 1), others)!!.contains("characters"))
    }

    @Test
    fun missingActiveProfileFallsBackToTheDefault() {
        assertEquals(2L, ProfileRules.fallbackId(listOf(me, kid), 2))
        assertEquals(DEFAULT_PROFILE_ID, ProfileRules.fallbackId(listOf(me, kid), 9))
    }

    @Test
    fun deletingActiveProfileAsksForPinLikeSwitching() {
        assertTrue("child safe on, falls back to unrestricted Me", ProfileSwitchRules.needsPinToDelete(dad, me, childSafeNow = true, hasPin = true))
        assertFalse("no PIN yet", ProfileSwitchRules.needsPinToDelete(dad, me, childSafeNow = true, hasPin = false))
        assertFalse("nothing restricted", ProfileSwitchRules.needsPinToDelete(dad, me, childSafeNow = false, hasPin = true))
        assertFalse("child profile already asked for the PIN", ProfileSwitchRules.needsPinToDelete(kid, me, childSafeNow = true, hasPin = true))
    }
}
