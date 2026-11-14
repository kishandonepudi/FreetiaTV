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

    /**
     * EPG grid rows: programmes overlapping [from, to) for one page of channels (by tvg-id). The grid pages its
     * channel rows and asks for each visible window, so 7 days × 5,000 channels is never loaded at once.
     */
    @Query("""
        SELECT * FROM epg_programmes
        WHERE tvgId IN (:tvgIds) AND stop > :from AND start < :to
        ORDER BY tvgId ASC, start ASC
        LIMIT :limit
    """)
    suspend fun getGridWindow(tvgIds: List<String>, from: Long, to: Long, limit: Int = 2_000): List<EpgProgrammeEntity>

    /** §3.9 search by programme title or genre; raw term, % wrapping here. Only programmes not yet ended. */
    @Query("""
        SELECT * FROM epg_programmes
        WHERE (title LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%') AND stop > :now
        ORDER BY start ASC
        LIMIT :limit
    """)
    suspend fun searchProgrammes(query: String, now: Long, limit: Int = 100): List<EpgProgrammeEntity>

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
        WHERE ep.category LIKE :category AND ep.stop > :now
        ORDER BY ep.start ASC
        LIMIT :limit
    """)
    suspend fun getProgrammesByCategory(category: String, now: Long, limit: Int = 100): List<EpgProgrammeEntity>

    @Query("DELETE FROM epg_channels WHERE channelId = :channelId")
    suspend fun deleteEpgChannelByChannelId(channelId: Long)

    @Query("DELETE FROM epg_programmes")
    suspend fun deleteAllProgrammes()

    @Query("DELETE FROM epg_channels")
    suspend fun deleteAllEpgChannels()

    /** Programmes airing in [from, to) for a playlist channel, matched by channel id or its tvg-id. */
    @Query("""
        SELECT ep.* FROM epg_programmes ep
        JOIN epg_channels ec ON ep.epgChannelId = ec.id
        WHERE (ec.channelId = :channelId OR (:tvgId != '' AND ec.epgChannelId = :tvgId))
        AND ep.stop > :from AND ep.start < :to
        ORDER BY ep.start ASC
        LIMIT :limit
    """)
    fun observeSchedule(channelId: Long, tvgId: String, from: Long, to: Long, limit: Int = 4): Flow<List<EpgProgrammeEntity>>

    @Query("SELECT channelId, epgChannelId FROM epg_channels WHERE channelId IN (:channelIds) LIMIT 1000")
    suspend fun getEpgRefs(channelIds: List<Long>): List<EpgChannelRef>

    @Query("""
        SELECT ec.* FROM epg_channels ec JOIN channels c ON ec.channelId = c.id
        WHERE c.playlistId = :playlistId
        LIMIT 100000
    """)
    suspend fun getEpgChannelsForPlaylist(playlistId: Long): List<EpgChannelEntity>

    /**
     * §3.9 search by programme title, genre or channel name, not yet ended. [query] must be LIKE-escaped with `\`.
     * Child Safe, dead-stream and region filters mirror ChannelFilter / RegionFilter (country and language are
     * upper/lower-case codes; channels with no tag are kept).
     */
    @Query("""
        SELECT ep.id AS programmeId, ep.title AS title, ep.category AS category, ep.description AS description,
               ep.start AS start, ep.stop AS stop, c.id AS channelId, c.name AS channelName
        FROM epg_programmes ep
        JOIN epg_channels ec ON ep.epgChannelId = ec.id
        JOIN channels c ON ec.channelId = c.id
        WHERE ep.stop > :now
        AND (:playlistId = 0 OR c.playlistId = :playlistId)
        AND (:hideAdult = 0 OR (c.isAdult = 0 AND c.isMarkedAdult = 0))
        AND (:hideDead = 0 OR c.isDead = 0)
        AND (:country = '' OR c.tvgCountry = '' OR instr(';' || upper(replace(replace(c.tvgCountry, ',', ';'), '|', ';')) || ';', ';' || :country || ';') > 0)
        AND (:language = '' OR c.tvgLanguage = '' OR instr(';' || lower(replace(replace(c.tvgLanguage, ',', ';'), '|', ';')) || ';', ';' || :language || ';') > 0)
        AND (ep.title LIKE '%' || :query || '%' ESCAPE '\' OR ep.category LIKE '%' || :query || '%' ESCAPE '\' OR c.name LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY CASE WHEN ep.start <= :now THEN 0 ELSE 1 END, ep.start ASC
        LIMIT :limit
    """)
    suspend fun searchGuide(
        query: String, now: Long, playlistId: Long, hideAdult: Boolean, hideDead: Boolean,
        country: String, language: String, limit: Int = 100,
    ): List<EpgSearchRow>

    @Query("SELECT EXISTS(SELECT 1 FROM epg_programmes WHERE stop > :now)")
    suspend fun hasUpcoming(now: Long): Boolean
}

/** Guide lookup row: which XMLTV channel id a playlist channel resolves to. */
data class EpgChannelRef(val channelId: Long, val epgChannelId: String)

/** One EPG search hit with the channel it airs on. */
data class EpgSearchRow(
    val programmeId: Long,
    val title: String,
    val category: String,
    val description: String,
    val start: Long,
    val stop: Long,
    val channelId: Long,
    val channelName: String,
)
