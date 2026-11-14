package com.freetia.tv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.freetia.tv.work.ReminderWorker

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.freetia.action.REMINDER_ALARM") {
            val reminderId = intent.getLongExtra("reminder_id", -1)
            val programmeName = intent.getStringExtra("programme_name") ?: "Programme"
            val channelId = intent.getLongExtra("channel_id", -1)

            Log.d("ReminderReceiver", "Reminder triggered for: $programmeName")

            val data = Data.Builder()
                .putLong("reminder_id", reminderId)
                .putString("programme_name", programmeName)
                .putLong("channel_id", channelId)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
