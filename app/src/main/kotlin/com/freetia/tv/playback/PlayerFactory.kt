package com.freetia.tv.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.settings.BufferMode


/** The four DefaultLoadControl durations. ExoPlayer requires playback/rebuffer <= min <= max. */
data class BufferValues(val minMs: Int, val maxMs: Int, val playbackMs: Int, val rebufferMs: Int)

fun bufferValues(mode: BufferMode, customMinMs: Int = 10_000, customMaxMs: Int = 30_000): BufferValues {
    val (min, max) = when (mode) {
        BufferMode.LOW_LATENCY -> 2_000 to 5_000
        BufferMode.BALANCED -> 10_000 to 20_000
        BufferMode.STABLE -> 30_000 to 50_000
        BufferMode.CUSTOM -> {
            val lo = customMinMs.coerceAtLeast(1_000)
            lo to customMaxMs.coerceAtLeast(lo)
        }
    }
    return BufferValues(min, max, playbackMs = minOf(1_500, min), rebufferMs = minOf(2_000, min))
}

@UnstableApi
object PlayerFactory {
    fun create(context: Context, buffer: BufferValues = bufferValues(BufferMode.BALANCED)): ExoPlayer {
        val app = context.applicationContext as FreetiaApp
        val dataSource = OkHttpDataSource.Factory(app.httpClient)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(buffer.minMs, buffer.maxMs, buffer.playbackMs, buffer.rebufferMs)
            .build()
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    /** Muted, low-resolution player for the channel-list live preview; never takes audio focus. */
    fun createPreview(context: Context): ExoPlayer {
        val app = context.applicationContext as FreetiaApp
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                // setMaxVideoSize alone is only a preference: for a single-variant stream ExoPlayer would
                // still pick the 1080p track and spin up a full-HD decoder. With these flags such streams
                // select no video track instead, so the preview shows the logo fallback.
                buildUponParameters()
                    .setMaxVideoSize(854, 480)
                    .setExceedVideoConstraintsIfNecessary(false)
                    .setExceedRendererCapabilitiesIfNecessary(false)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            )
        }
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(app.httpClient)))
            .setTrackSelector(trackSelector)
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(2_000, 5_000, 1_000, 1_500).build())
            .build()
            .apply { volume = 0f }
    }
}
