package com.freetia.tv.core.system

import android.app.Activity
import android.os.Process
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import com.freetia.tv.FreetiaApp
import com.freetia.tv.playback.PlaybackHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * The one full exit (§3.12), used by double-Back, Settings "Exit app" and the sleep timer: stop playback, close the
 * session and service, run the exit cache clear when it is on, finish the task and end the process.
 */
@androidx.annotation.OptIn(UnstableApi::class)
object AppExit {
    const val DIM_MS = 2_500L

    private val running = AtomicBoolean(false)
    private var activity: WeakReference<Activity>? = null

    fun attach(a: Activity) { activity = WeakReference(a) }

    fun detach(a: Activity) { if (activity?.get() === a) activity = null }

    fun exit(app: FreetiaApp) {
        if (!running.compareAndSet(false, true)) return
        app.appScope.launch {
            withContext(NonCancellable + Dispatchers.Main) {
                app.sleepTimer.stop()
                PlaybackHost.shutdown(app)
            }
            // Read from the synchronous mirror: this must finish before the process ends.
            if (app.cacheManager.policy.clearOnExit) app.cacheManager.clearForExit(app.imageLoader)
            withContext(Dispatchers.Main) { activity?.get()?.finishAndRemoveTask() }
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }
    }

    /** Sleep timer end: pause, give the dim overlay a moment when the screen is showing, then the same full exit. */
    fun sleepExit(app: FreetiaApp) {
        app.appScope.launch {
            withContext(Dispatchers.Main) {
                PlaybackHost.playerForService()?.pause()
                val visible = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                if (visible) delay(DIM_MS)
            }
            exit(app)
        }
    }
}
