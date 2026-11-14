package com.freetia.tv.ui.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.freetia.tv.FreetiaApp
import com.freetia.tv.core.system.AutoStartResult
import com.freetia.tv.core.system.AutoStartStatus
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingSectionHeader
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.Navigator
import kotlinx.coroutines.launch

/**
 * One-time steps for "Open app when TV turns on" (§3.11). Android 10+ blocks background launches and some Fire TV
 * and Android TV builds only allow it after the user changes a system setting, so the app explains instead of promising.
 */
@Composable
fun AutoStartSetupScreen(nav: Navigator) {
    val context = LocalContext.current
    val app = context.applicationContext as FreetiaApp
    val scope = rememberCoroutineScope()
    val last = AutoStartStatus.get(context)

    fun finish(keepOn: Boolean) {
        scope.launch {
            app.settings.autoStartSetupShown.set(true)
            if (!keepOn) app.settings.autoStartOnTv.set(false)
        }
        nav.pop()
    }

    SettingsScaffold(
        title = "Open app when TV turns on",
        subtitle = "Your TV may stop apps from opening by themselves. Check these steps once, then restart the TV to test.",
    ) {
        item { SettingSectionHeader("Last start") }
        item {
            SettingActionRow(
                title = when (last) {
                    AutoStartResult.LAUNCHED -> "Opened by itself"
                    AutoStartResult.BLOCKED -> "Blocked by the TV"
                    AutoStartResult.NEVER_TRIED -> "Not tried yet"
                },
                onClick = {},
                subtitle = "Updated every time the TV starts",
                showChevron = false,
            )
        }
        item { SettingSectionHeader("Steps") }
        item {
            SettingActionRow(
                "1. Open Freetia TV settings",
                onClick = { openSettings(context, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app.packageName) },
                subtitle = "Turn on Start in background or Appear on top if your TV lists it",
            )
        }
        item {
            SettingActionRow(
                "2. Allow it to run in the background",
                onClick = { openSettings(context, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, null) },
                subtitle = "Choose Unrestricted or No restrictions for Freetia TV",
            )
        }
        item {
            SettingActionRow(
                "3. Restart the TV",
                onClick = {},
                subtitle = "If you force-stop the app it cannot start by itself until you open it once",
                showChevron = false,
            )
        }
        item { SettingActionRow("Done", onClick = { finish(keepOn = true) }, showChevron = false) }
        item {
            SettingActionRow("Turn this off", onClick = { finish(keepOn = false) }, showChevron = false, destructive = true)
        }
    }
}

private fun openSettings(context: Context, action: String, packageName: String?) {
    val intent = Intent(action).apply {
        if (packageName != null) data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    // Not every TV build ships these screens.
    runCatching { context.startActivity(intent) }
}
