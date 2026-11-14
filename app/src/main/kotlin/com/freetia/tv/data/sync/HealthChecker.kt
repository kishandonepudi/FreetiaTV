package com.freetia.tv.data.sync

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.withTransaction
import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.model.PlaylistHealthCheckEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class HealthProgress(val playlistId: Long, val checked: Int, val total: Int, val dead: Int, val done: Boolean)

/**
 * §3.15 playlist health check. Bounded: at most [MAX_PER_RUN] channels per run, only ones not checked in the last
 * [RECHECK_AFTER_MS]; throttled: small batches with a pause between them; never logs or stores a stream URL.
 */
class HealthChecker(private val app: FreetiaApp) {
    private val db get() = app.database
    private val client: OkHttpClient by lazy {
        app.httpClient.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
    private val running = Mutex()
    private val _progress = MutableStateFlow<HealthProgress?>(null)
    val progress: StateFlow<HealthProgress?> = _progress.asStateFlow()

    /**
     * Returns the number of channels flagged dead in this run, or a negative code (nothing more was written):
     * [BUSY] a run was already in progress, [OFFLINE] no validated network, [DEFERRED] memory or playback.
     */
    suspend fun run(playlistId: Long, force: Boolean = false): Int {
        if (!running.tryLock()) return BUSY
        try {
            return withContext(Dispatchers.IO) { check(playlistId, force) }
        } finally {
            running.unlock()
        }
    }

    private suspend fun check(playlistId: Long, force: Boolean): Int {
        // Offline or behind a captive portal every probe fails; that must not flag the whole playlist dead.
        if (!hasValidatedNetwork()) return OFFLINE
        val keys = db.playlistSyncDao().existingKeys(playlistId)
        val lastChecked = db.healthCheckDao().getAll().associate { it.channelId to it.lastCheckTime }
        val cutoff = System.currentTimeMillis() - RECHECK_AFTER_MS
        val todo = keys
            .filter { it.streamUrl.startsWith("http://", true) || it.streamUrl.startsWith("https://", true) }
            .filter { force || (lastChecked[it.id] ?: 0L) < cutoff }
            .take(MAX_PER_RUN)
        var checked = 0
        var dead = 0
        var outcome: Int? = null
        _progress.value = HealthProgress(playlistId, 0, todo.size, 0, false)
        try {
            for ((index, batch) in todo.chunked(BATCH).withIndex()) {
                // Background work yields to the player and the UI when memory is tight.
                if (app.memoryGuard.shouldDeferBackgroundWork()) { outcome = DEFERRED; break }
                val results = coroutineScope {
                    batch.map { key -> async { key.id to probe(key.streamUrl) } }.awaitAll()
                }
                if (ProbeRules.looksOffline(results.map { it.second }, firstBatch = index == 0)) { outcome = OFFLINE; break }
                // Connectivity errors are inconclusive: no record, so the channel is checked again next run.
                val conclusive = results.filter { it.second != Probe.NETWORK }
                val now = System.currentTimeMillis()
                db.withTransaction {
                    for ((id, result) in conclusive) {
                        val alive = result == Probe.ALIVE
                        db.healthCheckDao().insert(
                            PlaylistHealthCheckEntity(
                                channelId = id, lastCheckTime = now, isHealthy = alive,
                                errorMessage = if (alive) "" else "No response",
                            ),
                        )
                    }
                    val (aliveIds, deadIds) = conclusive.partition { it.second == Probe.ALIVE }
                    if (aliveIds.isNotEmpty()) db.channelDao().setDead(aliveIds.map { it.first }, false)
                    if (deadIds.isNotEmpty()) db.channelDao().setDead(deadIds.map { it.first }, true)
                    dead += deadIds.size
                }
                checked += batch.size
                _progress.value = HealthProgress(playlistId, checked, todo.size, dead, false)
                delay(PAUSE_BETWEEN_BATCHES_MS)
            }
        } catch (e: CancellationException) {
            _progress.value = null
            throw e
        }
        _progress.value = HealthProgress(playlistId, checked, todo.size, dead, true)
        return outcome ?: dead
    }

    private fun hasValidatedNetwork(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** HEAD first; some servers refuse it, so any failure is retried once as a GET whose body is never read. */
    private fun probe(url: String): Probe {
        var result = Probe.NETWORK
        for (method in arrayOf("HEAD", "GET")) {
            val attempt = try {
                val builder = Request.Builder().url(url)
                if (method == "GET") builder.get().header("Range", "bytes=0-0") else builder.head()
                client.newCall(builder.build()).execute().use { ProbeRules.ofStatus(it.code) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProbeRules.ofError(e)
            }
            result = ProbeRules.combine(result, attempt)
            if (result == Probe.ALIVE) break
        }
        return result
    }

    /** Un-flags every dead stream of a playlist (e.g. after the user fixed their network). */
    suspend fun clear(playlistId: Long) {
        db.channelDao().clearDead(playlistId)
    }

    companion object {
        const val BUSY = -1
        const val OFFLINE = -2
        const val DEFERRED = -3
        const val MAX_PER_RUN = 300
        const val RECHECK_AFTER_MS = 24 * 60 * 60_000L
        private const val BATCH = 6
        private const val PAUSE_BETWEEN_BATCHES_MS = 150L

        @Volatile private var instance: HealthChecker? = null

        fun get(app: FreetiaApp): HealthChecker =
            instance ?: synchronized(this) { instance ?: HealthChecker(app).also { instance = it } }
    }
}

enum class Probe { ALIVE, DEAD, NETWORK }

/** Pure health-probe classification, so a dead network is never mistaken for dead streams. */
object ProbeRules {
    fun ofStatus(code: Int): Probe = if (code in 200..399) Probe.ALIVE else Probe.DEAD

    /**
     * Not resolving, not connecting or timing out says nothing about the stream (offline, captive portal, flaky
     * Wi-Fi). Any other failure (TLS, protocol, reset after connect) came from a reachable host.
     */
    fun ofError(e: Throwable): Probe = when (e) {
        is UnknownHostException, is ConnectException, is NoRouteToHostException, is InterruptedIOException -> Probe.NETWORK
        else -> Probe.DEAD
    }

    /** HEAD then GET: either answering is alive; a host that answered once was reachable, so its failure is dead. */
    fun combine(a: Probe, b: Probe): Probe = when {
        a == Probe.ALIVE || b == Probe.ALIVE -> Probe.ALIVE
        a == Probe.DEAD || b == Probe.DEAD -> Probe.DEAD
        else -> Probe.NETWORK
    }

    /** The first batch failing only with connectivity errors means the network is down, not the streams. */
    fun looksOffline(results: List<Probe>, firstBatch: Boolean): Boolean =
        firstBatch && results.isNotEmpty() && results.all { it == Probe.NETWORK }
}
