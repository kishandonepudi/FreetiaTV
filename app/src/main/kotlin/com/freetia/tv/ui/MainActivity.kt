package com.freetia.tv.ui

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.freetia.tv.FreetiaApp
import com.freetia.tv.core.system.AppExit
import com.freetia.tv.core.system.BackgroundPolicy
import com.freetia.tv.core.system.PipState
import com.freetia.tv.core.util.DeviceDetector
import com.freetia.tv.playback.PlaybackHost
import com.freetia.tv.data.epg.ReminderLaunch
import androidx.compose.runtime.LaunchedEffect
import android.content.Intent
import com.freetia.tv.ui.components.BrandToastState
import com.freetia.tv.ui.nav.AppSession
import com.freetia.tv.ui.nav.FreetiaNavHost
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.nav.Route
import com.freetia.tv.ui.theme.FreetiaTheme

class MainActivity : ComponentActivity() {
    private val app get() = application as FreetiaApp

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // The app is always dark: force light system-bar icons on transparent bars, whatever the phone's day/night mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        AppExit.attach(this)
        if (savedInstanceState == null) ReminderLaunch.fromIntent(this, intent)
        setContent {
            val highContrast by app.settings.highContrast.flow.collectAsState(initial = false)
            val textSize by app.settings.textSizePercent.flow.collectAsState(initial = 100)
            // The back stack survives rotation and process death; routes are Serializable.
            val nav = rememberSaveable(saver = NavigatorSaver) { Navigator() }
            val session = remember { AppSession(app, lifecycleScope, BrandToastState()) { AppExit.exit(app) } }
            val reminderChannel by ReminderLaunch.pending.collectAsState()
            LaunchedEffect(reminderChannel) { reminderChannel?.let { nav.play(it); ReminderLaunch.consume() } }
            FreetiaTheme(darkTheme = true, highContrast = highContrast, textSizePercent = textSize) { FreetiaNavHost(nav, session) }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val enter = BackgroundPolicy.shouldEnterPip(
            isTv = DeviceDetector.isTV(this),
            pipSupported = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE),
            backgroundAllowed = PlaybackHost.backgroundAllowed,
            playing = PlaybackHost.isPlaying,
        )
        if (enter) {
            val params = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            runCatching { enterPictureInPictureMode(params) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipState.set(isInPictureInPictureMode)
    }

    override fun onDestroy() {
        AppExit.detach(this)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ReminderLaunch.fromIntent(this, intent)
    }

    private companion object {
        val NavigatorSaver = Saver<Navigator, ArrayList<Route>>(
            save = { ArrayList(it.stack) },
            restore = { Navigator(it) },
        )
    }
}
