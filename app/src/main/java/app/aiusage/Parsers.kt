package app.aiusage

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Turns what claude.ai and chatgpt.com answer into [Snapshot]s.
 * Both answers are undocumented and can change, so every field is read defensively.
 */
object Parsers {

    // ---- Claude --------------------------------------------------------------------------

    /**
     * [text] is what ClaudeSource's script returns:
     * `{"org": {"capabilities": [...], "tier": "..."}, "usage": {...} }` (usage is null when the call failed).
     */
    fun claude(text: String, nowMs: Long): Snapshot {
        val root = JSONObject(text)
        val org = root.optJSONObject("org")
        val usage = root.optJSONObject("usage") ?: JSONObject()
        val meters = mutableListOf<Meter>()

        val limits = usage.optJSONArray("limits")
        if (limits != null && limits.length() > 0) {
            for (i in 0 until limits.length()) {
                val l = limits.getJSONObject(i)
                val percent = number(l, "percent", "utilization") ?: continue
                val kind = l.optString("kind")
                val scope = l.optString("scope").takeIf { it.isNotBlank() && it != "null" }
                meters += Meter(claudeLabel(kind, scope), percent, time(l.opt("resets_at")))
            }
        } else {
            // Older shape with fixed keys. A null value means "not part of this plan".
            for ((key, label) in LEGACY_CLAUDE) {
                val w = usage.optJSONObject(key) ?: continue
                val percent = number(w, "utilization", "percent") ?: continue
                meters += Meter(label, percent, time(w.opt("resets_at")))
            }
        }

        val notes = mutableListOf<String>()
        usage.optJSONObject("seven_day_breakdown")?.optJSONArray("rows")?.let { rows ->
            for (i in 0 until rows.length()) {
                val r = rows.getJSONObject(i)
                if (r.optString("key") == "claude_code") {
                    number(r, "percent")?.let { notes += "Claude Code is ${Math.round(it)}% of your weekly use" }
                }
            }
        }
        usage.optJSONObject("extra_usage")?.takeIf { it.optBoolean("is_enabled") }?.let { e ->
            number(e, "utilization")?.let { notes += "Extra usage: ${Math.round(it)}% used" }
        }

        return Snapshot("claude", claudePlan(org), meters, notes, nowMs)
    }

    private val LEGACY_CLAUDE = listOf(
        "five_hour" to "5-hour",
        "seven_day" to "Weekly",
        "seven_day_opus" to "Weekly Opus",
        "seven_day_sonnet" to "Weekly Sonnet",
    )

    private fun claudeLabel(kind: String, scope: String?): String {
        val base = when (kind) {
            "session", "five_hour" -> "5-hour"
            "weekly_all", "weekly", "seven_day" -> "Weekly"
            "weekly_opus", "seven_day_opus" -> "Weekly Opus"
            "weekly_sonnet", "seven_day_sonnet" -> "Weekly Sonnet"
            else -> kind.replace('_', ' ').replaceFirstChar { it.uppercase() }.ifBlank { "Limit" }
        }
        return if (scope != null) "$base · $scope" else base
    }

    private fun claudePlan(org: JSONObject?): String? {
        if (org == null) return null
        val tier = org.optString("tier").lowercase()
        val caps = org.optJSONArray("capabilities")?.let { a -> (0 until a.length()).map { a.optString(it).lowercase() } } ?: emptyList()
        return when {
            "max" in tier || "claude_max" in caps -> "Max"
            "pro" in tier || "claude_pro" in caps -> "Pro"
            "team" in tier || "team" in caps -> "Team"
            "enterprise" in tier || "enterprise" in caps -> "Enterprise"
            else -> null
        }
    }

    // ---- ChatGPT Codex -------------------------------------------------------------------

    /** [text] is the body of chatgpt.com/backend-api/wham/usage. */
    fun codex(text: String, nowMs: Long): Snapshot {
        val root = JSONObject(text)
        val meters = mutableListOf<Meter>()
        windows(root.optJSONObject("rate_limit") ?: root.optJSONObject("rateLimit"), "", meters, nowMs)
        windows(root.optJSONObject("code_review_rate_limit") ?: root.optJSONObject("codeReviewRateLimit"),
            "Code review ", meters, nowMs)

        val notes = mutableListOf<String>()
        root.optJSONObject("credits")?.let { c ->
            when {
                c.optBoolean("unlimited") -> notes += "Credits: unlimited"
                c.optBoolean("has_credits") || c.optBoolean("hasCredits") ->
                    c.opt("balance")?.toString()?.takeIf { it != "null" }?.let { notes += "Credits left: $it" }
            }
        }
        val plan = (root.optString("plan_type").ifBlank { root.optString("planType") })
            .takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
        return Snapshot("codex", plan, meters, notes, nowMs)
    }

    private fun windows(rl: JSONObject?, prefix: String, out: MutableList<Meter>, nowMs: Long) {
        if (rl == null) return
        for (key in listOf("primary_window", "secondary_window", "primaryWindow", "secondaryWindow")) {
            val w = rl.optJSONObject(key) ?: continue
            val used = number(w, "used_percent", "usedPercent") ?: continue
            val seconds = number(w, "limit_window_seconds", "limitWindowSeconds")?.toLong()
            val resets = resetMs(w, nowMs)
            out += Meter(prefix + windowLabel(seconds, key.startsWith("primary")), used, resets)
        }
    }

    private fun resetMs(w: JSONObject, nowMs: Long): Long? {
        val at = w.opt("reset_at") ?: w.opt("resetAt")
        if (at != null && at != JSONObject.NULL) time(at)?.let { return it }
        number(w, "reset_after_seconds", "resetAfterSeconds")?.let { return nowMs + (it * 1000).toLong() }
        return null
    }

    fun windowLabel(seconds: Long?, primary: Boolean): String = when {
        seconds == null -> if (primary) "Short window" else "Long window"
        seconds % 604_800 == 0L -> if (seconds == 604_800L) "Weekly" else "${seconds / 604_800} weeks"
        seconds % 86_400 == 0L -> "${seconds / 86_400} days"
        seconds % 3_600 == 0L -> "${seconds / 3_600}-hour"
        else -> "${seconds / 60} min"
    }

    // ---- Gemini --------------------------------------------------------------------------

    /**
     * [text] is what GeminiSource's script returns from gemini.google.com/usage:
     * `{"cur": "<text of the current-limit block>", "wk": "<text of the weekly block>", "page": "<whole page text>"}`.
     * The page's wording is not documented, so this only relies on "NN%" and a line that says "reset".
     */
    fun gemini(text: String, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Snapshot {
        val o = JSONObject(text)
        val meters = mutableListOf<Meter>()
        val notes = mutableListOf<String>()

        fun add(label: String, raw: String) {
            val (used, resetLine) = geminiBlock(raw) ?: return
            val at = parseReset(resetLine, nowMs, zone)
            meters += Meter(label, used, at)
            if (at == null && resetLine.isNotBlank()) notes += "$label: $resetLine"
        }
        o.optString("cur").takeIf { it.isNotBlank() }?.let { add("5-hour", it) }
        o.optString("wk").takeIf { it.isNotBlank() }?.let { add("Weekly", it) }

        val lines = o.optString("page").lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (meters.isEmpty()) {
            // The page (seen in November 2026):
            //   Usage limits / PLUS / ... / Current usage / info / 18% used / Resets at 11:19 PM /
            //   Weekly limit / Resets Nov 24 at 12:19 PM / 2% used / Get 2x more usage ...
            // The reset line comes after the percent in the first block and before it in the second, so
            // split at the two headings and read each block on its own.
            val iCur = lines.indexOfFirst { it.contains("current", ignoreCase = true) }
            val iWk = lines.indexOfFirst { it.contains("weekly", ignoreCase = true) }
            if (iCur >= 0 && iWk >= 0) {
                val curEnd = if (iWk > iCur) iWk else lines.size
                val wkEnd = if (iCur > iWk) iCur else lines.size
                add("5-hour", lines.subList(iCur, curEnd).joinToString("\n"))
                add("Weekly", lines.subList(iWk, wkEnd).joinToString("\n"))
            } else {
                // No headings found: the first two percentages, the first for the short limit, the second for the weekly one.
                val at = lines.indices.filter { PERCENT.containsMatchIn(lines[it]) }.take(2)
                at.forEachIndexed { n, i ->
                    add(if (n == 0) "5-hour" else "Weekly", lines.subList(maxOf(0, i - 2), minOf(lines.size, i + 4)).joinToString("\n"))
                }
            }
        }
        // The plan is the short all-capitals line under the title ("PLUS", "PRO", "ULTRA").
        val titleAt = lines.indexOfFirst { it.contains("usage limits", ignoreCase = true) }
        val plan = lines.drop(maxOf(0, titleAt + 1)).take(2)
            .firstOrNull { it.length in 2..12 && it == it.uppercase() && it.any { ch -> ch.isLetter() } }
            ?.lowercase()?.replaceFirstChar { it.uppercase() }
        return Snapshot("gemini", plan, meters, notes, nowMs)
    }

    private val PERCENT = Regex("(\\d+(?:\\.\\d+)?)\\s*%")

    /** (percent used, the line that talks about the reset) */
    private fun geminiBlock(raw: String): Pair<Double, String>? {
        val m = PERCENT.find(raw) ?: return null
        var used = m.groupValues[1].toDouble()
        val after = raw.substring(m.range.last + 1, minOf(raw.length, m.range.last + 1 + 12)).lowercase()
        if ("left" in after || "remaining" in after) used = 100 - used
        val reset = raw.lines().map { it.trim() }.firstOrNull { it.contains("reset", ignoreCase = true) } ?: ""
        return used to reset
    }

    /**
     * "Resets in 3 hr 12 min", "resets in 2 days", "Resets Tue 9:00 AM", "Resets Dec 3, 9:00 PM", "resets tomorrow at 6 AM".
     * Returns epoch millis, or null when the wording is not understood.
     */
    fun parseReset(text: String, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val t = text.lowercase().trim()
        if (t.isEmpty()) return null

        if (Regex("\\bin\\s+(?:about\\s+|~\\s*)?\\d").containsMatchIn(t)) {
            fun unit(pattern: String) = Regex("(\\d+)\\s*(?:$pattern)\\b").find(t)?.groupValues?.get(1)?.toLong() ?: 0L
            val ms = unit("d|day|days") * 86_400_000L + unit("h|hr|hrs|hour|hours") * 3_600_000L + unit("m|min|mins|minute|minutes") * 60_000L
            if (ms > 0) return nowMs + ms
        }

        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), zone)
        val time = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)").find(t)?.let {
            var h = it.groupValues[1].toInt() % 12
            if (it.groupValues[3] == "pm") h += 12
            LocalTime.of(h, it.groupValues[2].ifEmpty { "0" }.toInt())
        } ?: Regex("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b").find(t)?.let {
            LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        } ?: return null

        val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        val monthDay = Regex("\\b(${months.joinToString("|")})[a-z]*\\.?\\s+(\\d{1,2})\\b").find(t)
        val date: LocalDate = when {
            monthDay != null -> {
                val month = months.indexOf(monthDay.groupValues[1]) + 1
                var d = LocalDate.of(now.year, month, monthDay.groupValues[2].toInt())
                if (LocalDateTime.of(d, time).isBefore(now.minusDays(1))) d = d.plusYears(1)
                d
            }
            "tomorrow" in t -> now.toLocalDate().plusDays(1)
            "today" in t -> now.toLocalDate()
            else -> {
                val days = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
                val wd = Regex("\\b(${days.joinToString("|")})[a-z]*\\b").find(t)?.let { days.indexOf(it.groupValues[1]) }
                if (wd != null) {
                    var d = now.toLocalDate()
                    while (d.dayOfWeek != DayOfWeek.of(wd + 1) || !LocalDateTime.of(d, time).isAfter(now)) d = d.plusDays(1)
                    d
                } else {
                    var d = now.toLocalDate()
                    if (!LocalDateTime.of(d, time).isAfter(now)) d = d.plusDays(1)
                    d
                }
            }
        }
        return LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun number(o: JSONObject, vararg keys: String): Double? {
        for (k in keys) {
            val v = o.opt(k)
            if (v is Number) return v.toDouble()
            if (v is String) v.toDoubleOrNull()?.let { return it }
        }
        return null
    }

    /** ISO-8601 text or epoch seconds/millis -> epoch millis. */
    private fun time(v: Any?): Long? = when (v) {
        null, JSONObject.NULL -> null
        is Number -> v.toLong().let { if (it < 100_000_000_000L) it * 1000 else it }
        is String -> v.takeIf { it.isNotBlank() }?.let {
            runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
                ?: it.toLongOrNull()?.let { n -> if (n < 100_000_000_000L) n * 1000 else n }
        }
        else -> null
    }
}
