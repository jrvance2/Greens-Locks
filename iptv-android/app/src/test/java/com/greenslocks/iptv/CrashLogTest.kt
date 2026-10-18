package com.greenslocks.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogTest {
    @Test fun aReportNamesTheVersionDeviceAndError() {
        val text = CrashLog.format(IllegalStateException("boom"), "1.2.51-beta", "Amazon AFTGAZL, Android 9", 1_700_000_000_000L)
        assertTrue(text.startsWith("=== "))
        assertTrue(text.contains("v1.2.51-beta"))
        assertTrue(text.contains("Amazon AFTGAZL"))
        assertTrue(text.contains("IllegalStateException: boom"))
        assertTrue(text.endsWith("\n\n"))
    }

    @Test fun latestIsTheNewestReport() {
        val text = CrashLog.format(RuntimeException("old"), "1", "d", 1L) + CrashLog.format(RuntimeException("new"), "1", "d", 2L)
        val last = CrashLog.latest(text)!!
        assertTrue(last.contains("new"))
        assertFalse(last.contains("old"))
        assertNull(CrashLog.latest("nothing here"))
    }

    @Test fun trimDropsOldestReportsAndStartsOnABoundary() {
        val a = "=== A ===\n" + "x".repeat(300) + "\n\n"
        val b = "=== B ===\n" + "y".repeat(300) + "\n\n"
        val c = "=== C ===\n" + "z".repeat(300) + "\n\n"
        val trimmed = CrashLog.trim(a + b + c, max = 700)
        assertTrue(trimmed.startsWith("=== "))
        assertFalse(trimmed.contains("=== A"))
        assertTrue(trimmed.contains("=== C"))
        assertEquals(a + b, CrashLog.trim(a + b, max = 2_000)) // small logs are left alone
    }
}

class AddedTextTest {
    private val now = 1_800_000_000_000L
    private fun ago(days: Int) = now / 1000 - days * 86_400L - 3_600

    @Test fun labelsRecentAdditions() {
        assertEquals("Added today", AddedText.label(now / 1000 - 60, now))
        assertEquals("Added yesterday", AddedText.label(ago(1), now))
        assertEquals("Added 12 days ago", AddedText.label(ago(12), now))
        assertNull(AddedText.label(ago(200), now))
        assertNull(AddedText.label(0L, now))
    }

    @Test fun newMeansTwoWeeksOrLess() {
        assertTrue(AddedText.isNew(ago(3), now))
        assertTrue(AddedText.isNew(ago(14), now))
        assertFalse(AddedText.isNew(ago(15), now))
        assertFalse(AddedText.isNew(0L, now))
    }
}
