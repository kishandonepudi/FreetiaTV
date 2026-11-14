package com.freetia.tv.data.database

import androidx.room.*
import com.freetia.tv.data.model.LockedChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LockedChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lockedChannel: LockedChannelEntity): Long

    @Delete
    suspend fun delete(lockedChannel: LockedChannelEntity)

    @Query("DELETE FROM locked_channels WHERE channelId = :channelId")
    suspend fun unlockChannel(channelId: Long)

    @Query("SELECT * FROM locked_channels WHERE channelId = :channelId LIMIT 1")
    suspend fun isLocked(channelId: Long): LockedChannelEntity?

    @Query("SELECT * FROM locked_channels ORDER BY lockedTime DESC")
    fun getAllLockedFlow(): Flow<List<LockedChannelEntity>>

    @Query("SELECT * FROM locked_channels ORDER BY lockedTime DESC")
    suspend fun getAllLocked(): List<LockedChannelEntity>

    @Query("""
        SELECT lc.* FROM locked_channels lc
        JOIN channels c ON lc.channelId = c.id
        WHERE c.playlistId = :playlistId
        ORDER BY lc.lockedTime DESC
    """)
    suspend fun getLockedChannelsForPlaylist(playlistId: Long): List<LockedChannelEntity>

    @Query("""
        SELECT COUNT(*) FROM locked_channels
    """)
    suspend fun getLockedCount(): Int

    @Query("""
        SELECT COUNT(*) FROM locked_channels
        WHERE channelId = :channelId
    """)
    suspend fun isChannelLocked(channelId: Long): Boolean

    @Query("DELETE FROM locked_channels")
    suspend fun unlockAll()

    @Query("""
        SELECT lc.* FROM locked_channels lc
        WHERE lc.lockedTime > :timeThreshold
    """)
    suspend fun getRecentlyLocked(timeThreshold: Long): List<LockedChannelEntity>
}
