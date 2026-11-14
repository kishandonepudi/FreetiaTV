package com.freetia.tv.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.freetia.tv.FreetiaApp
import com.freetia.tv.core.safety.ChannelFilter
import com.freetia.tv.data.database.ChannelRef
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.PlaylistEntity
import com.freetia.tv.ui.components.BrandToastState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Activity-scoped app state shared by screens: the active playlist and its (Child Safe filtered) channels, the
 * toast host and the full-exit action. Screens read it with `LocalAppSession.current`.
 */
@Stable
class AppSession(
    private val app: FreetiaApp,
    scope: CoroutineScope,
    val toast: BrandToastState,
    /** Full exit (§3.12): stop playback, run the exit cache clear, close the process. */
    val exitApp: () -> Unit,
) {
    private val selectedPlaylistId = MutableStateFlow<Long?>(null)

    /** null until the first database read. */
    val playlists: StateFlow<List<PlaylistEntity>?> =
        app.playlistRepository.observeAll().stateIn(scope, SharingStarted.Eagerly, null)

    /** In-session choice, then Settings > Startup playlist, then the default flag, then priority 1 (§3.1). */
    val activePlaylist: StateFlow<PlaylistEntity?> =
        combine(playlists.filterNotNull(), selectedPlaylistId, app.settings.defaultPlaylistId.flow) { list, selected, startup ->
            list.firstOrNull { it.id == selected }
                ?: list.firstOrNull { it.id == startup }
                ?: list.firstOrNull { it.isDefault }
                ?: list.firstOrNull()
        }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Slim rows of [activePlaylist]'s channels; null while loading. Re-queried only when the active playlist id
     * changes. Full rows are resolved by id (`ChannelDao.observeById`) where a screen needs them.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val channels: StateFlow<List<ChannelRef>?> = activePlaylist
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else channelsFor(id).onStart<List<ChannelRef>?> { emit(null) }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Last channel shown in the player, so the list can restore focus to it. */
    var lastWatchedChannelId: Long? by mutableStateOf(null)

    /** Switches the source shown by Home and the player without leaving the player (§3.1 "Switching"). */
    fun selectPlaylist(id: Long) {
        selectedPlaylistId.value = id
    }

    /** A playlist's channels (slim rows) with Child Safe Mode and "hide dead streams" applied. */
    fun channelsFor(playlistId: Long): Flow<List<ChannelRef>> =
        combine(
            app.database.channelDao().observeRefs(playlistId).settle(),
            app.settings.childSafeMode.flow,
            app.settings.hideDeadStreams.flow,
        ) { list, childSafe, hideDead -> ChannelFilter.visible(list, childSafe, hideDead) }.distinctUntilChanged().flowOn(Dispatchers.Default)

    /** Full rows for the Home list, which needs logos and stream URLs; only used below its paging threshold. */
    fun fullChannelsFor(playlistId: Long): Flow<List<ChannelEntity>> =
        combine(
            app.database.channelDao().getByPlaylistFlow(playlistId).settle(),
            app.settings.childSafeMode.flow,
            app.settings.hideDeadStreams.flow,
        ) { list, childSafe, hideDead -> ChannelFilter.visible(list, childSafe, hideDead) }.distinctUntilChanged().flowOn(Dispatchers.Default)

    /**
     * Saves a new playlist (URL into encrypted storage) and downloads it. appScope, not a UI scope: the first
     * download must finish even if the user leaves the app meanwhile.
     */
    fun addPlaylist(name: String, url: String) {
        app.appScope.launch {
            // §3.2: the first playlist is the default and updates at start; later ones start with both off.
            val saved = app.playlistRepository.addNew(name, url)
            selectedPlaylistId.value = saved.id
            app.playlistSyncer.sync(saved, force = true)
        }
    }
}

/**
 * A sync or health check invalidates the channels table many times in a row; each Room emission would re-read and
 * re-filter the whole playlist. The first emission passes at once (cold start), later ones are debounced.
 */
@OptIn(FlowPreview::class)
private fun <T> Flow<T>.settle(ms: Long = 300): Flow<T> = flow {
    var first = true
    emitAll(debounce { if (first) { first = false; 0L } else ms })
}

val LocalAppSession =staticCompositionLocalOf<AppSession> { error("No AppSession provided") }
