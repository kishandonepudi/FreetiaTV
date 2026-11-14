package com.freetia.tv.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.epg.DAY_MS
import com.freetia.tv.data.epg.ReminderMath
import com.freetia.tv.data.epg.ReminderNotifier
import com.freetia.tv.data.epg.ReminderScheduler

/**
 * Catch-up pass after a reboot or a permission change: shows reminders that came due while the device was off (only
 * while still relevant), re-arms every future one, and drops reminders that ended long ago.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FreetiaApp
        val dao = app.database.reminderDao()
        val now = System.currentTimeMillis()

        dao.getDue(now).forEach { reminder ->
            if (ReminderMath.isStillRelevant(reminder.reminderTime, reminder.stopTime, now)) {
                ReminderNotifier.fire(app, reminder.id)
            } else {
                dao.markNotified(reminder.id)
            }
        }
        dao.getFuture(now).forEach { ReminderScheduler.schedule(app, it, now) }
        dao.deleteEnded(now - DAY_MS)
        return Result.success()
    }
}
