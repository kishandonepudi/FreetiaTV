package com.freetia.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetia.tv.ui.HintBar
import com.freetia.tv.ui.theme.LocalBrand

/** Screen insets (§5): TV-safe 48dp × 24dp; below 720dp width (phones) 16dp, as phones have no overscan. */
@Immutable
data class ScreenInsets(val horizontal: Dp, val vertical: Dp, val isCompact: Boolean)

@Composable
fun rememberScreenInsets(): ScreenInsets {
    val compact = LocalConfiguration.current.screenWidthDp < 720
    return remember(compact) {
        if (compact) ScreenInsets(16.dp, 16.dp, true) else ScreenInsets(48.dp, 24.dp, false)
    }
}

val DefaultSettingsHints = listOf("OK" to "Select", "←→" to "Change", "↑↓" to "Move", "Back" to "Return")

/**
 * Full-screen settings page: title, optional subtitle, a lazy list of rows and a hint bar. The first focusable row
 * takes focus on entry so the remote works immediately.
 */
@Composable
fun SettingsScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    hints: List<Pair<String, String>> = DefaultSettingsHints,
    autoFocus: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val brand = LocalBrand.current
    val insets = rememberScreenInsets()
    val focus = remember { FocusRequester() }
    Column(
        modifier
            .fillMaxSize()
            .background(brand.ink)
            .padding(horizontal = insets.horizontal, vertical = insets.vertical)
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = brand.textPrimary)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 14.sp, color = brand.textMuted)
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 960.dp)
                .focusRequester(focus),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            // Room for the 1.03 focus scale so the first and last rows are not clipped.
            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
            content = content,
        )
        Spacer(Modifier.height(12.dp))
        HintBar(*hints.toTypedArray())
    }
    if (autoFocus) {
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}

/** Small muted heading between groups of rows. */
@Composable
fun SettingSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        color = LocalBrand.current.textMuted, modifier = modifier.padding(top = 12.dp, bottom = 2.dp, start = 16.dp),
    )
}
