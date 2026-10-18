package com.greenslocks.iptv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

enum class SportKind { BASKETBALL, FOOTBALL, BASEBALL, HOCKEY, SOCCER, COMBAT }

data class SportLeague(val key: String, val name: String, val short: String, val path: String, val kind: SportKind)

val SPORT_LEAGUES = listOf(
    SportLeague("nfl", "NFL", "NFL", "football/nfl", SportKind.FOOTBALL),
    SportLeague("nba", "NBA", "NBA", "basketball/nba", SportKind.BASKETBALL),
    SportLeague("mlb", "MLB", "MLB", "baseball/mlb", SportKind.BASEBALL),
    SportLeague("nhl", "NHL", "NHL", "hockey/nhl", SportKind.HOCKEY),
    SportLeague("ncaaf", "College football", "NCAAF", "football/college-football", SportKind.FOOTBALL),
    SportLeague("ncaam", "College basketball", "NCAAM", "basketball/mens-college-basketball", SportKind.BASKETBALL),
    SportLeague("wnba", "WNBA", "WNBA", "basketball/wnba", SportKind.BASKETBALL),
    SportLeague("mls", "MLS", "MLS", "soccer/usa.1", SportKind.SOCCER),
    SportLeague("epl", "Premier League", "EPL", "soccer/eng.1", SportKind.SOCCER),
    SportLeague("laliga", "La Liga", "LaLiga", "soccer/esp.1", SportKind.SOCCER),
    SportLeague("bundesliga", "Bundesliga", "BUN", "soccer/ger.1", SportKind.SOCCER),
    SportLeague("seriea", "Serie A", "Serie A", "soccer/ita.1", SportKind.SOCCER),
    SportLeague("ucl", "Champions League", "UCL", "soccer/uefa.champions", SportKind.SOCCER),
    SportLeague("ufc", "UFC", "UFC", "mma/ufc", SportKind.COMBAT),
)
val DEFAULT_SPORT_LEAGUES = listOf("nfl", "nba", "mlb", "nhl", "ncaaf", "ncaam", "mls", "epl", "ucl", "ufc")

data class TeamLine(
    val id: String, val name: String, val nick: String, val loc: String, val abbr: String,
    val logo: String, val score: String, val record: String, val winner: Boolean, val lines: List<String>,
)

data class Leader(val label: String, val team: String, val name: String, val line: String)

data class Game(
    val id: String, val league: String, val start: Long,
    /** "pre" (not started), "in" (live) or "post" (finished). */
    val state: String,
    val status: String, val detail: String,
    val home: TeamLine, val away: TeamLine,
    val networks: List<String>, val venue: String, val note: String, val leaders: List<Leader>,
) {
    val isLive get() = state == "in"
    val isFinal get() = state == "post"
    val isUpcoming get() = state == "pre"
}

data class StandRow(val teamId: String, val name: String, val abbr: String, val logo: String, val seed: Int, val cells: List<String>)
data class StandGroup(val name: String, val headers: List<String>, val rows: List<StandRow>)

/** Reads ESPN's public scoreboard and standings JSON. Kept free of Android classes so it can be unit tested. */
object SportsParse {
    private val iso = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }
    private val isoSec = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }

    fun parseTime(s: String): Long {
        if (s.isBlank()) return 0L
        return runCatching { iso.get()!!.parse(s)!!.time }.getOrNull()
            ?: runCatching { isoSec.get()!!.parse(s)!!.time }.getOrDefault(0L)
    }

    private fun JSONObject.arr(name: String): List<JSONObject> {
        val a: JSONArray = optJSONArray(name) ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    private fun teamLine(o: JSONObject): TeamLine {
        val t = o.optJSONObject("team")
        val a = o.optJSONObject("athlete")
        val full = t?.optString("displayName").orEmpty().ifEmpty { a?.optString("displayName").orEmpty() }
        val short = t?.optString("shortDisplayName").orEmpty().ifEmpty { a?.optString("shortName").orEmpty() }.ifEmpty { full }
        val nick = t?.optString("name").orEmpty().ifEmpty { full.substringAfterLast(' ') }
        val logo = t?.optString("logo").orEmpty()
            .ifEmpty { t?.optJSONArray("logos")?.optJSONObject(0)?.optString("href").orEmpty() }
            .ifEmpty { a?.optJSONObject("flag")?.optString("href").orEmpty() }
            .ifEmpty { a?.optJSONObject("headshot")?.optString("href").orEmpty() }
        val records = o.arr("records")
        val record = (records.firstOrNull { it.optString("name") == "overall" || it.optString("type") == "total" } ?: records.firstOrNull())
            ?.optString("summary").orEmpty()
        val lines = o.arr("linescores").map { l -> l.optString("displayValue").ifEmpty { l.optDouble("value", 0.0).toInt().toString() } }
        return TeamLine(
            id = t?.optString("id").orEmpty().ifEmpty { a?.optString("id").orEmpty() }.ifEmpty { o.optString("id") },
            name = short, nick = nick, loc = t?.optString("location").orEmpty(),
            abbr = t?.optString("abbreviation").orEmpty().ifEmpty { short.take(3).uppercase() },
            logo = logo, score = o.optString("score"), record = record, winner = o.optBoolean("winner", false), lines = lines,
        )
    }

    private fun leadersOf(c: JSONObject, comps: List<JSONObject>, byId: Map<String, String>): List<Leader> {
        val out = ArrayList<Leader>()
        fun read(cats: List<JSONObject>, defaultTeam: String) {
            for (cat in cats) {
                val label = cat.optString("shortDisplayName").ifEmpty { cat.optString("displayName") }
                val l = cat.optJSONArray("leaders")?.optJSONObject(0) ?: continue
                val name = l.optJSONObject("athlete")?.optString("shortName").orEmpty()
                if (name.isEmpty()) continue
                val team = byId[l.optJSONObject("team")?.optString("id").orEmpty()] ?: defaultTeam
                out += Leader(label, team, name, l.optString("displayValue"))
            }
        }
        read(c.arr("leaders"), "")
        for (comp in comps) read(comp.arr("leaders"), byId[comp.optJSONObject("team")?.optString("id").orEmpty()].orEmpty())
        return out.take(8)
    }

    private fun game(ev: JSONObject, c: JSONObject, league: SportLeague): Game? {
        val comps = c.arr("competitors")
        if (comps.size < 2) return null
        val combat = league.kind == SportKind.COMBAT
        val ordered = comps.sortedBy { it.optInt("order", 0) }
        val home = comps.firstOrNull { it.optString("homeAway") == "home" } ?: ordered[1]
        val away = comps.firstOrNull { it.optString("homeAway") == "away" } ?: ordered.first { it !== home }
        val statusObj = c.optJSONObject("status") ?: ev.optJSONObject("status")
        val type = statusObj?.optJSONObject("type")
        val homeLine = teamLine(home)
        val awayLine = teamLine(away)
        val nets = LinkedHashSet<String>()
        c.arr("broadcasts").forEach { b -> b.optJSONArray("names")?.let { n -> for (i in 0 until n.length()) nets += n.optString(i) } }
        c.arr("geoBroadcasts").forEach { g -> g.optJSONObject("media")?.optString("shortName")?.takeIf { it.isNotBlank() }?.let { nets += it } }
        val note = if (combat) ev.optString("name") else c.arr("notes").firstOrNull()?.optString("headline").orEmpty()
        val start = parseTime(c.optString("date").ifEmpty { ev.optString("date") })
        return Game(
            id = c.optString("id").ifEmpty { ev.optString("id") },
            league = league.key, start = start,
            state = type?.optString("state").orEmpty().ifEmpty { "pre" },
            status = type?.optString("shortDetail").orEmpty(), detail = type?.optString("detail").orEmpty(),
            home = homeLine, away = awayLine,
            networks = nets.filter { it.isNotBlank() }.take(4),
            venue = c.optJSONObject("venue")?.optString("fullName").orEmpty(), note = note,
            leaders = leadersOf(c, comps, mapOf(homeLine.id to homeLine.abbr, awayLine.id to awayLine.abbr)),
        )
    }

    fun scoreboard(text: String, league: SportLeague): List<Game> {
        val root = JSONObject(text)
        val out = ArrayList<Game>()
        for (ev in root.arr("events")) for (c in ev.arr("competitions")) game(ev, c, league)?.let { out += it }
        return out
    }

    private fun spec(kind: SportKind): List<Pair<String, String>> = when (kind) {
        SportKind.BASKETBALL, SportKind.BASEBALL -> listOf("wins" to "W", "losses" to "L", "winPercent" to "PCT", "gamesBehind" to "GB", "streak" to "STRK")
        SportKind.HOCKEY -> listOf("wins" to "W", "losses" to "L", "otLosses" to "OTL", "points" to "PTS", "streak" to "STRK")
        SportKind.FOOTBALL -> listOf("wins" to "W", "losses" to "L", "ties" to "T", "winPercent" to "PCT")
        SportKind.SOCCER -> listOf("gamesPlayed" to "GP", "wins" to "W", "ties" to "D", "losses" to "L", "pointDifferential" to "GD", "points" to "PTS")
        SportKind.COMBAT -> emptyList()
    }

    private fun group(name: String, entries: List<JSONObject>, kind: SportKind): StandGroup {
        val statMaps = entries.map { e -> e.arr("stats").associate { it.optString("name") to it.optString("displayValue") } }
        val cols = spec(kind).filter { (key, _) -> statMaps.any { m -> m[key]?.isNotBlank() == true } }
        val rows = entries.mapIndexed { i, e ->
            val t = e.optJSONObject("team")
            val m = statMaps[i]
            StandRow(
                teamId = t?.optString("id").orEmpty(),
                name = t?.optString("displayName").orEmpty(),
                abbr = t?.optString("abbreviation").orEmpty(),
                logo = t?.optJSONArray("logos")?.optJSONObject(0)?.optString("href").orEmpty(),
                seed = (m["playoffSeed"] ?: m["rank"])?.toIntOrNull() ?: 0,
                cells = cols.map { (key, _) -> m[key]?.takeIf { it.isNotBlank() } ?: "-" },
            )
        }
        val ordered = if (rows.isNotEmpty() && rows.all { it.seed > 0 }) rows.sortedBy { it.seed } else rows
        return StandGroup(name, cols.map { it.second }, ordered)
    }

    fun standings(text: String, league: SportLeague): List<StandGroup> {
        val out = ArrayList<StandGroup>()
        fun walk(node: JSONObject, name: String) {
            val title = node.optString("name").ifEmpty { name }
            val entries = node.optJSONObject("standings")?.arr("entries").orEmpty()
            if (entries.isNotEmpty()) out += group(title, entries, league.kind)
            node.arr("children").forEach { walk(it, title) }
        }
        walk(JSONObject(text), "")
        return out
    }
}

/** Works out which of your channels carry a game: team names in the channel name, then the TV network. */
object ChannelMatch {
    data class Hit(val entry: Entry, val score: Int, val why: String)

    fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun teamTerms(t: TeamLine) = listOf(t.nick, t.loc).map(::norm).filter { it.length >= 4 }.distinct()

    /** Words worth searching your channel index for. */
    fun searchTerms(g: Game): List<String> =
        (teamTerms(g.home) + teamTerms(g.away) + g.networks.map(::norm).filter { it.length >= 3 }).distinct()

    fun score(g: Game, channelName: String): Int {
        val n = " ${norm(channelName)} "
        fun hit(t: TeamLine): Int {
            if (teamTerms(t).any { n.contains(it) }) return 2
            val ab = norm(t.abbr)
            return if (ab.length >= 3 && n.contains(" $ab ")) 1 else 0
        }
        val h = hit(g.home)
        val a = hit(g.away)
        var s = when {
            h > 0 && a > 0 -> 100 + h + a
            h > 0 || a > 0 -> 35 + (h + a) * 2
            else -> 0
        }
        val nets = g.networks.map(::norm).filter { it.length >= 3 }
        val exact = nets.any { n.contains(" $it ") }
        val prefix = nets.any { n.contains(" $it") }
        if (exact) s += if (s > 0) 15 else 20 else if (prefix) s += if (s > 0) 5 else 8
        return s
    }

    fun rank(g: Game, channels: List<Entry>, limit: Int = 15): List<Hit> =
        channels.distinctBy { it.id }
            .map { it to score(g, it.title) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Entry, Int>> { it.second }.thenBy { it.first.title })
            .take(limit)
            .map { (e, s) ->
                Hit(e, s, when {
                    s >= 100 -> "Both teams in the name"
                    s >= 35 -> "One of the teams in the name"
                    else -> "Carries ${g.networks.joinToString(", ")}"
                })
            }
}

object SportsApi {
    private const val BASE = "https://site.api.espn.com/apis"
    private val cache = ConcurrentHashMap<String, Pair<Long, String>>()

    private suspend fun get(url: String, ttlMs: Long): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cache[url]?.takeIf { now - it.first < ttlMs }?.let { return@withContext it.second }
        val req = Request.Builder().url(url).header("User-Agent", "VanceTV/1.0").build()
        Net.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val body = r.body?.string() ?: error("empty response")
            cache[url] = now to body
            body
        }
    }

    suspend fun scoreboard(league: SportLeague, date: String?, ttlMs: Long = 20_000): List<Game> {
        val q = "?limit=200" + (date?.let { "&dates=$it" } ?: "")
        val text = get("$BASE/site/v2/sports/${league.path}/scoreboard$q", ttlMs)
        return withContext(Dispatchers.Default) { SportsParse.scoreboard(text, league) }
    }

    suspend fun standings(league: SportLeague): List<StandGroup> {
        val text = get("$BASE/v2/sports/${league.path}/standings", 30 * 60_000L)
        return withContext(Dispatchers.Default) { SportsParse.standings(text, league) }
    }
}

/** What the Sports screen is showing, kept in the app state so it survives switching tabs. */
class SportsState(private val store: Store) {
    /** "live" (everything in progress), "my" (followed teams) or a league key. */
    var leagueKey by mutableStateOf(store.gString("sportsLeague")?.takeIf { it == "live" || it == "my" || SPORT_LEAGUES.any { l -> l.key == it } } ?: "live")
    var dayOffset by mutableIntStateOf(0)
    /** 0 = scores, 1 = standings. */
    var mode by mutableIntStateOf(0)
    var games by mutableStateOf<List<Game>>(emptyList())
    var standings by mutableStateOf<List<StandGroup>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var tick by mutableIntStateOf(0)
    /** "league:teamId:Team name" */
    var follows by mutableStateOf(store.list("sportsTeams"))
    var enabled by mutableStateOf(store.list("sportsLeagues").ifEmpty { DEFAULT_SPORT_LEAGUES })
    /** Show games in progress as a row on Home. */
    var homeRow by mutableStateOf(store.gString("sportsHome") != "0")
    var homeLive by mutableStateOf<List<Game>>(emptyList())
    /** A game another screen wants the Sports hub to open straight away. */
    var pendingOpen by mutableStateOf<Game?>(null)

    fun showHomeRow(on: Boolean) { homeRow = on; store.setGString("sportsHome", if (on) "1" else "0"); if (!on) homeLive = emptyList() }

    /** Live games for the Home row: followed teams first, then the rest by league order. */
    suspend fun refreshHome() {
        val live = coroutineScope {
            leagues().map { l -> async { runCatching { SportsApi.scoreboard(l, null, 45_000).filter { it.isLive } }.getOrDefault(emptyList()) } }
                .awaitAll().flatten()
        }
        homeLive = live.sortedBy { g -> if (isFollowed(g.league, g.home.id) || isFollowed(g.league, g.away.id)) 0 else 1 }.take(24)
    }

    fun leagues() = SPORT_LEAGUES.filter { it.key in enabled }
    fun league(key: String = leagueKey) = SPORT_LEAGUES.firstOrNull { it.key == key }

    fun select(key: String) {
        leagueKey = key; store.setGString("sportsLeague", key)
        if (key == "live" || key == "my" || league(key)?.kind == SportKind.COMBAT) mode = 0
        if (key == "live") dayOffset = 0
    }

    private fun followKey(league: String, teamId: String) = "$league:$teamId"
    fun isFollowed(league: String, teamId: String) = follows.any { it.startsWith(followKey(league, teamId) + ":") }

    fun toggleFollow(league: String, team: TeamLine) = toggleFollow(league, team.id, team.name)
    fun toggleFollow(league: String, teamId: String, name: String) {
        follows = if (isFollowed(league, teamId)) follows.filter { !it.startsWith(followKey(league, teamId) + ":") }
        else follows + "${followKey(league, teamId)}:$name"
        store.setList("sportsTeams", follows)
    }

    fun clearFollows() { follows = emptyList(); store.setList("sportsTeams", follows); tick++ }

    fun toggleLeague(key: String) {
        enabled = if (key in enabled) enabled - key else enabled + key
        store.setList("sportsLeagues", enabled)
        if (leagueKey != "live" && leagueKey != "my" && leagueKey !in enabled) select("live")
    }

    private fun dateString(offset: Int): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(System.currentTimeMillis() + offset * 86_400_000L)

    suspend fun refresh(silent: Boolean = false) {
        if (!silent) loading = true
        error = null
        try {
            when (leagueKey) {
                "live" -> games = coroutineScope {
                    leagues().map { l -> async { runCatching { SportsApi.scoreboard(l, null).filter { it.isLive } }.getOrDefault(emptyList()) } }
                        .awaitAll().flatten()
                }
                "my" -> {
                    val keys = follows.mapNotNull { f -> f.split(':', limit = 3).takeIf { it.size >= 2 }?.let { it[0] to it[1] } }
                    val date = dateString(dayOffset)
                    games = coroutineScope {
                        keys.map { it.first }.distinct().mapNotNull { league(it) }.map { l ->
                            async {
                                runCatching { SportsApi.scoreboard(l, date) }.getOrDefault(emptyList())
                                    .filter { g -> (l.key to g.home.id) in keys || (l.key to g.away.id) in keys }
                            }
                        }.awaitAll().flatten()
                    }.sortedBy { it.start }
                }
                else -> {
                    val l = league() ?: return
                    if (mode == 1 && l.kind != SportKind.COMBAT) standings = SportsApi.standings(l)
                    else games = SportsApi.scoreboard(l, dateString(dayOffset))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Couldn't reach the scoreboard"
            if (!silent) { games = emptyList(); standings = emptyList() }
        } finally {
            loading = false
        }
    }
}
