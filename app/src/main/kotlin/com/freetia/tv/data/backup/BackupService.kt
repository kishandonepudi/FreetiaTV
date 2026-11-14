package com.freetia.tv.data.backup

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.CategoryLockEntity
import com.freetia.tv.data.model.FavouriteEntity
import com.freetia.tv.data.model.GroupChannelCrossRef
import com.freetia.tv.data.model.GroupEntity
import com.freetia.tv.data.model.LockedChannelEntity
import com.freetia.tv.data.model.PlaylistEntity
import com.freetia.tv.data.settings.Pref
import com.freetia.tv.data.settings.StoredEnum
import com.freetia.tv.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExportOptions(
    val includeLibrary: Boolean = true,
    /** With a passphrase the file is encrypted and may carry links; without one nothing secret is written. */
    val passphrase: CharArray? = null,
    val includePin: Boolean = false,
)

data class ApplyResult(
    val playlistsAdded: Int,
    val favouritesMatched: Int,
    val favouritesTotal: Int,
    val groupsApplied: Int,
    val locksApplied: Int,
)

/** Reads the app's state into [BackupData] and applies an import. File and text handling is in [BackupFiles]. */
class BackupService(private val app: FreetiaApp) {
    private val repo get() = app.playlistRepository
    private val db get() = app.database

    suspend fun build(opts: ExportOptions): BackupData = withContext(Dispatchers.IO) {
        val encrypted = opts.passphrase != null && opts.passphrase.isNotEmpty()
        val playlists = repo.getAll()
        val index = playlists.withIndex().associate { (i, p) -> p.id to i }
        val profile = app.settings.activeProfileId.get()

        fun ref(c: ChannelEntity): ChannelRef? = index[c.playlistId]?.let { ChannelRef(it, c.tvgId, c.name) }

        var favourites: List<ChannelRef>? = null
        var groups: List<BackupGroup>? = null
        var locked: List<ChannelRef>? = null
        var categories: List<CategoryLockRef>? = null
        if (opts.includeLibrary) {
            favourites = db.favouriteDao().observeFavouriteChannels(profile, false, MAX_BACKUP_REFS).first().mapNotNull(::ref)
            groups = db.groupDao().observeGroups(profile).first().map { g ->
                BackupGroup(g.name, db.groupDao().getGroupChannels(g.id).mapNotNull(::ref))
            }
            locked = db.lockedChannelDao().getLockedChannels().mapNotNull(::ref)
            categories = db.lockedChannelDao().getCategoryLocks().map { CategoryLockRef(it.scope, it.value) }
        }
        BackupData(
            settings = exportSettings(),
            playlists = playlists.map { p ->
                BackupPlaylist(
                    name = p.name,
                    url = if (encrypted) repo.sourceUrl(p)?.takeIf { it.isNotBlank() } else null,
                    userEpgUrl = if (encrypted) repo.userEpgUrl(p).takeIf { it.isNotBlank() } else null,
                    region = p.region,
                    isDefault = p.isDefault,
                    updateOnStartup = p.updateOnStartup,
                    refreshMinutes = p.backgroundRefreshIntervalMinutes,
                    allowBackgroundPlay = p.allowBackgroundPlay,
                    resumeLastChannel = p.resumeLastChannel,
                )
            },
            favourites = favourites,
            groups = groups,
            lockedChannels = locked,
            categoryLocks = categories,
            epgSourceUrl = if (encrypted) EpgSourceUrl.get(app).takeIf { it.isNotBlank() } else null,
            pinSecrets = if (encrypted && opts.includePin) app.safety.pin.exportSecrets().takeIf { it.isNotEmpty() } else null,
        )
    }

    /** Only settings that differ from their default, so a QR code stays small. */
    suspend fun buildCompact(passphrase: CharArray?): BackupData {
        val full = build(ExportOptions(includeLibrary = false, passphrase = passphrase))
        val changed = exportSettings(onlyChanged = true)
        return full.copy(settings = changed)
    }

    private suspend fun exportSettings(onlyChanged: Boolean = false): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        for (pref in app.settings.all) {
            val name = pref.key.name
            if (name in SettingRules.excluded || name !in SettingRules.rules) continue
            @Suppress("UNCHECKED_CAST")
            val p = pref as Pref<Any>
            val value = p.get()
            if (onlyChanged && value == p.default) continue
            out[name] = if (value is StoredEnum) value.storage else value
        }
        return out
    }

    suspend fun existingNames(): List<String> = repo.getAll().map { it.name }

    suspend fun apply(data: BackupData, mode: ImportMode, restorePin: Boolean): ApplyResult = withContext(Dispatchers.IO) {
        applySettings(data.settings)
        data.epgSourceUrl?.let { EpgSourceUrl.set(app, it) }

        val (idByIndex, added) = applyPlaylists(data, mode)
        val profile = app.settings.activeProfileId.get()

        var favMatched = 0
        data.favourites?.let { refs ->
            for (r in refs) {
                val ch = find(r, idByIndex) ?: continue
                db.favouriteDao().insert(FavouriteEntity(channelId = ch.id, profileId = profile))
                favMatched++
            }
        }
        var groupsApplied = 0
        data.groups?.let { list ->
            val have = db.groupDao().observeGroups(profile).first()
            for (g in list) {
                val id = have.firstOrNull { it.name.equals(g.name, ignoreCase = true) }?.id
                    ?: db.groupDao().insertGroup(GroupEntity(name = g.name, profileId = profile))
                for (r in g.channels) {
                    val ch = find(r, idByIndex) ?: continue
                    db.groupDao().addChannelToGroup(GroupChannelCrossRef(groupId = id, channelId = ch.id))
                }
                groupsApplied++
            }
        }
        var locks = 0
        data.lockedChannels?.forEach { r ->
            val ch = find(r, idByIndex) ?: return@forEach
            db.lockedChannelDao().insert(LockedChannelEntity(channelId = ch.id))
            locks++
        }
        data.categoryLocks?.forEach {
            db.lockedChannelDao().insertCategoryLock(CategoryLockEntity(scope = it.scope, value = it.value))
            locks++
        }
        if (restorePin) data.pinSecrets?.let { app.safety.pin.importSecrets(it) }

        ApplyResult(added, favMatched, data.favourites?.size ?: 0, groupsApplied, locks)
    }

    private suspend fun find(r: ChannelRef, idByIndex: Map<Int, Long>): ChannelEntity? {
        val pid = idByIndex[r.playlist] ?: return null
        val dao = db.channelDao()
        return (if (r.tvgId.isNotEmpty()) dao.getByTvgId(r.tvgId, pid) else null)
            ?: if (r.name.isNotEmpty()) dao.getByName(r.name, pid) else null
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun applySettings(values: Map<String, Any>) {
        if (values.isEmpty()) return
        app.dataStore.edit { prefs ->
            for (pref in app.settings.all) {
                val v = values[pref.key.name] ?: continue
                val key = pref.key
                when (pref.default) {
                    is Boolean -> (v as? Boolean)?.let { prefs[key as Preferences.Key<Boolean>] = it }
                    is Int -> (v as? Int)?.let { prefs[key as Preferences.Key<Int>] = it }
                    is Long -> (v as? Number)?.let { prefs[key as Preferences.Key<Long>] = it.toLong() }
                    is Float -> (v as? Float)?.let { prefs[key as Preferences.Key<Float>] = it }
                    is String, is StoredEnum -> (v as? String)?.let { prefs[key as Preferences.Key<String>] = it }
                }
            }
        }
    }

    /** Returns file-index to playlist-id and the number of playlists added. */
    private suspend fun applyPlaylists(data: BackupData, mode: ImportMode): Pair<Map<Int, Long>, Int> {
        val existing = repo.getAll()
        val byName = existing.associateBy { it.name.trim().lowercase() }
        val idByIndex = HashMap<Int, Long>()
        val fresh = ArrayList<PlaylistEntity>()
        var added = 0
        data.playlists.forEachIndexed { i, b ->
            val match = byName[b.name.trim().lowercase()]
            if (match != null) {
                idByIndex[i] = match.id
                if (mode == ImportMode.REPLACE) {
                    repo.update(match.copy(
                        region = b.region, updateOnStartup = b.updateOnStartup,
                        backgroundRefreshIntervalMinutes = b.refreshMinutes,
                        allowBackgroundPlay = b.allowBackgroundPlay, resumeLastChannel = b.resumeLastChannel,
                    ))
                }
                b.url?.let { repo.updateSourceUrl(match, it) }
                b.userEpgUrl?.let { repo.setUserEpgUrl(match, it) }
            } else {
                val entity = PlaylistEntity(
                    name = b.name, sourceUrl = "", region = b.region, updateOnStartup = b.updateOnStartup,
                    backgroundRefreshIntervalMinutes = b.refreshMinutes, allowBackgroundPlay = b.allowBackgroundPlay,
                    resumeLastChannel = b.resumeLastChannel, lastUpdateSuccess = b.url != null,
                    failureMessage = if (b.url == null) "Add this playlist's link, then refresh." else "",
                )
                val saved = repo.add(entity, b.url.orEmpty())
                b.userEpgUrl?.let { repo.setUserEpgUrl(saved, it) }
                idByIndex[i] = saved.id
                added++
                if (b.url != null) fresh += saved
            }
        }
        if (mode == ImportMode.REPLACE) {
            val keep = idByIndex.values.toSet()
            existing.filter { it.id !in keep }.forEach { repo.delete(it) }
        }
        val all = repo.getAll()
        val fileOrder = data.playlists.indices.mapNotNull { idByIndex[it] }
        val ordered = if (mode == ImportMode.REPLACE) {
            fileOrder.mapNotNull { id -> all.firstOrNull { it.id == id } }
        } else {
            val old = existing.map { it.id }
            (old + fileOrder.filter { it !in old }).mapNotNull { id -> all.firstOrNull { it.id == id } }
        }
        if (ordered.isNotEmpty()) repo.reorder(ordered)
        data.playlists.withIndex().firstOrNull { it.value.isDefault }?.let { (i, _) ->
            val id = idByIndex[i]
            if (id != null && (mode == ImportMode.REPLACE || all.none { it.isDefault })) repo.setDefault(id)
        }
        fresh.forEach { p -> app.appScope.launch { app.playlistSyncer.sync(p, force = true) } }
        return idByIndex to added
    }
}
