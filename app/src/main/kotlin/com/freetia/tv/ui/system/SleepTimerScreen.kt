package com.freetia.tv.ui.system

import android.os.Build
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetia.tv.FreetiaApp
import com.freetia.tv.core.system.AutoStartAdvisor
import com.freetia.tv.core.system.AutoStartStatus
import com.freetia.tv.core.timer.SleepTimerManager
import com.freetia.tv.core.timer.SleepTimerManager.TimerState
import com.freetia.tv.core.util.DeviceDetector
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingStepperRow
import com.freetia.tv.ui.components.SettingToggleRow
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.nav.Route
import com.freetia.tv.ui.theme.LocalBrand
import kotlinx.coroutines.launch

/** The sleep timer page (§3.10): on/off, length, and quick +15 / +30 while it runs. */
@Composable
fun SleepTimerScreen(nav: Navigator) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val timer = app.sleepTimer
    val state by timer.timerState.collectAsState()
    val minutes by app.settings.sleepTimerDefaultMinutes.flow.collectAsState(SleepTimerManager.DEFAULT_MINUTES)
    val scope = rememberCoroutineScope()
    val running = state is TimerState.Active || state is TimerState.FinalWarning

    SettingsScaffold(
        title = "Sleep timer",
        subtitle = if (running) "Closing the app in ${formatRemaining(state)}" else "Stops playback and closes the app after the time you choose.",
    ) {
        item {
            SettingToggleRow(
                title = "Sleep timer",
                checked = running,
                onCheckedChange = { on ->
                    if (on) timer.start(minutes) else timer.stop()
                    scope.launch { app.settings.sleepTimerEnabled.set(on) }
                },
            )
        }
        item {
            SettingStepperRow(
                title = "Time",
                value = minutes,
                onValueChange = { next ->
                    scope.launch { app.settings.sleepTimerDefaultMinutes.set(next) }
                    if (running) timer.start(next)
                },
                range = SleepTimerManager.MIN_MINUTES..SleepTimerManager.MAX_MINUTES,
                step = SleepTimerManager.STEP_MINUTES,
                format = { "$it min" },
            )
        }
        if (running) {
            SleepTimerManager.QUICK_ADD_MINUTES.forEach { add ->
                item { SettingActionRow("Add $add minutes", onClick = { timer.addTime(add) }, showChevron = false) }
            }
        }
        item { SettingActionRow("Done", onClick = { nav.pop() }, showChevron = false) }
    }
}

private fun formatRemaining(state: TimerState): String {
    val ms = when (state) {
        is TimerState.Active -> state.remainingMs
        is TimerState.FinalWarning -> state.remainingMs
        else -> 0L
    }
    val total = (ms + 999) / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * Always-on system overlay, hosted once by the nav host: the 60-second sleep warning, the dim before the sleep exit,
 * and the one-time auto-start setup prompt.
 */
@Composable
fun SystemHost(nav: Navigator) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val brand = LocalBrand.current
    val state by app.sleepTimer.timerState.collectAsState()

    val optedIn by app.settings.autoStartOnTv.flow.collectAsState(false)
    val setupShown by app.settings.autoStartSetupShown.flow.collectAsState(true)
    LaunchedEffect(optedIn, setupShown) {
        val show = AutoStartAdvisor.shouldShowSetup(
            optedIn, setupShown, DeviceDetector.isTV(app), Build.VERSION.SDK_INT, AutoStartStatus.get(app),
        )
        if (show && nav.current.isBase) nav.push(Route.AutoStartSetup)
    }

    when (state) {
        is TimerState.FinalWarning -> Box(Modifier.fillMaxSize()) {
            Text(
                "Sleeping in 60 seconds. Press OK to continue.",
                fontSize = 20.sp,
                color = brand.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 48.dp, vertical = 48.dp)
                    .widthIn(max = 640.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brand.scrimStrong)
                    .clickable { app.sleepTimer.continueWatching() }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
        is TimerState.Expired -> {
            var target by remember { mutableStateOf(0f) }
            LaunchedEffect(Unit) { target = 1f }
            val alpha by animateFloatAsState(target, tween(2_000), label = "sleepDim")
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = alpha))
                    .pointerInput(Unit) { detectTapGestures { } },
            )
        }
        else -> Unit
    }
}

/**
 * While the warning shows, OK ends the countdown and is swallowed (down and up) so it does not also press the
 * focused control underneath.
 */
@Composable
fun Modifier.sleepWarningKeys(): Modifier {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val toast = LocalBrandToast.current
    val swallowUp = remember { booleanArrayOf(false) }
    return onPreviewKeyEvent { event ->
        val code = event.key.nativeKeyCode
        val isOk = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
            code == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (!isOk) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (app.sleepTimer.timerState.value !is TimerState.FinalWarning) return@onPreviewKeyEvent false
                swallowUp[0] = true
                app.sleepTimer.continueWatching()
                app.appScope.launch { app.settings.sleepTimerEnabled.set(false) }
                toast.show("Sleep timer off", false)
                true
            }
            KeyEventType.KeyUp -> swallowUp[0].also { swallowUp[0] = false }
            else -> false
        }
    }
}
