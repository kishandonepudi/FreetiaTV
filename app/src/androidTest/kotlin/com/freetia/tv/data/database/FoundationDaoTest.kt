package com.freetia.tv.data.database

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freetia.tv.data.model.CategoryLockEntity
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.DEFAULT_PROFILE_ID
import com.freetia.tv.data.model.LockScope
import com.freetia.tv.data.model.LockedChannelEntity
import com.freetia.tv.data.model.PlaylistEntity
import com.freetia.tv.data.model.ProfileEntity
import com.freetia.tv.data.model.ReminderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Wave 1 queries with non-trivial SQL: profiles, recents trimming, favourites, locks, paging filters, reminders. */
@RunWith(AndroidJUnit4::class)
class FoundationDaoTest {
    private lateinit var db: FreetiaDatabase
    private var playlistId = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FreetiaDatabase::class.java)
            .addCallback(FreetiaDatabase.SeedDefaultProfile)
            .allowMainThreadQueries()
            .build()
        playlistId = db.playlistDao().insert(PlaylistEntity(name = "P", sourceUrl = "playlist_url:x", priority = 1))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun channel(name: String, number: Int, group: String = "", country: String = "", adult: Boolean = false) =
        db.channelDao().insert(
            ChannelEntity(
                playlistId = playlistId, name = name, number = number, streamUrl = "http://s/$name",
                groupTitle = group, tvgCountry = country, isAdult = adult,
            )
        )

    @Test
    fun defaultProfileIsSeededAndCannotBeDeleted() = runTest {
        assertEquals("Me", db.profileDao().getById(DEFAULT_PROFILE_ID)?.name)
        db.profileDao().deleteById(DEFAULT_PROFILE_ID)
        assertEquals(1, db.profileDao().count())
    }

    @Test
    fun recentsKeepNewestTwentyPerProfile() = runTest {
        val kid = db.profileDao().insert(ProfileEntity(name = "Kid", isChild = true))
        val ids = (1..25).map { channel("c$it", it) }
        ids.forEachIndexed { i, id -> db.recentChannelDao().record(id, DEFAULT_PROFILE_ID, time = 1_000L + i) }
        db.recentChannelDao().record(ids[0], kid, time = 5_000L)
        // Re-watching moves a channel to the top instead of duplicating it.
        db.recentChannelDao().record(ids[10], DEFAULT_PROFILE_ID, time = 9_000L)

        val recent = db.recentChannelDao().observeRecentChannels(DEFAULT_PROFILE_ID, hideAdult = false).first()
        assertEquals(20, recent.size)
        assertEquals(ids[10], recent.first().id)
        assertEquals(20, db.recentChannelDao().count(DEFAULT_PROFILE_ID))
        assertEquals(1, db.recentChannelDao().count(kid))
    }

    @Test
    fun favouritesTogglePerProfileAndHideAdult() = runTest {
        val news = channel("news", 1)
        val adult = channel("late", 2, adult = true)
        assertTrue(db.favouriteDao().toggle(news, DEFAULT_PROFILE_ID))
        assertTrue(db.favouriteDao().toggle(adult, DEFAULT_PROFILE_ID))
        assertEquals(2, db.favouriteDao().observeFavouriteChannels(DEFAULT_PROFILE_ID, hideAdult = false).first().size)
        assertEquals(listOf(news), db.favouriteDao().observeFavouriteChannels(DEFAULT_PROFILE_ID, hideAdult = true).first().map { it.id })
        assertFalse(db.favouriteDao().toggle(news, DEFAULT_PROFILE_ID))
        assertFalse(db.favouriteDao().isFavourite(news, DEFAULT_PROFILE_ID))
    }

    @Test
    fun locksMatchChannelGroupSegmentAndRegion() = runTest {
        val single = channel("a", 1)
        val kids = channel("b", 2, group = "Movies; Kids")
        val kidsTv = channel("c", 3, group = "KidsTV")
        val us = channel("d", 4, country = "IN;US")
        val free = channel("e", 5, group = "News", country = "IN")
        val dao = db.lockedChannelDao()

        dao.insert(LockedChannelEntity(channelId = single))
        dao.insertCategoryLock(CategoryLockEntity(scope = LockScope.GROUP, value = "kids"))
        dao.insertCategoryLock(CategoryLockEntity(scope = LockScope.REGION, value = "US"))

        assertTrue(dao.isChannelLocked(single))
        assertTrue("group segments match case-insensitively", dao.isChannelLocked(kids))
        assertFalse("a group is matched whole, not as a prefix", dao.isChannelLocked(kidsTv))
        assertTrue(dao.isChannelLocked(us))
        assertFalse(dao.isChannelLocked(free))
        assertEquals(setOf(single, kids, us), dao.observeLockedIds(playlistId).first().toSet())

        dao.removeCategoryLock(LockScope.GROUP, "Kids")
        assertFalse(dao.isChannelLocked(kids))
    }

    @Test
    fun pagingFiltersByCategoryAdultAndDead() = runTest {
        channel("n1", 1, group = "News")
        channel("n2", 2, group = "Sport;News")
        channel("x", 3, group = "News", adult = true)
        val dead = channel("n4", 4, group = "News")
        db.channelDao().setDead(dead, true)

        val source = db.channelDao().pageByCategory(playlistId, "News", hideAdult = true, hideDead = true)
        val page = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false))
        val names = (page as PagingSource.LoadResult.Page).data.map { it.name }
        assertEquals(listOf("n1", "n2"), names)
    }

    @Test
    fun reminderSurvivesEpgRefreshAndReplacesOnSameProgramme() = runTest {
        val ch = channel("a", 1)
        val dao = db.reminderDao()
        dao.upsert(ReminderEntity(channelId = ch, title = "Show", reminderTime = 900, startTime = 1_000, stopTime = 2_000))
        dao.upsert(ReminderEntity(channelId = ch, title = "Show", reminderTime = 800, startTime = 1_000, stopTime = 2_000, autoTune = true))
        db.epgDao().deleteAllProgrammes()

        val upcoming = dao.observeUpcoming(now = 500).first()
        assertEquals(1, upcoming.size)
        assertTrue(upcoming.single().autoTune)
        assertEquals(1, dao.getDue(now = 850).size)
    }
}
