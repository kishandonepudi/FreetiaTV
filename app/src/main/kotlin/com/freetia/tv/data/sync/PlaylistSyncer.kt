package com.freetia.tv.data.sync

import androidx.room.withTransaction
import com.freetia.tv.core.safety.ChannelFilter
import com.freetia.tv.data.database.ChannelSourceUpdate
import com.freetia.tv.data.database.FreetiaDatabase
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.PlaylistEntity
import com.freetia.tv.data.parser.M3uChannel
import com.freetia.tv.data.parser.M3uParser
import com.freetia.tv.data.secure.PlaylistUrlStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

sealed class SyncResult {
    data class Updated(val added: Int, val removed: Int, val total: Int) : SyncResult()
    data class Skipped(val reason: String) : SyncResult()
    data class Failed(val message: String) : SyncResult()
}

class PlaylistSyncer(
    private val db: FreetiaDatabase,
    private val http: OkHttpClient,
    private val urls: PlaylistUrlStore,
    private val parser: M3uParser = M3uParser(),
) {
    private val syncDao = db.playlistSyncDao()
    private val playlistDao = db.playlistDao()

    suspend fun sync(playlist: PlaylistEntity, force: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (SyncPolicy.shouldSkip(force, playlist.lastUpdateSuccess, playlist.lastUpdateTime, now)) {
            return@withContext SyncResult.Skipped("Updated ${(now - playlist.lastUpdateTime) / 60_000} min ago")
        }
        val url = urls.get(playlist.sourceUrl)
        if (url == null) {
            playlistDao.markUpdateFailure(playlist.id, "Playlist link missing. Edit the playlist and add it again.")
            SyncEvents.emit(SyncEvent.Failed(playlist.id, playlist.name))
            return@withContext SyncResult.Failed("Playlist link missing")
        }
        try {
            val request = Request.Builder().url(url).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty response")
                if (body.contentLength() > MAX_PLAYLIST_BYTES) throw IOException(TOO_BIG)
                val result = apply(playlist.id, CappedInputStream(body.byteStream(), MAX_PLAYLIST_BYTES))
                playlistDao.markUpdateSuccess(playlist.id)
                SyncEvents.succeeded(playlist.id)
                if (result.added > 0 || result.removed > 0) {
                    SyncEvents.emit(SyncEvent.Updated(playlist.id, playlist.name, result.added, result.removed))
                }
                result
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Some exceptions (e.g. a malformed URL) quote the URL, which may carry a token.
            val message = (e.message ?: e.javaClass.simpleName).replace(url, "playlist link")
            playlistDao.markUpdateFailure(playlist.id, message)
            SyncEvents.emit(SyncEvent.Failed(playlist.id, playlist.name))
            SyncResult.Failed(message)
        }
    }

    /**
     * Download and parse run outside any transaction, so a slow server never holds the database write lock. Small
     * playlists (up to [SINGLE_COMMIT_ROWS]) are written in one short transaction at the end, so lists update once.
     * Larger ones are flushed in [BATCH]-row transactions while parsing; removing vanished channels stays atomic in
     * the final transaction.
     */
    private suspend fun apply(playlistId: Long, stream: InputStream): SyncResult.Updated {
        val diff = ChannelDiff(syncDao.existingKeys(playlistId).map { it.streamUrl to it.id })
        val toInsert = ArrayList<ChannelEntity>(BATCH)
        val toUpdate = ArrayList<ChannelSourceUpdate>(BATCH)
        var streaming = false

        suspend fun flush() {
            if (toInsert.isEmpty() && toUpdate.isEmpty()) return
            db.withTransaction {
                if (toUpdate.isNotEmpty()) syncDao.updateSource(toUpdate)
                if (toInsert.isNotEmpty()) syncDao.insertAll(toInsert)
            }
            toUpdate.clear()
            toInsert.clear()
        }

        var headerEpgUrl = ""
        val total = parser.parse(stream, onHeader = { headerEpgUrl = it.tvgUrl }) { ch ->
            val id = diff.match(ch.streamUrl)
            if (id != null) toUpdate += ch.toSourceUpdate(id) else toInsert += ch.toEntity(playlistId)
            val buffered = toInsert.size + toUpdate.size
            if (!streaming && buffered > SINGLE_COMMIT_ROWS) streaming = true
            if (streaming && buffered >= BATCH) flush()
        }
        // Keep the cached list if the server returned an empty/garbled playlist.
        if (total == 0) throw IOException("Playlist contained no channels")
        db.withTransaction {
            flush()
            diff.removedIds.chunked(BATCH).forEach { syncDao.deleteByIds(it) }
        }
        if (headerEpgUrl.isNotBlank()) saveHeaderEpgUrl(playlistId, headerEpgUrl)
        return SyncResult.Updated(added = diff.added, removed = diff.removed, total = total)
    }

    /** The x-tvg-url header can carry a token, so it goes to encrypted storage like the playlist link. */
    private suspend fun saveHeaderEpgUrl(playlistId: Long, url: String) {
        if (PlaylistUrlStore.isRef(syncDao.epgUrlRef(playlistId).orEmpty())) return
        val ref = urls.put(url)
        if (syncDao.setEpgUrlRefIfUnset(playlistId, ref) == 0) urls.remove(ref)
    }

    private fun M3uChannel.isAdult(): Boolean = ChannelFilter.isAdultText(groupTitle, name)

    private fun M3uChannel.toEntity(playlistId: Long) = ChannelEntity(
        playlistId = playlistId, tvgId = tvgId, tvgName = tvgName, tvgLogo = tvgLogo, name = name,
        number = number, streamUrl = streamUrl, groupTitle = groupTitle, tvgCountry = tvgCountry,
        tvgLanguage = tvgLanguage, tvgType = tvgType, catchupEnabled = catchupEnabled,
        catchupDays = catchupDays, catchup = rawAttributes["catchup"].orEmpty(),
        catchupSource = rawAttributes["catchup-source"].orEmpty(), epgChannelId = tvgId, isAdult = isAdult(),
    )

    private fun M3uChannel.toSourceUpdate(id: Long) = ChannelSourceUpdate(
        id = id, tvgId = tvgId, tvgName = tvgName, tvgLogo = tvgLogo, name = name, number = number,
        groupTitle = groupTitle, tvgCountry = tvgCountry, tvgLanguage = tvgLanguage, tvgType = tvgType,
        catchupEnabled = catchupEnabled, catchupDays = catchupDays, catchup = rawAttributes["catchup"].orEmpty(),
        catchupSource = rawAttributes["catchup-source"].orEmpty(), isAdult = isAdult(),
    )

    companion object {
        private const val BATCH = 500
        private const val SINGLE_COMMIT_ROWS = 5_000

        /** 100k channels at ~600 bytes each, with headroom. The parser also stops at [M3uParser.MAX_CHANNELS]. */
        const val MAX_PLAYLIST_BYTES = 100L * 1024 * 1024
        internal const val TOO_BIG = "Playlist is too large"
    }
}

/** Fails with an IOException once more than [maxBytes] have been read, so a hostile server can't stream forever. */
internal class CappedInputStream(input: InputStream, private val maxBytes: Long) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) add(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) add(n.toLong())
        return n
    }

    override fun skip(n: Long): Long = super.skip(n).also(::add)

    private fun add(n: Long) {
        count += n
        if (count > maxBytes) throw IOException(PlaylistSyncer.TOO_BIG)
    }
}
