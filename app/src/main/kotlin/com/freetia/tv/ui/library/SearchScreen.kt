package com.freetia.tv.ui.library

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.repository.LibraryRepository
import com.freetia.tv.ui.FormField
import com.freetia.tv.ui.components.FocusPill
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.Navigator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce

/** Search by channel name or group across every playlist (§3.15), typed or by voice. Child Safe Mode applies. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Composable
fun SearchScreen(nav: Navigator) {
    val app = LocalContext.current.applicationContext as FreetiaApp
    val toast = LocalBrandToast.current
    val scope = rememberCoroutineScope()
    val library = remember { LibraryRepository(app.database, app.settings) }
    var query by rememberSaveable { mutableStateOf("") }
    val queryFlow = remember { MutableStateFlow(query) }
    LaunchedEffect(query) { queryFlow.value = query }
    val results = remember { library.search(queryFlow.debounce(250)).cachedIn(scope) }.collectAsLazyPagingItems()
    val favouriteIds = rememberFavouriteIds()
    var contextChannel by remember { mutableStateOf<ChannelEntity?>(null) }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                ?.let { query = LibraryRepository.sanitizeQuery(it) }
        }
    }
    val startVoice = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a channel name")
        try {
            voice.launch(intent)
        } catch (e: Exception) {
            // No recognizer on this device (some Fire TV models, emulators): typing still works.
            toast.show("Voice search isn't available on this device. Type to search.", isError = true)
        }
    }

    AllLockedIdsProvider {
        SettingsScaffold(
            title = "Search",
            subtitle = "Channel names and groups, across all your playlists",
            hints = listOf("OK" to "Type or watch", "Hold OK" to "Actions", "↑↓" to "Move", "Back" to "Return"),
        ) {
            item(key = "field") {
                FormField(
                    label = "Search",
                    value = query,
                    onValueChange = { query = it.take(60) },
                    placeholder = "Channel or group name",
                    isError = false,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "voice") {
                Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FocusPill("Voice search", startVoice)
                    if (query.isNotEmpty()) FocusPill("Clear", { query = "" })
                }
            }
            when {
                LibraryRepository.sanitizeQuery(query).isEmpty() ->
                    item(key = "hint") { NoteText("Type a channel or group name, or use Voice search.") }
                results.isEmptyAndSettled() ->
                    item(key = "none") { NoteText("No channels match that search. Try a shorter name.") }
                else -> pagedChannelItems(results, favouriteIds, onPlay = { nav.play(it.id) }, onContext = { contextChannel = it })
            }
        }
    }
    contextChannel?.let { ChannelContextMenu(it, onDismiss = { contextChannel = null }) }
}
