package com.freetia.tv.ui.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.model.PlaylistEntity
import com.freetia.tv.ui.FormField
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingChoiceRow
import com.freetia.tv.ui.components.SettingToggleRow
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.playlistUrlError
import com.freetia.tv.ui.theme.LocalBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val REFRESH_INTERVALS = listOf(0, 6 * 60, 12 * 60, 24 * 60, 48 * 60)

private fun intervalLabel(minutes: Int): String = when (minutes) {
    0 -> "Off"
    else -> "Every ${minutes / 60} hours"
}

private data class EditSource(val playlist: PlaylistEntity, val url: String, val epgUrl: String)

/** Every per-playlist option from §3.1. The playlist link and guide link are kept in encrypted storage. */
@Composable
fun PlaylistEditScreen(playlistId: Long, nav: Navigator) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val source by produceState<EditSource?>(null, playlistId) {
        value = withContext(Dispatchers.IO) {
            app.playlistRepository.get(playlistId)?.let {
                EditSource(it, app.playlistRepository.sourceUrl(it).orEmpty(), app.playlistRepository.userEpgUrl(it))
            }
        }
    }
    val loaded = source
    if (loaded == null) {
        SettingsScaffold(title = "Edit playlist", autoFocus = false, hints = listOf("Back" to "Return")) {
            item { NoteText("Loading…") }
        }
        return
    }
    EditForm(loaded, nav)
}

@Composable
private fun EditForm(source: EditSource, nav: Navigator) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val toast = LocalBrandToast.current
    val scope = rememberCoroutineScope()
    val brand = LocalBrand.current
    val p = source.playlist
    var name by rememberSaveable { mutableStateOf(p.name) }
    var url by rememberSaveable { mutableStateOf(source.url) }
    var region by rememberSaveable { mutableStateOf(p.region) }
    var epgUrl by rememberSaveable { mutableStateOf(source.epgUrl) }
    var updateOnStartup by rememberSaveable { mutableStateOf(p.updateOnStartup) }
    var interval by rememberSaveable { mutableStateOf(p.backgroundRefreshIntervalMinutes) }
    var backgroundPlay by rememberSaveable { mutableStateOf(p.allowBackgroundPlay) }
    var resume by rememberSaveable { mutableStateOf(p.resumeLastChannel) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var epgError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val save = {
        urlError = playlistUrlError(url)
        epgError = epgUrl.takeIf { it.isNotBlank() }?.let(::playlistUrlError)
        if (urlError == null && epgError == null && !saving) {
            saving = true
            val cleanUrl = url.trim()
            scope.launch {
                val repo = app.playlistRepository
                val linkChanged = cleanUrl != source.url
                if (linkChanged) repo.updateSourceUrl(p, cleanUrl)
                repo.update(
                    p.copy(
                        name = name.trim().ifEmpty { p.name },
                        region = region.trim(),
                        updateOnStartup = updateOnStartup,
                        backgroundRefreshIntervalMinutes = interval,
                        allowBackgroundPlay = backgroundPlay,
                        resumeLastChannel = resume,
                    )
                )
                repo.setUserEpgUrl(p, epgUrl)
                toast.show("Saved")
                nav.pop()
                // A new link means the saved channels are stale: fetch them now, in the background.
                if (linkChanged) app.appScope.launch { repo.get(p.id)?.let { app.playlistSyncer.sync(it, force = true) } }
            }
        }
    }

    SettingsScaffold(
        title = "Edit playlist",
        subtitle = p.name,
        hints = listOf("OK" to "Edit or select", "↑↓" to "Move", "Back" to "Discard changes"),
    ) {
        item(key = "name") {
            FormField(
                "Name", name, { name = it.take(60) }, placeholder = "Playlist name", isError = false,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "url") {
            FormField(
                "Playlist link", url, { url = it; urlError = null }, placeholder = "https://", isError = urlError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            urlError?.let { Text(it, fontSize = 13.sp, color = brand.error) }
        }
        item(key = "region") {
            FormField(
                "Region (optional)", region, { region = it.take(30) }, placeholder = "For example IN or US", isError = false,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "epg") {
            FormField(
                "TV guide link (optional)", epgUrl, { epgUrl = it; epgError = null },
                placeholder = "Leave empty to use the playlist's own guide", isError = epgError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            epgError?.let { Text(it, fontSize = 13.sp, color = brand.error) }
        }
        item(key = "startup") {
            SettingToggleRow(
                "Update when app starts", updateOnStartup, { updateOnStartup = it },
                subtitle = "Off means this playlist makes no network request at start",
            )
        }
        item(key = "interval") {
            SettingChoiceRow(
                "Background refresh", REFRESH_INTERVALS, interval, { interval = it }, label = ::intervalLabel,
                subtitle = "Needs Update when app starts to be on", enabled = updateOnStartup,
            )
        }
        item(key = "bgplay") {
            SettingToggleRow(
                "Allow background play", backgroundPlay, { backgroundPlay = it },
                subtitle = "Keep the sound going when you press Home",
            )
        }
        item(key = "resume") {
            SettingToggleRow(
                "Resume last channel", resume, { resume = it },
                subtitle = "Off means the app starts on the first channel",
            )
        }
        item(key = "save") { SettingActionRow("Save", save, showChevron = false) }
        item(key = "cancel") { SettingActionRow("Discard changes", { nav.pop() }, showChevron = false) }
    }
}
