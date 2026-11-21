package app.aiusage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** The last numbers and errors, kept on the phone so the widget can draw without a network call. */
object Store {
    /** Bumped on every change so screens know to read again. */
    val changes = MutableStateFlow(0)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("aiusage", Context.MODE_PRIVATE)

    /** The look of one widget on the home screen. Widgets made before each had its own look use the old shared one. */
    /** When the refresh button was tapped (0 = nothing pending). The widget says "Refreshing..." meanwhile. */
    fun refreshRequestedAt(c: Context): Long = prefs(c).getLong("refreshRequestedAt", 0L)

    fun setRefreshRequestedAt(c: Context, at: Long) {
        prefs(c).edit().putLong("refreshRequestedAt", at).apply()
    }

    /** When the last tap-triggered refresh finished: the widget shows a green tick for a few seconds. */
    fun refreshDoneAt(c: Context): Long = prefs(c).getLong("refreshDoneAt", 0L)

    fun setRefreshDoneAt(c: Context, at: Long) {
        prefs(c).edit().putLong("refreshDoneAt", at).apply()
    }

    /** Which services a widget shows (at most two fit). Empty = the first two connected ones. */
    fun services(c: Context, widgetId: Int): List<Provider> =
        prefs(c).getString("services_$widgetId", null)?.split(',')?.mapNotNull { Provider.byId(it) }.orEmpty()

    fun setServices(c: Context, widgetId: Int, list: List<Provider>) {
        prefs(c).edit().putString("services_$widgetId", list.joinToString(",") { it.id }).apply()
        changes.value++
    }

    fun style(c: Context, widgetId: Int): WidgetStyle =
        WidgetStyle.byName(prefs(c).getString("style_$widgetId", null) ?: prefs(c).getString("style", null))

    fun setStyle(c: Context, widgetId: Int, style: WidgetStyle) {
        prefs(c).edit().putString("style_$widgetId", style.name).apply()
        changes.value++
    }

    fun removeStyle(c: Context, widgetId: Int) {
        prefs(c).edit().remove("style_$widgetId").remove("services_$widgetId").apply()
        changes.value++
    }

    /** Debug builds only: draw made-up numbers, to check how the widget looks. */
    fun demo(c: Context): Boolean = prefs(c).getBoolean("demo", false)

    fun demoCount(c: Context): Int = prefs(c).getInt("demoCount", 2)

    fun setDemoCount(c: Context, n: Int) {
        prefs(c).edit().putInt("demoCount", n.coerceIn(1, 4)).apply()
    }

    fun setDemo(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("demo", on).apply()
        changes.value++
    }

    fun snapshot(c: Context, p: Provider): Snapshot? =
        prefs(c).getString("snap_${p.id}", null)?.let(Snapshot::fromJson)

    fun error(c: Context, p: Provider): String? = prefs(c).getString("err_${p.id}", null)

    fun save(c: Context, p: Provider, snapshot: Snapshot) {
        prefs(c).edit().putString("snap_${p.id}", snapshot.toJson()).remove("err_${p.id}").apply()
        changes.value++
    }

    fun saveError(c: Context, p: Provider, message: String) {
        prefs(c).edit().putString("err_${p.id}", message).apply()
        changes.value++
    }

    fun clear(c: Context, p: Provider) {
        prefs(c).edit().remove("snap_${p.id}").remove("err_${p.id}").apply()
        changes.value++
    }
}
