package com.freetia.tv.ui.nav

import com.freetia.tv.data.settings.ColorAction
import java.io.Serializable

/**
 * Every screen in the app. Routes are Serializable so the back stack survives rotation and process death.
 * To add a screen: add a route here, then one `is Route.X ->` line in [FreetiaNavHost].
 */
sealed interface Route : Serializable {
    /** Saved-state key. Routes that should keep their state when only their arguments change override this. */
    val stateKey: String get() = toString()

    /** Channel list; the root of the stack. Shows the splash while channels first load. */
    data object Home : Route
    /** Player for [channelId]. Channel up/down calls `replace`, so the player keeps its state (stateKey is fixed). */
    data class Player(val channelId: Long) : Route { override val stateKey get() = "player" }
    data object AddPlaylist : Route
    data object Playlists : Route
    data class PlaylistEdit(val playlistId: Long) : Route
    data object Search : Route
    data object Favourites : Route
    data object Groups : Route
    data class GroupDetail(val groupId: Long) : Route
    data object Recents : Route
    data object RegionPicker : Route
    data object Epg : Route
    data object Reminders : Route
    data object Settings : Route
    data class SettingsPage(val section: SettingsSection) : Route
    data object Safety : Route
    data object PinRecovery : Route
    data object Locks : Route
    data object Profiles : Route
    data object Backup : Route
    data object Diagnostics : Route
    data object About : Route
    data object AutoStartSetup : Route
    data object SleepTimer : Route

    /** Routes the colour buttons and top-level menus open: they sit directly on Home or the player. */
    val isBase: Boolean get() = this is Home || this is Player
}

/** §3.14 groups, one page each under Settings. */
enum class SettingsSection(val title: String) {
    PLAYBACK("Playback"),
    STARTUP("Startup"),
    NAVIGATION("Navigation"),
    DISPLAY("Display"),
    SAFETY("Safety"),
    SLEEP_REMINDERS("Sleep and reminders"),
    PLAYLISTS_EPG("Playlists and guide"),
    CACHE("Cache"),
    ADVANCED("Advanced"),
}

/** Colour-button target (§2); null for [ColorAction.NONE]. */
fun ColorAction.route(): Route? = when (this) {
    ColorAction.FAVOURITES -> Route.Favourites
    ColorAction.SEARCH -> Route.Search
    ColorAction.EPG -> Route.Epg
    ColorAction.SETTINGS -> Route.Settings
    ColorAction.PLAYLISTS -> Route.Playlists
    ColorAction.RECENTS -> Route.Recents
    ColorAction.GROUPS -> Route.Groups
    ColorAction.NONE -> null
}
