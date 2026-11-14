package com.freetia.tv.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.repository.LibraryRepository
import com.freetia.tv.data.repository.RegionRules
import com.freetia.tv.ui.ChannelRow
import com.freetia.tv.ui.safety.LockedIdsProvider
import com.freetia.tv.ui.theme.LocalBrand

/** Muted line used for empty and loading states inside a settings-style list. */
@Composable
internal fun NoteText(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp)) {
        Text(text, fontSize = 16.sp, color = LocalBrand.current.textMuted)
    }
}

/** Padlocks for lists that mix playlists: the union of every playlist's individually locked channel ids. */
@Composable
fun AllLockedIdsProvider(content: @Composable () -> Unit) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val playlists by com.freetia.tv.ui.nav.LocalAppSession.current.playlists.collectAsState()
    val ids by remember(playlists) {
        val list = playlists.orEmpty()
        if (list.isEmpty()) kotlinx.coroutines.flow.flowOf(emptySet<Long>())
        else kotlinx.coroutines.flow.combine(list.map { app.safety.lockedIds(it.id) }) { sets ->
            sets.fold(emptySet<Long>()) { acc, s -> acc + s }
        }
    }.collectAsState(emptySet())
    androidx.compose.runtime.CompositionLocalProvider(
        com.freetia.tv.ui.safety.LocalLockedChannelIds provides ids, content = content,
    )
}

/** Favourite ids for star badges on result rows. */
@Composable
internal fun rememberFavouriteIds(): Set<Long> {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val ids by remember { LibraryRepository(app.database, app.settings).favouriteIds() }.collectAsState(initial = emptySet())
    return ids
}

/** Paged channel rows inside a `SettingsScaffold` list; every result opens the player and Hold OK / Menu-less actions. */
internal fun LazyListScope.pagedChannelItems(
    items: LazyPagingItems<ChannelEntity>,
    favouriteIds: Set<Long>,
    onPlay: (ChannelEntity) -> Unit,
    onContext: (ChannelEntity) -> Unit,
) {
    items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
        items[index]?.let { ch -> ChannelResultRow(ch, favouriteIds, onPlay, onContext) }
    }
}

internal fun LazyListScope.channelItems(
    channels: List<ChannelEntity>,
    favouriteIds: Set<Long>,
    onPlay: (ChannelEntity) -> Unit,
    onContext: (ChannelEntity) -> Unit,
) {
    items(channels, key = { it.id }) { ch -> ChannelResultRow(ch, favouriteIds, onPlay, onContext) }
}

@Composable
private fun ChannelResultRow(
    ch: ChannelEntity,
    favouriteIds: Set<Long>,
    onPlay: (ChannelEntity) -> Unit,
    onContext: (ChannelEntity) -> Unit,
) {
    ChannelRow(
        ch,
        primaryCategory = RegionRules.categoriesOf(ch.groupTitle).firstOrNull(),
        onClick = { onPlay(ch) },
        onFocus = {},
        favourite = ch.id in favouriteIds,
        onLongPress = { onContext(ch) },
    )
}

/** True once a paged list has settled with nothing in it. */
internal fun <T : Any> LazyPagingItems<T>.isEmptyAndSettled(): Boolean =
    itemCount == 0 && loadState.refresh is LoadState.NotLoading
