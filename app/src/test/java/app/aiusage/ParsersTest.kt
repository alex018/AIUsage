package app.aiusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    private val now = 1_800_000_000_000L

    @Test fun claudeLimitsList() {
        val s = Parsers.claude(
            """{"org":{"capabilities":["chat","claude_pro"],"tier":"default_claude_pro"},
               "usage":{"limits":[
                 {"kind":"session","percent":38,"resets_at":"2026-11-04T20:49:59.75+00:00","scope":null},
                 {"kind":"weekly_all","percent":59.5,"resets_at":"2026-11-07T03:59:59.75+00:00","scope":null}],
                "seven_day_breakdown":{"rows":[{"key":"claude_code","display_name":"Claude Code","percent":83}]}}}""",
            now,
        )
        assertEquals("Pro", s.plan)
        assertEquals(listOf("5-hour", "Weekly"), s.meters.map { it.label })
        assertEquals(38.0, s.meters[0].percent, 0.0)
        assertEquals(59.5, s.meters[1].percent, 0.0)
        assertNotNull(s.meters[0].resetsAtMs)
        assertTrue(s.notes.single().contains("83%"))
    }

    @Test fun claudeLegacyKeysSkipNulls() {
        val s = Parsers.claude(
            """{"org":{"capabilities":["chat"],"tier":"default_claude_max_5x"},
               "usage":{"five_hour":{"utilization":12.0,"resets_at":"2026-11-04T20:00:00Z"},
                        "seven_day":{"utilization":40.0,"resets_at":null},
                        "seven_day_opus":null,"seven_day_sonnet":{"utilization":5.0,"resets_at":"2026-11-07T00:00:00Z"}}}""",
            now,
        )
        assertEquals("Max", s.plan)
        assertEquals(listOf("5-hour", "Weekly", "Weekly Sonnet"), s.meters.map { it.label })
        assertNull(s.meters[1].resetsAtMs)
    }

    @Test fun claudeFreePlanHasNoMeters() {
        val s = Parsers.claude("""{"org":{"capabilities":["chat"],"tier":null},"usage":{"limits":[],"five_hour":null}}""", now)
        assertTrue(s.meters.isEmpty())
    }

    @Test fun codexWindows() {
        val s = Parsers.codex(
            """{"plan_type":"plus","rate_limit":{
                 "primary_window":{"used_percent":21,"limit_window_seconds":18000,"reset_after_seconds":3600,"reset_at":1800003600},
                 "secondary_window":{"used_percent":64.5,"limit_window_seconds":604800,"reset_after_seconds":86400,"reset_at":1800086400}},
               "credits":{"has_credits":true,"unlimited":false,"balance":"12.50"}}""",
            now,
        )
        assertEquals("Plus", s.plan)
        assertEquals(listOf("5-hour", "Weekly"), s.meters.map { it.label })
        assertEquals(21.0, s.meters[0].percent, 0.0)
        assertEquals(1_800_003_600_000L, s.meters[0].resetsAtMs)
        assertEquals("Credits left: 12.50", s.notes.single())
    }

    @Test fun codexFallsBackToResetAfter() {
        val s = Parsers.codex("""{"rate_limit":{"primary_window":{"used_percent":5,"limit_window_seconds":18000,"reset_after_seconds":600}}}""", now)
        assertEquals(now + 600_000, s.meters.single().resetsAtMs)
    }

    @Test fun snapshotJsonRoundTrip() {
        val s = Snapshot("codex", "Plus", listOf(Meter("Weekly", 10.0, null), Meter("x", 2.0, 5L)), listOf("n"), 7L)
        assertEquals(s, Snapshot.fromJson(s.toJson()))
    }

    @Test fun formats() {
        assertEquals("now", Format.resetIn(now - 1, now))
        assertEquals("2h 10m", Format.resetIn(now + (130 * 60_000L), now))
        assertEquals("3d 4h", Format.resetIn(now + ((3 * 24 + 4) * 3_600_000L), now))
        assertEquals("5 min ago", Format.ago(now - 5 * 60_000L, now))
    }
}
