package com.freetia.tv.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.freetia.tv.core.system.BackgroundPolicy

/**
 * The single decoder-holding player (M3: one decoder). The player screen creates it and registers it here; when the
 * app goes to the background with background play on, the same instance is handed to [PlaybackService] for its
 * media session. A recreated activity adopts it with [reclaim] instead of building a second one.
 * Main thread only, like ExoPlayer itself.
 */
@UnstableApi
object PlaybackHost {
    private var player: ExoPlayer? = null
    private var composed = false
    private var handedOff = false
    private var videoOff = false

    /** Set by the player screen from the active playlist's "Allow background play". */
    var backgroundAllowed = false

    val isPlaying: Boolean
        get() = player?.let { it.playWhenReady && it.playbackState != Player.STATE_IDLE } == true

    /** [isPlaying] mirrored by a listener, for workers: they run off the main thread and may not touch ExoPlayer. */
    @Volatile var isPlayingAnyThread = false
        private set
    private val mirror = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            isPlayingAnyThread = player.playWhenReady && player.playbackState != Player.STATE_IDLE
        }
    }

    /** Player for the service's session, or null when there is nothing to host. */
    fun playerForService(): ExoPlayer? = player

    fun register(p: ExoPlayer) {
        p.removeListener(mirror)
        p.addListener(mirror)
        player = p
        composed = true
        handedOff = false
        videoOff = false
    }

    /** The service still holds the player for background audio after the screen is gone: this picks it up. */
    fun reclaim(): ExoPlayer? {
        val p = player ?: return null
        if (!handedOff || composed) return null
        setVideo(p, on = true)
        return p
    }

    /** Called when the screen's composition ends. Returns true when the caller must release the player. */
    fun unregister(p: ExoPlayer): Boolean {
        if (player !== p) return true
        composed = false
        if (handedOff) return false
        player = null
        isPlayingAnyThread = false
        return true
    }

    fun onActivityStart(p: ExoPlayer) {
        if (player !== p) return
        handedOff = false
        setVideo(p, on = true)
    }

    /** Home, screen off or PiP closed. Returns true when playback goes on in the background. */
    fun onActivityStop(context: Context, p: ExoPlayer, backgroundAllowed: Boolean): Boolean {
        val action = BackgroundPolicy.onActivityStop(backgroundAllowed, p.playWhenReady)
        if (action.stopPlayback || player !== p) {
            p.stop()
            return false
        }
        if (action.disableVideo) setVideo(p, on = false)
        handedOff = true
        val started = runCatching {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
        }.isSuccess
        if (!started) {
            handedOff = false
            setVideo(p, on = true)
            p.stop()
        }
        return started
    }

    /** The notification's Stop button. */
    fun stopFromService() {
        val p = player ?: return
        p.stop()
        handedOff = false
        if (!composed) {
            p.release()
            player = null
            isPlayingAnyThread = false
        } else {
            setVideo(p, on = true)
        }
    }

    /** The service went away: drop the player if no screen is using it. */
    fun onServiceDestroyed() {
        handedOff = false
        if (!composed) {
            player?.release()
            player = null
            isPlayingAnyThread = false
        }
    }

    /** Full exit: nothing may keep decoding. */
    fun shutdown(context: Context) {
        player?.let { it.stop(); it.release() }
        player = null
        isPlayingAnyThread = false
        composed = false
        handedOff = false
        context.stopService(Intent(context, PlaybackService::class.java))
    }

    private fun setVideo(p: ExoPlayer, on: Boolean) {
        if (videoOff == !on) return
        videoOff = !on
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !on)
            .build()
    }
}
