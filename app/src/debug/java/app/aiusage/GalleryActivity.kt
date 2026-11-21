package app.aiusage

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import java.io.File

/**
 * Debug only. `am start -n app.aiusage.debug/app.aiusage.GalleryActivity --ei n 4` draws all ten looks with n
 * services into `files/gallery_<n>.png` (copy it out with `run-as`).
 */
class GalleryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val n = intent.getIntExtra("n", 3).coerceIn(1, 4)
        val dark = intent.getBooleanExtra("dark", true)
        val more = intent.getIntExtra("more", 0).toFloat() // extra height (dp), like a widget taller than it needs
        val density = 1.5f
        val data = WidgetModel.demo(System.currentTimeMillis(), count = n)
        val gap = 16f
        val cols = 2
        val cellW = 380f
        val cellH = 400f + more
        val rows = (WidgetStyle.entries.size + cols - 1) / cols
        val sheet = Bitmap.createBitmap(((cellW * cols + gap) * density).toInt(), ((cellH * rows + gap) * density).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(if (dark) Color.parseColor("#0E1014") else Color.parseColor("#C9CCD6"))
        WidgetStyle.entries.forEachIndexed { i, style ->
            val bmp = WidgetDraw.render(style, data, style.refW, style.refH(n) + 28f + more, density, dark)
            c.drawBitmap(bmp, (gap + (i % cols) * cellW) * density, (gap + (i / cols) * cellH) * density, null)
        }
        File(filesDir, "gallery_$n.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        finish()
    }
}
