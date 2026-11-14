package com.freetia.tv.ui

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.freetia.tv.FreetiaApp
import com.freetia.tv.R
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.ui.safety.LocalLockedChannelIds
import com.freetia.tv.ui.safety.Padlock
import com.freetia.tv.data.model.EpgProgrammeEntity
import com.freetia.tv.data.model.PlaylistEntity
import com.freetia.tv.playback.PlayerFactory
import com.freetia.tv.ui.theme.BrandColors
import com.freetia.tv.ui.theme.LocalBrand
import com.freetia.tv.data.repository.LibraryRepository
import com.freetia.tv.data.repository.RegionFilter
import com.freetia.tv.ui.library.ChannelContextMenu
import com.freetia.tv.ui.library.PagedBrowser
import com.freetia.tv.ui.library.PlaylistSwitcherDialog
import com.freetia.tv.ui.nav.LocalAppSession
import com.freetia.tv.ui.nav.LocalNavigator
import com.freetia.tv.ui.nav.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.util.Date

internal const val ALL = "All"
internal const val OTHER = "Other"
// Above this many channels the list is paged from Room instead of indexed in memory (§4).
internal const val PAGING_THRESHOLD = 5_000
// Waiting before starting the preview avoids opening a stream for every row scrolled past.
private const val PREVIEW_DELAY_MS = 900L
// Holding a D-pad key moves focus every ~50 ms; the guide is only queried once focus settles on a row.
private const val SCHEDULE_DEBOUNCE_MS = 600L
private const val SCHEDULE_WINDOW_MS = 60 * 60_000L
private val FadeHeight = 24.dp

private val KeyCapStyle = TextStyle(
    fontSize = 12.sp,
    fontWeight = FontWeight.Bold,
    lineHeight = 1.3.em,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

@Composable
fun ChannelListScreen(
    playlist: PlaylistEntity?,
    playlistsLoaded: Boolean,
    channels: List<ChannelEntity>,
    focusChannelId: Long?,
    onPlay: (ChannelEntity) -> Unit,
    onAddPlaylist: () -> Unit,
    /** Playlist size; above [PAGING_THRESHOLD] the caller passes no rows and the list pages from Room. */
    totalCount: Int = channels.size,
) {
    val brand = LocalBrand.current
    val app = LocalContext.current.applicationContext as FreetiaApp
    val nav = LocalNavigator.current
    val session = LocalAppSession.current
    val library = remember { LibraryRepository(app.database, app.settings) }
    val favouriteIds by remember { library.favouriteIds() }.collectAsState(initial = emptySet())
    val regionStored by app.settings.selectedRegion.flow.collectAsState(initial = RegionFilter.ALL)
    val region = remember(regionStored) { RegionFilter.decode(regionStored) }
    val allPlaylists by session.playlists.collectAsState()
    var viewAll by rememberSaveable { mutableStateOf(false) }
    var showSwitcher by remember { mutableStateOf(false) }
    var contextChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var focusedChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    // The merged view only makes sense with more than one playlist.
    val allView = viewAll && (allPlaylists?.size ?: 0) > 1

    // Region filter (§3.3). If it would hide everything, show the whole playlist instead of an empty screen.
    val regionFiltered = remember(channels, region) {
        if (region.isAll) channels else channels.filter { region.matches(it.tvgCountry, it.tvgLanguage) }
    }
    val shown = if (regionFiltered.isEmpty()) channels else regionFiltered
    val paged = allView || totalCount > PAGING_THRESHOLD
    val noChannels = playlistsLoaded && (playlist == null || totalCount == 0) && !allView
    // Hoisted above the loading/empty branches: a playlist refresh can briefly emit an empty list, which disposes
    // ChannelBrowser; state owned there would reset and throw focus back to the last-watched row.
    var selected by rememberSaveable { mutableStateOf(ALL) }
    val memory = remember { BrowseMemory() }
    val actions = remember(nav) {
        listOf(
            "Search" to { nav.push(Route.Search) },
            "Favourites" to { nav.push(Route.Favourites) },
            "Recently watched" to { nav.push(Route.Recents) },
            "Groups" to { nav.push(Route.Groups) },
            "Playlists" to { showSwitcher = true },
            "+ Add playlist" to onAddPlaylist,
        )
    }

    // TV overscan-safe insets; phones (< 720dp) have no overscan and use 16dp.
    val insets = com.freetia.tv.ui.components.rememberScreenInsets()
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .ambientBackground(brand, flat = app.memoryGuard.isLowRamDevice)
            .padding(horizontal = insets.horizontal, vertical = insets.vertical)
            // Remote MENU key opens the channel actions (favourite, group, lock, adult mark); with no row focused
            // it opens the playlist switcher. Preview so a focused row can't swallow it.
            .onPreviewKeyEvent {
                if (it.key != Key.Menu || !playlistsLoaded) return@onPreviewKeyEvent false
                if (it.type == KeyEventType.KeyUp) {
                    val target = focusedChannel
                    if (target != null) contextChannel = target else showSwitcher = true
                }
                true
            }
    ) {
        val wide = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize()) {
            Header(
                if (allView) null else playlist,
                if (allView) 0 else if (paged) totalCount else shown.size,
                if (allView) "All playlists" else null,
            )
            if (!allView && playlist != null && !playlist.lastUpdateSuccess && totalCount > 0) {
                Text("⚠ Update failed — showing saved channels", fontSize = 13.sp, color = brand.error)
            }
            if (!allView && regionFiltered.isEmpty() && channels.isNotEmpty()) {
                Text("No channels match your region, so all channels are shown.", fontSize = 13.sp, color = brand.textMuted)
            }
            Spacer(Modifier.height(14.dp))

            Box(Modifier.weight(1f)) {
                when {
                    !playlistsLoaded -> Message("Loading…")
                    allView -> com.freetia.tv.ui.library.AllLockedIdsProvider {
                        PagedBrowser(
                            playlistId = null, region = region, wide = wide, actions = actions,
                            favouriteIds = favouriteIds, onPlay = onPlay, onFocusChannel = { focusedChannel = it },
                            onContext = { contextChannel = it },
                        )
                    }
                    playlist == null -> EmptyState(
                        "Add a playlist to start watching. You'll need an M3U or M3U8 link from your TV provider.",
                        onAddPlaylist,
                    )
                    totalCount == 0 -> EmptyState(emptyChannelsMessage(playlist), onAddPlaylist)
                    paged -> PagedBrowser(
                        playlistId = playlist.id, region = region, wide = wide, actions = actions,
                        favouriteIds = favouriteIds, onPlay = onPlay, onFocusChannel = { focusedChannel = it },
                        onContext = { contextChannel = it },
                    )
                    else -> ChannelBrowser(
                        shown, wide, selected, onSelect = { selected = it }, memory, focusChannelId, onPlay,
                        actions, favouriteIds,
                        onFocusChannel = { focusedChannel = it },
                        onContext = { contextChannel = it },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            when {
                !playlistsLoaded -> HintBar("Back ×2" to "Exit")
                noChannels -> HintBar("OK" to "Add playlist", "Back ×2" to "Exit")
                wide -> HintBar(
                    "OK" to "Watch", "Hold OK" to "Actions", "←→" to "Categories", "↑↓" to "Browse",
                    "Menu" to "Actions", "Back ×2" to "Exit",
                )
                else -> HintBar("OK" to "Watch", "↑↓" to "Browse", "Hold" to "Actions", "Back ×2" to "Exit")
            }
        }
    }

    contextChannel?.let { ChannelContextMenu(it, onDismiss = { contextChannel = null }) }
    if (showSwitcher) {
        PlaylistSwitcherDialog(
            playlists = allPlaylists.orEmpty(),
            activeId = if (allView) null else playlist?.id,
            showAll = allView,
            onPickAll = { viewAll = true; selected = ALL; showSwitcher = false },
            onPick = {
                viewAll = false
                selected = ALL
                memory.focusedId = null
                memory.category = null
                memory.listState = null
                session.selectPlaylist(it.id)
                showSwitcher = false
            },
            onManage = { showSwitcher = false; nav.push(Route.Playlists) },
            onDismiss = { showSwitcher = false },
        )
    }
}

/** Scroll position and last focused channel; outlives ChannelBrowser so a transient empty list can't reset them. */
private class BrowseMemory {
    var category: String? = null
    var listState: LazyListState? = null
    var focusedId: Long? = null
}

/** Categories split once per channel list, so key presses never re-split groupTitle. */
@Immutable
private class CategoryIndex(
    val categories: List<Pair<String, Int>>,
    val byCategory: Map<String, List<ChannelEntity>>,
    val categoriesOf: Map<Long, List<String>>,
)

private fun buildIndex(channels: List<ChannelEntity>): CategoryIndex {
    val categoriesOf = HashMap<Long, List<String>>(channels.size * 2)
    val byCategory = LinkedHashMap<String, MutableList<ChannelEntity>>()
    for (ch in channels) {
        val cats = ch.categories()
        categoriesOf[ch.id] = cats
        cats.forEach { byCategory.getOrPut(it) { ArrayList() }.add(ch) }
    }
    val counts = byCategory.entries.sortedByDescending { it.value.size }.map { it.key to it.value.size }
    return CategoryIndex(listOf(ALL to channels.size) + counts, byCategory + (ALL to channels), categoriesOf)
}

private fun Modifier.ambientBackground(brand: BrandColors, flat: Boolean): Modifier =
    // Low-RAM TVs (MemoryGuard, M2) and high contrast get flat ink: no full-screen gradient fill each frame.
    if (flat || !brand.ambientGlow) background(brand.ink)
    else drawWithCache {
        val w = size.width.coerceAtLeast(1f)
        val violetGlow = Brush.radialGradient(
            listOf(brand.violet.copy(alpha = 0.22f), Color.Transparent),
            center = Offset(w * 0.1f, 0f),
            radius = w * 0.55f,
        )
        val blueGlow = Brush.radialGradient(
            listOf(brand.blue.copy(alpha = 0.16f), Color.Transparent),
            center = Offset(w * 0.95f, size.height),
            radius = w * 0.6f,
        )
        onDrawBehind {
            drawRect(brand.ink)
            drawRect(violetGlow)
            drawRect(blueGlow)
        }
    }

/** Fades the last [FadeHeight] into ink so a row cut off by the viewport reads as "more below", not as clipped. */
private fun Modifier.bottomFade(ink: Color): Modifier = drawWithCache {
    val h = FadeHeight.toPx().coerceAtMost(size.height)
    val top = size.height - h
    val fade = Brush.verticalGradient(
        listOf(Color.Transparent, ink.copy(alpha = 0.9f)), startY = top, endY = size.height,
    )
    onDrawWithContent {
        drawContent()
        drawRect(fade, topLeft = Offset(0f, top), size = Size(size.width, h))
    }
}

@Composable
private fun Header(playlist: PlaylistEntity?, count: Int, title: String? = null) {
    val context = LocalContext.current
    val brand = LocalBrand.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.ic_brand_mark), contentDescription = "Freetia", modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.width(12.dp))
        // Name and count share the free space; only the name shrinks, so the clock stays pinned to the right edge.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title ?: playlist?.name ?: "Freetia TV",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = brand.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (count > 0) {
                Spacer(Modifier.width(12.dp))
                Text("$count channels", fontSize = 14.sp, color = brand.textMuted, maxLines = 1)
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            DateFormat.getTimeFormat(context).format(now),
            fontSize = 18.sp, fontWeight = FontWeight.Medium, color = brand.textPrimary,
        )
    }
}

private fun ChannelEntity.categories(): List<String> =
    com.freetia.tv.data.repository.RegionRules.categoriesOf(groupTitle).ifEmpty { listOf(OTHER) }

@Composable
private fun ChannelBrowser(
    channels: List<ChannelEntity>,
    wide: Boolean,
    selected: String,
    onSelect: (String) -> Unit,
    memory: BrowseMemory,
    focusChannelId: Long?,
    onPlay: (ChannelEntity) -> Unit,
    actions: List<Pair<String, () -> Unit>>,
    favouriteIds: Set<Long>,
    onFocusChannel: (ChannelEntity?) -> Unit,
    onContext: (ChannelEntity) -> Unit,
) {
    // Splitting groupTitle for 10k channels is long enough to drop frames, so it runs off the main thread;
    // the previous index stays on screen until the new one is ready.
    var index by remember { mutableStateOf<CategoryIndex?>(null) }
    LaunchedEffect(channels) {
        index = withContext(Dispatchers.Default) { buildIndex(channels) }
    }
    val idx = index ?: return
    // A refresh or process-death restore can leave a category that no longer exists; the rail's focus restorer
    // would then point at nothing, so fall back to All.
    if (selected !in idx.byCategory) {
        LaunchedEffect(selected) { onSelect(ALL) }
        return
    }
    val visible = idx.byCategory[selected].orEmpty()
    // Keyed on the category so the details pane and preview never show a channel outside the visible list.
    var focused by remember(selected) { mutableStateOf<ChannelEntity?>(null) }
    // Only the first list grabs focus; later category switches must leave focus on the rail.
    var autoFocus by remember { mutableStateOf(true) }
    val onRowFocus: (ChannelEntity) -> Unit = {
        focused = it
        memory.focusedId = it.id
        autoFocus = false
        onFocusChannel(it)
    }
    val onRailFocus = { onFocusChannel(null) }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            CategoryRail(idx.categories, selected, onSelect, actions, onRailFocus, modifier = Modifier.width(176.dp))
            Spacer(Modifier.width(16.dp))
            key(selected) {
                ChannelList(
                    visible, selected, idx.categoriesOf, memory, focusChannelId, autoFocus, onPlay, onRowFocus,
                    favouriteIds, onContext, modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.width(20.dp))
            val shown = focused ?: visible.firstOrNull()
            ChannelDetails(shown, shown?.let { idx.categoriesOf[it.id] }.orEmpty(), Modifier.width(300.dp))
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            CategoryChips(idx.categories, selected, onSelect, actions)
            Spacer(Modifier.height(8.dp))
            key(selected) {
                ChannelList(
                    visible, selected, idx.categoriesOf, memory, focusChannelId, autoFocus, onPlay, onRowFocus,
                    favouriteIds, onContext, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@kotlin.OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CategoryRail(
    categories: List<Pair<String, Int>>,
    selected: String,
    onSelect: (String) -> Unit,
    actions: List<Pair<String, () -> Unit>>,
    onFocused: () -> Unit,
    modifier: Modifier,
) {
    val brand = LocalBrand.current
    val selectedItem = remember { FocusRequester() }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = FadeHeight),
        // Entering the rail always lands on the selected category, never on whichever row is level with the list focus
        // (that would silently switch category). An explicit enter rather than focusRestorer: the restorer would
        // return to the Add playlist item when focus last left the rail from there.
        modifier = modifier
            .fillMaxHeight()
            .bottomFade(brand.ink)
            .focusProperties { enter = { selectedItem } }
            .focusGroup(),
    ) {
        items(categories, key = { it.first }) { (name, count) ->
            CategoryItem(
                name, count.takeIf { it >= 0 }, name == selected,
                onClick = { onSelect(name) },
                onFocused = onFocused,
                modifier = if (name == selected) Modifier.focusRequester(selectedItem) else Modifier,
            )
        }
        // Google TV / Chromecast remotes have no MENU key, so every screen the colour buttons open (and the
        // playlist form) needs a reachable item too. An Int key can never collide with a category name.
        itemsIndexed(actions, key = { i, _ -> -(i + 1) }) { _, (label, run) ->
            CategoryItem(label, count = null, isSelected = false, onClick = run, onFocused = onFocused, selectOnFocus = false)
        }
    }
}

@Composable
private fun CategoryItem(
    name: String,
    count: Int?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectOnFocus: Boolean = true,
    onFocused: () -> Unit = {},
) {
    val brand = LocalBrand.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused() }
    // Google TV style: moving focus onto a category selects it.
    if (selectOnFocus) LaunchedEffect(focused) { if (focused) onClick() }
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, tween(150), label = "categoryScale")
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(50),
        color = when {
            focused -> Color.White
            isSelected -> brand.selectedTint
            else -> Color.Transparent
        },
        border = if (focused) BorderStroke(brand.focusBorderWidth, Color.White) else null,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (isSelected || focused) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    focused -> brand.ink
                    isSelected -> brand.textPrimary
                    else -> brand.textMuted
                },
                modifier = Modifier.weight(1f),
            )
            if (count != null) {
                Text(count.toString(), fontSize = 12.sp, color = if (focused) brand.inkMuted else brand.textMuted)
            }
        }
    }
}

@Composable
internal fun CategoryChips(
    categories: List<Pair<String, Int>>,
    selected: String,
    onSelect: (String) -> Unit,
    actions: List<Pair<String, () -> Unit>>,
) {
    // Padding leaves room for the focus scale and border so they aren't clipped by the row.
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        items(categories, key = { it.first }) { (name, _) ->
            CategoryChip(name, name == selected) { onSelect(name) }
        }
        // Phones have no MENU key; these are their touch targets for the library screens and the form.
        itemsIndexed(actions, key = { i, _ -> -(i + 1) }) { _, (label, run) ->
            CategoryChip(label, isSelected = false, onSelect = run)
        }
    }
}

@Composable
private fun CategoryChip(name: String, isSelected: Boolean, onSelect: () -> Unit) {
    val brand = LocalBrand.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, tween(150), label = "chipScale")
    Surface(
        onClick = onSelect,
        interactionSource = interaction,
        shape = RoundedCornerShape(50),
        color = when {
            focused -> Color.White
            isSelected -> brand.selectedTint
            else -> brand.tintChip
        },
        border = if (focused) BorderStroke(brand.focusBorderWidth, Color.White) else null,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Text(
            name, fontSize = 14.sp, maxLines = 1,
            fontWeight = if (focused || isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) brand.ink else brand.textPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@kotlin.OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChannelList(
    channels: List<ChannelEntity>,
    category: String,
    categoriesOf: Map<Long, List<String>>,
    memory: BrowseMemory,
    focusChannelId: Long?,
    autoFocus: Boolean,
    onPlay: (ChannelEntity) -> Unit,
    onFocus: (ChannelEntity) -> Unit,
    favouriteIds: Set<Long>,
    onContext: (ChannelEntity) -> Unit,
    modifier: Modifier,
) {
    if (channels.isEmpty()) {
        Box(modifier.fillMaxHeight()) { Message("No channels in this category.") }
        return
    }
    val brand = LocalBrand.current
    // Resolved once per category (the caller keys on it): re-emissions of the same list must not move the
    // focus target or the scroll position.
    val targetId = remember {
        val id = memory.focusedId ?: focusChannelId
        (channels.firstOrNull { it.id == id } ?: channels.first()).id
    }
    val state = remember {
        memory.listState?.takeIf { memory.category == category } ?: run {
            val start = channels.indexOfFirst { it.id == targetId }.coerceAtLeast(0)
            LazyListState(firstVisibleItemIndex = (start - 3).coerceAtLeast(0)).also {
                memory.category = category
                memory.listState = it
            }
        }
    }
    val target = remember { FocusRequester() }
    LazyColumn(
        state = state,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        // The bottom padding lets the last row scroll clear of the fade.
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 4.dp, bottom = FadeHeight),
        modifier = modifier.fillMaxHeight().bottomFade(brand.ink).focusRestorer(),
    ) {
        items(channels, key = { it.id }) { ch ->
            ChannelRow(
                ch,
                primaryCategory = categoriesOf[ch.id]?.firstOrNull(),
                onClick = { onPlay(ch) },
                onFocus = { onFocus(ch) },
                favourite = ch.id in favouriteIds,
                onLongPress = { onContext(ch) },
                modifier = if (ch.id == targetId) Modifier.focusRequester(target) else Modifier,
            )
        }
    }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            // The target row only exists after the first layout pass.
            withFrameNanos { }
            runCatching { target.requestFocus() }
        }
    }
}

@Composable
internal fun ChannelRow(
    ch: ChannelEntity,
    primaryCategory: String?,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    favourite: Boolean = false,
    /** Hold OK on a remote (or long-press on touch): the channel actions menu. */
    onLongPress: (() -> Unit)? = null,
) {
    val brand = LocalBrand.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocus() }
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, tween(150), label = "rowScale")
    val shape = RoundedCornerShape(10.dp)
    // The click that ends a long press must not also play the channel; the next press start clears the flag.
    var longHandled by remember { mutableStateOf(false) }
    val longPressModifier = if (onLongPress == null) Modifier else Modifier
        .onPreviewKeyEvent { e ->
            val select = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
            if (!select || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                e.nativeKeyEvent.repeatCount == 0 -> { longHandled = false; false }
                !longHandled -> { longHandled = true; onLongPress(); true }
                else -> true
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = { longHandled = false },
                onLongPress = { longHandled = true; onLongPress() },
            )
        }
    Surface(
        onClick = { if (longHandled) longHandled = false else onClick() },
        interactionSource = interaction,
        shape = shape,
        color = if (focused) Color.Transparent else brand.tintSubtle,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(longPressModifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused) Modifier.background(brand.focusGradient, shape) else Modifier)
            .border(brand.focusBorderWidth, if (focused) Color.White else Color.Transparent, shape)
            .semantics { contentDescription = "Channel ${ch.number}, ${ch.name}${if (favourite) ", favourite" else ""}" },
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                ch.number.toString(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                color = if (focused) brand.textPrimary else brand.textMuted,
                modifier = Modifier.width(36.dp),
            )
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(width = 56.dp, height = 36.dp).clip(RoundedCornerShape(6.dp)).background(brand.hairline),
                contentAlignment = Alignment.Center,
            ) { Logo(ch, Modifier.padding(3.dp).fillMaxSize(), focused = focused) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ch.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = brand.textPrimary,
                )
                primaryCategory?.takeIf { it != OTHER }?.let {
                    // §3: no muted text on the focus gradient.
                    Text(
                        it, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (focused) brand.textPrimary else brand.textMuted,
                    )
                }
            }
            if (favourite) {
                Text("★", fontSize = 16.sp, color = if (focused) brand.textPrimary else brand.blue, modifier = Modifier.padding(start = 8.dp))
            }
            if (ch.id in LocalLockedChannelIds.current) Padlock(focused)
        }
    }
}

@Composable
internal fun ChannelDetails(ch: ChannelEntity?, categories: List<String>, modifier: Modifier) {
    val brand = LocalBrand.current
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(brand.panel)
            .border(1.dp, brand.hairline, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        if (ch == null) return@Column
        val app = LocalContext.current.applicationContext as FreetiaApp
        val underPressure by app.memoryGuard.underPressure.collectAsState()
        val previewModifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
        if (app.memoryGuard.livePreviewAllowed(underPressure)) {
            LivePreview(ch, previewModifier)
        } else {
            // Low-RAM TVs and memory pressure: a second decoder would make the whole app lag, so show the logo only.
            Box(previewModifier.background(brand.ink), contentAlignment = Alignment.Center) {
                Logo(ch, Modifier.fillMaxSize(0.55f), fallbackSize = 40)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            ch.name, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, color = brand.textPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text("Channel ${ch.number}", fontSize = 13.sp, color = brand.textMuted)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (categories.filter { it != OTHER } + listOf(ch.tvgLanguage, ch.tvgCountry).flatMap { it.split(';') })
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(3)
                .forEach { Chip(it) }
        }
        Schedule(ch, Modifier.padding(top = 12.dp))
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KeyCap("OK")
            Spacer(Modifier.width(8.dp))
            Text("Watch full screen", fontSize = 13.sp, color = brand.textMuted)
        }
    }
}

// Re-query each minute so "now" stays current without a per-frame clock.
private fun minuteTicks() = flow {
    while (true) {
        emit(System.currentTimeMillis() / 60_000)
        delay(60_000)
    }
}

/** Next hour of EPG for the focused channel; renders nothing when no guide data is loaded. */
@kotlin.OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun Schedule(ch: ChannelEntity, modifier: Modifier) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val context = LocalContext.current
    val brand = LocalBrand.current
    var programmes by remember { mutableStateOf(emptyList<EpgProgrammeEntity>()) }
    LaunchedEffect(ch.id) {
        programmes = emptyList()
        delay(SCHEDULE_DEBOUNCE_MS)
        val tvgId = ch.tvgId.ifBlank { ch.epgChannelId }
        minuteTicks()
            .flatMapLatest { minute ->
                val now = minute * 60_000
                app.database.epgDao().observeSchedule(ch.id, tvgId, now, now + SCHEDULE_WINDOW_MS, limit = 3)
            }
            .collect { programmes = it }
    }
    if (programmes.isEmpty()) return

    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    val now = System.currentTimeMillis()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("NEXT HOUR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = brand.textMuted, letterSpacing = 1.sp)
        programmes.forEach { p ->
            val live = p.start <= now && p.stop > now
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    timeFormat.format(Date(p.start)), fontSize = 12.sp,
                    color = if (live) brand.blue else brand.textMuted, modifier = Modifier.width(64.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        p.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal, color = brand.textPrimary,
                    )
                    if (live) {
                        val progress = ((now - p.start).toFloat() / (p.stop - p.start)).coerceIn(0f, 1f)
                        Box(
                            Modifier.padding(top = 3.dp).fillMaxWidth().height(3.dp)
                                .clip(RoundedCornerShape(2.dp)).background(brand.tintTrack),
                        ) {
                            // §3: live progress is solid brand blue; the gradient is reserved for focus.
                            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(brand.blue))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LivePreview(ch: ChannelEntity, modifier: Modifier) {
    val context = LocalContext.current
    val brand = LocalBrand.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val app = context.applicationContext as FreetiaApp
    val player = remember { PlayerFactory.createPreview(context) }
    var showVideo by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { showVideo = true }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    // In the background the decoder and network are freed entirely (M3); coming back re-prepares the same channel.
    LifecycleStartEffect(player) {
        if (player.currentMediaItem != null) {
            player.prepare()
            player.playWhenReady = true
        }
        onStopOrDispose {
            showVideo = false
            player.stop()
        }
    }
    LaunchedEffect(ch.id) {
        showVideo = false
        player.stop()
        player.clearMediaItems()
        delay(PREVIEW_DELAY_MS)
        // RUNNING_* trim levels are not delivered on API 34+, so ask the OS directly before allocating a decoder.
        if (app.memoryGuard.sample()) return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(ch.streamUrl))
        // If the app went to the background during the delay, LifecycleStartEffect prepares it on return.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            player.prepare()
            player.playWhenReady = true
        }
    }
    val videoAlpha by animateFloatAsState(if (showVideo) 1f else 0f, tween(300), label = "previewFade")

    Box(modifier.background(brand.ink), contentAlignment = Alignment.Center) {
        Logo(ch, Modifier.fillMaxSize(0.55f), fallbackSize = 40)
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.preview_player, FrameLayout(ctx), false) as PlayerView)
                    .also { it.player = player }
            },
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = videoAlpha },
        )
        AnimatedVisibility(
            visible = showVideo,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) { LiveBadge() }
    }
}

@Composable
private fun LiveBadge() {
    val brand = LocalBrand.current
    Row(
        Modifier.clip(RoundedCornerShape(4.dp)).background(brand.liveRed).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(4.dp))
        Text("LIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
internal fun Logo(ch: ChannelEntity, modifier: Modifier, fallbackSize: Int = 16, focused: Boolean = false) {
    if (ch.tvgLogo.isNotBlank()) {
        AsyncImage(model = ch.tvgLogo, contentDescription = null, modifier = modifier)
    } else {
        // No logo: show the channel's initial so rows keep their shape.
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                ch.name.take(1).uppercase(), fontSize = fallbackSize.sp, fontWeight = FontWeight.Bold,
                // §3: no muted text on the focus gradient.
                color = if (focused) LocalBrand.current.textPrimary else LocalBrand.current.textMuted,
            )
        }
    }
}

@Composable
private fun Chip(text: String) {
    val brand = LocalBrand.current
    Text(
        text,
        fontSize = 12.sp,
        color = brand.textPrimary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(brand.tintChip)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

// failureMessage is raw exception text (it may even contain the link), so it only picks the copy, never shown.
private fun emptyChannelsMessage(playlist: PlaylistEntity): String = when {
    !playlist.lastUpdateSuccess && playlist.failureMessage.contains("no channels", ignoreCase = true) ->
        "This link didn't have any channels. Check the link and try again."
    !playlist.lastUpdateSuccess ->
        "Couldn't load this playlist, and there are no saved channels yet. Check your internet connection and the link, then open Playlists and choose Refresh now."
    playlist.lastUpdateTime == 0L ->
        "Getting your channels… If this takes more than a minute, check your internet connection."
    else -> "There are no channels to show in this playlist. Add another playlist to keep watching."
}

@Composable
private fun EmptyState(text: String, onAddPlaylist: () -> Unit) {
    val brand = LocalBrand.current
    val button = remember { FocusRequester() }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.ic_brand_mark), contentDescription = null, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            text, fontSize = 16.sp, lineHeight = 1.3.em, color = brand.textPrimary, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp),
        )
        Spacer(Modifier.height(24.dp))
        PillButton("Add playlist", onClick = onAddPlaylist, primary = true, modifier = Modifier.focusRequester(button))
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { button.requestFocus() }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 16.sp, color = LocalBrand.current.textMuted)
    }
}

@Composable
private fun KeyCap(key: String) {
    val brand = LocalBrand.current
    Text(
        key,
        // A fixed style rather than the inherited LocalTextStyle keeps caps the same height on every screen,
        // whatever text style the caller's subtree provides.
        style = KeyCapStyle,
        color = brand.textPrimary,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .wrapContentHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(brand.tintKey)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

// FlowRow so phones wrap the hints onto a second line instead of clipping them; on TV they fit on one line.
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
fun HintBar(vararg hints: Pair<String, String>, modifier: Modifier = Modifier) {
    val brand = LocalBrand.current
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        hints.forEach { (key, action) ->
            Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                KeyCap(key)
                Spacer(Modifier.width(6.dp))
                Text(action, fontSize = 13.sp, color = brand.textMuted, maxLines = 1)
            }
        }
    }
}
