package com.freetia.tv.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.freetia.tv.data.settings.SettingsRepository
import com.freetia.tv.data.settings.StoredEnum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingRulesTest {
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
    fun everySettingIsEitherExportedOrDeliberatelyExcluded() {
        val names = settings.all.map { it.key.name }.toSet()
        val covered = SettingRules.rules.keys + SettingRules.excluded
        assertEquals("settings missing from the backup rules", names - covered, emptySet<String>())
        assertEquals("rules for settings that no longer exist", covered - names, emptySet<String>())
        assertTrue(SettingRules.rules.keys.intersect(SettingRules.excluded).isEmpty())
    }

    @Test
    fun everyDefaultPassesItsOwnRule() {
        for (pref in settings.all) {
            val name = pref.key.name
            if (name !in SettingRules.rules) continue
            val d = pref.default
            val exported = if (d is StoredEnum) d.storage else d
            assertNotNull("default of $name fails its rule", SettingRules.normalize(name, exported))
        }
    }

    @Test
    fun secretsNeverAppearInTheSettingsSection() {
        for (secret in listOf("pin_hash", "recovery_code_hash", "recovery_answer_hash", "epg_source_url")) {
            assertNull(SettingRules.normalize(secret, "x"))
            assertTrue(secret in SettingRules.excluded)
        }
    }

    @Test
    fun numbersAreRangeChecked() {
        assertEquals(100, SettingRules.normalize("text_size", 100))
        assertEquals(100, SettingRules.normalize("text_size", 100L))
        assertEquals(100, SettingRules.normalize("text_size", 100.0))
        assertNull(SettingRules.normalize("text_size", 49))
        assertNull(SettingRules.normalize("text_size", 201))
        assertEquals(0.5f, SettingRules.normalize("subtitle_bg_opacity", 0.5) as Float, 0f)
        assertNull(SettingRules.normalize("osd_timeout_seconds", null))
    }
}
