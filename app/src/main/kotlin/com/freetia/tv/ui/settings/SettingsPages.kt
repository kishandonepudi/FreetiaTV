package com.freetia.tv.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import coil.imageLoader
import com.freetia.tv.core.util.DeviceDetector
import com.freetia.tv.data.backup.EpgSourceUrl
import com.freetia.tv.data.backup.UrlRules
import com.freetia.tv.data.model.DEFAULT_PROFILE_ID
import com.freetia.tv.data.settings.AspectRatioMode
import com.freetia.tv.data.settings.BufferMode
import com.freetia.tv.data.settings.ColorAction
import com.freetia.tv.data.settings.ColorButton
import com.freetia.tv.ui.components.BrandDialog
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingChoiceRow
import com.freetia.tv.ui.components.SettingSectionHeader
import com.freetia.tv.ui.components.SettingStepperRow
import com.freetia.tv.ui.components.SettingToggleRow
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.LocalAppSession
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.nav.Route
import com.freetia.tv.ui.safety.rememberSafetyActions
import com.freetia.tv.ui.theme.TextSize
import com.freetia.tv.work.EpgRefreshWorker
import kotlinx.coroutines.launch
import java.util.Locale

private fun bufferLabel(m: BufferMode) = when (m) {
    BufferMode.LOW_LATENCY -> "Low delay"
    BufferMode.BALANCED -> "Balanced"
    BufferMode.STABLE -> "Smoothest"
    BufferMode.CUSTOM -> "Custom"
}

private fun aspectLabel(m: AspectRatioMode) = when (m) {
    AspectRatioMode.FIT -> "Fit"
    AspectRatioMode.FILL -> "Stretch to fill"
    AspectRatioMode.ZOOM -> "Zoom"
    AspectRatioMode.RATIO_16_9 -> "16:9"
    AspectRatioMode.RATIO_4_3 -> "4:3"
}

private val Qualities = listOf("auto", "2160p", "1440p", "1080p", "720p", "480p", "360p")
private fun qualityLabel(q: String) = if (q == "auto") "Auto" else q

private val CaptionColors = listOf("white", "yellow", "green", "cyan", "blue", "red")
private val CaptionBackgrounds = listOf("black", "blue", "transparent")
private fun colorLabel(c: String) = c.replaceFirstChar { it.uppercase() }

private val AudioLanguages = listOf(
    "", "en", "hi", "ta", "te", "ml", "kn", "bn", "mr", "gu", "pa", "ur",
    "es", "fr", "de", "it", "pt", "ru", "ar", "tr", "zh", "ja", "ko",
)

private fun languageLabel(code: String): String =
    if (code.isEmpty()) "Stream default"
    else Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code }
        .replaceFirstChar { it.uppercase() }

private fun seconds(ms: Int) = "${ms / 1000.0} s".replace(".0 s", " s")

@Composable
internal fun PlaybackPage() {
    val c = rememberSettingsCtx()
    val s = c.s
    val context = LocalContext.current
    val session = LocalAppSession.current
    val toast = LocalBrandToast.current
    val playlists by session.playlists.collectAsState()
    val buffer = s.bufferMode.collect()
    val bufferMin = s.bufferMinMs.collect()
    val bufferMax = s.bufferMaxMs.collect()
    val quality = s.defaultQuality.collect()
    val autoQuality = s.autoQuality.collect()
    val notify = s.qualityNotifications.collect()
    val lock = s.lockQuality.collect()
    val aspect = s.aspectRatio.collect()
    val systemCaptions = s.subtitleUseSystemStyle.collect()
    val captionSize = s.subtitleSizePercent.collect()
    val captionColor = s.subtitleColor.collect()
    val captionBg = s.subtitleBgColor.collect()
    val captionOpacity = s.subtitleBgOpacity.collect()
    val audio = s.defaultAudioLanguage.collect()
    val backgroundOn = !playlists.isNullOrEmpty() && playlists.orEmpty().all { it.allowBackgroundPlay }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast.show("Notifications are off, so the playback controls will not show while the app is in the background.")
    }
    fun setBackground(on: Boolean) {
        c.launch { playlists.orEmpty().forEach { c.app.playlistRepository.update(it.copy(allowBackgroundPlay = on)) } }
        if (on && Build.VERSION.SDK_INT >= 33) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    SettingsScaffold("Playback", subtitle = "Buffer and quality apply the next time a channel starts.") {
        item {
            SettingChoiceRow(
                "Buffer", BufferMode.entries, buffer, { c.set(s.bufferMode, it) }, label = ::bufferLabel,
                subtitle = "Low delay starts fastest. Smoothest copes best with a weak connection.",
                modifier = Modifier.describe("Buffer"),
            )
        }
        if (buffer == BufferMode.CUSTOM) {
            item {
                SettingStepperRow(
                    "Start playing after", bufferMin / 1000,
                    { v -> c.launch { s.bufferMinMs.set(v * 1000); if (v * 1000 > bufferMax) s.bufferMaxMs.set(v * 1000) } },
                    range = 1..60, step = 1, format = { "$it s" }, modifier = Modifier.describe("Minimum buffer"),
                )
            }
            item {
                SettingStepperRow(
                    "Keep buffered up to", bufferMax / 1000,
                    { v -> c.launch { s.bufferMaxMs.set(v * 1000); if (v * 1000 < bufferMin) s.bufferMinMs.set(v * 1000) } },
                    range = 2..120, step = 1, format = { "$it s" }, modifier = Modifier.describe("Maximum buffer"),
                )
            }
        }
        item { SettingSectionHeader("Quality") }
        item {
            SettingChoiceRow(
                "Default quality", Qualities, quality, { c.set(s.defaultQuality, it) }, label = ::qualityLabel,
                subtitle = "Used when a channel offers several sizes",
                modifier = Modifier.describe("Default quality"),
            )
        }
        item {
            SettingToggleRow(
                "Switch quality automatically", autoQuality, { c.set(s.autoQuality, it) },
                subtitle = "Lowers the quality when the connection is slow", modifier = Modifier.describe("Automatic quality"),
            )
        }
        item {
            SettingToggleRow(
                "Tell me when quality changes", notify, { c.set(s.qualityNotifications, it) },
                enabled = autoQuality, modifier = Modifier.describe("Quality change messages"),
            )
        }
        item {
            SettingToggleRow(
                "Keep my quality", lock, { c.set(s.lockQuality, it) },
                subtitle = "Never switch quality on its own", modifier = Modifier.describe("Lock quality"),
            )
        }
        item {
            SettingChoiceRow(
                "Picture shape", AspectRatioMode.entries, aspect, { c.set(s.aspectRatio, it) }, label = ::aspectLabel,
                modifier = Modifier.describe("Picture shape"),
            )
        }
        item { SettingSectionHeader("Captions and audio") }
        item {
            SettingToggleRow(
                "Use the system caption style", systemCaptions, { c.set(s.subtitleUseSystemStyle, it) },
                subtitle = "Follows Accessibility > Captions on this device", modifier = Modifier.describe("System caption style"),
            )
        }
        if (!systemCaptions) {
            item {
                SettingStepperRow(
                    "Caption size", captionSize, { c.set(s.subtitleSizePercent, it) }, range = 50..200, step = 25,
                    format = { "$it%" }, modifier = Modifier.describe("Caption size"),
                )
            }
            item {
                SettingChoiceRow(
                    "Caption colour", CaptionColors, captionColor, { c.set(s.subtitleColor, it) }, label = ::colorLabel,
                    modifier = Modifier.describe("Caption colour"),
                )
            }
            item {
                SettingChoiceRow(
                    "Caption background", CaptionBackgrounds, captionBg, { c.set(s.subtitleBgColor, it) }, label = ::colorLabel,
                    modifier = Modifier.describe("Caption background"),
                )
            }
            item {
                SettingStepperRow(
                    "Background strength", (captionOpacity * 100).toInt(), { c.set(s.subtitleBgOpacity, it / 100f) },
                    range = 0..100, step = 25, format = { "$it%" }, modifier = Modifier.describe("Caption background strength"),
                )
            }
        }
        item {
            SettingChoiceRow(
                "Preferred audio language", AudioLanguages, audio, { c.set(s.defaultAudioLanguage, it) },
                label = ::languageLabel, subtitle = "Used when a channel has more than one audio track",
                modifier = Modifier.describe("Preferred audio language"),
            )
        }
        item { SettingSectionHeader("Background") }
        item {
            SettingToggleRow(
                "Keep playing audio when I leave the app", backgroundOn, ::setBackground,
                subtitle = "Applies to every playlist. Each playlist can change it in Playlists.",
                enabled = !playlists.isNullOrEmpty(), modifier = Modifier.describe("Background play for all playlists"),
            )
        }
    }
}

@Composable
internal fun StartupPage(nav: Navigator) {
    val c = rememberSettingsCtx()
    val s = c.s
    val context = LocalContext.current
    val session = LocalAppSession.current
    val playlists by session.playlists.collectAsState()
    val startupId = s.defaultPlaylistId.collect()
    val resume = s.resumeLastChannel.collect()
    val openPlayer = s.openPlayerOnStartup.collect()
    val autoStart = s.autoStartOnTv.collect()
    val names = playlists.orEmpty().associate { it.id to it.name }
    val ids = listOf(0L) + names.keys

    SettingsScaffold("Startup") {
        item {
            SettingChoiceRow(
                "Playlist to open", ids, startupId, { c.set(s.defaultPlaylistId, it) },
                label = { if (it == 0L) "Default playlist" else names[it] ?: "Default playlist" },
                subtitle = "If it cannot load, the next playlist is tried",
                modifier = Modifier.describe("Playlist to open at start"),
            )
        }
        item {
            SettingChoiceRow(
                "Open on", listOf(true, false), openPlayer, { c.set(s.openPlayerOnStartup, it) },
                label = { if (it) "Player" else "Channel list" },
                subtitle = "Player starts the last or first channel straight away",
                modifier = Modifier.describe("Screen to open at start"),
            )
        }
        item {
            SettingToggleRow(
                "Resume the last channel", resume, { c.set(s.resumeLastChannel, it) },
                subtitle = "Off starts the first channel instead", enabled = openPlayer,
                modifier = Modifier.describe("Resume last channel"),
            )
        }
        if (DeviceDetector.isTV(context)) {
            item {
                SettingToggleRow(
                    "Open Freetia TV when the TV turns on", autoStart,
                    { on -> c.set(s.autoStartOnTv, on); if (on) nav.push(Route.AutoStartSetup) },
                    subtitle = "Some TVs need one extra step. We will show you.",
                    modifier = Modifier.describe("Open when the TV turns on"),
                )
            }
            if (autoStart) {
                item {
                    SettingActionRow(
                        "Check auto-start setup", onClick = { nav.push(Route.AutoStartSetup) },
                        modifier = Modifier.describe("Check auto-start setup"),
                    )
                }
            }
        }
    }
}

@Composable
internal fun NavigationPage() {
    val c = rememberSettingsCtx()
    val s = c.s
    val backPrevious = s.backReturnsToPrevious.collect()
    val instant = s.channelChangeInstant.collect()
    val delay = s.channelChangeOsdDelayMs.collect()
    val timeout = s.numberEntryTimeoutMs.collect()
    val mapping by s.colorButtons.collectAsState(
        initial = ColorButton.entries.associateWith { s.colorAction(it).default },
    )
    val clashes = ColorButtonRules.duplicates(mapping)

    SettingsScaffold("Navigation") {
        item {
            SettingToggleRow(
                "Long-press Back for the previous channel", backPrevious, { c.set(s.backReturnsToPrevious, it) },
                subtitle = "A normal press of Back still goes back one screen",
                modifier = Modifier.describe("Long-press Back for the previous channel"),
            )
        }
        item {
            SettingChoiceRow(
                "Changing channel with Up and Down", listOf(true, false), instant, { c.set(s.channelChangeInstant, it) },
                label = { if (it) "Switch at once" else "Show the banner first" },
                modifier = Modifier.describe("How Up and Down change channel"),
            )
        }
        if (!instant) {
            item {
                SettingStepperRow(
                    "Wait before switching", delay, { c.set(s.channelChangeOsdDelayMs, it) },
                    range = 500..5_000, step = 500, format = ::seconds, modifier = Modifier.describe("Wait before switching"),
                )
            }
        }
        item {
            SettingStepperRow(
                "Wait after typing a channel number", timeout, { c.set(s.numberEntryTimeoutMs, it) },
                range = 1_000..5_000, step = 500, format = ::seconds,
                modifier = Modifier.describe("Wait after typing a channel number"),
            )
        }
        item { SettingSectionHeader("Colour buttons on the remote") }
        ColorButton.entries.forEach { button ->
            item(key = button.name) {
                val current = mapping[button] ?: s.colorAction(button).default
                SettingChoiceRow(
                    ColorButtonRules.buttonLabel(button), ColorButtonRules.optionsFor(current), current,
                    { c.set(s.colorAction(button), it) }, label = ColorButtonRules::label,
                    subtitle = if (current in clashes) "Another button opens this too" else null,
                    modifier = Modifier.describe(ColorButtonRules.buttonLabel(button)),
                )
            }
        }
        item {
            SettingActionRow(
                "Restore colour button defaults",
                onClick = { c.launch { ColorButton.entries.forEach { s.colorAction(it).reset() } } },
                showChevron = false, modifier = Modifier.describe("Restore colour button defaults"),
            )
        }
    }
}

@Composable
internal fun DisplayPage() {
    val c = rememberSettingsCtx()
    val s = c.s
    val highContrast = s.highContrast.collect()
    val percent = s.textSizePercent.collect()
    val osd = s.osdTimeoutSeconds.collect()

    SettingsScaffold("Display") {
        item {
            SettingChoiceRow(
                "Theme", listOf(false, true), highContrast, { c.set(s.highContrast, it) },
                label = { if (it) "High contrast" else "Default" },
                subtitle = "High contrast uses pure black and white for easier reading",
                modifier = Modifier.describe("Theme"),
            )
        }
        item {
            SettingChoiceRow(
                "Text size", TextSize.entries, TextSize.fromPercent(percent), { c.set(s.textSizePercent, it.percent) },
                label = { it.label }, subtitle = "Adds to the text size set in your device's accessibility settings",
                modifier = Modifier.describe("Text size"),
            )
        }
        item {
            SettingStepperRow(
                "Hide the channel banner after", osd, { c.set(s.osdTimeoutSeconds, it) },
                range = 2..30, step = 1, format = { "$it s" }, modifier = Modifier.describe("Banner timeout"),
            )
        }
    }
}

@Composable
internal fun SleepRemindersPage(nav: Navigator) {
    val c = rememberSettingsCtx()
    val s = c.s
    val sleepMinutes = s.sleepTimerDefaultMinutes.collect()
    val lead = s.reminderLeadMinutes.collect()
    val wake = s.reminderWakeScreen.collect()
    val autoTune = s.reminderAutoTune.collect()

    SettingsScaffold("Sleep and reminders") {
        item {
            SettingStepperRow(
                "Default sleep time", sleepMinutes, { c.set(s.sleepTimerDefaultMinutes, it) },
                range = 10..240, step = 5, format = { "$it min" }, modifier = Modifier.describe("Default sleep time"),
            )
        }
        item {
            SettingActionRow(
                "Sleep timer", onClick = { nav.push(Route.SleepTimer) },
                subtitle = "Start or change the timer now", modifier = Modifier.describe("Open the sleep timer"),
            )
        }
        item { SettingSectionHeader("Reminders") }
        item {
            SettingStepperRow(
                "Remind me before a programme", lead, { c.set(s.reminderLeadMinutes, it) },
                range = 0..30, step = 1, format = { if (it == 0) "At the start" else "$it min" },
                modifier = Modifier.describe("Reminder lead time"),
            )
        }
        item {
            SettingToggleRow(
                "Wake the screen", wake, { c.set(s.reminderWakeScreen, it) },
                subtitle = "Turns the screen on when a programme starts", modifier = Modifier.describe("Wake the screen for reminders"),
            )
        }
        item {
            SettingToggleRow(
                "Switch to the channel", autoTune, { c.set(s.reminderAutoTune, it) },
                subtitle = "Opens the channel when the programme starts", modifier = Modifier.describe("Switch to the channel for reminders"),
            )
        }
        item {
            SettingActionRow(
                "My reminders", onClick = { nav.push(Route.Reminders) }, modifier = Modifier.describe("Open my reminders"),
            )
        }
    }
}

private val EpgIntervals = listOf(6, 12, 24, 48, 72)

@Composable
internal fun PlaylistsEpgPage(nav: Navigator) {
    val c = rememberSettingsCtx()
    val s = c.s
    val toast = LocalBrandToast.current
    val context = LocalContext.current
    val session = LocalAppSession.current
    val playlists by session.playlists.collectAsState()
    val interval = s.epgRefreshIntervalHours.collect()
    val days = s.epgDays.collect()
    val hideDead = s.hideDeadStreams.collect()
    val epgRef = s.epgSourceUrl.collect()
    var editing by remember { mutableStateOf<String?>(null) }

    SettingsScaffold("Playlists and guide") {
        item {
            SettingActionRow(
                "Playlists", onClick = { nav.push(Route.Playlists) },
                subtitle = "Add, order and edit. Refresh timing is set for each playlist.",
                value = playlists?.size?.let { "$it" }, modifier = Modifier.describe("Manage playlists"),
            )
        }
        item {
            SettingActionRow(
                "Refresh playlists now", showChevron = false,
                onClick = {
                    val list = playlists.orEmpty()
                    if (list.isEmpty()) toast.show("Add a playlist first.")
                    else {
                        toast.show("Updating your playlists.")
                        list.forEach { p -> c.app.appScope.launch { c.app.playlistSyncer.sync(p, force = true) } }
                    }
                },
                modifier = Modifier.describe("Refresh playlists now"),
            )
        }
        item {
            SettingActionRow(
                "Region", onClick = { nav.push(Route.RegionPicker) }, subtitle = "Which channels to show",
                modifier = Modifier.describe("Choose a region"),
            )
        }
        item {
            SettingToggleRow(
                "Hide channels that do not work", hideDead, { c.set(s.hideDeadStreams, it) },
                subtitle = "Based on the last check of each playlist", modifier = Modifier.describe("Hide dead channels"),
            )
        }
        item { SettingSectionHeader("TV guide") }
        item {
            SettingActionRow(
                "Guide link (XMLTV)",
                onClick = { c.launch { editing = EpgSourceUrl.get(c.app) } },
                subtitle = "Used when a playlist has no guide of its own",
                value = if (epgRef.isBlank()) "Not set" else "Set",
                modifier = Modifier.describe("Guide link", if (epgRef.isBlank()) "Not set" else "Set"),
            )
        }
        item {
            SettingChoiceRow(
                "Update the guide every", EpgIntervals, interval, { c.set(s.epgRefreshIntervalHours, it) },
                label = { if (it % 24 == 0) "${it / 24} d" else "$it h" }, modifier = Modifier.describe("Guide update interval"),
            )
        }
        item {
            SettingStepperRow(
                "Days of guide to keep", days, { c.set(s.epgDays, it) }, range = 1..14, step = 1,
                format = { if (it == 1) "1 day" else "$it days" }, modifier = Modifier.describe("Days of guide"),
            )
        }
        item {
            SettingActionRow(
                "Refresh guide now", showChevron = false,
                onClick = {
                    EpgRefreshWorker.enqueue(context, force = true)
                    toast.show("Updating the guide in the background.")
                },
                modifier = Modifier.describe("Refresh guide now"),
            )
        }
    }

    editing?.let { current ->
        TextPromptDialog(
            title = "Guide link",
            label = "https://…",
            initial = current,
            confirmLabel = "Save",
            message = "Leave it empty to remove the link. It is stored encrypted.",
            keyboardType = KeyboardType.Uri,
            maxLength = UrlRules.MAX_LENGTH,
            allowBlank = true,
            validate = { if (UrlRules.isHttpUrl(it)) null else "The link must start with http or https." },
            onConfirm = { url ->
                c.launch { EpgSourceUrl.set(c.app, url) }
                toast.show(if (url.isEmpty()) "Guide link removed." else "Guide link saved.")
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private val LogoSizes = listOf(25, 50, 100, 200)

@Composable
internal fun CachePage() {
    val c = rememberSettingsCtx()
    val s = c.s
    val context = LocalContext.current
    val toast = LocalBrandToast.current
    val onExit = s.clearCacheOnExit.collect()
    val onStart = s.clearCacheOnStartup.collect()
    val logoMb = s.logoCacheSizeMb.collect()
    var refresh by remember { mutableIntStateOf(0) }
    val size by produceState<Long?>(null, refresh) { value = c.app.cacheManager.sizeBytes() }

    SettingsScaffold("Cache", subtitle = "Playlists, favourites and settings are never cleared here.") {
        item {
            SettingToggleRow(
                "Clear the cache when I exit", onExit, { c.set(s.clearCacheOnExit, it) },
                modifier = Modifier.describe("Clear the cache on exit"),
            )
        }
        item {
            SettingToggleRow(
                "Clear the cache at start", onStart, { c.set(s.clearCacheOnStartup, it) },
                modifier = Modifier.describe("Clear the cache at start"),
            )
        }
        item {
            SettingChoiceRow(
                "Channel logo storage", LogoSizes, if (logoMb in LogoSizes) logoMb else 50, { c.set(s.logoCacheSizeMb, it) },
                label = { "$it MB" }, subtitle = "A new size applies the next time the app starts",
                modifier = Modifier.describe("Channel logo storage"),
            )
        }
        item {
            SettingActionRow(
                "Clear now", showChevron = false,
                value = size?.let { formatBytes(it) },
                onClick = {
                    c.launch {
                        val freed = c.app.cacheManager.clearNow(imageLoader = context.imageLoader)
                        refresh++
                        toast.show("Cleared ${formatBytes(freed)}.")
                    }
                },
                modifier = Modifier.describe("Clear the cache now", size?.let { "Using ${formatBytes(it)}" }),
            )
        }
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(Locale.US, bytes / 1048576.0)} MB"
}

@Composable
internal fun AdvancedPage(nav: Navigator) {
    val c = rememberSettingsCtx()
    val toast = LocalBrandToast.current
    val session = LocalAppSession.current
    val actions = rememberSafetyActions()
    var confirmReset by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    SettingsScaffold("Advanced") {
        item { SettingSectionHeader("Reset") }
        item {
            SettingActionRow(
                "Reset all settings", onClick = { confirmReset = true }, destructive = true, showChevron = false,
                subtitle = "Keeps your playlists, favourites and PIN", modifier = Modifier.describe("Reset all settings"),
            )
        }
        item {
            SettingActionRow(
                "Remove all playlists", onClick = { confirmWipe = true }, destructive = true, showChevron = false,
                subtitle = "Removes every playlist and its channels", modifier = Modifier.describe("Remove all playlists"),
            )
        }
        item { SettingSectionHeader("App") }
        item {
            SettingActionRow(
                "Exit Freetia TV", onClick = { confirmExit = true }, showChevron = false,
                subtitle = "Stops playback and closes the app", modifier = Modifier.describe("Exit the app"),
            )
        }
    }

    if (confirmReset) {
        BrandDialog(
            title = "Reset all settings?", message = "Every setting goes back to its default. Your playlists, favourites and PIN stay.",
            confirmLabel = "Reset", destructive = true, onDismiss = { confirmReset = false },
            onConfirm = {
                confirmReset = false
                c.launchPinned(actions, "Enter your PIN to reset settings") {
                    c.launch {
                        com.freetia.tv.data.backup.EpgSourceUrl.set(c.app, "")
                        c.s.resetPreferences()
                        c.s.activeProfileId.set(DEFAULT_PROFILE_ID)
                        toast.show("Settings reset.")
                    }
                }
            },
        )
    }
    if (confirmWipe) {
        BrandDialog(
            title = "Remove all playlists?",
            message = "Every playlist and its channels are deleted from this device. Favourites of removed channels go too. Settings stay.",
            confirmLabel = "Remove all", destructive = true, onDismiss = { confirmWipe = false },
            onConfirm = {
                confirmWipe = false
                c.launchPinned(actions, "Enter your PIN to remove playlists") {
                    c.launch {
                        c.app.playlistRepository.getAll().forEach { c.app.playlistRepository.delete(it) }
                        toast.show("Playlists removed.")
                    }
                }
            },
        )
    }
    if (confirmExit) {
        BrandDialog(
            title = "Exit Freetia TV?", confirmLabel = "Exit", onDismiss = { confirmExit = false },
            onConfirm = { confirmExit = false; session.exitApp() },
        )
    }
}

/** Runs [block] straight away, or after the PIN when one exists (never offers to create one). */
internal fun SettingsCtx.launchPinned(
    actions: com.freetia.tv.ui.safety.SafetyActions,
    title: String,
    block: () -> Unit,
) {
    launch { if (app.safety.pin.hasPin()) actions.withPin(title, action = block) else block() }
}
