package com.freetia.tv.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freetia.tv.data.model.PlaylistEntity
import com.freetia.tv.ui.components.BrandDialog
import com.freetia.tv.ui.components.SettingActionRow

/** One line on what a playlist's last update did. A failed update keeps the saved channels (§3.2). */
fun playlistStatus(p: PlaylistEntity, now: Long = System.currentTimeMillis()): String = when {
    !p.lastUpdateSuccess -> "⚠ Update failed. Showing saved channels."
    p.lastUpdateTime == 0L -> "Not updated yet"
    else -> "Updated " + DateUtils.getRelativeTimeSpanString(p.lastUpdateTime, now, DateUtils.MINUTE_IN_MILLIS)
}

/**
 * The "Playlists" switcher (§3.1): pick a source, or the merged All view, without leaving the screen.
 * [activeId] is the playlist being shown (null while the merged view is).
 */
@Composable
fun PlaylistSwitcherDialog(
    playlists: List<PlaylistEntity>,
    activeId: Long?,
    showAll: Boolean,
    onPickAll: (() -> Unit)?,
    onPick: (PlaylistEntity) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BrandDialog(
        title = "Playlists",
        onDismiss = onDismiss,
        modifier = modifier,
        dismissLabel = "Close",
        hints = listOf("OK" to "Switch", "Back" to "Close"),
    ) {
        LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (playlists.size > 1 && onPickAll != null) {
                item(key = "all") {
                    SettingActionRow(
                        "All playlists", onPickAll, subtitle = "Every channel, in playlist priority order",
                        value = if (showAll) "Showing" else null, showChevron = false,
                    )
                }
            }
            items(playlists, key = { it.id }) { p ->
                SettingActionRow(
                    "${p.priority}. ${p.name}", { onPick(p) }, subtitle = playlistStatus(p),
                    value = if (!showAll && p.id == activeId) "Showing" else null, showChevron = false,
                )
            }
            item(key = "manage") { SettingActionRow("Manage playlists", onManage, subtitle = "Add, rename, reorder, refresh") }
        }
    }
}
