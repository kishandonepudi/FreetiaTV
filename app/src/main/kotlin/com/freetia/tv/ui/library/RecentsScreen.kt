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
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.repository.LibraryRepository
import com.freetia.tv.ui.components.BrandDialog
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.Navigator
import kotlinx.coroutines.launch

/** The last 20 channels watched by the current profile (§3.3), newest first. */
@Composable
fun RecentsScreen(nav: Navigator) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val scope = rememberCoroutineScope()
    val toast = LocalBrandToast.current
    val library = remember { LibraryRepository(app.database, app.settings) }
    val recents by remember { library.observeRecents() }.collectAsState(initial = null)
    val favouriteIds = rememberFavouriteIds()
    var contextChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    AllLockedIdsProvider {
        SettingsScaffold(
            title = "Recently watched",
            subtitle = "Your last 20 channels",
            hints = listOf("OK" to "Watch", "Hold OK" to "Actions", "↑↓" to "Move", "Back" to "Return"),
        ) {
            val list = recents
            when {
                list == null -> Unit
                list.isEmpty() -> item(key = "empty") { NoteText("Nothing here yet. Channels you watch will show up here.") }
                else -> {
                    channelItems(list, favouriteIds, onPlay = { nav.play(it.id) }, onContext = { contextChannel = it })
                    item(key = "clear") {
                        SettingActionRow("Clear recently watched", { confirmClear = true }, destructive = true, showChevron = false)
                    }
                }
            }
        }
    }
    contextChannel?.let { ChannelContextMenu(it, onDismiss = { contextChannel = null }) }
    if (confirmClear) {
        BrandDialog(
            title = "Clear recently watched?",
            message = "This only clears the list. Your favourites and groups stay.",
            confirmLabel = "Clear", destructive = true,
            onConfirm = {
                confirmClear = false
                scope.launch { library.clearRecents(); toast.show("Recently watched cleared") }
            },
            onDismiss = { confirmClear = false },
        )
    }
}
