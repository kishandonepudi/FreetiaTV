package com.freetia.tv.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.model.GroupEntity
import com.freetia.tv.data.repository.LibraryRepository
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.nav.Route
import kotlinx.coroutines.launch

/** User-created groups such as Kids, News or Family (§3.3), per profile. */
@Composable
fun GroupsScreen(nav: Navigator) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val scope = rememberCoroutineScope()
    val library = remember { LibraryRepository(app.database, app.settings) }
    val groups by remember { library.observeGroups() }.collectAsState(initial = null)
    var creating by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = "Groups",
        subtitle = "Your own lists, like Kids, News or Family",
        hints = listOf("OK" to "Open", "↑↓" to "Move", "Back" to "Return"),
    ) {
        item(key = "new") {
            SettingActionRow("New group", { creating = true }, subtitle = "Add channels from the channel list: hold OK, then Add to group")
        }
        val list = groups
        if (list != null && list.isEmpty()) {
            item(key = "empty") { NoteText("No groups yet. Create one, for example Kids.") }
        }
        items(list.orEmpty().size, key = { list.orEmpty()[it].id }) { index ->
            GroupRow(list.orEmpty()[index], library) { nav.push(Route.GroupDetail(it.id)) }
        }
    }
    if (creating) {
        NameDialog(
            title = "New group", label = "Group name", initial = "", confirmLabel = "Create",
            onConfirm = { name ->
                creating = false
                scope.launch { nav.push(Route.GroupDetail(library.createGroup(name))) }
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun GroupRow(group: GroupEntity, library: LibraryRepository, onOpen: (GroupEntity) -> Unit) {
    val count by remember(group.id) { library.observeGroupCount(group.id) }.collectAsState(initial = 0)
    SettingActionRow(group.name, { onOpen(group) }, subtitle = if (count == 1) "1 channel" else "$count channels")
}
