package app.aiusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaceTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L
    private val hour = 3_600_000L

    @Test fun windowsFromLabels() {
        assertEquals(5 * hour, Pace.windowMs("5-hour"))
        assertEquals(7 * day, Pace.windowMs("Weekly"))
        assertEquals(7 * day, Pace.windowMs("Weekly Opus"))
        assertEquals(2 * day, Pace.windowMs("2 days"))
        assertNull(Pace.windowMs("Something"))
    }

    @Test fun evenPaceHalfwayThroughAWeek() {
        val m = Meter("Weekly", 50.0, now + 3 * day + 12 * hour)
        assertEquals(50.0, Pace.percent(m, now)!!, 0.001)
    }

    @Test fun noResetTimeMeansNoPace() {
        assertNull(Pace.percent(Meter("Weekly", 50.0, null), now))
    }

    @Test fun statusRules() {
        assertEquals(Status.ON_TRACK, Pace.status(38.0, 57.0))
        assertEquals(Status.ON_TRACK, Pace.status(60.0, 55.0))
        assertEquals(Status.A_BIT_FAST, Pace.status(66.0, 55.0))
        assertEquals(Status.TOO_FAST, Pace.status(86.0, 27.0))
        // never green from 75%, always red from 90%
        assertEquals(Status.A_BIT_FAST, Pace.status(76.0, 80.0))
        assertEquals(Status.TOO_FAST, Pace.status(91.0, 99.0))
        // no pace: by the plain number
        assertEquals(Status.ON_TRACK, Pace.status(30.0, null))
        assertEquals(Status.A_BIT_FAST, Pace.status(60.0, null))
        assertEquals(Status.TOO_FAST, Pace.status(85.0, null))
    }

    @Test fun runsOutBeforeResetOnly() {
        // 86% used 46 h into a week: about 7.5 h left, reset is 5 days away
        val fast = Meter("Weekly", 86.0, now + 7 * day - 46 * hour)
        val ms = Pace.runsOutInMs(fast, now)
        assertNotNull(ms)
        assertEquals(7.5, ms!! / hour.toDouble(), 0.2)
        // slow user: lasts until the reset
        val slow = Meter("5-hour", 38.0, now + 2 * hour + 10 * 60_000L)
        assertNull(Pace.runsOutInMs(slow, now))
    }

    @Test fun tightestIsTheWorstOne() {
        val a = Pace.item("Claude", Meter("5-hour", 38.0, now + 130 * 60_000L), now)
        val b = Pace.item("Claude", Meter("Weekly", 66.0, now + 3 * day + 4 * hour), now)
        assertEquals("Weekly", Pace.tightest(listOf(a, b))!!.label)
    }
}
