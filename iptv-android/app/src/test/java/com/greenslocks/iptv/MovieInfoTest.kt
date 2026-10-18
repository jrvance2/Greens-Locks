package com.greenslocks.iptv

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieInfoTest {
    private fun res(name: String) = javaClass.getResource("/$name")!!.readText()

    // ---- provider technical info
    @Test fun describesVideoAudioAndBitrateFromProviderInfo() {
        val info = JSONObject("""{"video":{"codec_name":"hevc","width":3840,"height":2160},"audio":{"codec_name":"eac3","channels":6},"bitrate":12040}""")
        assertEquals("HEVC  ·  3840x2160  ·  E-AC3 5.1  ·  12.0 Mbps  ·  MKV", TechInfo.describe(info, "mkv"))
    }

    @Test fun providersThatSendEmptyArraysOrNothingGiveNoLine() {
        assertEquals("", TechInfo.describe(JSONObject("""{"video":[],"audio":[]}"""), "mp4"))
        assertEquals("", TechInfo.describe(null, "mp4"))
        assertEquals("H.264  ·  1920x1080  ·  MP4", TechInfo.describe(JSONObject("""{"video":{"codec_name":"h264","width":"1920","height":"1080"}}"""), "mp4"))
    }

    // ---- format words in titles
    @Test fun findsFormatWordsInTitles() {
        assertEquals(listOf("Dolby Vision", "HEVC", "Atmos", "7.1"), TechInfo.formatTags("Dune (2024) 4K DV x265 Atmos 7.1"))
        assertEquals(listOf("HDR", "5.1", "Multi-sub"), TechInfo.formatTags("|EN| Movie HDR10 5.1 MULTI-SUB"))
        assertTrue(TechInfo.formatTags("The Plain Movie").isEmpty())
        assertTrue(TechInfo.formatTags("Advanced Dvorak").isEmpty()) // "dv" only as a whole word
    }

    @Test fun qualityLabelsMatchTheRanking() {
        assertEquals("4K", TechInfo.qualityLabel("Movie 4K"))
        assertEquals("1080p", TechInfo.qualityLabel("Movie FHD"))
        assertEquals("HD", TechInfo.qualityLabel("Movie HD"))
        assertEquals("SD", TechInfo.qualityLabel("Movie SD"))
        assertEquals("Standard", TechInfo.qualityLabel("Movie"))
    }

    // ---- TMDB movie page
    @Test fun parsesAMoviePage() {
        val d = Tmdb.parseDetail(JSONObject(res("tmdb_movie_detail.json")), tv = false)
        assertEquals("Paul Atreides unites with the Fremen.", d.plot)
        assertEquals("PG-13", d.certification)
        assertEquals("Denis Villeneuve", d.director)
        assertEquals(listOf("Timothée Chalamet", "Zendaya"), d.cast)
        assertEquals(listOf(1190668, 505710), d.castIds)
        assertEquals("Way9Dexny3w", d.trailer)
        assertEquals("tt15239678", d.imdbId)
        assertEquals(726871, d.collectionId)
        assertEquals("Dune Collection", d.collectionName)
        assertEquals(2, d.similar.size)
        assertEquals("Dune", d.similar[0].title)
        assertTrue(d.meta.contains("2h 46m") && d.meta.contains("2024"))
        assertTrue(d.backdrop.endsWith("/b.jpg"))
    }

    @Test fun seriesUseContentRatingsAndCreators() {
        val d = Tmdb.parseDetail(JSONObject("""{"name":"Show","overview":"x","first_air_date":"2020-01-01","created_by":[{"name":"Jane Maker"}],
            "content_ratings":{"results":[{"iso_3166_1":"DE","rating":"16"},{"iso_3166_1":"US","rating":"TV-MA"}]},"external_ids":{"imdb_id":"tt999"}}"""), tv = true)
        assertEquals("TV-MA", d.certification)
        assertEquals("Jane Maker", d.director)
        assertEquals("tt999", d.imdbId)
    }

    @Test fun missingRatingsAndCollectionsAreEmpty() {
        val d = Tmdb.parseDetail(JSONObject("""{"title":"Bare","overview":""}"""), tv = false)
        assertEquals("", d.certification); assertEquals(0, d.collectionId); assertEquals("", d.director); assertTrue(d.similar.isEmpty())
    }

    @Test fun mergeKeepsProviderTextAndAddsTmdbExtras() {
        val base = Detail(plot = "Provider plot", meta = "", poster = "", tech = "HEVC")
        val t = Tmdb.parseDetail(JSONObject(res("tmdb_movie_detail.json")), tv = false)
        val m = Tmdb.merge(base, t)!!
        assertEquals("Provider plot", m.plot)
        assertEquals("HEVC", m.tech)
        assertEquals("PG-13", m.certification)
        assertEquals(2, m.similar.size)
    }

    // ---- saved pages
    @Test fun aSavedPageComesBackIdentical() {
        val d = Tmdb.parseDetail(JSONObject(res("tmdb_movie_detail.json")), tv = false).copy(tech = "HEVC", ratings = "IMDb 8.5")
        val back = DetailJson.decode(DetailJson.encode(d))
        assertNotNull(back)
        assertEquals(d, back)
        assertEquals(null, DetailJson.decode("not json"))
    }

    // ---- OMDb
    @Test fun readsImdbRottenTomatoesAndMetacriticScores() {
        assertEquals("IMDb 8.5  ·  RT 92%  ·  Meta 79", Omdb.parseRatings(res("omdb_response.json")))
    }

    @Test fun omdbMissesAndFallbacks() {
        assertEquals("", Omdb.parseRatings("""{"Response":"False","Error":"Incorrect IMDb ID."}"""))
        assertEquals("IMDb 7.1", Omdb.parseRatings("""{"Response":"True","Ratings":[],"imdbRating":"7.1"}"""))
        assertEquals("", Omdb.parseRatings("""{"Response":"True","Ratings":[],"imdbRating":"N/A"}"""))
    }
}
