package com.freetia.tv.data.database

import androidx.room.*
import com.freetia.tv.data.model.CategoryLockEntity
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.LockedChannelEntity
import kotlinx.coroutines.flow.Flow

/**
 * Channel locks (§3.8): individual channels plus bulk locks by group category or region. A channel is locked if any
 * of the three match; [isChannelLocked] and [observeLockedIds] combine them in SQL.
 */
@Dao
interface LockedChannelDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(lockedChannel: LockedChannelEntity): Long

    @Query("DELETE FROM locked_channels WHERE channelId = :channelId")
    suspend fun unlockChannel(channelId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategoryLock(lock: CategoryLockEntity): Long

    @Query("DELETE FROM category_locks WHERE scope = :scope AND value = :value COLLATE NOCASE")
    suspend fun removeCategoryLock(scope: String, value: String)

    @Query("SELECT * FROM category_locks ORDER BY scope ASC, value ASC")
    fun observeCategoryLocks(): Flow<List<CategoryLockEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM locked_channels WHERE channelId = :channelId)")
    suspend fun isIndividuallyLocked(channelId: Long): Boolean

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM channels c WHERE c.id = :channelId AND (
                EXISTS(SELECT 1 FROM locked_channels lc WHERE lc.channelId = c.id)
                OR EXISTS(SELECT 1 FROM category_locks l WHERE
                    (l.scope = 'group' AND (';' || REPLACE(c.groupTitle, '; ', ';') || ';') LIKE ('%;' || l.value || ';%'))
                    OR (l.scope = 'region' AND (';' || c.tvgCountry || ';') LIKE ('%;' || l.value || ';%')))
            )
        )
    """)
    suspend fun isChannelLocked(channelId: Long): Boolean

    /** Ids of every locked channel in a playlist, for padlocks and for skipping in channel up/down. */
    @Query("""
        SELECT c.id FROM channels c WHERE c.playlistId = :playlistId AND (
            EXISTS(SELECT 1 FROM locked_channels lc WHERE lc.channelId = c.id)
            OR EXISTS(SELECT 1 FROM category_locks l WHERE
                (l.scope = 'group' AND (';' || REPLACE(c.groupTitle, '; ', ';') || ';') LIKE ('%;' || l.value || ';%'))
                OR (l.scope = 'region' AND (';' || c.tvgCountry || ';') LIKE ('%;' || l.value || ';%')))
        )
    """)
    fun observeLockedIds(playlistId: Long): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM locked_channels")
    suspend fun getLockedCount(): Int

    @Query("DELETE FROM locked_channels")
    suspend fun unlockAllChannels()

    @Query("DELETE FROM category_locks")
    suspend fun removeAllCategoryLocks()

    @Query("SELECT c.* FROM locked_channels l JOIN channels c ON c.id = l.channelId LIMIT :limit")
    suspend fun getLockedChannels(limit: Int = 5_000): List<ChannelEntity>

    @Query("SELECT * FROM category_locks LIMIT 1000")
    suspend fun getCategoryLocks(): List<CategoryLockEntity>
}
