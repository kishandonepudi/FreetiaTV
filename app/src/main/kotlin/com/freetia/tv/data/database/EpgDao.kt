package com.freetia.tv.data.database

import androidx.room.*
import com.freetia.tv.data.model.EpgChannelEntity
import com.freetia.tv.data.model.EpgProgrammeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpgChannel(channel: EpgChannelEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpgProgrammes(programmes: List<EpgProgrammeEntity>)

    @Update
    suspend fun updateEpgChannel(channel: EpgChannelEntity)

    @Delete
    suspend fun deleteEpgChannel(channel: EpgChannelEntity)

    @Delete
    suspend fun deleteEpgProgramme(programme: EpgProgrammeEntity)

    @Query("SELECT * FROM epg_channels WHERE channelId = :channelId LIMIT 1")
    suspend fun getEpgChannel(channelId: Long): EpgChannelEntity?

    @Query("SELECT * FROM epg_channels WHERE epgChannelId = :epgChannelId LIMIT 1")
    suspend fun getEpgChannelByTvgId(epgChannelId: String): EpgChannelEntity?

    @Query("SELECT * FROM epg_channels")
    fun getEpgChannelsFlow(): Flow<List<EpgChannelEntity>>

    @Query("""
        SELECT ep.* FROM epg_programmes ep
        WHERE ep.epgChannelId = :epgChannelId
        AND ep.start <= :now AND ep.stop > :now
        LIMIT 1
    """)
    suspend fun getNowPlaying(epgChannelId: Long, now: Long = System.currentTimeMillis()): EpgProgrammeEntity?

    @Query("""
        SELECT ep.* FROM epg_programmes ep
        WHERE ep.epgChannelId = :epgChannelId
        AND ep.start > :now
        ORDER BY ep.start ASC
        LIMIT 1
    """)
    suspend fun getNextProgramme(epgChannelId: Long, now: Long = System.currentTimeMillis()): EpgProgrammeEntity?

    @Query("""
        SELECT ep.* FROM epg_programmes ep
        WHERE ep.epgChannelId = :epgChannelId
        AND ep.start >= :startTime AND ep.start < :endTime
        ORDER BY ep.start ASC
    """)
    suspend fun getProgrammesByTimeRange(
        epgChannelId: Long,
        startTime: Long,
        endTime: Long
    ): List<EpgProgrammeEntity>

    @Query("""
        SELECT ep.* FROM epg_programmes ep
        JOIN epg_channels ec ON ep.epgChannelId = ec.id
        WHERE ep.start >= :startTime AND ep.start < :endTime
        ORDER BY ec.channelName ASC, ep.start ASC
    """)
    suspend fun getGridView(startTime: Long, endTime: Long): List<EpgProgrammeEntity>

    @Query("""
        SELECT ep.* FROM epg_programmes ep
        WHERE ep.title LIKE :query OR ep.description LIKE :query
        ORDER BY ep.start DESC
    """)
    suspend fun searchProgrammes(query: String): List<EpgProgrammeEntity>

    @Query("""
        SELECT ep.* FROM epg_programmes ep
        WHERE ep.epgChannelId = :epgChannelId
        ORDER BY ep.start DESC
        LIMIT :limit
    """)
    suspend fun getLatestProgrammes(epgChannelId: Long, limit: Int = 100): List<EpgProgrammeEntity>

    @Query("DELETE FROM epg_programmes WHERE epgChannelId = :epgChannelId")
    suspend fun deleteChannelProgrammes(epgChannelId: Long)

    @Query("""
        DELETE FROM epg_programmes
        WHERE stop < :cutoffTime
    """)
    suspend fun deletePastProgrammes(cutoffTime: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM epg_programmes WHERE epgChannelId = :epgChannelId")
    suspend fun getProgrammeCount(epgChannelId: Long): Int

    @Query("""
        SELECT DISTINCT ep.category FROM epg_programmes ep
        WHERE ep.category != '' AND ep.category IS NOT NULL
        ORDER BY ep.category ASC
    """)
    suspend fun getCategories(): List<String>

    @Query("""
        SELECT ep.* FROM epg_programmes ep
        WHERE ep.category LIKE :category
        ORDER BY ep.start DESC
    """)
    suspend fun getProgrammesByCategory(category: String): List<EpgProgrammeEntity>

    @Query("DELETE FROM epg_channels WHERE channelId = :channelId")
    suspend fun deleteEpgChannelByChannelId(channelId: Long)

    @Query("DELETE FROM epg_programmes")
    suspend fun deleteAllProgrammes()

    @Query("DELETE FROM epg_channels")
    suspend fun deleteAllEpgChannels()
}
