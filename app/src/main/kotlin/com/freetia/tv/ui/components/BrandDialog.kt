package com.freetia.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.freetia.tv.ui.HintBar
import com.freetia.tv.ui.theme.LocalBrand

/**
 * Brand modal dialog. Back or Cancel calls [onDismiss]. With [onConfirm] set, the confirm pill is focused first;
 * otherwise focus goes to the first focusable inside [content] (or the dismiss pill).
 */
@Composable
fun BrandDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    confirmLabel: String = "OK",
    onConfirm: (() -> Unit)? = null,
    dismissLabel: String? = "Cancel",
    destructive: Boolean = false,
    /** Focused when the dialog opens; overrides the default (confirm pill, else first item in content). */
    initialFocus: FocusRequester? = null,
    hints: List<Pair<String, String>> = listOf("OK" to "Select", "Back" to "Cancel"),
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val brand = LocalBrand.current
    val confirmFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier
                .padding(16.dp)
                .widthIn(min = 320.dp, max = 600.dp)
                .background(brand.surface, RoundedCornerShape(20.dp))
                .semantics { paneTitle = title }
                .padding(24.dp)
        ) {
            Text(
                title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                color = if (destructive) brand.error else brand.textPrimary,
            )
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(message, fontSize = 16.sp, color = brand.textMuted, lineHeight = 21.sp)
            }
            if (content != null) {
                Spacer(Modifier.height(16.dp))
                Column(Modifier.focusRequester(bodyFocus)) { content() }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onConfirm != null) {
                    FocusPill(confirmLabel, onConfirm, primary = true, modifier = Modifier.focusRequester(confirmFocus))
                }
                if (dismissLabel != null) {
                    FocusPill(
                        dismissLabel, onDismiss,
                        modifier = if (onConfirm == null && content == null) Modifier.focusRequester(confirmFocus) else Modifier,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            HintBar(*hints.toTypedArray())
        }
        LaunchedEffect(Unit) {
            runCatching {
                when {
                    initialFocus != null -> initialFocus.requestFocus()
                    onConfirm == null && content != null -> bodyFocus.requestFocus()
                    else -> confirmFocus.requestFocus()
                }
            }
        }
    }
}

/** Pick one of [options]; the current choice is focused when the dialog opens. */
@Composable
fun <T> BrandChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    label: (T) -> String = { it.toString() },
) {
    val brand = LocalBrand.current
    val selectedFocus = remember { FocusRequester() }
    BrandDialog(
        title = title, onDismiss = onDismiss, initialFocus = selectedFocus.takeIf { selected in options },
        hints = listOf("OK" to "Choose", "Back" to "Cancel"),
    ) {
        LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(options) { _, option ->
                val isSelected = option == selected
                FocusSurface(
                    onClick = { onSelect(option) },
                    shape = RoundedCornerShape(12.dp),
                    unfocusedColor = if (isSelected) brand.selectedTint else brand.tintSubtle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .then(if (isSelected) Modifier.focusRequester(selectedFocus) else Modifier),
                ) { focused ->
                    Text(
                        (if (isSelected) "✓  " else "") + label(option), fontSize = 16.sp,
                        color = if (focused) brand.ink else brand.textPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
