package com.greenslocks.iptv

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LibraryCacheTest {
    private class Fake : Provider by M3uProvider(emptyList()) {
        var calls = 0
        var fail = false
        var cats = listOf(Category("1", "News"), Category("2", "Sport"))
        var list = listOf(Entry(Kind.LIVE, "10", "BBC One", icon = "http://x/a.png", num = 1, cat = "1"))
        override val supportsVod = true
        override suspend fun categories(kind: Kind): List<Category> { calls++; if (fail) error("down"); return cats }
        override suspend fun entries(kind: Kind, category: Category): List<Entry> { calls++; if (fail) error("down"); return list }
        override suspend fun counts(kind: Kind): Map<String, Int> { calls++; if (fail) error("down"); return mapOf("1" to 5) }
    }

    private fun dir(): File = Files.createTempDirectory("libcache").toFile()

    @Test fun secondReadComesFromDisk() = runBlocking {
        val inner = Fake()
        val p = CachedProvider(inner, dir()) { 3_600_000L }
        assertEquals(2, p.categories(Kind.LIVE).size)
        assertEquals(2, p.categories(Kind.LIVE).size)
        assertEquals(1, inner.calls)
        assertEquals("BBC One", p.entries(Kind.LIVE, Category("1", "News")).first().title)
        assertEquals("BBC One", p.entries(Kind.LIVE, Category("1", "News")).first().title)
        assertEquals(2, inner.calls)
        assertEquals(mapOf("1" to 5), p.counts(Kind.LIVE))
        assertEquals(mapOf("1" to 5), p.counts(Kind.LIVE))
        assertEquals(3, inner.calls)
    }

    @Test fun cachingOffAlwaysAsksProvider() = runBlocking {
        val inner = Fake()
        val p = CachedProvider(inner, dir()) { 0L }
        p.categories(Kind.LIVE); p.categories(Kind.LIVE)
        assertEquals(2, inner.calls)
    }

    @Test fun staleCopyIsUsedWhenProviderFails() = runBlocking {
        val inner = Fake()
        val d = dir()
        val p = CachedProvider(inner, d) { 3_600_000L }
        p.categories(Kind.LIVE)
        File(d, "cat_LIVE").setLastModified(System.currentTimeMillis() - 10 * 3_600_000L) // expired
        inner.fail = true
        assertEquals(listOf("News", "Sport"), p.categories(Kind.LIVE).map { it.name })
    }

    @Test fun failsWhenNothingIsSaved() = runBlocking {
        val inner = Fake().apply { fail = true }
        val p = CachedProvider(inner, dir()) { 3_600_000L }
        try { p.categories(Kind.LIVE); fail("should throw") } catch (e: IllegalStateException) { assertTrue(e.message == "down") }
    }

    @Test fun emptyResultsAreNotSaved() = runBlocking {
        val inner = Fake().apply { cats = emptyList() }
        val d = dir()
        val p = CachedProvider(inner, d) { 3_600_000L }
        assertTrue(p.categories(Kind.LIVE).isEmpty())
        assertTrue(!File(d, "cat_LIVE").exists())
    }

    @Test fun refreshListsReplacesSavedCopies() = runBlocking {
        val inner = Fake()
        val d = dir()
        val p = CachedProvider(inner, d) { 3_600_000L }
        p.categories(Kind.LIVE); p.entries(Kind.LIVE, Category("1", "News"))
        inner.cats = listOf(Category("9", "New"))
        p.refreshLists()
        assertEquals(listOf("New"), p.categories(Kind.LIVE).map { it.name })
        assertTrue(d.listFiles()!!.none { it.name.startsWith("ent_") })
    }
}
