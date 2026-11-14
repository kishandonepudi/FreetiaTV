package com.freetia.tv.data.repository

import androidx.room.withTransaction
import com.freetia.tv.data.database.FreetiaDatabase
import com.freetia.tv.data.model.PlaylistEntity
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(private val db: FreetiaDatabase) {
    private val dao = db.playlistDao()

    fun observeAll(): Flow<List<PlaylistEntity>> = dao.getAllFlow()

    /** Startup rule: default playlist if set, otherwise priority 1. */
    suspend fun startupPlaylist(): PlaylistEntity? = dao.getDefaultPlaylist() ?: dao.getFirstPlaylist()

    suspend fun add(playlist: PlaylistEntity): Long = db.withTransaction {
        val nextPriority = dao.getCount() + 1
        val id = dao.insert(playlist.copy(priority = nextPriority))
        if (playlist.isDefault) setDefault(id)
        id
    }

    suspend fun setDefault(id: Long) = db.withTransaction {
        dao.clearAllDefaults()
        dao.setAsDefault(id)
    }

    suspend fun reorder(ordered: List<PlaylistEntity>) = dao.reorderPlaylists(ordered)

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun rememberLastChannel(playlistId: Long, channelNumber: Int) =
        dao.updateLastChannel(playlistId, channelNumber)
}
