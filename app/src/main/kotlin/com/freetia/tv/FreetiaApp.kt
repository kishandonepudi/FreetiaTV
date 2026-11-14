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
import com.freetia.tv.core.cache.CacheManager
import com.freetia.tv.core.security.EncryptionManager
import com.freetia.tv.data.DefaultPlaylistSeeder
import com.freetia.tv.data.database.FreetiaDatabase
import com.freetia.tv.data.preferences.PreferencesKeys
import com.freetia.tv.data.repository.PlaylistRepository
import com.freetia.tv.data.sync.PlaylistSyncer
import com.freetia.tv.work.PlaylistRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class FreetiaApp : Application(), ImageLoaderFactory {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: FreetiaDatabase by lazy { FreetiaDatabase.getDatabase(this) }
    val cacheManager: CacheManager by lazy { CacheManager(this) }
    val encryptionManager: EncryptionManager by lazy { EncryptionManager(this) }
    val playlistRepository: PlaylistRepository by lazy { PlaylistRepository(database) }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followSslRedirects(true)
            .build()
    }

    val playlistSyncer: PlaylistSyncer by lazy { PlaylistSyncer(database, httpClient) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val prefs = dataStore.data.first()
            if (prefs[PreferencesKeys.CLEAR_CACHE_ON_STARTUP] != false) cacheManager.clearTransientCache()
            DefaultPlaylistSeeder(playlistRepository, dataStore).seedIfNeeded()
            PlaylistRefreshWorker.enqueueStartup(this@FreetiaApp)
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { httpClient }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.10).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheManager.logoDir)
                .maxSizeBytes(CacheManager.LOGO_CACHE_BYTES)
                .build()
        }
        .crossfade(true)
        .build()
}
