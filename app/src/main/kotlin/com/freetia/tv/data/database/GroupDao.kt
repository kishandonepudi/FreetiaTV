package com.freetia.tv.data.database

import androidx.paging.PagingSource
import androidx.room.*
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.GroupChannelCrossRef
import com.freetia.tv.data.model.GroupEntity
import kotlinx.coroutines.flow.Flow

/** User-created groups ("Kids", "News"…), per profile. */
@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGroup(group: GroupEntity): Long

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Query("DELETE FROM user_groups WHERE id = :id")
    suspend fun deleteGroupById(id: Long)

    @Query("SELECT * FROM user_groups WHERE id = :id")
    suspend fun getGroupById(id: Long): GroupEntity?

    @Query("SELECT * FROM user_groups WHERE profileId = :profileId ORDER BY sortOrder ASC, name ASC")
    fun observeGroups(profileId: Long): Flow<List<GroupEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addChannelToGroup(crossRef: GroupChannelCrossRef)

    @Query("DELETE FROM group_channels WHERE groupId = :groupId AND channelId = :channelId")
    suspend fun removeChannelFromGroup(groupId: Long, channelId: Long)

    @Query("""
        SELECT c.* FROM channels c JOIN group_channels gc ON c.id = gc.channelId
        WHERE gc.groupId = :groupId
        AND (:hideAdult = 0 OR (c.isAdult = 0 AND c.isMarkedAdult = 0))
        ORDER BY c.number ASC, c.name ASC
    """)
    fun pageGroupChannels(groupId: Long, hideAdult: Boolean): PagingSource<Int, ChannelEntity>

    @Query("SELECT COUNT(*) FROM group_channels WHERE groupId = :groupId")
    fun observeGroupChannelCount(groupId: Long): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM group_channels WHERE groupId = :groupId AND channelId = :channelId)")
    suspend fun isChannelInGroup(groupId: Long, channelId: Long): Boolean

    @Query("""
        SELECT g.* FROM user_groups g
        WHERE g.profileId = :profileId AND EXISTS (
            SELECT 1 FROM group_channels gc WHERE gc.groupId = g.id AND gc.channelId = :channelId
        )
        ORDER BY g.name ASC
    """)
    suspend fun getGroupsContainingChannel(channelId: Long, profileId: Long): List<GroupEntity>

    @Query("SELECT COUNT(*) FROM user_groups WHERE profileId = :profileId")
    suspend fun getGroupCount(profileId: Long): Int

    @Query("SELECT c.* FROM group_channels gc JOIN channels c ON c.id = gc.channelId WHERE gc.groupId = :groupId LIMIT :limit")
    suspend fun getGroupChannels(groupId: Long, limit: Int = 5_000): List<ChannelEntity>
}
