package com.freetia.tv.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.filter
import com.freetia.tv.FreetiaApp
import com.freetia.tv.core.safety.ChannelFilter
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.repository.LibraryRepository
import com.freetia.tv.data.repository.RegionFilter
import com.freetia.tv.data.repository.RegionRules
import com.freetia.tv.ui.ALL
import com.freetia.tv.ui.CategoryChips
import com.freetia.tv.ui.CategoryRail
import com.freetia.tv.ui.ChannelDetails
import com.freetia.tv.ui.ChannelRow
import com.freetia.tv.ui.theme.LocalBrand
import kotlinx.coroutines.flow.map

/**
 * The channel browser for lists too big to index in memory (more than 5,000 channels) and for the merged All view
 * (§3.1, §4): the same rail, rows and details pane as the regular browser, but rows are paged from Room.
 * [playlistId] null = every playlist in priority order. Category counts are not computed (that would scan the table).
 */
@Composable
fun PagedBrowser(
    playlistId: Long?,
    region: RegionFilter,
    wide: Boolean,
    actions: List<Pair<String, () -> Unit>>,
    favouriteIds: Set<Long>,
    onPlay: (ChannelEntity) -> Unit,
    onFocusChannel: (ChannelEntity?) -> Unit,
    onContext: (ChannelEntity) -> Unit,
) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val scope = rememberCoroutineScope()
    val childSafe by app.settings.childSafeMode.flow.collectAsState(initial = false)
    val hideDead by app.settings.hideDeadStreams.flow.collectAsState(initial = false)
    var selected by rememberSaveable(playlistId) { mutableStateOf(ALL) }
    val categories by produceState(listOf(ALL to -1), playlistId, childSafe) {
        value = listOf(ALL to -1) + if (playlistId == null) emptyList()
        else RegionRules.categories(app.database.channelDao().getGroups(playlistId, childSafe))
            .filterNot { childSafe && ChannelFilter.isAdultText(it, "") }
            .map { it to -1 }
    }

    val flow = remember(playlistId, selected, region.country, childSafe, hideDead) {
        val dao = app.database.channelDao()
        Pager(PagingConfig(pageSize = LibraryRepository.PAGE_SIZE, enablePlaceholders = false)) {
            when {
                playlistId == null -> dao.pageAll(childSafe, hideDead)
                selected != ALL -> dao.pageByCategory(playlistId, selected, childSafe, hideDead)
                region.country.isNotEmpty() -> dao.pageByRegion(playlistId, region.country, childSafe, hideDead)
                else -> dao.pageByPlaylist(playlistId, childSafe, hideDead)
            }
        }.flow
            .map { page -> page.filter { !ChannelFilter.isHidden(it, childSafe, hideDead) } }
            .cachedIn(scope)
    }
    val items = flow.collectAsLazyPagingItems()
    var focused by remember(selected, playlistId) { mutableStateOf<ChannelEntity?>(null) }
    var autoFocus by remember { mutableStateOf(true) }
    val onRowFocus: (ChannelEntity) -> Unit = {
        focused = it
        autoFocus = false
        onFocusChannel(it)
    }
    val onRailFocus = { onFocusChannel(null) }

    val list: @Composable (Modifier) -> Unit = { modifier ->
        PagedList(
            items = items, favouriteIds = favouriteIds, autoFocus = autoFocus, onPlay = onPlay, onFocus = onRowFocus,
            onContext = onContext, emptyText = "No channels here. Try another category, or change your region in Playlists.",
            modifier = modifier,
        )
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            CategoryRail(categories, selected, { selected = it }, actions, onRailFocus, modifier = Modifier.width(176.dp))
            Spacer(Modifier.width(16.dp))
            list(Modifier.weight(1f))
            Spacer(Modifier.width(20.dp))
            val shown = focused
            ChannelDetails(shown, shown?.let { RegionRules.categoriesOf(it.groupTitle) }.orEmpty(), Modifier.width(300.dp))
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            CategoryChips(categories, selected, { selected = it }, actions)
            Spacer(Modifier.height(8.dp))
            list(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PagedList(
    items: androidx.paging.compose.LazyPagingItems<ChannelEntity>,
    favouriteIds: Set<Long>,
    autoFocus: Boolean,
    onPlay: (ChannelEntity) -> Unit,
    onFocus: (ChannelEntity) -> Unit,
    onContext: (ChannelEntity) -> Unit,
    emptyText: String,
    modifier: Modifier,
) {
    val brand = LocalBrand.current
    if (items.isEmptyAndSettled()) {
        Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(emptyText, fontSize = 16.sp, color = brand.textMuted)
        }
        return
    }
    val state = rememberLazyListState()
    val first = remember { FocusRequester() }
    LazyColumn(
        state = state,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 24.dp),
        modifier = modifier.fillMaxHeight(),
    ) {
        items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
            val ch = items[index] ?: return@items
            ChannelRow(
                ch,
                primaryCategory = RegionRules.categoriesOf(ch.groupTitle).firstOrNull(),
                onClick = { onPlay(ch) },
                onFocus = { onFocus(ch) },
                favourite = ch.id in favouriteIds,
                onLongPress = { onContext(ch) },
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
    }
    if (autoFocus && items.itemCount > 0) {
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}
