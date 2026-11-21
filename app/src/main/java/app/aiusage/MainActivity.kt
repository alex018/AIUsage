package app.aiusage

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Diag.init(this)
        RefreshWorker.schedule(this)
        setContent { Screen(onRefresh = ::refresh) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch { Repo.refreshAll(applicationContext) }
    }
}

private fun statusColor(s: Status, dark: Boolean): Color = Color(
    when (s) {
        Status.ON_TRACK -> if (dark) 0xFF3DBE6B else 0xFF1E8E4A
        Status.A_BIT_FAST -> if (dark) 0xFFF5A524 else 0xFFB86E00
        Status.TOO_FAST -> if (dark) 0xFFF0524F else 0xFFD32F2F
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(onRefresh: () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    AppTheme {
        val tick by Store.changes.collectAsState()
        val refreshing by Repo.refreshing.collectAsState()
        val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onRefresh() }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(title = { Text("AI Usage") }, actions = {
                    if (refreshing) CircularProgressIndicator(Modifier.size(24.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    else TextButton(onClick = onRefresh) { Text("Refresh") }
                })
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (p in Provider.ALL) {
                    // `tick` makes this re-read the store whenever it changes.
                    val signedIn = remember(tick) { p.signedIn() }
                    val snap = remember(tick) { Store.snapshot(context, p) }
                    val error = remember(tick) { Store.error(context, p) }
                    ProviderCard(
                        provider = p, signedIn = signedIn, snapshot = snap, error = error, dark = dark,
                        onConnect = { login.launch(Intent(context, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_PROVIDER, p.id)) },
                        onDisconnect = { p.signOut(); Store.clear(context, p); UsageWidget.updateAll(context) },
                    )
                }

                OutlinedButton(onClick = { pinWidget(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add widget to home screen")
                }

                Text("Your widgets", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                val ids = remember(tick) { UsageWidget.ids(context).toList() }
                if (ids.isEmpty()) {
                    Text("None on your home screen yet. Add one with the button above, then choose its look. " +
                        "Each widget has its own look, so you can add several.", style = MaterialTheme.typography.bodyMedium)
                }
                ids.forEachIndexed { i, id ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Widget ${i + 1}", style = MaterialTheme.typography.titleMedium)
                                val shown = Store.services(context, id).ifEmpty { Provider.ALL.filter { it.signedIn() }.take(WidgetStyle.MAX_SERVICES) }
                                Text(Store.style(context, id).title + (if (shown.isNotEmpty()) " · " + shown.joinToString(", ") { it.title.substringBefore(' ') } else ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                context.startActivity(Intent(context, WidgetConfigActivity::class.java)
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                            }) { Text("Change look") }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("AI Usage log", Diag.read()))
                        Toast.makeText(context, "Copied the last 60 lines", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy log (last 60 lines)") }
                    TextButton(onClick = {
                        Diag.clear()
                        Toast.makeText(context, "Log cleared", Toast.LENGTH_SHORT).show()
                    }) { Text("Clear log") }
                }
                Text(
                    "Privacy: this app talks only to claude.ai, chatgpt.com and gemini.google.com (and Google's sign-in for Gemini), and to nothing else (no server of ours, no ads, no tracking). " +
                        "You sign in on the real sites; the app never sees your password and keeps only the sign-in cookie on this phone. " +
                        "Disconnect deletes it. Unofficial: it reads the same pages you see in the browser, which can change at any time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
                }
                Text("Version $version", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: Provider, signedIn: Boolean, snapshot: Snapshot?, error: String?, dark: Boolean,
    onConnect: () -> Unit, onDisconnect: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(provider.title + (snapshot?.plan?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
                if (signedIn) TextButton(onClick = onDisconnect) { Text("Disconnect") }
            }
            if (!signedIn) {
                Button(onClick = onConnect) { Text("Connect") }
            } else if (snapshot == null) {
                Text(error ?: "Waiting for the first numbers...", style = MaterialTheme.typography.bodyMedium)
            } else {
                val now = System.currentTimeMillis()
                if (snapshot.meters.isEmpty()) {
                    Text("No limits reported for this plan.", style = MaterialTheme.typography.bodyMedium)
                }
                snapshot.meters.forEach { m ->
                    val item = Pace.item(provider.title, m, now)
                    val color = statusColor(item.status, dark)
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(m.label, style = MaterialTheme.typography.bodyMedium)
                            Text(Format.percentText(m.percent) + " · " + item.status.word,
                                style = MaterialTheme.typography.bodyMedium, color = color)
                        }
                        LinearProgressIndicator(
                            progress = { (m.percent / 100.0).coerceIn(0.0, 1.0).toFloat() },
                            color = color,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                        Text(
                            listOfNotNull(
                                item.pace?.let { "Even pace: ${Format.percentText(it)}" },
                                item.resetIn?.let { "resets in $it" },
                                item.runsOutIn?.let { "runs out in ~$it at this speed" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                snapshot.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("Updated ${Format.ago(snapshot.fetchedAtMs, now)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun pinWidget(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    if (manager.isRequestPinAppWidgetSupported) {
        // When the launcher has placed the widget, the system fills in its id and opens the look screen for it.
        val chooseLook = android.app.PendingIntent.getActivity(
            context, 2, Intent(context, WidgetConfigActivity::class.java),
            android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.requestPinAppWidget(ComponentName(context, UsageWidget::class.java), null, chooseLook)
    }
}
