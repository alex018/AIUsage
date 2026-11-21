package app.aiusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class GeminiTest {
    private val zone = ZoneId.of("UTC")
    private val now = LocalDateTime.of(2026, 11, 21, 12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test fun relativeResets() {
        assertEquals(now + (3 * 60 + 12) * 60_000L, Parsers.parseReset("Resets in 3 hr 12 min", now, zone))
        assertEquals(now + (2 * 24 * 60 + 5 * 60) * 60_000L, Parsers.parseReset("Resets in 2 days 5 hours", now, zone))
        assertEquals(now + 45 * 60_000L, Parsers.parseReset("resets in 45 minutes", now, zone))
    }

    @Test fun absoluteResets() {
        assertEquals(at(2026, 11, 22, 6, 0), Parsers.parseReset("Resets tomorrow at 6 AM", now, zone))
        assertEquals(at(2026, 12, 3, 21, 0), Parsers.parseReset("Resets Dec 3, 9:00 PM", now, zone))
        // 2026-11-21 is a Saturday; the next Tuesday is the 24th
        assertEquals(at(2026, 11, 24, 9, 0), Parsers.parseReset("Resets Tue 9:00 AM", now, zone))
        assertEquals(at(2026, 11, 21, 18, 30), Parsers.parseReset("Resets today at 18:30", now, zone))
    }

    @Test fun unknownWordingGivesNull() {
        assertNull(Parsers.parseReset("Resets soon", now, zone))
        assertNull(Parsers.parseReset("", now, zone))
    }

    @Test fun usagePageBlocks() {
        val s = Parsers.gemini(
            """{"cur":"Current limit\n38% used\nResets in 2 hr 10 min","wk":"Weekly limit\n61% used\nResets Dec 3, 9:00 PM","page":""}""",
            now, zone,
        )
        assertEquals(listOf("5-hour", "Weekly"), s.meters.map { it.label })
        assertEquals(38.0, s.meters[0].percent, 0.0)
        assertEquals(now + 130 * 60_000L, s.meters[0].resetsAtMs)
        assertEquals(61.0, s.meters[1].percent, 0.0)
        assertEquals(at(2026, 12, 3, 21, 0), s.meters[1].resetsAtMs)
    }

    @Test fun percentLeftIsTurnedIntoUsed() {
        val s = Parsers.gemini("""{"cur":"72% left\nResets in 1 hr","wk":"","page":""}""", now, zone)
        assertEquals(28.0, s.meters.single().percent, 0.0)
    }

    @Test fun fallsBackToWholePageText() {
        val s = Parsers.gemini("""{"cur":"","wk":"","page":"Usage limits\nCurrent\n20% used\nResets in 1 hr\nWeekly\n50% used\nResets in 2 days"}""", now, zone)
        assertEquals(listOf(20.0, 50.0), s.meters.map { it.percent })
        assertNotNull(s.meters[1].resetsAtMs)
    }

    @Test fun unreadableResetIsKeptAsANote() {
        val s = Parsers.gemini("""{"cur":"10% used\nResets whenever","wk":"","page":""}""", now, zone)
        assertNull(s.meters.single().resetsAtMs)
        assertEquals(listOf("5-hour: Resets whenever"), s.notes)
    }

    @Test fun realShapeOfTheUsagePage() {
        // Text of gemini.google.com/usage as seen in November 2026 (with made-up numbers).
        val page = "Gemini\nUsage limits\nPLUS\n\nYour plan's limits determine how much you can use Gemini over time. " +
            "Advanced models and features can take up more usage. Learn more\n\nUpdated just now\n\nCurrent usage\ninfo\n" +
            "18% used\nResets at 11:19 PM\nWeekly limit\nResets Nov 24 at 12:19 PM\n3% used\nGet 2x more usage with AI Pro\nUpgrade"
        val json = org.json.JSONObject().put("cur", "").put("wk", "").put("page", page).toString()
        val s = Parsers.gemini(json, now, zone)
        assertEquals("Plus", s.plan)
        assertEquals(listOf("5-hour", "Weekly"), s.meters.map { it.label })
        assertEquals(18.0, s.meters[0].percent, 0.0)
        assertEquals(at(2026, 11, 21, 23, 19), s.meters[0].resetsAtMs)
        assertEquals(3.0, s.meters[1].percent, 0.0)
        assertEquals(at(2026, 11, 24, 12, 19), s.meters[1].resetsAtMs)
        assertEquals(emptyList<String>(), s.notes)
    }
}
