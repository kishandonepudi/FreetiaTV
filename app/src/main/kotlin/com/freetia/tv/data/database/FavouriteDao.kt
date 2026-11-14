package com.freetia.tv.data.database

import androidx.room.*
import com.freetia.tv.data.model.FavouriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavouriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favourite: FavouriteEntity): Long

    @Delete
    suspend fun delete(favourite: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE channelId = :channelId AND userId = :userId")
    suspend fun deleteByChannelAndUser(channelId: Long, userId: String)

    @Query("""
        SELECT f.* FROM favourites f
        JOIN channels c ON f.channelId = c.id
        WHERE f.userId = :userId
        ORDER BY f.addedTime DESC
    """)
    fun getFavouritesFlow(userId: String = "default"): Flow<List<FavouriteEntity>>

    @Query("""
        SELECT f.* FROM favourites f
        JOIN channels c ON f.channelId = c.id
        WHERE f.userId = :userId
        ORDER BY f.addedTime DESC
    """)
    suspend fun getFavourites(userId: String = "default"): List<FavouriteEntity>

    @Query("""
        SELECT f.* FROM favourites f
        WHERE f.channelId = :channelId AND f.userId = :userId
        LIMIT 1
    """)
    suspend fun isFavourite(channelId: Long, userId: String = "default"): FavouriteEntity?

    @Query("""
        SELECT f.* FROM favourites f
        WHERE f.userId = :userId AND f.channelId IN (
            SELECT id FROM channels WHERE playlistId = :playlistId
        )
        ORDER BY f.addedTime DESC
    """)
    suspend fun getFavouritesByPlaylist(playlistId: Long, userId: String = "default"): List<FavouriteEntity>

    @Query("SELECT COUNT(*) FROM favourites WHERE userId = :userId")
    suspend fun getFavouriteCount(userId: String = "default"): Int

    @Query("DELETE FROM favourites WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("""
        SELECT COUNT(*) FROM favourites
        WHERE channelId = :channelId AND userId = :userId
    """)
    suspend fun isFavourited(channelId: Long, userId: String = "default"): Boolean
}
