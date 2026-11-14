package com.freetia.tv.core.timer

import android.os.CountDownTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Must be driven from the main thread (CountDownTimer needs a Looper). */
class SleepTimerManager {
    private var countDownTimer: CountDownTimer? = null

    private val _timerState = MutableStateFlow<TimerState>(TimerState.Inactive)
    val timerState: StateFlow<TimerState> = _timerState

    fun startTimer(durationMinutes: Int) {
        startTimerMs(durationMinutes * 60_000L)
    }

    fun startTimerMs(durationMs: Long) {
        countDownTimer?.cancel()
        _timerState.value = stateFor(durationMs, durationMs)

        countDownTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _timerState.value = stateFor(durationMs, millisUntilFinished)
            }

            override fun onFinish() {
                countDownTimer = null
                _timerState.value = TimerState.Expired
            }
        }.start()
    }

    fun addTime(additionalMinutes: Int) {
        val remaining = remainingMs() ?: return
        startTimerMs(remaining + additionalMinutes * 60_000L)
    }

    fun stopTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        _timerState.value = TimerState.Inactive
    }

    fun pauseTimer() {
        val remaining = remainingMs() ?: return
        countDownTimer?.cancel()
        countDownTimer = null
        _timerState.value = TimerState.Paused(remaining)
    }

    fun resumeTimer() {
        val current = _timerState.value
        if (current is TimerState.Paused) startTimerMs(current.remainingMs)
    }

    private fun remainingMs(): Long? = when (val s = _timerState.value) {
        is TimerState.Active -> s.remainingMs
        is TimerState.FinalWarning -> s.remainingMs
        is TimerState.Paused -> s.remainingMs
        else -> null
    }

    private fun stateFor(totalMs: Long, remainingMs: Long): TimerState =
        if (remainingMs <= FINAL_WARNING_MS) TimerState.FinalWarning(remainingMs)
        else TimerState.Active(totalMs, remainingMs)

    sealed class TimerState {
        object Inactive : TimerState()
        data class Active(val totalMs: Long, val remainingMs: Long) : TimerState()
        data class Paused(val remainingMs: Long) : TimerState()
        data class FinalWarning(val remainingMs: Long) : TimerState()
        object Expired : TimerState()
    }

    companion object {
        const val FINAL_WARNING_MS = 60_000L
    }
}
