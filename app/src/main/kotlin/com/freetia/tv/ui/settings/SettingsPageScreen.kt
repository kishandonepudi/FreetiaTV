package com.freetia.tv.ui.settings

import androidx.compose.runtime.Composable
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.nav.SettingsSection
import com.freetia.tv.ui.safety.SafetyScreen

/** One page per §3.14 group. Safety is its own screen, PIN-gated by the safety APIs. */
@Composable
fun SettingsPageScreen(section: SettingsSection, nav: Navigator) {
    when (section) {
        SettingsSection.PLAYBACK -> PlaybackPage()
        SettingsSection.STARTUP -> StartupPage(nav)
        SettingsSection.NAVIGATION -> NavigationPage()
        SettingsSection.DISPLAY -> DisplayPage()
        SettingsSection.SAFETY -> SafetyScreen(nav)
        SettingsSection.SLEEP_REMINDERS -> SleepRemindersPage(nav)
        SettingsSection.PLAYLISTS_EPG -> PlaylistsEpgPage(nav)
        SettingsSection.CACHE -> CachePage()
        SettingsSection.ADVANCED -> AdvancedPage(nav)
    }
}
