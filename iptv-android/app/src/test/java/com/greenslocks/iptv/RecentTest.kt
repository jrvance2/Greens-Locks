package com.greenslocks.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentTest {
    @Test fun topNKeepsNewestAndOrdersBestFirst() {
        val t = TopN<String>(3)
        listOf("a" to 5L, "b" to 9L, "c" to 1L, "d" to 7L, "e" to 3L, "f" to 8L).forEach { t.offer(it.second, it.first) }
        assertEquals(listOf("b", "f", "d"), t.toList())
    }

    @Test fun topNHandlesFewerItemsThanLimitAndZeroLimit() {
        val t = TopN<Int>(10); t.offer(2, 20); t.offer(5, 50)
        assertEquals(listOf(50, 20), t.toList())
        val z = TopN<Int>(0); z.offer(1, 1)
        assertTrue(z.toList().isEmpty())
    }

    @Test fun topNIgnoresItemsOlderThanTheCurrentCutoff() {
        val t = TopN<String>(2)
        t.offer(10, "x"); t.offer(20, "y"); t.offer(5, "old")
        assertEquals(listOf("y", "x"), t.toList())
    }

    @Test fun firstSightRecordsABaselineInsteadOfFlagging() {
        assertTrue(EpisodeWatch.needsBaseline(null, null))
        assertTrue(EpisodeWatch.needsBaseline(100L, null))
        assertFalse(EpisodeWatch.needsBaseline(100L, 12))
    }

    @Test fun onlyAProviderChangeAfterTheLastVisitTriggersACount() {
        assertTrue(EpisodeWatch.changedSince(200L, 100L))
        assertFalse(EpisodeWatch.changedSince(100L, 100L))
        assertFalse(EpisodeWatch.changedSince(50L, 100L))
    }

    @Test fun newOnlyWhenMoreEpisodesThanBefore() {
        assertTrue(EpisodeWatch.hasNew(13, 12))
        assertFalse(EpisodeWatch.hasNew(12, 12))
        assertFalse(EpisodeWatch.hasNew(10, 12))
    }
}
