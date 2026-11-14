package com.freetia.tv.core.util

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.freetia.tv.playback.PlaybackHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single source of truth for memory decisions (see standards/ENGINEERING_STANDARDS.md, "Memory budget").
 * Optional, memory-hungry features (live preview, large image caches) must check this before running.
 */
class MemoryGuard(context: Context, private val scope: CoroutineScope) {
    private val am = context.getSystemService(ActivityManager::class.java)

    /** True on Fire TV Stick Lite class hardware (about 1 GB RAM) or when the OS flags the device as low-RAM. */
    val isLowRamDevice: Boolean = am.isLowRamDevice ||
        ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem < LOW_RAM_TOTAL_BYTES

    private val pressure = MutableStateFlow(false)
    val underPressure: StateFlow<Boolean> = pressure.asStateFlow()
    private var resetJob: Job? = null

    fun onTrimMemory(level: Int) {
        // UI_HIDDEN (20) and BACKGROUND+ (40+) are lifecycle signals sent on every Home press, not
        // foreground pressure, so only the RUNNING_* band raises the flag directly.
        if (isRunningPressure(level)) raise() else sample()
    }

    fun onLowMemory() = raise()

    /**
     * Cheap foreground pressure probe. RUNNING_* trim levels are not delivered on API 34+, so the app's trim
     * callback and the UI (before starting the live preview) poll the OS low-memory flag instead.
     * Returns the resulting pressure state.
     */
    fun sample(): Boolean {
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        if (info.lowMemory) raise()
        return pressure.value
    }

    /**
     * Background workers check this between batches and stop early (and retry later) when true. On a 1 GB device
     * a refresh or guide import next to the decoder risks the low-memory killer taking the player, so it waits.
     */
    @OptIn(UnstableApi::class)
    fun shouldDeferBackgroundWork(): Boolean =
        pressure.value || (isLowRamDevice && PlaybackHost.isPlayingAnyThread)

    private fun raise() {
        pressure.value = true
        // Pressure is transient; re-enable optional features once the system has had time to recover.
        resetJob?.cancel()
        resetJob = scope.launch {
            delay(PRESSURE_COOLDOWN_MS)
            pressure.value = false
        }
    }

    fun livePreviewAllowed(underPressure: Boolean): Boolean = !isLowRamDevice && !underPressure

    companion object {
        private const val LOW_RAM_TOTAL_BYTES = 1_300L * 1024 * 1024
        private const val PRESSURE_COOLDOWN_MS = 120_000L

        // The RUNNING_* constants are deprecated on API 34+ but still delivered on older devices.
        @Suppress("DEPRECATION")
        fun isRunningPressure(level: Int): Boolean =
            level in ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW..ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
    }
}
