package com.freetia.tv.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.repository.LibraryRepository
import com.freetia.tv.ui.components.BrandDialog
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.nav.LocalAppSession
import com.freetia.tv.ui.nav.LocalNavigator
import com.freetia.tv.ui.safety.rememberSafetyActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** OK-long-press or the Menu key on a channel row: Favourite, Add to group, Lock channel, Mark as adult. */
@Composable
fun ChannelContextMenu(channel: ChannelEntity, onDismiss: () -> Unit) {
    BrandDialog(
        title = channel.name,
        onDismiss = onDismiss,
        dismissLabel = "Close",
        hints = listOf("OK" to "Select", "Back" to "Close"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { ChannelActionRows(channel, onDone = onDismiss) }
    }
}

/**
 * The channel action rows, shared by the list menu and the player menu. Lock and adult mark go through
 * `SafetyActions`, which asks for the PIN; the host stays open behind that dialog and closes through [onDone].
 */
@Composable
fun ChannelActionRows(channel: ChannelEntity, onDone: () -> Unit, firstRow: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val scope = rememberCoroutineScope()
    val toast = LocalBrandToast.current
    val safety = rememberSafetyActions()
    val library = remember { LibraryRepository(app.database, app.settings) }
    val favouriteIds by remember { library.favouriteIds() }.collectAsState(initial = emptySet())
    val fresh by produceState(channel, channel.id) {
        app.database.channelDao().observeById(channel.id).collect { if (it != null) value = it }
    }
    val locked by produceState(false, channel.id) { value = app.safety.isIndividuallyLocked(channel.id) }
    var showGroups by remember { mutableStateOf(false) }
    val isFavourite = channel.id in favouriteIds

    // Closes the host once the PIN step (if any) and the write have finished.
    val finishAfterSafety = {
        scope.launch {
            delay(150)
            snapshotFlow { safety.pending }.first { it == null }
            delay(400)
            onDone()
        }
        Unit
    }

    SettingActionRow(
        if (isFavourite) "Remove from favourites" else "Add to favourites",
        {
            scope.launch {
                val now = library.toggleFavourite(channel.id)
                toast.show(if (now) "${channel.name} added to favourites" else "${channel.name} removed from favourites")
                onDone()
            }
        },
        modifier = firstRow, showChevron = false,
    )
    SettingActionRow("Add to group", { showGroups = true }, subtitle = "For example Kids, News or Family")
    SettingActionRow(
        if (locked) "Unlock channel" else "Lock channel",
        { safety.toggleChannelLock(channel); finishAfterSafety() },
        subtitle = "Needs your PIN", showChevron = false,
    )
    SettingActionRow(
        if (fresh.isMarkedAdult) "Not adult" else "Mark as adult",
        { safety.toggleMarkAdult(channel); finishAfterSafety() },
        subtitle = "Needs your PIN. Child Safe Mode hides adult channels", showChevron = false,
    )
    if (showGroups) GroupPickerDialog(channel, library, onDismiss = { showGroups = false })
}

@Composable
private fun GroupPickerDialog(channel: ChannelEntity, library: LibraryRepository, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val groups by remember { library.observeGroups() }.collectAsState(initial = emptyList())
    var member by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var creating by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(channel.id) { member = library.groupsContaining(channel.id).map { it.id }.toSet() }

    BrandDialog(title = "Add to group", onDismiss = onDismiss, dismissLabel = "Done", hints = listOf("OK" to "Add or remove", "Back" to "Done")) {
        LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(groups, key = { it.id }) { g ->
                SettingActionRow(
                    g.name,
                    {
                        scope.launch {
                            val inGroup = g.id in member
                            library.setInGroup(g.id, channel.id, !inGroup)
                            member = if (inGroup) member - g.id else member + g.id
                        }
                    },
                    value = if (g.id in member) "In group" else null, showChevron = false,
                )
            }
            item(key = "new") { SettingActionRow("New group", { creating = true }, subtitle = "For example Kids, News or Family") }
        }
    }
    if (creating) {
        NameDialog(
            title = "New group", label = "Group name", initial = "", confirmLabel = "Create",
            onConfirm = { name ->
                scope.launch {
                    val id = library.createGroup(name)
                    library.setInGroup(id, channel.id, true)
                    member = member + id
                    creating = false
                }
            },
            onDismiss = { creating = false },
        )
    }
}

/**
 * Library rows for the player menu (§3.1, §3.3): the channel actions plus the "Playlists" switcher, which tunes
 * the chosen playlist's startup channel without leaving the player.
 */
@Composable
fun PlayerLibraryRows(channel: ChannelEntity, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val nav = LocalNavigator.current
    val session = LocalAppSession.current
    val toast = LocalBrandToast.current
    val scope = rememberCoroutineScope()
    val playlists by session.playlists.collectAsState()
    var switching by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingActionRow("Playlists", { switching = true }, subtitle = "Switch to another playlist")
        ChannelActionRows(channel, onDone = onClose)
    }
    if (switching) {
        PlaylistSwitcherDialog(
            playlists = playlists.orEmpty(),
            activeId = channel.playlistId,
            showAll = false,
            onPickAll = null,
            onPick = { p ->
                switching = false
                scope.launch {
                    val target = LibraryStartup.channelFor(app, p)
                    session.selectPlaylist(p.id)
                    if (target == null) {
                        toast.show("${p.name} has no channels to play yet", isError = true)
                    } else {
                        nav.play(target.id)
                        onClose()
                    }
                }
            },
            onManage = { switching = false; onClose(); nav.push(com.freetia.tv.ui.nav.Route.Playlists) },
            onDismiss = { switching = false },
        )
    }
}
