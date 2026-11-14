package com.freetia.tv.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.model.PlaylistEntity
import com.freetia.tv.data.sync.HealthChecker
import com.freetia.tv.ui.components.BrandDialog
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingSectionHeader
import com.freetia.tv.ui.components.SettingToggleRow
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.LocalAppSession
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.nav.Route
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.items

/**
 * Add, edit, rename, delete, reorder, set default and refresh playlists (§3.1, §3.2). Priority 1 opens first.
 * A failed update shows a warning on the playlist and keeps its saved channels.
 */
@Composable
fun PlaylistManagerScreen(nav: Navigator) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val session = LocalAppSession.current
    val toast = LocalBrandToast.current
    val scope = rememberCoroutineScope()
    val playlists by session.playlists.collectAsState()
    val hideDead by app.settings.hideDeadStreams.flow.collectAsState(initial = false)
    val health by HealthChecker.get(app).progress.collectAsState()
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var renaming by remember { mutableStateOf<PlaylistEntity?>(null) }
    var deleting by remember { mutableStateOf<PlaylistEntity?>(null) }
    val list = playlists.orEmpty()

    SettingsScaffold(
        title = "Playlists",
        subtitle = if (list.isEmpty()) "No playlists yet" else "Priority 1 opens first. Select a playlist for its options.",
        hints = listOf("OK" to "Options", "↑↓" to "Move", "Back" to "Return"),
    ) {
        items(list, key = { it.id }) { p ->
            SettingActionRow(
                title = (if (!p.lastUpdateSuccess) "⚠ " else "") + "${p.priority}. ${p.name}" + if (p.isDefault) " (default)" else "",
                onClick = { menuFor = p.id },
                subtitle = playlistStatus(p) + if (p.updateOnStartup) ". Updates when the app starts" else ". Updates only when you refresh",
            )
        }
        item(key = "add") { SettingActionRow("Add playlist", { nav.push(Route.AddPlaylist) }, showChevron = false) }
        if (list.isNotEmpty()) {
            item(key = "refresh-all") {
                SettingActionRow(
                    "Refresh all now",
                    {
                        toast.show("Updating your playlists…")
                        app.appScope.launch { list.forEach { app.playlistSyncer.sync(it, force = true) } }
                    },
                    subtitle = "Downloads every playlist again", showChevron = false,
                )
            }
        }
        item(key = "h-filters") { SettingSectionHeader("Filters") }
        item(key = "region") {
            SettingActionRow("Region and language", { nav.push(Route.RegionPicker) }, subtitle = "Choose which channels to show")
        }
        item(key = "hide-dead") {
            SettingToggleRow(
                "Hide dead streams", hideDead, { on -> scope.launch { app.settings.hideDeadStreams.set(on) } },
                subtitle = "Hides channels that did not respond in a stream check",
            )
        }
        health?.let { h ->
            item(key = "health") {
                SettingActionRow(
                    "Stream check", {},
                    subtitle = if (h.done) "Checked ${h.checked} channels, ${h.dead} not responding" else "Checking ${h.checked} of ${h.total}…",
                    showChevron = false,
                )
            }
        }
    }

    val current = list.firstOrNull { it.id == menuFor }
    if (current != null) {
        PlaylistOptionsDialog(
            playlist = current,
            isFirst = list.first().id == current.id,
            isLast = list.last().id == current.id,
            health = health,
            onMove = { delta -> scope.launch { app.playlistRepository.move(current.id, delta) } },
            onShow = {
                menuFor = null
                session.selectPlaylist(current.id)
                nav.popToHome()
            },
            onRefresh = {
                menuFor = null
                toast.show("Updating ${current.name}…")
                app.appScope.launch { app.playlistSyncer.sync(current, force = true) }
            },
            onEdit = { menuFor = null; nav.push(Route.PlaylistEdit(current.id)) },
            onRename = { menuFor = null; renaming = current },
            onDefault = {
                menuFor = null
                scope.launch {
                    if (current.isDefault) app.playlistRepository.clearDefault() else app.playlistRepository.setDefault(current.id)
                    toast.show(if (current.isDefault) "No default playlist. Priority 1 opens first." else "${current.name} opens first")
                }
            },
            onCheck = {
                menuFor = null
                toast.show("Checking streams in the background…")
                app.appScope.launch { HealthChecker.get(app).run(current.id, force = true) }
            },
            onDelete = { menuFor = null; deleting = current },
            onDismiss = { menuFor = null },
        )
    }
    renaming?.let { p ->
        NameDialog(
            title = "Rename playlist", label = "Name", initial = p.name, confirmLabel = "Save",
            onConfirm = { name ->
                renaming = null
                scope.launch { app.playlistRepository.rename(p.id, name) }
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { p ->
        BrandDialog(
            title = "Delete ${p.name}?",
            message = "Its saved channels are removed from this device. Favourites from this playlist go too.",
            confirmLabel = "Delete", destructive = true,
            onConfirm = {
                deleting = null
                scope.launch {
                    app.playlistRepository.delete(p)
                    toast.show("${p.name} deleted")
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun PlaylistOptionsDialog(
    playlist: PlaylistEntity,
    isFirst: Boolean,
    isLast: Boolean,
    health: com.freetia.tv.data.sync.HealthProgress?,
    onMove: (Int) -> Unit,
    onShow: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDefault: () -> Unit,
    onCheck: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val checking = health != null && !health.done && health.playlistId == playlist.id
    BrandDialog(
        title = playlist.name,
        onDismiss = onDismiss,
        dismissLabel = "Close",
        message = playlistStatus(playlist),
        hints = listOf("OK" to "Select", "↑↓" to "Move", "Back" to "Close"),
    ) {
        LazyColumn(Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { SettingActionRow("Show this playlist", onShow, showChevron = false) }
            item {
                SettingActionRow(
                    "Refresh now", onRefresh,
                    subtitle = if (playlist.lastUpdateSuccess) "Download the latest channels" else "The last update failed. Try again",
                    showChevron = false,
                )
            }
            item {
                SettingActionRow(
                    "Move up", { onMove(-1) }, enabled = !isFirst,
                    subtitle = "Priority ${playlist.priority}", showChevron = false,
                )
            }
            item {
                SettingActionRow(
                    "Move down", { onMove(1) }, enabled = !isLast,
                    subtitle = "Priority ${playlist.priority}", showChevron = false,
                )
            }
            item {
                SettingActionRow(
                    if (playlist.isDefault) "Remove as default" else "Set as default", onDefault,
                    subtitle = "The default opens first at start", showChevron = false,
                )
            }
            item { SettingActionRow("Edit settings", onEdit, subtitle = "Link, region, guide link, update options") }
            item { SettingActionRow("Rename", onRename, showChevron = false) }
            item {
                SettingActionRow(
                    "Check streams", onCheck, enabled = !checking,
                    subtitle = "Finds channels that do not respond", showChevron = false,
                )
            }
            item { SettingActionRow("Delete", onDelete, destructive = true, showChevron = false) }
        }
    }
}
