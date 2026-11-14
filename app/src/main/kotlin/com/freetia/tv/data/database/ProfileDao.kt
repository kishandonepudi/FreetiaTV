package com.freetia.tv.data.database

import androidx.room.*
import com.freetia.tv.data.model.DEFAULT_PROFILE_ID
import com.freetia.tv.data.model.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    /** The default profile is never deleted; deleting another cascades its favourites, recents and groups. */
    @Query("DELETE FROM profiles WHERE id = :id AND id != $DEFAULT_PROFILE_ID")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observeById(id: Long): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles ORDER BY id ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int
}
