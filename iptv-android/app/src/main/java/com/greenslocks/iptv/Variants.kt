package com.greenslocks.iptv

private val tagLead = Regex("""^\s*(?:[|\[(][^|\])]{1,12}[|\])]\s*)+""")
private val qualityWords = Regex("""(?i)\b(uhd|4k|fhd|hd|sd|hevc|h\.?265|h\.?264|raw|1080p?|720p?|576p?|50fps|60fps|backup|alt)\b""")
private val nonWord = Regex("""[^\p{L}\p{N}]+""")

/** "|US| ESPN HD (backup)" and "US: ESPN 4K" both reduce to "espn". */
fun normName(title: String): String {
    val n = title.replace(tagLead, "").replace(qualityWords, " ").replace(nonWord, "").lowercase()
    return n.ifEmpty { title.lowercase() }
}

fun qualityScore(title: String): Int {
    val t = title.lowercase()
    return when {
        Regex("""\b(4k|uhd)\b""").containsMatchIn(t) -> 4
        Regex("""\b(fhd|1080p?)\b""").containsMatchIn(t) -> 3
        Regex("""\b(hd|720p?)\b""").containsMatchIn(t) -> 2
        Regex("""\b(sd|576p?)\b""").containsMatchIn(t) -> 0
        else -> 1
    }
}

/** One row per channel name: the best-quality version stands in for its HD/FHD/4K/backup copies. */
fun dedupe(list: List<Entry>): List<Entry> {
    val best = LinkedHashMap<String, Entry>()
    for (e in list) {
        val k = normName(e.title)
        val cur = best[k]
        if (cur == null || qualityScore(e.title) > qualityScore(cur.title)) best[k] = e
    }
    return best.values.toList()
}

/** Every copy of [e]'s channel in [pool], the remembered working one first, then best quality first. */
fun variantsOf(e: Entry, pool: List<Entry>, preferredId: String?): List<Entry> {
    val k = normName(e.title)
    return pool.filter { it.kind == Kind.LIVE && normName(it.title) == k }
        .distinctBy { it.id }
        .sortedWith(compareByDescending<Entry> { it.id == preferredId }.thenByDescending { qualityScore(it.title) })
}
