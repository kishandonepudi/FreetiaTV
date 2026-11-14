package com.freetia.tv.data.database

import androidx.room.*
import com.freetia.tv.data.model.PlaylistHealthCheckEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistHealthCheckDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(healthCheck: PlaylistHealthCheckEntity): Long

    @Update
    suspend fun update(healthCheck: PlaylistHealthCheckEntity)

    @Delete
    suspend fun delete(healthCheck: PlaylistHealthCheckEntity)

    @Query("SELECT * FROM health_checks WHERE channelId = :channelId LIMIT 1")
    suspend fun getByChannelId(channelId: Long): PlaylistHealthCheckEntity?

    @Query("SELECT * FROM health_checks ORDER BY lastCheckTime DESC")
    fun getAllFlow(): Flow<List<PlaylistHealthCheckEntity>>

    @Query("SELECT * FROM health_checks ORDER BY lastCheckTime DESC")
    suspend fun getAll(): List<PlaylistHealthCheckEntity>

    @Query("""
        SELECT hc.* FROM health_checks hc
        JOIN channels c ON hc.channelId = c.id
        WHERE c.playlistId = :playlistId AND hc.isHealthy = 0
        ORDER BY hc.lastCheckTime DESC
    """)
    suspend fun getUnhealthyChannelsForPlaylist(playlistId: Long): List<PlaylistHealthCheckEntity>

    @Query("""
        SELECT COUNT(*) FROM health_checks
        WHERE isHealthy = 0
    """)
    suspend fun getUnhealthyCount(): Int

    @Query("""
        UPDATE health_checks
        SET isHealthy = 1, lastCheckTime = :time, errorMessage = ''
        WHERE channelId = :channelId
    """)
    suspend fun markHealthy(channelId: Long, time: Long = System.currentTimeMillis())

    @Query("""
        UPDATE health_checks
        SET isHealthy = 0, lastCheckTime = :time, errorMessage = :error
        WHERE channelId = :channelId
    """)
    suspend fun markUnhealthy(channelId: Long, error: String, time: Long = System.currentTimeMillis())

    @Query("""
        DELETE FROM health_checks
        WHERE lastCheckTime < :cutoffTime
    """)
    suspend fun deleteOldChecks(cutoffTime: Long = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)

    @Query("""
        SELECT hc.* FROM health_checks hc
        WHERE hc.lastCheckTime < :checkBefore
        ORDER BY hc.lastCheckTime ASC
        LIMIT :limit
    """)
    suspend fun getChannelsNeedingCheck(checkBefore: Long, limit: Int = 100): List<PlaylistHealthCheckEntity>

    @Query("DELETE FROM health_checks WHERE channelId = :channelId")
    suspend fun deleteByChannelId(channelId: Long)

    @Query("""
        SELECT COUNT(*) FROM health_checks
        WHERE isHealthy = 1
    """)
    suspend fun getHealthyCount(): Int
}
