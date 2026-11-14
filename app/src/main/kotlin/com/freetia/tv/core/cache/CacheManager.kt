package com.freetia.tv.core.cache

import android.content.Context
import coil.ImageLoader
import com.freetia.tv.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Which cache areas a clear touches (§3.13). Settings, playlists, channels, locks and reminders are never here. */
data class CacheSelection(
    val logos: Boolean = true,
    val epg: Boolean = true,
    val temp: Boolean = true,
    val player: Boolean = true,
    val probe: Boolean = true,
    val crashLogs: Boolean = true,
) {
    val isAll get() = logos && epg && temp && player && probe && crashLogs

    companion object {
        val ALL = CacheSelection()
    }
}

/** Both clear toggles and the logo cap, mirrored from Settings so Application.onCreate can read them without I/O. */
data class CachePolicy(val clearOnExit: Boolean, val clearOnStartup: Boolean, val logoCapMb: Int) {
    val logoCapBytes: Long get() = logoCapMb.coerceIn(MIN_LOGO_CAP_MB, MAX_LOGO_CAP_MB) * 1024L * 1024L

    companion object {
        const val DEFAULT_LOGO_CAP_MB = 50
        const val MIN_LOGO_CAP_MB = 10
        const val MAX_LOGO_CAP_MB = 500
    }
}

/**
 * The cache directory layout and the pure file operations on it. Everything the app may delete lives under
 * cacheDir; settings, PIN, playlists and the rest live in filesDir/databases, so a clear here cannot reach them.
 */
class CacheLayout(private val cacheDir: File) {
    val root = File(cacheDir, ROOT)
    val logoDir = File(root, "logos")
    val epgDir = File(root, "epg")
    val tempDir = File(root, "temp")
    val playerDir = File(root, "player")
    val probeDir = File(root, "probe")
    val crashDir = File(root, "crash")

    /** Deletes the selected areas and returns the bytes freed. A full selection also empties stray cacheDir entries. */
    fun clear(selection: CacheSelection): Long {
        val before = sizeBytes()
        if (selection.isAll) {
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        } else {
            listOfNotNull(
                logoDir.takeIf { selection.logos },
                epgDir.takeIf { selection.epg },
                tempDir.takeIf { selection.temp },
                playerDir.takeIf { selection.player },
                probeDir.takeIf { selection.probe },
                crashDir.takeIf { selection.crashLogs },
            ).forEach { it.deleteRecursively() }
        }
        return (before - sizeBytes()).coerceAtLeast(0L)
    }

    /** Cheap startup clear: moves the tree aside in one step and returns the leftovers to delete in the background. */
    fun moveAside(): List<File> {
        if (root.exists()) root.renameTo(File(cacheDir, "$TRASH${System.nanoTime()}"))
        return trash()
    }

    fun trash(): List<File> = cacheDir.listFiles { f -> f.name.startsWith(TRASH) }?.toList().orEmpty()

    fun sizeBytes(): Long = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    companion object {
        const val ROOT = "freetia"
        const val TRASH = "freetia-trash-"
    }
}

class CacheManager(private val context: Context) {
    private val layout = CacheLayout(context.cacheDir)
    val logoDir get() = layout.logoDir
    val epgDir get() = layout.epgDir
    val tempDir get() = layout.tempDir
    val playerDir get() = layout.playerDir
    val crashDir get() = layout.crashDir

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val policy: CachePolicy
        get() = prefs.let {
            CachePolicy(
                clearOnExit = it.getBoolean(K_EXIT, true),
                clearOnStartup = it.getBoolean(K_STARTUP, true),
                logoCapMb = it.getInt(K_LOGO_MB, CachePolicy.DEFAULT_LOGO_CAP_MB),
            )
        }

    /** Keeps the synchronous mirror in step with Settings. A new logo cap applies from the next launch. */
    fun mirrorSettings(settings: SettingsRepository, scope: CoroutineScope) {
        scope.launch {
            combine(
                settings.clearCacheOnExit.flow,
                settings.clearCacheOnStartup.flow,
                settings.logoCacheSizeMb.flow,
            ) { exit, startup, mb -> Triple(exit, startup, mb) }.collect { (exit, startup, mb) ->
                prefs.edit().putBoolean(K_EXIT, exit).putBoolean(K_STARTUP, startup).putInt(K_LOGO_MB, mb).apply()
            }
        }
    }

    /**
     * Cold-start clear. Runs before the image loader exists (it opens the logo cache lazily), so the tree is renamed
     * on the calling thread and deleted in the background.
     */
    fun clearAtStartup(scope: CoroutineScope) {
        if (!policy.clearOnStartup) return
        val trash = layout.moveAside()
        scope.launch(Dispatchers.IO) { trash.forEach { it.deleteRecursively() } }
    }

    /** "Clear now" for Settings: also empties Coil's memory and disk cache. Returns the bytes freed. */
    suspend fun clearNow(selection: CacheSelection = CacheSelection.ALL, imageLoader: ImageLoader? = null): Long =
        withContext(Dispatchers.IO) {
            // Coil keeps its journal open, so while it is running its own clear() empties the logos, not a delete.
            val coilClears = selection.logos && imageLoader != null
            if (coilClears) {
                imageLoader?.memoryCache?.clear()
                imageLoader?.diskCache?.clear()
            }
            layout.clear(if (coilClears) selection.copy(logos = false) else selection)
        }

    /** Full exit: closes the image loader first so nothing rewrites the logo cache while it is deleted. */
    suspend fun clearForExit(imageLoader: ImageLoader?) = withContext(Dispatchers.IO) {
        imageLoader?.memoryCache?.clear()
        imageLoader?.shutdown()
        layout.clear(CacheSelection.ALL)
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) { layout.sizeBytes() }

    private companion object {
        const val PREFS = "cache_policy"
        const val K_EXIT = "clear_on_exit"
        const val K_STARTUP = "clear_on_startup"
        const val K_LOGO_MB = "logo_cap_mb"
    }
}
