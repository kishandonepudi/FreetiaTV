package com.freetia.tv.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.freetia.tv.data.preferences.PreferencesKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("settings.preferences_pb") }
        settings = SettingsRepository(store)
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun everyPreferencesKeyHasATypedSetting() {
        val declared = PreferencesKeys::class.java.declaredFields
            .filter { Preferences.Key::class.java.isAssignableFrom(it.type) }
            .map { it.isAccessible = true; (it.get(PreferencesKeys) as Preferences.Key<*>).name }
            .toSet()
        val covered = settings.all.map { it.key.name }
        assertTrue("no keys found by reflection", declared.isNotEmpty())
        assertEquals("duplicate registration", covered.size, covered.toSet().size)
        assertEquals(declared, covered.toSet())
    }

    @Test
    fun specDefaults() = runTest {
        assertEquals(5, settings.osdTimeoutSeconds.get())
        assertEquals(60, settings.sleepTimerDefaultMinutes.get())
        assertFalse(settings.sleepTimerEnabled.get())
        assertEquals(30, settings.sessionUnlockMinutes.get())
        assertFalse(settings.backReturnsToPrevious.get())
        assertTrue(settings.clearCacheOnExit.get())
        assertTrue(settings.clearCacheOnStartup.get())
        assertEquals(50, settings.logoCacheSizeMb.get())
        assertFalse(settings.autoStartOnTv.get())
        assertFalse(settings.childSafeMode.get())
        assertFalse(settings.analyticsEnabled.get())
        assertFalse(settings.highContrast.get())
        assertEquals(BufferMode.BALANCED, settings.bufferMode.get())
        assertEquals(1L, settings.activeProfileId.get())
        assertEquals("", settings.pinHash.get())
        assertEquals(
            mapOf(
                ColorButton.RED to ColorAction.FAVOURITES, ColorButton.GREEN to ColorAction.SEARCH,
                ColorButton.YELLOW to ColorAction.EPG, ColorButton.BLUE to ColorAction.SETTINGS,
            ),
            ColorButton.entries.associateWith { settings.colorAction(it).get() },
        )
    }

    @Test
    fun setThenResetReturnsToDefault() = runTest {
        settings.osdTimeoutSeconds.set(9)
        assertEquals(9, settings.osdTimeoutSeconds.get())
        settings.osdTimeoutSeconds.reset()
        assertEquals(5, settings.osdTimeoutSeconds.get())
    }

    @Test
    fun enumRoundTripsAndUnknownValueFallsBackToDefault() = runTest {
        settings.colorBlue.set(ColorAction.RECENTS)
        assertEquals(ColorAction.RECENTS, settings.colorBlue.get())
        store.edit { it[PreferencesKeys.COLOR_BUTTON_BLUE] = "something_removed" }
        assertEquals(ColorAction.SETTINGS, settings.colorBlue.get())
    }

    @Test
    fun resetPreferencesKeepsSafetySecrets() = runTest {
        settings.pinHash.set("salt:hash")
        settings.highContrast.set(true)
        settings.resetPreferences()
        assertEquals("salt:hash", settings.pinHash.get())
        assertFalse(settings.highContrast.get())
    }
}
