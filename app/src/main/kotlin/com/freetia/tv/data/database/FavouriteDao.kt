package com.freetia.tv.data.database

import androidx.paging.PagingSource
import androidx.room.*
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.FavouriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavouriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favourite: FavouriteEntity): Long

    @Query("DELETE FROM favourites WHERE channelId = :channelId AND profileId = :profileId")
    suspend fun remove(channelId: Long, profileId: Long)

    /** Returns true if the channel is now a favourite. */
    @Transaction
    suspend fun toggle(channelId: Long, profileId: Long): Boolean {
        if (isFavourite(channelId, profileId)) {
            remove(channelId, profileId)
            return false
        }
        insert(FavouriteEntity(channelId = channelId, profileId = profileId))
        return true
    }

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE channelId = :channelId AND profileId = :profileId)")
    suspend fun isFavourite(channelId: Long, profileId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE channelId = :channelId AND profileId = :profileId)")
    fun observeIsFavourite(channelId: Long, profileId: Long): Flow<Boolean>

    /** Ids only, for star badges in lists. */
    @Query("SELECT channelId FROM favourites WHERE profileId = :profileId LIMIT :limit")
    fun observeFavouriteIds(profileId: Long, limit: Int = 5_000): Flow<List<Long>>

    @Query("""
        SELECT c.* FROM channels c JOIN favourites f ON f.channelId = c.id
        WHERE f.profileId = :profileId
        AND (:hideAdult = 0 OR (c.isAdult = 0 AND c.isMarkedAdult = 0))
        ORDER BY f.sortOrder ASC, f.addedTime DESC
    """)
    fun pageFavouriteChannels(profileId: Long, hideAdult: Boolean): PagingSource<Int, ChannelEntity>

    @Query("""
        SELECT c.* FROM channels c JOIN favourites f ON f.channelId = c.id
        WHERE f.profileId = :profileId
        AND (:hideAdult = 0 OR (c.isAdult = 0 AND c.isMarkedAdult = 0))
        ORDER BY f.sortOrder ASC, f.addedTime DESC
        LIMIT :limit
    """)
    fun observeFavouriteChannels(profileId: Long, hideAdult: Boolean, limit: Int = 500): Flow<List<ChannelEntity>>

    @Query("UPDATE favourites SET sortOrder = :sortOrder WHERE channelId = :channelId AND profileId = :profileId")
    suspend fun updateSortOrder(channelId: Long, profileId: Long, sortOrder: Int)

    @Query("SELECT COUNT(*) FROM favourites WHERE profileId = :profileId")
    suspend fun count(profileId: Long): Int

    @Query("DELETE FROM favourites WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Long)
}
