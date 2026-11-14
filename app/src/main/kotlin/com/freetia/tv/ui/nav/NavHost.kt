package com.freetia.tv.ui.nav

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import com.freetia.tv.FreetiaApp
import com.freetia.tv.core.safety.ChannelFilter
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.settings.ColorButton
import com.freetia.tv.ui.AddPlaylistScreen
import com.freetia.tv.ui.BrandSplash
import com.freetia.tv.ui.ChannelListScreen
import com.freetia.tv.ui.PAGING_THRESHOLD
import com.freetia.tv.ui.player.PlayerScreen
import com.freetia.tv.ui.components.BrandToastHost
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.diagnostics.DiagnosticsScreen
import com.freetia.tv.ui.epg.EpgScreen
import com.freetia.tv.ui.epg.RemindersScreen
import com.freetia.tv.ui.library.FavouritesScreen
import com.freetia.tv.ui.library.GroupDetailScreen
import com.freetia.tv.ui.library.GroupsScreen
import com.freetia.tv.ui.library.LibraryHost
import com.freetia.tv.ui.library.PlaylistEditScreen
import com.freetia.tv.ui.library.PlaylistManagerScreen
import com.freetia.tv.ui.library.RecentsScreen
import com.freetia.tv.ui.library.RegionPickerScreen
import com.freetia.tv.ui.library.SearchScreen
import com.freetia.tv.ui.safety.LockGate
import com.freetia.tv.ui.safety.LockedIdsProvider
import com.freetia.tv.ui.safety.LocksScreen
import com.freetia.tv.ui.safety.PinRecoveryScreen
import com.freetia.tv.ui.safety.SafetyScreen
import com.freetia.tv.ui.settings.BackupScreen
import com.freetia.tv.ui.settings.ProfilesScreen
import com.freetia.tv.ui.settings.SettingsPageScreen
import com.freetia.tv.ui.settings.SettingsScreen
import com.freetia.tv.ui.system.AutoStartSetupScreen
import com.freetia.tv.ui.system.SleepTimerScreen
import com.freetia.tv.ui.system.SystemHost
import com.freetia.tv.ui.system.sleepWarningKeys
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

/** One line per route. Feature agents change only their own line (and their own screen file). */
@Composable
private fun Screen(route: Route, nav: Navigator, session: AppSession) {
    when (route) {
        Route.Home -> HomeRoute(nav, session)
        is Route.Player -> PlayerRoute(route, nav, session)
        Route.AddPlaylist -> AddPlaylistScreen(
            onAdd = { name, url ->
                session.addPlaylist(name, url)
                nav.popToHome()
            },
            onCancel = { nav.pop() },
        )
        Route.Playlists -> PlaylistManagerScreen(nav)
        is Route.PlaylistEdit -> PlaylistEditScreen(route.playlistId, nav)
        Route.Search -> SearchScreen(nav)
        Route.Favourites -> FavouritesScreen(nav)
        Route.Groups -> GroupsScreen(nav)
        is Route.GroupDetail -> GroupDetailScreen(route.groupId, nav)
        Route.Recents -> RecentsScreen(nav)
        Route.RegionPicker -> RegionPickerScreen(nav)
        Route.Epg -> EpgScreen(nav)
        Route.Reminders -> RemindersScreen(nav)
        Route.Settings -> SettingsScreen(nav)
        is Route.SettingsPage -> SettingsPageScreen(route.section, nav)
        Route.Safety -> SafetyScreen(nav)
        Route.PinRecovery -> PinRecoveryScreen(nav)
        Route.Locks -> LocksScreen(nav)
        Route.Profiles -> ProfilesScreen(nav)
        Route.Backup -> BackupScreen(nav)
        Route.Diagnostics -> DiagnosticsScreen(nav)
        Route.About -> com.freetia.tv.ui.settings.AboutScreen(nav)
        Route.AutoStartSetup -> AutoStartSetupScreen(nav)
        Route.SleepTimer -> SleepTimerScreen(nav)
    }
}

/**
 * Hosts the current route. Back handling:
 * - Back is caught in `onKeyEvent` on the root, after the focused screen had its chance but before Compose's
 *   default "Back exits focus" step, which used to swallow the first Back in the player. It is then dispatched to
 *   the OnBackPressedDispatcher, so a screen's own `BackHandler` still wins.
 * - The host's BackHandler pops the stack; at Home it runs the double-Back exit.
 * A screen that needs raw Back (e.g. long-press) consumes both KeyDown and KeyUp of `Key.Back` in its onKeyEvent.
 */
@Composable
fun FreetiaNavHost(nav: Navigator, session: AppSession) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val holder = rememberSaveableStateHolder()
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val colorButtons by app.settings.colorButtons.collectAsState(initial = null)
    val exitGate = remember { DoubleBackExit() }
    val resumed = rememberSaveable { mutableStateOf(false) }

    // Saved state of popped routes is dropped, so re-opening a screen starts fresh while the screens still on the
    // stack (e.g. Home's selected category under the player or the add form) are restored on return.
    val keys = nav.stack.map { it.stateKey }.toSet()
    val retained = remember { mutableSetOf<String>() }
    LaunchedEffect(keys) {
        (retained - keys).forEach(holder::removeState)
        retained.clear()
        retained += keys
    }

    BackHandler {
        if (nav.pop()) return@BackHandler
        if (exitGate.onBack(SystemClock.uptimeMillis())) session.exitApp()
        else session.toast.show("Press Back again to exit")
    }

    CompositionLocalProvider(
        LocalNavigator provides nav,
        LocalAppSession provides session,
        LocalBrandToast provides session.toast,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .sleepWarningKeys()
                .onKeyEvent { event ->
                    if (event.key == Key.Back) {
                        if (event.type == KeyEventType.KeyUp && !event.nativeKeyEvent.isCanceled) {
                            dispatcher?.onBackPressed()
                        }
                        return@onKeyEvent true
                    }
                    val button = colorButtonOf(event.nativeKeyEvent.keyCode) ?: return@onKeyEvent false
                    if (event.type == KeyEventType.KeyUp) {
                        colorButtons?.get(button)?.route()?.let(nav::openTopLevel)
                    }
                    true
                }
        ) {
            val route = nav.current
            val fullBleed = route is Route.Player
            ImmersiveWhile(fullBleed)
            Box(
                if (fullBleed) Modifier.fillMaxSize()
                else Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                holder.SaveableStateProvider(route.stateKey) { Screen(route, nav, session) }
            }
            LibraryHost(nav, session, resumed)
            SystemHost(nav)
            BrandToastHost(session.toast)
        }
    }
}

private fun colorButtonOf(keyCode: Int): ColorButton? = when (keyCode) {
    AndroidKeyEvent.KEYCODE_PROG_RED -> ColorButton.RED
    AndroidKeyEvent.KEYCODE_PROG_GREEN -> ColorButton.GREEN
    AndroidKeyEvent.KEYCODE_PROG_YELLOW -> ColorButton.YELLOW
    AndroidKeyEvent.KEYCODE_PROG_BLUE -> ColorButton.BLUE
    else -> null
}

@Composable
private fun HomeRoute(nav: Navigator, session: AppSession) {
    val playlists by session.playlists.collectAsState()
    val active by session.activePlaylist.collectAsState()
    val loadedChannels by session.channels.collectAsState()
    val current = active
    // Full rows (logos, previews) only below the list's paging threshold; above it the list pages from Room.
    val paged = (loadedChannels?.size ?: 0) > PAGING_THRESHOLD
    val refsReady = loadedChannels != null
    val fullChannels by remember(current?.id, paged, refsReady) {
        val pid = current?.id
        if (pid == null || paged || !refsReady) flowOf(emptyList()) else session.fullChannelsFor(pid)
    }.collectAsState(initial = null)
    val channels = fullChannels.orEmpty()

    // Startup playlist/channel selection (resume, first-channel fallback, failure fallback) lives in LibraryHost.

    // Brand screen while a first download is likely under way. A sync only starts when updateOnStartup is set
    // (or right after the user adds a playlist), and the startup worker waits for a network that may never
    // come, so the timeout hands over to the list, whose empty state explains what to do.
    var splashTimedOut by remember(current?.id) { mutableStateOf(false) }
    LaunchedEffect(current?.id) {
        delay(SPLASH_TIMEOUT_MS)
        splashTimedOut = true
    }
    val firstDownload = current != null && loadedChannels?.isEmpty() == true &&
        current.updateOnStartup && current.lastUpdateTime == 0L && current.lastUpdateSuccess
    val splashStatus = when {
        splashTimedOut -> null
        playlists == null || (current != null && (loadedChannels == null || fullChannels == null)) -> "Loading your channels…"
        firstDownload -> "Downloading channels…"
        else -> null
    }

    if (splashStatus != null) {
        BrandSplash(splashStatus)
    } else {
        LockedIdsProvider(current?.id) {
            ChannelListScreen(
                playlist = current,
                playlistsLoaded = playlists != null,
                channels = channels,
                focusChannelId = session.lastWatchedChannelId,
                onPlay = { nav.play(it.id) },
                onAddPlaylist = { nav.push(Route.AddPlaylist) },
                totalCount = if (paged) loadedChannels?.size ?: 0 else channels.size,
            )
        }
    }
}

private sealed interface ChannelLookup {
    data object Loading : ChannelLookup
    data object Missing : ChannelLookup
    data class Found(val channel: ChannelEntity) : ChannelLookup
}

@Composable
private fun PlayerRoute(route: Route.Player, nav: Navigator, session: AppSession) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val active by session.channels.collectAsState()
    val inActive = active?.any { it.id == route.channelId } == true

    // The session list holds slim rows only, so the full row is always loaded by id. A channel outside the active
    // playlist (a favourite, recent or search hit from another playlist) browses within its own playlist. Child Safe
    // Mode still applies.
    val lookup by produceState<ChannelLookup>(ChannelLookup.Loading, route.channelId) {
        app.database.channelDao().observeById(route.channelId).collect { ch ->
            val hidden = ch != null && ChannelFilter.isHidden(ch, app.settings.childSafeMode.get())
            value = if (ch == null || hidden) ChannelLookup.Missing else ChannelLookup.Found(ch)
        }
    }
    // Up/Down replaces the route; keep the playing channel while the next row loads so the player never leaves
    // composition for a frame.
    val last = remember { arrayOfNulls<ChannelEntity>(1) }
    val found = (lookup as? ChannelLookup.Found)?.channel
    if (found != null) last[0] = found
    val channel = found ?: last[0].takeIf { lookup == ChannelLookup.Loading }
    val siblings by remember(channel?.playlistId, inActive) {
        val pid = channel?.playlistId
        if (inActive || pid == null) flowOf(emptyList()) else session.channelsFor(pid)
    }.collectAsState(initial = emptyList())

    if (channel == null) {
        // Deleted by a refresh, or hidden by Child Safe Mode: go back rather than show a blank player.
        if (lookup == ChannelLookup.Missing) LaunchedEffect(Unit) { nav.pop() }
        return
    }

    LockGate(channel, onCancel = { nav.pop() }) {
        PlayerScreen(
            channel = channel,
            channels = if (inActive) active.orEmpty() else siblings,
            onChannelChange = { nav.replace(Route.Player(it.id)) },
        )
        LaunchedEffect(channel.id) {
            session.lastWatchedChannelId = channel.id
            app.playlistRepository.rememberLastChannel(channel.playlistId, channel.id, channel.number)
            app.database.channelDao().updateLastPlayTime(channel.id)
            app.database.recentChannelDao().record(channel.id, app.settings.activeProfileId.get())
        }
    }
}

private const val SPLASH_TIMEOUT_MS = 10_000L

/** Hides the system bars (swipe to peek) on full-bleed routes and restores them on leaving. */
@Composable
private fun ImmersiveWhile(immersive: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(immersive, lifecycle) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        fun hide() {
            controller?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        // The system restores the bars when the app comes back from Home, so hide them again on every resume.
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (immersive && event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) hide()
        }
        if (immersive) hide()
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
}
