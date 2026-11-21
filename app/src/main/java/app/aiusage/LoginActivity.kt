package app.aiusage

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.os.Message
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** The real sign-in page of the service, inside the app. We only keep the cookies it sets. */
class LoginActivity : ComponentActivity() {
    private lateinit var provider: Provider
    private lateinit var container: FrameLayout
    private lateinit var main: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        provider = Provider.byId(intent.getStringExtra(EXTRA_PROVIDER)) ?: return finish()
        Diag.init(this)
        Diag.log("login: open ${provider.id}")

        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.webViewClient = loggingClient("main") { maybeFinish(it) }
        // "Continue with Google" opens a pop-up window that reports back to the page that opened it.
        // Without this the pop-up replaces the page, the report is lost, and you get a white screen.
        web.settings.setSupportMultipleWindows(true)
        web.settings.javaScriptCanOpenWindowsAutomatically = true
        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val popup = WebView(this@LoginActivity)
                popup.settings.javaScriptEnabled = true
                popup.settings.domStorageEnabled = true
                popup.settings.setSupportMultipleWindows(true)
                popup.settings.javaScriptCanOpenWindowsAutomatically = true
                CookieManager.getInstance().setAcceptThirdPartyCookies(popup, true)
                popup.webViewClient = loggingClient("popup") { }
                popup.webChromeClient = this
                Diag.log("login: popup opened")
                container.addView(popup, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                (resultMsg.obj as WebView.WebViewTransport).webView = popup
                resultMsg.sendToTarget()
                return true
            }

            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR)
                    Diag.log("console error: ${m.message().take(160)} (${Diag.safeUrl(m.sourceId())}:${m.lineNumber()})")
                return true
            }

            override fun onCloseWindow(window: WebView) {
                Diag.log("login: popup closed; signed in=${provider.signedIn()}")
                container.removeView(window)
                window.destroy()
                maybeFinish(main.url)
            }
        }
        main = web

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 8, 24, 8)
            setBackgroundColor(Color.parseColor("#1F2A44"))
            addView(TextView(context).apply {
                text = "Sign in to ${provider.title}\nThis is the real ${provider.host}. The app never sees your password."
                setTextColor(Color.WHITE)
                textSize = 14f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(context).apply {
                text = "Done"
                setOnClickListener { finishOk() }
            })
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar)
            container = FrameLayout(context).apply { addView(web) }
            addView(container,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        // Keep clear of the status and navigation bars.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        web.loadUrl(provider.loginUrl)
    }

    private fun loggingClient(tag: String, onFinished: (String?) -> Unit) = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            Diag.log("$tag start ${Diag.safeUrl(url)}")
        }

        override fun onPageFinished(view: WebView, url: String?) {
            Diag.log("$tag done ${Diag.safeUrl(url)}; signed in=${provider.signedIn()}")
            onFinished(url)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) Diag.log("$tag error ${error.errorCode} ${error.description} ${Diag.safeUrl(request.url.toString())}")
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            if (request.isForMainFrame || response.statusCode >= 400)
                Diag.log("$tag http ${response.statusCode} ${Diag.safeUrl(request.url.toString())}")
        }
    }

    private fun maybeFinish(url: String?) {
        val uri = url?.let(android.net.Uri::parse) ?: return
        val path = uri.path.orEmpty()
        if (uri.host == provider.host && provider.signedIn() &&
            !path.startsWith("/login") && !path.startsWith("/auth") && !path.startsWith("/magic-link")
        ) finishOk()
    }

    private fun finishOk() {
        CookieManager.getInstance().flush()
        setResult(if (provider.signedIn()) Activity.RESULT_OK else Activity.RESULT_CANCELED)
        finish()
    }

    companion object {
        const val EXTRA_PROVIDER = "provider"
    }
}
