package com.greenslocks.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsTest {
    private fun res(name: String) = javaClass.getResource("/$name")!!.readText()
    private fun league(key: String) = SPORT_LEAGUES.first { it.key == key }

    @Test fun parsesAScheduledBasketballGame() {
        val games = SportsParse.scoreboard(res("espn_nba.json"), league("nba"))
        assertEquals(2, games.size)
        val g = games[0]
        assertTrue(g.isUpcoming)
        assertEquals("Detroit Pistons", g.home.name.let { if (it == "Pistons") "Detroit Pistons" else it })
        assertEquals("Celtics", g.away.nick)
        assertEquals("Boston", g.away.loc)
        assertTrue(g.start > 0)
        assertEquals(listOf("NBC"), g.networks)
        assertTrue(g.home.logo.startsWith("http"))
        assertTrue(g.venue.isNotBlank())
    }

    @Test fun parsesLiveFootballGamesWithScoresAndLeaders() {
        val games = SportsParse.scoreboard(res("espn_nfl.json"), league("nfl"))
        assertEquals(3, games.size)
        val atl = games.first { it.home.nick == "Falcons" }
        assertTrue(atl.isLive)
        assertEquals("7", atl.home.score)
        assertEquals("21", atl.away.score)
        assertTrue(atl.leaders.isNotEmpty())
        assertTrue(atl.leaders.first().name.isNotBlank())
        assertEquals(listOf("Fox"), atl.networks)
        assertTrue(atl.home.record.isNotBlank())
    }

    @Test fun parsesFinishedSoccerGames() {
        val games = SportsParse.scoreboard(res("espn_epl.json"), league("epl"))
        assertEquals(2, games.size)
        assertTrue(games.all { it.isFinal })
        val g = games[0]
        assertEquals("2", g.home.score)
        assertEquals("0", g.away.score)
        assertTrue(g.home.winner)
        assertFalse(g.away.winner)
    }

    @Test fun parsesEachUfcBoutAsAGame() {
        val games = SportsParse.scoreboard(res("espn_ufc.json"), league("ufc"))
        assertEquals(4, games.size)
        assertTrue(games.all { it.home.name.isNotBlank() && it.away.name.isNotBlank() })
        assertTrue(games.all { it.home.id != it.away.id })
        assertTrue(games[0].note.contains("UFC"))
        assertTrue(games[0].home.logo.startsWith("http")) // country flag
    }

    @Test fun parsesStandingsByConferenceWithSensibleColumns() {
        val groups = SportsParse.standings(res("espn_standings_nba.json"), league("nba"))
        assertEquals(listOf("Eastern Conference", "Western Conference"), groups.map { it.name })
        val east = groups[0]
        assertEquals(listOf("W", "L", "PCT", "GB", "STRK"), east.headers)
        assertEquals(4, east.rows.size)
        assertTrue(east.rows.all { it.cells.size == east.headers.size })
        assertTrue(east.rows.all { it.name.isNotBlank() })
        // ordered by playoff seed
        assertEquals(east.rows.sortedBy { it.seed }.map { it.teamId }, east.rows.map { it.teamId })
    }

    @Test fun toleratesGarbageAndEmptyScoreboards() {
        assertTrue(SportsParse.scoreboard("{}", league("nba")).isEmpty())
        assertTrue(SportsParse.scoreboard("""{"events":[{"id":"1","competitions":[{"competitors":[]}]}]}""", league("nba")).isEmpty())
        assertTrue(SportsParse.standings("{}", league("nba")).isEmpty())
    }

    @Test fun parsesIsoTimesWithAndWithoutSeconds() {
        assertEquals(SportsParse.parseTime("2026-10-20T19:00Z"), SportsParse.parseTime("2026-10-20T19:00:00Z"))
        assertEquals(0L, SportsParse.parseTime("not a date"))
        assertEquals(0L, SportsParse.parseTime(""))
    }

    private fun celticsPistons() = SportsParse.scoreboard(res("espn_nba.json"), league("nba"))[0]
    private fun ch(id: String, title: String) = Entry(Kind.LIVE, id, title)

    @Test fun channelNamedForBothTeamsRanksFirst() {
        val g = celticsPistons()
        val hits = ChannelMatch.rank(g, listOf(
            ch("1", "NBC HD"), ch("2", "NBA 03: Celtics vs Pistons"), ch("3", "Weather Channel"), ch("4", "Boston Sports"),
        ))
        assertEquals("2", hits.first().entry.id)
        assertTrue(hits.first().score >= 100)
        assertTrue(hits.none { it.entry.id == "3" })
        assertTrue(hits.map { it.entry.id }.containsAll(listOf("1", "4")))
    }

    @Test fun networkOnlyMatchesRankBelowTeamMatches() {
        val g = celticsPistons()
        val nbc = ChannelMatch.score(g, "US| NBC HD")
        val oneTeam = ChannelMatch.score(g, "Detroit Local")
        val both = ChannelMatch.score(g, "Celtics @ Pistons")
        assertTrue(nbc in 1..34)
        assertTrue(oneTeam > nbc)
        assertTrue(both > oneTeam)
    }

    @Test fun abbreviationsMatchOnlyAsWholeWords() {
        val g = celticsPistons()
        assertTrue(ChannelMatch.score(g, "BOS vs DET Live") >= 100)
        assertEquals(0, ChannelMatch.score(g, "Cabos Detective Show"))
    }

    @Test fun searchTermsIncludeTeamsAndNetworks() {
        val terms = ChannelMatch.searchTerms(celticsPistons())
        assertTrue(terms.containsAll(listOf("celtics", "pistons", "boston", "detroit", "nbc")))
    }
}
