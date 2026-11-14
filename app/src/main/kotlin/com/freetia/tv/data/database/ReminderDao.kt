package com.freetia.tv.data.database

import androidx.room.*
import com.freetia.tv.data.model.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    /** One reminder per channel + start time; setting it again replaces the flags and lead time. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity): Long

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reminders WHERE channelId = :channelId AND startTime = :startTime")
    suspend fun deleteForProgramme(channelId: Long, startTime: Long)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE channelId = :channelId AND startTime = :startTime LIMIT 1")
    suspend fun findForProgramme(channelId: Long, startTime: Long): ReminderEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM reminders WHERE channelId = :channelId AND startTime = :startTime)")
    fun observeHasReminder(channelId: Long, startTime: Long): Flow<Boolean>

    /** Settings > Reminders list: programmes that have not ended yet. */
    @Query("SELECT * FROM reminders WHERE stopTime = 0 OR stopTime > :now ORDER BY reminderTime ASC LIMIT :limit")
    fun observeUpcoming(now: Long, limit: Int = 200): Flow<List<ReminderEntity>>

    /** Reminders to (re)schedule with AlarmManager, e.g. after boot. */
    @Query("SELECT * FROM reminders WHERE isNotified = 0 AND reminderTime > :now ORDER BY reminderTime ASC LIMIT :limit")
    suspend fun getFuture(now: Long, limit: Int = 200): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE isNotified = 0 AND reminderTime <= :now ORDER BY reminderTime ASC LIMIT :limit")
    suspend fun getDue(now: Long, limit: Int = 50): List<ReminderEntity>

    @Query("UPDATE reminders SET isNotified = 1 WHERE id = :id")
    suspend fun markNotified(id: Long)

    @Query("UPDATE reminders SET isWoken = 1 WHERE id = :id")
    suspend fun markWoken(id: Long)

    @Query("DELETE FROM reminders WHERE stopTime != 0 AND stopTime < :cutoffTime")
    suspend fun deleteEnded(cutoffTime: Long)
}
