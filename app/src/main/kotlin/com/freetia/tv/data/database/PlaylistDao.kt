package com.freetia.tv.data.database

import androidx.room.*
import com.freetia.tv.data.model.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Delete
    suspend fun delete(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY priority ASC")
    fun getAllFlow(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY priority ASC")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultPlaylist(): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY priority ASC LIMIT 1")
    suspend fun getFirstPlaylist(): PlaylistEntity?

    @Query("UPDATE playlists SET priority = :newPriority WHERE id = :id")
    suspend fun updatePriority(id: Long, newPriority: Int)

    @Query("UPDATE playlists SET isDefault = 1 WHERE id = :id")
    suspend fun setAsDefault(id: Long)

    @Query("UPDATE playlists SET isDefault = 0")
    suspend fun clearAllDefaults()

    @Query("UPDATE playlists SET lastChannelNumber = :channelNumber WHERE id = :playlistId")
    suspend fun updateLastChannel(playlistId: Long, channelNumber: Int)

    @Query("UPDATE playlists SET lastUpdateTime = :time, lastUpdateSuccess = 1, failureMessage = '' WHERE id = :id")
    suspend fun markUpdateSuccess(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE playlists SET lastUpdateSuccess = 0, failureMessage = :message WHERE id = :id")
    suspend fun markUpdateFailure(id: Long, message: String)

    @Transaction
    suspend fun reorderPlaylists(playlists: List<PlaylistEntity>) {
        playlists.forEachIndexed { index, playlist ->
            updatePriority(playlist.id, index)
        }
    }

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun getCount(): Int

    @Query("SELECT * FROM playlists WHERE sourceUrl = :url LIMIT 1")
    suspend fun getByUrl(url: String): PlaylistEntity?
}
