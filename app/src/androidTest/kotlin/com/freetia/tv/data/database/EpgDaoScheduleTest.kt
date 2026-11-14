package com.freetia.tv.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freetia.tv.data.model.EpgChannelEntity
import com.freetia.tv.data.model.EpgProgrammeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpgDaoScheduleTest {

    private lateinit var db: FreetiaDatabase
    private lateinit var dao: EpgDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FreetiaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.epgDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun epgChannel(channelId: Long, tvgId: String): Long =
        dao.insertEpgChannel(EpgChannelEntity(channelId = channelId, epgChannelId = tvgId))

    private fun programme(epgChannel: Long, start: Long, stop: Long, title: String) =
        EpgProgrammeEntity(epgChannelId = epgChannel, start = start, stop = stop, title = title)

    @Test
    fun programmesMatch_whenChannelIdMatches() = runTest {
        val mine = epgChannel(channelId = 1, tvgId = "")
        val other = epgChannel(channelId = 2, tvgId = "")
        dao.insertEpgProgrammes(listOf(programme(mine, 100, 200, "Mine"), programme(other, 100, 200, "Other")))

        val result = dao.observeSchedule(channelId = 1, tvgId = "x.in", from = 0, to = 1_000).first()

        assertEquals(listOf("Mine"), result.map { it.title })
    }

    @Test
    fun programmesMatch_whenTvgIdMatches() = runTest {
        val byTvg = epgChannel(channelId = 99, tvgId = "news.in")
        dao.insertEpgProgrammes(listOf(programme(byTvg, 100, 200, "News")))

        val result = dao.observeSchedule(channelId = 1, tvgId = "news.in", from = 0, to = 1_000).first()

        assertEquals(listOf("News"), result.map { it.title })
    }

    @Test
    fun emptyTvgIdDoesNotMatch_whenOtherRowsHaveEmptyEpgChannelId() = runTest {
        val unrelated = epgChannel(channelId = 2, tvgId = "")
        dao.insertEpgProgrammes(listOf(programme(unrelated, 100, 200, "Unrelated")))

        val result = dao.observeSchedule(channelId = 1, tvgId = "", from = 0, to = 1_000).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun overlappingProgrammesIncluded_whenTheyStartBeforeFromAndEndAfterIt() = runTest {
        val ch = epgChannel(channelId = 1, tvgId = "")
        dao.insertEpgProgrammes(
            listOf(
                programme(ch, 0, 100, "EndedAtFrom"),
                programme(ch, 50, 150, "Running"),
                programme(ch, 150, 200, "Inside"),
                programme(ch, 300, 400, "StartsAtTo"),
                programme(ch, 350, 450, "AfterTo"),
            )
        )

        val result = dao.observeSchedule(channelId = 1, tvgId = "", from = 100, to = 300, limit = 10).first()

        assertEquals(listOf("Running", "Inside"), result.map { it.title })
    }

    @Test
    fun resultsAreOrderedAndLimited_whenMoreThanLimitMatch() = runTest {
        val ch = epgChannel(channelId = 1, tvgId = "")
        dao.insertEpgProgrammes((5 downTo 0).map { programme(ch, it * 100L, it * 100L + 100, "P$it") })

        val result = dao.observeSchedule(channelId = 1, tvgId = "", from = 0, to = 10_000, limit = 3).first()

        assertEquals(listOf("P0", "P1", "P2"), result.map { it.title })
    }
}
