package com.greenslocks.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayModeTest {
    private fun m(id: Int, w: Int, h: Int, r: Float) = DisplayTools.ModeInfo(id, w, h, r)
    private val modes = listOf(
        m(1, 3840, 2160, 60f), m(2, 3840, 2160, 59.94f), m(3, 3840, 2160, 50f),
        m(4, 3840, 2160, 30f), m(5, 3840, 2160, 24f), m(6, 3840, 2160, 23.976f),
        m(7, 1920, 1080, 24f),
    )

    @Test fun filmGetsItsOwnRateAtTheCurrentResolution() {
        assertEquals(6, DisplayTools.pickMode(modes, modes[0], 23.976f)?.id)
        assertEquals(5, DisplayTools.pickMode(modes, modes[0], 24f)?.id)
    }

    @Test fun neverChangesResolution() {
        val onlyHd = listOf(m(1, 3840, 2160, 60f), m(7, 1920, 1080, 24f))
        assertNull(DisplayTools.pickMode(onlyHd, onlyHd[0], 24f))
    }

    @Test fun sixtyHertzMultiplesAreFine() {
        // 30 fps video on a TV that only offers 60 Hz is already a whole multiple.
        val sixty = listOf(m(1, 3840, 2160, 60f))
        assertNull(DisplayTools.pickMode(sixty, sixty[0], 30f))
        // 59.94 fps wants 59.94, not 60.
        assertEquals(2, DisplayTools.pickMode(modes, modes[0], 59.94f)?.id)
    }

    @Test fun staysPutWhenAlreadyBest() {
        assertNull(DisplayTools.pickMode(modes, modes[5], 23.976f))
        assertNull(DisplayTools.pickMode(modes, modes[0], 5f))
    }
}
