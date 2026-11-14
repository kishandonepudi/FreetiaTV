package com.freetia.tv.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.repository.LibraryRepository
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.Navigator

/** Global favourites for the current profile (§3.3), across every playlist. */
@Composable
fun FavouritesScreen(nav: Navigator) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val scope = rememberCoroutineScope()
    val library = remember { LibraryRepository(app.database, app.settings) }
    val items = remember { library.pagedFavourites().cachedIn(scope) }.collectAsLazyPagingItems()
    val favouriteIds = rememberFavouriteIds()
    var contextChannel by remember { mutableStateOf<ChannelEntity?>(null) }

    AllLockedIdsProvider {
        SettingsScaffold(
            title = "Favourites",
            hints = listOf("OK" to "Watch", "Hold OK" to "Actions", "↑↓" to "Move", "Back" to "Return"),
        ) {
            if (items.isEmptyAndSettled()) {
                item(key = "empty") {
                    NoteText("No favourites yet. On the channel list, hold OK on a channel (or press Menu) and choose Add to favourites.")
                }
            } else {
                pagedChannelItems(items, favouriteIds, onPlay = { nav.play(it.id) }, onContext = { contextChannel = it })
            }
        }
    }
    contextChannel?.let { ChannelContextMenu(it, onDismiss = { contextChannel = null }) }
}
