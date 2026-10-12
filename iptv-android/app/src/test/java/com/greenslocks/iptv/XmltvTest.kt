package com.greenslocks.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.kxml2.io.KXmlParser
import org.junit.Test
import java.io.StringReader

class XmltvTest {
    @Test fun timeWithPositiveZone() {
        // 19:30 at UTC+1 is 18:30 UTC.
        assertEquals(XmltvParser.parseTime("20261012183000 +0000"), XmltvParser.parseTime("20261012193000 +0100"))
    }

    @Test fun timeWithNegativeZone() {
        assertEquals(XmltvParser.parseTime("20261012193000 +0000"), XmltvParser.parseTime("20261012143000 -0500"))
    }

    @Test fun missingZoneMeansUtc() {
        assertEquals(XmltvParser.parseTime("20261012120000 +0000"), XmltvParser.parseTime("20261012120000"))
    }

    @Test fun shortAndInvalidTimes() {
        assertEquals(0L, XmltvParser.parseTime("2026"))
        assertEquals(0L, XmltvParser.parseTime(""))
        assertEquals(XmltvParser.parseTime("20261012120000 +0000"), XmltvParser.parseTime("202610121200 +0000"))
    }

    @Test fun readsChannelsAndProgrammesInsideTheWindow() {
        val xml = """
            <tv>
              <channel id="ESPN.us"><display-name>ESPN</display-name><display-name>ESPN HD</display-name></channel>
              <channel id="cnn"><display-name lang="en">CNN</display-name></channel>
              <programme start="20261012180000 +0000" stop="20261012190000 +0000" channel="ESPN.us">
                <title lang="en">SportsCenter</title><desc>Highlights</desc>
              </programme>
              <programme start="20260101000000 +0000" stop="20260101010000 +0000" channel="cnn"><title>Old news</title></programme>
              <programme start="20261012200000 +0000" stop="20261012210000 +0000" channel="cnn"><title>   </title></programme>
              <programme start="20261012200000 +0000" stop="20261012210000 +0000" channel="cnn"><title>Tonight</title></programme>
            </tv>
        """.trimIndent()
        val parser = KXmlParser().apply { setInput(StringReader(xml)) }
        val from = XmltvParser.parseTime("20261012170000 +0000")
        val to = XmltvParser.parseTime("20261013170000 +0000")
        val channels = LinkedHashMap<String, List<String>>()
        val programmes = ArrayList<Triple<String, Long, String>>()
        XmltvParser.parse(parser, from, to, { id, names -> channels[id] = names }) { ch, start, _, title ->
            programmes += Triple(ch, start, title)
        }
        assertEquals(listOf("ESPN", "ESPN HD"), channels["ESPN.us"])
        assertEquals(listOf("CNN"), channels["cnn"])
        // The old programme is outside the window and the blank title is skipped.
        assertEquals(listOf("SportsCenter", "Tonight"), programmes.map { it.third })
        assertTrue(programmes.all { it.second >= from })
    }
}
