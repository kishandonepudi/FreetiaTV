package com.freetia.tv.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.freetia.tv.BuildConfig
import com.freetia.tv.R
import com.freetia.tv.core.system.AutoStartResult
import com.freetia.tv.core.system.AutoStartStatus
import com.freetia.tv.core.util.DeviceDetector
import com.freetia.tv.data.preferences.PreferencesKeys
import com.freetia.tv.dataStore
import com.freetia.tv.ui.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Opens the app after boot, but only on a TV and only when the user has turned on auto-start. LibraryHost then
 * resumes the default playlist and its last channel. The outcome is recorded so the setup screen can explain a block.
 */
class AutoStartWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Launching on boot without consent breaks store policy, so this stays off unless the user opts in.
        val optedIn = applicationContext.dataStore.data.first()[PreferencesKeys.AUTO_START_ON_TV] == true
        if (!optedIn || !DeviceDetector.isTV(applicationContext)) return Result.success()

        val launch = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val thrown = runCatching { applicationContext.startActivity(launch) }.isFailure
        // Android 10+ drops a background launch without an exception, so the only proof is the app becoming visible.
        val visible = !thrown && waitUntilVisible()
        AutoStartStatus.set(applicationContext, if (visible) AutoStartResult.LAUNCHED else AutoStartResult.BLOCKED)
        if (!visible) {
            if (BuildConfig.DEBUG) Log.w("AutoStartWorker", "Auto-start launch blocked")
            postOpenNotification()
        }
        return Result.success()
    }

    private suspend fun waitUntilVisible(): Boolean {
        repeat(VISIBLE_POLLS) {
            if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return true
            delay(POLL_MS)
        }
        return false
    }

    /** The next best thing to opening: a tap-to-open notification, if the user has allowed notifications. */
    private fun postOpenNotification() {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.autostart_channel_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_brand_mark)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.autostart_blocked_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "autostart"
        const val NOTIFICATION_ID = 4101
        const val VISIBLE_POLLS = 10
        const val POLL_MS = 500L
    }
}
