package app.aiusage

import org.json.JSONArray
import org.json.JSONObject

/** One limit: how much of it is used and when it resets. */
data class Meter(
    val label: String,
    /** 0..100. Can pass 100 when a limit is overrun. */
    val percent: Double,
    /** Epoch millis, or null when the service did not say. */
    val resetsAtMs: Long?,
)

/** What one service said the last time we asked. */
data class Snapshot(
    val provider: String,
    val plan: String?,
    val meters: List<Meter>,
    /** Extra one-line facts (credits left, share used by Claude Code, ...). */
    val notes: List<String>,
    val fetchedAtMs: Long,
) {
    fun toJson(): String = JSONObject().apply {
        put("provider", provider)
        put("plan", plan ?: JSONObject.NULL)
        put("fetchedAtMs", fetchedAtMs)
        put("notes", JSONArray(notes))
        put("meters", JSONArray().also { arr ->
            meters.forEach {
                arr.put(JSONObject().put("label", it.label).put("percent", it.percent)
                    .put("resetsAtMs", it.resetsAtMs ?: JSONObject.NULL))
            }
        })
    }.toString()

    companion object {
        fun fromJson(text: String): Snapshot? = runCatching {
            val o = JSONObject(text)
            val meters = o.getJSONArray("meters")
            val notes = o.optJSONArray("notes")
            Snapshot(
                provider = o.getString("provider"),
                plan = if (o.isNull("plan")) null else o.getString("plan"),
                meters = (0 until meters.length()).map {
                    val m = meters.getJSONObject(it)
                    Meter(m.getString("label"), m.getDouble("percent"),
                        if (m.isNull("resetsAtMs")) null else m.getLong("resetsAtMs"))
                },
                notes = notes?.let { n -> (0 until n.length()).map { n.getString(it) } } ?: emptyList(),
                fetchedAtMs = o.getLong("fetchedAtMs"),
            )
        }.getOrNull()
    }
}
