package com.freetia.tv.data.database

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.DEFAULT_PROFILE_ID
import com.freetia.tv.data.model.GroupChannelCrossRef
import com.freetia.tv.data.model.GroupEntity
import com.freetia.tv.data.model.PlaylistEntity
import com.freetia.tv.data.model.ProfileEntity
import com.freetia.tv.data.repository.StartupRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Library queries (§3.1 reorder and merged All, §3.3 favourites, groups and recents). */
@RunWith(AndroidJUnit4::class)
class LibraryDaoTest {
    private lateinit var db: FreetiaDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FreetiaDatabase::class.java)
            .addCallback(FreetiaDatabase.SeedDefaultProfile)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun playlist(name: String, priority: Int, default: Boolean = false) =
        db.playlistDao().insert(PlaylistEntity(name = name, sourceUrl = "playlist_url:$name", priority = priority, isDefault = default))

    private suspend fun channel(playlistId: Long, name: String, number: Int, group: String = "") =
        db.channelDao().insert(
            ChannelEntity(playlistId = playlistId, name = name, number = number, streamUrl = "http://s/$playlistId/$name", groupTitle = group)
        )

    private suspend fun names(source: PagingSource<Int, ChannelEntity>): List<String> {
        val page = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = false))
        return (page as PagingSource.LoadResult.Page).data.map { it.name }
    }

    @Test
    fun reorderRenumbersPrioritiesAndChangesWhichPlaylistOpens() = runTest {
        val a = playlist("A", 1)
        val b = playlist("B", 2)
        val c = playlist("C", 3)
        assertEquals(a, db.playlistDao().getFirstPlaylist()?.id)

        val moved = StartupRules.move(db.playlistDao().getAll(), -1) { it.id == c }
        db.playlistDao().reorderPlaylists(moved)

        assertEquals(listOf(a, c, b), db.playlistDao().getAll().map { it.id })
        assertEquals(listOf(1, 2, 3), db.playlistDao().getAll().map { it.priority })

        val toTop = StartupRules.move(db.playlistDao().getAll(), -1) { it.id == c }
        db.playlistDao().reorderPlaylists(toTop)
        assertEquals(c, db.playlistDao().getFirstPlaylist()?.id)
        assertEquals(b, db.playlistDao().getNextByPriority(2)?.id)
    }

    @Test
    fun onlyOnePlaylistIsDefault() = runTest {
        val a = playlist("A", 1, default = true)
        val b = playlist("B", 2)
        db.playlistDao().clearAllDefaults()
        db.playlistDao().setAsDefault(b)
        assertEquals(b, db.playlistDao().getDefaultPlaylist()?.id)
        assertFalse(db.playlistDao().getById(a)!!.isDefault)
    }

    @Test
    fun mergedAllViewFollowsPlaylistPriorityThenNumber() = runTest {
        val first = playlist("First", 1)
        val second = playlist("Second", 2)
        channel(second, "s2", 2)
        channel(second, "s1", 1)
        channel(first, "f2", 2)
        channel(first, "f1", 1)

        assertEquals(listOf("f1", "f2", "s1", "s2"), names(db.channelDao().pageAll(hideAdult = false, hideDead = false)))

        db.playlistDao().reorderPlaylists(db.playlistDao().getAll().reversed())
        assertEquals(listOf("s1", "s2", "f1", "f2"), names(db.channelDao().pageAll(hideAdult = false, hideDead = false)))
    }

    @Test
    fun searchSpansPlaylistsByNameAndGroup() = runTest {
        val a = playlist("A", 1)
        val b = playlist("B", 2)
        channel(a, "BBC One", 1, group = "UK")
        channel(b, "Sky", 1, group = "BBC Partners")
        channel(b, "Other", 2, group = "Misc")
        assertEquals(listOf("BBC One", "Sky"), names(db.channelDao().pageSearch("bbc", 0, hideAdult = false, hideDead = false)))
    }

    @Test
    fun favouritesAreGlobalAndPerProfile() = runTest {
        val a = playlist("A", 1)
        val b = playlist("B", 2)
        val c1 = channel(a, "one", 1)
        val c2 = channel(b, "two", 1)
        val kid = db.profileDao().insert(ProfileEntity(name = "Kid"))

        assertTrue(db.favouriteDao().toggle(c1, DEFAULT_PROFILE_ID))
        assertTrue(db.favouriteDao().toggle(c2, DEFAULT_PROFILE_ID))
        assertTrue(db.favouriteDao().toggle(c1, kid))

        assertEquals(setOf(c1, c2), db.favouriteDao().observeFavouriteIds(DEFAULT_PROFILE_ID).first().toSet())
        assertEquals(setOf(c1), db.favouriteDao().observeFavouriteIds(kid).first().toSet())
        assertEquals(2, names(db.favouriteDao().pageFavouriteChannels(DEFAULT_PROFILE_ID, hideAdult = false)).size)

        assertFalse(db.favouriteDao().toggle(c1, DEFAULT_PROFILE_ID))
        assertFalse(db.favouriteDao().isFavourite(c1, DEFAULT_PROFILE_ID))
        assertTrue(db.favouriteDao().isFavourite(c1, kid))
    }

    @Test
    fun refreshKeepsFavouritesOfChannelsThatSurvive() = runTest {
        val a = playlist("A", 1)
        val keep = channel(a, "keep", 1)
        val gone = channel(a, "gone", 2)
        db.favouriteDao().toggle(keep, DEFAULT_PROFILE_ID)
        db.favouriteDao().toggle(gone, DEFAULT_PROFILE_ID)

        val existing = db.channelDao().getById(keep)!!
        db.playlistSyncDao().updateSource(
            listOf(
                ChannelSourceUpdate(
                    id = keep, tvgId = "", tvgName = "", tvgLogo = "", name = "keep renamed", number = existing.number,
                    groupTitle = "", tvgCountry = "", tvgLanguage = "", tvgType = "", catchupEnabled = false,
                    catchup = "", catchupSource = "", catchupDays = 0, isAdult = false,
                )
            )
        )
        db.playlistSyncDao().deleteByIds(listOf(gone))

        assertEquals("keep renamed", db.channelDao().getById(keep)!!.name)
        assertEquals(setOf(keep), db.favouriteDao().observeFavouriteIds(DEFAULT_PROFILE_ID).first().toSet())
    }

    @Test
    fun recentsKeepOnlyTheLastTwentyPerProfile() = runTest {
        val a = playlist("A", 1)
        val kid = db.profileDao().insert(ProfileEntity(name = "Kid"))
        val ids = (1..30).map { channel(a, "c$it", it) }
        ids.forEachIndexed { i, id -> db.recentChannelDao().record(id, DEFAULT_PROFILE_ID, time = 1_000L + i) }
        db.recentChannelDao().record(ids[0], kid, time = 9_000L)

        assertEquals(20, db.recentChannelDao().count(DEFAULT_PROFILE_ID))
        assertEquals(1, db.recentChannelDao().count(kid))
        val newest = db.recentChannelDao().observeRecentChannels(DEFAULT_PROFILE_ID, hideAdult = false).first()
        assertEquals(ids.last(), newest.first().id)
        assertEquals(ids[10], newest.last().id)
    }

    @Test
    fun groupsHoldChannelsFromAnyPlaylistAndAreRemovedWithTheirChannels() = runTest {
        val a = playlist("A", 1)
        val b = playlist("B", 2)
        val c1 = channel(a, "cartoon", 1)
        val c2 = channel(b, "news", 1)
        val kids = db.groupDao().insertGroup(GroupEntity(name = "Kids"))
        db.groupDao().addChannelToGroup(GroupChannelCrossRef(groupId = kids, channelId = c1))
        db.groupDao().addChannelToGroup(GroupChannelCrossRef(groupId = kids, channelId = c2))
        db.groupDao().addChannelToGroup(GroupChannelCrossRef(groupId = kids, channelId = c2))

        assertEquals(2, db.groupDao().observeGroupChannelCount(kids).first())
        assertEquals(listOf("Kids"), db.groupDao().getGroupsContainingChannel(c1, DEFAULT_PROFILE_ID).map { it.name })

        db.playlistSyncDao().deleteByIds(listOf(c2))
        assertEquals(listOf("cartoon"), names(db.groupDao().pageGroupChannels(kids, hideAdult = false)))
    }
}
