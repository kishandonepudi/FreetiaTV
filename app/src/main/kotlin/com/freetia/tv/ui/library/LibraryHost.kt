package com.freetia.tv.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.LocalContext
import com.freetia.tv.FreetiaApp
import com.freetia.tv.core.safety.ChannelFilter
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.PlaylistEntity
import com.freetia.tv.data.repository.RegionFilter
import com.freetia.tv.data.repository.RegionRules
import com.freetia.tv.data.repository.StartupRules
import com.freetia.tv.data.sync.SyncEvent
import com.freetia.tv.data.sync.SyncEvents
import com.freetia.tv.ui.nav.AppSession
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.nav.Route
import com.freetia.tv.work.PlaylistRefreshWorker
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

object LibraryStartup {
    // A first download can be slow, but the list is never blocked on it: this only bounds how long we wait
    // before treating the chosen playlist as failed and trying the next priority.
    internal const val WAIT_FOR_PLAYLIST_MS = 12_000L

    /**
     * The channel to tune when opening [playlist] (§3.1): its last channel when "Resume last channel" is on (both
     * the setting and the playlist's own), otherwise its first. Respects Child Safe Mode and hidden dead streams.
     */
    suspend fun channelFor(app: FreetiaApp, playlist: PlaylistEntity): ChannelEntity? {
        val childSafe = app.settings.childSafeMode.get()
        val hideDead = app.settings.hideDeadStreams.get()
        val resume = playlist.resumeLastChannel && app.settings.resumeLastChannel.get()
        val dao = app.database.channelDao()
        if (resume && playlist.lastChannelId > 0) {
            dao.getById(playlist.lastChannelId)
                ?.takeIf { it.playlistId == playlist.id && !ChannelFilter.isHidden(it, childSafe, hideDead) }
                ?.let { return it }
        }
        val visible = ChannelFilter.visible(dao.getByPlaylist(playlist.id), childSafe, hideDead)
        return StartupRules.startupChannel(visible, resume, playlist.lastChannelId, playlist.lastChannelNumber)
    }

    /** True once [playlist] has channels to show, false if it failed or nothing will fetch it, null while unsure. */
    internal suspend fun awaitUsable(app: FreetiaApp, playlist: PlaylistEntity): Boolean {
        val result = withTimeoutOrNull(WAIT_FOR_PLAYLIST_MS) {
            combine(app.database.channelDao().observeCount(playlist.id), SyncEvents.failedIds) { count, failed ->
                when {
                    count > 0 -> true
                    playlist.id in failed -> false
                    // Nothing will download it: auto-update is off and it has no saved channels.
                    !playlist.updateOnStartup && playlist.lastUpdateTime > 0 -> false
                    else -> null
                }
            }.filterNotNull().first()
        }
        return result ?: false
    }
}

/**
 * App-level library behaviour that must outlive any one screen, hosted once by the nav host:
 * startup playlist and channel selection with failure fallback (§3.1), the first-run region picker (§3.3),
 * background refresh scheduling and sync toasts (§3.2).
 */
@Composable
fun LibraryHost(nav: Navigator, session: AppSession, started: MutableState<Boolean>) {
    val context = LocalContext.current
    val app = context.applicationContext as FreetiaApp
    val toast = session.toast

    LaunchedEffect(Unit) {
        SyncEvents.flow.collect { event ->
            when (event) {
                is SyncEvent.Updated -> toast.show("${event.playlistName}: +${event.added} / −${event.removed} channels")
                is SyncEvent.Failed -> toast.show("${event.playlistName} couldn't update. Showing saved channels.", isError = true)
            }
        }
    }

    LaunchedEffect(Unit) { PlaylistRefreshWorker.ensurePeriodic(context) }

    LaunchedEffect(Unit) {
        if (started.value) return@LaunchedEffect
        val playlists = session.playlists.filterNotNull().first()
        started.value = true
        if (nav.canGoBack) return@LaunchedEffect
        val offerRegionPicker: suspend (PlaylistEntity) -> Boolean = { playlist ->
            val countries = RegionRules.countries(app.database.channelDao().getCountries(playlist.id))
            val offer = RegionFilter.needsFirstRunPicker(app.settings.selectedRegion.get()) && countries.size > 1
            if (offer && !nav.canGoBack) nav.push(Route.RegionPicker)
            offer
        }
        if (playlists.isEmpty()) {
            // First run: once the first playlist the user adds has channels, offer the region picker.
            val first = session.playlists.filterNotNull().first { it.isNotEmpty() }.first()
            if (LibraryStartup.awaitUsable(app, first)) offerRegionPicker(first)
            return@LaunchedEffect
        }

        val candidates = StartupRules.candidates(playlists, app.settings.defaultPlaylistId.get())
        val failed = ArrayList<PlaylistEntity>()
        var chosen: PlaylistEntity? = null
        for (candidate in candidates) {
            if (LibraryStartup.awaitUsable(app, candidate)) { chosen = candidate; break }
            failed += candidate
        }
        // Nothing loaded anywhere: stay on the first choice; its list explains what to do.
        val target = chosen ?: candidates.first()
        if (target.id != session.activePlaylist.value?.id) session.selectPlaylist(target.id)
        if (failed.isNotEmpty() && chosen != null) {
            val names = failed.joinToString(", ") { it.name }
            toast.show("$names couldn't load. Showing ${chosen.name} instead.", isError = true)
        }
        if (chosen == null) return@LaunchedEffect

        if (offerRegionPicker(chosen)) return@LaunchedEffect
        if (!app.settings.openPlayerOnStartup.get()) return@LaunchedEffect
        LibraryStartup.channelFor(app, chosen)?.let { if (!nav.canGoBack) nav.play(it.id) }
    }
}
