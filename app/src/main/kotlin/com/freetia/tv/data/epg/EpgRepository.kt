package com.freetia.tv.data.epg

import com.freetia.tv.data.database.EpgSearchRow
import com.freetia.tv.data.database.FreetiaDatabase
import com.freetia.tv.data.model.EpgProgrammeEntity
import com.freetia.tv.data.repository.RegionFilter

/** Read side of the guide: every query is windowed or LIMITed so 7 days × 5,000 channels is never loaded at once. */
class EpgRepository(db: FreetiaDatabase) {
    private val dao = db.epgDao()

    /** Playlist channel id to the XMLTV channel id its programmes are stored under; channels without guide data are absent. */
    suspend fun guideIds(channelIds: List<Long>): Map<Long, String> {
        val out = HashMap<Long, String>()
        channelIds.chunked(CHUNK).forEach { part -> dao.getEpgRefs(part).forEach { out[it.channelId] = it.epgChannelId } }
        return out
    }

    suspend fun window(ids: List<String>, from: Long, to: Long): Map<String, List<EpgProgrammeEntity>> {
        if (ids.isEmpty()) return emptyMap()
        return dao.getGridWindow(ids.distinct(), from, to, GRID_LIMIT).groupBy { it.tvgId }
    }

    suspend fun search(
        query: String,
        now: Long,
        playlistId: Long,
        childSafe: Boolean,
        hideDead: Boolean,
        region: RegionFilter,
        limit: Int = 100,
    ): List<EpgSearchRow> {
        val q = query.trim().take(60)
        if (q.length < 2) return emptyList()
        return dao.searchGuide(escapeLike(q), now, playlistId, childSafe, hideDead, region.country, region.language, limit)
    }

    suspend fun hasGuide(now: Long): Boolean = dao.hasUpcoming(now)

    companion object {
        private const val CHUNK = 500
        const val GRID_LIMIT = 4_000

        fun escapeLike(q: String): String = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}
