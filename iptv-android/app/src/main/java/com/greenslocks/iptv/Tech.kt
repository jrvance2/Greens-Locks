package com.greenslocks.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** What a movie file is made of, from the provider's own info and from the title. */
object TechInfo {
    private fun video(v: String) = when (v.lowercase()) {
        "" -> ""
        "hevc", "h265" -> "HEVC"
        "h264", "avc", "avc1" -> "H.264"
        "av1" -> "AV1"
        "vp9" -> "VP9"
        "mpeg2video" -> "MPEG-2"
        else -> v.uppercase()
    }

    private fun audio(v: String) = when (v.lowercase()) {
        "" -> ""
        "ac3" -> "AC3"
        "eac3" -> "E-AC3"
        "aac" -> "AAC"
        "dts" -> "DTS"
        "truehd" -> "TrueHD"
        "mp3" -> "MP3"
        "opus" -> "Opus"
        else -> v.uppercase()
    }

    private fun channels(n: Int) = when (n) {
        0 -> ""
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "$n ch"
    }

    /** "HEVC · 3840x2160 · E-AC3 5.1 · 12.0 Mbps · MKV" from an Xtream vod_info "info" object; empty if nothing is known. */
    fun describe(info: JSONObject?, ext: String): String {
        val v = info?.optJSONObject("video")
        val a = info?.optJSONObject("audio")
        val w = v?.optInt("width", 0) ?: 0
        val h = v?.optInt("height", 0) ?: 0
        val kbps = info?.optInt("bitrate", 0) ?: 0
        val audioText = listOf(audio(a?.optString("codec_name").orEmpty()), channels(a?.optInt("channels", 0) ?: 0)).filter { it.isNotEmpty() }.joinToString(" ")
        val parts = listOfNotNull(
            video(v?.optString("codec_name").orEmpty()).ifEmpty { null },
            if (w > 0 && h > 0) "${w}x$h" else null,
            audioText.ifEmpty { null },
            if (kbps > 0) "%.1f Mbps".format(kbps / 1000.0) else null,
        )
        if (parts.isEmpty()) return ""
        return (parts + listOfNotNull(ext.uppercase().ifBlank { null })).joinToString("  ·  ")
    }

    private val tags = listOf(
        Regex("(?i)dolby[ .-]?vision|\\bdv\\b") to "Dolby Vision",
        Regex("(?i)hdr10\\+|hdr10|\\bhdr\\b") to "HDR",
        Regex("(?i)\\bhevc\\b|x265|h\\.?265") to "HEVC",
        Regex("(?i)atmos") to "Atmos",
        Regex("(?i)dts[ .-]?hd|\\bdts\\b") to "DTS",
        Regex("(?i)7\\.1") to "7.1",
        Regex("(?i)5\\.1") to "5.1",
        Regex("(?i)remux") to "Remux",
        Regex("(?i)\\bimax\\b") to "IMAX",
        Regex("(?i)multi[ .-]?sub") to "Multi-sub",
    )

    /** Format words found in a title, e.g. "Dune (2024) 4K HDR Atmos" -> HDR, Atmos. */
    fun formatTags(title: String): List<String> = tags.filter { it.first.containsMatchIn(title) }.map { it.second }.take(5)

    fun qualityLabel(title: String): String = when (qualityScore(title)) {
        4 -> "4K"
        3 -> "1080p"
        2 -> "HD"
        0 -> "SD"
        else -> "Standard"
    }
}

/** IMDb, Rotten Tomatoes and Metacritic scores from OMDb (a free key from omdbapi.com). */
object Omdb {
    fun parseRatings(text: String): String {
        val o = JSONObject(text)
        if (o.optString("Response").equals("False", true)) return ""
        val out = ArrayList<String>()
        val arr: JSONArray = o.optJSONArray("Ratings") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            val value = r.optString("Value")
            when (r.optString("Source")) {
                "Internet Movie Database" -> value.substringBefore('/').takeIf { it.isNotBlank() }?.let { out += "IMDb $it" }
                "Rotten Tomatoes" -> if (value.isNotBlank()) out += "RT $value"
                "Metacritic" -> value.substringBefore('/').takeIf { it.isNotBlank() }?.let { out += "Meta $it" }
            }
        }
        if (out.isEmpty()) o.optString("imdbRating").takeIf { it.isNotBlank() && it != "N/A" }?.let { out += "IMDb $it" }
        return out.joinToString("  ·  ")
    }

    private val cache = ConcurrentHashMap<String, String>()

    suspend fun ratings(key: String, imdbId: String): String = withContext(Dispatchers.IO) {
        if (key.isBlank() || imdbId.isBlank()) return@withContext ""
        cache[imdbId]?.let { return@withContext it }
        val req = Request.Builder().url("https://www.omdbapi.com/?i=$imdbId&apikey=$key").header("User-Agent", "VanceTV/1.0").build()
        val text = runCatching { Net.client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string().orEmpty() else "" } }.getOrDefault("")
        val out = if (text.isBlank()) "" else runCatching { parseRatings(text) }.getOrDefault("")
        if (out.isNotEmpty()) cache[imdbId] = out
        out
    }
}

/** Saves a Detail as text so movie pages open instantly the second time. */
object DetailJson {
    fun encode(d: Detail): String = JSONObject()
        .put("plot", d.plot).put("meta", d.meta).put("poster", d.poster).put("trailer", d.trailer)
        .put("cast", JSONArray(d.cast)).put("backdrop", d.backdrop).put("tagline", d.tagline)
        .put("tech", d.tech).put("cert", d.certification).put("castIds", JSONArray(d.castIds))
        .put("director", d.director).put("imdb", d.imdbId).put("colId", d.collectionId).put("colName", d.collectionName)
        .put("ratings", d.ratings)
        .put("similar", JSONArray().also { a ->
            d.similar.forEach { i ->
                a.put(JSONObject().put("id", i.id).put("tv", i.tv).put("title", i.title).put("year", i.year)
                    .put("overview", i.overview).put("poster", i.poster).put("rating", i.rating))
            }
        }).toString()

    fun decode(s: String): Detail? = runCatching {
        val o = JSONObject(s)
        fun strings(name: String) = o.optJSONArray(name)?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty()
        Detail(
            plot = o.optString("plot"), meta = o.optString("meta"), poster = o.optString("poster"), trailer = o.optString("trailer"),
            cast = strings("cast"), backdrop = o.optString("backdrop"), tagline = o.optString("tagline"),
            tech = o.optString("tech"), certification = o.optString("cert"),
            castIds = o.optJSONArray("castIds")?.let { a -> List(a.length()) { a.optInt(it) } }.orEmpty(),
            director = o.optString("director"), imdbId = o.optString("imdb"),
            collectionId = o.optInt("colId"), collectionName = o.optString("colName"), ratings = o.optString("ratings"),
            similar = o.optJSONArray("similar")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                    DiscoverItem(it.optInt("id"), it.optBoolean("tv"), it.optString("title"), it.optString("year"), it.optString("overview"), it.optString("poster"), it.optDouble("rating", 0.0))
                }
            }.orEmpty(),
        )
    }.getOrNull()
}
