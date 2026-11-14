package com.freetia.tv.data.backup

const val BACKUP_FORMAT = "freetia-backup"
const val BACKUP_SCHEMA_VERSION = 1

/** Files and typed codes above this are refused before any parsing. */
const val MAX_IMPORT_CHARS = 2 * 1024 * 1024
const val MAX_BACKUP_PLAYLISTS = 100
const val MAX_BACKUP_REFS = 20_000
const val MAX_BACKUP_GROUPS = 200

/** A channel named in a backup. [playlist] indexes [BackupData.playlists]; channels are matched by tvg-id, then name. */
data class ChannelRef(val playlist: Int, val tvgId: String, val name: String)

data class BackupPlaylist(
    val name: String,
    /** Only present in an encrypted file. */
    val url: String? = null,
    val userEpgUrl: String? = null,
    val region: String = "",
    val isDefault: Boolean = false,
    val updateOnStartup: Boolean = false,
    val refreshMinutes: Int = 24 * 60,
    val allowBackgroundPlay: Boolean = false,
    val resumeLastChannel: Boolean = true,
)

data class BackupGroup(val name: String, val channels: List<ChannelRef>)

data class CategoryLockRef(val scope: String, val value: String)

/**
 * Everything a backup can carry. Playlist order is the list order. Null optional sections were not exported.
 * Links and PIN hashes may only be written to a passphrase-encrypted file (see [BackupFiles]).
 */
data class BackupData(
    val settings: Map<String, Any> = emptyMap(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val favourites: List<ChannelRef>? = null,
    val groups: List<BackupGroup>? = null,
    val lockedChannels: List<ChannelRef>? = null,
    val categoryLocks: List<CategoryLockRef>? = null,
    val epgSourceUrl: String? = null,
    val pinSecrets: Map<String, String>? = null,
) {
    val hasSecrets: Boolean
        get() = epgSourceUrl != null || pinSecrets != null || playlists.any { it.url != null || it.userEpgUrl != null }

    fun withoutSecrets(): BackupData = copy(
        playlists = playlists.map { it.copy(url = null, userEpgUrl = null) },
        epgSourceUrl = null,
        pinSecrets = null,
    )
}

enum class ImportMode { MERGE, REPLACE }

sealed interface BackupOpen {
    data class Ok(val data: BackupData) : BackupOpen
    data object NeedsPassphrase : BackupOpen
    data class Invalid(val reason: String) : BackupOpen
}

/** What an import will do, shown before anything is applied. */
data class ImportSummary(
    val settings: Int,
    val newPlaylists: Int,
    val existingPlaylists: Int,
    val removedPlaylists: Int,
    val withLinks: Int,
    val favourites: Int,
    val groups: Int,
    val locks: Int,
    val includesPin: Boolean,
    val mode: ImportMode,
)

object ImportPlanner {
    /** Playlists match by name, ignoring case. */
    fun summarize(data: BackupData, existingNames: List<String>, mode: ImportMode): ImportSummary {
        val have = existingNames.map { it.trim().lowercase() }.toSet()
        val incoming = data.playlists.map { it.name.trim().lowercase() }.toSet()
        val existing = data.playlists.count { it.name.trim().lowercase() in have }
        return ImportSummary(
            settings = data.settings.size,
            newPlaylists = data.playlists.size - existing,
            existingPlaylists = existing,
            removedPlaylists = if (mode == ImportMode.REPLACE) have.count { it !in incoming } else 0,
            withLinks = data.playlists.count { !it.url.isNullOrBlank() },
            favourites = data.favourites?.size ?: 0,
            groups = data.groups?.size ?: 0,
            locks = (data.lockedChannels?.size ?: 0) + (data.categoryLocks?.size ?: 0),
            includesPin = !data.pinSecrets.isNullOrEmpty(),
            mode = mode,
        )
    }
}

/** Plain sentences for the summary shown before an import is applied. */
fun ImportSummary.lines(): List<String> = buildList {
    if (settings > 0) add("$settings settings will be applied.")
    val playlistParts = ArrayList<String>()
    if (newPlaylists > 0) playlistParts += "$newPlaylists new"
    if (existingPlaylists > 0) playlistParts += "$existingPlaylists already here"
    if (removedPlaylists > 0) playlistParts += "$removedPlaylists will be removed"
    if (playlistParts.isNotEmpty()) add("Playlists: " + playlistParts.joinToString(", ") + ".")
    if (withLinks > 0) add("$withLinks playlist links are included.")
    else if (newPlaylists > 0) add("New playlists come without links. Add each link in Playlists afterwards.")
    if (favourites > 0) add("$favourites favourites, matched to channels already on this device.")
    if (groups > 0) add("$groups groups.")
    if (locks > 0) add("$locks locks.")
    if (includesPin) add("This backup includes a PIN.")
    if (isEmpty()) add("This backup has nothing to apply.")
}
