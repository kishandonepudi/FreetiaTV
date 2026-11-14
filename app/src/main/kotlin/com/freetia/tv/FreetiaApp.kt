package com.freetia.tv

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import android.os.SystemClock
import com.freetia.tv.core.cache.CacheManager
import com.freetia.tv.core.system.AppExit
import com.freetia.tv.core.timer.SleepTimerManager
import com.freetia.tv.core.safety.SafetyManager
import com.freetia.tv.core.security.EncryptionManager
import com.freetia.tv.core.util.MemoryGuard
import coil.imageLoader
import com.freetia.tv.data.DefaultPlaylistSeeder
import com.freetia.tv.data.database.FreetiaDatabase
import com.freetia.tv.data.repository.PlaylistRepository
import com.freetia.tv.data.secure.PlaylistUrlStore
import com.freetia.tv.data.settings.SettingsRepository
import com.freetia.tv.data.sync.PlaylistSyncer
import com.freetia.tv.work.EpgRefreshWorker
import com.freetia.tv.work.PlaylistRefreshWorker
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class FreetiaApp : Application(), ImageLoaderFactory {

    // Last resort for a failed background job: log the type only (messages can quote URLs) and keep the app alive.
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            if (BuildConfig.DEBUG) Log.w("FreetiaApp", "Background job failed: ${e.javaClass.simpleName}")
        }
    )

    val database: FreetiaDatabase by lazy { FreetiaDatabase.getDatabase(this) }
    val cacheManager: CacheManager by lazy { CacheManager(this) }
    val sleepTimer: SleepTimerManager by lazy {
        SleepTimerManager(appScope, SystemClock::elapsedRealtime, { AppExit.sleepExit(this) })
    }
    val encryptionManager: EncryptionManager by lazy { EncryptionManager(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(dataStore) }
    val playlistUrlStore: PlaylistUrlStore by lazy { PlaylistUrlStore(encryptionManager) }
    val playlistRepository: PlaylistRepository by lazy { PlaylistRepository(database, playlistUrlStore) }
    val safety: SafetyManager by lazy { SafetyManager.create(this, appScope) }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followSslRedirects(true)
            .build()
    }

    // Its own call timeout, not on [httpClient]: the player streams live video through that client, and a call
    // timeout there would cut a long-running stream. 5 minutes covers a playlist near the 100 MB cap on slow Wi-Fi, parsed and flushed on a low-end TV.
    val playlistSyncer: PlaylistSyncer by lazy {
        PlaylistSyncer(database, httpClient.newBuilder().callTimeout(300, TimeUnit.SECONDS).build(), playlistUrlStore)
    }

    val memoryGuard: MemoryGuard by lazy { MemoryGuard(this, appScope) }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Raises underPressure only for RUNNING_LOW..RUNNING_CRITICAL; other levels just sample lowMemory.
        memoryGuard.onTrimMemory(level)
        // Logos are re-fetchable from the disk cache; RAM is not. Clearing on UI_HIDDEN/BACKGROUND is
        // deliberately decoupled from the pressure flag.
        @Suppress("DEPRECATION")
        if (level >= TRIM_MEMORY_RUNNING_LOW) imageLoader.memoryCache?.clear()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        memoryGuard.onLowMemory()
        imageLoader.memoryCache?.clear()
    }

    override fun onCreate() {
        super.onCreate()
        // Before the image loader opens its logo cache; see CacheManager.clearAtStartup.
        cacheManager.clearAtStartup(appScope)
        cacheManager.mirrorSettings(settings, appScope)
        appScope.launch {
            DefaultPlaylistSeeder(playlistRepository, settings).seedIfNeeded()
            PlaylistRefreshWorker.enqueueStartup(this@FreetiaApp)
            EpgRefreshWorker.start(this@FreetiaApp)
            com.freetia.tv.ui.settings.ProfileEnforcer.start(this@FreetiaApp)
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { httpClient }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(if (memoryGuard.isLowRamDevice) 0.05 else 0.10).build() }
        .allowRgb565(memoryGuard.isLowRamDevice)
        .diskCache {
            DiskCache.Builder()
                .directory(cacheManager.logoDir)
                .maxSizeBytes(cacheManager.policy.logoCapBytes)
                .build()
        }
        .crossfade(!memoryGuard.isLowRamDevice)
        .build()
}
