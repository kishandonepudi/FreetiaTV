package com.freetia.tv.data.repository

import androidx.room.withTransaction
import com.freetia.tv.data.database.FreetiaDatabase
import com.freetia.tv.data.model.PlaylistEntity
import com.freetia.tv.data.secure.PlaylistUrlStore
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(private val db: FreetiaDatabase, private val urls: PlaylistUrlStore) {
    private val dao = db.playlistDao()

    fun observeAll(): Flow<List<PlaylistEntity>> = dao.getAllFlow()

    suspend fun get(id: Long): PlaylistEntity? = dao.getById(id)

    suspend fun getAll(): List<PlaylistEntity> = dao.getAll()

    /** Startup rule: default playlist if set, otherwise priority 1. */
    suspend fun startupPlaylist(): PlaylistEntity? = dao.getDefaultPlaylist() ?: dao.getFirstPlaylist()

    /** Saves [playlist] with [url] in encrypted storage; `playlist.sourceUrl` is ignored and set to the reference. */
    suspend fun add(playlist: PlaylistEntity, url: String): PlaylistEntity {
        val ref = urls.put(url)
        val saved = db.withTransaction {
            val withRef = playlist.copy(sourceUrl = ref, priority = dao.maxPriority() + 1)
            val id = dao.insert(withRef)
            if (playlist.isDefault) setDefault(id)
            withRef.copy(id = id)
        }
        return saved
    }

    /** A playlist added by the user: the first one auto-updates, later ones start with it off (§3.2). None is flagged default, so priority order decides which opens (§8). */
    suspend fun addNew(name: String, url: String): PlaylistEntity {
        val first = StartupRules.autoUpdateDefault(dao.getCount())
        return add(
            PlaylistEntity(name = name, sourceUrl = "", isDefault = false, updateOnStartup = first, resumeLastChannel = true),
            url,
        )
    }

    /** Decrypted source URL for display in the edit form or for export. Never log it. */
    fun sourceUrl(playlist: PlaylistEntity): String? = urls.get(playlist.sourceUrl)

    /** Replaces the stored link. A playlist whose reference is missing (e.g. restored without secrets) gets a new one. */
    suspend fun updateSourceUrl(playlist: PlaylistEntity, url: String) {
        if (PlaylistUrlStore.isRef(playlist.sourceUrl)) urls.update(playlist.sourceUrl, url)
        else dao.update(playlist.copy(sourceUrl = urls.put(url)))
    }

    /**
     * The XMLTV URL the user set (it can carry a token, so it lives in encrypted storage like the playlist link),
     * or "" if none. Also reads a plain value, should an older build have saved one.
     */
    fun userEpgUrl(playlist: PlaylistEntity): String =
        if (PlaylistUrlStore.isRef(playlist.userEpgUrl)) urls.get(playlist.userEpgUrl).orEmpty() else playlist.userEpgUrl

    /** The playlist's x-tvg-url header link (an encrypted `playlist_url:` ref; plain only if an older build saved it). */
    fun headerEpgUrl(playlist: PlaylistEntity): String =
        if (PlaylistUrlStore.isRef(playlist.epgUrl)) urls.get(playlist.epgUrl).orEmpty() else playlist.epgUrl

    /** The guide URL to use: the user's own, else the playlist's x-tvg-url header. */
    fun effectiveEpgUrl(playlist: PlaylistEntity): String = userEpgUrl(playlist).ifBlank { headerEpgUrl(playlist) }

    suspend fun setUserEpgUrl(playlist: PlaylistEntity, url: String) {
        val clean = url.trim()
        val current = playlist.userEpgUrl
        when {
            clean.isEmpty() -> {
                urls.remove(current)
                dao.setUserEpgUrl(playlist.id, "")
            }
            PlaylistUrlStore.isRef(current) -> urls.update(current, clean)
            else -> dao.setUserEpgUrl(playlist.id, urls.put(clean))
        }
    }

    suspend fun setDefault(id: Long) = db.withTransaction {
        dao.clearAllDefaults()
        dao.setAsDefault(id)
    }

    suspend fun clearDefault() = dao.clearAllDefaults()

    suspend fun reorder(ordered: List<PlaylistEntity>) = dao.reorderPlaylists(ordered)

    /** Move Up (-1) / Move Down (+1). Priorities are renumbered 1..n, so the next start opens the new priority 1. */
    suspend fun move(id: Long, delta: Int) {
        val all = dao.getAll()
        val moved = StartupRules.move(all, delta) { it.id == id }
        if (moved !== all) dao.reorderPlaylists(moved)
    }

    /** Saves edited settings; priority, default flag, link and sync state are left alone. */
    suspend fun update(edited: PlaylistEntity) {
        val current = dao.getById(edited.id) ?: return
        dao.update(
            current.copy(
                name = edited.name,
                region = edited.region,
                updateOnStartup = edited.updateOnStartup,
                backgroundRefreshIntervalMinutes = edited.backgroundRefreshIntervalMinutes,
                allowBackgroundPlay = edited.allowBackgroundPlay,
                resumeLastChannel = edited.resumeLastChannel,
            )
        )
    }

    suspend fun rename(id: Long, name: String) {
        val current = dao.getById(id) ?: return
        dao.update(current.copy(name = name))
    }

    suspend fun delete(playlist: PlaylistEntity) {
        val current = dao.getById(playlist.id)
        dao.deleteById(playlist.id)
        urls.remove(playlist.sourceUrl)
        urls.remove(playlist.userEpgUrl)
        // Re-read: the header ref may have been saved by a sync after [playlist] was loaded.
        urls.remove(current?.epgUrl ?: playlist.epgUrl)
    }

    suspend fun rememberLastChannel(playlistId: Long, channelId: Long, channelNumber: Int) =
        dao.updateLastChannel(playlistId, channelId, channelNumber)
}
