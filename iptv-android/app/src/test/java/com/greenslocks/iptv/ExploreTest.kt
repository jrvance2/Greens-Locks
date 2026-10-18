package com.greenslocks.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ExploreTest {
    private fun res(name: String) = javaClass.getResource("/$name")!!.readText()
    private fun mv(title: String, year: String = "") = DiscoverItem(1, false, title, year, "", "", 0.0)

    // ---- TMDB rows
    @Test fun parsesMovieRowsAndSkipsBrokenItems() {
        val items = TmdbDiscover.parse(res("tmdb_movies.json"), tv = false)
        assertEquals(2, items.size)
        val dune = items[0]
        assertEquals("Dune: Part Two", dune.title)
        assertEquals("2024", dune.year)
        assertEquals("https://image.tmdb.org/t/p/w342/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg", dune.poster)
        assertEquals("movie:693134", dune.key)
        assertEquals("", items[1].poster)
        assertEquals("", items[1].year)
    }

    @Test fun parsesSeriesRowsWithNameAndFirstAirDate() {
        val g = TmdbDiscover.parse(res("tmdb_tv.json"), tv = true).single()
        assertEquals("Game of Thrones", g.title)
        assertEquals("2011", g.year)
        assertTrue(g.tv)
        assertEquals("tv:1399", g.key)
    }

    @Test fun garbageGivesNoItems() {
        assertTrue(TmdbDiscover.parse("{}", false).isEmpty())
        assertTrue(TmdbDiscover.parse("""{"results":[]}""", true).isEmpty())
    }

    // ---- matching to your library
    @Test fun providerDecorationsDoNotStopAMatch() {
        assertEquals(LibraryMatch.key("Dune: Part Two"), LibraryMatch.key("|EN| Dune Part Two (2024) 4K"))
        assertEquals(LibraryMatch.key("Dune: Part Two"), LibraryMatch.key("Dune Part Two 2024"))
    }

    @Test fun searchQueryDropsPunctuationAndFillerWords() {
        assertEquals("dune part two", LibraryMatch.searchQuery("Dune: Part Two"))
        assertFalse(LibraryMatch.searchQuery("The Lord of the Rings").split(" ").contains("the"))
    }

    @Test fun prefersTheSameYearThenTheBestQuality() {
        val item = mv("Dune: Part Two", "2024")
        val hit = LibraryMatch.best(item, listOf(
            Entry(Kind.MOVIE, "1", "Dune Part Two (2021)"),
            Entry(Kind.MOVIE, "2", "Dune Part Two (2024) HD"),
            Entry(Kind.MOVIE, "3", "Dune Part Two (2024) 4K"),
        ))
        assertEquals("3", hit?.id)
    }

    @Test fun noMatchWhenTitlesDiffer() {
        assertNull(LibraryMatch.best(mv("The Batman"), listOf(Entry(Kind.MOVIE, "1", "Batman Begins"), Entry(Kind.MOVIE, "2", "Batman"))))
        assertNull(LibraryMatch.best(mv("X"), listOf(Entry(Kind.MOVIE, "1", "X"))))
    }

    // ---- Tonight
    private fun live() = listOf(
        Entry(Kind.LIVE, "10", "US| ESPN HD", num = 30, epgId = "ESPN.us"),
        Entry(Kind.LIVE, "11", "US| ESPN FHD", num = 31, epgId = ""),
        Entry(Kind.LIVE, "12", "NBC", num = 4),
        Entry(Kind.LIVE, "13", "Nick Jr", num = 9),
    )

    @Test fun mapsGuideChannelsToYourChannelsByIdThenName() {
        val idx = Tonight.indexLive(live())
        val rows = listOf(
            AirRow("espn.us", "SportsCenter", 100, 200),
            AirRow("nbc.us2", "Nightly News", 100, 200),
            AirRow("nickjr.us", "Bluey", 100, 200),
            AirRow("unknown.us", "Mystery", 100, 200),
        )
        val names = mapOf("nbc.us2" to listOf(normName("NBC")), "nickjr.us" to listOf(normName("Nick Jr HD")))
        val out = Tonight.build(rows, names, idx)
        assertEquals(listOf("NBC", "Nick Jr", "US| ESPN HD").map { it }, out.map { it.entry.title })
        assertEquals("news", out.first { it.entry.title == "NBC" }.genre)
        assertEquals("kids", out.first { it.entry.title == "Nick Jr" }.genre)
    }

    @Test fun duplicateQualityCopiesShowOnce() {
        val idx = Tonight.indexLive(live())
        val rows = listOf(AirRow("espn.us", "NBA Tonight", 100, 200), AirRow("espn2", "NBA Tonight", 100, 200))
        val out = Tonight.build(rows, mapOf("espn2" to listOf(normName("ESPN"))), idx)
        assertEquals(1, out.size)
    }

    @Test fun prefersTheHigherQualityCopyOfAChannel() {
        val idx = Tonight.indexLive(listOf(Entry(Kind.LIVE, "1", "ESPN"), Entry(Kind.LIVE, "2", "ESPN 4K"), Entry(Kind.LIVE, "3", "ESPN HD")))
        assertEquals("2", idx.byNorm[normName("ESPN")]?.id)
    }

    @Test fun timeChoicesAreNowLaterAndPrimeTime() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 18, 15, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        assertEquals(now, Tonight.timeFor(0, now))
        assertEquals(now + 7_200_000L, Tonight.timeFor(1, now))
        val prime = Calendar.getInstance().apply { timeInMillis = Tonight.timeFor(2, now) }
        assertEquals(20, prime.get(Calendar.HOUR_OF_DAY)); assertEquals(18, prime.get(Calendar.DAY_OF_MONTH))
        val late = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 18, 23, 30, 0) }.timeInMillis
        val next = Calendar.getInstance().apply { timeInMillis = Tonight.timeFor(2, late) }
        assertEquals(19, next.get(Calendar.DAY_OF_MONTH))
    }

    // ---- My agenda
    private fun games(name: String, key: String) = SportsParse.scoreboard(javaClass.getResource("/$name")!!.readText(), SPORT_LEAGUES.first { it.key == key })

    @Test fun agendaMixesRemindersRecordingsAndGamesInTimeOrder() {
        val now = 1_000_000_000_000L
        val ch = Entry(Kind.LIVE, "5", "ESPN")
        val upcoming = games("espn_nba.json", "nba")[0].copy(start = now + 3 * 3_600_000L)
        val finished = games("espn_epl.json", "epl")[0]
        val items = Agenda.build(
            now,
            reminders = listOf(Reminder(ch, now + 3_600_000L, "SportsCenter"), Reminder(ch, now - 3_600_000L, "Old")),
            jobs = listOf(RecJob("a", "u", "Movie Night", now + 2 * 3_600_000L, 3_600_000L), RecJob("b", "u", "Done", now - 5_000_000L, 1_000_000L)),
            games = listOf(upcoming, finished),
        )
        assertEquals(listOf("SportsCenter", "Movie Night", "Celtics at Pistons".let { items[2].title }), items.map { it.title })
        assertEquals(listOf(AgendaKind.REMINDER, AgendaKind.RECORDING, AgendaKind.GAME), items.map { it.kind })
        assertTrue(items[2].sub.startsWith("NBA"))
    }

    @Test fun aRecordingInProgressStaysOnTheAgenda() {
        val now = 1_000_000_000_000L
        val items = Agenda.build(now, emptyList(), listOf(RecJob("a", "u", "Live now", now - 600_000L, 3_600_000L)), emptyList())
        assertEquals(1, items.size)
    }

    @Test fun dayLabelsAreTodayTomorrowThenTheDate() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 18, 15, 0, 0) }.timeInMillis
        val later = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 18, 23, 59, 0) }.timeInMillis
        val tomorrow = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 19, 0, 5, 0) }.timeInMillis
        val far = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 24, 12, 0, 0) }.timeInMillis
        assertEquals("Today", Agenda.dayLabel(later, now))
        assertEquals("Tomorrow", Agenda.dayLabel(tomorrow, now))
        assertTrue(Agenda.dayLabel(far, now).contains("24"))
    }
}
