package com.freetia.tv.ui.safety

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetia.tv.FreetiaApp
import com.freetia.tv.core.safety.PinCheck
import com.freetia.tv.ui.components.BrandDialog
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.theme.LocalBrand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class RecoveryMode { CODE, ANSWER }

/** Forgot PIN (§3.7): the recovery code or the security answer leads to setting a new PIN. */
@Composable
fun PinRecoveryScreen(nav: Navigator) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val toast = LocalBrandToast.current
    val brand = LocalBrand.current
    val hasPin by produceState<Boolean?>(null) { value = app.safety.pin.hasPin() }
    val question by produceState<String?>(null) { value = app.safety.pin.securityQuestion() }
    var mode by remember { mutableStateOf<RecoveryMode?>(null) }
    var resetting by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = "Forgot PIN",
        subtitle = "Use your recovery code or security question to set a new PIN.",
        hints = listOf("OK" to "Select", "↑↓" to "Move", "Back" to "Return"),
    ) {
        if (hasPin == false) {
            item {
                Text(
                    "No PIN is set, so there is nothing to recover.", fontSize = 16.sp, color = brand.textMuted,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            item {
                SettingActionRow(
                    "Enter recovery code", subtitle = "The code shown when the PIN was created",
                    onClick = { mode = RecoveryMode.CODE },
                )
            }
            question?.let { q ->
                item { SettingActionRow("Answer security question", subtitle = q, onClick = { mode = RecoveryMode.ANSWER }) }
            }
        }
    }

    mode?.let { m ->
        RecoveryInputDialog(
            mode = m,
            question = question.orEmpty(),
            onDismiss = { mode = null },
            onVerified = {
                mode = null
                resetting = true
            },
        )
    }
    if (resetting) {
        PinSetupDialog(
            title = "Set a new PIN",
            onDismiss = { resetting = false },
            onDone = {
                resetting = false
                toast.show("New PIN saved")
                nav.pop()
            },
        )
    }
}

@Composable
private fun RecoveryInputDialog(mode: RecoveryMode, question: String, onDismiss: () -> Unit, onVerified: () -> Unit) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val scope = rememberCoroutineScope()
    val brand = LocalBrand.current
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockedMs by remember { mutableLongStateOf(0L) }
    var checking by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { lockedMs = app.safety.pin.lockoutRemainingMs() }
    LaunchedEffect(lockedMs > 0) {
        while (lockedMs > 0) {
            delay(1_000)
            lockedMs = (lockedMs - 1_000).coerceAtLeast(0)
        }
    }

    fun submit() {
        if (checking || lockedMs > 0 || text.isBlank()) return
        checking = true
        val entered = text
        scope.launch {
            val result = if (mode == RecoveryMode.CODE) app.safety.pin.verifyRecoveryCode(entered)
            else app.safety.pin.verifyAnswer(entered)
            checking = false
            when (result) {
                PinCheck.Ok -> {
                    text = ""
                    onVerified()
                }
                is PinCheck.LockedOut -> lockedMs = result.remainingMs
                else -> error = checkMessage(result, if (mode == RecoveryMode.CODE) "code" else "answer")
            }
        }
    }

    BrandDialog(
        title = if (mode == RecoveryMode.CODE) "Enter your recovery code" else question,
        message = if (mode == RecoveryMode.CODE) "Dashes and capitals don't matter." else null,
        onDismiss = onDismiss,
        confirmLabel = "Check",
        onConfirm = ::submit,
        initialFocus = field,
        hints = listOf("OK" to "Type / confirm", "↑↓" to "Move", "Back" to "Cancel"),
    ) {
        SafetyTextField(
            label = if (mode == RecoveryMode.CODE) "Recovery code" else "Answer",
            value = text,
            onValueChange = { text = it.take(MAX_TEXT); error = null },
            modifier = Modifier.focusRequester(field),
            capitalization = if (mode == RecoveryMode.CODE) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
            onSubmit = ::submit,
        )
        val status = when {
            lockedMs > 0 -> lockoutText(lockedMs)
            checking -> "Checking…"
            else -> error
        }
        if (status != null) {
            Text(
                status, fontSize = 14.sp, color = if (checking) brand.textMuted else brand.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
