package com.freetia.tv.data.sync

import androidx.room.withTransaction
import com.freetia.tv.data.database.ChannelSourceUpdate
import com.freetia.tv.data.database.FreetiaDatabase
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.PlaylistEntity
import com.freetia.tv.data.parser.M3uChannel
import com.freetia.tv.data.parser.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

sealed class SyncResult {
    data class Updated(val added: Int, val removed: Int, val total: Int) : SyncResult()
    data class Skipped(val reason: String) : SyncResult()
    data class Failed(val message: String) : SyncResult()
}

class PlaylistSyncer(
    private val db: FreetiaDatabase,
    private val http: OkHttpClient,
    private val parser: M3uParser = M3uParser(),
) {
    private val syncDao = db.playlistSyncDao()
    private val playlistDao = db.playlistDao()

    suspend fun sync(playlist: PlaylistEntity, force: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        val sinceLast = System.currentTimeMillis() - playlist.lastUpdateTime
        if (!force && playlist.lastUpdateSuccess && sinceLast in 0 until MIN_REFRESH_INTERVAL_MS) {
            return@withContext SyncResult.Skipped("Updated ${sinceLast / 60_000} min ago")
        }
        try {
            val request = Request.Builder().url(playlist.sourceUrl).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty response")
                val result = db.withTransaction { apply(playlist.id, body.byteStream()) }
                playlistDao.markUpdateSuccess(playlist.id)
                result
            }
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            playlistDao.markUpdateFailure(playlist.id, message)
            SyncResult.Failed(message)
        }
    }

    private suspend fun apply(playlistId: Long, stream: java.io.InputStream): SyncResult.Updated {
        val existing = syncDao.existingKeys(playlistId).associate { it.streamUrl to it.id }.toMutableMap()
        val toInsert = ArrayList<ChannelEntity>(BATCH)
        val toUpdate = ArrayList<ChannelSourceUpdate>(BATCH)
        var added = 0

        val total = parser.parse(
            stream,
            onHeader = { if (it.tvgUrl.isNotBlank()) syncDao.setEpgUrlIfEmpty(playlistId, it.tvgUrl) },
        ) { ch ->
            val id = existing.remove(ch.streamUrl)
            if (id != null) {
                toUpdate += ch.toSourceUpdate(id)
                if (toUpdate.size >= BATCH) { syncDao.updateSource(toUpdate); toUpdate.clear() }
            } else {
                toInsert += ch.toEntity(playlistId)
                added++
                if (toInsert.size >= BATCH) { syncDao.insertAll(toInsert); toInsert.clear() }
            }
        }
        if (toUpdate.isNotEmpty()) syncDao.updateSource(toUpdate)
        if (toInsert.isNotEmpty()) syncDao.insertAll(toInsert)
        // Keep the cached list if the server returned an empty/garbled playlist.
        if (total == 0) throw IOException("Playlist contained no channels")
        existing.values.chunked(BATCH).forEach { syncDao.deleteByIds(it) }
        return SyncResult.Updated(added = added, removed = existing.size, total = total)
    }

    private fun M3uChannel.isAdult(): Boolean {
        val haystack = "$groupTitle $name".lowercase()
        return ADULT_KEYWORDS.any { it in haystack }
    }

    private fun M3uChannel.toEntity(playlistId: Long) = ChannelEntity(
        playlistId = playlistId, tvgId = tvgId, tvgName = tvgName, tvgLogo = tvgLogo, name = name,
        number = number, streamUrl = streamUrl, groupTitle = groupTitle, tvgCountry = tvgCountry,
        tvgLanguage = tvgLanguage, tvgType = tvgType, catchupEnabled = catchupEnabled,
        catchupDays = catchupDays, epgChannelId = tvgId, isAdult = isAdult(),
    )

    private fun M3uChannel.toSourceUpdate(id: Long) = ChannelSourceUpdate(
        id = id, tvgId = tvgId, tvgName = tvgName, tvgLogo = tvgLogo, name = name, number = number,
        groupTitle = groupTitle, tvgCountry = tvgCountry, tvgLanguage = tvgLanguage, tvgType = tvgType,
        catchupEnabled = catchupEnabled, catchupDays = catchupDays, isAdult = isAdult(),
    )

    companion object {
        const val MIN_REFRESH_INTERVAL_MS = 10 * 60_000L
        private const val BATCH = 500
        private val ADULT_KEYWORDS = listOf("adult", "18+", "xxx")
    }
}
