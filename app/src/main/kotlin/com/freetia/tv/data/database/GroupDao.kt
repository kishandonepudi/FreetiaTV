package com.freetia.tv.data.database

import androidx.room.*
import com.freetia.tv.data.model.GroupEntity
import com.freetia.tv.data.model.GroupChannelCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity): Long

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Delete
    suspend fun deleteGroup(group: GroupEntity)

    @Query("DELETE FROM user_groups WHERE id = :id")
    suspend fun deleteGroupById(id: Long)

    @Query("SELECT * FROM user_groups WHERE id = :id")
    suspend fun getGroupById(id: Long): GroupEntity?

    @Query("SELECT * FROM user_groups WHERE userId = :userId ORDER BY name ASC")
    fun getGroupsFlow(userId: String = "default"): Flow<List<GroupEntity>>

    @Query("SELECT * FROM user_groups WHERE userId = :userId ORDER BY name ASC")
    suspend fun getGroups(userId: String = "default"): List<GroupEntity>

    @Query("SELECT * FROM user_groups WHERE userId = :userId AND name LIKE :query ORDER BY name ASC")
    suspend fun searchGroups(query: String, userId: String = "default"): List<GroupEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addChannelToGroup(crossRef: GroupChannelCrossRef)

    @Query("DELETE FROM group_channels WHERE groupId = :groupId AND channelId = :channelId")
    suspend fun removeChannelFromGroup(groupId: Long, channelId: Long)

    @Query("DELETE FROM group_channels WHERE groupId = :groupId")
    suspend fun removeAllChannelsFromGroup(groupId: Long)

    @Query("""
        SELECT c.id FROM channels c
        JOIN group_channels gc ON c.id = gc.channelId
        WHERE gc.groupId = :groupId
        ORDER BY c.number ASC
    """)
    suspend fun getChannelsInGroup(groupId: Long): List<Long>

    @Query("""
        SELECT COUNT(*) FROM group_channels
        WHERE groupId = :groupId
    """)
    suspend fun getGroupChannelCount(groupId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM group_channels
        WHERE groupId = :groupId AND channelId = :channelId
    """)
    suspend fun isChannelInGroup(groupId: Long, channelId: Long): Boolean

    @Query("DELETE FROM user_groups WHERE userId = :userId")
    suspend fun deleteAllGroupsForUser(userId: String)

    @Query("SELECT COUNT(*) FROM user_groups WHERE userId = :userId")
    suspend fun getGroupCount(userId: String = "default"): Int

    @Query("""
        SELECT g.* FROM user_groups g
        WHERE g.userId = :userId AND EXISTS (
            SELECT 1 FROM group_channels gc
            WHERE gc.groupId = g.id AND gc.channelId = :channelId
        )
    """)
    suspend fun getGroupsContainingChannel(channelId: Long, userId: String = "default"): List<GroupEntity>
}
