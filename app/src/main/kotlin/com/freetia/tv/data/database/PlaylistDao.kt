package com.freetia.tv.data.database

import androidx.room.*
import com.freetia.tv.data.model.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists ORDER BY priority ASC")
    fun getAllFlow(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY priority ASC")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultPlaylist(): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY priority ASC LIMIT 1")
    suspend fun getFirstPlaylist(): PlaylistEntity?

    /** Failure fallback (§3.1): the next playlist after [priority]. */
    @Query("SELECT * FROM playlists WHERE priority > :priority ORDER BY priority ASC LIMIT 1")
    suspend fun getNextByPriority(priority: Int): PlaylistEntity?

    @Query("UPDATE playlists SET priority = :newPriority WHERE id = :id")
    suspend fun updatePriority(id: Long, newPriority: Int)

    @Query("UPDATE playlists SET isDefault = 1 WHERE id = :id")
    suspend fun setAsDefault(id: Long)

    @Query("UPDATE playlists SET isDefault = 0")
    suspend fun clearAllDefaults()

    @Query("UPDATE playlists SET lastChannelId = :channelId, lastChannelNumber = :channelNumber WHERE id = :playlistId")
    suspend fun updateLastChannel(playlistId: Long, channelId: Long, channelNumber: Int)

    @Query("UPDATE playlists SET lastUpdateTime = :time, lastUpdateSuccess = 1, failureMessage = '' WHERE id = :id")
    suspend fun markUpdateSuccess(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE playlists SET lastUpdateSuccess = 0, failureMessage = :message WHERE id = :id")
    suspend fun markUpdateFailure(id: Long, message: String)

    @Query("UPDATE playlists SET epgLastUpdateTime = :time, epgLastUpdateSuccess = :success WHERE id = :id")
    suspend fun markEpgUpdate(id: Long, success: Boolean, time: Long = System.currentTimeMillis())

    @Query("UPDATE playlists SET userEpgUrl = :url WHERE id = :id")
    suspend fun setUserEpgUrl(id: Long, url: String)

    /** Priorities become 1..n in list order (1 = highest). */
    @Transaction
    suspend fun reorderPlaylists(playlists: List<PlaylistEntity>) {
        playlists.forEachIndexed { index, playlist ->
            updatePriority(playlist.id, index + 1)
        }
    }

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun getCount(): Int

    @Query("SELECT COALESCE(MAX(priority), 0) FROM playlists")
    suspend fun maxPriority(): Int
}
