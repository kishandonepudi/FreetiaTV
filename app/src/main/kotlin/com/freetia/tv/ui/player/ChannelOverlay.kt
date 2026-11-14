package com.freetia.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetia.tv.data.database.ChannelRef
import com.freetia.tv.ui.HintBar
import com.freetia.tv.ui.components.FocusPill
import com.freetia.tv.ui.components.FocusSurface
import com.freetia.tv.ui.components.rememberScreenInsets
import com.freetia.tv.ui.theme.LocalBrand

/** OK in the player (§2): the channel list over the video, scrolled to the playing channel. Back is handled by the caller. */
@Composable
fun ChannelOverlay(
    channels: List<ChannelRef>,
    currentId: Long,
    onPick: (ChannelRef) -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalBrand.current
    val insets = rememberScreenInsets()
    val currentIndex = channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
    val currentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { currentFocus.requestFocus() } }
    Column(
        modifier
            .fillMaxHeight()
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .background(brand.scrimStrong)
            .padding(horizontal = insets.horizontal, vertical = insets.vertical),
    ) {
        FocusPill("Player options", onOptions, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(channels, key = { _, c -> c.id }) { index, ch ->
                val playing = ch.id == currentId
                FocusSurface(
                    onClick = { onPick(ch) },
                    shape = RoundedCornerShape(12.dp),
                    unfocusedColor = if (playing) brand.selectedTint else brand.tintSubtle,
                    modifier = Modifier.fillMaxWidth()
                        .semantics { if (playing) stateDescription = "Now playing" }
                        .then(if (index == currentIndex) Modifier.focusRequester(currentFocus) else Modifier),
                ) { focused ->
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${ch.number}", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = if (focused) brand.ink else brand.blue, modifier = Modifier.width(52.dp),
                        )
                        Text(
                            ch.name, fontSize = 16.sp, color = if (focused) brand.ink else brand.textPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HintBar("↑↓" to "Move", "OK" to "Watch", "Back" to "Close")
    }
}
