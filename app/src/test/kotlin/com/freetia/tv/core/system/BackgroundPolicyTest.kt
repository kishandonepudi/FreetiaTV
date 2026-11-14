package com.freetia.tv.core.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundPolicyTest {
    @Test fun homeWithoutBackgroundPlayStopsPlayback() {
        val action = BackgroundPolicy.onActivityStop(backgroundAllowed = false, playing = true)
        assertTrue(action.stopPlayback)
        assertFalse(action.keepAudio)
    }

    @Test fun homeWithBackgroundPlayKeepsAudioAndDropsVideo() {
        assertEquals(
            StopAction(stopPlayback = false, keepAudio = true, disableVideo = true),
            BackgroundPolicy.onActivityStop(backgroundAllowed = true, playing = true),
        )
    }

    @Test fun pausedPlaybackIsNotKeptAlive() {
        assertTrue(BackgroundPolicy.onActivityStop(backgroundAllowed = true, playing = false).stopPlayback)
    }

    @Test fun pipNeedsPhoneSupportBackgroundPlayAndPlayback() {
        assertTrue(BackgroundPolicy.shouldEnterPip(isTv = false, pipSupported = true, backgroundAllowed = true, playing = true))
        assertFalse(BackgroundPolicy.shouldEnterPip(isTv = true, pipSupported = true, backgroundAllowed = true, playing = true))
        assertFalse(BackgroundPolicy.shouldEnterPip(isTv = false, pipSupported = false, backgroundAllowed = true, playing = true))
        assertFalse(BackgroundPolicy.shouldEnterPip(isTv = false, pipSupported = true, backgroundAllowed = false, playing = true))
        assertFalse(BackgroundPolicy.shouldEnterPip(isTv = false, pipSupported = true, backgroundAllowed = true, playing = false))
    }

    @Test fun autoStartSetupShowsOnlyForOptedInTvsThatMayBeBlocked() {
        assertTrue(AutoStartAdvisor.shouldShowSetup(true, false, true, 34, AutoStartResult.NEVER_TRIED))
        assertFalse(AutoStartAdvisor.shouldShowSetup(false, false, true, 34, AutoStartResult.NEVER_TRIED))
        assertFalse(AutoStartAdvisor.shouldShowSetup(true, true, true, 34, AutoStartResult.BLOCKED))
        assertFalse(AutoStartAdvisor.shouldShowSetup(true, false, false, 34, AutoStartResult.BLOCKED))
        assertFalse(AutoStartAdvisor.shouldShowSetup(true, false, true, 28, AutoStartResult.NEVER_TRIED))
        assertFalse(AutoStartAdvisor.shouldShowSetup(true, false, true, 34, AutoStartResult.LAUNCHED))
        assertTrue(AutoStartAdvisor.shouldShowSetup(true, false, true, 28, AutoStartResult.BLOCKED))
    }
}
