package com.greenslocks.iptv

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device index of every channel, movie and series title (one database per profile), so search
 * answers instantly instead of hitting the provider. Built in the background, refreshed twice a day.
 */
class SearchIndex(context: Context, profileId: Int) : SQLiteOpenHelper(context, "search_$profileId.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging() // searches keep working while a rebuild is writing
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE items(kind TEXT, id TEXT, title TEXT, tl TEXT, icon TEXT, cat TEXT, ext TEXT, num INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS items")
        onCreate(db)
    }

    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM items", null).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }

    suspend fun rebuild(provider: Provider, kinds: List<Kind>, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM items")
            val st = db.compileStatement("INSERT INTO items VALUES (?,?,?,?,?,?,?,?)")
            var n = 0
            for (k in kinds) {
                provider.indexAll(k) { e ->
                    st.clearBindings()
                    st.bindString(1, e.kind.name)
                    st.bindString(2, e.id)
                    st.bindString(3, e.title)
                    st.bindString(4, e.title.lowercase())
                    st.bindString(5, e.icon)
                    st.bindString(6, e.cat)
                    st.bindString(7, e.ext)
                    st.bindLong(8, e.num.toLong())
                    st.executeInsert()
                    n++
                    if (n % 2000 == 0) onProgress(n)
                }
            }
            db.setTransactionSuccessful()
            onProgress(n)
        } finally {
            db.endTransaction()
        }
    }

    /** Every word must appear in the title. */
    fun search(query: String, perKind: Int = 40): Map<Kind, List<Entry>> {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyMap()
        val where = terms.joinToString(" AND ") { "tl LIKE ? ESCAPE '\\'" }
        val args = terms.map { "%" + it.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%" }
        return listOf(Kind.LIVE, Kind.MOVIE, Kind.SERIES).associateWith { kind ->
            readableDatabase.rawQuery(
                "SELECT id,title,icon,cat,ext,num FROM items WHERE kind=? AND $where LIMIT $perKind",
                arrayOf(kind.name) + args,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(Entry(kind, c.getString(0), c.getString(1), c.getString(4), c.getString(2), c.getInt(5), c.getString(3)))
                    }
                }
            }
        }
    }
}
