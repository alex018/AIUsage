package app.aiusage

import android.content.Context
import android.webkit.CookieManager

class NotSignedIn : Exception("Not signed in")
class FetchFailed(message: String) : Exception(message)

/** A service we can read usage from. Added one by one; the screens and the widget just loop over [ALL]. */
enum class Provider(
    val id: String,
    val title: String,
    /** Opened by the sign-in screen. */
    val loginUrl: String,
    val host: String,
    private val cookieUrl: String,
    /** Part of the cookie name that exists only while signed in. */
    private val cookieName: String,
    /** The page the hidden browser opens first (same site, so cookies and Cloudflare's pass apply). */
    private val startUrl: String,
    private val script: String,
    private val parse: (String, Long) -> Snapshot,
) {
    CLAUDE(
        id = "claude",
        title = "Claude Code",
        loginUrl = "https://claude.ai/login",
        host = "claude.ai",
        cookieUrl = "https://claude.ai",
        cookieName = "sessionKey",
        startUrl = "https://claude.ai/api/organizations",
        script = """
            (async function(){
              var r = await fetch('/api/organizations', {credentials:'include'});
              if (r.status !== 200) return {status:r.status, body:''};
              var orgs = await r.json();
              var org = orgs.find(function(o){return (o.capabilities||[]).indexOf('chat') >= 0}) || orgs[0];
              if (!org) return {status:200, body:JSON.stringify({org:null, usage:null})};
              var u = await fetch('/api/organizations/' + org.uuid + '/usage', {credentials:'include'});
              if (u.status !== 200) return {status:u.status, body:''};
              return {status:200, body:JSON.stringify({
                org:{capabilities:org.capabilities, tier:org.rate_limit_tier}, usage:await u.json()})};
            })()
        """.trimIndent(),
        parse = Parsers::claude,
    ),
    CODEX(
        id = "codex",
        title = "ChatGPT Codex",
        loginUrl = "https://chatgpt.com/auth/login",
        host = "chatgpt.com",
        cookieUrl = "https://chatgpt.com",
        cookieName = "session-token",
        startUrl = "https://chatgpt.com/api/auth/session",
        script = """
            (async function(){
              var s = await fetch('/api/auth/session', {credentials:'include'});
              if (s.status !== 200) return {status:s.status, body:''};
              var session = await s.json();
              if (!session.accessToken) return {status:401, body:''};
              var h = {Authorization:'Bearer ' + session.accessToken};
              var acct = session.account && session.account.id;
              if (acct) h['ChatGPT-Account-Id'] = acct;
              var paths = ['/backend-api/wham/usage', '/backend-api/codex/usage'];
              var last = {status:-1, body:''};
              for (var i = 0; i < paths.length; i++) {
                var r = await fetch(paths[i], {credentials:'include', headers:h});
                if (r.status === 200) return {status:200, body:await r.text()};
                last = {status:r.status, body:''};
              }
              return last;
            })()
        """.trimIndent(),
        parse = Parsers::codex,
    ),
    GEMINI(
        id = "gemini",
        title = "Gemini",
        loginUrl = "https://gemini.google.com/app",
        host = "gemini.google.com",
        cookieUrl = "https://gemini.google.com",
        cookieName = "__Secure-1PSID",
        startUrl = "https://gemini.google.com/usage",
        // The usage page draws itself after it loads, so wait for it. Its blocks are the same ones other
        // Gemini usage tools read: data-test-id "gxu-currently" (short limit) and "gxu-weekly".
        script = """
            (async function(){
              function block(sel, cls){ var e = document.querySelector(sel) || document.querySelector(cls); return e ? e.innerText : ''; }
              for (var i = 0; i < 40; i++) {
                if (location.host.indexOf('accounts.google.com') >= 0 || /sign in|anmelden/i.test(document.title)) return {status:401, body:''};
                var cur = block('[data-test-id="gxu-currently"]', '.gxu-currently');
                var wk = block('[data-test-id="gxu-weekly"]', '.gxu-weekly');
                var page = document.body ? document.body.innerText : '';
                if (cur || wk || (i > 12 && /\d+\s*%/.test(page))) return {status:200, body:JSON.stringify({cur:cur, wk:wk, page:page.slice(0, 4000)})};
                await new Promise(function(r){ setTimeout(r, 500); });
              }
              return {status:404, body:''};
            })()
        """.trimIndent(),
        parse = Parsers::gemini,
    );

    fun signedIn(): Boolean =
        CookieManager.getInstance().getCookie(cookieUrl)?.contains(cookieName) == true

    suspend fun fetch(context: Context, nowMs: Long = System.currentTimeMillis()): Snapshot {
        if (!signedIn()) throw NotSignedIn()
        Diag.init(context)
        val r = WebFetcher(context).run(startUrl, script)
        Diag.log("fetch ${id}: status ${r.status}, ${r.body.length} bytes")
        // Gemini's page wording is not documented: keep a short copy of the numbers' text to fix the parser if needed.
        if (this == GEMINI && r.status == 200) Diag.log("gemini text: " + r.body.replace("\\n", " | ").take(500))
        return when {
            r.status == 401 -> throw NotSignedIn()
            r.status != 200 -> throw FetchFailed(
                if (r.status == -2) "Timed out. Is the phone online?" else "$title answered HTTP ${r.status}. Blocked or signed out?")
            else -> runCatching { parse(r.body, nowMs) }
                .getOrElse { throw FetchFailed("Could not read the answer (${it.javaClass.simpleName}). The service may have changed.") }
        }
    }

    /** Forget the sign-in for this service. */
    fun signOut() {
        val cm = CookieManager.getInstance()
        cm.getCookie(cookieUrl)?.split(';')?.forEach { part ->
            val name = part.substringBefore('=').trim()
            cm.setCookie(cookieUrl, "$name=; Max-Age=0; Path=/")
        }
        cm.flush()
    }

    companion object {
        val ALL = entries.toList()
        fun byId(id: String?) = ALL.firstOrNull { it.id == id }
    }
}
