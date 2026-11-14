package com.freetia.tv.data.epg

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.freetia.tv.FreetiaApp
import com.freetia.tv.R
import com.freetia.tv.core.safety.ChannelFilter
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.ReminderEntity
import com.freetia.tv.receiver.ReminderReceiver
import com.freetia.tv.ui.MainActivity
import com.freetia.tv.work.ReminderWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Pure rules for the one-time token on a reminder's "Watch" intent. MainActivity is exported, so any app could send it
 * a channel id; only an intent carrying the random nonce we stored when posting the notification is honoured, and
 * the channel comes from our stored record, never from the intent.
 */
internal object ReminderNonce {
    const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    private val random = SecureRandom()

    fun newNonce(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun encode(nonce: String, channelId: Long, issuedAt: Long) = "$nonce:$channelId:$issuedAt"

    /** The channel to tune, or null unless [given] matches the [stored] record and it is fresh. */
    fun channelFor(stored: String?, given: String?, now: Long): Long? {
        if (stored == null || given.isNullOrEmpty()) return null
        val parts = stored.split(':')
        if (parts.size != 3) return null
        val channelId = parts[1].toLongOrNull() ?: return null
        val issuedAt = parts[2].toLongOrNull() ?: return null
        if (isExpired(issuedAt, now)) return null
        val match = MessageDigest.isEqual(parts[0].toByteArray(), given.toByteArray())
        return if (match && channelId > 0) channelId else null
    }

    fun isExpired(issuedAt: Long, now: Long) = now - issuedAt !in 0..MAX_AGE_MS
}

/** A "Watch" tap (or the full-screen intent) reaches MainActivity with a reminder id and nonce; the nav host tunes once. */
object ReminderLaunch {
    const val EXTRA_REMINDER_ID = "reminder_id"
    const val EXTRA_NONCE = "reminder_nonce"
    private const val LEGACY_EXTRA_CHANNEL_ID = "channel_id"
    private const val PREFS = "reminder_launch"

    private val mutable = MutableStateFlow<Long?>(null)
    val pending: StateFlow<Long?> = mutable

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Stores a fresh nonce for [reminderId] and returns it for the PendingIntent extras. Drops stale entries. */
    fun issue(context: Context, reminderId: Long, channelId: Long, now: Long = System.currentTimeMillis()): String {
        val prefs = prefs(context)
        val nonce = ReminderNonce.newNonce()
        val edit = prefs.edit()
        prefs.all.forEach { (key, value) ->
            val issuedAt = (value as? String)?.substringAfterLast(':')?.toLongOrNull()
            if (issuedAt == null || ReminderNonce.isExpired(issuedAt, now)) edit.remove(key)
        }
        edit.putString(reminderId.toString(), ReminderNonce.encode(nonce, channelId, now)).apply()
        return nonce
    }

    fun revoke(context: Context, reminderId: Long) {
        prefs(context).edit().remove(reminderId.toString()).apply()
    }

    fun fromIntent(context: Context, intent: Intent?) {
        intent ?: return
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val nonce = intent.getStringExtra(EXTRA_NONCE)
        intent.removeExtra(EXTRA_REMINDER_ID)
        intent.removeExtra(EXTRA_NONCE)
        intent.removeExtra(LEGACY_EXTRA_CHANNEL_ID)
        if (reminderId <= 0 || nonce == null) return
        val key = reminderId.toString()
        val prefs = prefs(context)
        val channelId = ReminderNonce.channelFor(prefs.getString(key, null), nonce, System.currentTimeMillis()) ?: return
        prefs.edit().remove(key).apply()
        mutable.value = channelId
    }

    fun consume() { mutable.value = null }
}

/** Which of the optional reminder permissions the user still has to grant, for the UI to explain. */
object ReminderPermissions {
    fun notificationsAllowed(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** True where a runtime prompt can still be shown (API 33+, and not yet granted). */
    fun needsNotificationPrompt(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** Exact alarms are an opt-in on API 31+ (Settings, "Alarms & reminders"); below that they are always allowed. */
    fun exactAlarmsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

    fun exactAlarmSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= 31) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        } else null

    /** API 34+ lets the user revoke full-screen intents; before that the manifest permission is enough. */
    fun fullScreenAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || NotificationManagerCompat.from(context).canUseFullScreenIntent()

    fun fullScreenSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= 34) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
        } else null
}

/** AlarmManager side of reminders: exact when permitted, otherwise an inexact alarm that may arrive a few minutes late. */
object ReminderScheduler {
    const val ACTION = "com.freetia.action.REMINDER_ALARM"
    const val EXTRA_REMINDER_ID = "reminder_id"

    private fun pending(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION).putExtra(EXTRA_REMINDER_ID, reminderId)
        return PendingIntent.getBroadcast(
            context, reminderId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Arms the alarm; returns false when the programme has already started. */
    fun schedule(context: Context, reminder: ReminderEntity, now: Long = System.currentTimeMillis()): Boolean {
        val at = ReminderMath.fireAt(reminder.reminderTime, reminder.startTime, now) ?: return false
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val operation = pending(context, reminder.id)
        if (ReminderPermissions.exactAlarmsAllowed(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
        return true
    }

    fun cancel(context: Context, reminderId: Long) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context, reminderId))
    }

    /** After a reboot (BootReceiver) or a permission change: ReminderWorker re-arms every future reminder. */
    fun rescheduleAll(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "reminder_reschedule", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>().build(),
        )
    }
}

class ReminderRepository(private val app: FreetiaApp) {
    private val dao = app.database.reminderDao()

    fun observeUpcoming(now: Long) = dao.observeUpcoming(now)

    fun observeHas(channelId: Long, startTime: Long) = dao.observeHasReminder(channelId, startTime)

    suspend fun set(channel: ChannelEntity, programmeId: Long, title: String, start: Long, stop: Long): ReminderEntity {
        val s = app.settings
        val lead = s.reminderLeadMinutes.get()
        val reminder = ReminderEntity(
            programmeId = programmeId,
            channelId = channel.id,
            title = title.take(200),
            reminderTime = ReminderMath.reminderTime(start, lead),
            startTime = start,
            stopTime = stop,
            wakeScreen = s.reminderWakeScreen.get(),
            autoTune = s.reminderAutoTune.get(),
        )
        val id = dao.upsert(reminder)
        val saved = reminder.copy(id = id)
        ReminderScheduler.schedule(app, saved)
        return saved
    }

    suspend fun cancel(reminder: ReminderEntity) {
        ReminderScheduler.cancel(app, reminder.id)
        ReminderLaunch.revoke(app, reminder.id)
        dao.deleteById(reminder.id)
    }

    suspend fun cancelForProgramme(channelId: Long, startTime: Long) {
        dao.findForProgramme(channelId, startTime)?.let { cancel(it) }
    }
}

/** Posts the reminder notification. Programme names only ever go into the notification itself, never a log. */
object ReminderNotifier {
    private const val CHANNEL_ID = "freetia_reminders"

    suspend fun fire(app: FreetiaApp, reminderId: Long) {
        val dao = app.database.reminderDao()
        val reminder = dao.getById(reminderId) ?: return
        if (reminder.isNotified) return
        val channel = app.database.channelDao().getById(reminder.channelId)
        dao.markNotified(reminder.id)
        if (channel == null || !ReminderPermissions.notificationsAllowed(app)) return
        // A reminder set before Child Safe Mode came on must not show an adult title or wake the screen to it.
        if (ChannelFilter.isHidden(channel, app.settings.childSafeMode.get())) return
        post(app, reminder, channel, ReminderLaunch.issue(app, reminder.id, channel.id))
    }

    private fun post(context: Context, reminder: ReminderEntity, channel: ChannelEntity, nonce: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val watch = PendingIntent.getActivity(
            context, reminder.id.toInt(), watchIntent(context, reminder.id, nonce),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_brand_mark)
            .setContentTitle(reminder.title)
            .setContentText("On ${channel.name}")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(watch)
            .setAutoCancel(true)
            .addAction(0, "Watch", watch)
        if ((reminder.wakeScreen || reminder.autoTune) && ReminderPermissions.fullScreenAllowed(context)) {
            builder.setFullScreenIntent(watch, true)
        }
        manager.notify(reminder.id.toInt(), builder.build())
    }

    private fun watchIntent(context: Context, reminderId: Long, nonce: String) =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ReminderLaunch.EXTRA_REMINDER_ID, reminderId)
            .putExtra(ReminderLaunch.EXTRA_NONCE, nonce)
}
