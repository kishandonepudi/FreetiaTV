package com.freetia.tv.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.sync.HealthChecker
import com.freetia.tv.data.sync.SyncPolicy
import java.util.concurrent.TimeUnit

/**
 * Stale-while-revalidate: the UI already shows cached channels; this refreshes them behind it. Added/removed counts
 * and failures reach the user as BrandToasts through `SyncEvents` (a worker has no UI).
 */
class PlaylistRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as FreetiaApp
        val mode = inputData.getString(KEY_MODE) ?: MODE_STARTUP
        val force = inputData.getBoolean(KEY_FORCE, false)
        val now = System.currentTimeMillis()
        val playlists = app.database.playlistDao().getAll().filter {
            when (mode) {
                MODE_STARTUP -> it.updateOnStartup
                MODE_PERIODIC -> SyncPolicy.dueInBackground(it.updateOnStartup, it.backgroundRefreshIntervalMinutes, it.lastUpdateTime, now)
                else -> true
            }
        }

        for (playlist in playlists) {
            if (isStopped) break
            // One playlist is one batch; under memory pressure the rest wait for the next run.
            if (app.memoryGuard.shouldDeferBackgroundWork()) return Result.retry()
            app.playlistSyncer.sync(playlist, force)
        }
        return Result.success()
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_FORCE = "force"
        private const val MODE_STARTUP = "startup"
        private const val MODE_PERIODIC = "periodic"
        private const val MODE_ALL = "all"
        private const val PERIODIC_NAME = "playlist_refresh_periodic"

        private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Playlists with "Update when app starts" off make no network request here. */
        fun enqueueStartup(context: Context) = enqueue(context, MODE_STARTUP, force = false)

        fun enqueueAll(context: Context, force: Boolean = true) = enqueue(context, MODE_ALL, force)

        /** Background refresh at each playlist's own interval; WorkManager's floor is 15 minutes. */
        fun ensurePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<PlaylistRefreshWorker>(1, TimeUnit.HOURS)
                .setInputData(workDataOf(KEY_MODE to MODE_PERIODIC))
                .setConstraints(connected)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun enqueue(context: Context, mode: String, force: Boolean) {
            val request = OneTimeWorkRequestBuilder<PlaylistRefreshWorker>()
                .setInputData(workDataOf(KEY_MODE to mode, KEY_FORCE to force))
                .setConstraints(connected)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("playlist_refresh", ExistingWorkPolicy.KEEP, request)
        }
    }
}

/** Health check (§3.15) as background work, for a library-wide run; the playlist screen can also run one inline. */
class HealthCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as FreetiaApp
        val checker = HealthChecker.get(app)
        for (playlist in app.database.playlistDao().getAll()) {
            if (isStopped) break
            if (app.memoryGuard.shouldDeferBackgroundWork()) return Result.retry()
            if (checker.run(playlist.id) < 0) return Result.retry()
        }
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<HealthCheckWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("health_check", ExistingWorkPolicy.KEEP, request)
        }
    }
}
