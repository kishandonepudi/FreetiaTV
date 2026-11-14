package com.freetia.tv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.playback.PlayerFactory
import kotlinx.coroutines.delay

private const val MAX_RETRIES = 3
private const val NUMBER_ENTRY_TIMEOUT_MS = 2_000L
private const val OSD_TIMEOUT_MS = 5_000L

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channel: ChannelEntity,
    channels: List<ChannelEntity>,
    onChannelChange: (ChannelEntity) -> Unit,
) {
    val context = LocalContext.current
    val player = remember { PlayerFactory.create(context) }
    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var resolution by remember { mutableStateOf("") }
    var showBanner by remember { mutableIntStateOf(0) }
    var digits by remember { mutableStateOf("") }
    var retries by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) error = null
            }
            override fun onVideoSizeChanged(size: VideoSize) {
                resolution = if (size.height > 0) "${size.height}p" else ""
            }
            override fun onPlayerError(e: PlaybackException) {
                if (retries < MAX_RETRIES) {
                    retries++
                    player.prepare()
                } else {
                    error = "This channel isn't available right now (${e.errorCodeName}). Press ↑ or ↓ for another channel."
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(channel.id) {
        error = null
        retries = 0
        player.setMediaItem(MediaItem.fromUri(channel.streamUrl))
        player.prepare()
        player.playWhenReady = true
        showBanner++
    }
    LaunchedEffect(showBanner) { if (showBanner > 0) { delay(OSD_TIMEOUT_MS); showBanner = 0 } }
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(NUMBER_ENTRY_TIMEOUT_MS)
        channels.firstOrNull { it.number == digits.toInt() }?.let(onChannelChange)
        digits = ""
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun step(delta: Int) {
        if (channels.isEmpty()) return
        val i = channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        onChannelChange(channels[(i + delta).mod(channels.size)])
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (val code = event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> { step(+1); true }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> { step(-1); true }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> { showBanner++; true }
                    in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> {
                        digits = (digits + (code - AndroidKeyEvent.KEYCODE_0)).takeLast(4); true
                    }
                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        if (digits.isNotEmpty()) {
            Text(
                digits, fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(32.dp).background(Color(0xAA000000)).padding(16.dp),
            )
        }

        error?.let {
            Text(
                it, fontSize = 26.sp, color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(48.dp).background(Color(0xCC000000)).padding(24.dp),
            )
        }

        if (showBanner > 0 || buffering) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xCC000000)).padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(model = channel.tvgLogo.ifBlank { null }, contentDescription = null, modifier = Modifier.size(96.dp, 64.dp))
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${channel.number}  ${channel.name}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            listOfNotNull(
                                channel.groupTitle.ifBlank { null },
                                resolution.ifBlank { null },
                                if (buffering) "Buffering…" else null,
                            ).joinToString("  •  "),
                            fontSize = 22.sp, color = Color(0xFFCCCCCC),
                        )
                    }
                }
                HintBar("↑↓: Channel    0–9: Jump    OK: Info    Back: Channel list")
            }
        }
    }
}
