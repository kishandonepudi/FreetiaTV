package com.freetia.tv.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.freetia.tv.data.model.ChannelEntity

data class ChannelKey(val id: Long, val streamUrl: String)

/** Source-owned columns only, so a refresh never overwrites user choices (adult mark, audio, CC). */
data class ChannelSourceUpdate(
    val id: Long,
    val tvgId: String,
    val tvgName: String,
    val tvgLogo: String,
    val name: String,
    val number: Int,
    val groupTitle: String,
    val tvgCountry: String,
    val tvgLanguage: String,
    val tvgType: String,
    val catchupEnabled: Boolean,
    val catchup: String,
    val catchupSource: String,
    val catchupDays: Int,
    val isAdult: Boolean,
)

@Dao
interface PlaylistSyncDao {
    @Query("SELECT id, streamUrl FROM channels WHERE playlistId = :playlistId ORDER BY id")
    suspend fun existingKeys(playlistId: Long): List<ChannelKey>

    // ABORT, not REPLACE: REPLACE deletes the old row and would cascade-delete favourites and locks.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Update(entity = ChannelEntity::class)
    suspend fun updateSource(updates: List<ChannelSourceUpdate>)

    @Query("DELETE FROM channels WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT epgUrl FROM playlists WHERE id = :playlistId")
    suspend fun epgUrlRef(playlistId: Long): String?

    /**
     * Saves the header guide link's encrypted-store reference unless one is already set. A plain value left by an
     * older build is replaced. Returns 0 when nothing changed, so the caller can drop the unused secret.
     */
    @Query("UPDATE playlists SET epgUrl = :ref WHERE id = :playlistId AND epgUrl NOT LIKE 'playlist_url:%'")
    suspend fun setEpgUrlRefIfUnset(playlistId: Long, ref: String): Int
}
