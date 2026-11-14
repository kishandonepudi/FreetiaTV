package com.freetia.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetia.tv.ui.theme.LocalBrand

/**
 * Brand focus container (§6): focused = white fill, 2dp white border, scale 1.03 over a 150 ms tween.
 *
 * Built on `clickable(indication = null)` rather than an M3 `Surface(onClick)`: the Surface's focus state layer
 * tints the white pill grey, which is the "focused grey" bug every pill used to have. [content] receives the
 * focus state so it can switch to ink text on the white fill.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(50),
    unfocusedColor: Color = LocalBrand.current.tintChip,
    enabled: Boolean = true,
    role: Role = Role.Button,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val brand = LocalBrand.current
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, tween(150), label = "focusScale")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(if (focused) Color.White else unfocusedColor, shape)
            .then(if (focused) Modifier.border(brand.focusBorderWidth, Color.White, shape) else Modifier)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, role = role, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) { content(focused) }
}

/** The brand pill button. [primary] gives the blue selected tint when not focused. */
@Composable
fun FocusPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val brand = LocalBrand.current
    FocusSurface(
        onClick = onClick,
        enabled = enabled,
        unfocusedColor = if (primary) brand.selectedTint else brand.tintChip,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
    ) { focused ->
        val color = when {
            focused -> brand.ink
            enabled -> brand.textPrimary
            else -> brand.textMuted
        }
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 28.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, color = color)
        }
    }
}
