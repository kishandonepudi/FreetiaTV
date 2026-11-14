package com.freetia.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.freetia.tv.R
import com.freetia.tv.ui.theme.LocalBrand
import java.net.URI

// Long enough for token-bearing provider links, short enough to keep a pasted blob out of the database.
internal const val MAX_PLAYLIST_URL_LENGTH = 2048
private const val MAX_PLAYLIST_NAME_LENGTH = 60

/** Returns plain-language guidance when [raw] is not a usable http(s) playlist link, or null when it is. */
internal fun playlistUrlError(raw: String): String? {
    val url = raw.trim()
    if (url.isEmpty()) return "Enter the playlist link. It starts with http:// or https://"
    if (url.length > MAX_PLAYLIST_URL_LENGTH) return "This link is too long. Check it and try again."
    val uri = runCatching { URI(url) }.getOrNull()
    val scheme = uri?.scheme?.lowercase()
    val host = uri?.host ?: uri?.rawAuthority
    if ((scheme != "http" && scheme != "https") || host.isNullOrBlank()) {
        return "Check the link. It should start with http:// or https://"
    }
    return null
}

internal fun defaultPlaylistName(url: String): String =
    runCatching { URI(url.trim()).host }.getOrNull()
        ?.removePrefix("www.")
        ?.takeIf { it.isNotBlank() }
        ?: "My playlist"

/** TV-first form for adding a playlist link; [onAdd] receives a validated, trimmed URL and a non-blank name. */
@Composable
fun AddPlaylistScreen(onAdd: (name: String, url: String) -> Unit, onCancel: () -> Unit) {
    val brand = LocalBrand.current
    var url by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    // A quick double OK must not insert the playlist twice before the screen leaves.
    var submitted by remember { mutableStateOf(false) }
    val urlFocus = remember { FocusRequester() }
    val addFocus = remember { FocusRequester() }

    val submit = {
        val problem = playlistUrlError(url)
        error = problem
        if (problem != null) {
            urlFocus.requestFocus()
        } else if (!submitted) {
            submitted = true
            val cleanUrl = url.trim()
            onAdd(name.trim().ifEmpty { defaultPlaylistName(cleanUrl) }, cleanUrl)
        }
    }

    val screenInsets = com.freetia.tv.ui.components.rememberScreenInsets()
    Column(
        Modifier
            .fillMaxSize()
            .background(brand.ink)
            .padding(horizontal = screenInsets.horizontal, vertical = screenInsets.vertical)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.ic_brand_mark), contentDescription = "Freetia",
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text("Add playlist", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = brand.textPrimary)
        }
        Spacer(Modifier.height(16.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    "Type the M3U or M3U8 link from your TV provider, then choose Add.",
                    fontSize = 15.sp, lineHeight = 1.3.em, color = brand.textMuted,
                )
                Spacer(Modifier.height(20.dp))
                FormField(
                    label = "Playlist link",
                    value = url,
                    onValueChange = {
                        // The IME re-sends the current text when it reconnects; only a real edit clears the error.
                        if (it != url) {
                            url = it
                            error = null
                        }
                    },
                    placeholder = "https://",
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.focusRequester(urlFocus),
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 13.sp, lineHeight = 1.3.em, color = brand.error)
                }
                Spacer(Modifier.height(16.dp))
                FormField(
                    label = "Name (optional)",
                    value = name,
                    onValueChange = { name = it.take(MAX_PLAYLIST_NAME_LENGTH) },
                    placeholder = if (playlistUrlError(url) == null) defaultPlaylistName(url) else "My playlist",
                    isError = false,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    // Geometric search from the wide field picks Cancel, the button nearest its centre.
                    down = addFocus,
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Add", onClick = submit, primary = true, modifier = Modifier.focusRequester(addFocus))
                    PillButton("Cancel", onClick = onCancel)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HintBar("OK" to "Type or select", "↑↓" to "Move", "Back" to "Cancel")
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { urlFocus.requestFocus() }
    }
}

@Composable
internal fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isError: Boolean,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    down: FocusRequester? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    val brand = LocalBrand.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // The legacy text field opens the IME whenever it gains focus, and an open IME on TV captures every arrow
    // key. So the D-pad lands on this wrapper; OK (or a tap) hands focus to the text field, which opens the
    // keyboard, and leaving the text field makes it unfocusable again.
    val wrapperInteraction = remember { MutableInteractionSource() }
    val fieldInteraction = remember { MutableInteractionSource() }
    val wrapperFocused by wrapperInteraction.collectIsFocusedAsState()
    val fieldFocused by fieldInteraction.collectIsFocusedAsState()
    val focused = wrapperFocused || fieldFocused
    var editing by remember { mutableStateOf(false) }
    // §6 white focus pill while browsing; editing keeps the dark fill so the blue cursor stays readable.
    val pill = wrapperFocused && !editing
    var hadFieldFocus by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    LaunchedEffect(editing) {
        if (editing) {
            // enabled only takes effect after recomposition.
            withFrameNanos { }
            runCatching { field.requestFocus() }
        }
    }
    // 1.02 rather than 1.03: these fields are wide, and the larger scale pushes them past the column edge.
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, tween(150), label = "fieldScale")
    val shape = RoundedCornerShape(10.dp)
    Column {
        Text(label, fontSize = 13.sp, color = brand.textMuted)
        Spacer(Modifier.height(6.dp))
        // A separate node: on the text field's own node the wrapper's focus would count as the field's and
        // open the keyboard anyway.
        Box(
            modifier
                .focusProperties { if (down != null) this.down = down }
                .clickable(interactionSource = wrapperInteraction, indication = null) { editing = true }
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(if (pill) Color.White else brand.surfaceVariant, shape)
                .border(
                    width = if (focused || isError) brand.focusBorderWidth else 1.dp,
                    color = when {
                        focused -> Color.White
                        isError -> brand.error
                        else -> brand.hairline
                    },
                    shape = shape,
                )
                .semantics { contentDescription = label },
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = visualTransformation,
                textStyle = TextStyle(
                    fontSize = 16.sp, lineHeight = 1.3.em, color = if (pill) brand.ink else brand.textPrimary,
                ),
                cursorBrush = SolidColor(brand.blue),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                interactionSource = fieldInteraction,
                // Disabled until OK: the text field's own focusable ignores focusProperties { canFocus }.
                enabled = editing,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(field)
                    .onFocusChanged {
                        if (it.isFocused) {
                            hadFieldFocus = true
                        } else if (hadFieldFocus) {
                            hadFieldFocus = false
                            editing = false
                        }
                    }
                    // The text field treats ↑↓ as cursor moves; on a remote they must move between controls.
                    // OK reopens the keyboard after Back closed it.
                    .onPreviewKeyEvent { e ->
                        when (e.key) {
                            Key.DirectionDown, Key.DirectionUp -> {
                                if (e.type == KeyEventType.KeyDown) {
                                    if (e.key == Key.DirectionDown && down != null) {
                                        down.requestFocus()
                                    } else {
                                        focusManager.moveFocus(
                                            if (e.key == Key.DirectionDown) FocusDirection.Down else FocusDirection.Up
                                        )
                                    }
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
                decorationBox = { inner ->
                    Box(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder, fontSize = 16.sp, color = if (pill) brand.inkMuted else brand.textMuted,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

/** Brand pill button (§6 focus state): white pill with ink text when focused. */
@Composable
internal fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) =
    com.freetia.tv.ui.components.FocusPill(text, onClick, modifier, primary = primary)
