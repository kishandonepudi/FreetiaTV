package com.freetia.tv.ui.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.annotation.OptIn
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.util.Locale

data class AudioOption(val language: String, val label: String)

@OptIn(UnstableApi::class)
fun Tracks.audioOptions(): List<AudioOption> =
    groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        .mapIndexed { i, g ->
            val lang = g.mediaTrackGroup.getFormat(0).language.orEmpty().takeIf { it != C.LANGUAGE_UNDETERMINED }.orEmpty()
            val name = if (lang.isBlank()) "Track ${i + 1}" else Locale(lang).getDisplayLanguage(Locale.getDefault()).ifBlank { lang }
            AudioOption(lang, name.replaceFirstChar { it.uppercase() })
        }
        .distinctBy { it.language.ifBlank { it.label } }

/** Distinct video heights on offer, tallest first. */
fun Tracks.videoHeights(): List<Int> =
    groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g.getTrackFormat(it).height } }
        .filter { it > 0 }.distinct().sortedDescending()

fun lowerHeight(heights: List<Int>, current: Int): Int? = heights.filter { it < current }.maxOrNull()

/** Lock to [height], or return to adaptive selection when null. */
fun Player.selectResolution(height: Int?) {
    val b = trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
    if (height != null) {
        for (g in currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }) {
            val idx = (0 until g.length).firstOrNull { g.isTrackSupported(it) && g.getTrackFormat(it).height == height } ?: continue
            b.addOverride(TrackSelectionOverride(g.mediaTrackGroup, listOf(idx)))
            break
        }
    }
    trackSelectionParameters = b.build()
}

fun Player.setCaptionsEnabled(enabled: Boolean) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled).build()
}

fun Player.setPreferredAudio(language: String?) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setPreferredAudioLanguage(language).build()
}

fun captionColor(name: String): Int = when (name.lowercase()) {
    "yellow" -> 0xFFFFEB3B.toInt()
    "green" -> 0xFF4CAF50.toInt()
    "cyan" -> 0xFF00E5FF.toInt()
    "blue" -> 0xFF2196F3.toInt()
    "red" -> 0xFFF44336.toInt()
    "black" -> 0xFF000000.toInt()
    "transparent" -> 0x00000000
    else -> 0xFFFFFFFF.toInt()
}
