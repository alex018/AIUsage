package app.aiusage

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Opens when you add a widget (and when you tap "Change look"): pick the look of THAT widget.
 * Every widget keeps its own look, so you can have several different ones.
 */
class WidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Backing out cancels: the launcher then does not add the widget.
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()

        setContent {
            AppTheme {
                val context = LocalContext.current
                val current = remember { Store.style(context, widgetId) }
                // The looks have room for up to four services. Start from what this widget shows, else everything connected.
                val chosen = remember {
                    mutableStateListOf<Provider>().apply {
                        val saved = Store.services(context, widgetId)
                        addAll(saved.ifEmpty { Provider.ALL.filter { it.signedIn() }.ifEmpty { listOf(Provider.CLAUDE, Provider.CODEX) } }.take(WidgetStyle.MAX_SERVICES))
                    }
                }
                val data = remember(chosen.toList()) { previewData(context, chosen.toList()) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { TopAppBar(title = { Text("Choose a look") }) },
                ) { padding ->
                    Column(
                        Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Tap the look for this widget. The dotted line is your even pace: where you would be if you used the limit evenly.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (WidgetModel.build(context, System.currentTimeMillis(), chosen.toList()).message != null) {
                            Text("Previews use sample numbers until you connect an account.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Show on this widget (up to four)", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Provider.ALL.forEach { p ->
                                FilterChip(
                                    selected = p in chosen,
                                    onClick = {
                                        if (p in chosen) { if (chosen.size > 1) chosen.remove(p) }
                                        else { if (chosen.size >= WidgetStyle.MAX_SERVICES) chosen.removeAt(0); chosen.add(p) }
                                    },
                                    label = { Text(p.title) },
                                )
                            }
                        }
                        WidgetStyle.entries.forEachIndexed { i, style ->
                            LookCard(i + 1, style, data, selected = style == current, onPick = { choose(style, chosen.toList()) })
                        }
                    }
                }
            }
        }
    }

    private fun choose(style: WidgetStyle, services: List<Provider>) {
        Store.setServices(this, widgetId, services)
        Store.setStyle(this, widgetId, style)
        UsageWidget.update(this, widgetId)
        RefreshWorker.schedule(this)
        RefreshWorker.refreshNow(this)
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
