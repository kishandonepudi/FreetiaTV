package com.freetia.tv.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.freetia.tv.data.model.DEFAULT_PROFILE_ID
import com.freetia.tv.data.preferences.PreferencesKeys as K
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/** One typed setting. [flow] emits the stored value or [default], and only when it changes. */
interface Pref<T> {
    val key: Preferences.Key<*>
    val default: T
    val flow: Flow<T>
    suspend fun get(): T = flow.first()
    suspend fun set(value: T)
    suspend fun reset()
}

/** Stored as its lower-case name, so renaming a constant is a breaking change for saved settings. */
interface StoredEnum { val storage: String get() = (this as Enum<*>).name.lowercase() }

enum class ThemeMode : StoredEnum { DARK, LIGHT, SYSTEM }
enum class BufferMode : StoredEnum { LOW_LATENCY, BALANCED, STABLE, CUSTOM }
enum class AspectRatioMode : StoredEnum { FIT, FILL, ZOOM, RATIO_16_9, RATIO_4_3 }
enum class LockBehavior : StoredEnum { SESSION_UNLOCK, ALWAYS_ASK }
enum class ColorButton { RED, GREEN, YELLOW, BLUE }

/** What a remote colour button opens (§2). */
enum class ColorAction : StoredEnum { FAVOURITES, SEARCH, EPG, SETTINGS, PLAYLISTS, RECENTS, GROUPS, NONE }

/**
 * Typed access to every DataStore key in [K], with spec defaults (§3.14). Feature code uses this, never DataStore
 * directly. Usage: `app.settings.highContrast.flow` / `app.settings.highContrast.set(true)`.
 */
class SettingsRepository(private val store: DataStore<Preferences>) {

    private val data: Flow<Preferences> = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    private val registry = ArrayList<Pref<*>>()

    /** Every setting, for export/import, reset and tests. */
    val all: List<Pref<*>> get() = registry

    // Startup
    /** 0 = follow the playlist default flag, then priority 1. */
    val defaultPlaylistId = pref(K.DEFAULT_PLAYLIST_ID, 0L)
    /** true = Player on launch (resume/first channel), false = Home. */
    val openPlayerOnStartup = pref(K.OPEN_PLAYER_ON_STARTUP, true)
    val autoStartOnTv = pref(K.AUTO_START_ON_TV, false)
    val resumeLastChannel = pref(K.RESUME_LAST_CHANNEL, true)
    val activeProfileId = pref(K.ACTIVE_PROFILE_ID, DEFAULT_PROFILE_ID)

    // Display
    val themeMode = enumPref(K.THEME_MODE, ThemeMode.DARK)
    /** Percent of the base type scale. */
    val textSizePercent = pref(K.TEXT_SIZE, 100)
    val highContrast = pref(K.HIGH_CONTRAST, false)
    val osdTimeoutSeconds = pref(K.OSD_TIMEOUT_SECONDS, 5)

    // Playback
    val bufferMode = enumPref(K.BUFFER_MODE, BufferMode.BALANCED)
    /** Custom preset only. */
    val bufferMinMs = pref(K.BUFFER_MIN_MS, 10_000)
    val bufferMaxMs = pref(K.BUFFER_MAX_MS, 30_000)
    /** "auto" or a height label such as "1080p". */
    val defaultQuality = pref(K.DEFAULT_QUALITY, "auto")
    val autoQuality = pref(K.AUTO_QUALITY, true)
    val qualityNotifications = pref(K.QUALITY_NOTIFICATIONS, true)
    val lockQuality = pref(K.LOCK_QUALITY, false)
    val aspectRatio = enumPref(K.ASPECT_RATIO, AspectRatioMode.FIT)

    // Captions and audio
    /** Percent of the default caption size. */
    val subtitleSizePercent = pref(K.SUBTITLE_SIZE, 100)
    val subtitleColor = pref(K.SUBTITLE_COLOR, "white")
    val subtitleBgColor = pref(K.SUBTITLE_BG_COLOR, "black")
    val subtitleBgOpacity = pref(K.SUBTITLE_BG_OPACITY, 0.5f)
    /** ISO 639 code; empty = the stream's default track. */
    val defaultAudioLanguage = pref(K.DEFAULT_AUDIO_LANGUAGE, "")
    /** Use the system caption style (accessibility settings) instead of the three options above. */
    val subtitleUseSystemStyle = pref(K.SUBTITLE_USE_SYSTEM_STYLE, true)

    // Navigation
    /** Long-press Back in the player returns to the previous channel (§2; off by default). */
    val backReturnsToPrevious = pref(K.BACK_RETURNS_TO_PREVIOUS, false)
    /** false = OSD delay: Up/Down browses the banner and tunes after [channelChangeOsdDelayMs]. */
    val channelChangeInstant = pref(K.CHANNEL_CHANGE_INSTANT, true)
    val channelChangeOsdDelayMs = pref(K.CHANNEL_CHANGE_OSD_DELAY_MS, 1_500)
    val numberEntryTimeoutMs = pref(K.NUMBER_ENTRY_TIMEOUT_MS, 2_000)
    val colorRed = enumPref(K.COLOR_BUTTON_RED, ColorAction.FAVOURITES)
    val colorGreen = enumPref(K.COLOR_BUTTON_GREEN, ColorAction.SEARCH)
    val colorYellow = enumPref(K.COLOR_BUTTON_YELLOW, ColorAction.EPG)
    val colorBlue = enumPref(K.COLOR_BUTTON_BLUE, ColorAction.SETTINGS)

    // Safety (hashes come from EncryptionManager.hash; empty = not set)
    val childSafeMode = pref(K.CHILD_SAFE_MODE, false)
    val pinHash = pref(K.PIN_HASH, "")
    val recoveryCodeHash = pref(K.RECOVERY_CODE_HASH, "")
    val recoveryQuestion = pref(K.RECOVERY_QUESTION, "")
    val recoveryAnswerHash = pref(K.RECOVERY_ANSWER_HASH, "")
    val defaultLockBehavior = enumPref(K.DEFAULT_LOCK_BEHAVIOR, LockBehavior.SESSION_UNLOCK)
    /** 0 = until exit. */
    val sessionUnlockMinutes = pref(K.SESSION_UNLOCK_DURATION_MINUTES, 30)

    // Sleep timer
    val sleepTimerEnabled = pref(K.SLEEP_TIMER_ENABLED, false)
    /** 10..240. */
    val sleepTimerDefaultMinutes = pref(K.SLEEP_TIMER_DEFAULT_MINUTES, 60)

    // Cache
    val clearCacheOnExit = pref(K.CLEAR_CACHE_ON_EXIT, true)
    val clearCacheOnStartup = pref(K.CLEAR_CACHE_ON_STARTUP, true)
    val logoCacheSizeMb = pref(K.LOGO_CACHE_SIZE_MB, 50)

    // Reminders
    val reminderLeadMinutes = pref(K.REMINDER_LEAD_TIME_MINUTES, 2)
    val reminderWakeScreen = pref(K.REMINDER_WAKE_SCREEN, false)
    val reminderAutoTune = pref(K.REMINDER_AUTO_TUNE, false)

    // EPG
    /** Global user-supplied XMLTV URL; a playlist's own userEpgUrl / x-tvg-url wins. */
    val epgSourceUrl = pref(K.EPG_SOURCE_URL, "")
    val epgRefreshIntervalHours = pref(K.EPG_REFRESH_INTERVAL_HOURS, 12)
    val epgDays = pref(K.EPG_DAYS, 7)

    // Region and content
    /** Region code from the first-run picker; empty = all regions. */
    val selectedRegion = pref(K.SELECTED_REGION, "")
    val hideDeadStreams = pref(K.HIDDEN_DEAD_STREAMS, false)

    // First run
    val firstRunCompleted = pref(K.FIRST_RUN_COMPLETED, false)
    val defaultPlaylistSeeded = pref(K.DEFAULT_PLAYLIST_SEEDED, false)
    val firstRunPinSetup = pref(K.FIRST_RUN_PIN_SETUP, false)
    val autoStartSetupShown = pref(K.AUTO_START_SETUP_SHOWN, false)

    // Privacy: opt-in only.
    val analyticsEnabled = pref(K.ANALYTICS_ENABLED, false)

    fun colorAction(button: ColorButton): Pref<ColorAction> = when (button) {
        ColorButton.RED -> colorRed
        ColorButton.GREEN -> colorGreen
        ColorButton.YELLOW -> colorYellow
        ColorButton.BLUE -> colorBlue
    }

    val colorButtons: Flow<Map<ColorButton, ColorAction>> =
        combine(colorRed.flow, colorGreen.flow, colorYellow.flow, colorBlue.flow) { r, g, y, b ->
            mapOf(ColorButton.RED to r, ColorButton.GREEN to g, ColorButton.YELLOW to y, ColorButton.BLUE to b)
        }

    /** Resets every setting except safety secrets and first-run markers ("Reset" in Advanced). */
    suspend fun resetPreferences() {
        val keep = setOf<Preferences.Key<*>>(
            K.PIN_HASH, K.RECOVERY_CODE_HASH, K.RECOVERY_QUESTION, K.RECOVERY_ANSWER_HASH,
            K.DEFAULT_PLAYLIST_SEEDED, K.FIRST_RUN_COMPLETED,
        )
        store.edit { prefs -> registry.filter { it.key !in keep }.forEach { prefs.remove(it.key) } }
    }

    private fun <T> pref(key: Preferences.Key<T>, default: T): Pref<T> =
        register(object : Pref<T> {
            override val key = key
            override val default = default
            override val flow = data.map { it[key] ?: default }.distinctUntilChanged()
            override suspend fun set(value: T) { store.edit { it[key] = value } }
            override suspend fun reset() { store.edit { it.remove(key) } }
        })

    private inline fun <reified E> enumPref(key: Preferences.Key<String>, default: E): Pref<E>
        where E : Enum<E>, E : StoredEnum {
        val values = enumValues<E>()
        return register(object : Pref<E> {
            override val key = key
            override val default = default
            override val flow = data.map { p -> p[key]?.let { s -> values.firstOrNull { it.storage == s } } ?: default }
                .distinctUntilChanged()
            override suspend fun set(value: E) { store.edit { it[key] = value.storage } }
            override suspend fun reset() { store.edit { it.remove(key) } }
        })
    }

    private fun <P : Pref<*>> register(p: P): P = p.also { registry += it }
}
