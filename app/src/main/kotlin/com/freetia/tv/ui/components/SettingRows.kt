package com.freetia.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetia.tv.ui.theme.LocalBrand

private val RowShape = RoundedCornerShape(14.dp)

/** Shared row chrome: white focus pill, title + optional subtitle on the left, [trailing] on the right. */
@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    role: Role = Role.Button,
    trailing: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    val brand = LocalBrand.current
    FocusSurface(
        onClick = onClick,
        enabled = enabled,
        shape = RowShape,
        unfocusedColor = brand.tintSubtle,
        role = role,
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
    ) { focused ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2,
                    color = when {
                        focused -> brand.ink
                        !enabled -> brand.textMuted
                        destructive -> brand.error
                        else -> brand.textPrimary
                    },
                )
                if (subtitle != null) {
                    Text(
                        subtitle, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (focused) brand.inkMuted else brand.textMuted,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            trailing(focused)
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val brand = LocalBrand.current
    SettingRow(
        title, subtitle, onClick = { onCheckedChange(!checked) }, enabled = enabled, role = Role.Switch,
        modifier = modifier.semantics { stateDescription = if (checked) "On" else "Off" },
    ) { focused ->
        Text(
            if (checked) "On" else "Off", fontSize = 14.sp,
            color = if (focused) brand.inkMuted else brand.textMuted,
        )
        Spacer(Modifier.width(10.dp))
        val track = when {
            checked -> brand.blue
            focused -> brand.inkMuted
            else -> brand.tintTrack
        }
        Box(
            Modifier.size(width = 44.dp, height = 24.dp).background(track, CircleShape).padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(18.dp).background(Color.White, CircleShape))
        }
    }
}

/**
 * A setting with a fixed list of options. Left/Right cycles in place; OK opens a choice dialog, which is also the
 * touch path on phones.
 */
@Composable
fun <T> SettingChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.toString() },
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val brand = LocalBrand.current
    var dialog by remember { mutableStateOf(false) }
    val index = options.indexOf(selected).coerceAtLeast(0)
    SettingRow(
        title, subtitle, onClick = { dialog = true }, enabled = enabled,
        modifier = modifier
            .semantics { stateDescription = label(selected) }
            .onPreviewKeyEvent {
                if (!enabled || options.isEmpty()) return@onPreviewKeyEvent false
                val delta = when (it.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onPreviewKeyEvent false
                }
                if (it.type == KeyEventType.KeyDown) onSelect(options[(index + delta).mod(options.size)])
                true
            },
    ) { focused ->
        Text(
            if (focused) "‹  ${label(selected)}  ›" else label(selected), fontSize = 15.sp, maxLines = 1,
            fontWeight = FontWeight.Medium, color = if (focused) brand.ink else brand.textMuted,
        )
    }
    if (dialog) {
        BrandChoiceDialog(
            title = title, options = options, selected = selected, label = label,
            onSelect = { onSelect(it); dialog = false }, onDismiss = { dialog = false },
        )
    }
}

/** A number in [range] moved by [step] with Left/Right (or the − / + touch targets on phones). */
@Composable
fun SettingStepperRow(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val brand = LocalBrand.current
    fun move(delta: Int) {
        val next = (value + delta * step).coerceIn(range.first, range.last)
        if (next != value) onValueChange(next)
    }
    SettingRow(
        title, subtitle, onClick = { move(+1) }, enabled = enabled,
        modifier = modifier
            .semantics { stateDescription = format(value) }
            .onPreviewKeyEvent {
                if (!enabled) return@onPreviewKeyEvent false
                val delta = when (it.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onPreviewKeyEvent false
                }
                if (it.type == KeyEventType.KeyDown) move(delta)
                true
            },
    ) { focused ->
        val color = if (focused) brand.ink else brand.textPrimary
        StepKey("−", "Decrease", color, enabled) { move(-1) }
        Text(
            format(value), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = color, maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        StepKey("+", "Increase", color, enabled) { move(+1) }
    }
}

/** Touch-only target: never focusable, so D-pad focus stays on the row and Left/Right do the stepping. */
@Composable
private fun StepKey(symbol: String, description: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .focusProperties { canFocus = false }
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** Opens something or runs an action. [value] shows the current state on the right, e.g. "3 playlists". */
@Composable
fun SettingActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    showChevron: Boolean = true,
) {
    val brand = LocalBrand.current
    SettingRow(title, subtitle, onClick, modifier, enabled = enabled, destructive = destructive) { focused ->
        if (value != null) {
            Text(value, fontSize = 15.sp, maxLines = 1, color = if (focused) brand.inkMuted else brand.textMuted)
        }
        if (showChevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = if (focused) brand.ink else brand.textMuted, modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Gap between groups of rows inside a [SettingsScaffold]. */
@Composable
fun SettingSpacer() = Spacer(Modifier.height(8.dp))
