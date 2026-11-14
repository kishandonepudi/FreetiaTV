package com.freetia.tv.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.EpgChannelEntity
import com.freetia.tv.data.model.EpgProgrammeEntity
import com.freetia.tv.data.model.PlaylistEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpgDaoGridSearchTest {

    private lateinit var db: FreetiaDatabase
    private lateinit var dao: EpgDao
    private var playlistId = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FreetiaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.epgDao()
        playlistId = db.playlistDao().insert(PlaylistEntity(name = "P", sourceUrl = "ref"))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun channel(
        name: String, tvgId: String, country: String = "", language: String = "", adult: Boolean = false,
    ): Long {
        val id = db.channelDao().insert(
            ChannelEntity(
                playlistId = playlistId, name = name, streamUrl = "http://x/$name", tvgId = tvgId,
                tvgCountry = country, tvgLanguage = language, isAdult = adult,
            ),
        )
        return dao.insertEpgChannel(EpgChannelEntity(channelId = id, epgChannelId = tvgId))
    }

    private fun prog(epg: Long, tvgId: String, start: Long, stop: Long, title: String, category: String = "") =
        EpgProgrammeEntity(epgChannelId = epg, tvgId = tvgId, start = start, stop = stop, title = title, category = category)

    @Test
    fun gridWindow_returnsOnlyOverlappingProgrammesForRequestedChannels() = runTest {
        val a = channel("A", "a.in")
        val b = channel("B", "b.in")
        dao.insertEpgProgrammes(
            listOf(
                prog(a, "a.in", 0, 100, "before"),
                prog(a, "a.in", 100, 200, "inside"),
                prog(a, "a.in", 190, 400, "overlapsEnd"),
                prog(a, "a.in", 500, 600, "after"),
                prog(b, "b.in", 100, 200, "other"),
            ),
        )

        val rows = dao.getGridWindow(listOf("a.in"), from = 100, to = 300)

        assertEquals(listOf("inside", "overlapsEnd"), rows.map { it.title })
    }

    @Test
    fun gridWindow_isOrderedByChannelThenStartAndHonoursLimit() = runTest {
        val a = channel("A", "a.in")
        val b = channel("B", "b.in")
        dao.insertEpgProgrammes(
            listOf(
                prog(b, "b.in", 10, 20, "b1"), prog(a, "a.in", 30, 40, "a2"), prog(a, "a.in", 10, 20, "a1"),
            ),
        )

        assertEquals(listOf("a1", "a2", "b1"), dao.getGridWindow(listOf("a.in", "b.in"), 0, 100).map { it.title })
        assertEquals(2, dao.getGridWindow(listOf("a.in", "b.in"), 0, 100, limit = 2).size)
    }

    @Test
    fun guideRefs_mapPlaylistChannelsToXmltvIds() = runTest {
        channel("A", "a.in")
        val ids = db.channelDao().getByPlaylist(playlistId).map { it.id }

        assertEquals(listOf("a.in"), dao.getEpgRefs(ids).map { it.epgChannelId })
        assertTrue(dao.getEpgRefs(listOf(9_999L)).isEmpty())
    }

    @Test
    fun search_matchesTitleGenreAndChannelName() = runTest {
        val news = channel("Daily Tonight", "n.in")
        dao.insertEpgProgrammes(
            listOf(
                prog(news, "n.in", 100, 200, "Evening Bulletin", category = "News"),
                prog(news, "n.in", 200, 300, "Cricket Live", category = "Sports"),
            ),
        )

        fun titles(q: String) = runBlockingSearch(q).map { it.title }

        assertEquals(listOf("Cricket Live"), titles("cricket"))
        assertEquals(listOf("Evening Bulletin"), titles("news"))
        assertEquals(listOf("Evening Bulletin", "Cricket Live"), titles("tonight"))
    }

    private fun runBlockingSearch(q: String, country: String = "", language: String = "", hideAdult: Boolean = false) =
        kotlinx.coroutines.runBlocking {
            dao.searchGuide(q, now = 0, playlistId = 0, hideAdult = hideAdult, hideDead = false, country = country, language = language)
        }

    @Test
    fun search_skipsFinishedProgrammesAndHonoursLimit() = runTest {
        val c = channel("C", "c.in")
        dao.insertEpgProgrammes((1..10).map { prog(c, "c.in", it * 100L, it * 100L + 50, "Show $it") })

        val future = dao.searchGuide("show", now = 500, playlistId = 0, hideAdult = false, hideDead = false, country = "", language = "")
        assertTrue(future.all { it.stop > 500 })

        val limited = dao.searchGuide("show", now = 0, playlistId = 0, hideAdult = false, hideDead = false, country = "", language = "", limit = 3)
        assertEquals(3, limited.size)
    }

    @Test
    fun search_childSafeHidesAdultChannels() = runTest {
        val ok = channel("Family", "f.in")
        val adult = channel("Late", "l.in", adult = true)
        dao.insertEpgProgrammes(listOf(prog(ok, "f.in", 100, 200, "Movie"), prog(adult, "l.in", 100, 200, "Movie")))

        assertEquals(2, runBlockingSearch("movie").size)
        assertEquals(listOf("Family"), runBlockingSearch("movie", hideAdult = true).map { it.channelName })
    }

    @Test
    fun search_regionFilterKeepsMatchingAndUntaggedChannels() = runTest {
        val india = channel("India One", "i.in", country = "IN", language = "hindi")
        val uk = channel("UK One", "u.uk", country = "GB;UK", language = "english")
        val untagged = channel("Plain", "p.xx")
        dao.insertEpgProgrammes(
            listOf(prog(india, "i.in", 100, 200, "Film"), prog(uk, "u.uk", 100, 200, "Film"), prog(untagged, "p.xx", 100, 200, "Film")),
        )

        assertEquals(setOf("India One", "Plain"), runBlockingSearch("film", country = "IN").map { it.channelName }.toSet())
        assertEquals(setOf("UK One", "Plain"), runBlockingSearch("film", language = "english").map { it.channelName }.toSet())
    }

    @Test
    fun search_treatsPercentLiterally_whenEscaped() = runTest {
        val c = channel("C", "c.in")
        dao.insertEpgProgrammes(listOf(prog(c, "c.in", 100, 200, "100% Funny"), prog(c, "c.in", 200, 300, "Plain")))

        val hits = dao.searchGuide("100\\%", now = 0, playlistId = 0, hideAdult = false, hideDead = false, country = "", language = "")
        assertEquals(listOf("100% Funny"), hits.map { it.title })
    }
}
