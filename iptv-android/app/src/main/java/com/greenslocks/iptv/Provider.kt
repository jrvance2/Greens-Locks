package com.greenslocks.iptv

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/** A source of channels and (optionally) on-demand content. Lists are loaded per category. */
interface Provider {
    val supportsVod: Boolean
    suspend fun categories(kind: Kind): List<Category>
    suspend fun entries(kind: Kind, category: Category): List<Entry>
    suspend fun episodes(series: Entry): List<Entry>
    suspend fun epg(entry: Entry): List<Programme>
    fun streamUrl(entry: Entry): String
}

private fun httpGet(url: String): String {
    val conn = URL(url).openConnection().apply { connectTimeout = 15000; readTimeout = 60000 }
    return conn.getInputStream().bufferedReader().use { it.readText() }
}

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

/** Xtream-style panel API (player_api.php). Never downloads the whole library at once. */
class XtreamProvider(
    private val base: String,
    private val user: String,
    private val pass: String,
) : Provider {
    override val supportsVod = true

    private fun api(action: String? = null, extra: String = ""): String =
        "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}" +
            (action?.let { "&action=$it" } ?: "") + extra

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) { httpGet(url) }

    suspend fun verify() {
        val info = JSONObject(fetch(api())).optJSONObject("user_info") ?: error("Not an Xtream server")
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
        val arr = JSONArray(fetch(api(action, "&category_id=${enc(category.id)}")))
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Entry(kind, o.optString(idKey), o.optString("name"), o.optString("container_extension").ifEmpty { "mp4" })
        }
    }

    override suspend fun episodes(series: Entry): List<Entry> {
        val seasons = JSONObject(fetch(api("get_series_info", "&series_id=${enc(series.id)}")))
            .optJSONObject("episodes") ?: return emptyList()
        val out = mutableListOf<Entry>()
        for (season in seasons.keys().asSequence().sortedBy { it.toIntOrNull() ?: 0 }) {
            val arr = seasons.optJSONArray(season) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val label = "S$season E${o.optInt("episode_num")}  ${o.optString("title")}"
                out += Entry(Kind.EPISODE, o.optString("id"), label, o.optString("container_extension").ifEmpty { "mp4" })
            }
        }
        return out
    }

    override suspend fun epg(entry: Entry): List<Programme> {
        val arr = JSONObject(fetch(api("get_short_epg", "&stream_id=${enc(entry.id)}&limit=3")))
            .optJSONArray("epg_listings") ?: return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Programme(
                String(Base64.decode(o.optString("title"), Base64.DEFAULT), Charsets.UTF_8),
                o.optLong("start_timestamp") * 1000,
                o.optLong("stop_timestamp") * 1000,
            )
        }.filter { it.stop > 0 }
    }

    override fun streamUrl(entry: Entry): String = when (entry.kind) {
        Kind.LIVE -> "$base/live/$user/$pass/${entry.id}.m3u8"
        Kind.MOVIE -> "$base/movie/$user/$pass/${entry.id}.${entry.ext}"
        Kind.EPISODE -> "$base/series/$user/$pass/${entry.id}.${entry.ext}"
        Kind.SERIES -> error("A series has no stream")
    }
}

/** Plain M3U playlist: live channels only (movie/series entries are skipped to save memory). */
class M3uProvider(private val channels: List<Channel>) : Provider {
    override val supportsVod = false

    override suspend fun categories(kind: Kind): List<Category> =
        if (kind == Kind.LIVE) channels.map { it.group }.distinct().sorted().map { Category(it, it) }
        else emptyList()

    override suspend fun entries(kind: Kind, category: Category): List<Entry> =
        if (kind == Kind.LIVE) channels.filter { it.group == category.id }.map { Entry(Kind.LIVE, it.url, it.name) }
        else emptyList()

    override suspend fun episodes(series: Entry): List<Entry> = emptyList()
    override suspend fun epg(entry: Entry): List<Programme> = emptyList()
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

suspend fun connectProvider(input: String): Provider {
    val url = input.trim()
    xtreamFromUrl(url)?.let { it.verify(); return it }
    val playlist = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection().apply { connectTimeout = 15000; readTimeout = 60000 }
        conn.getInputStream().bufferedReader().use { M3uParser.parse(it.lineSequence()) }
    }
    return M3uProvider(playlist.channels)
}
