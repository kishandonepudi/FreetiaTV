package com.freetia.tv.ui.library

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.freetia.tv.ui.FormField
import com.freetia.tv.ui.components.BrandDialog

/** A one-line text prompt (rename a playlist, name a group). [onConfirm] gets a trimmed, non-blank name. */
@Composable
fun NameDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    maxLength: Int = 60,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    val submit = {
        val name = text.trim()
        if (name.isNotEmpty()) onConfirm(name)
    }
    BrandDialog(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = confirmLabel,
        onConfirm = { submit() },
        initialFocus = field,
    ) {
        FormField(
            label = label,
            value = text,
            onValueChange = { text = it.take(maxLength) },
            placeholder = label,
            isError = false,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.focusRequester(field),
        )
    }
}
