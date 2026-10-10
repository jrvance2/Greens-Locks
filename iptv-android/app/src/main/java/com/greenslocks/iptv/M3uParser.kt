package com.greenslocks.iptv

data class Channel(
    val name: String,
    val url: String,
    val group: String,
)

object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): List<Channel> {
        val out = mutableListOf<Channel>()
        var name: String? = null
        var group = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    val attrs = attr.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                    group = attrs["group-title"].orEmpty()
                    name = line.substringAfterLast(',', "").trim()
                        .ifEmpty { attrs["tvg-name"].orEmpty() }
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    out += Channel(name?.ifEmpty { null } ?: line, line, group.ifEmpty { "Other" })
                    name = null
                    group = ""
                }
            }
        }
        return out
    }
}
