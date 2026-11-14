package com.freetia.tv.core.timer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Sleep timer (§3.10). The state is derived from a deadline on [clock], so it stays right however late a tick
 * arrives. It lives in the app process, which the playback service keeps alive while audio plays in the background.
 * [onExpired] runs once, outside the lock, after the state has become [TimerState.Expired].
 */
class SleepTimerManager(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val onExpired: () -> Unit,
    private val autoTick: Boolean = true,
) {
    private val _timerState = MutableStateFlow<TimerState>(TimerState.Inactive)
    val timerState: StateFlow<TimerState> = _timerState

    private var deadline = 0L
    private var totalMs = 0L
    private var ticker: Job? = null

    /** Starts (or restarts) the countdown; [minutes] is snapped to the 10..240 range in steps of 10. */
    fun start(minutes: Int) {
        synchronized(this) {
            totalMs = clampMinutes(minutes) * 60_000L
            deadline = clock() + totalMs
            _timerState.value = stateFor(totalMs, totalMs)
        }
        ensureTicker()
    }

    /** Adds to the remaining time (never beyond 240 minutes in total); ignored when no timer is running. */
    fun addTime(minutes: Int) {
        synchronized(this) {
            if (!isRunning()) return
            val remaining = (deadline - clock()).coerceAtLeast(0L)
            val next = (remaining + minutes * 60_000L).coerceIn(0L, MAX_MINUTES * 60_000L)
            deadline = clock() + next
            totalMs = maxOf(totalMs, next)
            _timerState.value = stateFor(totalMs, next)
        }
    }

    /** "Press OK to continue": cancels the pending sleep. */
    fun continueWatching() = stop()

    fun stop() {
        synchronized(this) {
            ticker?.cancel()
            ticker = null
            _timerState.value = TimerState.Inactive
        }
    }

    fun isRunning(): Boolean = _timerState.value.let { it is TimerState.Active || it is TimerState.FinalWarning }

    /** Recomputes the state from the clock. Called every second by the ticker; tests call it directly. */
    fun tick() {
        val expired = synchronized(this) {
            if (!isRunning()) return
            val remaining = deadline - clock()
            if (remaining <= 0L) {
                ticker?.cancel()
                ticker = null
                _timerState.value = TimerState.Expired
                true
            } else {
                _timerState.value = stateFor(totalMs, remaining)
                false
            }
        }
        if (expired) onExpired()
    }

    private fun ensureTicker() {
        if (!autoTick) return
        synchronized(this) {
            if (ticker?.isActive == true) return
            ticker = scope.launch {
                while (isActive) {
                    tick()
                    delay(1_000L)
                }
            }
        }
    }

    private fun stateFor(total: Long, remaining: Long): TimerState =
        if (remaining <= FINAL_WARNING_MS) TimerState.FinalWarning(remaining) else TimerState.Active(total, remaining)

    sealed class TimerState {
        data object Inactive : TimerState()
        data class Active(val totalMs: Long, val remainingMs: Long) : TimerState()
        data class FinalWarning(val remainingMs: Long) : TimerState()
        data object Expired : TimerState()
    }

    companion object {
        const val FINAL_WARNING_MS = 60_000L
        const val MIN_MINUTES = 10
        const val MAX_MINUTES = 240
        const val STEP_MINUTES = 10
        const val DEFAULT_MINUTES = 60
        val QUICK_ADD_MINUTES = listOf(15, 30)

        fun clampMinutes(minutes: Int): Int =
            ((minutes + STEP_MINUTES / 2) / STEP_MINUTES * STEP_MINUTES).coerceIn(MIN_MINUTES, MAX_MINUTES)
    }
}
