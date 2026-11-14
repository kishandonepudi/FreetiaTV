package com.freetia.tv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.freetia.tv.work.AutoStartWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootReceiver", "Device boot detected")

            val workRequest = OneTimeWorkRequestBuilder<AutoStartWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
