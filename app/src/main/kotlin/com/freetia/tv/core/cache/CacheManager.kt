package com.freetia.tv.core.cache

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns everything under cacheDir/freetia. Settings, playlists, channels, favourites, locks and
 * reminders live in Room/DataStore (filesDir), so clearing here never touches them.
 */
class CacheManager(context: Context) {
    private val root = File(context.cacheDir, "freetia")
    val logoDir = File(root, "logos")
    val epgDir = File(root, "epg")
    val tempDir = File(root, "temp")
    val playerDir = File(root, "player")

    /** Startup/exit clear: EPG buffers, temp files, probe results, player segments. */
    suspend fun clearTransientCache() = withContext(Dispatchers.IO) {
        listOf(epgDir, tempDir, playerDir).forEach { it.deleteRecursively() }
    }

    /** Logo LRU (capped at 50 MB by Coil) is dropped only on full exit or "Clear now". */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        root.deleteRecursively()
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    companion object {
        const val LOGO_CACHE_BYTES = 50L * 1024 * 1024
    }
}
