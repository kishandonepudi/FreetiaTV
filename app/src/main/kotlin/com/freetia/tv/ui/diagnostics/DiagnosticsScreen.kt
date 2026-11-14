package com.freetia.tv.ui.diagnostics

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import com.freetia.tv.BuildConfig
import com.freetia.tv.playback.PlaybackHost
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingSectionHeader
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.settings.describe
import kotlinx.coroutines.delay

private fun deviceInfo() = DeviceInfo(
    appVersion = BuildConfig.VERSION_NAME,
    build = BuildConfig.BUILD_TYPE + "/" + BuildConfig.FLAVOR,
    android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
)

/** Settings > Advanced > Diagnostics (§3.14). Read-only numbers plus a share-sheet report; nothing is uploaded. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun DiagnosticsScreen(nav: Navigator) {
    val context = LocalContext.current
    val toast = LocalBrandToast.current
    val device = remember { deviceInfo() }
    var stats by remember { mutableStateOf(DiagnosticsSnapshot.last) }
    var live by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val player = PlaybackHost.playerForService()
            val now = player?.let { DiagnosticsSnapshot.read(it) }
            live = now != null
            stats = now ?: DiagnosticsSnapshot.last
            delay(1_000)
        }
    }

    val s = stats
    val missing = "Start a channel first"
    val rows = listOf(
        "Video codec" to (s?.codec ?: missing),
        "Resolution" to (s?.resolution ?: missing),
        "Bitrate" to (s?.let { if (it.bitrateKbps > 0) "${it.bitrateKbps} kbps" else "Unknown" } ?: missing),
        "Dropped frames" to (s?.droppedFrames?.toString() ?: missing),
        "Buffer" to (s?.let { "${it.bufferMs / 1000.0} s" } ?: missing),
    )

    SettingsScaffold(
        title = "Diagnostics",
        subtitle = if (live) "Playing now" else if (s != null) "From the last time a channel played" else "Nothing has played yet",
        hints = listOf("↑↓" to "Move", "OK" to "Send report", "Back" to "Return"),
    ) {
        item { SettingSectionHeader("Playback") }
        rows.forEach { (label, value) ->
            item(key = label) {
                SettingActionRow(
                    label, onClick = {}, value = value, showChevron = false, modifier = Modifier.describe(label, value),
                )
            }
        }
        item { SettingSectionHeader("This device") }
        listOf(
            "App version" to "${device.appVersion} (${device.build})",
            "Android" to device.android,
            "Device" to device.device,
        ).forEach { (label, value) ->
            item(key = label) {
                SettingActionRow(
                    label, onClick = {}, value = value, showChevron = false, modifier = Modifier.describe(label, value),
                )
            }
        }
        item { SettingSectionHeader("Report") }
        item {
            SettingActionRow(
                "Send report", showChevron = false,
                subtitle = "Opens the share sheet with the numbers above. No links or personal details.",
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Freetia TV diagnostics")
                        putExtra(Intent.EXTRA_TEXT, DiagnosticsReport.build(device, s))
                    }
                    try {
                        context.startActivity(Intent.createChooser(send, "Send report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: ActivityNotFoundException) {
                        toast.show("No app on this device can send the report.", isError = true)
                    }
                },
                modifier = Modifier.describe("Send report"),
            )
        }
    }
}
