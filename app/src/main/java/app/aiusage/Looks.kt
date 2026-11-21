package app.aiusage

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        android.os.Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Real numbers when something is connected, made-up ones until then. */
fun previewData(context: Context, only: List<Provider> = emptyList()): WidgetData {
    val now = System.currentTimeMillis()
    val real = WidgetModel.build(context, now, only)
    return if (real.message == null && !Store.demo(context)) real
    else WidgetModel.demo(now, names = only.map { it.title.substringBefore(' ') }.map { if (it == "Claude") "Claude" else if (it == "ChatGPT") "Codex" else it })
}

@Composable
fun LookCard(number: Int, style: WidgetStyle, data: WidgetData, selected: Boolean, onPick: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val density = minOf(LocalContext.current.resources.displayMetrics.density, 2f)
    val bitmap = remember(style, data, dark) {
        WidgetDraw.render(style, data, style.refW, style.refH(data.rows.size) + 28f, density, dark).asImageBitmap()
    }
    Card(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth(),
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("$number · ${style.title}", style = MaterialTheme.typography.titleMedium)
                if (selected) Text("In use", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Image(bitmap = bitmap, contentDescription = "Preview of ${style.title}", modifier = Modifier.fillMaxWidth())
        }
    }
}
