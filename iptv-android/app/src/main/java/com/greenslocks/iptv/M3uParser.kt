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

    fun parse(text: String): Playlist {
        val out = mutableListOf<Channel>()
        var epgUrl: String? = null
        var name: String? = null
        var group = ""
        var tvgId = ""
        for (raw in text.lineSequence()) {
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
                    out += Channel(name?.ifEmpty { null } ?: line, line, group.ifEmpty { "Other" }, tvgId)
                    name = null
                    group = ""
                    tvgId = ""
                }
            }
        }
        return Playlist(out, epgUrl)
    }
}
