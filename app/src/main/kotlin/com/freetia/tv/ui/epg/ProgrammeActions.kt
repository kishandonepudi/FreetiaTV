package com.freetia.tv.ui.epg

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.epg.CatchupRequest
import com.freetia.tv.data.epg.CatchupUrl
import com.freetia.tv.data.epg.ReminderPermissions
import com.freetia.tv.data.epg.ReminderRepository
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.ui.components.BrandDialog
import com.freetia.tv.ui.components.FocusPill
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.theme.LocalBrand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the grid and search need to know about one programme to offer actions on it. */
data class ProgrammeInfo(
    val id: Long,
    val title: String,
    val description: String,
    val category: String,
    val start: Long,
    val stop: Long,
)

private enum class Help { NOTIFICATIONS_PROMPT, NOTIFICATIONS_OFF, EXACT_ALARMS, FULL_SCREEN }

/**
 * Setting and cancelling reminders, including the permission explanations (§3.9): POST_NOTIFICATIONS is requested when
 * the first reminder is set, exact alarms and full-screen intents fall back gracefully and say what the user can grant.
 */
@Stable
class ReminderUi internal constructor(
    private val app: FreetiaApp,
    private val scope: CoroutineScope,
    private val toast: (String, Boolean) -> Unit,
    private val launchNotificationPermission: () -> Unit,
) {
    private val repo = ReminderRepository(app)
    private val queue = mutableStateListOf<Help>()
    private var pendingSet: (suspend () -> Unit)? = null

    fun set(channel: ChannelEntity, info: ProgrammeInfo) {
        val commit: suspend () -> Unit = {
            repo.set(channel, info.id, info.title, info.start, info.stop)
            toast("Reminder set for ${EpgFormat.time(info.start)}", false)
            if (!ReminderPermissions.exactAlarmsAllowed(app)) queue += Help.EXACT_ALARMS
            val wakes = app.settings.reminderWakeScreen.get() || app.settings.reminderAutoTune.get()
            if (wakes && !ReminderPermissions.fullScreenAllowed(app)) queue += Help.FULL_SCREEN
        }
        if (ReminderPermissions.needsNotificationPrompt(app)) {
            pendingSet = commit
            queue += Help.NOTIFICATIONS_PROMPT
        } else {
            scope.launch {
                commit()
                if (!ReminderPermissions.notificationsAllowed(app)) queue += Help.NOTIFICATIONS_OFF
            }
        }
    }

    fun cancel(channelId: Long, start: Long) {
        scope.launch {
            repo.cancelForProgramme(channelId, start)
            toast("Reminder cancelled", false)
        }
    }

    internal fun onNotificationResult(granted: Boolean) {
        val next = pendingSet
        pendingSet = null
        scope.launch {
            next?.invoke()
            if (!granted) queue += Help.NOTIFICATIONS_OFF
        }
    }

    @Composable
    fun Dialogs() {
        val context = LocalContext.current
        val help = queue.firstOrNull() ?: return
        val done = { queue.removeAt(0); Unit }
        when (help) {
            Help.NOTIFICATIONS_PROMPT -> BrandDialog(
                title = "Allow notifications",
                message = "Freetia TV needs to show a notification when your programme is about to start. " +
                    "On the next screen, choose Allow.",
                confirmLabel = "Continue",
                onConfirm = { done(); launchNotificationPermission() },
                dismissLabel = "Not now",
                onDismiss = { done(); onNotificationResult(false) },
            )
            Help.NOTIFICATIONS_OFF -> BrandDialog(
                title = "Notifications are off",
                message = "Your reminder is saved, but you will not see it until notifications are on. " +
                    "Open Settings, then Apps, then Freetia TV, then Notifications, and turn them on.",
                confirmLabel = "Open settings",
                onConfirm = { done(); open(context, notificationSettingsIntent(context)) },
                dismissLabel = "Close",
                onDismiss = { done() },
            )
            Help.EXACT_ALARMS -> BrandDialog(
                title = "Reminder may be a few minutes late",
                message = "Android is not letting Freetia TV set exact alarms, so this reminder can arrive a little late. " +
                    "To fix it, open Settings, then Apps, then Freetia TV, and allow Alarms & reminders.",
                confirmLabel = ReminderPermissions.exactAlarmSettingsIntent(context)?.let { "Open settings" } ?: "OK",
                onConfirm = { done(); open(context, ReminderPermissions.exactAlarmSettingsIntent(context)) },
                dismissLabel = "Close",
                onDismiss = { done() },
            )
            Help.FULL_SCREEN -> BrandDialog(
                title = "Allow full-screen alerts",
                message = "To wake the screen and open the channel, Freetia TV needs full-screen notifications. " +
                    "Open Settings, then Apps, then Freetia TV, then Notifications, and turn on Full-screen notifications. " +
                    "Until then you will get a normal notification with a Watch button.",
                confirmLabel = "Open settings",
                onConfirm = { done(); open(context, ReminderPermissions.fullScreenSettingsIntent(context)) },
                dismissLabel = "Close",
                onDismiss = { done() },
            )
        }
    }

    companion object {
        /** Opens a system settings screen; some TV builds have none, so the written steps above are the fallback. */
        fun open(context: Context, intent: Intent?) {
            if (intent == null) return
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
            } catch (e: SecurityException) {
            }
        }

        fun notificationSettingsIntent(context: Context): Intent =
            if (Build.VERSION.SDK_INT >= 26) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            }
    }
}

@Composable
fun rememberReminderUi(): ReminderUi {
    val context = LocalContext.current
    val app = context.applicationContext as FreetiaApp
    val scope = rememberCoroutineScope()
    val toast = LocalBrandToast.current
    var ui: ReminderUi? = null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        ui?.onNotificationResult(granted)
    }
    val created = remember {
        ReminderUi(app, scope, { text, error -> toast.show(text, error) }) {
            if (Build.VERSION.SDK_INT >= 33) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else ui?.onNotificationResult(true)
        }
    }
    ui = created
    return created
}

/** Plays the catch-up stream for a finished (or running) programme; false when the channel cannot build a URL. */
fun playFromStart(nav: Navigator, channel: ChannelEntity, info: ProgrammeInfo): Boolean {
    val url = CatchupUrl.build(
        channel.streamUrl, channel.catchup, channel.catchupSource, info.start, info.stop, System.currentTimeMillis(),
    ) ?: return false
    CatchupRequest.set(channel.id, url)
    nav.play(channel.id)
    return true
}

/**
 * Actions for one programme. Current: tune. Future: remind or watch the channel. Past: "Watch from start" where the
 * channel has catch-up. The dialog is only shown for programmes that are not currently airing.
 */
@Composable
fun ProgrammeDialog(
    channel: ChannelEntity,
    info: ProgrammeInfo,
    nav: Navigator,
    reminders: ReminderUi,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as FreetiaApp
    val brand = LocalBrand.current
    val toast = LocalBrandToast.current
    val now = remember { System.currentTimeMillis() }
    val hasReminder by remember(channel.id, info.start) {
        ReminderRepository(app).observeHas(channel.id, info.start)
    }.collectAsState(initial = false)
    val future = info.start > now
    val catchup = CatchupUrl.isAvailable(channel.catchup, channel.catchupEnabled, channel.catchupDays, info.start, now)

    BrandDialog(
        title = info.title,
        onDismiss = onDismiss,
        dismissLabel = "Close",
        hints = listOf("OK" to "Select", "Back" to "Close"),
    ) {
        Text(
            "${channel.name}\n${EpgFormat.range(info.start, info.stop, now)}",
            fontSize = 14.sp, color = brand.textMuted, lineHeight = 19.sp,
        )
        if (info.description.isNotBlank()) {
            Text(
                info.description, fontSize = 16.sp, color = brand.textPrimary, lineHeight = 21.sp, maxLines = 5,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (!future && !catchup) {
            Text(
                "This programme has finished and this channel has no catch-up.",
                fontSize = 14.sp, color = brand.textMuted, modifier = Modifier.padding(top = 8.dp),
            )
        }
        Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (future) {
                if (hasReminder) {
                    FocusPill("Cancel reminder", { reminders.cancel(channel.id, info.start); onDismiss() }, Modifier.fillMaxWidth())
                } else {
                    FocusPill("Remind me", { reminders.set(channel, info); onDismiss() }, Modifier.fillMaxWidth(), primary = true)
                }
            }
            if (catchup) {
                FocusPill(
                    "Watch from start",
                    {
                        if (playFromStart(nav, channel, info)) onDismiss()
                        else toast.show("Catch-up is not available for this programme", true)
                    },
                    Modifier.fillMaxWidth(), primary = !future,
                )
            }
            FocusPill("Watch channel", { onDismiss(); nav.play(channel.id) }, Modifier.fillMaxWidth())
        }
    }
}
