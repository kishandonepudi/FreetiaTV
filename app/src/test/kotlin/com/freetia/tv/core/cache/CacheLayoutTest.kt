package com.freetia.tv.core.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheLayoutTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, bytes: Int = 10) = File(dir, name).apply {
        parentFile?.mkdirs()
        writeBytes(ByteArray(bytes))
    }

    private fun setup(): Pair<File, CacheLayout> {
        val cacheDir = tmp.newFolder("cache")
        return cacheDir to CacheLayout(cacheDir)
    }

    @Test fun partialClearRemovesOnlyTheSelectedAreas() {
        val (_, layout) = setup()
        val logo = file(layout.logoDir, "a.png")
        val epg = file(layout.epgDir, "guide.xml")
        val temp = file(layout.tempDir, "t.tmp")
        val player = file(layout.playerDir, "seg.ts")
        val probe = file(layout.probeDir, "p.json")
        val crash = file(layout.crashDir, "c.txt")

        layout.clear(CacheSelection(logos = false, epg = true, temp = false, player = false, probe = true, crashLogs = false))

        assertTrue(logo.exists()); assertTrue(temp.exists()); assertTrue(player.exists()); assertTrue(crash.exists())
        assertFalse(epg.exists()); assertFalse(probe.exists())
    }

    @Test fun fullClearLeavesTheCacheDirEmptyAndKeepsFilesDir() {
        val (cacheDir, layout) = setup()
        val filesDir = tmp.newFolder("files")
        val settings = file(filesDir, "datastore/settings.preferences_pb")
        val db = file(filesDir, "freetia.db")
        file(layout.logoDir, "a.png", 100)
        file(layout.epgDir, "g.xml", 50)
        file(cacheDir, "stray.bin", 5)

        val freed = layout.clear(CacheSelection.ALL)

        assertEquals(155L, freed)
        assertEquals(emptyList<String>(), cacheDir.list()!!.toList())
        assertTrue(settings.exists()); assertTrue(db.exists())
    }

    @Test fun moveAsideEmptiesTheLiveTreeAndReportsTheTrash() {
        val (cacheDir, layout) = setup()
        file(layout.logoDir, "a.png")

        val trash = layout.moveAside()

        assertFalse(layout.root.exists())
        assertEquals(1, trash.size)
        trash.forEach { it.deleteRecursively() }
        assertEquals(0L, layout.sizeBytes())
        assertEquals(emptyList<String>(), cacheDir.list()!!.toList())
    }

    @Test fun clearingAnEmptyCacheIsFine() {
        val (_, layout) = setup()
        assertEquals(0L, layout.clear(CacheSelection.ALL))
    }

    @Test fun logoCapIsClamped() {
        assertEquals(50L * 1024 * 1024, CachePolicy(true, true, 50).logoCapBytes)
        assertEquals(10L * 1024 * 1024, CachePolicy(true, true, 1).logoCapBytes)
        assertEquals(500L * 1024 * 1024, CachePolicy(true, true, 9_999).logoCapBytes)
    }
}
