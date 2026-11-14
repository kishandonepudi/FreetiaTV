package com.freetia.tv.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.freetia.tv.FreetiaApp

enum class BufferPreset(val minMs: Int, val maxMs: Int) {
    LOW_LATENCY(2_000, 5_000),
    BALANCED(10_000, 20_000),
    STABLE(30_000, 50_000),
}

@UnstableApi
object PlayerFactory {
    fun create(context: Context, preset: BufferPreset = BufferPreset.BALANCED): ExoPlayer {
        val app = context.applicationContext as FreetiaApp
        val dataSource = OkHttpDataSource.Factory(app.httpClient)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(preset.minMs, preset.maxMs, 1_500, 2_000)
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
            .build()
    }
}
