package com.greenslocks.iptv

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private const val F = "\u0001"

val LIB_TTL_NAMES = listOf("Off", "1 hour", "6 hours", "24 hours")
val LIB_TTL_MS = listOf(0L, 3_600_000L, 6 * 3_600_000L, 24 * 3_600_000L)

/** Where one login's cached lists live. Cache space the system may reclaim at any time, so nothing here is precious. */
fun libCacheDir(ctx: Context, url: String): File {
    val h = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)
    return File(ctx.cacheDir, "lib/$h")
}

/**
 * Keeps category lists, per-category channel lists and counts on disk so the app opens straight onto
 * the library instead of waiting on the provider, and still opens when the provider is slow or down.
 */
class CachedProvider(
    private val inner: Provider,
    val dir: File,
    private val ttlMs: () -> Long,
) : Provider by inner {

    private suspend fun <T> cached(
        name: String,
        force: Boolean = false,
        read: (List<String>) -> T?,
        write: (T) -> List<String>,
        isEmpty: (T) -> Boolean,
        load: suspend () -> T,
    ): T {
        val f = File(dir, name)
        val ttl = ttlMs()
        if (!force && ttl > 0 && f.exists() && System.currentTimeMillis() - f.lastModified() < ttl) {
            val hit = withContext(Dispatchers.IO) { runCatching { read(f.readLines()) }.getOrNull() }
            if (hit != null) return hit
        }
        return try {
            val fresh = load()
            if (ttl > 0 && !isEmpty(fresh)) withContext(Dispatchers.IO) {
                runCatching {
                    dir.mkdirs()
                    val tmp = File(dir, "$name.tmp")
                    tmp.writeText(write(fresh).joinToString("\n"))
                    // Android normally replaces the destination on rename; Windows does not.
                    if (!tmp.renameTo(f)) {
                        tmp.copyTo(f, overwrite = true)
                        tmp.delete()
                    }
                }
            }
            fresh
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The provider failed: an older copy is better than an error screen.
            val stale = if (f.exists()) withContext(Dispatchers.IO) { runCatching { read(f.readLines()) }.getOrNull() } else null
            stale ?: throw e
        }
    }

    private fun catKey(c: Category) = c.id.filter { it.isLetterOrDigit() || it == '_' }.take(24) + "_" + c.id.hashCode().toString(16)

    private fun catsOf(kind: Kind, force: Boolean = false) = suspend {
        cached(
            "cat_${kind.name}", force,
            read = { l -> l.mapNotNull { s -> s.split(F, limit = 2).takeIf { it.size == 2 }?.let { Category(it[0], it[1]) } } },
            write = { l -> l.map { "${it.id}$F${it.name.replace('\n', ' ')}" } },
            isEmpty = { it.isEmpty() },
        ) { inner.categories(kind) }
    }

    override suspend fun categories(kind: Kind): List<Category> = catsOf(kind)()

    override suspend fun entries(kind: Kind, category: Category): List<Entry> = cached(
        "ent_${kind.name}_${catKey(category)}",
        read = { l -> l.mapNotNull(::decodeEntry) },
        write = { l -> l.map { it.encode().replace('\n', ' ') } },
        isEmpty = { it.isEmpty() },
    ) { inner.entries(kind, category) }

    private fun countsOf(kind: Kind, force: Boolean = false) = suspend {
        cached(
            "cnt_${kind.name}", force,
            read = { l -> l.mapNotNull { s -> s.split(F).let { p -> p.getOrNull(1)?.toIntOrNull()?.let { n -> p[0] to n } } }.toMap() },
            write = { m -> m.map { (k, v) -> "$k$F$v" } },
            isEmpty = { it.isEmpty() },
        ) { inner.counts(kind) }
    }

    override suspend fun counts(kind: Kind): Map<String, Int> = countsOf(kind)()

    /** Re-downloads the category lists and counts (used by the background job). */
    suspend fun refreshLists() {
        val kinds = if (inner.supportsVod) listOf(Kind.LIVE, Kind.MOVIE, Kind.SERIES) else listOf(Kind.LIVE)
        for (k in kinds) {
            runCatching { catsOf(k, true)() }
            runCatching { countsOf(k, true)() }
        }
        // Drop per-category lists so the next visit fetches fresh ones.
        dir.listFiles()?.filter { it.name.startsWith("ent_") }?.forEach { it.delete() }
    }
}
