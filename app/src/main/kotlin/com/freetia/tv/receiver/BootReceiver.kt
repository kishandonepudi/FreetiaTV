package com.freetia.tv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.freetia.tv.BuildConfig
import com.freetia.tv.data.epg.ReminderScheduler
import com.freetia.tv.work.AutoStartWorker

/** Hands boot to AutoStartWorker, which returns early unless AUTO_START_ON_TV is on (default off). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            if (BuildConfig.DEBUG) Log.d("BootReceiver", "Device boot detected")

            val workRequest = OneTimeWorkRequestBuilder<AutoStartWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
            ReminderScheduler.rescheduleAll(context)
        }
    }
}
