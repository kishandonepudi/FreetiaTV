package com.freetia.tv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.epg.ReminderNotifier
import com.freetia.tv.data.epg.ReminderScheduler
import android.util.Log
import com.freetia.tv.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Not exported: only our own PendingIntents reach it. The extra is a row id; programme names are read from Room. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION) return
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0) return
        val app = context.applicationContext as FreetiaApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderNotifier.fire(app, reminderId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A broken reminder must not crash the app from the background. Type only: messages may quote titles.
                if (BuildConfig.DEBUG) Log.w("ReminderReceiver", "Reminder failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }
}
