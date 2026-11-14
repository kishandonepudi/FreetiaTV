package com.freetia.tv.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.PlaylistEntity
import com.freetia.tv.data.preferences.PreferencesKeys
import com.freetia.tv.dataStore
import com.freetia.tv.ui.theme.FreetiaTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {
    private val app get() = application as FreetiaApp
    private var lastBackAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FreetiaTheme(darkTheme = true) { AppRoot() } }
    }

    @Composable
    private fun AppRoot() {
        val playlists by app.playlistRepository.observeAll().collectAsState(initial = null)
        val active = playlists?.let { list -> list.firstOrNull { it.isDefault } ?: list.firstOrNull() }
        val channels by remember(active?.id) { channelsFor(active) }.collectAsState(initial = emptyList())
        var playing by remember { mutableStateOf<ChannelEntity?>(null) }
        var resumed by remember { mutableStateOf(false) }

        LaunchedEffect(active?.id, channels.isNotEmpty()) {
            if (!resumed && active != null && active.resumeLastChannel && active.lastChannelNumber > 0) {
                channels.firstOrNull { it.number == active.lastChannelNumber }?.let {
                    playing = it
                    resumed = true
                }
            }
        }

        val current = playing
        if (current == null) {
            BackHandler { onExitBack() }
            ChannelListScreen(
                playlist = active,
                playlistsLoaded = playlists != null,
                channels = channels,
                onPlay = { playing = it },
            )
        } else {
            BackHandler { playing = null }
            PlayerScreen(
                channel = current,
                channels = channels,
                onChannelChange = { ch ->
                    playing = ch
                    active?.let { lifecycleScope.launch { app.playlistRepository.rememberLastChannel(it.id, ch.number) } }
                },
            )
            LaunchedEffect(current.id) {
                active?.let { app.playlistRepository.rememberLastChannel(it.id, current.number) }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun channelsFor(playlist: PlaylistEntity?) =
        if (playlist == null) emptyFlow()
        else app.database.channelDao().getByPlaylistFlow(playlist.id)
            .combine(app.dataStore.data.map { it[PreferencesKeys.CHILD_SAFE_MODE] == true }) { list, childSafe ->
                if (childSafe) list.filterNot { it.isAdult || it.isMarkedAdult } else list
            }
            .map { list -> list.sortedBy { it.number } }

    /** One Back never exits; a second Back within 2 s performs a full exit. */
    private fun onExitBack() {
        val now = System.currentTimeMillis()
        if (now - lastBackAt <= DOUBLE_BACK_MS) {
            fullExit()
        } else {
            lastBackAt = now
            Toast.makeText(this, "Press Back again to exit", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fullExit() {
        lifecycleScope.launch {
            val prefs = app.dataStore.data.first()
            if (prefs[PreferencesKeys.CLEAR_CACHE_ON_EXIT] != false) app.cacheManager.clearAll()
            finishAndRemoveTask()
            exitProcess(0)
        }
    }

    companion object {
        private const val DOUBLE_BACK_MS = 2_000L
    }
}
