package com.freetia.tv.ui.settings

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.settings.Pref
import com.freetia.tv.ui.FormField
import com.freetia.tv.ui.components.BrandDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** App, settings and a scope for fire-and-forget writes from a settings row. */
internal class SettingsCtx(val app: FreetiaApp, private val scope: CoroutineScope) {
    val s get() = app.settings
    fun <T> set(pref: Pref<T>, value: T) { scope.launch { pref.set(value) } }
    fun launch(block: suspend () -> Unit) { scope.launch { block() } }
}

@Composable
internal fun rememberSettingsCtx(): SettingsCtx {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val scope = rememberCoroutineScope()
    return remember(app, scope) { SettingsCtx(app, scope) }
}

@Composable
internal fun <T> Pref<T>.collect(): T = flow.collectAsState(initial = default).value

/** Spoken description for a row: TalkBack reads "title. subtitle" plus the row's own state. */
internal fun Modifier.describe(title: String, subtitle: String? = null): Modifier =
    semantics { contentDescription = listOfNotNull(title, subtitle).joinToString(". ") }

/** A one-line text prompt that, unlike NameDialog, may be confirmed blank and can show an error. */
@Composable
internal fun TextPromptDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    maxLength: Int = 200,
    allowBlank: Boolean = false,
    password: Boolean = false,
    validate: (String) -> String? = { null },
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val field = remember { FocusRequester() }
    val submit = {
        val value = text.trim()
        val problem = if (value.isEmpty() && !allowBlank) "Type something first." else if (value.isEmpty()) null else validate(value)
        if (problem != null) error = problem else onConfirm(value)
    }
    BrandDialog(
        title = title,
        message = error ?: message,
        onDismiss = onDismiss,
        confirmLabel = confirmLabel,
        onConfirm = { submit() },
        initialFocus = field,
    ) {
        FormField(
            label = label,
            value = text,
            onValueChange = { text = it.take(maxLength); error = null },
            placeholder = label,
            isError = error != null,
            keyboardOptions = if (password) KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
            else KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.focusRequester(field),
        )
    }
}
