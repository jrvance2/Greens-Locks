package com.greenslocks.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

/** Richer movie/series info (cast, backdrop, tagline, trailer) from TMDB, using the user's own free API key. */
object Tmdb {
    private val yearRegex = Regex("""[(\[]\s*((?:19|20)\d{2})\s*[)\]]""")
    private val tagRegex = Regex("""^\s*(?:[|\[(][^|\])]{1,12}[|\])]\s*)+""")
    private val noiseRegex = Regex("""(?i)\b(4k|uhd|fhd|hd|1080p|720p|hdr|multi[- ]?sub|multi)\b""")

    /** "|EN| The Long Road Home (2024) 4K" -> ("The Long Road Home", 2024). */
    fun cleanTitle(raw: String): Pair<String, Int?> {
        val year = yearRegex.find(raw)?.groupValues?.get(1)?.toIntOrNull()
        var t = raw.replace(yearRegex, " ").replace(tagRegex, "").replace(noiseRegex, " ")
        t = t.replace(Regex("""\s+[-–]\s*$"""), "").replace(Regex("""\s{2,}"""), " ").trim()
        return t to year
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection().apply { connectTimeout = 10000; readTimeout = 15000 }
        return conn.getInputStream().bufferedReader().use { it.readText() }
    }

    suspend fun lookup(key: String, rawTitle: String, tv: Boolean): Detail? = withContext(Dispatchers.IO) {
        val (title, year) = cleanTitle(rawTitle)
        if (title.isBlank()) return@withContext null
        val type = if (tv) "tv" else "movie"
        val yearParam = if (year == null) "" else if (tv) "&first_air_date_year=$year" else "&year=$year"
        val q = URLEncoder.encode(title, "UTF-8")
        val results = JSONObject(get("https://api.themoviedb.org/3/search/$type?api_key=$key&query=$q$yearParam"))
            .optJSONArray("results") ?: return@withContext null
        val id = results.optJSONObject(0)?.optInt("id", 0) ?: return@withContext null
        if (id == 0) return@withContext null
        val d = JSONObject(get("https://api.themoviedb.org/3/$type/$id?api_key=$key&append_to_response=credits,videos"))

        val cast = d.optJSONObject("credits")?.optJSONArray("cast")?.let { a ->
            List(minOf(a.length(), 10)) { a.getJSONObject(it).optString("name") }.filter { it.isNotBlank() }
        }.orEmpty()
        var trailer = ""
        d.optJSONObject("videos")?.optJSONArray("results")?.let { a ->
            for (i in 0 until a.length()) {
                val v = a.getJSONObject(i)
                if (v.optString("site") == "YouTube" && v.optString("type") == "Trailer") { trailer = v.optString("key"); break }
            }
        }
        val genres = d.optJSONArray("genres")?.let { a -> List(a.length()) { a.getJSONObject(it).optString("name") } }.orEmpty()
        val minutes = if (tv) d.optJSONArray("episode_run_time")?.optInt(0, 0) ?: 0 else d.optInt("runtime", 0)
        val date = d.optString(if (tv) "first_air_date" else "release_date").take(4)
        val rating = d.optDouble("vote_average", 0.0)
        val meta = listOfNotNull(
            if (rating > 0) "★ %.1f".format(rating) else null,
            date.ifBlank { null },
            genres.take(3).joinToString(", ").ifBlank { null },
            if (minutes > 0) "${minutes / 60}h %02dm".format(minutes % 60).removePrefix("0h ") else null,
        ).joinToString("  ·  ")
        fun img(path: String, size: String) = if (path.isBlank() || path == "null") "" else "https://image.tmdb.org/t/p/$size$path"
        Detail(
            plot = d.optString("overview"),
            meta = meta,
            poster = img(d.optString("poster_path"), "w500"),
            trailer = trailer,
            cast = cast,
            backdrop = img(d.optString("backdrop_path"), "w1280"),
            tagline = d.optString("tagline"),
        )
    }

    /** Keeps the provider's own text where it has any; adds TMDB's cast, backdrop and tagline. */
    fun merge(base: Detail?, t: Detail?): Detail? {
        if (t == null) return base
        if (base == null) return t
        return Detail(
            plot = base.plot.ifBlank { t.plot },
            meta = base.meta.ifBlank { t.meta },
            poster = base.poster.ifBlank { t.poster },
            trailer = base.trailer.ifBlank { t.trailer },
            cast = t.cast.ifEmpty { base.cast },
            backdrop = t.backdrop.ifBlank { base.backdrop },
            tagline = t.tagline.ifBlank { base.tagline },
        )
    }
}
