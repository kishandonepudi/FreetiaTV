package com.freetia.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.ui.HintBar
import com.freetia.tv.ui.components.rememberScreenInsets
import com.freetia.tv.ui.theme.LocalBrand

fun initialsOf(name: String): String =
    name.split(' ', '-', '_').filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString("")
        .ifBlank { "?" }

fun bufferLabel(bufferedSeconds: Int, buffering: Boolean): String =
    if (buffering) "Buffering…" else "Buffer ${bufferedSeconds}s"

data class BannerInfo(
    val channel: ChannelEntity,
    val now: String?,
    val next: String?,
    val resolution: String,
    val buffer: String,
)

@Composable
fun PlayerBanner(info: BannerInfo, modifier: Modifier = Modifier) {
    val brand = LocalBrand.current
    val insets = rememberScreenInsets()
    val channel = info.channel
    Column(
        modifier
            .fillMaxWidth()
            // Solid from 35% down so the meta line never sits on a bright, semi-transparent part of the scrim.
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to brand.scrimStrong, 1f to brand.scrimStrong))
            .padding(start = insets.horizontal, end = insets.horizontal, top = 56.dp, bottom = insets.vertical),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(channel)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${channel.number}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = brand.blue)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        channel.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = brand.textPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                info.now?.let {
                    Text("Now: $it", fontSize = 16.sp, color = brand.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                info.next?.let {
                    Text("Next: $it", fontSize = 14.sp, color = brand.textPrimary.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    listOfNotNull(
                        channel.groupTitle.substringBefore(';').trim().ifBlank { null },
                        info.resolution.ifBlank { null },
                        info.buffer,
                    ).joinToString("  •  "),
                    fontSize = 14.sp, color = brand.textPrimary.copy(alpha = 0.9f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        HintBar("↑↓" to "Channel", "0–9" to "Jump", "OK" to "Channels", "Menu" to "Options", "Back" to "Exit")
    }
}

@Composable
private fun ChannelLogo(channel: ChannelEntity) {
    val size = Modifier.size(64.dp, 40.dp)
    var failed by remember(channel.id) { mutableStateOf(false) }
    if (channel.tvgLogo.isBlank() || failed) {
        InitialsBadge(channel.name, size)
    } else {
        SubcomposeAsyncImage(
            model = channel.tvgLogo, contentDescription = null, modifier = size,
            loading = { InitialsBadge(channel.name, Modifier.fillMaxSize()) },
            error = { InitialsBadge(channel.name, Modifier.fillMaxSize()) },
            onError = { failed = true },
        )
    }
}

@Composable
fun InitialsBadge(name: String, modifier: Modifier = Modifier) {
    val brand = LocalBrand.current
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(brand.selectedTint),
        contentAlignment = Alignment.Center,
    ) {
        Text(initialsOf(name), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = brand.textPrimary)
    }
}
