package com.freetia.tv.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.nav.Route
import com.freetia.tv.ui.nav.SettingsSection

private fun hubHint(section: SettingsSection): String = when (section) {
    SettingsSection.PLAYBACK -> "Buffer, quality, captions, audio"
    SettingsSection.STARTUP -> "What opens when the app starts"
    SettingsSection.NAVIGATION -> "Back, channel change, colour buttons"
    SettingsSection.DISPLAY -> "Theme, text size, on-screen display"
    SettingsSection.SAFETY -> "Child Safe Mode and PIN"
    SettingsSection.SLEEP_REMINDERS -> "Sleep timer and programme reminders"
    SettingsSection.PLAYLISTS_EPG -> "Playlists, TV guide and refresh"
    SettingsSection.CACHE -> "Clear data and cache size"
    SettingsSection.ADVANCED -> "Reset settings, remove playlists, exit"
}

@Composable
fun SettingsScreen(nav: Navigator) {
    val c = rememberSettingsCtx()
    val profileId = c.s.activeProfileId.collect()
    val profiles by remember { c.app.database.profileDao().observeAll() }.collectAsState(initial = emptyList())
    val profileName = profiles.firstOrNull { it.id == profileId }?.name
    val sections = SettingsSection.entries.filter { it != SettingsSection.ADVANCED }

    SettingsScaffold(
        title = "Settings",
        hints = listOf("OK" to "Open", "↑↓" to "Move", "Back" to "Return"),
    ) {
        sections.forEach { section ->
            item(key = section.name) {
                SettingActionRow(
                    section.title,
                    subtitle = hubHint(section),
                    onClick = {
                        if (section == SettingsSection.SAFETY) nav.push(Route.Safety)
                        else nav.push(Route.SettingsPage(section))
                    },
                    modifier = Modifier.describe("${section.title} settings", hubHint(section)),
                )
            }
        }
        item {
            SettingActionRow(
                "Export and import", onClick = { nav.push(Route.Backup) },
                subtitle = "Back up or move your settings and playlists", modifier = Modifier.describe("Export and import"),
            )
        }
        item {
            SettingActionRow(
                "Profiles", onClick = { nav.push(Route.Profiles) }, value = profileName,
                subtitle = "Separate favourites and recents for each person",
                modifier = Modifier.describe("Profiles", profileName?.let { "Current: $it" }),
            )
        }
        item {
            SettingActionRow(
                "Diagnostics", onClick = { nav.push(Route.Diagnostics) },
                subtitle = "Playback details you can send when something goes wrong",
                modifier = Modifier.describe("Diagnostics"),
            )
        }
        item {
            SettingActionRow(
                "About", onClick = { nav.push(Route.About) },
                subtitle = "Version, licences and privacy", modifier = Modifier.describe("About Freetia TV"),
            )
        }
        item {
            SettingActionRow(
                SettingsSection.ADVANCED.title, subtitle = hubHint(SettingsSection.ADVANCED),
                onClick = { nav.push(Route.SettingsPage(SettingsSection.ADVANCED)) },
                modifier = Modifier.describe("${SettingsSection.ADVANCED.title} settings", hubHint(SettingsSection.ADVANCED)),
            )
        }
    }
}
