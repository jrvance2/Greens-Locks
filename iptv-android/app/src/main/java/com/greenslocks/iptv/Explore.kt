package com.greenslocks.iptv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// ------------------------------------------------------------------ Discover (TMDB, matched against your library)

data class DiscoverItem(
    val id: Int, val tv: Boolean, val title: String, val year: String,
    val overview: String, val poster: String, val rating: Double,
) {
    val key get() = "${if (tv) "tv" else "movie"}:$id"
}

data class DiscoverRow(val title: String, val items: List<DiscoverItem>)

object TmdbDiscover {
    /** Row title, TMDB path, whether the rows are series. */
    val ROWS = listOf(
        Triple("Trending movies this week", "trending/movie/week", false),
        Triple("Trending series this week", "trending/tv/week", true),
        Triple("In theaters now", "movie/now_playing", false),
        Triple("Coming soon", "movie/upcoming", false),
        Triple("Top rated movies", "movie/top_rated", false),
        Triple("Popular series", "tv/popular", true),
    )

    fun parse(text: String, tv: Boolean): List<DiscoverItem> {
        val arr = JSONObject(text).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<DiscoverItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optInt("id", 0)
            val title = o.optString("title").ifBlank { o.optString("name") }
            if (id == 0 || title.isBlank()) continue
            val poster = o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
            out += DiscoverItem(
                id, tv, title,
                year = o.optString(if (tv) "first_air_date" else "release_date").take(4),
                overview = o.optString("overview"),
                poster = poster?.let { "https://image.tmdb.org/t/p/w342$it" }.orEmpty(),
                rating = o.optDouble("vote_average", 0.0),
            )
        }
        return out
    }

    private val cache = ConcurrentHashMap<String, Pair<Long, List<DiscoverItem>>>()

    private suspend fun fetch(key: String, path: String, tv: Boolean, region: String): List<DiscoverItem> = withContext(Dispatchers.IO) {
        val ck = "$path|$region"
        val now = System.currentTimeMillis()
        cache[ck]?.takeIf { now - it.first < 30 * 60_000L }?.let { return@withContext it.second }
        val url = "https://api.themoviedb.org/3/$path?api_key=$key&language=en-US&region=$region&page=1"
        val req = Request.Builder().url(url).header("User-Agent", "VanceTV/1.0").build()
        val items = Net.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("TMDB error ${r.code}")
            parse(r.body?.string().orEmpty(), tv)
        }
        cache[ck] = now to items
        items
    }

    suspend fun rows(key: String, region: String): List<DiscoverRow> = coroutineScope {
        ROWS.map { (title, path, tv) ->
            async { runCatching { DiscoverRow(title, fetch(key, path, tv, region).take(20)) }.getOrNull() }
        }.awaitAll().filterNotNull().filter { it.items.isNotEmpty() }
    }
}

/** Finds the library title behind a TMDB title. */
object LibraryMatch {
    private val tail = Regex("(19|20)\\d{2}$")
    private val stop = setOf("the", "a", "an", "of", "and")

    fun key(title: String): String = normName(Tmdb.cleanTitle(title).first).replace(tail, "")

    /** Words to look for in the on-device search index. */
    fun searchQuery(title: String): String =
        Tmdb.cleanTitle(title).first.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .split(' ').filter { it.length > 1 && it !in stop }.take(4).joinToString(" ")

    fun best(item: DiscoverItem, candidates: List<Entry>): Entry? {
        val k = key(item.title)
        if (k.length < 2) return null
        val equal = candidates.filter { key(it.title) == k }
        if (equal.isEmpty()) return null
        return equal.sortedWith(
            compareByDescending<Entry> { if (Tmdb.cleanTitle(it.title).second?.toString() == item.year) 1 else 0 }
                .thenByDescending { qualityScore(it.title) }
        ).first()
    }
}

// ------------------------------------------------------------------ Tonight (what's on, from the extra guide)

data class AirRow(val ch: String, val title: String, val start: Long, val stop: Long)
data class TonightItem(val entry: Entry, val prog: Programme, val genre: String)

object Tonight {
    /** Looks up channels by guide id and by normalized name; where several copies exist, the best quality wins. */
    class LiveIndex(val byEpg: Map<String, Entry>, val byNorm: Map<String, Entry>)

    fun indexLive(live: List<Entry>): LiveIndex {
        val byEpg = HashMap<String, Entry>()
        val byNorm = HashMap<String, Entry>()
        for (e in live) {
            if (e.epgId.isNotBlank()) {
                val k = e.epgId.lowercase()
                if (byEpg[k]?.let { qualityScore(it.title) >= qualityScore(e.title) } != true) byEpg[k] = e
            }
            val n = normName(e.title)
            if (n.isNotEmpty() && byNorm[n]?.let { qualityScore(it.title) >= qualityScore(e.title) } != true) byNorm[n] = e
        }
        return LiveIndex(byEpg, byNorm)
    }

    fun build(rows: List<AirRow>, names: Map<String, List<String>>, idx: LiveIndex): List<TonightItem> {
        val seen = HashSet<String>()
        val out = ArrayList<TonightItem>()
        for (r in rows) {
            val e = idx.byEpg[r.ch] ?: names[r.ch]?.firstNotNullOfOrNull { idx.byNorm[it] } ?: continue
            if (!seen.add(normName(e.title) + "|" + r.start)) continue
            out += TonightItem(e, Programme(r.title, r.start, r.stop), genreOf(r.title))
        }
        return out.sortedWith(compareBy<TonightItem>({ if (it.entry.num > 0) it.entry.num else Int.MAX_VALUE }, { it.entry.title.lowercase() }))
    }

    /** When to look: now, two hours on, or tonight at 8 PM (tomorrow's if it is already late). */
    fun timeFor(mode: Int, now: Long): Long = when (mode) {
        1 -> now + 2 * 3_600_000L
        2 -> {
            val c = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            if (now > c.timeInMillis + 2 * 3_600_000L) c.add(Calendar.DAY_OF_MONTH, 1)
            c.timeInMillis
        }
        else -> now
    }
}

// ------------------------------------------------------------------ My agenda

enum class AgendaKind { REMINDER, RECORDING, GAME }

data class AgendaItem(
    val kind: AgendaKind, val start: Long, val end: Long, val title: String, val sub: String,
    val reminder: Reminder? = null, val recId: String = "", val game: Game? = null,
)

object Agenda {
    fun build(now: Long, reminders: List<Reminder>, jobs: List<RecJob>, games: List<Game>): List<AgendaItem> {
        val out = ArrayList<AgendaItem>()
        reminders.filter { it.start >= now - 5 * 60_000L }.forEach {
            out += AgendaItem(AgendaKind.REMINDER, it.start, it.start, it.title, "Reminder · ${it.channel.title}", reminder = it)
        }
        jobs.filter { it.startMs + it.durMs > now }.forEach {
            out += AgendaItem(AgendaKind.RECORDING, it.startMs, it.startMs + it.durMs, it.name, "Recording · ${it.durMs / 60_000} min", recId = it.id)
        }
        games.filter { !it.isFinal }.forEach { g ->
            val league = SPORT_LEAGUES.firstOrNull { it.key == g.league }?.short.orEmpty()
            out += AgendaItem(
                AgendaKind.GAME, g.start, g.start + 3 * 3_600_000L, "${g.away.name} at ${g.home.name}",
                listOf(league, g.networks.firstOrNull().orEmpty(), if (g.isLive) "Live" else "").filter { it.isNotBlank() }.joinToString(" · "),
                game = g,
            )
        }
        return out.sortedWith(compareBy<AgendaItem>({ it.start }, { it.kind.ordinal }))
    }

    private fun dayStart(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun dayLabel(ms: Long, now: Long): String {
        val days = Math.round((dayStart(ms) - dayStart(now)) / 86_400_000.0).toInt()
        return when {
            days <= 0 -> "Today"
            days == 1 -> "Tomorrow"
            else -> SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(ms))
        }
    }
}

/** Upcoming and live games for the teams you follow, over the next three days. */
suspend fun followedGames(follows: List<String>): List<Game> {
    val keys = follows.mapNotNull { f -> f.split(':', limit = 3).takeIf { it.size >= 2 }?.let { it[0] to it[1] } }
    if (keys.isEmpty()) return emptyList()
    val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    val dates = (0..2).map { fmt.format(Date(System.currentTimeMillis() + it * 86_400_000L)) }
    return coroutineScope {
        keys.map { it.first }.distinct().mapNotNull { k -> SPORT_LEAGUES.firstOrNull { it.key == k } }.flatMap { l ->
            dates.map { d ->
                async {
                    runCatching { SportsApi.scoreboard(l, d) }.getOrDefault(emptyList())
                        .filter { g -> !g.isFinal && ((l.key to g.home.id) in keys || (l.key to g.away.id) in keys) }
                }
            }
        }.awaitAll().flatten()
    }.distinctBy { it.league + it.id }
}

// ------------------------------------------------------------------ state

class ExploreState(store: Store) {
    /** 0 Discover, 1 Tonight, 2 My agenda */
    var section by mutableIntStateOf(0)

    var rows by mutableStateOf<List<DiscoverRow>>(emptyList())
    var discoverLoading by mutableStateOf(false)
    var discoverError by mutableStateOf<String?>(null)
    var discoverTick by mutableIntStateOf(0)
    val matches = mutableStateMapOf<String, Entry>()
    val checked = mutableStateMapOf<String, Boolean>()
    var libraryOnly by mutableStateOf(store.flag("discLibOnly", false))

    /** 0 now, 1 later, 2 prime time */
    var tonightMode by mutableIntStateOf(0)
    /** 0 all, 1 movies, 2 sports, 3 news, 4 kids */
    var tonightGenre by mutableIntStateOf(0)
    var tonight by mutableStateOf<List<TonightItem>>(emptyList())
    var tonightLoading by mutableStateOf(false)
    var tonightNoGuide by mutableStateOf(false)

    var games by mutableStateOf<List<Game>>(emptyList())
    var agendaTick by mutableIntStateOf(0)

    private val store = store
    fun toggleLibraryOnly() { libraryOnly = !libraryOnly; store.setFlag("discLibOnly", libraryOnly) }
    fun reset() { matches.clear(); checked.clear(); rows = emptyList() }
}
