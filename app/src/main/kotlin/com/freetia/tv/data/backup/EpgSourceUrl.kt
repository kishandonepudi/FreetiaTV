package com.freetia.tv.data.backup

import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.secure.PlaylistUrlStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The global guide (XMLTV) link from Settings. Like a playlist's own guide link it can carry a token, so the
 * `epgSourceUrl` setting holds only a `playlist_url:` reference and the link sits in encrypted storage.
 */
object EpgSourceUrl {
    /** The link, or "" when none is set. Also reads a plain value should an older build have saved one. */
    suspend fun get(app: FreetiaApp): String {
        val raw = app.settings.epgSourceUrl.get()
        if (!PlaylistUrlStore.isRef(raw)) {
            if (raw.isNotBlank()) set(app, raw)
            return raw
        }
        return withContext(Dispatchers.IO) { app.playlistUrlStore.get(raw).orEmpty() }
    }

    suspend fun set(app: FreetiaApp, url: String) {
        val clean = url.trim()
        val current = app.settings.epgSourceUrl.get()
        withContext(Dispatchers.IO) {
            when {
                clean.isEmpty() -> {
                    app.playlistUrlStore.remove(current)
                    app.settings.epgSourceUrl.reset()
                }
                PlaylistUrlStore.isRef(current) -> app.playlistUrlStore.update(current, clean)
                else -> app.settings.epgSourceUrl.set(app.playlistUrlStore.put(clean))
            }
        }
    }

    suspend fun isSet(app: FreetiaApp): Boolean = app.settings.epgSourceUrl.get().isNotBlank()
}
