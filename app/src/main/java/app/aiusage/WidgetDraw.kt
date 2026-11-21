package app.aiusage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/**
 * The ten looks. [refW] x [refH] (dp) is the size each one is designed at; it is scaled to the real widget.
 * The height depends on how many services (1 to 4) are shown: three or four use tighter layouts.
 */
enum class WidgetStyle(val title: String, val refW: Float, private val heights: (Int) -> Float) {
    BIG("Big numbers", 360f, { n -> if (n <= 2) 34f + 102f * n else 34f + 72f * n }),
    RINGS("Rings", 360f, { n -> if (n <= 2) 174f else 248f }),
    TRAFFIC("Traffic lights", 360f, { _ -> 212f }),
    BATTERIES("Batteries (shows what is left)", 360f, { n -> if (n <= 2) 44f + 98f * n else 44f + 52f * n }),
    ONE_LINE("One line", 360f, { _ -> 146f }),
    COUNTDOWN("Countdown", 360f, { n -> if (n <= 2) 150f + 124f * (n - 1) else 30f + 62f * n }),
    TILES("Colour tiles", 360f, { n -> if (n <= 2) 210f else if (n == 3) 186f else 258f }),
    GAUGES("Gauges", 360f, { n -> if (n <= 2) 34f + 92f * n - 6f else 244f }),
    COLUMNS("Columns", 360f, { _ -> 214f }),
    ONE_NUMBER("One number (small)", 210f, { _ -> 188f });

    /** Height of the design for [n] services (1 to 4). */
    fun refH(n: Int): Float = heights(n.coerceIn(1, 4))

    companion object {
        const val MAX_SERVICES = 4
        fun byName(name: String?) = entries.firstOrNull { it.name == name } ?: BIG
    }
}

data class Row(val service: String, val items: List<Item>)

enum class RefreshState { IDLE, REFRESHING, DONE }

data class WidgetData(
    val rows: List<Row>,
    /** "Updated 3 min ago", or a warning. */
    val header: String,
    /** When there is nothing to show yet: what to say instead. */
    val message: String? = null,
    /** After a tap on the refresh button: asking, then done. The button and the top text change with it. */
    val state: RefreshState = RefreshState.IDLE,
) {
    val all: List<Item> get() = rows.flatMap { it.items }
}

private class Pal(dark: Boolean) {
    val bg = if (dark) 0xFF1E1F26.toInt() else 0xFFFFFFFF.toInt()
    val text = if (dark) 0xFFF2F2F5.toInt() else 0xFF1C1B1F.toInt()
    val dim = if (dark) 0xFFA8AAB5.toInt() else 0xFF5F5D66.toInt()
    val track = if (dark) 0xFF3A3C46.toInt() else 0xFFDADCE3.toInt()
    val chip = if (dark) 0xFF2A2C35.toInt() else 0xFFECEDF2.toInt()
    val green = if (dark) 0xFF3DBE6B.toInt() else 0xFF1E8E4A.toInt()
    val amber = if (dark) 0xFFF5A524.toInt() else 0xFFB86E00.toInt()
    val red = if (dark) 0xFFF0524F.toInt() else 0xFFD32F2F.toInt()
    fun of(s: Status) = when (s) {
        Status.ON_TRACK -> green
        Status.A_BIT_FAST -> amber
        Status.TOO_FAST -> red
    }
}

object WidgetDraw {
    /** Draws the whole widget as one picture of [widthDp] x [heightDp]. */
    fun render(style: WidgetStyle, data: WidgetData, widthDp: Float, heightDp: Float, density: Float, dark: Boolean): Bitmap {
        var d = density
        val maxPixels = 1_000_000f
        if (widthDp * d * heightDp * d > maxPixels) d = Math.sqrt((maxPixels / (widthDp * heightDp)).toDouble()).toFloat()
        val bmp = Bitmap.createBitmap((widthDp * d).toInt().coerceAtLeast(1), (heightDp * d).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(d, d)
        val pal = Pal(dark)
        val p = Painter(c, pal)
        p.rrect(0f, 0f, widthDp, heightDp, 22f, pal.bg)

        // Top band: when it was updated, and a big round refresh button.
        val band = 56f
        if (data.message != null) {
            p.text(data.message, widthDp / 2, band + (heightDp - band) / 2 - 12f, 15f, pal.text, bold = true, align = Paint.Align.CENTER)
        } else {
            val avail = heightDp - band
            val refEff = style.refH(data.rows.size) - 28f // the first 28dp of each design are empty; the band replaces them
            val s = minOf(widthDp / style.refW, avail / refEff).coerceIn(0.4f, 1.6f)
            c.save()
            // Top-aligned: the content starts right under the button; spare height, if any, ends up at the bottom.
            c.translate((widthDp - style.refW * s) / 2, band - 28f * s)
            c.scale(s, s)
            // Spare height (in design units). Row and column looks spread out to use it; the others are centred in it.
            val extra = maxOf(0f, avail / s - refEff)
            val spreads = style in setOf(WidgetStyle.BIG, WidgetStyle.BATTERIES, WidgetStyle.COUNTDOWN,
                WidgetStyle.GAUGES, WidgetStyle.TILES, WidgetStyle.COLUMNS)
            if (!spreads) c.translate(0f, extra / 2)
            val dr = Designs(p, pal, style.refW, if (spreads) extra else 0f)
            when (style) {
                WidgetStyle.BIG -> dr.big(data.rows)
                WidgetStyle.RINGS -> dr.rings(data.rows)
                WidgetStyle.TRAFFIC -> dr.traffic(data.rows)
                WidgetStyle.BATTERIES -> dr.batteries(data.rows)
                WidgetStyle.ONE_LINE -> dr.oneLine(data.rows)
                WidgetStyle.COUNTDOWN -> dr.countdown(data.rows)
                WidgetStyle.TILES -> dr.tiles(data.rows)
                WidgetStyle.GAUGES -> dr.gauges(data.rows)
                WidgetStyle.COLUMNS -> dr.columns(data.rows)
                WidgetStyle.ONE_NUMBER -> dr.oneNumber(data.all)
            }
            c.restore()
        }
        val bx = widthDp - 32f
        val by = 28f
        when (data.state) {
            RefreshState.IDLE -> {
                p.text(data.header, 16f, 21f, 12f, pal.dim)
                p.circle(bx, by, 24f, pal.chip)
                p.refreshIcon(bx, by, 11f, pal.text)
            }
            RefreshState.REFRESHING -> {
                val blue = 0xFF4F8CFF.toInt()
                p.text(data.header, 16f, 20f, 14f, blue, bold = true)
                p.circle(bx, by, 24f, blue)
                p.refreshIcon(bx, by, 11f, 0xFFFFFFFF.toInt())
                p.arc(RectF(bx - 27f, by - 27f, bx + 27f, by + 27f), -70f, 240f, blue, 3f)
            }
            RefreshState.DONE -> {
                p.text(data.header, 16f, 20f, 14f, pal.green, bold = true)
                p.circle(bx, by, 24f, pal.green)
                p.polyline(floatArrayOf(bx - 10f, by + 1f, bx - 3f, by + 8f, bx + 10f, by - 7f), 0xFFFFFFFF.toInt(), 4f)
            }
        }
        return bmp
    }
}

/** Small drawing helpers. All sizes are dp: the canvas is already scaled. */
private class Painter(val c: Canvas, val pal: Pal) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotted = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(2f, 2f), 0f)
    }
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val strong = Typeface.create("sans-serif", Typeface.BOLD)

    /** [top] is the top of the line of text. */
    fun text(s: String, x: Float, top: Float, size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT): Float {
        paint.style = Paint.Style.FILL
        paint.textSize = size
        paint.color = color
        paint.typeface = if (bold) strong else regular
        paint.textAlign = align
        c.drawText(s, x, top + size * 0.92f, paint)
        return paint.measureText(s)
    }

    fun width(s: String, size: Float, bold: Boolean): Float {
        paint.textSize = size
        paint.typeface = if (bold) strong else regular
        return paint.measureText(s)
    }

    fun rrect(l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawRoundRect(RectF(l, t, r, b), rad, rad, paint)
    }

    fun circle(cx: Float, cy: Float, r: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawCircle(cx, cy, r, paint)
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, w: Float = 1f) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w
        paint.color = color
        c.drawLine(x1, y1, x2, y2, paint)
    }

    fun polyline(pts: FloatArray, color: Int, width: Float) {
        val path = Path()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = color
        c.drawPath(path, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeJoin = Paint.Join.MITER
    }

    fun dottedLine(x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        dotted.color = color
        c.drawLine(x1, y1, x2, y2, dotted)
    }

    fun arc(oval: RectF, start: Float, sweep: Float, color: Int, width: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = color
        c.drawArc(oval, start, sweep, false, paint)
        paint.strokeCap = Paint.Cap.BUTT
    }

    /** The horizontal bar, with the even-pace mark across it. */
    fun bar(x: Float, y: Float, w: Float, h: Float, used: Double, pace: Double?, color: Int, track: Int = pal.track, mark: Int = pal.text) {
        rrect(x, y, x + w, y + h, h / 2, track)
        if (used > 0) rrect(x, y, x + maxOf(h, (w * used.coerceIn(0.0, 100.0) / 100).toFloat()), y + h, h / 2, color)
        if (pace != null) {
            val mx = x + (w * pace.coerceIn(0.0, 100.0) / 100).toFloat()
            dottedLine(mx, y - 4f, mx, y + h + 4f, mark)
        }
    }

    fun refreshIcon(cx: Float, cy: Float, r: Float, color: Int) {
        val k = r / 6.5f
        arc(RectF(cx - r, cy - r, cx + r, cy + r), 50f, 280f, color, 2.4f * k.coerceAtLeast(0.8f))
        val ex = cx + r * Math.cos(Math.toRadians(50.0)).toFloat()
        val ey = cy + r * Math.sin(Math.toRadians(50.0)).toFloat()
        val head = Path().apply {
            moveTo(ex + 2f * k, ey + 4f * k)
            lineTo(ex + 1f * k, ey - 3f * k)
            lineTo(ex - 5f * k, ey + 2f * k)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawPath(head, paint)
    }
}

private class Designs(val p: Painter, val pal: Pal, val w: Float, val extra: Float = 0f) {
    private fun pct(v: Double) = "${Math.round(v)}%"
    private fun col(i: Item) = pal.of(i.status)

    /** Centre x of column [i] of [n] equal columns. */
    private fun xc(i: Int, n: Int) = w * (i + 0.5f) / n

    /** Centre x of group [i] when there are one or two groups (the older, roomier layouts). */
    private fun gc(i: Int, n: Int) = if (n == 1) w / 2 else 95f + i * 170f

    private fun short(label: String) = when {
        label.endsWith("-hour") -> label.removeSuffix("-hour") + "h"
        label == "Weekly" -> "Wk"
        else -> label.take(4)
    }

    // 1 · Big numbers
    fun big(rows: List<Row>) {
        val n = rows.size
        if (n <= 2) {
            val pitch = 102f + extra / n
            rows.forEachIndexed { i, row ->
                val top = 32f + i * pitch + (pitch - 102f) / 2
                p.text(row.service, 16f, top + 34f, 15f, pal.text, bold = true)
                row.items.take(2).forEachIndexed { j, it ->
                    val x = 72f + j * 142f
                    val c = col(it)
                    p.text(it.label, x, top, 12f, pal.dim)
                    p.text(pct(it.used), x, top + 14f, 28f, c, bold = true)
                    p.bar(x, top + 52f, 128f, 6f, it.used, it.pace, c)
                    p.text(it.status.word, x, top + 64f, 12f, c, bold = true)
                    it.resetIn?.let { r -> p.text("resets in $r", x, top + 78f, 11f, pal.dim) }
                }
            }
        } else {
            // Three or four services: tighter rows, status and reset on one line.
            val pitch = 72f + extra / n
            rows.forEachIndexed { i, row ->
                val top = 32f + i * pitch + (pitch - 72f) / 2
                p.text(row.service, 12f, top + 23f, 13f, pal.text, bold = true)
                row.items.take(2).forEachIndexed { j, it ->
                    val x = 82f + j * 140f
                    val c = col(it)
                    p.text(it.label, x, top, 11f, pal.dim)
                    p.text(pct(it.used), x, top + 11f, 22f, c, bold = true)
                    p.bar(x, top + 40f, 124f, 5f, it.used, it.pace, c)
                    val sw = p.text(it.status.word, x, top + 50f, 11f, c, bold = true)
                    it.resetIn?.let { r -> p.text(" · $r", x + sw, top + 50f, 11f, pal.dim) }
                }
                if (i < n - 1) p.line(14f, 32f + (i + 1) * pitch - 5f, w - 14f, 32f + (i + 1) * pitch - 5f, pal.track)
            }
        }
    }

    // 2 · Rings
    fun rings(rows: List<Row>) {
        val n = rows.size
        if (n <= 2) {
            rows.forEachIndexed { gi, row ->
                val g = gc(gi, n)
                p.text(row.service, g, 30f, 15f, pal.text, bold = true, align = Paint.Align.CENTER)
                row.items.take(2).forEachIndexed { j, it ->
                    val cx = g + if (j == 0) -40f else 40f
                    val cy = 88f
                    val c = col(it)
                    val oval = RectF(cx - 28f, cy - 28f, cx + 28f, cy + 28f)
                    p.arc(oval, 0f, 360f, pal.track, 8f)
                    if (it.used > 0) p.arc(oval, -90f, (360 * it.used.coerceIn(0.0, 100.0) / 100).toFloat(), c, 8f)
                    it.pace?.let { pace ->
                        val a = Math.toRadians(-90.0 + 360 * pace.coerceIn(0.0, 100.0) / 100)
                        val ca = Math.cos(a).toFloat(); val sa = Math.sin(a).toFloat()
                        p.dottedLine(cx + ca * 21f, cy + sa * 21f, cx + ca * 36f, cy + sa * 36f, pal.text)
                    }
                    p.text(Math.round(it.used).toString(), cx, cy - 10f, 18f, pal.text, bold = true, align = Paint.Align.CENTER)
                    p.text(it.label, cx, 124f, 11f, pal.dim, align = Paint.Align.CENTER)
                    p.text(it.status.word, cx, 138f, 12f, c, bold = true, align = Paint.Align.CENTER)
                    it.resetIn?.let { r -> p.text(r, cx, 152f, 11f, pal.dim, align = Paint.Align.CENTER) }
                }
                if (gi < n - 1) p.line(w / 2, 36f, w / 2, 164f, pal.track)
            }
        } else {
            // Three or four services: one column each, the two rings stacked.
            rows.forEachIndexed { i, row ->
                val cx = xc(i, n)
                p.text(row.service, cx, 30f, 14f, pal.text, bold = true, align = Paint.Align.CENTER)
                row.items.take(2).forEachIndexed { j, it ->
                    val cy = 74f + j * 104f
                    val c = col(it)
                    val oval = RectF(cx - 22f, cy - 22f, cx + 22f, cy + 22f)
                    p.arc(oval, 0f, 360f, pal.track, 7f)
                    if (it.used > 0) p.arc(oval, -90f, (360 * it.used.coerceIn(0.0, 100.0) / 100).toFloat(), c, 7f)
                    it.pace?.let { pace ->
                        val a = Math.toRadians(-90.0 + 360 * pace.coerceIn(0.0, 100.0) / 100)
                        val ca = Math.cos(a).toFloat(); val sa = Math.sin(a).toFloat()
                        p.dottedLine(cx + ca * 16f, cy + sa * 16f, cx + ca * 29f, cy + sa * 29f, pal.text)
                    }
                    p.text(Math.round(it.used).toString(), cx, cy - 8f, 15f, pal.text, bold = true, align = Paint.Align.CENTER)
                    p.text(it.label + (it.resetIn?.let { r -> " · $r" } ?: ""), cx, cy + 30f, 11f, pal.dim, align = Paint.Align.CENTER)
                    p.text(it.status.word, cx, cy + 43f, 12f, c, bold = true, align = Paint.Align.CENTER)
                }
                if (i < n - 1) p.line(w * (i + 1) / n, 36f, w * (i + 1) / n, 234f, pal.track)
            }
        }
    }

    // 3 · Traffic lights
    fun traffic(rows: List<Row>) {
        val n = rows.size
        val dense = n >= 3
        val r = if (dense) 29f else 35f
        rows.forEachIndexed { i, row ->
            val it = Pace.tightest(row.items) ?: return@forEachIndexed
            val cx = if (n <= 2) gc(i, n) else xc(i, n)
            val c = col(it)
            val cy = if (dense) 84f else 90f
            p.text(row.service, cx, 30f, if (dense) 14f else 15f, pal.text, bold = true, align = Paint.Align.CENTER)
            p.circle(cx, cy, r, c)
            p.text(pct(it.used), cx, cy - (if (dense) 12f else 14f), if (dense) 20f else 24f, 0xFF1A1300.toInt(), bold = true, align = Paint.Align.CENTER)
            val wordTop = if (dense) 120f else 132f
            p.text(it.status.word, cx, wordTop, if (dense) 15f else 17f, c, bold = true, align = Paint.Align.CENTER)
            val you = when {
                it.pace == null -> "You ${pct(it.used)}"
                dense -> "${pct(it.used)} · pace ${pct(it.pace)}"
                else -> "You ${pct(it.used)} · even pace ${pct(it.pace)}"
            }
            p.text(you, cx, wordTop + (if (dense) 21f else 24f), if (dense) 11f else 12f, pal.text, bold = true, align = Paint.Align.CENTER)
            p.text(it.label + (it.resetIn?.let { r2 -> if (dense) " · $r2" else " · resets in $r2" } ?: ""), cx,
                wordTop + (if (dense) 37f else 40f), 11f, pal.dim, align = Paint.Align.CENTER)
            it.runsOutIn?.let { r2 -> p.text(if (dense) "Out in ~$r2" else "Runs out in ~$r2", cx, wordTop + (if (dense) 51f else 55f), 11f, pal.dim, align = Paint.Align.CENTER) }
            if (i < n - 1) p.line(w * (i + 1) / n, 36f, w * (i + 1) / n, 190f, pal.track)
        }
    }

    // 4 · Batteries (what is left)
    private fun battery(x: Float, y: Float, bw: Float, bh: Float, left: Double, paceLeft: Double?, color: Int) {
        val inner = bw - 12f
        p.rrect(x, y, x + bw, y + bh, 8f, 0xFF5A5D6A.toInt())
        p.rrect(x + 3f, y + 3f, x + bw - 3f, y + bh - 3f, 5f, pal.bg)
        if (left > 0) p.rrect(x + 6f, y + 6f, x + 6f + (inner * left / 100).toFloat().coerceAtLeast(6f), y + bh - 6f, 4f, color)
        paceLeft?.let { pl ->
            val mx = x + 6f + (inner * pl.coerceIn(0.0, 100.0) / 100).toFloat()
            p.dottedLine(mx, y - 2f, mx, y + bh + 2f, pal.text)
        }
        p.rrect(x + bw, y + bh / 2 - 7f, x + bw + 5f, y + bh / 2 + 7f, 2f, 0xFF5A5D6A.toInt())
    }

    fun batteries(rows: List<Row>) {
        val n = rows.size
        if (n <= 2) {
            rows.firstOrNull()?.items?.take(2)?.forEachIndexed { j, it -> p.text(it.label, 76f + j * 140f, 30f, 12f, pal.dim) }
            val pitch = 98f + extra / n
            rows.forEachIndexed { i, row ->
                val top = 50f + i * pitch + (pitch - 98f) / 2
                p.text(row.service, 16f, top + 10f, 15f, pal.text, bold = true)
                row.items.take(2).forEachIndexed { j, it ->
                    val x = 76f + j * 140f
                    val left = 100 - it.used.coerceIn(0.0, 100.0)
                    battery(x, top, 84f, 36f, left, it.pace?.let { pc -> 100 - pc }, col(it))
                    p.text("${Math.round(left)}% left", x, top + 42f, 14f, pal.text, bold = true)
                    p.text(it.status.word, x, top + 58f, 12f, col(it), bold = true)
                    it.resetIn?.let { r -> p.text("resets in $r", x, top + 73f, 11f, pal.dim) }
                }
            }
        } else {
            rows.firstOrNull()?.items?.take(2)?.forEachIndexed { j, it -> p.text(it.label, 70f + j * 146f, 24f, 11f, pal.dim) }
            val pitch = 52f + extra / n
            rows.forEachIndexed { i, row ->
                val top = 44f + i * pitch + (pitch - 52f) / 2
                p.text(row.service, 10f, top + 9f, 12f, pal.text, bold = true)
                row.items.take(2).forEachIndexed { j, it ->
                    val x = 70f + j * 146f
                    val left = 100 - it.used.coerceIn(0.0, 100.0)
                    battery(x, top + 4f, 56f, 28f, left, it.pace?.let { pc -> 100 - pc }, col(it))
                    p.text("${Math.round(left)}% left", x + 66f, top - 2f, 12f, pal.text, bold = true)
                    p.text(it.status.word, x + 66f, top + 12f, 11f, col(it), bold = true)
                    it.resetIn?.let { r -> p.text(r, x + 66f, top + 25f, 10f, pal.dim) }
                }
            }
        }
    }

    // 5 · One line. Each limit is one narrow column; everything in it starts at the bar's left edge.
    fun oneLine(rows: List<Row>) {
        val n = rows.size
        if (n <= 2) {
            rows.forEachIndexed { i, row ->
                val gx = if (n == 1) (w - 150f) / 2 else 18f + i * 180f
                p.text(row.service, gx, 30f, 13f, pal.text, bold = true)
                row.items.take(2).forEachIndexed { j, it ->
                    val x = gx + j * 80f
                    val c = col(it)
                    p.text(pct(it.used), x, 48f, 24f, c, bold = true)
                    p.bar(x, 82f, 70f, 5f, it.used, it.pace, c)
                    p.text(it.label, x, 94f, 11f, pal.dim)
                    p.text(it.status.word, x, 108f, 12f, c, bold = true)
                    p.text(it.resetIn ?: "", x, 124f, 12f, pal.text)
                }
                if (n == 2 && i == 0) p.line(w / 2, 36f, w / 2, 140f, pal.track)
            }
        } else {
            val cw = w / n
            rows.forEachIndexed { i, row ->
                val gx = cw * i + 8f
                val bw = (cw - 16f - 6f) / 2
                p.text(row.service, gx, 30f, 13f, pal.text, bold = true)
                row.items.take(2).forEachIndexed { j, it ->
                    val x = gx + j * (bw + 6f)
                    val c = col(it)
                    p.text(pct(it.used), x, 48f, if (n == 4) 17f else 19f, c, bold = true)
                    p.bar(x, 78f, bw, 5f, it.used, it.pace, c)
                    p.text(short(it.label), x, 90f, 11f, pal.dim)
                    p.text(it.resetIn ?: "", x, 118f, if (n == 4) 10f else 11f, pal.text)
                }
                Pace.tightest(row.items)?.let { t -> p.text(t.status.word, gx, 104f, 11f, col(t), bold = true) }
                if (i < n - 1) p.line(cw * (i + 1), 36f, cw * (i + 1), 134f, pal.track)
            }
        }
    }

    // 6 · Countdown
    fun countdown(rows: List<Row>) {
        val n = rows.size
        if (n <= 2) {
            val pitch = 124f + extra / n
            rows.forEachIndexed { i, row ->
                val it = Pace.tightest(row.items) ?: return@forEachIndexed
                val top = 32f + i * pitch + (pitch - 124f) / 2
                val c = col(it)
                p.text("${row.service} · ${it.label}", 18f, top, 15f, pal.text, bold = true)
                p.text("Resets in", 18f, top + 18f, 12f, pal.dim)
                p.text(it.resetIn ?: "?", 18f, top + 30f, 26f, pal.text, bold = true)
                p.rrect(w - 90f, top + 6f, w - 18f, top + 56f, 14f, pal.chip)
                p.text(pct(it.used), w - 54f, top + 10f, 22f, c, bold = true, align = Paint.Align.CENTER)
                p.text("used", w - 54f, top + 36f, 11f, pal.dim, align = Paint.Align.CENTER)
                p.bar(18f, top + 68f, w - 36f, 7f, it.used, it.pace, c)
                val sw = p.text(it.status.word + ".", 18f, top + 82f, 12f, c, bold = true)
                if (it.pace != null) p.text(" Even pace is ${pct(it.pace)}.", 18f + sw, top + 82f, 12f, pal.text)
                p.text(if (it.runsOutIn != null) "At this speed it runs out in ~${it.runsOutIn}." else "Should last until the reset.",
                    18f, top + 98f, 12f, pal.dim)
                if (n == 2 && i == 0) p.line(18f, 32f + pitch - 2f, w - 18f, 32f + pitch - 2f, pal.track)
            }
        } else {
            val pitch = 62f + extra / n
            rows.forEachIndexed { i, row ->
                val it = Pace.tightest(row.items) ?: return@forEachIndexed
                val top = 32f + i * pitch + (pitch - 62f) / 2
                val c = col(it)
                p.text("${row.service} · ${it.label}", 16f, top, 13f, pal.text, bold = true)
                p.text(pct(it.used), w - 16f, top - 2f, 18f, c, bold = true, align = Paint.Align.RIGHT)
                p.bar(16f, top + 24f, w - 32f, 6f, it.used, it.pace, c)
                val sw = p.text(it.status.word, 16f, top + 35f, 12f, c, bold = true)
                p.text(" · resets in ${it.resetIn ?: "?"}", 16f + sw, top + 35f, 12f, pal.dim)
                if (i < n - 1) p.line(16f, 32f + (i + 1) * pitch - 8f, w - 16f, 32f + (i + 1) * pitch - 8f, pal.track)
            }
        }
    }

    // 7 · Colour tiles
    private fun inkOf(s: Status) = when (s) {
        Status.ON_TRACK -> 0xFF04210F.toInt()
        Status.A_BIT_FAST -> 0xFF1A1300.toInt()
        Status.TOO_FAST -> 0xFF1F0505.toInt()
    }

    private fun fillOf(s: Status) = when (s) {
        Status.ON_TRACK -> 0xFF3DBE6B.toInt()
        Status.A_BIT_FAST -> 0xFFF5A524.toInt()
        Status.TOO_FAST -> 0xFFF0524F.toInt()
    }

    fun tiles(rows: List<Row>) {
        val n = rows.size
        when {
            n <= 2 -> {
                val tw = (w - 36f - 10f) / 2
                rows.forEachIndexed { i, row ->
                    val it = Pace.tightest(row.items) ?: return@forEachIndexed
                    val other = row.items.firstOrNull { o -> o !== it }
                    val x = if (n == 1) (w - tw) / 2 else 18f + i * (tw + 10f)
                    val y = 32f + extra / 2
                    val ink = inkOf(it.status)
                    p.rrect(x, 32f, x + tw, 32f + 170f + extra, 20f, fillOf(it.status))
                    p.text(row.service, x + 14f, y + 12f, 15f, ink, bold = true)
                    p.text(pct(it.used), x + 14f, y + 28f, 44f, ink, bold = true)
                    p.text("${it.label} · ${it.status.word}", x + 14f, y + 78f, 13f, ink, bold = true)
                    p.bar(x + 14f, y + 102f, tw - 28f, 8f, it.used, it.pace, ink, track = (ink and 0x00FFFFFF) or 0x38000000, mark = ink)
                    it.pace?.let { pace -> p.text("Even pace: ${pct(pace)}", x + 14f, y + 120f, 12f, ink, bold = true) }
                    it.resetIn?.let { r -> p.text("Resets in $r", x + 14f, y + 136f, 12f, ink) }
                    other?.let { o -> p.text("${o.label}: ${pct(o.used)}" + (o.resetIn?.let { r -> " · $r" } ?: ""), x + 14f, y + 152f, 12f, ink) }
                }
            }
            n == 3 -> {
                val tw = (w - 36f - 16f) / 3
                rows.forEachIndexed { i, row ->
                    val it = Pace.tightest(row.items) ?: return@forEachIndexed
                    val x = 18f + i * (tw + 8f)
                    val y = 32f + extra / 2
                    val ink = inkOf(it.status)
                    p.rrect(x, 32f, x + tw, 32f + 142f + extra, 16f, fillOf(it.status))
                    p.text(row.service, x + 10f, y + 9f, 13f, ink, bold = true)
                    p.text(pct(it.used), x + 10f, y + 25f, 30f, ink, bold = true)
                    p.text(it.label, x + 10f, y + 62f, 11f, ink)
                    p.text(it.status.word, x + 10f, y + 76f, 12f, ink, bold = true)
                    p.bar(x + 10f, y + 100f, tw - 20f, 7f, it.used, it.pace, ink, track = (ink and 0x00FFFFFF) or 0x38000000, mark = ink)
                    it.resetIn?.let { r -> p.text("Resets in $r", x + 10f, y + 116f, 11f, ink) }
                }
            }
            else -> {
                val tw = (w - 36f - 8f) / 2
                rows.take(4).forEachIndexed { i, row ->
                    val it = Pace.tightest(row.items) ?: return@forEachIndexed
                    val x = 18f + (i % 2) * (tw + 8f)
                    val tileY = 32f + (i / 2) * (108f + extra / 2)
                    val y = tileY + extra / 4
                    val ink = inkOf(it.status)
                    p.rrect(x, tileY, x + tw, tileY + 100f + extra / 2, 16f, fillOf(it.status))
                    p.text(row.service, x + 12f, y + 8f, 13f, ink, bold = true)
                    p.text(pct(it.used), x + 12f, y + 24f, 30f, ink, bold = true)
                    p.text(it.label, x + tw - 12f, y + 30f, 11f, ink, align = Paint.Align.RIGHT)
                    p.text(it.status.word, x + tw - 12f, y + 44f, 12f, ink, bold = true, align = Paint.Align.RIGHT)
                    p.bar(x + 12f, y + 70f, tw - 24f, 7f, it.used, it.pace, ink, track = (ink and 0x00FFFFFF) or 0x38000000, mark = ink)
                    it.resetIn?.let { r -> p.text("Resets in $r", x + 12f, y + 82f, 11f, ink) }
                }
            }
        }
    }

    // 8 · Gauges
    private fun gauge(cx: Float, cy: Float, r: Float, sw: Float, it: Item) {
        val c = col(it)
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        p.arc(oval, 180f, 180f, pal.track, sw)
        if (it.used > 0) p.arc(oval, 180f, (180 * it.used.coerceIn(0.0, 100.0) / 100).toFloat(), c, sw)
        it.pace?.let { pace ->
            val a = Math.toRadians(180.0 + 180 * pace.coerceIn(0.0, 100.0) / 100)
            val ca = Math.cos(a).toFloat(); val sa = Math.sin(a).toFloat()
            p.dottedLine(cx + ca * (r - sw / 2 - 3f), cy + sa * (r - sw / 2 - 3f), cx + ca * (r + sw / 2 + 3f), cy + sa * (r + sw / 2 + 3f), pal.text)
        }
    }

    fun gauges(rows: List<Row>) {
        val n = rows.size
        if (n <= 2) {
            val pitch = 92f + extra / n
            rows.forEachIndexed { i, row ->
                val top = 34f + i * pitch + (pitch - 92f) / 2
                p.text(row.service, 16f, top + 28f, 15f, pal.text, bold = true)
                row.items.take(2).forEachIndexed { j, it ->
                    val cx = 140f + j * 120f
                    val cy = top + 46f
                    gauge(cx, cy, 40f, 10f, it)
                    p.text(pct(it.used), cx, cy - 22f, 22f, pal.text, bold = true, align = Paint.Align.CENTER)
                    p.text(it.label, cx, cy + 6f, 11f, pal.dim, align = Paint.Align.CENTER)
                    val sw = p.width(it.status.word, 12f, true)
                    val tail = it.resetIn?.let { r -> " · $r" } ?: ""
                    val tw = sw + p.width(tail, 12f, false)
                    p.text(it.status.word, cx - tw / 2, cy + 20f, 12f, col(it), bold = true)
                    p.text(tail, cx - tw / 2 + sw, cy + 20f, 12f, pal.dim)
                }
                if (n == 2 && i == 0) p.line(16f, 34f + pitch - 4f, w - 16f, 34f + pitch - 4f, pal.track)
            }
        } else {
            // Three or four services: a 2 x 2 grid, a service in each cell with its two small gauges.
            val cell = 104f + extra / 2
            rows.take(4).forEachIndexed { k, row ->
                val x0 = (k % 2) * 180f
                val y0 = 30f + (k / 2) * cell + (cell - 104f) / 2
                p.text(row.service, x0 + 90f, y0, 13f, pal.text, bold = true, align = Paint.Align.CENTER)
                row.items.take(2).forEachIndexed { j, it ->
                    val cx = x0 + 50f + j * 80f
                    val cy = y0 + 52f
                    gauge(cx, cy, 28f, 8f, it)
                    p.text(pct(it.used), cx, cy - 17f, 15f, pal.text, bold = true, align = Paint.Align.CENTER)
                    p.text(it.label, cx, cy + 4f, 10f, pal.dim, align = Paint.Align.CENTER)
                    p.text(it.status.word, cx, cy + 16f, 11f, col(it), bold = true, align = Paint.Align.CENTER)
                    it.resetIn?.let { r -> p.text(r, cx, cy + 29f, 10f, pal.dim, align = Paint.Align.CENTER) }
                }
            }
            if (n >= 3) p.line(w / 2, 40f, w / 2, 30f + 2 * cell - 6f, pal.track)
        }
    }

    // 9 · Columns
    private fun column(cx: Float, tw: Float, it: Item, name: String, showLabel: Boolean = true) {
        val c = col(it)
        val th = 90f + extra // the track grows with the spare height
        val bottom = 56f + th
        p.text(pct(it.used), cx, 30f, 20f, c, bold = true, align = Paint.Align.CENTER)
        p.rrect(cx - tw / 2, 56f, cx + tw / 2, bottom, 14f, pal.track)
        val fh = (th * it.used.coerceIn(0.0, 100.0) / 100).toFloat()
        if (fh > 0) p.rrect(cx - tw / 2, bottom - maxOf(fh, 14f), cx + tw / 2, bottom, 14f, c)
        it.pace?.let { pace ->
            val y = bottom - (th * pace.coerceIn(0.0, 100.0) / 100).toFloat()
            p.dottedLine(cx - tw / 2 - 5f, y, cx + tw / 2 + 5f, y, pal.text)
        }
        p.text(name, cx, bottom + 6f, 13f, pal.text, bold = true, align = Paint.Align.CENTER)
        if (showLabel) p.text(it.label, cx, bottom + 22f, 11f, pal.dim, align = Paint.Align.CENTER)
        p.text(it.status.word, cx, bottom + 36f, 12f, c, bold = true, align = Paint.Align.CENTER)
        it.resetIn?.let { r -> p.text(r, cx, bottom + 50f, 11f, pal.dim, align = Paint.Align.CENTER) }
    }

    fun columns(rows: List<Row>) {
        val n = rows.size
        if (n <= 2) {
            rows.forEachIndexed { gi, row ->
                val base = if (n == 1) 143f else (if (gi == 0) 52f else 234f)
                row.items.take(2).forEachIndexed { j, it -> column(base + j * 74f, 44f, it, row.service) }
            }
            if (n == 2) p.line(w / 2, 40f, w / 2, 205f + extra, pal.track)
        } else {
            // Three or four services: one column each, for the limit that worries most.
            rows.forEachIndexed { i, row ->
                val it = Pace.tightest(row.items) ?: return@forEachIndexed
                column(xc(i, n), if (n == 3) 44f else 40f, it, row.service)
            }
        }
    }

    // 10 · One number
    fun oneNumber(all: List<Item>) {
        val it = Pace.tightest(all) ?: return
        val c = col(it)
        p.text("Biggest worry right now", 18f, 30f, 12f, pal.dim)
        p.text(pct(it.used), 18f, 44f, 52f, c, bold = true)
        p.text("${it.service} · ${it.label}", 18f, 100f, 15f, pal.text, bold = true)
        p.bar(18f, 128f, w - 36f, 8f, it.used, it.pace, c)
        val head = it.status.word + (it.pace?.let { pace -> " · even pace is ${pct(pace)}" } ?: "")
        p.text(head, 18f, 144f, 12f, c, bold = true)
        val tail = when {
            it.runsOutIn != null && it.resetIn != null -> "Runs out in ~${it.runsOutIn} · resets ${it.resetIn}"
            it.resetIn != null -> "Resets in ${it.resetIn}"
            else -> ""
        }
        p.text(tail, 18f, 162f, 11f, pal.dim)
    }
}
