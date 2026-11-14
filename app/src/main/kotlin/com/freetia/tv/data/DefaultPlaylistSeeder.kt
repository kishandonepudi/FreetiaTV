package com.freetia.tv.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.freetia.tv.BuildConfig
import com.freetia.tv.data.model.PlaylistEntity
import com.freetia.tv.data.preferences.PreferencesKeys
import com.freetia.tv.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.first

/**
 * Adds the flavor's default playlist once, on first run. Empty URL (store flavor) = no-op.
 * Runs only once so a user who deletes the playlist does not get it back.
 */
class DefaultPlaylistSeeder(
    private val repository: PlaylistRepository,
    private val settings: DataStore<Preferences>,
) {
    suspend fun seedIfNeeded(): Boolean {
        if (BuildConfig.DEFAULT_PLAYLIST_URL.isBlank()) return false
        if (settings.data.first()[PreferencesKeys.DEFAULT_PLAYLIST_SEEDED] == true) return false

        repository.add(
            PlaylistEntity(
                name = BuildConfig.DEFAULT_PLAYLIST_NAME,
                sourceUrl = BuildConfig.DEFAULT_PLAYLIST_URL,
                region = BuildConfig.DEFAULT_PLAYLIST_REGION,
                isDefault = true,
                updateOnStartup = true,
                resumeLastChannel = true,
                backgroundRefreshIntervalMinutes = 24 * 60,
            )
        )
        settings.edit { it[PreferencesKeys.DEFAULT_PLAYLIST_SEEDED] = true }
        return true
    }
}
