package app.aiusage

object Format {
    /** "2h 10m", "3d 4h", "now". */
    fun resetIn(resetsAtMs: Long?, nowMs: Long): String? {
        if (resetsAtMs == null) return null
        val min = (resetsAtMs - nowMs) / 60_000
        return when {
            min <= 0 -> "now"
            min < 60 -> "${min}m"
            min < 24 * 60 -> if (min % 60 == 0L) "${min / 60}h" else "${min / 60}h ${min % 60}m"
            else -> {
                val h = (min / 60) % 24
                if (h == 0L) "${min / (24 * 60)}d" else "${min / (24 * 60)}d ${h}h"
            }
        }
    }

    /** "just now", "5 min ago", "3 h ago". */
    fun ago(thenMs: Long, nowMs: Long): String {
        val min = (nowMs - thenMs) / 60_000
        return when {
            min < 1 -> "just now"
            min < 60 -> "$min min ago"
            min < 24 * 60 -> "${min / 60} h ago"
            else -> "${min / (24 * 60)} d ago"
        }
    }

    fun percentText(p: Double): String = "${Math.round(p)}%"
}
