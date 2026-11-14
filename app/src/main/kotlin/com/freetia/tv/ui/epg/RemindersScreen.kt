package com.freetia.tv.ui.epg

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.epg.ReminderPermissions
import com.freetia.tv.data.epg.ReminderRepository
import com.freetia.tv.data.model.ReminderEntity
import com.freetia.tv.ui.components.BrandDialog
import com.freetia.tv.ui.components.FocusPill
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingSectionHeader
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.theme.LocalBrand
import kotlinx.coroutines.launch

/** Upcoming reminders (§3.9): review and cancel, plus the permissions that decide how reliably they fire. */
@Composable
fun RemindersScreen(nav: Navigator) {
    val context = LocalContext.current
    val app = context.applicationContext as FreetiaApp
    val brand = LocalBrand.current
    val scope = rememberCoroutineScope()
    val toast = LocalBrandToast.current
    val repo = remember { ReminderRepository(app) }
    val upcoming by remember { repo.observeUpcoming(System.currentTimeMillis()) }.collectAsState(initial = null)
    val wakes by app.settings.reminderWakeScreen.flow.collectAsState(initial = false)
    val tunes by app.settings.reminderAutoTune.flow.collectAsState(initial = false)
    var resumes by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<ReminderEntity?>(null) }

    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }

    val names by produceState(emptyMap<Long, String>(), upcoming) {
        val list = upcoming.orEmpty()
        value = list.map { it.channelId }.distinct().associateWith { app.database.channelDao().getById(it)?.name.orEmpty() }
    }
    val notificationsOff = resumes >= 0 && !ReminderPermissions.notificationsAllowed(context)
    val inexact = resumes >= 0 && !ReminderPermissions.exactAlarmsAllowed(context)
    val noFullScreen = resumes >= 0 && (wakes || tunes) && !ReminderPermissions.fullScreenAllowed(context)

    SettingsScaffold(
        title = "Reminders",
        subtitle = "Programmes you asked to be reminded about",
        hints = listOf("OK" to "Open", "↑↓" to "Move", "Back" to "Return"),
    ) {
        if (notificationsOff || inexact || noFullScreen) {
            item(key = "needs") { SettingSectionHeader("Needs your attention") }
        }
        if (notificationsOff) {
            item(key = "n") {
                SettingActionRow(
                    "Notifications are off",
                    { ReminderUi.open(context, ReminderUi.notificationSettingsIntent(context)) },
                    subtitle = "You will not see reminders. Open Settings, Apps, Freetia TV, Notifications, and turn them on.",
                    value = "Fix",
                )
            }
        }
        if (inexact) {
            item(key = "e") {
                SettingActionRow(
                    "Reminders may be a few minutes late",
                    { ReminderUi.open(context, ReminderPermissions.exactAlarmSettingsIntent(context)) },
                    subtitle = "Android has not allowed exact alarms. Allow Alarms & reminders for Freetia TV in Settings, Apps.",
                    value = "Fix",
                )
            }
        }
        if (noFullScreen) {
            item(key = "f") {
                SettingActionRow(
                    "Full-screen alerts are off",
                    { ReminderUi.open(context, ReminderPermissions.fullScreenSettingsIntent(context)) },
                    subtitle = "Needed to wake the screen and open the channel. Open Settings, Apps, Freetia TV, Notifications, and turn on Full-screen notifications.",
                    value = "Fix",
                )
            }
        }
        item(key = "header") { SettingSectionHeader("Upcoming") }
        val list = upcoming
        when {
            list == null -> Unit
            list.isEmpty() -> item(key = "empty") {
                Text(
                    "No reminders yet. In the TV guide, choose a programme that has not started and pick Remind me.",
                    fontSize = 14.sp, color = brand.textMuted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            else -> items(list, key = { it.id }) { reminder ->
                SettingActionRow(
                    reminder.title,
                    { selected = reminder },
                    subtitle = "${names[reminder.channelId].orEmpty()}  |  ${EpgFormat.range(reminder.startTime, reminder.stopTime.coerceAtLeast(reminder.startTime), System.currentTimeMillis())}",
                    value = if (reminder.isNotified) "Shown" else null,
                )
            }
        }
    }

    selected?.let { reminder ->
        BrandDialog(
            title = reminder.title,
            onDismiss = { selected = null },
            dismissLabel = "Close",
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusPill("Cancel reminder", {
                    selected = null
                    scope.launch {
                        repo.cancel(reminder)
                        toast.show("Reminder cancelled")
                    }
                }, Modifier.fillMaxWidth(), primary = true)
                FocusPill("Watch channel", { selected = null; nav.play(reminder.channelId) }, Modifier.fillMaxWidth())
            }
        }
    }
}
