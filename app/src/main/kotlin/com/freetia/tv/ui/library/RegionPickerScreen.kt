package com.freetia.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.repository.RegionFilter
import com.freetia.tv.data.repository.RegionRules
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingSectionHeader
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.Navigator
import kotlinx.coroutines.launch

/**
 * Region and language picker (§3.3), built from the tvg-country and tvg-language values in the saved playlists.
 * Shown once on first run (Back or "Show everything" skips it) and reachable later from Playlists.
 */
@Composable
fun RegionPickerScreen(nav: Navigator) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val scope = rememberCoroutineScope()
    val stored by app.settings.selectedRegion.flow.collectAsState(initial = null)
    val options by produceState<Pair<List<String>, List<String>>?>(null) {
        val dao = app.database.channelDao()
        value = RegionRules.countries(dao.getCountries(0)) to RegionRules.languages(dao.getLanguages(0))
    }
    val loadedStored = stored
    val loaded = options
    if (loadedStored == null || loaded == null) {
        SettingsScaffold(title = "Region and language", autoFocus = false) { item { NoteText("Loading…") } }
        return
    }
    val firstRun = RegionFilter.needsFirstRunPicker(loadedStored)
    var draft by remember(loadedStored) { mutableStateOf(RegionFilter.decode(loadedStored)) }
    val (countries, languages) = loaded

    val finish = { value: String ->
        scope.launch {
            app.settings.selectedRegion.set(value)
            nav.pop()
        }
        Unit
    }
    // Back on first run means "skip": remember that so the picker doesn't return on every start.
    BackHandler { if (firstRun) finish(RegionFilter.ALL) else nav.pop() }

    SettingsScaffold(
        title = "Region and language",
        subtitle = "Channels from other regions are tucked away. You can change this any time in Playlists.",
        hints = listOf("OK" to "Select", "↑↓" to "Move", "Back" to if (firstRun) "Skip" else "Cancel"),
    ) {
        item(key = "apply") {
            SettingActionRow(
                "Apply", { finish(draft.encode()) },
                subtitle = draft.summary(), showChevron = false,
            )
        }
        item(key = "all") {
            SettingActionRow("Show everything", { finish(RegionFilter.ALL) }, subtitle = "No region or language filter", showChevron = false)
        }
        if (countries.isEmpty() && languages.isEmpty()) {
            item(key = "none") { NoteText("Your playlists don't say which region or language their channels are for, so there is nothing to filter yet.") }
        }
        if (countries.isNotEmpty()) {
            item(key = "h-region") { SettingSectionHeader("Region") }
            items(countries.size, key = { "c-" + countries[it] }) { i ->
                val code = countries[i]
                SettingActionRow(
                    RegionRules.countryName(code),
                    { draft = draft.copy(country = if (draft.country == code) "" else code) },
                    value = if (draft.country == code) "Selected" else code, showChevron = false,
                )
            }
        }
        if (languages.isNotEmpty()) {
            item(key = "h-language") { SettingSectionHeader("Language") }
            items(languages.size, key = { "l-" + languages[it] }) { i ->
                val language = languages[i]
                SettingActionRow(
                    RegionRules.languageName(language),
                    { draft = draft.copy(language = if (draft.language == language) "" else language) },
                    value = if (draft.language == language) "Selected" else null, showChevron = false,
                )
            }
        }
    }
}

private fun RegionFilter.summary(): String = when {
    isAll -> "Everything"
    country.isNotEmpty() && language.isNotEmpty() -> "${RegionRules.countryName(country)}, ${RegionRules.languageName(language)}"
    country.isNotEmpty() -> RegionRules.countryName(country)
    else -> RegionRules.languageName(language)
}
