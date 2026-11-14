package com.freetia.tv.data.epg

import androidx.room.withTransaction
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.database.EpgMatchRow
import com.freetia.tv.data.model.EpgChannelEntity
import com.freetia.tv.data.model.EpgProgrammeEntity
import com.freetia.tv.data.model.PlaylistEntity
import com.freetia.tv.data.parser.ParseResult
import com.freetia.tv.data.parser.XmltvParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

sealed interface EpgOutcome {
    data object Updated : EpgOutcome
    /** No guide URL for this playlist, or nothing to do. */
    data object NoSource : EpgOutcome
    /** Memory pressure: what was imported so far is kept and the work should retry later. */
    data object Deferred : EpgOutcome
    data object Failed : EpgOutcome
}

/**
 * Streams one playlist's XMLTV guide into Room: gzip aware, size/field bounded, http(s) only. Programmes are stored
 * once per XMLTV channel (on the first matched playlist channel) and replaced per channel, so a failed or
 * deferred run never leaves duplicates; ended programmes and unmatched channels are pruned.
 */
class EpgImporter(private val app: FreetiaApp) {
    private val db = app.database
    private val dao = db.epgDao()

    private class Deferral : RuntimeException()

    suspend fun refresh(playlist: PlaylistEntity, shouldDefer: () -> Boolean = { false }): EpgOutcome =
        withContext(Dispatchers.IO) {
            val raw = app.playlistRepository.effectiveEpgUrl(playlist).ifBlank { com.freetia.tv.data.backup.EpgSourceUrl.get(app) }
            val url = EpgRules.parseUrl(raw) ?: return@withContext EpgOutcome.NoSource
            val now = System.currentTimeMillis()
            val window = EpgWindow(now, app.settings.epgDays.get())
            try {
                val outcome = download(url.toString(), playlist, window, shouldDefer)
                when (outcome) {
                    EpgOutcome.Updated -> {
                        app.database.playlistDao().markEpgUpdate(playlist.id, true)
                        dao.deletePastProgrammes(window.from)
                    }
                    EpgOutcome.Failed -> app.database.playlistDao().markEpgUpdate(playlist.id, false)
                    else -> Unit
                }
                outcome
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.database.playlistDao().markEpgUpdate(playlist.id, false)
                EpgOutcome.Failed
            }
        }

    private suspend fun download(
        url: String,
        playlist: PlaylistEntity,
        window: EpgWindow,
        shouldDefer: () -> Boolean,
    ): EpgOutcome {
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity").build()
        app.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            if (body.contentLength() > EpgStreams.MAX_DOWNLOAD_BYTES) throw EpgLimitException("Guide file is too large")
            return import(EpgStreams.open(body.byteStream()), playlist, window, shouldDefer)
        }
    }

    suspend fun import(
        input: java.io.InputStream,
        playlist: PlaylistEntity,
        window: EpgWindow,
        shouldDefer: () -> Boolean,
    ): EpgOutcome {
        val channels = db.channelDao().getEpgMatchRows(playlist.id)
        val byTvg = HashMap<String, MutableList<EpgMatchRow>>()
        val byName = HashMap<String, MutableList<EpgMatchRow>>()
        for (c in channels) {
            val key = c.tvgId.ifBlank { c.epgChannelId }
            if (key.isNotBlank()) byTvg.getOrPut(EpgMatch.normalizeId(key)) { ArrayList() } += c
            else EpgMatch.normalizeName(c.tvgName.ifBlank { c.name }).takeIf { it.isNotEmpty() }
                ?.let { byName.getOrPut(it) { ArrayList() } += c }
        }

        val existing = dao.getEpgChannelsForPlaylist(playlist.id).associateBy { it.channelId }
        val claimed = HashSet<Long>()
        val matches = HashMap<String, List<EpgMatchRow>>()
        val primaryRow = HashMap<String, Long>()
        val buffer = ArrayList<EpgProgrammeEntity>(BATCH)
        var deferred = false

        suspend fun flush(checkDefer: Boolean = true) {
            if (buffer.isEmpty()) return
            db.withTransaction { dao.insertEpgProgrammes(buffer.toList()) }
            buffer.clear()
            if (checkDefer && shouldDefer()) {
                deferred = true
                throw Deferral()
            }
        }

        suspend fun rowsFor(xmltvId: String): Long? {
            primaryRow[xmltvId]?.let { return it }
            val targets = (matches[xmltvId] ?: return null).filter { claimed.add(it.id) }
            if (targets.isEmpty()) return null
            return db.withTransaction {
                var primary = 0L
                for (c in targets) {
                    val old = existing[c.id]
                    val rowId = if (old != null) {
                        dao.updateEpgChannel(old.copy(epgChannelId = xmltvId, channelName = c.name))
                        old.id
                    } else {
                        dao.insertEpgChannel(EpgChannelEntity(channelId = c.id, epgChannelId = xmltvId, channelName = c.name))
                    }
                    dao.deleteChannelProgrammes(rowId)
                    if (primary == 0L) primary = rowId
                }
                primary
            }.also { primaryRow[xmltvId] = it }
        }

        val result =
            XmltvParser().parse(
                input,
                onChannel = { x ->
                    val found = byTvg[EpgMatch.normalizeId(x.id)]
                        ?: byName[EpgMatch.normalizeName(x.displayName)]
                    if (found != null) matches[x.id] = found
                },
                onProgramme = { p ->
                    if (window.keeps(p.start, p.stop)) {
                        val rowId = rowsFor(p.channel)
                        if (rowId != null) {
                            buffer += EpgProgrammeEntity(
                                epgChannelId = rowId, tvgId = p.channel, start = p.start, stop = p.stop, title = p.title,
                                description = p.description, category = p.category, icon = p.icon, length = p.length,
                            )
                            if (buffer.size >= BATCH) flush()
                        }
                    }
                },
            )

        if (deferred) return EpgOutcome.Deferred
        if (result is ParseResult.Error && primaryRow.isEmpty()) return EpgOutcome.Failed
        flush(checkDefer = false)

        // Only a complete run may drop channels, otherwise a truncated file would erase guide data.
        if (result !is ParseResult.Error) {
            db.withTransaction { existing.keys.filter { it !in claimed }.forEach { dao.deleteEpgChannelByChannelId(it) } }
        }
        return if (result is ParseResult.Error) EpgOutcome.Failed else EpgOutcome.Updated
    }

    private companion object {
        const val BATCH = 500
    }
}
