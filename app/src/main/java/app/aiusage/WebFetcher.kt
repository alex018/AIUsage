package app.aiusage

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

data class FetchResult(val status: Int, val body: String)

/**
 * Runs a small piece of JavaScript inside a hidden WebView that shares the cookies of the sign-in
 * screen. claude.ai and chatgpt.com sit behind Cloudflare, which turns plain HTTP clients away
 * (403) but lets a real browser engine through.
 *
 * The script must evaluate to a promise of `{status: number, body: string}`.
 */
class WebFetcher(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun run(startUrl: String, script: String, timeoutMs: Long = 45_000): FetchResult =
        withContext(Dispatchers.Main) {
            val result = CompletableDeferred<FetchResult>()
            var lastBad: FetchResult? = null

            val web = WebView(context.applicationContext)
            try {
                web.settings.javaScriptEnabled = true
                web.settings.domStorageEnabled = true
                web.addJavascriptInterface(object {
                    @JavascriptInterface
                    fun done(json: String) {
                        val r = runCatching {
                            val o = JSONObject(json)
                            FetchResult(o.optInt("status", -1), o.optString("body"))
                        }.getOrElse { FetchResult(-1, it.message.orEmpty()) }
                        // 200 is the answer. 401 means signed out. Anything else may be Cloudflare's
                        // waiting page, which reloads into the real page, so keep waiting.
                        if (r.status == 200 || r.status == 401) result.complete(r) else lastBad = r
                    }
                }, "AiBridge")
                web.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(
                            "(function(){Promise.resolve().then(function(){return ($script)})" +
                                ".then(function(r){AiBridge.done(JSON.stringify(r))})" +
                                ".catch(function(e){AiBridge.done(JSON.stringify({status:-1,body:String(e)}))})})()",
                            null,
                        )
                    }
                }
                web.loadUrl(startUrl)
                withTimeoutOrNull(timeoutMs) { result.await() }
                    ?: lastBad
                    ?: FetchResult(-2, "timed out")
            } finally {
                web.stopLoading()
                web.destroy()
            }
        }
}
