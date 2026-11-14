package com.freetia.tv.ui.epg

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.database.EpgSearchRow
import com.freetia.tv.data.epg.EpgRepository
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.repository.RegionFilter
import com.freetia.tv.ui.FormField
import com.freetia.tv.ui.components.FocusPill
import com.freetia.tv.ui.components.FocusSurface
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.LocalAppSession
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.theme.LocalBrand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Guide search (§3.9) by programme title, channel name or genre, with a region filter. Child Safe Mode applies. */
@Composable
fun EpgSearchPanel(nav: Navigator, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as FreetiaApp
    val session = LocalAppSession.current
    val brand = LocalBrand.current
    val toast = LocalBrandToast.current
    val scope = rememberCoroutineScope()
    val repo = remember { EpgRepository(app.database) }
    val reminders = rememberReminderUi()

    val playlist by session.activePlaylist.collectAsState()
    val region by app.settings.selectedRegion.flow.collectAsState(initial = "")
    val childSafe by app.settings.childSafeMode.flow.collectAsState(initial = false)
    val hideDead by app.settings.hideDeadStreams.flow.collectAsState(initial = false)

    var query by rememberSaveable { mutableStateOf("") }
    var allRegions by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<EpgSearchRow>?>(null) }
    var dialog by remember { mutableStateOf<Pair<ChannelEntity, ProgrammeInfo>?>(null) }
    val now = remember(results) { System.currentTimeMillis() }

    BackHandler { onClose() }

    LaunchedEffect(query, allRegions, region, childSafe, hideDead, playlist?.id) {
        if (query.trim().length < 2) {
            results = null
            return@LaunchedEffect
        }
        delay(350)
        results = repo.search(
            query = query,
            now = System.currentTimeMillis(),
            playlistId = playlist?.id ?: 0L,
            childSafe = childSafe,
            hideDead = hideDead,
            region = if (allRegions) RegionFilter() else RegionFilter.decode(region),
        )
    }

    SettingsScaffold(
        title = "Search the guide",
        subtitle = "Programme, channel or genre",
        hints = listOf("OK" to "Open", "↑↓" to "Move", "Back" to "Return to guide"),
    ) {
        item(key = "field") {
            FormField(
                label = "Search",
                value = query,
                onValueChange = { query = it.take(60) },
                placeholder = "For example news, cricket or a channel name",
                isError = false,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "region") {
            Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val regionLabel = EpgFormat.regionLabel(region)
                FocusPill(
                    if (allRegions || regionLabel == "All regions") "Searching all regions" else "Region: $regionLabel",
                    { allRegions = !allRegions },
                    primary = !allRegions,
                )
                FocusPill("Back to guide", onClose)
            }
        }
        val list = results
        when {
            list == null -> item(key = "hint") {
                Text(
                    "Type at least two letters.", fontSize = 14.sp, color = brand.textMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            list.isEmpty() -> item(key = "none") {
                Text(
                    "Nothing matches. Try a shorter word, or search all regions.", fontSize = 14.sp, color = brand.textMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            else -> items(list, key = { it.programmeId }) { row ->
                FocusSurface(
                    onClick = {
                        scope.launch {
                            val channel = app.database.channelDao().getById(row.channelId)
                            if (channel == null) {
                                toast.show("That channel is no longer in your playlist.", true)
                            } else if (row.start <= System.currentTimeMillis()) {
                                nav.play(channel.id)
                            } else {
                                dialog = channel to ProgrammeInfo(row.programmeId, row.title, row.description, row.category, row.start, row.stop)
                            }
                        }
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().widthIn(min = 200.dp),
                ) { focused ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (row.start <= now) {
                                Text("NOW", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (focused) brand.ink else brand.liveRed)
                            }
                            Text(
                                row.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, color = if (focused) brand.ink else brand.textPrimary,
                            )
                        }
                        Text(
                            "${row.channelName}  |  ${EpgFormat.range(row.start, row.stop, now)}" +
                                if (row.category.isNotBlank()) "  |  ${row.category}" else "",
                            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (focused) brand.inkMuted else brand.textMuted,
                        )
                    }
                }
            }
        }
    }

    dialog?.let { (channel, info) -> ProgrammeDialog(channel, info, nav, reminders, onDismiss = { dialog = null }) }
    reminders.Dialogs()
}
