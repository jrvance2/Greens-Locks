package com.greenslocks.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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

    private val details = java.util.concurrent.ConcurrentHashMap<String, Detail>()
    private val backdrops = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val misses = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun get(url: String): String {
        val conn = URL(url).openConnection().apply { connectTimeout = 5000; readTimeout = 8000 }
        return conn.getInputStream().bufferedReader().use { it.readText() }
    }

    /** Just the wide backdrop image for the Home banner: one cached search request. */
    suspend fun backdropFor(key: String, rawTitle: String, tv: Boolean): String = withContext(Dispatchers.IO) {
        val ck = "${if (tv) "tv" else "movie"}:$rawTitle"
        backdrops[ck]?.let { return@withContext it }
        if (ck in misses) return@withContext ""
        val (title, year) = cleanTitle(rawTitle)
        if (title.isBlank()) return@withContext ""
        val type = if (tv) "tv" else "movie"
        val yearParam = if (year == null) "" else if (tv) "&first_air_date_year=$year" else "&year=$year"
        val path = runCatching {
            JSONObject(get("https://api.themoviedb.org/3/search/$type?api_key=$key&query=${URLEncoder.encode(title, "UTF-8")}$yearParam"))
                .optJSONArray("results")?.optJSONObject(0)?.optString("backdrop_path").orEmpty()
        }.getOrDefault("")
        if (path.isBlank() || path == "null") { misses += ck; "" }
        else "https://image.tmdb.org/t/p/w1280$path".also { backdrops[ck] = it }
    }

    /** Where movie pages are kept between runs (set at startup). */
    @Volatile var cacheDir: java.io.File? = null

    private fun diskFile(ck: String): java.io.File? =
        cacheDir?.let { java.io.File(it, "tmdb_" + java.security.MessageDigest.getInstance("MD5").digest(ck.toByteArray()).joinToString("") { b -> "%02x".format(b) } + ".json") }

    suspend fun lookup(key: String, rawTitle: String, tv: Boolean): Detail? = withContext(Dispatchers.IO) {
        val ck = "${if (tv) "tv" else "movie"}:$rawTitle"
        details[ck]?.let { return@withContext it }
        if (ck in misses) return@withContext null
        // Saved pages stay good for a week.
        diskFile(ck)?.takeIf { it.exists() && System.currentTimeMillis() - it.lastModified() < 7 * 86_400_000L }
            ?.let { f -> runCatching { DetailJson.decode(f.readText()) }.getOrNull() }
            ?.let { details[ck] = it; return@withContext it }
        val found = fetchDetail(key, rawTitle, tv)
        if (found != null) {
            details[ck] = found
            diskFile(ck)?.let { f -> runCatching { f.writeText(DetailJson.encode(found)) } }
        } else misses += ck
        found
    }

    /** The other movies of a collection ("Dune Collection"), oldest first. */
    suspend fun collection(key: String, id: Int): List<DiscoverItem> = withContext(Dispatchers.IO) {
        runCatching {
            val parts = JSONObject(get("https://api.themoviedb.org/3/collection/$id?api_key=$key")).optJSONArray("parts")
            TmdbDiscover.parseArray(parts, tv = false).sortedBy { it.year }
        }.getOrDefault(emptyList())
    }

    /** An actor's best-known movies, most popular first. */
    suspend fun personMovies(key: String, personId: Int): List<DiscoverItem> = withContext(Dispatchers.IO) {
        runCatching {
            val cast = JSONObject(get("https://api.themoviedb.org/3/person/$personId/movie_credits?api_key=$key")).optJSONArray("cast")
                ?: return@runCatching emptyList()
            val sorted = (0 until cast.length()).mapNotNull { cast.optJSONObject(it) }.sortedByDescending { it.optDouble("popularity", 0.0) }.take(40)
            TmdbDiscover.parseArray(JSONArray(sorted), tv = false)
        }.getOrDefault(emptyList())
    }

    private fun fetchDetail(key: String, rawTitle: String, tv: Boolean): Detail? {
        val (title, year) = cleanTitle(rawTitle)
        if (title.isBlank()) return null
        val type = if (tv) "tv" else "movie"
        val yearParam = if (year == null) "" else if (tv) "&first_air_date_year=$year" else "&year=$year"
        val q = URLEncoder.encode(title, "UTF-8")
        val results = JSONObject(get("https://api.themoviedb.org/3/search/$type?api_key=$key&query=$q$yearParam"))
            .optJSONArray("results") ?: return null
        val id = results.optJSONObject(0)?.optInt("id", 0) ?: return null
        if (id == 0) return null
        val extra = if (tv) "credits,videos,content_ratings,recommendations,external_ids" else "credits,videos,release_dates,recommendations"
        return parseDetail(JSONObject(get("https://api.themoviedb.org/3/$type/$id?api_key=$key&append_to_response=$extra")), tv)
    }

    /** Age rating for [region] from TMDB's release dates (movies) or content ratings (series). */
    fun certification(d: JSONObject, tv: Boolean, region: String = "US"): String {
        if (tv) {
            val a = d.optJSONObject("content_ratings")?.optJSONArray("results") ?: return ""
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                if (o.optString("iso_3166_1") == region) return o.optString("rating")
            }
        } else {
            val a = d.optJSONObject("release_dates")?.optJSONArray("results") ?: return ""
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                if (o.optString("iso_3166_1") != region) continue
                val rd = o.optJSONArray("release_dates") ?: continue
                for (j in 0 until rd.length()) {
                    val c = rd.optJSONObject(j)?.optString("certification").orEmpty()
                    if (c.isNotBlank()) return c
                }
            }
        }
        return ""
    }

    fun parseDetail(d: JSONObject, tv: Boolean): Detail {
        val castArr = d.optJSONObject("credits")?.optJSONArray("cast")
        val cast = ArrayList<String>()
        val castIds = ArrayList<Int>()
        if (castArr != null) for (i in 0 until minOf(castArr.length(), 10)) {
            val c = castArr.optJSONObject(i) ?: continue
            val n = c.optString("name")
            if (n.isNotBlank()) { cast += n; castIds += c.optInt("id", 0) }
        }
        var director = ""
        d.optJSONObject("credits")?.optJSONArray("crew")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                if (o.optString("job") == "Director") { director = o.optString("name"); break }
            }
        }
        if (tv && director.isBlank()) director = d.optJSONArray("created_by")?.optJSONObject(0)?.optString("name").orEmpty()
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
        val col = d.optJSONObject("belongs_to_collection")
        return Detail(
            plot = d.optString("overview"),
            meta = meta,
            poster = img(d.optString("poster_path"), "w500"),
            trailer = trailer,
            cast = cast,
            backdrop = img(d.optString("backdrop_path"), "w1280"),
            tagline = d.optString("tagline"),
            certification = certification(d, tv),
            castIds = castIds,
            director = director,
            imdbId = d.optString("imdb_id").ifBlank { d.optJSONObject("external_ids")?.optString("imdb_id").orEmpty() }.takeIf { it != "null" }.orEmpty(),
            collectionId = col?.optInt("id", 0) ?: 0,
            collectionName = col?.optString("name").orEmpty(),
            similar = TmdbDiscover.parseArray(d.optJSONObject("recommendations")?.optJSONArray("results"), tv).take(15),
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
            tech = base.tech.ifBlank { t.tech },
            certification = t.certification.ifBlank { base.certification },
            castIds = if (t.cast.isNotEmpty()) t.castIds else base.castIds,
            director = t.director.ifBlank { base.director },
            imdbId = t.imdbId.ifBlank { base.imdbId },
            collectionId = t.collectionId, collectionName = t.collectionName,
            similar = t.similar, ratings = base.ratings.ifBlank { t.ratings },
        )
    }
}
