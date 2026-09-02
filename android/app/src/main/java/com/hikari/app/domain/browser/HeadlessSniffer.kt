package com.hikari.app.domain.browser

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.JsPromptResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener

/** Ergebnis eines unsichtbaren Seitenbesuchs: der Stream plus Seiten-Metadaten. */
data class HeadlessResult(
    val pageUrl: String,
    val title: String?,
    val description: String?,
    val finding: MediaFinding,
)

/**
 * Besucht eine Seite mit eingebettetem Player unsichtbar und liest den Stream
 * mit — dieselbe Technik wie der In-App-Browser, nur ohne Bildschirm.
 *
 * Damit reicht es, einen Link zu teilen: Wenn yt-dlp mit der Seite nichts
 * anfangen kann (unbekannter Hoster, verschlüsselter Player), lassen wir die
 * Seite im Hintergrund ihren Player starten und fangen die Medien-URL samt
 * Request-Headern ab. Der Nutzer muss den Browser dafür nicht mehr öffnen.
 *
 * Die WebView ist nie am Fenster — sie bekommt aber ein festes Layout,
 * weil manche Player bei 0×0 gar nicht erst initialisieren.
 */
@Singleton
class HeadlessSniffer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Nur eine unsichtbare WebView gleichzeitig — jede kostet echten Speicher. */
    private val lock = Mutex()

    /**
     * Liefert den besten Stream der Seite oder null, wenn innerhalb von
     * [timeoutMs] keiner auftauchte (Captcha, toter Hoster, DRM).
     */
    suspend fun sniff(pageUrl: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): HeadlessResult? =
        lock.withLock {
            withContext(Dispatchers.Main.immediate) {
                val session = Session(pageUrl)
                try {
                    withTimeoutOrNull(timeoutMs) { session.run() }
                } finally {
                    session.destroy()
                }
            }
        }

    private inner class Session(private val pageUrl: String) {
        private val sniffer = MediaSniffer()
        @Volatile private var pageFinished = false
        @Volatile private var scan: Scan? = null

        private val webView: WebView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            // Ohne das startet kein Player von selbst.
            settings.mediaPlaybackRequiresUserGesture = false
            // Popup-Werbung darf keine Fenster öffnen — es gibt ohnehin
            // niemanden, der sie sähe, und ein Fensterwunsch ohne Activity
            // würde die WebView zum Absturz bringen.
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.userAgentString = settings.userAgentString
                ?.replace(" wv", "")
                ?.replace("Version/4.0 ", "")
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            // Festes Layout, obwohl nie sichtbar: Player prüfen ihre Größe.
            measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, WIDTH, HEIGHT)

            webChromeClient = object : WebChromeClient() {
                // Dialoge stumm wegdrücken — ohne Activity gäbe es sonst einen Crash.
                override fun onJsAlert(v: WebView?, u: String?, m: String?, r: JsResult?): Boolean {
                    r?.cancel(); return true
                }
                override fun onJsConfirm(v: WebView?, u: String?, m: String?, r: JsResult?): Boolean {
                    r?.cancel(); return true
                }
                override fun onJsPrompt(v: WebView?, u: String?, m: String?, d: String?, r: JsPromptResult?): Boolean {
                    r?.cancel(); return true
                }
                override fun onJsBeforeUnload(v: WebView?, u: String?, m: String?, r: JsResult?): Boolean {
                    r?.cancel(); return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    // Im Hintergrund gibt es keine Nutzer-Geste: Ad-Hosts
                    // werden ausnahmslos blockiert, auch im Hauptframe.
                    if (AdHosts.isAdUrl(url)) return emptyResponse()
                    val headers = HashMap(request.requestHeaders ?: emptyMap())
                    if (headers.keys.none { it.equals("Cookie", ignoreCase = true) }) {
                        CookieManager.getInstance().getCookie(url)
                            ?.takeIf { it.isNotBlank() }
                            ?.let { headers["Cookie"] = it }
                    }
                    sniffer.onRequest(url, headers)
                    return null
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    pageFinished = false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    pageFinished = true
                    view?.evaluateJavascript(PageScripts.AUTOPLAY, null)
                    view?.evaluateJavascript(PageScripts.SCAN) { raw ->
                        parseScan(raw)?.let { s ->
                            for (v in s.videos) sniffer.onRequest(v, emptyMap())
                            scan = s
                        }
                    }
                }
            }
        }

        suspend fun run(): HeadlessResult? {
            webView.loadUrl(pageUrl)
            var firstHit = -1L
            var lastPoke = 0L
            val start = System.currentTimeMillis()
            while (true) {
                delay(POLL_MS)
                val now = System.currentTimeMillis()
                val best = sniffer.best()
                if (best != null) {
                    if (firstHit < 0) firstHit = now
                    // Kurz nachwarten: Nach der ersten Datei taucht oft noch
                    // die HLS-Playlist auf, die alle Qualitäten enthält.
                    if (best.kind == MediaKind.HLS || now - firstHit >= GRACE_MS) {
                        return HeadlessResult(
                            pageUrl = pageUrl,
                            title = PageTitleFilter.clean(scan?.title ?: webView.title),
                            description = scan?.description,
                            finding = best,
                        )
                    }
                }
                // Den Player alle paar Sekunden erneut anstoßen: Der erste
                // Klick öffnet bei vielen Hostern nur ein Werbe-Overlay,
                // erst der zweite startet das Video.
                if (pageFinished && now - lastPoke >= POKE_MS) {
                    lastPoke = now
                    webView.evaluateJavascript(PageScripts.AUTOPLAY, null)
                    // Nachgeladene <video src> und Titel nach Bot-Prüfung.
                    if (now - start >= POKE_MS) {
                        webView.evaluateJavascript(PageScripts.SCAN) { raw ->
                            parseScan(raw)?.let { s ->
                                for (v in s.videos) sniffer.onRequest(v, emptyMap())
                                scan = s
                            }
                        }
                    }
                }
            }
        }

        fun destroy() {
            runCatching {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }
    }

    private data class Scan(val title: String, val description: String?, val videos: List<String>)

    /** `evaluateJavascript` liefert JSON-im-JSON — zweimal auspacken. */
    private fun parseScan(raw: String?): Scan? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching {
            val inner = JSONTokener(raw).nextValue() as? String ?: return null
            val o = JSONObject(inner)
            val videos = o.optJSONArray("videos")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
            }.orEmpty()
            Scan(
                title = o.optString("title"),
                description = o.optString("description")
                    .replace(Regex("\\s+"), " ").trim()
                    .takeIf { it.isNotEmpty() }?.take(MAX_DESCRIPTION),
                videos = videos,
            )
        }.getOrNull()
    }

    private fun emptyResponse() =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 35_000L
        const val POLL_MS = 400L
        const val POKE_MS = 3_000L
        const val GRACE_MS = 2_000L
        const val WIDTH = 1280
        const val HEIGHT = 720
        const val MAX_DESCRIPTION = 5000
    }
}
