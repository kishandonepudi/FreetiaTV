package com.freetia.tv.ui.player

import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.freetia.tv.BuildConfig
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.epg.CatchupRequest
import com.freetia.tv.data.database.ChannelRef
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.settings.AspectRatioMode
import com.freetia.tv.data.settings.ColorAction
import com.freetia.tv.data.settings.ColorButton
import com.freetia.tv.playback.BufferValues
import com.freetia.tv.core.system.PipState
import com.freetia.tv.playback.PlaybackHost
import com.freetia.tv.playback.PlayerFactory
import androidx.lifecycle.Lifecycle
import androidx.media3.common.MediaMetadata
import com.freetia.tv.playback.bufferValues
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.library.PlayerLibraryRows
import com.freetia.tv.ui.nav.LocalNavigator
import com.freetia.tv.ui.nav.Route
import com.freetia.tv.ui.nav.route
import com.freetia.tv.ui.theme.LocalBrand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "PlayerScreen"

private enum class Overlay { NONE, CHANNELS, MENU }

/** Survives the screen being rebuilt on a channel change, so long-press Back can return to the previous channel. */
object PlayerHistory {
    var previousId: Long = 0
    var currentId: Long = 0

    fun visit(id: Long) {
        if (id != currentId) { previousId = currentId; currentId = id }
    }
}

@Composable
fun PlayerScreen(
    channel: ChannelEntity,
    channels: List<ChannelRef>,
    onChannelChange: (ChannelRef) -> Unit,
) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    // The buffer preset is read once per player, so a change applies the next time the player opens.
    val buffer by produceState<BufferValues?>(null) {
        val s = app.settings
        value = bufferValues(s.bufferMode.get(), s.bufferMinMs.get(), s.bufferMaxMs.get())
    }
    val resolved = buffer
    if (resolved == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    PlayerContent(channel, channels, onChannelChange, resolved)
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(
    channel: ChannelEntity,
    channels: List<ChannelRef>,
    onChannelChange: (ChannelRef) -> Unit,
    buffer: BufferValues,
) {
    val context = LocalContext.current
    val app = context.applicationContext as FreetiaApp
    val s = app.settings
    val brand = LocalBrand.current
    val nav = LocalNavigator.current
    val toast = LocalBrandToast.current
    val scope = rememberCoroutineScope()
    val player = remember { PlaybackHost.reclaim() ?: PlayerFactory.create(context, buffer) }
    val allowBackground by produceState(false, channel.playlistId) {
        value = app.database.playlistDao().getById(channel.playlistId)?.allowBackgroundPlay == true
    }
    val allowBackgroundNow by rememberUpdatedState(allowBackground)
    val inPip by PipState.active.collectAsState()
    val focus = remember { FocusRequester() }
    val watcher = remember { QualityWatcher() }
    val skip = rememberRefStepSkip(channel.playlistId)

    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var height by remember { mutableIntStateOf(0) }
    var bannerTick by remember { mutableIntStateOf(0) }
    var digits by remember { mutableStateOf("") }
    var retries by remember { mutableIntStateOf(0) }
    var variantStepped by remember { mutableStateOf(false) }
    var browse by remember { mutableStateOf<ChannelRef?>(null) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var bufferedSeconds by remember { mutableIntStateOf(0) }
    var lockedHeight by remember { mutableStateOf<Int?>(null) }
    var defaultQualityDone by remember { mutableStateOf(false) }
    var captionsOn by remember { mutableStateOf(false) }
    var audioPref by remember { mutableStateOf("") }
    var backLong by remember { mutableStateOf(false) }

    val osdSeconds by s.osdTimeoutSeconds.flow.collectAsState(s.osdTimeoutSeconds.default)
    val instant by s.channelChangeInstant.flow.collectAsState(s.channelChangeInstant.default)
    val osdDelayMs by s.channelChangeOsdDelayMs.flow.collectAsState(s.channelChangeOsdDelayMs.default)
    val numberTimeoutMs by s.numberEntryTimeoutMs.flow.collectAsState(s.numberEntryTimeoutMs.default)
    val backPrevious by s.backReturnsToPrevious.flow.collectAsState(s.backReturnsToPrevious.default)
    val notify by s.qualityNotifications.flow.collectAsState(s.qualityNotifications.default)
    val autoQuality by s.autoQuality.flow.collectAsState(s.autoQuality.default)
    val lockQuality by s.lockQuality.flow.collectAsState(s.lockQuality.default)
    val aspect by s.aspectRatio.flow.collectAsState(s.aspectRatio.default)
    val colorButtons by s.colorButtons.collectAsState(emptyMap())
    val useSystemCaptions by s.subtitleUseSystemStyle.flow.collectAsState(s.subtitleUseSystemStyle.default)
    val captionSize by s.subtitleSizePercent.flow.collectAsState(s.subtitleSizePercent.default)
    val captionColorName by s.subtitleColor.flow.collectAsState(s.subtitleColor.default)
    val captionBgName by s.subtitleBgColor.flow.collectAsState(s.subtitleBgColor.default)
    val captionBgOpacity by s.subtitleBgOpacity.flow.collectAsState(s.subtitleBgOpacity.default)

    // The banner needs the full row (logo) of the channel being browsed to; stepping itself works on slim rows.
    val browseFull by produceState<ChannelEntity?>(null, browse?.id) {
        value = browse?.let { app.database.channelDao().getById(it.id) }
    }
    val shown = browseFull?.takeIf { it.id == browse?.id } ?: channel

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) { error = null; retries = 0 }
            }

            override fun onTracksChanged(t: Tracks) {
                tracks = t
                if (!defaultQualityDone && t.videoHeights().isNotEmpty()) {
                    defaultQualityDone = true
                    scope.launch {
                        val want = s.defaultQuality.get().removeSuffix("p").toIntOrNull()
                        if (want != null && want in t.videoHeights()) {
                            player.selectResolution(want)
                            lockedHeight = want
                        }
                    }
                }
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                height = size.height
                if (lockedHeight == null && lockQuality && size.height > 0) {
                    lockedHeight = size.height
                    player.selectResolution(size.height)
                }
                if (autoQuality && notify && lockedHeight == null) {
                    watcher.onHeight(size.height)?.let { toast.show(it) }
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                val lower = lowerHeight(player.currentTracks.videoHeights(), height)
                when (retryAction(e.errorCode, retries, variantStepped, hasLowerVariant = lower != null)) {
                    RetryAction.RETRY -> {
                        retries++
                        scope.launch {
                            delay(400L * retries)
                            // A live stream that fell behind its window can only recover by jumping back to the live edge.
                            if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) player.seekToDefaultPosition()
                            player.prepare()
                        }
                    }
                    RetryAction.NEXT_VARIANT -> {
                        variantStepped = true
                        retries = 0
                        if (lower != null) {
                            player.selectResolution(lower)
                            lockedHeight = lower
                        }
                        player.prepare()
                    }
                    RetryAction.PROTECTED -> error = PROTECTED_MESSAGE
                    RetryAction.GIVE_UP -> {
                        // Error codes are for developers only (BRAND_KIT §8); the stream URL may carry credentials,
                        // so it is never logged.
                        if (BuildConfig.DEBUG) Log.w(TAG, "Playback failed: ${e.errorCodeName}")
                        error = UNAVAILABLE_MESSAGE
                    }
                }
            }
        }
        PlaybackHost.register(player)
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            com.freetia.tv.ui.diagnostics.DiagnosticsSnapshot.capture(player)
            if (PlaybackHost.unregister(player)) player.release()
        }
    }

    // Home: with background play on, the service takes this same player for audio (one decoder); otherwise it stops.
    // Leaving the player screen while visible is a dispose, not a stop, and just stops the stream.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LifecycleStartEffect(player) {
        PlaybackHost.onActivityStart(player)
        if (player.currentMediaItem != null && player.playbackState == Player.STATE_IDLE) {
            player.prepare()
            player.playWhenReady = true
        }
        onStopOrDispose {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) player.stop()
            else PlaybackHost.onActivityStop(context, player, allowBackgroundNow)
        }
    }
    DisposableEffect(allowBackground) {
        PlaybackHost.backgroundAllowed = allowBackground
        onDispose { PlaybackHost.backgroundAllowed = false }
    }
    LaunchedEffect(inPip) { if (inPip) { overlay = Overlay.NONE; bannerTick = 0; digits = "" } }

    LaunchedEffect(channel.id) {
        PlayerHistory.visit(channel.id)
        browse = null
        error = null
        retries = 0
        variantStepped = false
        defaultQualityDone = false
        lockedHeight = null
        watcher.reset()
        captionsOn = channel.isCaptionsEnabled
        audioPref = channel.preferredAudioLanguage
        player.selectResolution(null)
        player.setCaptionsEnabled(captionsOn)
        player.setPreferredAudio(audioPreferenceFor(audioPref, s.defaultAudioLanguage.get()))
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(CatchupRequest.take(channel.id) ?: channel.streamUrl)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(channel.name).build())
                .build()
        )
        player.prepare()
        player.playWhenReady = true
        bannerTick++
    }
    LaunchedEffect(bannerTick) { if (bannerTick > 0) { delay(osdSeconds * 1_000L); bannerTick = 0 } }
    LaunchedEffect(bannerTick > 0) {
        while (bannerTick > 0) {
            bufferedSeconds = ((player.bufferedPosition - player.currentPosition) / 1_000).coerceAtLeast(0).toInt()
            delay(1_000)
        }
    }
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(numberTimeoutMs.toLong())
        NumberEntry.resolve(channels, digits)?.let(onChannelChange)
        digits = ""
    }
    LaunchedEffect(browse) {
        val target = browse ?: return@LaunchedEffect
        delay(osdDelayMs.toLong())
        onChannelChange(target)
    }
    LaunchedEffect(overlay) { if (overlay == Overlay.NONE) runCatching { focus.requestFocus() } }

    BackHandler(enabled = overlay != Overlay.NONE) { overlay = Overlay.NONE }

    val epg by remember(shown.id) {
        val now = System.currentTimeMillis()
        app.database.epgDao().observeSchedule(shown.id, shown.epgChannelId.ifBlank { shown.tvgId }, now, now + 6 * 3_600_000L, 2)
    }.collectAsState(emptyList())

    fun step(delta: Int) {
        val n = nextChannel(channels, browse?.id ?: channel.id, delta, skip) ?: return
        if (instant) onChannelChange(n) else { browse = n; bannerTick++ }
    }

    fun goPrevious() {
        channels.firstOrNull { it.id == PlayerHistory.previousId }?.let(onChannelChange)
    }

    // Consume KeyDown and KeyUp both, or the host's BackHandler would still pop on the KeyUp of a long press.
    fun handleBack(type: KeyEventType, repeatCount: Int): Boolean {
        if (!backPrevious) return false
        when (type) {
            KeyEventType.KeyDown -> if (repeatCount > 0 && !backLong) { backLong = true; goPrevious() }
            KeyEventType.KeyUp -> { val long = backLong; backLong = false; if (!long) nav.pop() }
        }
        return true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .semantics { contentDescription = "Playing ${shown.name}. Press OK or tap to open the channel list." }
            .pointerInput(Unit) { detectTapGestures { overlay = Overlay.CHANNELS } }
            .onKeyEvent { event ->
                if (overlay != Overlay.NONE) return@onKeyEvent false
                val code = event.nativeKeyEvent.keyCode
                if (code == AndroidKeyEvent.KEYCODE_BACK) {
                    return@onKeyEvent handleBack(event.type, event.nativeKeyEvent.repeatCount)
                }
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> { step(+1); true }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> { step(-1); true }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> { overlay = Overlay.CHANNELS; true }
                    AndroidKeyEvent.KEYCODE_MENU -> { overlay = Overlay.MENU; true }
                    AndroidKeyEvent.KEYCODE_INFO -> { bannerTick++; true }
                    AndroidKeyEvent.KEYCODE_PROG_RED -> colorKey(ColorButton.RED, colorButtons) { nav.openTopLevel(it) }
                    AndroidKeyEvent.KEYCODE_PROG_GREEN -> colorKey(ColorButton.GREEN, colorButtons) { nav.openTopLevel(it) }
                    AndroidKeyEvent.KEYCODE_PROG_YELLOW -> colorKey(ColorButton.YELLOW, colorButtons) { nav.openTopLevel(it) }
                    AndroidKeyEvent.KEYCODE_PROG_BLUE -> colorKey(ColorButton.BLUE, colorButtons) { nav.openTopLevel(it) }
                    in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> {
                        digits = NumberEntry.append(digits, code - AndroidKeyEvent.KEYCODE_0); true
                    }
                    else -> false
                }
            }
    ) {
        val ratio = when (aspect) {
            AspectRatioMode.RATIO_16_9 -> 16f / 9f
            AspectRatioMode.RATIO_4_3 -> 4f / 3f
            else -> null
        }
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    keepScreenOn = true
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = when (aspect) {
                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
                view.subtitleView?.apply {
                    if (useSystemCaptions) {
                        setUserDefaultStyle()
                        setUserDefaultTextSize()
                    } else {
                        val alpha = (captionBgOpacity.coerceIn(0f, 1f) * 255).toInt()
                        val bg = (captionColor(captionBgName) and 0x00FFFFFF) or (alpha shl 24)
                        setStyle(CaptionStyleCompat(captionColor(captionColorName), bg, 0, CaptionStyleCompat.EDGE_TYPE_NONE, 0, null))
                        setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * captionSize / 100f)
                    }
                }
            },
            modifier = if (ratio != null) Modifier.align(Alignment.Center).aspectRatio(ratio) else Modifier.fillMaxSize(),
        )

        if (digits.isNotEmpty() && !inPip) {
            Text(
                digits, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = brand.textPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 48.dp, vertical = 27.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(brand.scrim)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        error?.let {
            Text(
                it, fontSize = 18.sp, color = brand.textPrimary, textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brand.scrim)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }

        if ((bannerTick > 0 || buffering) && overlay == Overlay.NONE && !inPip) {
            val now = System.currentTimeMillis()
            val current = epg.firstOrNull()?.takeIf { it.start <= now }
            val upcoming = epg.firstOrNull { it.start > now }
            PlayerBanner(
                BannerInfo(
                    channel = shown,
                    now = current?.title,
                    next = upcoming?.title,
                    resolution = if (browse == null && height > 0) "${height}p" else "",
                    buffer = bufferLabel(bufferedSeconds, buffering),
                ),
                Modifier.align(Alignment.BottomCenter),
            )
        }

        when (overlay) {
            Overlay.CHANNELS -> ChannelOverlay(
                channels, channel.id,
                onPick = { picked ->
                    overlay = Overlay.NONE
                    if (picked.id != channel.id) onChannelChange(picked)
                },
                onOptions = { overlay = Overlay.MENU },
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Overlay.MENU -> {
                val options = remember(tracks) { tracks.audioOptions() }
                val defaultAudio by produceState("") { value = s.defaultAudioLanguage.get() }
                val chosen = chooseAudioLanguage(options.map { it.language }, audioPref, defaultAudio)
                PlayerMenu(
                    model = PlayerMenuModel(
                        audio = options,
                        selectedAudio = options.firstOrNull { it.language == chosen } ?: options.firstOrNull(),
                        captionsOn = captionsOn,
                        heights = remember(tracks) { tracks.videoHeights() },
                        selectedHeight = lockedHeight,
                        lockQuality = lockQuality,
                        aspect = aspect,
                    ),
                    onAudio = { opt ->
                        audioPref = opt.language
                        player.setPreferredAudio(opt.language.ifBlank { null })
                        scope.launch { app.database.channelDao().updatePreferredAudio(channel.id, opt.language) }
                    },
                    onCaptions = { on ->
                        captionsOn = on
                        player.setCaptionsEnabled(on)
                        scope.launch { app.database.channelDao().updateCaptionsEnabled(channel.id, on) }
                    },
                    onResolution = { h ->
                        lockedHeight = h
                        player.selectResolution(h)
                        watcher.reset()
                    },
                    onLockQuality = { on ->
                        scope.launch { s.lockQuality.set(on) }
                        val target = if (on) height.takeIf { it > 0 } else null
                        lockedHeight = target
                        player.selectResolution(target)
                        watcher.reset()
                    },
                    onAspect = { mode -> scope.launch { s.aspectRatio.set(mode) } },
                    onSleepTimer = {
                        overlay = Overlay.NONE
                        nav.push(Route.SleepTimer)
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                    extraRows = { PlayerLibraryRows(channel, onClose = { overlay = Overlay.NONE }) },
                )
            }
            Overlay.NONE -> Unit
        }
    }
}

private fun colorKey(button: ColorButton, mapping: Map<ColorButton, ColorAction>, open: (Route) -> Unit): Boolean {
    mapping[button]?.route()?.let(open)
    return true
}
