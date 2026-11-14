package com.freetia.tv.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.freetia.tv.FreetiaApp
import com.freetia.tv.R
import com.freetia.tv.ui.theme.LocalBrand

/**
 * Brand start-up screen (standards/BRAND_KIT.md §2.2 "splash/loading"): shown from process start until the
 * first playlist has channels, so the user never sees an empty list or a bare spinner.
 */
@Composable
fun BrandSplash(status: String) {
    val brand = LocalBrand.current
    // One short entrance, then a slow breathing glow; both cheap (graphicsLayer only, no relayout).
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
    // Kept as State and read only in the draw phase, so the breathing glow never recomposes or rebuilds the Brush.
    // Low-RAM TVs get a flat background and no infinite animation (M2).
    val lowRam = (LocalContext.current.applicationContext as FreetiaApp).memoryGuard.isLowRamDevice
    val glow: State<Float>? = if (brand.ambientGlow && !lowRam) {
        rememberInfiniteTransition(label = "glow").animateFloat(
            initialValue = 0.18f, targetValue = 0.32f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
            label = "glowAlpha",
        )
    } else null

    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val halo = Brush.radialGradient(
                    listOf(brand.violet, Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.42f),
                    radius = size.minDimension * 0.75f,
                )
                onDrawBehind {
                    drawRect(brand.ink)
                    if (glow != null) drawRect(halo, alpha = glow.value)
                }
            }
            .semantics { contentDescription = "Freetia. $status" },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                alpha = entrance.value
                val s = 0.92f + 0.08f * entrance.value
                scaleX = s; scaleY = s
            },
        ) {
            Image(painterResource(R.drawable.ic_brand_mark), contentDescription = null, modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                "Freetia", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = brand.textPrimary,
                letterSpacing = (-0.005).em,
            )
            Spacer(Modifier.height(6.dp))
            Text("Free & open-source IPTV player", fontSize = 14.sp, color = brand.textMuted)
            Spacer(Modifier.height(36.dp))
            IndeterminateBar(Modifier.width(180.dp), animated = !lowRam)
            Spacer(Modifier.height(12.dp))
            Text(status, fontSize = 13.sp, color = brand.textMuted)
        }
        HintBar(
            "Back ×2" to "Exit",
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 48.dp, vertical = 24.dp),
        )
    }
}

@Composable
private fun IndeterminateBar(modifier: Modifier, animated: Boolean) {
    val brand = LocalBrand.current
    // Low-RAM devices get a static centred segment: an infinite transition redraws every frame.
    val shiftState = if (animated) {
        rememberInfiniteTransition(label = "bar").animateFloat(
            initialValue = -0.4f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing)),
            label = "barShift",
        )
    } else {
        remember { mutableFloatStateOf(0.3f) }
    }
    // Read inside graphicsLayer only, so each frame redraws the layer without recomposing.
    val shift by shiftState
    Box(modifier.height(3.dp).clip(RoundedCornerShape(2.dp)).background(brand.tintTrack)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(maxWidth * 0.4f)
                    .graphicsLayer { translationX = shift * constraints.maxWidth }
                    .background(brand.focusGradient, RoundedCornerShape(2.dp)),
            )
        }
    }
}
