package com.freetia.tv.data.database

import androidx.room.*
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.RecentChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentChannelDao {
    // REPLACE is safe here: nothing references recent_channels, and it re-dates an existing entry.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recent: RecentChannelEntity): Long

    /** Records a watch and keeps only the newest [keep] entries for the profile (§3.3: last 20). */
    @Transaction
    suspend fun record(channelId: Long, profileId: Long, time: Long = System.currentTimeMillis(), keep: Int = MAX_RECENTS) {
        insert(RecentChannelEntity(channelId = channelId, profileId = profileId, watchedTime = time))
        trim(profileId, keep)
    }

    @Query("""
        DELETE FROM recent_channels
        WHERE profileId = :profileId AND id NOT IN (
            SELECT id FROM recent_channels WHERE profileId = :profileId
            ORDER BY watchedTime DESC, id DESC LIMIT :keep
        )
    """)
    suspend fun trim(profileId: Long, keep: Int = MAX_RECENTS)

    @Query("""
        SELECT c.* FROM channels c JOIN recent_channels r ON r.channelId = c.id
        WHERE r.profileId = :profileId
        AND (:hideAdult = 0 OR (c.isAdult = 0 AND c.isMarkedAdult = 0))
        ORDER BY r.watchedTime DESC, r.id DESC
        LIMIT :limit
    """)
    fun observeRecentChannels(profileId: Long, hideAdult: Boolean, limit: Int = MAX_RECENTS): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM recent_channels WHERE profileId = :profileId ORDER BY watchedTime DESC, id DESC LIMIT :limit")
    suspend fun getRecent(profileId: Long, limit: Int = MAX_RECENTS): List<RecentChannelEntity>

    @Query("DELETE FROM recent_channels WHERE channelId = :channelId AND profileId = :profileId")
    suspend fun remove(channelId: Long, profileId: Long)

    @Query("DELETE FROM recent_channels WHERE profileId = :profileId")
    suspend fun clearAllForProfile(profileId: Long)

    @Query("SELECT COUNT(*) FROM recent_channels WHERE profileId = :profileId")
    suspend fun count(profileId: Long): Int

    companion object {
        const val MAX_RECENTS = 20
    }
}
