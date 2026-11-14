package com.freetia.tv.ui.diagnostics

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/** What the player is doing right now. Nothing here can identify a channel, playlist or person. */
data class PlaybackStats(
    val codec: String,
    val resolution: String,
    val bitrateKbps: Int,
    val droppedFrames: Int,
    val bufferMs: Long,
)

data class DeviceInfo(val appVersion: String, val build: String, val android: String, val device: String)

object DiagnosticsReport {
    fun codecLabel(mime: String?): String = when (mime?.lowercase()) {
        "video/avc" -> "H.264"
        "video/hevc" -> "H.265 (HEVC)"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/av01" -> "AV1"
        "video/mpeg2" -> "MPEG-2"
        "video/mp4v-es" -> "MPEG-4"
        null, "" -> "Unknown"
        else -> mime.substringAfter('/').take(24)
    }

    fun resolution(width: Int, height: Int): String = if (width > 0 && height > 0) "${width}x$height" else "Unknown"

    /** Plain-text report for the share sheet. Only the fields above go in: no links, channel names or account data. */
    fun build(device: DeviceInfo, stats: PlaybackStats?): String = buildString {
        appendLine("Freetia TV diagnostics")
        appendLine("App version: ${device.appVersion} (${device.build})")
        appendLine("Android: ${device.android}")
        appendLine("Device: ${device.device}")
        if (stats == null) {
            appendLine("Playback: nothing was playing")
        } else {
            appendLine("Video codec: ${stats.codec}")
            appendLine("Resolution: ${stats.resolution}")
            appendLine("Bitrate: ${if (stats.bitrateKbps > 0) "${stats.bitrateKbps} kbps" else "unknown"}")
            appendLine("Dropped frames: ${stats.droppedFrames}")
            appendLine("Buffer: ${stats.bufferMs / 1000.0} s")
        }
    }.trimEnd()
}

/**
 * The player is released when its screen is left, so Diagnostics (opened from Settings) would otherwise find
 * nothing. The player screen calls [capture] as it goes away, and this keeps the last numbers.
 */
@androidx.annotation.OptIn(UnstableApi::class)
object DiagnosticsSnapshot {
    @Volatile
    var last: PlaybackStats? = null
        private set

    fun capture(player: ExoPlayer) {
        read(player)?.let { last = it }
    }

    /** Main thread only, like the player. Null while no video format is known. */
    fun read(player: ExoPlayer): PlaybackStats? {
        val format: Format = player.videoFormat ?: return null
        val counters = player.videoDecoderCounters
        counters?.ensureUpdated()
        val bitrate = if (format.averageBitrate > 0) format.averageBitrate else format.bitrate.coerceAtLeast(0)
        return PlaybackStats(
            codec = DiagnosticsReport.codecLabel(format.sampleMimeType),
            resolution = DiagnosticsReport.resolution(format.width, format.height),
            bitrateKbps = bitrate / 1000,
            droppedFrames = counters?.droppedBufferCount ?: 0,
            bufferMs = player.totalBufferedDuration,
        )
    }
}
