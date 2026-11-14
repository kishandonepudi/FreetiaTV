package com.freetia.tv.data.database

import androidx.room.*
import com.freetia.tv.data.model.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE userId = :userId ORDER BY reminderTime ASC")
    fun getRemindersFlow(userId: String = "default"): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE userId = :userId ORDER BY reminderTime ASC")
    suspend fun getReminders(userId: String = "default"): List<ReminderEntity>

    @Query("""
        SELECT * FROM reminders
        WHERE userId = :userId AND reminderTime > :now AND reminderTime < :endTime
        ORDER BY reminderTime ASC
    """)
    suspend fun getUpcomingReminders(
        userId: String = "default",
        now: Long = System.currentTimeMillis(),
        endTime: Long = System.currentTimeMillis() + 24L * 60 * 60 * 1000
    ): List<ReminderEntity>

    @Query("""
        SELECT * FROM reminders
        WHERE userId = :userId AND reminderTime <= :now AND isNotified = 0
        ORDER BY reminderTime ASC
    """)
    suspend fun getPendingNotifications(userId: String = "default", now: Long = System.currentTimeMillis()): List<ReminderEntity>

    @Query("""
        UPDATE reminders SET isNotified = 1 WHERE id = :id
    """)
    suspend fun markNotified(id: Long)

    @Query("""
        UPDATE reminders SET isWoken = 1 WHERE id = :id
    """)
    suspend fun markWoken(id: Long)

    @Query("""
        SELECT * FROM reminders
        WHERE userId = :userId AND channelId = :channelId
        ORDER BY reminderTime DESC
    """)
    suspend fun getRemindersByChannel(channelId: Long, userId: String = "default"): List<ReminderEntity>

    @Query("SELECT COUNT(*) FROM reminders WHERE userId = :userId")
    suspend fun getReminderCount(userId: String = "default"): Int

    @Query("""
        DELETE FROM reminders
        WHERE userId = :userId AND reminderTime < :cutoffTime
    """)
    suspend fun deletePastReminders(userId: String, cutoffTime: Long = System.currentTimeMillis())

    @Query("""
        SELECT * FROM reminders
        WHERE userId = :userId AND isNotified = 0
        ORDER BY reminderTime ASC
        LIMIT 1
    """)
    suspend fun getNextUnnotifiedReminder(userId: String = "default"): ReminderEntity?

    @Query("DELETE FROM reminders WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
