package com.freetia.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetia.tv.ui.theme.LocalBrand
import kotlinx.coroutines.delay

@Immutable
data class BrandToastMessage(val text: String, val isError: Boolean, val durationMs: Long, val id: Long)

/** Holder for the single on-screen toast; a new message replaces the current one. */
@Stable
class BrandToastState {
    var current: BrandToastMessage? by mutableStateOf(null)
        private set
    private var nextId = 0L

    fun show(text: String, isError: Boolean = false, durationMs: Long = DEFAULT_DURATION_MS) {
        current = BrandToastMessage(text, isError, durationMs, nextId++)
    }

    fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }

    companion object {
        const val DEFAULT_DURATION_MS = 3_000L
    }
}

/** Provided by the nav host: `LocalBrandToast.current.show("Saved")`. */
val LocalBrandToast = staticCompositionLocalOf { BrandToastState() }

/**
 * Non-blocking toast overlay (§3.2, §3.5): never takes focus or input, so the remote keeps working underneath.
 * Place it last in a full-screen Box.
 */
@Composable
fun BrandToastHost(state: BrandToastState, modifier: Modifier = Modifier) {
    val brand = LocalBrand.current
    val message = state.current
    var last by remember { mutableStateOf(message) }
    if (message != null) last = message
    LaunchedEffect(message?.id) {
        if (message != null) {
            delay(message.durationMs)
            state.dismiss(message.id)
        }
    }
    Box(modifier.fillMaxSize().padding(bottom = 72.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            // Keep the last text visible during the exit animation.
            val shown = message ?: last ?: return@AnimatedVisibility
            Text(
                shown.text,
                fontSize = 16.sp,
                color = if (shown.isError) brand.error else brand.textPrimary,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .background(brand.scrimStrong, RoundedCornerShape(50))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
