package com.freetia.tv.data

import com.freetia.tv.BuildConfig
import com.freetia.tv.data.model.PlaylistEntity
import com.freetia.tv.data.repository.PlaylistRepository
import com.freetia.tv.data.settings.SettingsRepository

/**
 * Adds the flavor's default playlist once, on first run. Empty URL (store flavor) = no-op.
 * Runs only once so a user who deletes the playlist does not get it back.
 */
class DefaultPlaylistSeeder(
    private val repository: PlaylistRepository,
    private val settings: SettingsRepository,
) {
    suspend fun seedIfNeeded(): Boolean {
        if (BuildConfig.DEFAULT_PLAYLIST_URL.isBlank()) return false
        if (settings.defaultPlaylistSeeded.get()) return false

        repository.add(
            PlaylistEntity(
                name = BuildConfig.DEFAULT_PLAYLIST_NAME,
                sourceUrl = "",
                region = BuildConfig.DEFAULT_PLAYLIST_REGION,
                isDefault = true,
                updateOnStartup = true,
                resumeLastChannel = true,
                backgroundRefreshIntervalMinutes = 24 * 60,
            ),
            url = BuildConfig.DEFAULT_PLAYLIST_URL,
        )
        settings.defaultPlaylistSeeded.set(true)
        return true
    }
}
