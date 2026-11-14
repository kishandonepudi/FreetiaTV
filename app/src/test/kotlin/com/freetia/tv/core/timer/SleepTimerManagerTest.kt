package com.freetia.tv.core.timer

import com.freetia.tv.core.timer.SleepTimerManager.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerManagerTest {
    private var now = 0L
    private var expiredCalls = 0
    private val timer = SleepTimerManager(CoroutineScope(Dispatchers.Unconfined), { now }, { expiredCalls++ }, autoTick = false)

    private fun advance(ms: Long) { now += ms; timer.tick() }

    @Test fun startsActiveWithTheRequestedLength() {
        timer.start(60)
        assertEquals(TimerState.Active(3_600_000L, 3_600_000L), timer.timerState.value)
    }

    @Test fun warnsAtSixtySecondsLeft() {
        timer.start(10)
        advance(10 * 60_000L - 61_000L)
        assertTrue(timer.timerState.value is TimerState.Active)
        advance(1_000L)
        assertEquals(TimerState.FinalWarning(60_000L), timer.timerState.value)
        assertEquals(0, expiredCalls)
    }

    @Test fun addTimeLeavesTheWarningAndExtendsTheDeadline() {
        timer.start(10)
        advance(10 * 60_000L - 30_000L)
        assertTrue(timer.timerState.value is TimerState.FinalWarning)
        timer.addTime(15)
        assertEquals(TimerState.Active(15 * 60_000L + 30_000L, 15 * 60_000L + 30_000L), timer.timerState.value)
        advance(15 * 60_000L)
        assertTrue(timer.timerState.value is TimerState.FinalWarning)
    }

    @Test fun addTimeIsCappedAtTheMaximum() {
        timer.start(240)
        timer.addTime(30)
        assertEquals(240 * 60_000L, (timer.timerState.value as TimerState.Active).remainingMs)
    }

    @Test fun addTimeWithoutATimerDoesNothing() {
        timer.addTime(15)
        assertEquals(TimerState.Inactive, timer.timerState.value)
    }

    @Test fun expiryCallsExitOnce() {
        timer.start(10)
        advance(10 * 60_000L)
        assertEquals(TimerState.Expired, timer.timerState.value)
        assertEquals(1, expiredCalls)
        advance(5_000L)
        assertEquals(1, expiredCalls)
    }

    @Test fun lateTicksStillExpire() {
        timer.start(10)
        advance(60 * 60_000L)
        assertEquals(1, expiredCalls)
    }

    @Test fun continueWatchingCancelsTheSleep() {
        timer.start(10)
        advance(10 * 60_000L - 59_000L)
        timer.continueWatching()
        assertEquals(TimerState.Inactive, timer.timerState.value)
        assertFalse(timer.isRunning())
        advance(10 * 60_000L)
        assertEquals(0, expiredCalls)
    }

    @Test fun minutesSnapToTheAllowedRange() {
        assertEquals(10, SleepTimerManager.clampMinutes(1))
        assertEquals(240, SleepTimerManager.clampMinutes(999))
        assertEquals(60, SleepTimerManager.clampMinutes(60))
        assertEquals(70, SleepTimerManager.clampMinutes(67))
    }
}
