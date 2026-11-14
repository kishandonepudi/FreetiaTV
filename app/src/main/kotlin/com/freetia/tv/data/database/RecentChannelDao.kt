package com.freetia.tv.data.database

import androidx.room.*
import com.freetia.tv.data.model.RecentChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recent: RecentChannelEntity): Long

    @Delete
    suspend fun delete(recent: RecentChannelEntity)

    @Query("SELECT * FROM recent_channels WHERE userId = :userId ORDER BY watchedTime DESC LIMIT :limit")
    fun getRecentFlow(userId: String = "default", limit: Int = 20): Flow<List<RecentChannelEntity>>

    @Query("SELECT * FROM recent_channels WHERE userId = :userId ORDER BY watchedTime DESC LIMIT :limit")
    suspend fun getRecent(userId: String = "default", limit: Int = 20): List<RecentChannelEntity>

    @Query("SELECT * FROM recent_channels WHERE channelId = :channelId AND userId = :userId LIMIT 1")
    suspend fun getByChannelAndUser(channelId: Long, userId: String = "default"): RecentChannelEntity?

    @Query("""
        DELETE FROM recent_channels
        WHERE userId = :userId AND watchedTime NOT IN (
            SELECT watchedTime FROM recent_channels
            WHERE userId = :userId
            ORDER BY watchedTime DESC
            LIMIT :keepCount
        )
    """)
    suspend fun trimOldEntries(userId: String, keepCount: Int = 20)

    @Query("DELETE FROM recent_channels WHERE userId = :userId")
    suspend fun clearAllForUser(userId: String)

    @Query("""
        UPDATE recent_channels
        SET watchedTime = :newTime
        WHERE channelId = :channelId AND userId = :userId
    """)
    suspend fun updateWatchTime(channelId: Long, userId: String = "default", newTime: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM recent_channels WHERE userId = :userId")
    suspend fun getCount(userId: String = "default"): Int

    @Query("""
        SELECT rc.* FROM recent_channels rc
        JOIN channels c ON rc.channelId = c.id
        WHERE rc.userId = :userId AND c.playlistId = :playlistId
        ORDER BY rc.watchedTime DESC
        LIMIT :limit
    """)
    suspend fun getRecentForPlaylist(playlistId: Long, userId: String = "default", limit: Int = 20): List<RecentChannelEntity>

    @Query("""
        SELECT * FROM recent_channels
        WHERE userId = :userId AND watchedTime > :cutoffTime
        ORDER BY watchedTime DESC
    """)
    suspend fun getRecentSince(userId: String = "default", cutoffTime: Long): List<RecentChannelEntity>
}
