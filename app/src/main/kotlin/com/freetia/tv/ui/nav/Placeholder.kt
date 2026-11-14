package com.freetia.tv.ui.nav

import androidx.compose.runtime.Composable
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingsScaffold

/** Stand-in for a screen a later wave fills in; each feature agent replaces its own file's call to this. */
@Composable
fun PlaceholderScreen(title: String, nav: Navigator, message: String = "This screen is on its way.") {
    SettingsScaffold(title = title, subtitle = message, hints = listOf("OK" to "Go back", "Back" to "Return")) {
        item { SettingActionRow("Go back", onClick = { nav.pop() }, showChevron = false) }
    }
}
