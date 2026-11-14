package com.freetia.tv.ui.safety

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.ui.components.BrandToastState
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.nav.LocalNavigator
import com.freetia.tv.ui.nav.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * After a correct PIN, further changes don't ask again for [ttlMs], so a child can't use a screen an adult left open.
 * [now] is a monotonic clock (elapsedRealtime), so changing the wall clock can't extend it.
 */
internal class PinGrace(private val ttlMs: Long = TTL_MS, private val now: () -> Long) {
    private var grantedAt: Long? = null

    fun grant() { grantedAt = now() }

    fun isActive(): Boolean {
        val at = grantedAt ?: return false
        val elapsed = now() - at
        if (elapsed in 0 until ttlMs) return true
        grantedAt = null
        return false
    }

    companion object {
        const val TTL_MS = 60_000L
    }
}

/**
 * Safety actions for any screen or menu (library rows, player menu, settings): lock/unlock, mark as adult, Child
 * Safe Mode and a general [withPin] gate. Get one with [rememberSafetyActions], which also shows the PIN dialogs.
 * After one correct PIN, further changes made through the same instance don't ask again for [PinGrace.TTL_MS].
 */
@Stable
class SafetyActions internal constructor(
    private val app: FreetiaApp,
    private val scope: CoroutineScope,
    private val toast: BrandToastState,
) {
    internal sealed interface Pending {
        val then: () -> Unit
        val onCancel: () -> Unit

        data class Verify(val title: String, override val then: () -> Unit, override val onCancel: () -> Unit) : Pending
        data class Setup(override val then: () -> Unit, override val onCancel: () -> Unit) : Pending
    }

    internal var pending by mutableStateOf<Pending?>(null)
    private val verified = PinGrace(now = SystemClock::elapsedRealtime)

    /** Runs [action] after a correct PIN. With no PIN yet, asks the user to create one first. */
    fun withPin(title: String = "Enter your PIN", onCancel: () -> Unit = {}, action: () -> Unit) {
        if (verified.isActive()) return action()
        scope.launch {
            pending = if (app.safety.pin.hasPin()) Pending.Verify(title, action, onCancel) else Pending.Setup(action, onCancel)
        }
    }

    /** Runs [action] once a PIN exists (creating one if needed), without asking for it. For making things safer. */
    fun ensurePin(onCancel: () -> Unit = {}, action: () -> Unit) {
        scope.launch {
            if (app.safety.pin.hasPin()) action() else pending = Pending.Setup(action, onCancel)
        }
    }

    internal fun complete() {
        val p = pending ?: return
        verified.grant()
        pending = null
        p.then()
    }

    internal fun cancel() {
        val p = pending ?: return
        pending = null
        p.onCancel()
    }

    /** Turning on needs only a PIN to exist (it is set on first enablement); turning off needs the PIN. */
    fun setChildSafeMode(on: Boolean) {
        val apply = {
            scope.launch {
                app.safety.setChildSafeMode(on)
                toast.show(if (on) "Child Safe Mode is on. Adult channels are hidden." else "Child Safe Mode is off")
            }
            Unit
        }
        if (on) ensurePin(action = apply) else withPin("Enter your PIN to turn off Child Safe Mode", action = apply)
    }

    fun setChannelLocked(channel: ChannelEntity, locked: Boolean) = withPin("Enter your PIN to change locks") {
        scope.launch {
            app.safety.setChannelLocked(channel.id, locked)
            toast.show(
                when {
                    locked -> "${channel.name} is locked"
                    app.safety.isLocked(channel.id) -> "${channel.name} is still locked by its group or region"
                    else -> "${channel.name} is unlocked"
                }
            )
        }
    }

    fun toggleChannelLock(channel: ChannelEntity) {
        scope.launch { setChannelLocked(channel, !app.safety.isIndividuallyLocked(channel.id)) }
    }

    fun setMarkedAdult(channel: ChannelEntity, marked: Boolean) = withPin("Enter your PIN to change adult marks") {
        scope.launch {
            app.safety.setMarkedAdult(channel.id, marked)
            toast.show(
                if (marked) "${channel.name} is marked as adult. Child Safe Mode hides it."
                else "${channel.name} is no longer marked as adult"
            )
        }
    }

    /** Uses the stored mark rather than [channel]'s, which may be stale. */
    fun toggleMarkAdult(channel: ChannelEntity) {
        scope.launch {
            val current = app.database.channelDao().observeById(channel.id).first() ?: return@launch
            setMarkedAdult(current, !current.isMarkedAdult)
        }
    }

    /** Bulk lock by group-title category ([com.freetia.tv.data.model.LockScope.GROUP]) or region code. */
    fun setCategoryLocked(scope: String, value: String, locked: Boolean) = withPin("Enter your PIN to change locks") {
        this.scope.launch {
            app.safety.setCategoryLocked(scope, value, locked)
            toast.show(if (locked) "Everything in $value is locked" else "$value is unlocked")
        }
    }

    /** Ends a session unlock now; locked channels need the PIN again. No PIN needed: it only makes things safer. */
    fun lockSessionNow() {
        app.safety.session.lock()
        toast.show("Locked channels need the PIN again")
    }
}

/** A [SafetyActions] bound to this screen. Call it once per screen; it hosts its own PIN dialogs. */
@Composable
fun rememberSafetyActions(): SafetyActions {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val scope = rememberCoroutineScope()
    val toast = LocalBrandToast.current
    val nav = LocalNavigator.current
    val actions = remember { SafetyActions(app, scope, toast) }
    when (val p = actions.pending) {
        is SafetyActions.Pending.Verify -> PinEntryDialog(
            title = p.title,
            onDismiss = actions::cancel,
            onSuccess = actions::complete,
            onForgot = { nav.push(Route.PinRecovery) },
        )
        is SafetyActions.Pending.Setup -> PinSetupDialog(onDismiss = actions::cancel, onDone = actions::complete)
        null -> Unit
    }
    return actions
}
