package com.freetia.tv.data.database

import androidx.room.*
import com.freetia.tv.data.model.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(channel: ChannelEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Update
    suspend fun update(channel: ChannelEntity)

    @Delete
    suspend fun delete(channel: ChannelEntity)

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getById(id: Long): ChannelEntity?

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY number ASC, name ASC")
    fun getByPlaylistFlow(playlistId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY number ASC, name ASC")
    suspend fun getByPlaylist(playlistId: Long): List<ChannelEntity>

    @Query("""
        SELECT * FROM channels
        WHERE playlistId IN (SELECT id FROM playlists ORDER BY priority ASC)
        ORDER BY
            (SELECT priority FROM playlists WHERE playlists.id = channels.playlistId) ASC,
            number ASC, name ASC
    """)
    fun getAllChannelsFlow(): Flow<List<ChannelEntity>>

    @Query("""
        SELECT * FROM channels
        WHERE playlistId IN (SELECT id FROM playlists ORDER BY priority ASC)
        ORDER BY
            (SELECT priority FROM playlists WHERE playlists.id = channels.playlistId) ASC,
            number ASC, name ASC
    """)
    suspend fun getAllChannels(): List<ChannelEntity>

    @Query("""
        SELECT * FROM channels
        WHERE playlistId = :playlistId AND (name LIKE :query OR tvgId LIKE :query OR tvgName LIKE :query)
        ORDER BY number ASC
    """)
    suspend fun searchByName(playlistId: Long, query: String): List<ChannelEntity>

    @Query("""
        SELECT * FROM channels
        WHERE playlistId = :playlistId AND groupTitle = :group
        ORDER BY number ASC
    """)
    suspend fun getByGroup(playlistId: Long, group: String): List<ChannelEntity>

    @Query("""
        SELECT DISTINCT groupTitle FROM channels
        WHERE playlistId = :playlistId AND groupTitle != ''
        ORDER BY groupTitle ASC
    """)
    suspend fun getGroups(playlistId: Long): List<String>

    @Query("""
        SELECT * FROM channels
        WHERE playlistId = :playlistId AND tvgCountry LIKE :country
        ORDER BY number ASC
    """)
    suspend fun getByCountry(playlistId: Long, country: String): List<ChannelEntity>

    @Query("""
        SELECT * FROM channels
        WHERE playlistId = :playlistId AND tvgLanguage LIKE :language
        ORDER BY number ASC
    """)
    suspend fun getByLanguage(playlistId: Long, language: String): List<ChannelEntity>

    @Query("""
        SELECT * FROM channels
        WHERE playlistId = :playlistId AND (isAdult = 1 OR isMarkedAdult = 1)
        ORDER BY number ASC
    """)
    suspend fun getAdultChannels(playlistId: Long): List<ChannelEntity>

    @Query("""
        SELECT * FROM channels
        WHERE playlistId = :playlistId AND isAdult = 0 AND isMarkedAdult = 0
        ORDER BY number ASC
    """)
    suspend fun getNonAdultChannels(playlistId: Long): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND number = :number LIMIT 1")
    suspend fun getByNumber(playlistId: Long, number: Int): ChannelEntity?

    @Query("SELECT * FROM channels WHERE tvgId = :tvgId AND playlistId = :playlistId LIMIT 1")
    suspend fun getByTvgId(tvgId: String, playlistId: Long): ChannelEntity?

    @Query("UPDATE channels SET preferredAudioLanguage = :language WHERE id = :id")
    suspend fun updatePreferredAudio(id: Long, language: String)

    @Query("UPDATE channels SET isCaptionsEnabled = :enabled WHERE id = :id")
    suspend fun updateCaptionsEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE channels SET lastPlayedTime = :time WHERE id = :id")
    suspend fun updateLastPlayTime(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE channels SET isMarkedAdult = :marked WHERE id = :id")
    suspend fun markAsAdult(id: Long, marked: Boolean)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    suspend fun getChannelCountForPlaylist(playlistId: Long): Int

    @Query("""
        SELECT * FROM channels
        WHERE playlistId = :playlistId
        ORDER BY lastPlayedTime DESC
        LIMIT :limit
    """)
    suspend fun getMostRecentlyPlayed(playlistId: Long, limit: Int = 20): List<ChannelEntity>
}
