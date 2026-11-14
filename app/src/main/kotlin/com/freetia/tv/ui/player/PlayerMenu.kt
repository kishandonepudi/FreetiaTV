package com.freetia.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetia.tv.data.settings.AspectRatioMode
import com.freetia.tv.ui.HintBar
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingChoiceRow
import com.freetia.tv.ui.components.SettingToggleRow
import com.freetia.tv.ui.components.rememberScreenInsets
import com.freetia.tv.ui.theme.LocalBrand

data class PlayerMenuModel(
    val audio: List<AudioOption>,
    val selectedAudio: AudioOption?,
    val captionsOn: Boolean,
    val heights: List<Int>,
    /** null = Auto. */
    val selectedHeight: Int?,
    val lockQuality: Boolean,
    val aspect: AspectRatioMode,
)

fun aspectLabel(mode: AspectRatioMode): String = when (mode) {
    AspectRatioMode.FIT -> "Fit"
    AspectRatioMode.FILL -> "Stretch"
    AspectRatioMode.ZOOM -> "Zoom"
    AspectRatioMode.RATIO_16_9 -> "16:9"
    AspectRatioMode.RATIO_4_3 -> "4:3"
}

/** Side panel opened with the Menu key or "Player options" in the channel list. Back is handled by the caller. */
@Composable
fun PlayerMenu(
    model: PlayerMenuModel,
    onAudio: (AudioOption) -> Unit,
    onCaptions: (Boolean) -> Unit,
    onResolution: (Int?) -> Unit,
    onLockQuality: (Boolean) -> Unit,
    onAspect: (AspectRatioMode) -> Unit,
    onSleepTimer: () -> Unit,
    modifier: Modifier = Modifier,
    /** Extra rows from other features (library: playlists, favourite, group, lock, adult mark). */
    extraRows: @Composable () -> Unit = {},
) {
    val brand = LocalBrand.current
    val insets = rememberScreenInsets()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(
        modifier
            .fillMaxHeight()
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .background(brand.scrimStrong)
            .padding(horizontal = insets.horizontal, vertical = insets.vertical),
    ) {
        Text("Player options", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = brand.textPrimary)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            item {
                val audioRow = Modifier.focusRequester(first)
                if (model.audio.size > 1) {
                    SettingChoiceRow(
                        "Audio language", model.audio, model.selectedAudio ?: model.audio.first(), onAudio,
                        modifier = audioRow, label = { it.label },
                    )
                } else {
                    SettingActionRow(
                        "Audio language", {}, modifier = audioRow,
                        value = model.audio.firstOrNull()?.label ?: "Stream default", showChevron = false,
                    )
                }
            }
            item { SettingToggleRow("Captions", model.captionsOn, onCaptions, subtitle = "Remembered for this channel") }
            item {
                val options: List<Int?> = listOf<Int?>(null) + model.heights
                SettingChoiceRow(
                    "Resolution", options, model.selectedHeight, onResolution,
                    label = { if (it == null) "Auto" else "${it}p" },
                )
            }
            item {
                SettingToggleRow(
                    "Lock quality", model.lockQuality, onLockQuality,
                    subtitle = "Stay at the current resolution",
                )
            }
            item {
                SettingChoiceRow("Aspect ratio", AspectRatioMode.entries, model.aspect, onAspect, label = ::aspectLabel)
            }
            item { SettingActionRow("Sleep timer", onSleepTimer, subtitle = "Stop playback after a set time") }
            item { extraRows() }
        }
        Spacer(Modifier.height(8.dp))
        HintBar("↑↓" to "Move", "OK" to "Select", "Back" to "Close")
    }
}
