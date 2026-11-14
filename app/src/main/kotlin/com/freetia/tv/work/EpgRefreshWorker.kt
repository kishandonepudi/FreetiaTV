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
import com.freetia.tv.data.epg.EpgImporter
import com.freetia.tv.data.epg.EpgOutcome
import com.freetia.tv.data.epg.EpgRules
import com.freetia.tv.data.sync.SyncEvent
import com.freetia.tv.data.sync.SyncEvents
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Refreshes the XMLTV guide (§3.9) behind the UI; each playlist is one unit and memory pressure defers the rest. */
class EpgRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as FreetiaApp
        val only = inputData.getLong(KEY_PLAYLIST, 0L)
        val force = inputData.getBoolean(KEY_FORCE, false)
        val now = System.currentTimeMillis()
        val interval = app.settings.epgRefreshIntervalHours.get()
        val importer = EpgImporter(app)

        val playlists = app.database.playlistDao().getAll().filter {
            if (only != 0L) it.id == only else force || EpgRules.isDue(it.epgLastUpdateTime, interval, now)
        }
        var retry = false
        for (playlist in playlists) {
            if (isStopped) break
            if (app.memoryGuard.shouldDeferBackgroundWork()) return Result.retry()
            when (importer.refresh(playlist) { app.memoryGuard.shouldDeferBackgroundWork() }) {
                EpgOutcome.Deferred -> return Result.retry()
                EpgOutcome.Failed -> retry = true
                else -> Unit
            }
        }
        return if (retry && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    companion object {
        private const val KEY_PLAYLIST = "playlist"
        private const val KEY_FORCE = "force"
        private const val PERIODIC_NAME = "epg_refresh_periodic"
        private const val MAX_ATTEMPTS = 3

        private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Called once from the Application: follows the refresh interval setting and refreshes after playlist syncs. */
        fun start(app: FreetiaApp) {
            app.appScope.launch {
                app.settings.epgRefreshIntervalHours.flow.distinctUntilChanged().collect { hours ->
                    schedulePeriodic(app, hours)
                }
            }
            app.appScope.launch {
                SyncEvents.flow.collect { event ->
                    if (event is SyncEvent.Updated) enqueue(app, playlistId = event.playlistId, force = true)
                }
            }
            enqueue(app)
        }

        fun schedulePeriodic(context: Context, hours: Int) {
            val request = PeriodicWorkRequestBuilder<EpgRefreshWorker>(hours.coerceIn(1, 168).toLong(), TimeUnit.HOURS)
                .setConstraints(connected)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** One run now: playlists that are due, or one playlist / all of them when [playlistId] / [force] say so. */
        fun enqueue(context: Context, playlistId: Long = 0L, force: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<EpgRefreshWorker>()
                .setInputData(workDataOf(KEY_PLAYLIST to playlistId, KEY_FORCE to force))
                .setConstraints(connected)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("epg_refresh", if (force) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP, request)
        }
    }
}
