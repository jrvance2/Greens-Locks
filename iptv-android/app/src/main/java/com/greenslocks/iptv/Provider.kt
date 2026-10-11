package com.greenslocks.iptv

import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A source of channels and (optionally) on-demand content. Lists are loaded per category. */
interface Provider {
    val supportsVod: Boolean
    suspend fun categories(kind: Kind): List<Category>
    suspend fun entries(kind: Kind, category: Category): List<Entry>
    suspend fun episodes(series: Entry): List<Entry>
    suspend fun epg(entry: Entry, limit: Int): List<Programme>
    /** Every live channel (streamed, minimal fields) for search and channel-number jumps. */
    suspend fun allLive(): List<Entry>
    suspend fun detail(entry: Entry): Detail?
    /** How many items each category holds (streamed and tallied, nothing retained). */
    suspend fun counts(kind: Kind): Map<String, Int>
    /** HLS ("m3u8") or transport stream ("ts") for live channels. */
    var liveFormat: String
    /** Added to every programme time, for providers whose guide is in the wrong time zone. */
    var epgOffsetMs: Long
    suspend fun account(): AccountInfo?
    /** Programmes around now, including past ones that may be available for catch-up. */
    suspend fun guideEpg(entry: Entry, pastMs: Long, futureMs: Long): List<Programme>
    /** Streams every item of a kind to [sink] without holding the list in memory. */
    suspend fun indexAll(kind: Kind, sink: (Entry) -> Unit)
    suspend fun nextEpisode(current: Entry): Entry?
    fun streamUrl(entry: Entry): String
}

private fun JsonReader.str(): String =
    if (peek() == JsonToken.NULL) { nextNull(); "" } else nextString()

private fun httpGet(url: String): String {
    val conn = URL(url).openConnection().apply { connectTimeout = 15000; readTimeout = 60000 }
    return conn.getInputStream().bufferedReader().use { it.readText() }
}

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

/** Xtream-style panel API (player_api.php). Never downloads the whole library at once. */
class XtreamProvider(
    val base: String,
    val user: String,
    val pass: String,
) : Provider {
    override val supportsVod = true
    override var liveFormat: String = "m3u8"
    override var epgOffsetMs: Long = 0L
    private var timezone: String = ""

    private fun api(action: String? = null, extra: String = ""): String =
        "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}" +
            (action?.let { "&action=$it" } ?: "") + extra

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) { httpGet(url) }

    suspend fun verify() {
        val root = JSONObject(fetch(api()))
        timezone = root.optJSONObject("server_info")?.optString("timezone").orEmpty()
        val info = root.optJSONObject("user_info") ?: error("Not an Xtream server")
        if (info.optString("auth") != "1") error("Login rejected - check username and password")
        val status = info.optString("status")
        if (status.isNotEmpty() && !status.equals("Active", ignoreCase = true)) error("Account is $status")
    }

    override suspend fun categories(kind: Kind): List<Category> {
        val action = when (kind) {
            Kind.LIVE -> "get_live_categories"
            Kind.MOVIE -> "get_vod_categories"
            Kind.SERIES -> "get_series_categories"
            Kind.EPISODE -> return emptyList()
        }
        val arr = JSONArray(fetch(api(action)))
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Category(o.optString("category_id"), o.optString("category_name"))
        }
    }

    override suspend fun entries(kind: Kind, category: Category): List<Entry> {
        val (action, idKey) = when (kind) {
            Kind.LIVE -> "get_live_streams" to "stream_id"
            Kind.MOVIE -> "get_vod_streams" to "stream_id"
            Kind.SERIES -> "get_series" to "series_id"
            Kind.EPISODE -> return emptyList()
        }
        val iconKey = if (kind == Kind.SERIES) "cover" else "stream_icon"
        val arr = JSONArray(fetch(api(action, "&category_id=${enc(category.id)}")))
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Entry(
                kind, o.optString(idKey), o.optString("name"),
                o.optString("container_extension").ifEmpty { "mp4" },
                icon = o.optString(iconKey).takeUnless { it == "null" }.orEmpty(),
                num = o.optInt("num"), cat = category.id,
                added = o.optString("added").toLongOrNull() ?: 0L,
                rating = o.optString("rating").toFloatOrNull() ?: o.optString("rating_5based").toFloatOrNull() ?: 0f,
            )
        }
    }

    override suspend fun allLive(): List<Entry> = withContext(Dispatchers.IO) {
        val conn = URL(api("get_live_streams")).openConnection().apply { connectTimeout = 15000; readTimeout = 120000 }
        JsonReader(conn.getInputStream().bufferedReader()).use { r ->
            val out = ArrayList<Entry>()
            r.beginArray()
            while (r.hasNext()) {
                var id = ""; var name = ""; var icon = ""; var num = 0; var cat = ""
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "stream_id" -> id = r.str()
                        "name" -> name = r.str()
                        "stream_icon" -> icon = r.str()
                        "num" -> num = r.str().toIntOrNull() ?: 0
                        "category_id" -> cat = r.str()
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                out += Entry(Kind.LIVE, id, name, icon = icon, num = num, cat = cat)
            }
            r.endArray()
            out
        }
    }

    override suspend fun counts(kind: Kind): Map<String, Int> = withContext(Dispatchers.IO) {
        val action = when (kind) {
            Kind.LIVE -> "get_live_streams"
            Kind.MOVIE -> "get_vod_streams"
            Kind.SERIES -> "get_series"
            Kind.EPISODE -> return@withContext emptyMap<String, Int>()
        }
        val conn = URL(api(action)).openConnection().apply { connectTimeout = 15000; readTimeout = 180000 }
        JsonReader(conn.getInputStream().bufferedReader()).use { r ->
            val out = HashMap<String, Int>()
            r.beginArray()
            while (r.hasNext()) {
                var cat = ""
                r.beginObject()
                while (r.hasNext()) { if (r.nextName() == "category_id") cat = r.str() else r.skipValue() }
                r.endObject()
                out[cat] = (out[cat] ?: 0) + 1
            }
            r.endArray()
            out
        }
    }

    private var seriesCache: Pair<String, JSONObject>? = null

    private suspend fun seriesInfo(id: String): JSONObject {
        seriesCache?.takeIf { it.first == id }?.let { return it.second }
        val o = JSONObject(fetch(api("get_series_info", "&series_id=${enc(id)}")))
        seriesCache = id to o
        return o
    }

    override suspend fun detail(entry: Entry): Detail? {
        val info = when (entry.kind) {
            Kind.MOVIE -> JSONObject(fetch(api("get_vod_info", "&vod_id=${enc(entry.id)}"))).optJSONObject("info")
            Kind.SERIES -> seriesInfo(entry.id).optJSONObject("info")
            else -> null
        } ?: return null
        fun field(vararg names: String) = names.map { info.optString(it) }.firstOrNull { it.isNotBlank() && it != "null" }.orEmpty()
        val rating = field("rating").takeIf { it != "0" }?.let { "\u2605 $it" }
        val year = field("releasedate", "releaseDate").take(4).takeIf { it.isNotBlank() }
        val meta = listOfNotNull(rating, year, field("genre").ifEmpty { null }, field("duration").ifEmpty { null })
            .joinToString("  \u00B7  ")
        return Detail(
            plot = field("plot", "description"),
            meta = meta,
            poster = field("movie_image", "cover", "cover_big").ifEmpty { entry.icon },
            trailer = field("youtube_trailer"),
        )
    }

    override suspend fun episodes(series: Entry): List<Entry> {
        val seasons = seriesInfo(series.id).optJSONObject("episodes") ?: return emptyList()
        val out = mutableListOf<Entry>()
        for (season in seasons.keys().asSequence().sortedBy { it.toIntOrNull() ?: 0 }) {
            val arr = seasons.optJSONArray(season) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val label = "S$season E${o.optInt("episode_num")}  ${o.optString("title")}"
                out += Entry(
                    Kind.EPISODE, o.optString("id"), label,
                    o.optString("container_extension").ifEmpty { "mp4" },
                    icon = series.icon, season = season.toIntOrNull() ?: 0, parent = series.title, series = series.id,
                )
            }
        }
        return out
    }

    private fun parseProgramme(o: JSONObject): Programme {
        val raw = o.optString("title")
        val title = runCatching { String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(raw)
        return Programme(
            title, o.optLong("start_timestamp") * 1000 + epgOffsetMs, o.optLong("stop_timestamp") * 1000 + epgOffsetMs,
            archive = o.optInt("has_archive") == 1,
        )
    }

    override suspend fun epg(entry: Entry, limit: Int): List<Programme> {
        val arr = JSONObject(fetch(api("get_short_epg", "&stream_id=${enc(entry.id)}&limit=$limit")))
            .optJSONArray("epg_listings") ?: return emptyList()
        return List(arr.length()) { i -> parseProgramme(arr.getJSONObject(i)) }.filter { it.stop > 0 }
    }

    override suspend fun guideEpg(entry: Entry, pastMs: Long, futureMs: Long): List<Programme> {
        val now = System.currentTimeMillis()
        val full = runCatching {
            val arr = JSONObject(fetch(api("get_simple_data_table", "&stream_id=${enc(entry.id)}")))
                .optJSONArray("epg_listings")
            if (arr == null) emptyList() else List(arr.length()) { i -> parseProgramme(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
        val inWindow = full.filter { it.stop > now - pastMs && it.start < now + futureMs && it.stop > 0 }
        return if (inWindow.isNotEmpty()) inWindow else runCatching { epg(entry, 10) }.getOrDefault(emptyList())
    }

    override suspend fun account(): AccountInfo? = runCatching {
        val root = JSONObject(fetch(api()))
        val u = root.optJSONObject("user_info") ?: error("no user_info")
        AccountInfo(
            status = u.optString("status").ifEmpty { "Unknown" },
            expires = u.optString("exp_date").toLongOrNull()?.times(1000),
            maxConnections = u.optString("max_connections").toIntOrNull() ?: 0,
            activeConnections = u.optString("active_cons").toIntOrNull() ?: 0,
            trial = u.optString("is_trial") == "1",
            created = u.optString("created_at").toLongOrNull()?.times(1000),
            timezone = root.optJSONObject("server_info")?.optString("timezone").orEmpty(),
        )
    }.getOrNull()

    override suspend fun indexAll(kind: Kind, sink: (Entry) -> Unit) = withContext(Dispatchers.IO) {
        val (action, idKey) = when (kind) {
            Kind.LIVE -> "get_live_streams" to "stream_id"
            Kind.MOVIE -> "get_vod_streams" to "stream_id"
            Kind.SERIES -> "get_series" to "series_id"
            Kind.EPISODE -> return@withContext
        }
        val iconKey = if (kind == Kind.SERIES) "cover" else "stream_icon"
        val conn = URL(api(action)).openConnection().apply { connectTimeout = 15000; readTimeout = 180000 }
        JsonReader(conn.getInputStream().bufferedReader()).use { r ->
            r.beginArray()
            while (r.hasNext()) {
                var id = ""; var name = ""; var icon = ""; var cat = ""; var ext = ""; var num = 0
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        idKey -> id = r.str()
                        "name" -> name = r.str()
                        iconKey -> icon = r.str()
                        "category_id" -> cat = r.str()
                        "container_extension" -> ext = r.str()
                        "num" -> num = r.str().toIntOrNull() ?: 0
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                sink(Entry(kind, id, name, ext.ifEmpty { "mp4" }, icon, num, cat))
            }
            r.endArray()
        }
    }

    override suspend fun nextEpisode(current: Entry): Entry? {
        if (current.series.isBlank()) return null
        val eps = episodes(Entry(Kind.SERIES, current.series, current.parent, icon = current.icon, cat = current.cat))
        val i = eps.indexOfFirst { it.id == current.id }
        return if (i < 0) null else eps.getOrNull(i + 1)?.copy(cat = current.cat)
    }

    private fun timeshiftUrl(e: Entry): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone(timezone.ifBlank { "UTC" })
        return "$base/timeshift/$user/$pass/${e.durMin}/${fmt.format(Date(e.start))}/${e.id}.ts"
    }

    override fun streamUrl(entry: Entry): String = when (entry.kind) {
        Kind.LIVE -> if (entry.start > 0) timeshiftUrl(entry) else "$base/live/$user/$pass/${entry.id}.$liveFormat"
        Kind.MOVIE -> "$base/movie/$user/$pass/${entry.id}.${entry.ext}"
        Kind.EPISODE -> "$base/series/$user/$pass/${entry.id}.${entry.ext}"
        Kind.SERIES -> error("A series has no stream")
    }
}

/** Plain M3U playlist: live channels only (movie/series entries are skipped to save memory). */
class M3uProvider(private val channels: List<Channel>) : Provider {
    override val supportsVod = false
    override var liveFormat: String = "m3u8"
    override var epgOffsetMs: Long = 0L
    override suspend fun account(): AccountInfo? = null
    override suspend fun guideEpg(entry: Entry, pastMs: Long, futureMs: Long): List<Programme> = emptyList()
    override suspend fun nextEpisode(current: Entry): Entry? = null
    override suspend fun indexAll(kind: Kind, sink: (Entry) -> Unit) {
        if (kind != Kind.LIVE) return
        channels.forEachIndexed { i, c -> sink(Entry(Kind.LIVE, c.url, c.name, icon = c.logo, num = i + 1, cat = c.group)) }
    }

    override suspend fun categories(kind: Kind): List<Category> =
        if (kind == Kind.LIVE) channels.map { it.group }.distinct().sorted().map { Category(it, it) }
        else emptyList()

    override suspend fun entries(kind: Kind, category: Category): List<Entry> =
        if (kind == Kind.LIVE) channels.filter { it.group == category.id }.map { Entry(Kind.LIVE, it.url, it.name, icon = it.logo, cat = it.group) }
        else emptyList()

    override suspend fun allLive(): List<Entry> =
        channels.mapIndexed { i, c -> Entry(Kind.LIVE, c.url, c.name, icon = c.logo, num = i + 1, cat = c.group) }

    override suspend fun detail(entry: Entry): Detail? = null
    override suspend fun counts(kind: Kind): Map<String, Int> =
        if (kind == Kind.LIVE) channels.groupingBy { it.group }.eachCount() else emptyMap()
    override suspend fun episodes(series: Entry): List<Entry> = emptyList()
    override suspend fun epg(entry: Entry, limit: Int): List<Programme> = emptyList()
    override fun streamUrl(entry: Entry): String = entry.id
}

/** Pulls server and login out of a get.php / player_api.php URL so nothing has to be retyped. */
fun xtreamFromUrl(input: String): XtreamProvider? {
    val uri = runCatching { URI(input) }.getOrNull() ?: return null
    val path = uri.path.orEmpty()
    if (!path.endsWith("get.php") && !path.endsWith("player_api.php")) return null
    val params = uri.rawQuery.orEmpty().split('&').mapNotNull { kv ->
        kv.split('=', limit = 2).takeIf { it.size == 2 }
            ?.let { URLDecoder.decode(it[0], "UTF-8") to URLDecoder.decode(it[1], "UTF-8") }
    }.toMap()
    val user = params["username"]
    val pass = params["password"]
    if (user.isNullOrBlank() || pass.isNullOrBlank() || uri.scheme == null || uri.authority == null) return null
    return XtreamProvider("${uri.scheme}://${uri.authority}${path.substringBeforeLast('/')}", user, pass)
}

/** Builds the standard playlist URL from separate login fields; the app stores it encrypted. */
fun buildXtreamUrl(server: String, user: String, pass: String): String {
    var s = server.trim().trimEnd('/')
    if (!s.contains("://")) s = "http://$s"
    return "$s/get.php?username=${enc(user.trim())}&password=${enc(pass)}&type=m3u_plus&output=m3u8"
}

suspend fun connectProvider(input: String): Provider {
    val url = input.trim()
    xtreamFromUrl(url)?.let { it.verify(); return it }
    val playlist = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection().apply { connectTimeout = 15000; readTimeout = 60000 }
        conn.getInputStream().bufferedReader().use { M3uParser.parse(it.lineSequence()) }
    }
    return M3uProvider(playlist.channels)
}
