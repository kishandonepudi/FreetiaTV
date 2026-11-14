package com.freetia.tv.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.repository.LibraryRepository
import com.freetia.tv.ui.components.BrandDialog
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.Navigator
import kotlinx.coroutines.launch

/** The channels in one user group, with rename and delete. */
@Composable
fun GroupDetailScreen(groupId: Long, nav: Navigator) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val scope = rememberCoroutineScope()
    val toast = LocalBrandToast.current
    val library = remember { LibraryRepository(app.database, app.settings) }
    var nameVersion by remember { mutableStateOf(0) }
    val group by produceState<com.freetia.tv.data.model.GroupEntity?>(null, groupId, nameVersion) { value = library.group(groupId) }
    val items = remember(groupId) { library.pagedGroup(groupId).cachedIn(scope) }.collectAsLazyPagingItems()
    val favouriteIds = rememberFavouriteIds()
    var contextChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AllLockedIdsProvider {
        SettingsScaffold(
            title = group?.name ?: "Group",
            hints = listOf("OK" to "Watch", "Hold OK" to "Actions", "↑↓" to "Move", "Back" to "Return"),
        ) {
            if (items.isEmptyAndSettled()) {
                item(key = "empty") { NoteText("This group is empty. On the channel list, hold OK on a channel and choose Add to group.") }
            } else {
                pagedChannelItems(items, favouriteIds, onPlay = { nav.play(it.id) }, onContext = { contextChannel = it })
            }
            item(key = "rename") { SettingActionRow("Rename group", { renaming = true }, showChevron = false) }
            item(key = "delete") { SettingActionRow("Delete group", { confirmDelete = true }, destructive = true, showChevron = false) }
        }
    }
    contextChannel?.let { ChannelContextMenu(it, onDismiss = { contextChannel = null }) }
    if (renaming) {
        NameDialog(
            title = "Rename group", label = "Group name", initial = group?.name.orEmpty(), confirmLabel = "Save",
            onConfirm = { name ->
                renaming = false
                scope.launch { library.renameGroup(groupId, name); nameVersion++ }
            },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete) {
        BrandDialog(
            title = "Delete ${group?.name ?: "group"}?",
            message = "The channels stay in your playlists. Only the group is removed.",
            confirmLabel = "Delete", destructive = true,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    library.deleteGroup(groupId)
                    toast.show("Group deleted")
                    nav.pop()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
