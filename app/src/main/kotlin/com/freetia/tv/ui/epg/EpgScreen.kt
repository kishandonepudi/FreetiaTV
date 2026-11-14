package com.freetia.tv.ui.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetia.tv.FreetiaApp
import com.freetia.tv.core.safety.ChannelFilter
import com.freetia.tv.data.epg.CatchupUrl
import com.freetia.tv.data.epg.EpgRepository
import com.freetia.tv.data.epg.GridCell
import com.freetia.tv.data.epg.GridLayout
import com.freetia.tv.data.database.ChannelRef
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.EpgProgrammeEntity
import com.freetia.tv.data.repository.RegionFilter
import com.freetia.tv.ui.HintBar
import com.freetia.tv.ui.components.FocusPill
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.components.rememberScreenInsets
import com.freetia.tv.ui.nav.LocalAppSession
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.nav.Route
import com.freetia.tv.ui.safety.LocalLockedChannelIds
import com.freetia.tv.ui.safety.LockedIdsProvider
import com.freetia.tv.ui.safety.Padlock
import com.freetia.tv.ui.theme.LocalBrand
import com.freetia.tv.work.EpgRefreshWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Full-screen TV guide (§3.9): channels down the side, time across, search and reminders one key away. */
@Composable
fun EpgScreen(nav: Navigator) {
    var searching by rememberSaveable { mutableStateOf(false) }
    if (searching) {
        EpgSearchPanel(nav, onClose = { searching = false })
    } else {
        EpgGuide(nav, onSearch = { searching = true })
    }
}

private const val LOAD_BUCKET_MS = 3 * EpgFormat.HOUR

@Composable
private fun EpgGuide(nav: Navigator, onSearch: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as FreetiaApp
    val session = LocalAppSession.current
    val brand = LocalBrand.current
    val insets = rememberScreenInsets()
    val toast = LocalBrandToast.current
    val focusManager = LocalFocusManager.current
    val repo = remember { EpgRepository(app.database) }
    val reminders = rememberReminderUi()
    val scope = rememberCoroutineScope()

    val all by session.channels.collectAsState()
    val playlist by session.activePlaylist.collectAsState()
    val region by app.settings.selectedRegion.flow.collectAsState(initial = "")
    val childSafe by app.settings.childSafeMode.flow.collectAsState(initial = false)
    val hideDead by app.settings.hideDeadStreams.flow.collectAsState(initial = false)
    val epgDays by app.settings.epgDays.flow.collectAsState(initial = 7)

    val startNow = remember { System.currentTimeMillis() }
    val gridFrom = remember { EpgFormat.floorHalfHour(startNow - 12 * EpgFormat.HOUR) }
    val gridTo = remember(epgDays) { EpgFormat.floorHalfHour(startNow) + epgDays.coerceIn(1, 14) * EpgFormat.DAY }
    val now by produceState(startNow) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }

    val filtered = remember(all, region, childSafe, hideDead) {
        val rf = RegionFilter.decode(region)
        ChannelFilter.visible(all.orEmpty(), childSafe, hideDead).filter { rf.matches(it.tvgCountry, it.tvgLanguage) }
    }
    val guide by produceState<Map<Long, String>?>(null, filtered, playlist?.epgLastUpdateTime) {
        value = repo.guideIds(filtered.map { it.id })
    }
    val rows = remember(filtered, guide) { guide?.let { g -> filtered.filter { it.id in g } } }
    val hasSource by produceState(true, playlist) {
        val p = playlist
        value = p == null || app.playlistRepository.effectiveEpgUrl(p).isNotBlank() || app.settings.epgSourceUrl.get().isNotBlank()
    }

    var windowStart by rememberSaveable { mutableStateOf(EpgFormat.floorHalfHour(startNow) - EpgFormat.HALF_HOUR) }
    var selRow by rememberSaveable { mutableStateOf(0) }
    var selTime by rememberSaveable { mutableStateOf(startNow) }
    var programmes by remember { mutableStateOf<Map<String, List<EpgProgrammeEntity>>>(emptyMap()) }
    var dialog by remember { mutableStateOf<Pair<ChannelEntity, ProgrammeInfo>?>(null) }
    var gridFocused by remember { mutableStateOf(false) }
    val gridFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LockedIdsProvider(playlist?.id) {
        Column(
            Modifier
                .fillMaxSize()
                .background(brand.ink)
                .padding(horizontal = insets.horizontal, vertical = insets.vertical),
        ) {
            Text("TV guide", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = brand.textPrimary)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FocusPill("Search", onSearch)
                FocusPill("Now", {
                    windowStart = EpgFormat.floorHalfHour(System.currentTimeMillis()) - EpgFormat.HALF_HOUR
                    selTime = System.currentTimeMillis()
                    runCatching { gridFocus.requestFocus() }
                })
                FocusPill(EpgFormat.regionLabel(region), { nav.push(Route.RegionPicker) })
                FocusPill("Reminders", { nav.push(Route.Reminders) })
                FocusPill("Refresh guide", {
                    EpgRefreshWorker.enqueue(context, force = true)
                    toast.show("Updating the guide in the background. This can take a few minutes.")
                })
            }
            Spacer(Modifier.height(8.dp))

            val list = rows
            if (all == null || list == null) {
                Message("Loading the guide...")
            } else if (list.isEmpty()) {
                Message(
                    if (!hasSource) {
                        "This playlist has no guide address. Add one in Settings, under Playlists and EPG, then choose Refresh guide."
                    } else {
                        "No guide data yet for these channels. Choose Refresh guide, or check your region. The first update can take a few minutes."
                    },
                )
            } else {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val compact = maxWidth < 720.dp
                    val dpPerMin = if (compact) 3f else 4f
                    val labelWidth = if (compact) 88.dp else 150.dp
                    val rowHeight = if (compact) 60.dp else 68.dp
                    val spanMin = ((maxWidth - labelWidth).value / dpPerMin).toInt().coerceAtLeast(30)
                    val spanMs = spanMin * 60_000L
                    val maxStart = maxOf(gridFrom, gridTo - spanMs)

                    fun progsOf(row: Int): List<EpgProgrammeEntity> {
                        val ch = list.getOrNull(row) ?: return emptyList()
                        val id = guide?.get(ch.id) ?: return emptyList()
                        return programmes[id].orEmpty()
                    }

                    fun cellsOf(p: List<EpgProgrammeEntity>): List<GridCell> =
                        GridLayout.cells(LongArray(p.size) { p[it].start }, LongArray(p.size) { p[it].stop }, gridFrom, gridTo)

                    fun showTime(t: Long) {
                        if (t < windowStart) windowStart = EpgFormat.floorHalfHour(t).coerceIn(gridFrom, maxStart)
                        else if (t >= windowStart + spanMs) windowStart = (t - spanMs + EpgFormat.HALF_HOUR).coerceIn(gridFrom, maxStart)
                    }

                    fun showCell(c: GridCell) {
                        val end = windowStart + spanMs
                        if (c.start < windowStart) windowStart = c.start.coerceIn(gridFrom, maxStart)
                        else if (c.stop > end) windowStart = minOf(c.start, c.stop - spanMs).coerceIn(gridFrom, maxStart)
                    }

                    fun moveH(dir: Int) {
                        val cells = cellsOf(progsOf(selRow))
                        val idx = cells.indexOfFirst { selTime >= it.start && selTime < it.stop }
                        if (idx < 0) return
                        if (cells[idx].isGap) {
                            // Empty or not-yet-loaded stretch: step by half hours so time can always be scrolled.
                            val t = (selTime + dir * EpgFormat.HALF_HOUR).coerceIn(gridFrom, gridTo - 1)
                            selTime = t
                            showTime(t)
                        } else {
                            val target = cells.getOrNull(idx + dir) ?: return
                            selTime = target.start
                            showCell(target)
                        }
                    }

                    fun moveV(delta: Int) {
                        selTime = selTime.coerceIn(windowStart, windowStart + spanMs - 1)
                        selRow = (selRow + delta).coerceIn(0, list.lastIndex)
                    }

                    fun jump(deltaMs: Long) {
                        windowStart = (windowStart + deltaMs).coerceIn(gridFrom, maxStart)
                        selTime = selTime.coerceIn(windowStart, windowStart + spanMs - 1)
                    }

                    fun activate(row: Int, t: Long) {
                        val ch = list.getOrNull(row) ?: return
                        val nowMs = System.currentTimeMillis()
                        val p = progsOf(row).firstOrNull { t >= it.start && t < it.stop }
                        if (p == null) {
                            val gap = cellsOf(progsOf(row)).firstOrNull { t >= it.start && t < it.stop }
                            if (gap != null && nowMs >= gap.start && nowMs < gap.stop) nav.play(ch.id)
                            else toast.show("No guide information for this time.")
                        } else if (p.start <= nowMs && nowMs < p.stop) {
                            nav.play(ch.id)
                        } else {
                            val info = ProgrammeInfo(p.id, p.title, p.description, p.category, p.start, p.stop)
                            scope.launch { app.database.channelDao().getById(ch.id)?.let { dialog = it to info } }
                        }
                    }
                    // Rows hold this stable State instead of a fresh closure, so a D-pad step only recomposes the
                    // rows whose selection changed.
                    val onActivate = rememberUpdatedState<(Int, Long) -> Unit> { r, t -> activate(r, t) }

                    val first by remember { derivedStateOf { listState.firstVisibleItemIndex } }
                    val shown by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.size } }
                    val loadFrom = Math.floorDiv(windowStart, LOAD_BUCKET_MS) * LOAD_BUCKET_MS - LOAD_BUCKET_MS
                    val loadTo = Math.floorDiv(windowStart + spanMs, LOAD_BUCKET_MS) * LOAD_BUCKET_MS + 2 * LOAD_BUCKET_MS

                    LaunchedEffect(list, guide, first, shown, loadFrom, loadTo, selRow) {
                        val g = guide ?: return@LaunchedEffect
                        val lo = (first - 2).coerceAtLeast(0)
                        val hi = (first + shown + 2).coerceAtMost(list.lastIndex)
                        val ids = (lo..hi).mapNotNull { g[list[it].id] } + listOfNotNull(list.getOrNull(selRow)?.let { g[it.id] })
                        // Keep the old map when nothing changed, so rows keep the same list instances and skip.
                        val loaded = repo.window(ids, loadFrom, loadTo)
                        if (loaded != programmes) programmes = loaded
                    }
                    LaunchedEffect(selRow, list.size) {
                        selRow = selRow.coerceIn(0, list.lastIndex)
                        val lastIdx = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: selRow
                        if (selRow < first) listState.scrollToItem(selRow)
                        else if (selRow >= lastIdx && shown > 0) listState.scrollToItem((selRow - shown + 2).coerceAtLeast(0))
                    }
                    LaunchedEffect(Unit) { runCatching { gridFocus.requestFocus() } }

                    Column(Modifier.fillMaxSize()) {
                        TimeHeader(windowStart, spanMs, now, labelWidth, dpPerMin)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .focusRequester(gridFocus)
                                .onFocusChanged { gridFocused = it.hasFocus }
                                .onKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                    when (e.key) {
                                        Key.DirectionLeft -> { moveH(-1); true }
                                        Key.DirectionRight -> { moveH(1); true }
                                        Key.DirectionDown -> { moveV(1); true }
                                        Key.DirectionUp -> {
                                            if (selRow == 0) focusManager.moveFocus(FocusDirection.Up) else moveV(-1)
                                            true
                                        }
                                        Key.PageDown, Key.ChannelDown -> { moveV(5); true }
                                        Key.PageUp, Key.ChannelUp -> { moveV(-5); true }
                                        Key.MediaFastForward -> { jump(3 * EpgFormat.HOUR); true }
                                        Key.MediaRewind -> { jump(-3 * EpgFormat.HOUR); true }
                                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                            if (e.nativeKeyEvent.repeatCount == 0) activate(selRow, selTime)
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .focusable()
                                .semantics {
                                    contentDescription = "TV guide. Use the arrow keys to move between programmes."
                                    list.getOrNull(selRow)?.let { stateDescription = "Selected channel ${it.name}" }
                                }
                                .pointerInput(dpPerMin, gridFrom, maxStart) {
                                    detectHorizontalDragGestures { _, dx ->
                                        val minutes = dx / density / dpPerMin
                                        windowStart = (windowStart - (minutes * 60_000f).toLong()).coerceIn(gridFrom, maxStart)
                                    }
                                },
                        ) {
                            val locked = LocalLockedChannelIds.current
                            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                                itemsIndexed(list, key = { _, c -> c.id }) { index, channel ->
                                    GuideRow(
                                        channel = channel,
                                        locked = channel.id in locked,
                                        programmes = guide?.get(channel.id)?.let { programmes[it] },
                                        windowStart = windowStart,
                                        spanMs = spanMs,
                                        selTime = if (index == selRow) selTime else null,
                                        gridFocused = gridFocused,
                                        now = now,
                                        dpPerMin = dpPerMin,
                                        labelWidth = labelWidth,
                                        rowHeight = rowHeight,
                                        compact = compact,
                                        onTap = { cell ->
                                            selRow = index
                                            selTime = cell.start
                                            onActivate.value(index, cell.start)
                                        },
                                    )
                                }
                            }
                            if (now >= windowStart && now < windowStart + spanMs) {
                                Box(
                                    Modifier
                                        .offset(x = labelWidth + ((now - windowStart) / 60_000f * dpPerMin).dp)
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(brand.liveRed),
                                )
                            }
                        }
                        val selProg = progsOf(selRow).firstOrNull { selTime >= it.start && selTime < it.stop }
                        InfoStrip(list.getOrNull(selRow), selProg, now, compact)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            HintBar("←→" to "Time", "↑↓" to "Channel", "OK" to "Watch or remind", "Back" to "Return")
        }
    }

    dialog?.let { (channel, info) -> ProgrammeDialog(channel, info, nav, reminders, onDismiss = { dialog = null }) }
    reminders.Dialogs()
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Message(text: String) {
    Box(Modifier.weight(1f).fillMaxWidth().heightIn(min = 160.dp).padding(horizontal = 4.dp, vertical = 24.dp)) {
        Text(text, fontSize = 16.sp, lineHeight = 21.sp, color = LocalBrand.current.textMuted)
    }
}

@Composable
private fun TimeHeader(windowStart: Long, spanMs: Long, now: Long, labelWidth: Dp, dpPerMin: Float) {
    val brand = LocalBrand.current
    Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            EpgFormat.day(windowStart, now), fontSize = 13.sp, color = brand.textMuted, maxLines = 1,
            modifier = Modifier.width(labelWidth).padding(start = 4.dp),
        )
        Box(Modifier.weight(1f).clipToBounds()) {
            var t = -Math.floorDiv(-windowStart, EpgFormat.HALF_HOUR) * EpgFormat.HALF_HOUR
            while (t < windowStart + spanMs) {
                Text(
                    EpgFormat.time(t), fontSize = 13.sp, color = brand.textPrimary, maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = ((t - windowStart) / 60_000f * dpPerMin).dp + 4.dp),
                )
                t += EpgFormat.HALF_HOUR
            }
        }
    }
}

@Composable
private fun GuideRow(
    channel: ChannelRef,
    locked: Boolean,
    programmes: List<EpgProgrammeEntity>?,
    windowStart: Long,
    spanMs: Long,
    selTime: Long?,
    gridFocused: Boolean,
    now: Long,
    dpPerMin: Float,
    labelWidth: Dp,
    rowHeight: Dp,
    compact: Boolean,
    onTap: (GridCell) -> Unit,
) {
    val brand = LocalBrand.current
    val list = programmes.orEmpty()
    val cells = remember(programmes, windowStart, spanMs) {
        GridLayout.cells(
            LongArray(list.size) { list[it].start }, LongArray(list.size) { list[it].stop },
            windowStart, windowStart + spanMs,
        )
    }
    val hasCatchup = channel.catchup.isNotBlank() || channel.catchupEnabled
    Row(Modifier.fillMaxWidth().height(rowHeight)) {
        Row(
            Modifier.width(labelWidth).fillMaxHeight().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                channel.name, fontSize = if (compact) 13.sp else 14.sp, color = brand.textPrimary, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            if (locked) Padlock(false, Modifier.padding(start = 4.dp))
        }
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            cells.forEach { cell ->
                val p = list.getOrNull(cell.programmeIndex)
                val selected = selTime != null && selTime >= cell.start && selTime < cell.stop
                val hot = selected && gridFocused
                val airing = p != null && p.start <= now && now < p.stop
                val past = p != null && p.stop <= now
                val shape = RoundedCornerShape(8.dp)
                val fill = when {
                    hot -> Color.White
                    airing -> brand.selectedTint
                    p == null -> brand.tintSubtle
                    else -> brand.tintChip
                }
                val titleColor = when {
                    hot -> brand.ink
                    past || p == null -> brand.textMuted
                    else -> brand.textPrimary
                }
                val subColor = if (hot) brand.inkMuted else brand.textMuted
                val widthDp = ((cell.stop - cell.start) / 60_000f * dpPerMin).dp
                Box(
                    Modifier
                        .offset(x = ((cell.start - windowStart) / 60_000f * dpPerMin).dp)
                        .width(widthDp)
                        .fillMaxHeight()
                        .padding(1.5.dp)
                        .background(fill, shape)
                        .then(
                            if (selected) Modifier.border(brand.focusBorderWidth, if (gridFocused) Color.White else brand.blue, shape)
                            else Modifier,
                        )
                        .pointerInput(cell) { detectTapGestures { onTap(cell) } }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    if (widthDp > 40.dp) {
                        Column(Modifier.align(Alignment.CenterStart)) {
                            Text(
                                p?.title ?: "No information", fontSize = if (compact) 14.sp else 16.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, color = titleColor, fontWeight = FontWeight.Medium,
                            )
                            val sub = when {
                                p == null -> null
                                past && hasCatchup &&
                                    CatchupUrl.isAvailable(channel.catchup, channel.catchupEnabled, channel.catchupDays, p.start, now) ->
                                    "Watch from start"
                                else -> EpgFormat.time(p.start)
                            }
                            if (sub != null && widthDp > 80.dp) {
                                Text(sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = subColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoStrip(channel: ChannelRef?, programme: EpgProgrammeEntity?, now: Long, compact: Boolean) {
    val brand = LocalBrand.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(brand.panel, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .heightIn(min = if (compact) 48.dp else 64.dp),
    ) {
        if (programme == null || channel == null) {
            Text("Choose a programme to see what is on.", fontSize = 14.sp, color = brand.textMuted)
        } else {
            Text(
                programme.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = brand.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${channel.name}  |  ${EpgFormat.range(programme.start, programme.stop, now)}" +
                    if (programme.category.isNotBlank()) "  |  ${programme.category}" else "",
                fontSize = 13.sp, color = brand.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!compact && programme.description.isNotBlank()) {
                Text(programme.description, fontSize = 13.sp, color = brand.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
