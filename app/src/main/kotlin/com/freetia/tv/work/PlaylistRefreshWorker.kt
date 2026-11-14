package com.freetia.tv.work

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.sync.SyncResult

/** Stale-while-revalidate: the UI already shows cached channels; this refreshes them behind it. */
class PlaylistRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as FreetiaApp
        val onlyStartup = inputData.getBoolean(KEY_STARTUP, true)
        val force = inputData.getBoolean(KEY_FORCE, false)
        val playlists = app.database.playlistDao().getAll()
            .filter { !onlyStartup || it.updateOnStartup }

        for (playlist in playlists) {
            if (isStopped) break
            when (val result = app.playlistSyncer.sync(playlist, force)) {
                is SyncResult.Updated -> if (result.added > 0 || result.removed > 0) {
                    toast("${playlist.name}: +${result.added} / −${result.removed} channels")
                }
                is SyncResult.Failed -> toast("${playlist.name}: update failed, showing saved channels")
                is SyncResult.Skipped -> Unit
            }
        }
        return Result.success()
    }

    private fun toast(text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val KEY_STARTUP = "startup_only"
        private const val KEY_FORCE = "force"

        /** Playlists with "Update when app starts" off make no network request here. */
        fun enqueueStartup(context: Context) = enqueue(context, startupOnly = true, force = false)

        fun enqueueAll(context: Context, force: Boolean = true) = enqueue(context, startupOnly = false, force = force)

        private fun enqueue(context: Context, startupOnly: Boolean, force: Boolean) {
            val request = OneTimeWorkRequestBuilder<PlaylistRefreshWorker>()
                .setInputData(workDataOf(KEY_STARTUP to startupOnly, KEY_FORCE to force))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("playlist_refresh", ExistingWorkPolicy.KEEP, request)
        }
    }
}
