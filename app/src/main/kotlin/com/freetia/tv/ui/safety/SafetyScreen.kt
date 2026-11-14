package com.freetia.tv.ui.safety

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.settings.LockBehavior
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingChoiceRow
import com.freetia.tv.ui.components.SettingSectionHeader
import com.freetia.tv.ui.components.SettingToggleRow
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.nav.Route
import kotlinx.coroutines.launch

private val UnlockMinutes = listOf(15, 30, 60, 120, 240, 0)

internal fun unlockLabel(minutes: Int) = if (minutes <= 0) "Until exit" else "$minutes min"

internal fun lockBehaviorLabel(b: LockBehavior) = when (b) {
    LockBehavior.SESSION_UNLOCK -> "Unlock for a while"
    LockBehavior.ALWAYS_ASK -> "Ask every time"
}

/** Settings > Safety (§3.7, §3.8, §3.14). Every change except turning protection on needs the PIN. */
@Composable
fun SafetyScreen(nav: Navigator) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val s = app.settings
    val toast = LocalBrandToast.current
    val scope = rememberCoroutineScope()
    val actions = rememberSafetyActions()
    val childSafe by s.childSafeMode.flow.collectAsState(s.childSafeMode.default)
    val behavior by s.defaultLockBehavior.flow.collectAsState(s.defaultLockBehavior.default)
    val minutes by s.sessionUnlockMinutes.flow.collectAsState(s.sessionUnlockMinutes.default)
    var refresh by remember { mutableIntStateOf(0) }
    val hasPin by produceState(false, refresh, actions.pending) { value = app.safety.pin.hasPin() }
    val question by produceState<String?>(null, refresh, actions.pending) { value = app.safety.pin.securityQuestion() }
    var changingPin by remember { mutableStateOf(false) }
    var editingQuestion by remember { mutableStateOf(false) }
    var shownCode by remember { mutableStateOf<String?>(null) }

    SettingsScaffold(
        title = "Child Safe Mode and PIN",
        subtitle = if (hasPin) "Changes here need your PIN." else "Create a PIN to protect these settings.",
    ) {
        item {
            SettingToggleRow(
                "Child Safe Mode", childSafe, onCheckedChange = actions::setChildSafeMode,
                subtitle = "Hides adult channels from every list, search and channel up/down",
            )
        }
        item { SettingSectionHeader("PIN") }
        item {
            SettingActionRow(
                if (hasPin) "Change PIN" else "Create PIN",
                onClick = {
                    if (hasPin) actions.withPin("Enter your current PIN") { changingPin = true }
                    else actions.ensurePin { refresh++ }
                },
            )
        }
        if (hasPin) {
            item { SettingActionRow("Forgot PIN", onClick = { nav.push(Route.PinRecovery) }) }
            item { SettingSectionHeader("Recovery") }
            item {
                SettingActionRow(
                    "New recovery code", subtitle = "Shows a new code once. The old code stops working.",
                    onClick = { actions.withPin { scope.launch { shownCode = app.safety.pin.regenerateRecoveryCode() } } },
                )
            }
            item {
                SettingActionRow(
                    "Security question", value = question ?: "Not set",
                    onClick = { actions.withPin { editingQuestion = true } },
                )
            }
        }
        item { SettingSectionHeader("Channel locks") }
        item {
            SettingChoiceRow(
                "When a locked channel opens", LockBehavior.entries, behavior,
                onSelect = { b -> actions.withPin { scope.launch { s.defaultLockBehavior.set(b) } } },
                label = ::lockBehaviorLabel,
            )
        }
        item {
            SettingChoiceRow(
                "Unlock lasts", (UnlockMinutes + minutes).distinct(), minutes,
                onSelect = { m -> actions.withPin { scope.launch { s.sessionUnlockMinutes.set(m) } } },
                label = ::unlockLabel,
                subtitle = "One PIN entry unlocks locked channels for this long",
                enabled = behavior == LockBehavior.SESSION_UNLOCK,
            )
        }
        item {
            SettingActionRow(
                "Lock again now", subtitle = "Ends an unlock early", showChevron = false,
                onClick = actions::lockSessionNow,
            )
        }
        item {
            SettingActionRow("Locked channels and categories", onClick = { nav.push(Route.Locks) })
        }
    }

    if (changingPin) {
        PinSetupDialog(
            title = "Enter a new PIN",
            issueRecoveryCode = false,
            onDismiss = { changingPin = false },
            onDone = {
                changingPin = false
                refresh++
                toast.show("PIN changed")
            },
        )
    }
    if (editingQuestion) {
        SecurityQuestionDialog(
            optional = false,
            onDone = {
                editingQuestion = false
                refresh++
                toast.show("Security question saved")
            },
            onDismiss = { editingQuestion = false },
        )
    }
    shownCode?.let { RecoveryCodeDialog(it, onDone = { shownCode = null }) }
}
