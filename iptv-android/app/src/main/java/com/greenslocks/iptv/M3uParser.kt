package com.greenslocks.iptv

data class Channel(
    val name: String,
    val url: String,
    val group: String,
    val tvgId: String = "",
)

data class Playlist(val channels: List<Channel>, val epgUrl: String?)

object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    /**
     * Streams the playlist line by line (provider playlists can be hundreds of MB) and keeps
     * live channels only: Xtream-style movie/series entries are skipped.
     */
    fun parse(lines: Sequence<String>): Playlist {
        val groups = HashMap<String, String>() // share one String per group name
        val out = mutableListOf<Channel>()
        var epgUrl: String? = null
        var name: String? = null
        var group = ""
        var tvgId = ""
        for (raw in lines) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTM3U") -> {
                    val attrs = attr.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                    epgUrl = (attrs["url-tvg"] ?: attrs["x-tvg-url"])
                        ?.split(',')?.firstOrNull()?.trim()?.ifEmpty { null }
                }
                line.startsWith("#EXTINF") -> {
                    val attrs = attr.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                    group = attrs["group-title"].orEmpty()
                    tvgId = attrs["tvg-id"].orEmpty()
                    name = line.substringAfterLast(',', "").trim()
                        .ifEmpty { attrs["tvg-name"].orEmpty() }
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    if (!line.contains("/movie/") && !line.contains("/series/")) {
                        val g = group.ifEmpty { "Other" }
                        out += Channel(name?.ifEmpty { null } ?: line, line, groups.getOrPut(g) { g }, tvgId)
                    }
                    name = null
                    group = ""
                    tvgId = ""
                }
            }
        }
        return Playlist(out, epgUrl)
    }
}
