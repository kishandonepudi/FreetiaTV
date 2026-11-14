package com.freetia.tv.ui.safety

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.freetia.tv.FreetiaApp
import com.freetia.tv.core.safety.ChannelFilter
import com.freetia.tv.data.epg.CatchupRequest
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.ui.nav.LocalNavigator
import com.freetia.tv.ui.nav.Route
import com.freetia.tv.ui.theme.LocalBrand
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Locked channel ids of the playlist on screen, for padlocks in lists. Empty outside [LockedIdsProvider]. */
val LocalLockedChannelIds = compositionLocalOf<Set<Long>> { emptySet() }

@Composable
fun LockedIdsProvider(playlistId: Long?, content: @Composable () -> Unit) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val ids by remember(playlistId) {
        if (playlistId == null) flowOf(emptySet()) else app.safety.lockedIds(playlistId)
    }.collectAsState(emptySet())
    CompositionLocalProvider(LocalLockedChannelIds provides ids, content = content)
}

@Composable
fun Padlock(focused: Boolean, modifier: Modifier = Modifier) {
    val brand = LocalBrand.current
    Icon(
        Icons.Filled.Lock, contentDescription = "Locked",
        tint = if (focused) brand.textPrimary else brand.textMuted, modifier = modifier.size(18.dp),
    )
}

/**
 * The skip rule for channel Up/Down in a playlist, for `nextChannel(..., skip = ...)`. Read at key press, so an
 * expired session unlock takes effect without recomposition.
 */
@Composable
fun rememberStepSkip(playlistId: Long): (ChannelEntity) -> Boolean {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val locked by remember(playlistId) { app.safety.lockedIds(playlistId) }.collectAsState(emptySet())
    val childSafe by app.safety.childSafeMode.collectAsState(false)
    return { ChannelFilter.skipWhenStepping(it, childSafe, locked, app.safety.session.isUnlocked()) }
}

private enum class Gate { CHECKING, ASK, OPEN }

/**
 * Every tune goes through here (§3.8, §8): a locked channel shows only a PIN pad over black until the PIN is
 * entered, so the stream never starts first. Back or Cancel calls [onCancel].
 */
@Composable
fun LockGate(channel: ChannelEntity, onCancel: () -> Unit, content: @Composable () -> Unit) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var passed by remember(channel.id) { mutableStateOf(false) }
    val gate by produceState(Gate.CHECKING, channel.id) {
        value = if (app.safety.needsPinToTune(channel)) Gate.ASK else Gate.OPEN
    }
    when {
        gate == Gate.OPEN || passed -> LockedIdsProvider(channel.playlistId, content)
        else -> {
            Box(Modifier.fillMaxSize().background(Color.Black))
            if (gate == Gate.ASK) {
                PinEntryDialog(
                    title = "Locked channel",
                    message = "Enter your PIN to watch ${channel.name}.",
                    onDismiss = { CatchupRequest.clear(); onCancel() },
                    onSuccess = {
                        passed = true
                        scope.launch { app.safety.onTunePinAccepted() }
                    },
                    onForgot = { nav.push(Route.PinRecovery) },
                )
            }
        }
    }
}
