package com.freetia.tv.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetia.tv.ui.theme.LocalBrand

private val Keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", DEL, "0", DONE)
private const val DEL = "⌫"
private const val DONE = "OK"

/**
 * TV-sized number pad for PINs and channel numbers (§3.8). D-pad moves between keys; the remote's number keys and
 * Delete work too, wherever focus is on the pad. Keys are 72dp on TV and 64dp on phones (both above the 48dp
 * minimum target).
 */
@Composable
fun NumberPad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    doneLabel: String = DONE,
    doneEnabled: Boolean = true,
    autoFocus: Boolean = true,
    keySize: Dp = if (rememberScreenInsets().isCompact) 64.dp else 72.dp,
) {
    val first = remember { FocusRequester() }
    Column(
        modifier.onPreviewKeyEvent {
            val code = it.nativeKeyEvent.keyCode
            val handled = code in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 ||
                code in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9 ||
                code == AndroidKeyEvent.KEYCODE_DEL
            if (handled && it.type == KeyEventType.KeyDown) {
                when (code) {
                    AndroidKeyEvent.KEYCODE_DEL -> onBackspace()
                    in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> onDigit(code - AndroidKeyEvent.KEYCODE_0)
                    else -> onDigit(code - AndroidKeyEvent.KEYCODE_NUMPAD_0)
                }
            }
            handled
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    val label = if (key == DONE) doneLabel else key
                    PadKey(
                        label = label,
                        description = when (key) { DEL -> "Delete"; DONE -> doneLabel; else -> key },
                        size = keySize,
                        enabled = key != DONE || doneEnabled,
                        primary = key == DONE,
                        modifier = if (key == "1") Modifier.focusRequester(first) else Modifier,
                        onClick = {
                            when (key) {
                                DEL -> onBackspace()
                                DONE -> onDone()
                                else -> onDigit(key.toInt())
                            }
                        },
                    )
                }
            }
        }
    }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun PadKey(
    label: String,
    description: String,
    size: Dp,
    enabled: Boolean,
    primary: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val brand = LocalBrand.current
    FocusSurface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        unfocusedColor = if (primary) brand.selectedTint else brand.tintChip,
        modifier = modifier.size(size).semantics { contentDescription = description },
    ) { focused ->
        Text(
            label, fontSize = if (label.length > 2) 18.sp else 26.sp, fontWeight = FontWeight.SemiBold,
            color = when {
                focused -> brand.ink
                enabled -> brand.textPrimary
                else -> brand.textMuted
            },
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** Masked PIN progress: [length] dots, the first [filled] solid. */
@Composable
fun PinDots(length: Int, filled: Int, modifier: Modifier = Modifier, error: Boolean = false) {
    val brand = LocalBrand.current
    val color = if (error) brand.error else brand.textPrimary
    Row(
        modifier.semantics { contentDescription = "$filled of $length digits entered" },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(length) { i ->
            Box(
                Modifier
                    .size(18.dp)
                    .then(
                        if (i < filled) Modifier.background(color, CircleShape)
                        else Modifier.border(2.dp, color, CircleShape)
                    )
            )
        }
    }
}
