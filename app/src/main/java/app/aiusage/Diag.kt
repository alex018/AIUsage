package app.aiusage

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small log of what the app did, to find out why a sign-in or a fetch failed.
 * It never records passwords, cookies' values, page contents or query strings (those can hold
 * sign-in codes): only addresses without their query, status codes and cookie names.
 */
object Diag {
    private const val TAG = "AiUsage"
    private const val MAX_BYTES = 60_000
    private var file: File? = null
    private val clock = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    fun init(context: Context) {
        if (file == null) file = File(context.applicationContext.filesDir, "diag.log")
    }

    @Synchronized
    fun log(message: String) {
        Log.i(TAG, message)
        val f = file ?: return
        runCatching {
            if (f.length() > MAX_BYTES) f.writeText(f.readText().takeLast(MAX_BYTES / 2))
            f.appendText("${clock.format(Date())} $message\n")
        }
    }

    /** `https://host/path`, with no query or fragment. */
    fun safeUrl(url: String?): String {
        val u = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return "?"
        return "${u.scheme}://${u.host}${u.path.orEmpty()}"
    }

    /** The newest [maxLines] lines (the whole log is rarely needed). */
    @Synchronized
    fun read(maxLines: Int = 60): String =
        runCatching { file?.readText().orEmpty().lines().filter { it.isNotBlank() }.takeLast(maxLines).joinToString("\n") }
            .getOrDefault("").ifBlank { "(empty)" }

    @Synchronized
    fun clear() { runCatching { file?.writeText("") } }
}
