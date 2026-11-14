package com.freetia.tv.ui.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.LockScope
import com.freetia.tv.ui.components.BrandChoiceDialog
import com.freetia.tv.ui.components.FocusPill
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingToggleRow
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.LocalAppSession
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.theme.LocalBrand
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Locale

private enum class LockTab(val label: String) { CHANNELS("Channels"), GROUPS("Groups"), REGIONS("Regions") }

private enum class ChannelAction { LOCK, UNLOCK, MARK_ADULT, UNMARK_ADULT }

/** Distinct segments of a ';'-joined attribute, as the lock queries match them. */
internal fun segments(values: Sequence<String>, upper: Boolean = false): List<String> =
    values.flatMap { it.split(';').asSequence() }
        .map { it.trim().let { s -> if (upper) s.uppercase(Locale.ROOT) else s } }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .sortedBy { it.lowercase(Locale.ROOT) }
        .toList()

/** Settings > Safety > Locked channels and categories (§3.8). Opening it needs the PIN. */
@Composable
fun LocksScreen(nav: Navigator) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val session = LocalAppSession.current
    val brand = LocalBrand.current
    val actions = rememberSafetyActions()
    var allowed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!allowed) actions.withPin("Enter your PIN to manage locks", onCancel = { nav.pop() }) { allowed = true }
    }
    if (!allowed) return

    val loaded by session.channels.collectAsState()
    val channels = loaded.orEmpty()
    val playlistId = session.activePlaylist.collectAsState().value?.id
    val lockedIds by remember(playlistId) {
        if (playlistId == null) flowOf(emptySet()) else app.safety.lockedIds(playlistId)
    }.collectAsState(emptySet())
    val categoryLocks by remember { app.safety.categoryLocks() }.collectAsState(emptyList())
    val lockedValues = remember(categoryLocks) {
        categoryLocks.map { it.scope to it.value.lowercase(Locale.ROOT) }.toSet()
    }
    val groups = remember(channels) { segments(channels.asSequence().map { it.groupTitle }) }
    val regions = remember(channels) { segments(channels.asSequence().map { it.tvgCountry }, upper = true) }
    var tab by rememberSaveable { mutableStateOf(LockTab.CHANNELS) }
    var picked by remember { mutableStateOf<ChannelEntity?>(null) }
    val scope = rememberCoroutineScope()

    SettingsScaffold(
        title = "Locked channels and categories",
        subtitle = "Locked channels need the PIN to watch and are skipped by channel up/down.",
        hints = listOf("OK" to "Change", "↑↓" to "Move", "Back" to "Return"),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                LockTab.entries.forEach { t -> FocusPill(t.label, onClick = { tab = t }, primary = t == tab) }
            }
        }
        val empty = when (tab) {
            LockTab.CHANNELS -> channels.isEmpty()
            LockTab.GROUPS -> groups.isEmpty()
            LockTab.REGIONS -> regions.isEmpty()
        }
        if (empty) {
            item {
                Text(
                    "Nothing to lock yet. Add a playlist first.", fontSize = 16.sp, color = brand.textMuted,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        when (tab) {
            LockTab.CHANNELS -> items(channels, key = { it.id }) { ch ->
                val state = listOfNotNull(
                    "Locked".takeIf { ch.id in lockedIds },
                    "Adult".takeIf { ch.isAdult || ch.isMarkedAdult },
                ).joinToString(" · ").ifEmpty { null }
                SettingActionRow(
                    "${ch.number}  ${ch.name}", value = state, showChevron = false,
                    onClick = { scope.launch { picked = app.database.channelDao().getById(ch.id) } },
                )
            }
            LockTab.GROUPS -> items(groups, key = { "g:$it" }) { g ->
                SettingToggleRow(
                    g, (LockScope.GROUP to g.lowercase(Locale.ROOT)) in lockedValues,
                    onCheckedChange = { on -> actions.setCategoryLocked(LockScope.GROUP, g, on) },
                )
            }
            LockTab.REGIONS -> items(regions, key = { "r:$it" }) { r ->
                SettingToggleRow(
                    r, (LockScope.REGION to r.lowercase(Locale.ROOT)) in lockedValues,
                    onCheckedChange = { on -> actions.setCategoryLocked(LockScope.REGION, r, on) },
                )
            }
        }
    }

    picked?.let { ch ->
        val options = listOf(
            if (ch.id in lockedIds) ChannelAction.UNLOCK else ChannelAction.LOCK,
            if (ch.isMarkedAdult) ChannelAction.UNMARK_ADULT else ChannelAction.MARK_ADULT,
        )
        BrandChoiceDialog(
            title = ch.name,
            options = options,
            selected = options.first(),
            onSelect = { action ->
                picked = null
                when (action) {
                    ChannelAction.LOCK -> actions.setChannelLocked(ch, true)
                    ChannelAction.UNLOCK -> actions.setChannelLocked(ch, false)
                    ChannelAction.MARK_ADULT -> actions.setMarkedAdult(ch, true)
                    ChannelAction.UNMARK_ADULT -> actions.setMarkedAdult(ch, false)
                }
            },
            onDismiss = { picked = null },
            label = {
                when (it) {
                    ChannelAction.LOCK -> "Lock channel"
                    ChannelAction.UNLOCK -> "Unlock channel"
                    ChannelAction.MARK_ADULT -> "Mark as adult"
                    ChannelAction.UNMARK_ADULT -> "Remove adult mark"
                }
            },
        )
    }
}
