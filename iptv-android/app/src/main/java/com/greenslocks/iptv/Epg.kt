package com.greenslocks.iptv

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream

data class Programme(val title: String, val start: Long, val stop: Long)

/** XMLTV guide, reduced to the few programmes per channel that matter right now. */
object Epg {
    private const val LOOKAHEAD_MS = 6 * 60 * 60 * 1000L
    private const val MAX_PER_CHANNEL = 4

    /** Blocking; call from a background dispatcher. Keys are lower-cased channel ids. */
    fun load(url: String): Map<String, List<Programme>> {
        val conn = URL(url).openConnection().apply { connectTimeout = 15000; readTimeout = 120000 }
        val raw = BufferedInputStream(conn.getInputStream())
        raw.mark(2)
        val gz = raw.read() == 0x1f && raw.read() == 0x8b
        raw.reset()
        (if (gz) GZIPInputStream(raw) else raw).use { return parse(it, System.currentTimeMillis()) }
    }

    fun parse(input: InputStream, now: Long): Map<String, List<Programme>> {
        val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        fun time(s: String?): Long? {
            if (s == null) return null
            val t = s.trim().let { if (it.length == 14) "$it +0000" else it }
            return runCatching { fmt.parse(t)!!.time }.getOrNull()
        }

        val out = HashMap<String, MutableList<Programme>>()
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && parser.name == "programme") {
                val channel = parser.getAttributeValue(null, "channel")?.lowercase()
                val start = time(parser.getAttributeValue(null, "start"))
                val stop = time(parser.getAttributeValue(null, "stop"))
                var title: String? = null
                while (true) {
                    ev = parser.next()
                    if (ev == XmlPullParser.END_DOCUMENT) break
                    if (ev == XmlPullParser.START_TAG && parser.name == "title" && title == null) {
                        title = parser.nextText()
                    } else if (ev == XmlPullParser.END_TAG && parser.name == "programme") break
                }
                if (channel != null && start != null && stop != null && title != null &&
                    stop > now && start < now + LOOKAHEAD_MS
                ) {
                    val list = out.getOrPut(channel) { mutableListOf() }
                    if (list.size < MAX_PER_CHANNEL) list += Programme(title, start, stop)
                }
            }
            if (ev == XmlPullParser.END_DOCUMENT) break
            ev = parser.next()
        }
        return out.mapValues { (_, v) -> v.sortedBy { it.start } }
    }

    /** Current and following programme for a channel at [now]. */
    fun nowNext(list: List<Programme>?, now: Long): Pair<Programme?, Programme?> {
        val upcoming = list.orEmpty().filter { it.stop > now }
        val current = upcoming.firstOrNull()?.takeIf { it.start <= now }
        val next = if (current != null) upcoming.getOrNull(1) else upcoming.firstOrNull()
        return current to next
    }
}
