package com.freetia.tv.data.secure

import com.freetia.tv.core.security.EncryptionManager
import java.util.UUID

/**
 * Playlist source URLs live only in encrypted preferences (§5: they can carry tokens or credentials). Room's
 * `PlaylistEntity.sourceUrl` holds the reference returned by [put]. Never log a resolved URL.
 */
class PlaylistUrlStore(private val encryption: EncryptionManager) {

    /** Stores [url] and returns the reference to save in `PlaylistEntity.sourceUrl`. */
    fun put(url: String): String {
        val ref = REF_PREFIX + UUID.randomUUID()
        encryption.putSecret(ref, url)
        return ref
    }

    /** Replaces the URL behind an existing reference (playlist edit). */
    fun update(ref: String, url: String) {
        require(isRef(ref)) { "not a playlist URL reference" }
        encryption.putSecret(ref, url)
    }

    /** Resolves a reference to its URL; null if missing or if [ref] is not a reference. */
    fun get(ref: String): String? = if (isRef(ref)) encryption.getSecret(ref) else null

    fun remove(ref: String) {
        if (isRef(ref)) encryption.removeSecret(ref)
    }

    companion object {
        const val REF_PREFIX = "playlist_url:"
        fun isRef(value: String) = value.startsWith(REF_PREFIX)
    }
}
