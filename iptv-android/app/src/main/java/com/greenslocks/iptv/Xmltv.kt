package com.greenslocks.iptv

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.GregorianCalendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** Reads an XMLTV guide file as a stream, so even very large files don't have to fit in memory. */
object XmltvParser {
    /** "20261012193000 +0100" -> milliseconds since 1970. A missing zone means UTC. */
    fun parseTime(raw: String): Long {
        val s = raw.trim()
        if (s.length < 12) return 0L
        val digits = s.takeWhile { it.isDigit() }
        if (digits.length < 12) return 0L
        fun n(from: Int, len: Int) = digits.substring(from, from + len).toInt()
        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(n(0, 4), n(4, 2) - 1, n(6, 2), n(8, 2), n(10, 2), if (digits.length >= 14) n(12, 2) else 0)
        var ms = cal.timeInMillis
        val zone = s.substring(digits.length).trim()
        if (zone.length >= 5 && (zone[0] == '+' || zone[0] == '-')) {
            val h = zone.substring(1, 3).toIntOrNull() ?: 0
            val m = zone.substring(3, 5).toIntOrNull() ?: 0
            val offset = (h * 60 + m) * 60_000L
            ms -= if (zone[0] == '+') offset else -offset
        }
        return ms
    }

    /**
     * Calls [onChannel] for every channel (id and its display names) and [onProgramme] for every
     * programme that overlaps the window between [from] and [to].
     */
    fun parse(
        p: XmlPullParser, from: Long, to: Long,
        onChannel: (String, List<String>) -> Unit,
        onProgramme: (String, Long, Long, String) -> Unit,
    ) {
        var ev = p.eventType
        var channelId = ""
        val names = ArrayList<String>()
        var progChannel = ""
        var progStart = 0L
        var progStop = 0L
        var title = ""
        var inChannel = false
        var inProgramme = false
        var text = ""
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "channel" -> { inChannel = true; channelId = p.getAttributeValue(null, "id").orEmpty(); names.clear() }
                    "programme" -> {
                        inProgramme = true
                        progChannel = p.getAttributeValue(null, "channel").orEmpty()
                        progStart = parseTime(p.getAttributeValue(null, "start").orEmpty())
                        progStop = parseTime(p.getAttributeValue(null, "stop").orEmpty())
                        title = ""
                    }
                    "display-name", "title" -> text = ""
                }
                XmlPullParser.TEXT -> text += p.text.orEmpty()
                XmlPullParser.END_TAG -> when (p.name) {
                    "display-name" -> if (inChannel && text.isNotBlank()) names += text.trim()
                    "title" -> if (inProgramme && title.isEmpty()) title = text.trim()
                    "channel" -> { if (channelId.isNotBlank()) onChannel(channelId, names.toList()); inChannel = false }
                    "programme" -> {
                        if (progChannel.isNotBlank() && title.isNotBlank() && progStop > progStart &&
                            progStop > from && progStart < to
                        ) onProgramme(progChannel, progStart, progStop, title)
                        inProgramme = false
                    }
                }
            }
            ev = p.next()
        }
    }
}

/** The extra guide, kept in a small database per profile. */
class XmltvStore(ctx: Context, profile: Int) : SQLiteOpenHelper(ctx, "xmltv_$profile.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE prog(ch TEXT NOT NULL, start INTEGER NOT NULL, stop INTEGER NOT NULL, title TEXT NOT NULL)")
        db.execSQL("CREATE INDEX prog_ch ON prog(ch, start)")
        db.execSQL("CREATE TABLE chan(id TEXT NOT NULL, norm TEXT NOT NULL)")
        db.execSQL("CREATE INDEX chan_id ON chan(id)")
        db.execSQL("CREATE INDEX chan_norm ON chan(norm)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** Downloads and loads [url]. Returns a short status message. */
    suspend fun refresh(url: String): String = withContext(Dispatchers.IO) {
        val client = Net.client.newBuilder().readTimeout(120, TimeUnit.SECONDS).build()
        val request = Request.Builder().url(url).header("User-Agent", Net.userAgent).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext "Couldn't download the guide (error ${resp.code})"
            val raw = BufferedInputStream(resp.body!!.byteStream(), 64 * 1024)
            raw.mark(2)
            val b0 = raw.read(); val b1 = raw.read()
            raw.reset()
            val stream: InputStream = if (b0 == 0x1f && b1 == 0x8b) GZIPInputStream(raw, 64 * 1024) else raw
            val now = System.currentTimeMillis()
            val parser = Xml.newPullParser().apply { setInput(stream, null) }
            val db = writableDatabase
            var channels = 0
            var programmes = 0
            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM prog")
                db.execSQL("DELETE FROM chan")
                val chanStmt = db.compileStatement("INSERT INTO chan(id, norm) VALUES(?, ?)")
                val progStmt = db.compileStatement("INSERT INTO prog(ch, start, stop, title) VALUES(?, ?, ?, ?)")
                XmltvParser.parse(
                    parser, now - 3 * 3_600_000L, now + 48 * 3_600_000L,
                    onChannel = { id, names ->
                        val key = id.lowercase()
                        chanStmt.clearBindings(); chanStmt.bindString(1, key); chanStmt.bindString(2, normName(names.firstOrNull() ?: id)); chanStmt.executeInsert()
                        names.drop(1).forEach { n ->
                            chanStmt.clearBindings(); chanStmt.bindString(1, key); chanStmt.bindString(2, normName(n)); chanStmt.executeInsert()
                        }
                        channels++
                    },
                    onProgramme = { ch, start, stop, title ->
                        progStmt.clearBindings()
                        progStmt.bindString(1, ch.lowercase()); progStmt.bindLong(2, start); progStmt.bindLong(3, stop); progStmt.bindString(4, title)
                        progStmt.executeInsert()
                        programmes++
                    },
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            "Extra guide updated: $channels channels, $programmes programmes"
        }
    }

    suspend fun hasData(): Boolean = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT 1 FROM prog LIMIT 1", null).use { it.moveToFirst() }
    }

    /** Every programme on air at [at], across all channels in the guide. */
    suspend fun airingAt(at: Long): List<AirRow> = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            "SELECT ch, title, start, stop FROM prog WHERE start <= ? AND stop > ?", arrayOf(at.toString(), at.toString()),
        ).use { c ->
            val out = ArrayList<AirRow>()
            while (c.moveToNext()) out += AirRow(c.getString(0), c.getString(1), c.getLong(2), c.getLong(3))
            out
        }
    }

    /** Guide channel id -> the normalized names it is known by. */
    suspend fun channelNames(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        val out = HashMap<String, MutableList<String>>()
        readableDatabase.rawQuery("SELECT id, norm FROM chan", null).use { c ->
            while (c.moveToNext()) out.getOrPut(c.getString(0)) { ArrayList() } += c.getString(1)
        }
        out
    }

    /** Programmes for [entry] in the window, found by its guide id or, failing that, by channel name. */
    suspend fun programmesFor(entry: Entry, from: Long, to: Long): List<Programme> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val ids = LinkedHashSet<String>()
        if (entry.epgId.isNotBlank()) ids += entry.epgId.lowercase()
        db.rawQuery("SELECT DISTINCT id FROM chan WHERE norm = ?", arrayOf(normName(entry.title))).use { c ->
            while (c.moveToNext()) ids += c.getString(0)
        }
        if (ids.isEmpty()) return@withContext emptyList()
        val marks = ids.joinToString(",") { "?" }
        db.rawQuery(
            "SELECT title, start, stop FROM prog WHERE ch IN ($marks) AND stop > ? AND start < ? ORDER BY start",
            ids.toTypedArray() + arrayOf(from.toString(), to.toString()),
        ).use { c ->
            val out = ArrayList<Programme>()
            while (c.moveToNext()) out += Programme(c.getString(0), c.getLong(1), c.getLong(2))
            out
        }
    }
}
