package com.freetia.tv.data.database

import androidx.paging.PagingSource
import androidx.room.*
import com.freetia.tv.data.model.ChannelEntity
import kotlinx.coroutines.flow.Flow

/**
 * Paging queries take `hideAdult` (Child Safe Mode) and `hideDead` (HIDDEN_DEAD_STREAMS) so filtering happens in
 * SQL, never by loading a whole playlist into memory.
 */
@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(channel: ChannelEntity): Long

    @Update
    suspend fun update(channel: ChannelEntity)

    @Delete
    suspend fun delete(channel: ChannelEntity)

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getById(id: Long): ChannelEntity?

    @Query("SELECT * FROM channels WHERE id = :id")
    fun observeById(id: Long): Flow<ChannelEntity?>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY number ASC, name ASC")
    fun getByPlaylistFlow(playlistId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY number ASC, name ASC")
    suspend fun getByPlaylist(playlistId: Long): List<ChannelEntity>

    @Query("""
        SELECT id, number, name, groupTitle, tvgCountry, tvgLanguage, tvgId, epgChannelId, isAdult, isMarkedAdult,
        isDead, catchup, catchupEnabled, catchupDays
        FROM channels WHERE playlistId = :playlistId ORDER BY number ASC, name ASC
    """)
    fun observeRefs(playlistId: Long): Flow<List<ChannelRef>>

    @Query("SELECT id, tvgId, epgChannelId, tvgName, name FROM channels WHERE playlistId = :playlistId")
    suspend fun getEpgMatchRows(playlistId: Long): List<EpgMatchRow>

    @Query("""
        SELECT * FROM channels
        WHERE playlistId = :playlistId
        AND (:hideAdult = 0 OR (isAdult = 0 AND isMarkedAdult = 0))
        AND (:hideDead = 0 OR isDead = 0)
        ORDER BY number ASC, name ASC
    """)
    fun pageByPlaylist(playlistId: Long, hideAdult: Boolean, hideDead: Boolean): PagingSource<Int, ChannelEntity>

    /** Merged "All" view in playlist priority order (§3.1). */
    @Query("""
        SELECT c.* FROM channels c JOIN playlists p ON p.id = c.playlistId
        WHERE (:hideAdult = 0 OR (c.isAdult = 0 AND c.isMarkedAdult = 0))
        AND (:hideDead = 0 OR c.isDead = 0)
        ORDER BY p.priority ASC, c.number ASC, c.name ASC
    """)
    fun pageAll(hideAdult: Boolean, hideDead: Boolean): PagingSource<Int, ChannelEntity>

    /** [category] is one segment of group-title; matches channels whose ';'-joined group-title contains it. */
    @Query("""
        SELECT * FROM channels
        WHERE playlistId = :playlistId
        AND (';' || REPLACE(groupTitle, '; ', ';') || ';') LIKE ('%;' || :category || ';%')
        AND (:hideAdult = 0 OR (isAdult = 0 AND isMarkedAdult = 0))
        AND (:hideDead = 0 OR isDead = 0)
        ORDER BY number ASC, name ASC
    """)
    fun pageByCategory(playlistId: Long, category: String, hideAdult: Boolean, hideDead: Boolean): PagingSource<Int, ChannelEntity>

    @Query("""
        SELECT * FROM channels
        WHERE playlistId = :playlistId
        AND (';' || tvgCountry || ';') LIKE ('%;' || :region || ';%')
        AND (:hideAdult = 0 OR (isAdult = 0 AND isMarkedAdult = 0))
        AND (:hideDead = 0 OR isDead = 0)
        ORDER BY number ASC, name ASC
    """)
    fun pageByRegion(playlistId: Long, region: String, hideAdult: Boolean, hideDead: Boolean): PagingSource<Int, ChannelEntity>

    /** Pass [playlistId] 0 to search every playlist. [query] is a raw term; the % wrapping happens here. */
    @Query("""
        SELECT c.* FROM channels c JOIN playlists p ON p.id = c.playlistId
        WHERE (:playlistId = 0 OR c.playlistId = :playlistId)
        AND (c.name LIKE '%' || :query || '%' OR c.tvgName LIKE '%' || :query || '%' OR c.groupTitle LIKE '%' || :query || '%')
        AND (:hideAdult = 0 OR (c.isAdult = 0 AND c.isMarkedAdult = 0))
        AND (:hideDead = 0 OR c.isDead = 0)
        ORDER BY p.priority ASC, c.number ASC, c.name ASC
    """)
    fun pageSearch(query: String, playlistId: Long, hideAdult: Boolean, hideDead: Boolean): PagingSource<Int, ChannelEntity>

    @Query("""
        SELECT c.* FROM channels c JOIN playlists p ON p.id = c.playlistId
        WHERE (:playlistId = 0 OR c.playlistId = :playlistId)
        AND (c.name LIKE '%' || :query || '%' OR c.tvgName LIKE '%' || :query || '%')
        AND (:hideAdult = 0 OR (c.isAdult = 0 AND c.isMarkedAdult = 0))
        ORDER BY p.priority ASC, c.number ASC
        LIMIT :limit
    """)
    suspend fun search(query: String, playlistId: Long, hideAdult: Boolean, limit: Int = 50): List<ChannelEntity>

    @Query("""
        SELECT DISTINCT groupTitle FROM channels
        WHERE playlistId = :playlistId AND groupTitle != ''
        AND (:hideAdult = 0 OR (isAdult = 0 AND isMarkedAdult = 0))
        ORDER BY groupTitle ASC
    """)
    suspend fun getGroups(playlistId: Long, hideAdult: Boolean): List<String>

    /** Raw distinct tvg-country values (may contain ';'); callers split and de-duplicate. 0 = all playlists. */
    @Query("""
        SELECT DISTINCT tvgCountry FROM channels
        WHERE (:playlistId = 0 OR playlistId = :playlistId) AND tvgCountry != ''
        ORDER BY tvgCountry ASC
    """)
    suspend fun getCountries(playlistId: Long = 0): List<String>

    @Query("""
        SELECT DISTINCT tvgLanguage FROM channels
        WHERE (:playlistId = 0 OR playlistId = :playlistId) AND tvgLanguage != ''
        ORDER BY tvgLanguage ASC
    """)
    suspend fun getLanguages(playlistId: Long = 0): List<String>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND number = :number LIMIT 1")
    suspend fun getByNumber(playlistId: Long, number: Int): ChannelEntity?

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY number ASC, name ASC LIMIT 1")
    suspend fun getFirst(playlistId: Long): ChannelEntity?

    @Query("SELECT * FROM channels WHERE tvgId = :tvgId AND playlistId = :playlistId LIMIT 1")
    suspend fun getByTvgId(tvgId: String, playlistId: Long): ChannelEntity?

    @Query("SELECT * FROM channels WHERE name = :name AND playlistId = :playlistId LIMIT 1")
    suspend fun getByName(name: String, playlistId: Long): ChannelEntity?

    @Query("UPDATE channels SET preferredAudioLanguage = :language WHERE id = :id")
    suspend fun updatePreferredAudio(id: Long, language: String)

    @Query("UPDATE channels SET isCaptionsEnabled = :enabled WHERE id = :id")
    suspend fun updateCaptionsEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE channels SET lastPlayedTime = :time WHERE id = :id")
    suspend fun updateLastPlayTime(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE channels SET isMarkedAdult = :marked WHERE id = :id")
    suspend fun markAsAdult(id: Long, marked: Boolean)

    @Query("UPDATE channels SET isDead = :dead WHERE id = :id")
    suspend fun setDead(id: Long, dead: Boolean)

    @Query("UPDATE channels SET isDead = :dead WHERE id IN (:ids)")
    suspend fun setDead(ids: List<Long>, dead: Boolean)

    @Query("UPDATE channels SET isDead = 0 WHERE playlistId = :playlistId")
    suspend fun clearDead(playlistId: Long)

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    suspend fun getChannelCountForPlaylist(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    fun observeCount(playlistId: Long): Flow<Int>
}

/**
 * The slim row the session list, the player's Up/Down and the guide hold for a whole playlist: about a fifth of a
 * ChannelEntity (no stream URL, logo or per-channel prefs). Resolve the full row with `getById`/`observeById`.
 */
data class ChannelRef(
    val id: Long,
    val number: Int = 0,
    val name: String = "",
    val groupTitle: String = "",
    val tvgCountry: String = "",
    val tvgLanguage: String = "",
    val tvgId: String = "",
    val epgChannelId: String = "",
    val isAdult: Boolean = false,
    val isMarkedAdult: Boolean = false,
    val isDead: Boolean = false,
    val catchup: String = "",
    val catchupEnabled: Boolean = false,
    val catchupDays: Int = 0,
)

/** Only the columns the XMLTV importer matches on. */
data class EpgMatchRow(val id: Long, val tvgId: String, val epgChannelId: String, val tvgName: String, val name: String)
