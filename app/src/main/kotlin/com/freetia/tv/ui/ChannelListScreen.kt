package com.freetia.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.PlaylistEntity

@Composable
fun ChannelListScreen(
    playlist: PlaylistEntity?,
    playlistsLoaded: Boolean,
    channels: List<ChannelEntity>,
    onPlay: (ChannelEntity) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Text(
            playlist?.name ?: "Freetia TV",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (playlist != null && !playlist.lastUpdateSuccess) {
            Text("⚠ Update failed — showing saved channels", fontSize = 20.sp, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))

        Box(Modifier.weight(1f)) {
            when {
                !playlistsLoaded -> Message("Loading…")
                playlist == null -> Message("No playlists yet. Add an M3U playlist URL in Settings.")
                channels.isEmpty() -> Message(
                    if (playlist.lastUpdateTime == 0L) "Downloading channels…"
                    else "No channels in this playlist."
                )
                else -> ChannelList(channels, onPlay)
            }
        }
        HintBar("OK: Watch    ↑↓: Browse    Back ×2: Exit")
    }
}

@Composable
private fun ChannelList(channels: List<ChannelEntity>, onPlay: (ChannelEntity) -> Unit) {
    val first = remember { FocusRequester() }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(channels, key = { it.id }) { ch ->
            ChannelRow(
                ch,
                onClick = { onPlay(ch) },
                modifier = if (ch.id == channels.first().id) Modifier.focusRequester(first) else Modifier,
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun ChannelRow(ch: ChannelEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = shape,
        color = if (focused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .border(if (focused) 4.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
            .semantics { contentDescription = "Channel ${ch.number}, ${ch.name}" },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                ch.number.toString(),
                fontSize = 24.sp,
                modifier = Modifier.width(72.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            AsyncImage(
                model = ch.tvgLogo.ifBlank { null },
                contentDescription = null,
                modifier = Modifier.size(width = 72.dp, height = 48.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(ch.name, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                if (ch.groupTitle.isNotBlank()) {
                    Text(ch.groupTitle, fontSize = 18.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun HintBar(text: String) {
    Text(
        text,
        fontSize = 20.sp,
        color = Color(0xFFDDDDDD),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
