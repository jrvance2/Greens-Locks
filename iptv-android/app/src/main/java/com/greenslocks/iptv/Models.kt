package com.greenslocks.iptv

enum class Kind { LIVE, MOVIE, SERIES, EPISODE }

data class Category(val id: String, val name: String)

/** Anything the user can open: a channel, movie, series, or episode. */
data class Entry(
    val kind: Kind,
    val id: String,
    val title: String,
    val ext: String = "",
    val icon: String = "",
    val num: Int = 0,
    val cat: String = "",
    val season: Int = 0,
    /** For episodes: the series title, so history can show "Show - S1 E2". */
    val parent: String = "",
)

data class Detail(val plot: String, val meta: String, val poster: String)

data class Programme(val title: String, val start: Long, val stop: Long)

private const val SEP = "\u0001"

/** Stable identity, unaffected by renames or changed artwork. */
fun Entry.key(): String = "${kind.name}:$id"

fun Entry.encode(): String =
    listOf(kind.name, id, ext, title, icon, num.toString(), cat, season.toString(), parent).joinToString(SEP)

fun decodeEntry(s: String): Entry? {
    val p = s.split(SEP)
    if (p.size < 4) return null
    val kind = runCatching { Kind.valueOf(p[0]) }.getOrNull() ?: return null
    return Entry(
        kind, p[1], p[3], p[2],
        icon = p.getOrElse(4) { "" },
        num = p.getOrNull(5)?.toIntOrNull() ?: 0,
        cat = p.getOrElse(6) { "" },
        season = p.getOrNull(7)?.toIntOrNull() ?: 0,
        parent = p.getOrElse(8) { "" },
    )
}

/** Category identity: episodes belong to the series categories. */
fun catKey(kind: Kind, id: String): String = "${if (kind == Kind.EPISODE) Kind.SERIES else kind}:$id"

private val adultWords = Regex("(?i)(^|[^a-z])(adults?|xxx|porn|erotic|18\\+|sex|nsfw)([^a-z]|$)")
fun isAdult(name: String): Boolean = adultWords.containsMatchIn(name)

/** Current and following programme at [now]. */
fun nowNext(list: List<Programme>, now: Long): Pair<Programme?, Programme?> {
    val upcoming = list.filter { it.stop > now }.sortedBy { it.start }
    val current = upcoming.firstOrNull()?.takeIf { it.start <= now }
    val next = if (current != null) upcoming.getOrNull(1) else upcoming.firstOrNull()
    return current to next
}
