package app.aiusage

import android.content.Context

/** Turns what is stored into what the widget and the look picker draw. */
object WidgetModel {
    private val SHORT = mapOf(Provider.CLAUDE to "Claude", Provider.CODEX to "Codex", Provider.GEMINI to "Gemini")

    /** [only]: show just these services (empty = all). The looks have room for two. */
    fun build(context: Context, now: Long, only: List<Provider> = emptyList()): WidgetData {
        val rows = mutableListOf<Row>()
        var oldest: Long? = null
        var anyError = false
        var anyConnected = false
        for (p in Provider.ALL) {
            if (!p.signedIn()) continue
            anyConnected = true
            if (only.isNotEmpty() && p !in only) continue
            if (Store.error(context, p) != null) anyError = true
            val snap = Store.snapshot(context, p) ?: continue
            oldest = minOf(oldest ?: snap.fetchedAtMs, snap.fetchedAtMs)
            val name = SHORT[p] ?: p.title
            rows += Row(name, Pace.mainMeters(snap.meters).map { Pace.item(name, it, now) })
        }
        while (rows.size > WidgetStyle.MAX_SERVICES) rows.removeAt(rows.size - 1)
        val requested = Store.refreshRequestedAt(context)
        val refreshing = requested != 0L && now - requested in 0..60_000
        val done = !refreshing && now - Store.refreshDoneAt(context) in 0..4_000
        val state = when {
            refreshing -> RefreshState.REFRESHING
            done -> RefreshState.DONE
            else -> RefreshState.IDLE
        }
        val header = when {
            refreshing -> "Refreshing…"
            done -> "Updated just now"
            anyError -> "Couldn't refresh. Open the app."
            oldest != null -> "Updated ${Format.ago(oldest, now)}"
            else -> ""
        }
        val message = when {
            rows.isNotEmpty() -> null
            !anyConnected -> "Tap here to connect your accounts"
            else -> "Getting your numbers…"
        }
        return WidgetData(rows, header, message, state)
    }

    /**
     * Made-up numbers, to show what a look is like before anything is connected.
     * [names]: which of "Claude", "Codex", "Gemini", "DeepSeek" to include (empty: the first [count]).
     */
    fun demo(now: Long, names: List<String> = emptyList(), count: Int = 2): WidgetData {
        val min = 60_000L
        val day = 24 * 60 * min
        fun row(name: String, a: Meter, b: Meter) = Row(name, listOf(Pace.item(name, a, now), Pace.item(name, b, now)))
        val all = listOf(
            row("Claude", Meter("5-hour", 38.0, now + 130 * min), Meter("Weekly", 66.0, now + 3 * day + 4 * 60 * min)),
            row("Codex", Meter("5-hour", 12.0, now + 45 * min), Meter("Weekly", 86.0, now + 5 * day + 2 * 60 * min)),
            row("Gemini", Meter("5-hour", 24.0, now + 185 * min), Meter("Weekly", 41.0, now + 4 * day + 6 * 60 * min)),
            row("DeepSeek", Meter("5-hour", 55.0, now + 90 * min), Meter("Weekly", 30.0, now + 2 * day + 3 * 60 * min)),
        )
        val rows = if (names.isEmpty()) all.take(count) else all.filter { it.service in names }.ifEmpty { all.take(count) }
        return WidgetData(rows = rows, header = "Updated 3 min ago")
    }
}
