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
    /** For episodes: the series id, so the next episode can be found. */
    val series: String = "",
    /** Catch-up playback of a past programme on a live channel (0 = the live stream). */
    val start: Long = 0L,
    val durMin: Int = 0,
    val added: Long = 0L,
    val rating: Float = 0f,
    /** The guide's own id for this channel (Xtream epg_channel_id / M3U tvg-id), used to match an extra guide. */
    val epgId: String = "",
)

val Entry.isLiveNow: Boolean get() = kind == Kind.LIVE && start == 0L
val Entry.isCatchUp: Boolean get() = kind == Kind.LIVE && start > 0L

data class Detail(
    val plot: String,
    val meta: String,
    val poster: String,
    val trailer: String = "",
    val cast: List<String> = emptyList(),
    val backdrop: String = "",
    val tagline: String = "",
    /** From the provider: "HEVC · 3840x2160 · 5.1 · 12.0 Mbps · MKV". */
    val tech: String = "",
    /** Age rating such as PG-13 or TV-MA. */
    val certification: String = "",
    /** TMDB person ids, parallel to [cast]. */
    val castIds: List<Int> = emptyList(),
    val director: String = "",
    val imdbId: String = "",
    val collectionId: Int = 0,
    val collectionName: String = "",
    val similar: List<DiscoverItem> = emptyList(),
    /** "IMDb 8.5 · RT 92% · Meta 79" when an OMDb key is set. */
    val ratings: String = "",
)

data class Programme(val title: String, val start: Long, val stop: Long, val archive: Boolean = false)

data class AccountInfo(
    val status: String,
    val expires: Long?,
    val maxConnections: Int,
    val activeConnections: Int,
    val trial: Boolean,
    val created: Long?,
    val timezone: String,
)

private const val SEP = "\u0001"
private const val RSEP = "\u0004"

/** A programme the user asked to be reminded about. */
data class Reminder(val channel: Entry, val start: Long, val title: String)

fun encodeReminder(r: Reminder): String = listOf(r.start.toString(), r.title, r.channel.encode()).joinToString(RSEP)

fun decodeReminder(s: String): Reminder? {
    val p = s.split(RSEP, limit = 3)
    if (p.size < 3) return null
    val ch = decodeEntry(p[2]) ?: return null
    return Reminder(ch, p[0].toLongOrNull() ?: return null, p[1])
}

/** Stable identity, unaffected by renames or changed artwork. */
fun Entry.key(): String = "${kind.name}:$id"

fun Entry.encode(): String =
    listOf(kind.name, id, ext, title, icon, num.toString(), cat, season.toString(), parent, series, epgId).joinToString(SEP)

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
        series = p.getOrElse(9) { "" },
        epgId = p.getOrElse(10) { "" },
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

/** "Record every episode of this show": a channel plus the programme title to match. */
fun encodeRule(channel: Entry, title: String): String = channel.encode() + RSEP + title

fun decodeRule(s: String): Pair<Entry, String>? {
    val i = s.lastIndexOf(RSEP)
    if (i < 0) return null
    val ch = decodeEntry(s.substring(0, i)) ?: return null
    return ch to s.substring(i + 1)
}
