package app.aiusage

enum class Status(val word: String) {
    ON_TRACK("On track"),
    A_BIT_FAST("A bit fast"),
    TOO_FAST("Too fast"),
}

/** One limit, ready to draw. */
data class Item(
    val service: String,
    val label: String,
    val used: Double,
    /** Where an even pace would be right now (0..100), or null when we cannot tell. */
    val pace: Double?,
    val status: Status,
    /** "2h 10m", "3d 4h", or null. */
    val resetIn: String?,
    /** How long until it runs out at this speed, only when that happens before the reset. */
    val runsOutIn: String?,
)

/**
 * "Even pace": if you spread a limit evenly over its whole time window, how much would you have used by now?
 * A week is 7 days, so about 14% a day.
 */
object Pace {
    private val HOURS = Regex("^(\\d+)-hour")
    private val DAYS = Regex("^(\\d+) days")

    /** Length of the limit's window, worked out from its label. */
    fun windowMs(label: String): Long? {
        HOURS.find(label)?.let { return it.groupValues[1].toLong() * 3_600_000L }
        DAYS.find(label)?.let { return it.groupValues[1].toLong() * 86_400_000L }
        if (label.startsWith("Weekly")) return 7 * 86_400_000L
        return null
    }

    private fun elapsedMs(m: Meter, now: Long): Long? {
        val window = windowMs(m.label) ?: return null
        val reset = m.resetsAtMs ?: return null
        return (window - (reset - now)).coerceIn(0L, window)
    }

    /** 0..100, or null. */
    fun percent(m: Meter, now: Long): Double? {
        val window = windowMs(m.label) ?: return null
        return elapsedMs(m, now)?.let { it * 100.0 / window }
    }

    /**
     * Green while you are at or before the even pace (up to 5 points past it), orange up to 20 points past,
     * red beyond. 75% used is never green, 90% used is always red. Without a pace: by the plain number.
     */
    fun status(used: Double, pace: Double?): Status {
        if (used >= 90) return Status.TOO_FAST
        val base = if (pace == null) {
            when {
                used >= 80 -> Status.TOO_FAST
                used >= 50 -> Status.A_BIT_FAST
                else -> Status.ON_TRACK
            }
        } else {
            val ahead = used - pace
            when {
                ahead > 20 -> Status.TOO_FAST
                ahead > 5 -> Status.A_BIT_FAST
                else -> Status.ON_TRACK
            }
        }
        return if (used >= 75 && base == Status.ON_TRACK) Status.A_BIT_FAST else base
    }

    /** Time until the limit is used up at the speed so far, if that comes before the reset. */
    fun runsOutInMs(m: Meter, now: Long): Long? {
        val window = windowMs(m.label) ?: return null
        val elapsed = elapsedMs(m, now) ?: return null
        val reset = m.resetsAtMs ?: return null
        if (elapsed < window / 20 || m.percent <= 0.0 || m.percent >= 100.0) return null
        val ratePerMs = m.percent / elapsed
        val remaining = ((100.0 - m.percent) / ratePerMs).toLong()
        return if (remaining < reset - now) remaining else null
    }

    fun item(service: String, m: Meter, now: Long): Item {
        val pace = percent(m, now)
        return Item(
            service = service,
            label = m.label,
            used = m.percent,
            pace = pace,
            status = status(m.percent, pace),
            resetIn = Format.resetIn(m.resetsAtMs, now),
            runsOutIn = runsOutInMs(m, now)?.let { Format.resetIn(now + it, now) },
        )
    }

    /** The two limits that matter: the short one, then the weekly one. */
    fun mainMeters(all: List<Meter>): List<Meter> =
        listOfNotNull(all.firstOrNull { it.label.endsWith("-hour") }, all.firstOrNull { it.label == "Weekly" })
            .ifEmpty { all.take(2) }

    /** The limit giving the most worry: worst status, then furthest past the even pace. */
    fun tightest(items: List<Item>): Item? =
        items.maxWithOrNull(compareBy<Item>({ it.status.ordinal }, { it.used - (it.pace ?: 0.0) }))
}
