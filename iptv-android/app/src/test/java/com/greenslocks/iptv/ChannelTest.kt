package com.greenslocks.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelTest {
    private fun live(id: String, title: String) = Entry(Kind.LIVE, id, title)

    @Test fun namesIgnoreTagsAndQualityWords() {
        assertEquals(normName("|US| ESPN HD (backup)"), normName("|US| ESPN 4K"))
        assertEquals("espn", normName("|US| ESPN HD"))
        assertEquals("espn", normName("ESPN FHD"))
        assertEquals("bbcone", normName("[UK] BBC One UHD"))
    }

    @Test fun differentChannelsStayDifferent() {
        assertNotEquals(normName("ESPN"), normName("ESPN2"))
        assertNotEquals(normName("CNN"), normName("CNN International"))
    }

    @Test fun qualityRanksHigherForBiggerPictures() {
        assertEquals(4, qualityScore("ESPN 4K"))
        assertEquals(3, qualityScore("ESPN FHD"))
        assertEquals(2, qualityScore("ESPN HD"))
        assertEquals(1, qualityScore("ESPN"))
        assertEquals(0, qualityScore("ESPN SD"))
    }

    @Test fun dedupeKeepsTheBestCopyInOrder() {
        val list = listOf(live("1", "ESPN SD"), live("2", "CNN HD"), live("3", "ESPN 4K"), live("4", "ESPN HD"))
        val merged = dedupe(list)
        assertEquals(listOf("3", "2"), merged.map { it.id })
    }

    @Test fun variantsPutTheRememberedOneFirst() {
        val pool = listOf(live("1", "ESPN SD"), live("2", "ESPN 4K"), live("3", "ESPN HD"), live("9", "CNN"))
        assertEquals(listOf("2", "3", "1"), variantsOf(pool[1], pool, null).map { it.id })
        assertEquals(listOf("3", "2", "1"), variantsOf(pool[1], pool, "3").map { it.id })
    }

    @Test fun entryRoundTripKeepsEverything() {
        val e = Entry(
            Kind.EPISODE, "42", "Pilot", ext = "mkv", icon = "http://x/y.png", num = 7, cat = "5",
            season = 2, parent = "Show", series = "99", epgId = "abc.tv",
        )
        val back = decodeEntry(e.encode())!!
        assertEquals(e.copy(), back)
        assertEquals("abc.tv", back.epgId)
    }

    @Test fun oldSavedEntriesWithoutGuideIdStillLoad() {
        val old = listOf("LIVE", "7", "ts", "ESPN", "", "3", "1", "0", "", "").joinToString("\u0001")
        val e = decodeEntry(old)!!
        assertEquals("", e.epgId)
        assertEquals("ESPN", e.title)
    }

    @Test fun garbageEntriesAreRejected() {
        assertNull(decodeEntry("nonsense"))
        assertNull(decodeEntry("BOGUS\u00017\u0001ts\u0001Name"))
    }

    @Test fun reminderAndSeriesRuleRoundTrip() {
        val ch = live("12", "Sports One")
        val r = Reminder(ch, 1_700_000_000_000L, "Final")
        assertEquals(r, decodeReminder(encodeReminder(r)))
        val (c, title) = decodeRule(encodeRule(ch, "Friends"))!!
        assertEquals("12", c.id)
        assertEquals("Friends", title)
        assertNull(decodeRule("no separator here"))
    }
}
