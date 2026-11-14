package com.freetia.tv.data.sync

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface SyncEvent {
    data class Updated(val playlistId: Long, val playlistName: String, val added: Int, val removed: Int) : SyncEvent
    data class Failed(val playlistId: Long, val playlistName: String) : SyncEvent
}

/** Worker and syncer results for the UI to show as BrandToasts; workers have no UI of their own. */
object SyncEvents {
    private val mutable = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val flow: SharedFlow<SyncEvent> = mutable.asSharedFlow()

    private val failed = MutableStateFlow<Set<Long>>(emptySet())

    /** Playlists whose latest update in this process failed; unlike [flow] it can be read after the fact. */
    val failedIds: StateFlow<Set<Long>> = failed.asStateFlow()

    fun emit(event: SyncEvent) {
        when (event) {
            is SyncEvent.Failed -> failed.update { it + event.playlistId }
            is SyncEvent.Updated -> failed.update { it - event.playlistId }
        }
        mutable.tryEmit(event)
    }

    /** An update that found no changes: clears a failure without a toast. */
    fun succeeded(playlistId: Long) = failed.update { it - playlistId }
}
