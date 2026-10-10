package com.greenslocks.iptv

enum class Kind { LIVE, MOVIE, SERIES, EPISODE }

data class Category(val id: String, val name: String)

/** Anything the user can open: a channel, movie, series, or episode. */
data class Entry(val kind: Kind, val id: String, val title: String, val ext: String = "")

data class Programme(val title: String, val start: Long, val stop: Long)

private const val SEP = "\u0001"

fun Entry.encode(): String = listOf(kind.name, id, ext, title).joinToString(SEP)

fun decodeEntry(s: String): Entry? {
    val p = s.split(SEP, limit = 4)
    if (p.size < 4) return null
    val kind = runCatching { Kind.valueOf(p[0]) }.getOrNull() ?: return null
    return Entry(kind, p[1], p[3], p[2])
}

/** Current and following programme at [now]. */
fun nowNext(list: List<Programme>, now: Long): Pair<Programme?, Programme?> {
    val upcoming = list.filter { it.stop > now }.sortedBy { it.start }
    val current = upcoming.firstOrNull()?.takeIf { it.start <= now }
    val next = if (current != null) upcoming.getOrNull(1) else upcoming.firstOrNull()
    return current to next
}
