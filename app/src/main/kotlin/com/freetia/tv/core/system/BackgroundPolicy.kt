package com.freetia.tv.core.system

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the player does when the activity stops (Home, screen off, PiP closed). */
data class StopAction(val stopPlayback: Boolean, val keepAudio: Boolean, val disableVideo: Boolean)

/** Pure rules for Home handling (§3.11) so they can be tested without a device. */
object BackgroundPolicy {
    /**
     * Home keeps audio only when the playlist allows background play and something is playing. Video is always
     * dropped in the background (the TV rule, and it frees the decoder on phones); PiP keeps the activity
     * visible, so it never reaches this decision.
     */
    fun onActivityStop(backgroundAllowed: Boolean, playing: Boolean): StopAction =
        if (backgroundAllowed && playing) StopAction(stopPlayback = false, keepAudio = true, disableVideo = true)
        else StopAction(stopPlayback = true, keepAudio = false, disableVideo = false)

    /** PiP is phone/tablet only, needs background play on for the playlist, and only while playing. */
    fun shouldEnterPip(isTv: Boolean, pipSupported: Boolean, backgroundAllowed: Boolean, playing: Boolean): Boolean =
        !isTv && pipSupported && backgroundAllowed && playing
}

/** True while the activity is in a picture-in-picture window; the player hides its overlays then. */
object PipState {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    fun set(inPip: Boolean) { _active.value = inPip }
}
