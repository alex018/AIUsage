package app.aiusage

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.Toast

/** The home-screen widget. The whole picture is drawn by [WidgetDraw] in the look chosen in the app. */
class UsageWidget : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        RefreshWorker.schedule(context)
        RefreshWorker.refreshNow(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        RefreshWorker.schedule(context)
        ids.forEach { update(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        update(context, manager, id)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { Store.removeStyle(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                Store.setRefreshRequestedAt(context, System.currentTimeMillis())
                Store.setRefreshDoneAt(context, 0L)
                updateAll(context)
                RefreshWorker.refreshNow(context)
                // Feedback the moment you tap: a short buzz and a message.
                buzz(context)
                Toast.makeText(context, "Refreshing…", Toast.LENGTH_SHORT).show()
            }
            // Only in debug builds: `am broadcast -n <app>/app.aiusage.UsageWidget -a app.aiusage.DEMO --ez on true --ei n 4`
            ACTION_DEMO -> if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                Store.setDemo(context, intent.getBooleanExtra("on", true))
                Store.setDemoCount(context, intent.getIntExtra("n", 2))
                updateAll(context)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun buzz(context: Context) {
        runCatching {
            val v = if (android.os.Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }
            v.vibrate(android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    companion object {
        const val ACTION_REFRESH = "app.aiusage.REFRESH"
        const val ACTION_DEMO = "app.aiusage.DEMO"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, UsageWidget::class.java)).forEach { update(context, manager, it) }
        }

        fun isDark(context: Context) =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        fun data(context: Context, widgetId: Int, now: Long = System.currentTimeMillis()): WidgetData =
            if (Store.demo(context)) WidgetModel.demo(now, count = Store.demoCount(context)) else WidgetModel.build(context, now, Store.services(context, widgetId))

        fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, UsageWidget::class.java))

        fun update(context: Context, id: Int) = update(context, AppWidgetManager.getInstance(context), id)

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val o = manager.getAppWidgetOptions(id)
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 250
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: 180
            manager.updateAppWidget(id, render(context, id, w.toFloat(), h.toFloat()))
        }

        fun render(context: Context, widgetId: Int, widthDp: Float, heightDp: Float): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_usage)
            val data = data(context, widgetId)
            val bmp = WidgetDraw.render(Store.style(context, widgetId), data, widthDp, heightDp, context.resources.displayMetrics.density, isDark(context))
            views.setImageViewBitmap(R.id.img, bmp)
            val summary = data.message ?: data.rows.joinToString(". ") { r ->
                r.service + ": " + r.items.joinToString(", ") { "${it.label} ${Math.round(it.used)} percent used, ${it.status.word}" }
            }
            views.setContentDescription(R.id.img, summary)

            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.root, open)
            val refresh = PendingIntent.getBroadcast(context, 1,
                Intent(context, UsageWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.refreshHit, refresh)
            return views
        }
    }
}
