package com.freetia.tv.ui.safety

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.freetia.tv.FreetiaApp
import com.freetia.tv.core.safety.PinCheck
import com.freetia.tv.core.safety.PinManager
import com.freetia.tv.ui.components.BrandDialog
import com.freetia.tv.ui.components.FocusPill
import com.freetia.tv.ui.components.NumberPad
import com.freetia.tv.ui.components.PinDots
import com.freetia.tv.ui.components.SettingChoiceRow
import com.freetia.tv.ui.theme.LocalBrand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PinHints = listOf("0–9" to "Enter PIN", "OK" to "Confirm", "Back" to "Cancel")

internal val SecurityQuestions = listOf(
    "What city were you born in?",
    "What was the name of your first school?",
    "What was your first pet's name?",
    "What was your childhood nickname?",
    "What is your favourite food?",
)

internal fun lockoutText(ms: Long): String = "Too many tries. Try again in ${(ms + 999) / 1000} s."

internal enum class PinEntryOutcome { PASS, SETUP, LOCKED_OUT, WRONG }

/** NotSet never passes a gate: with no PIN stored, any digits would open it, so the user creates a PIN instead. */
internal fun pinEntryOutcome(result: PinCheck): PinEntryOutcome = when (result) {
    PinCheck.Ok -> PinEntryOutcome.PASS
    PinCheck.NotSet -> PinEntryOutcome.SETUP
    is PinCheck.LockedOut -> PinEntryOutcome.LOCKED_OUT
    is PinCheck.Wrong -> PinEntryOutcome.WRONG
}

internal fun checkMessage(result: PinCheck, what: String): String? = when (result) {
    PinCheck.Ok -> null
    is PinCheck.Wrong -> "Wrong $what. ${result.attemptsLeft} ${if (result.attemptsLeft == 1) "try" else "tries"} left."
    is PinCheck.LockedOut -> lockoutText(result.remainingMs)
    PinCheck.NotSet -> "Nothing is saved to check against."
}

/** Counts a lockout down once a second; [ms] 0 = not locked. */
@Composable
private fun LockoutTicker(ms: Long, onTick: (Long) -> Unit) {
    LaunchedEffect(ms > 0) {
        var left = ms
        while (left > 0) {
            delay(1_000)
            left = (left - 1_000).coerceAtLeast(0)
            onTick(left)
        }
    }
}

/** Shared PIN pad body: dots, status line and the TV number pad. */
@Composable
private fun PinPad(
    digits: String,
    status: String?,
    isError: Boolean,
    enabled: Boolean,
    onDigits: (String) -> Unit,
    onDone: () -> Unit,
) {
    val brand = LocalBrand.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PinDots(length = maxOf(PinManager.MIN_LENGTH, digits.length), filled = digits.length, error = isError)
        Spacer(Modifier.height(10.dp))
        Text(
            status ?: "Enter 4 to 6 digits", fontSize = 14.sp,
            color = if (isError) brand.error else brand.textMuted,
        )
        Spacer(Modifier.height(14.dp))
        NumberPad(
            onDigit = { if (enabled && digits.length < PinManager.MAX_LENGTH) onDigits(digits + it) },
            onBackspace = { onDigits(digits.dropLast(1)) },
            onDone = onDone,
            doneEnabled = enabled && digits.length >= PinManager.MIN_LENGTH,
        )
    }
}

/**
 * Asks for the PIN. Wrong tries and lockouts are counted by [PinManager] (persisted), never here.
 * [onForgot] adds a "Forgot PIN?" pill that leads to recovery.
 */
@Composable
fun PinEntryDialog(
    title: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    message: String? = null,
    onForgot: (() -> Unit)? = null,
) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val scope = rememberCoroutineScope()
    var digits by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockedMs by remember { mutableLongStateOf(0L) }
    var checking by remember { mutableStateOf(false) }
    var needsSetup by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        lockedMs = app.safety.pin.lockoutRemainingMs()
        if (!app.safety.pin.hasPin()) needsSetup = true
    }
    LockoutTicker(lockedMs) { lockedMs = it }

    if (needsSetup) {
        PinSetupDialog(onDismiss = onDismiss, onDone = onSuccess)
        return
    }

    fun submit() {
        if (checking || digits.length < PinManager.MIN_LENGTH) return
        checking = true
        val entered = digits
        digits = ""
        scope.launch {
            val result = app.safety.pin.verifyPin(entered)
            checking = false
            when (pinEntryOutcome(result)) {
                PinEntryOutcome.PASS -> onSuccess()
                PinEntryOutcome.SETUP -> needsSetup = true
                PinEntryOutcome.LOCKED_OUT -> lockedMs = (result as? PinCheck.LockedOut)?.remainingMs ?: 0L
                PinEntryOutcome.WRONG -> error = checkMessage(result, "PIN")
            }
        }
    }

    BrandDialog(title = title, message = message, onDismiss = onDismiss, hints = PinHints) {
        PinPad(
            digits = digits,
            status = if (lockedMs > 0) lockoutText(lockedMs) else if (checking) "Checking…" else error,
            isError = lockedMs > 0 || error != null,
            enabled = lockedMs == 0L && !checking,
            onDigits = { digits = it; error = null },
            onDone = ::submit,
        )
        if (onForgot != null) {
            Spacer(Modifier.height(12.dp))
            FocusPill("Forgot PIN?", onClick = { onDismiss(); onForgot() }, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

private enum class SetupStep { NEW, CONFIRM, CODE, QUESTION }

/**
 * Creates or replaces the PIN: enter, confirm, then (with [issueRecoveryCode]) show the recovery code once and offer
 * a security question. Once the PIN is saved, any way out of the later steps counts as [onDone].
 */
@Composable
fun PinSetupDialog(
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    title: String = "Create a PIN",
    issueRecoveryCode: Boolean = true,
) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(SetupStep.NEW) }
    var first by remember { mutableStateOf("") }
    var digits by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<String?>(null) }

    when (step) {
        SetupStep.NEW, SetupStep.CONFIRM -> BrandDialog(
            title = if (step == SetupStep.NEW) title else "Enter the same PIN again",
            message = if (step == SetupStep.NEW) "You'll need it to turn off Child Safe Mode, change safety settings " +
                "and watch locked channels." else null,
            onDismiss = onDismiss,
            hints = PinHints,
        ) {
            PinPad(
                digits = digits,
                status = if (saving) "Saving…" else error,
                isError = error != null,
                enabled = !saving,
                onDigits = { digits = it; error = null },
                onDone = {
                    if (step == SetupStep.NEW) {
                        first = digits
                        digits = ""
                        step = SetupStep.CONFIRM
                    } else if (digits != first) {
                        digits = ""
                        first = ""
                        error = "The PINs didn't match. Enter a new PIN."
                        step = SetupStep.NEW
                    } else if (!saving) {
                        saving = true
                        scope.launch {
                            code = app.safety.pin.setPin(first, issueRecoveryCode)
                            first = ""
                            digits = ""
                            saving = false
                            if (code != null) step = SetupStep.CODE else onDone()
                        }
                    }
                },
            )
        }
        SetupStep.CODE -> RecoveryCodeDialog(code.orEmpty(), onDone = { code = null; step = SetupStep.QUESTION })
        SetupStep.QUESTION -> SecurityQuestionDialog(optional = true, onDone = onDone, onDismiss = onDone)
    }
}

/** Shows a freshly issued recovery code. It is stored only as a hash, so this is the one chance to see it. */
@Composable
fun RecoveryCodeDialog(code: String, onDone: () -> Unit) {
    val brand = LocalBrand.current
    BrandDialog(
        title = "Write down your recovery code",
        message = "If you forget your PIN, this code lets you set a new one. It won't be shown again.",
        onDismiss = onDone,
        confirmLabel = "I wrote it down",
        onConfirm = onDone,
        dismissLabel = null,
        hints = listOf("OK" to "Continue"),
    ) {
        Text(
            code, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
            letterSpacing = 0.1.em, color = brand.textPrimary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .semantics { contentDescription = "Recovery code ${code.toList().joinToString(" ")}" },
        )
    }
}

/** Pick a question and type the answer. The answer is normalised and hashed by [PinManager]. */
@Composable
fun SecurityQuestionDialog(optional: Boolean, onDone: () -> Unit, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf(SecurityQuestions.first()) }
    var answer by remember { mutableStateOf("") }
    val canSave = PinManager.normalizeAnswer(answer).isNotEmpty()
    BrandDialog(
        title = if (optional) "Add a security question?" else "Security question",
        message = "Another way to set a new PIN if you lose the recovery code.",
        onDismiss = onDismiss,
        confirmLabel = "Save",
        onConfirm = {
            if (canSave) scope.launch {
                app.safety.pin.setSecurityQuestion(question, answer)
                answer = ""
                onDone()
            }
        },
        dismissLabel = if (optional) "Skip" else "Cancel",
        hints = listOf("OK" to "Select", "↑↓" to "Move", "Back" to if (optional) "Skip" else "Cancel"),
    ) {
        SettingChoiceRow("Question", SecurityQuestions, question, onSelect = { question = it })
        Spacer(Modifier.height(12.dp))
        SafetyTextField("Answer", answer, onValueChange = { answer = it.take(MAX_TEXT) })
    }
}

internal const val MAX_TEXT = 64

/**
 * TV-safe text field: the D-pad lands on a wrapper and OK starts editing, because a focused text field opens the
 * keyboard, which then captures every arrow key. White fill when focused and not editing.
 */
@Composable
internal fun SafetyTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    onSubmit: () -> Unit = {},
) {
    val brand = LocalBrand.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val wrapperInteraction = remember { MutableInteractionSource() }
    val wrapperFocused by wrapperInteraction.collectIsFocusedAsState()
    var editing by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    LaunchedEffect(editing) {
        if (editing) {
            withFrameNanos { }
            runCatching { field.requestFocus() }
        }
    }
    val focused = wrapperFocused || fieldFocused
    val pill = wrapperFocused && !editing
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, tween(150), label = "fieldScale")
    val shape = RoundedCornerShape(10.dp)
    Column {
        Text(label, fontSize = 13.sp, color = brand.textMuted)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier
                .clickable(interactionSource = wrapperInteraction, indication = null) { editing = true }
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(if (pill) Color.White else brand.surfaceVariant, shape)
                .border(if (focused) brand.focusBorderWidth else 1.dp, if (focused) Color.White else brand.hairline, shape)
                .semantics { contentDescription = label },
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                enabled = editing,
                textStyle = TextStyle(fontSize = 16.sp, lineHeight = 1.3.em, color = if (pill) brand.ink else brand.textPrimary),
                cursorBrush = SolidColor(brand.blue),
                keyboardOptions = KeyboardOptions(
                    capitalization = capitalization, autoCorrectEnabled = false, imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    onSubmit()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .focusRequester(field)
                    .onFocusChanged {
                        if (fieldFocused && !it.isFocused) editing = false
                        fieldFocused = it.isFocused
                    }
                    .onPreviewKeyEvent { e ->
                        when (e.key) {
                            Key.DirectionDown, Key.DirectionUp -> {
                                if (e.type == KeyEventType.KeyDown) {
                                    focusManager.moveFocus(if (e.key == Key.DirectionDown) FocusDirection.Down else FocusDirection.Up)
                                }
                                true
                            }
                            Key.DirectionCenter -> {
                                if (e.type == KeyEventType.KeyUp) keyboard?.show()
                                true
                            }
                            else -> false
                        }
                    },
            )
        }
    }
}
