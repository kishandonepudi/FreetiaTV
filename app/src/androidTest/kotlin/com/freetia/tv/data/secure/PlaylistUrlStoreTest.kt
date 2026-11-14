package com.freetia.tv.data.secure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freetia.tv.core.security.EncryptionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistUrlStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = PlaylistUrlStore(EncryptionManager(context))

    @Test
    fun roundTripUpdateAndRemove() {
        val url = "https://example.test/list.m3u?token=abc123"
        val ref = store.put(url)
        assertTrue(PlaylistUrlStore.isRef(ref))
        assertFalse("the reference must not contain the URL", ref.contains("example.test"))
        assertEquals(url, store.get(ref))

        store.update(ref, "https://example.test/other.m3u")
        assertEquals("https://example.test/other.m3u", store.get(ref))

        store.remove(ref)
        assertNull(store.get(ref))
    }

    @Test
    fun plainValuesAreNeverTreatedAsReferences() {
        assertNull(store.get("https://example.test/list.m3u"))
        assertNull(store.get(""))
    }

    @Test
    fun urlIsNotStoredInPlainTextPreferences() {
        val url = "https://example.test/secret-path.m3u"
        store.put(url)
        val raw = context.getSharedPreferences("encrypted_preferences", Context.MODE_PRIVATE).all.values
        assertFalse(raw.any { it.toString().contains("secret-path") })
    }
}
