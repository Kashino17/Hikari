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
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
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
 * Ergebnis eines Sniff-Versuchs samt Diagnose-Spur. Die Spur landet bei
 * Misserfolg im Fehlertext der Import-Karte — nur so ist von außen zu sehen,
 * wo es hakte (Seite blockiert? keine Hoster? toter Hoster?), ohne Logcat.
 */
data class HeadlessOutcome(
    val result: HeadlessResult?,
    val diagnostics: String,
)

/** Eine auf einer Staffel-/Serienseite gefundene Folge. */
data class EpisodeRef(
    val url: String,
    val episode: Int?,
    val label: String,
)

/** Was ein Besuch einer Staffel-/Übersichtsseite ergab. */
data class EpisodeDiscovery(
    val seriesTitle: String?,
    val season: Int?,
    val episodes: List<EpisodeRef>,
    val diagnostics: String,
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
     * [timeoutMs] keiner auftauchte (Captcha, toter Hoster, DRM) — plus eine
     * Diagnose-Spur, die im Fehlerfall sichtbar macht, woran es lag.
     */
    suspend fun sniffDetailed(pageUrl: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): HeadlessOutcome =
        lock.withLock {
            withContext(Dispatchers.Main.immediate) {
                val session = Session(pageUrl)
                try {
                    val result = withTimeoutOrNull(timeoutMs) { session.run() }
                    HeadlessOutcome(result, session.diagnostics())
                } finally {
                    session.destroy()
                }
            }
        }

    /** Bequemer Kurzaufruf ohne Diagnose. */
    suspend fun sniff(pageUrl: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): HeadlessResult? =
        sniffDetailed(pageUrl, timeoutMs).result

    /**
     * Öffnet eine Staffel-/Übersichtsseite und liest die Folgen-Links daraus
     * aus (serienstream/aniworld: die Episoden-Liste im Seitenmenü). Damit kann
     * der Nutzer eine ganze Staffel schicken; jede Folge wird danach einzeln
     * gesnifft.
     */
    suspend fun discoverEpisodes(pageUrl: String, timeoutMs: Long = DISCOVER_TIMEOUT_MS): EpisodeDiscovery =
        lock.withLock {
            withContext(Dispatchers.Main.immediate) {
                val session = Session(pageUrl)
                try {
                    withTimeoutOrNull(timeoutMs) { session.discover() }
                        ?: EpisodeDiscovery(null, null, emptyList(), session.diagnostics())
                } finally {
                    session.destroy()
                }
            }
        }

    private inner class Session(private val pageUrl: String) {
        private val sniffer = MediaSniffer()
        @Volatile private var pageFinished = false
        @Volatile private var scan: Scan? = null

        /** Kurze, für den Nutzer lesbare Spur, was der Besuch tat. */
        private val trail = ArrayList<String>()
        private fun note(msg: String) { if (trail.size < 12) trail.add(msg) }
        fun diagnostics(): String = trail.joinToString(" · ")

        private fun shortHost(url: String): String =
            runCatching { java.net.URI(url).host ?: url }.getOrDefault(url).removePrefix("www.")

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

        /**
         * Besucht die Seite und, falls dort kein Player eingebettet ist, die
         * Hoster-Seiten dahinter.
         *
         * Viele Streaming-Seiten (serienstream/aniworld & Co.) betten das Video
         * NICHT direkt ein — die Episoden-Seite trägt nur eine Liste von
         * Hoster-Weiterleitungen (`/redirect/…`, voe/filemoon/…). Findet der
         * erste Besuch keinen Stream, sammeln wir diese Links und laden sie der
         * Reihe nach, bis einer einen abspielbaren Stream liefert. Tote Hoster
         * werden so einfach übersprungen.
         */
        suspend fun run(): HeadlessResult? {
            val visited = HashSet<String>()
            val queue = ArrayDeque<String>()
            queue.add(pageUrl)
            var expanded = false
            var pageTitle: String? = null
            var pageDescription: String? = null

            while (queue.isNotEmpty()) {
                val target = queue.removeFirst()
                if (!visited.add(target)) continue
                if (target != pageUrl) {
                    // Frischer Kontext je Hoster-Embed, damit Funde nicht
                    // fälschlich der Vorseite zugeschrieben werden.
                    sniffer.reset()
                    // Referer = Episoden-Seite: manche Redirect-Endpunkte
                    // (serienstream/aniworld) leiten ohne ihn auf die Startseite
                    // um statt zum Hoster.
                    navigate(target, referer = pageUrl)
                    note("Hoster ${shortHost(target)}")
                }

                val best = pollTarget(if (target == pageUrl) FIRST_PAGE_MS else EMBED_MS)

                // Titel/Beschreibung stammen von der Episoden-Seite, nicht vom
                // nichtssagenden Hoster-Embed ("VOE", "Filemoon").
                if (target == pageUrl) {
                    pageTitle = PageTitleFilter.clean(scan?.title ?: webView.title)
                    pageDescription = scan?.description
                    note(
                        "Seite: ${PageTitleFilter.clean(webView.title) ?: shortHost(pageUrl)}" +
                            " (${sniffer.inspectedCount()} Requests)",
                    )
                }

                if (best != null) {
                    note("Stream gefunden: ${best.kind}")
                    return HeadlessResult(
                        pageUrl = pageUrl,
                        title = pageTitle ?: PageTitleFilter.clean(scan?.title ?: webView.title),
                        description = pageDescription,
                        finding = best,
                    )
                } else if (target != pageUrl) {
                    note("kein Stream (${sniffer.inspectedCount()} Req.)")
                }

                // Nach dem ersten (erfolglosen) Besuch die Hoster-Links der
                // Seite in die Warteschlange stellen — nur einmal.
                if (!expanded) {
                    expanded = true
                    val links = collectHosterLinks()
                    note("${links.size} Hoster-Links")
                    for (link in links) if (link !in visited) queue.add(link)
                }
            }
            return null
        }

        /**
         * Lädt eine Staffel-/Übersichtsseite und liest die Folgen-Links aus.
         * Nutzt denselben DOM-Scan wie der Browser plus [EpisodeLinkFilter].
         */
        suspend fun discover(): EpisodeDiscovery {
            navigate(pageUrl)
            // Auf pageFinished warten, dann das DOM auslesen.
            val start = System.currentTimeMillis()
            while (!pageFinished && System.currentTimeMillis() - start < FIRST_PAGE_MS) delay(POLL_MS)
            delay(POLL_MS)
            val scanRaw = evalString(PageScripts.SCAN)
            val parsed = parseFullScan(scanRaw)
            val links = parsed?.links.orEmpty()
            val episodes = EpisodeLinkFilter.extract(pageUrl, links).map {
                EpisodeRef(url = it.url, episode = it.episode, label = it.label)
            }
            val meta = PageMetaParser.parse(pageUrl)
            note("${PageTitleFilter.clean(webView.title) ?: shortHost(pageUrl)}: ${episodes.size} Folgen")
            return EpisodeDiscovery(
                seriesTitle = meta.seriesTitle,
                season = meta.season,
                episodes = episodes,
                diagnostics = diagnostics(),
            )
        }

        /** Lädt [url] (optional mit Referer) und setzt den Seiten-Status zurück. */
        private fun navigate(url: String, referer: String? = null) {
            pageFinished = false
            scan = null
            if (referer != null) webView.loadUrl(url, mapOf("Referer" to referer))
            else webView.loadUrl(url)
        }

        /**
         * Pollt bis zu [budgetMs] auf einen abspielbaren Stream und stößt den
         * Player dabei wiederholt an. Liefert den Fund oder null bei Zeitablauf.
         */
        private suspend fun pollTarget(budgetMs: Long): MediaFinding? {
            val start = System.currentTimeMillis()
            var firstHit = -1L
            var lastPoke = 0L
            while (System.currentTimeMillis() - start < budgetMs) {
                delay(POLL_MS)
                val now = System.currentTimeMillis()
                val best = sniffer.best()
                if (best != null) {
                    if (firstHit < 0) firstHit = now
                    // Kurz nachwarten: Nach der ersten Datei taucht oft noch
                    // die HLS-Playlist auf, die alle Qualitäten enthält.
                    if (best.kind == MediaKind.HLS || now - firstHit >= GRACE_MS) return best
                }
                // Den Player alle paar Sekunden erneut anstoßen: Der erste
                // Klick öffnet bei vielen Hostern nur ein Werbe-Overlay,
                // erst der zweite startet das Video.
                if (pageFinished && now - lastPoke >= POKE_MS) {
                    lastPoke = now
                    webView.evaluateJavascript(PageScripts.AUTOPLAY, null)
                    webView.evaluateJavascript(PageScripts.SCAN) { raw ->
                        parseScan(raw)?.let { s ->
                            for (v in s.videos) sniffer.onRequest(v, emptyMap())
                            scan = s
                        }
                    }
                }
            }
            return null
        }

        /** Hoster-/Embed-Links der aktuellen Seite (leer, wenn keine da sind). */
        private suspend fun collectHosterLinks(): List<String> =
            parseStringArray(evalString(HOSTER_LINKS))

        /** [WebView.evaluateJavascript] als suspend-Aufruf. */
        private suspend fun evalString(script: String): String? =
            suspendCancellableCoroutine { cont ->
                webView.evaluateJavascript(script) { raw -> if (cont.isActive) cont.resume(raw) }
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

    /** Wie [Scan], aber mit den Links der Seite — für die Staffel-Erkennung. */
    private data class FullScan(val links: List<PageLink>)

    private fun parseFullScan(raw: String?): FullScan? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching {
            val inner = JSONTokener(raw).nextValue() as? String ?: return null
            val o = JSONObject(inner)
            val links = o.optJSONArray("links")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val l = arr.optJSONObject(i) ?: return@mapNotNull null
                    val url = l.optString("url").takeIf(String::isNotBlank) ?: return@mapNotNull null
                    PageLink(url, l.optString("label"))
                }
            }.orEmpty()
            FullScan(links)
        }.getOrNull()
    }

    /** JSON-Array-im-JSON-String (wie [parseScan]) zu einer Liste von Strings. */
    private fun parseStringArray(raw: String?): List<String> {
        if (raw.isNullOrBlank() || raw == "null") return emptyList()
        return runCatching {
            val inner = JSONTokener(raw).nextValue() as? String ?: return emptyList()
            val arr = JSONArray(inner)
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())
    }

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
        // Reicht für die Episoden-Seite plus zwei, drei Hoster-Versuche.
        const val DEFAULT_TIMEOUT_MS = 55_000L
        /** Staffelseite laden und Folgen-Links auslesen — kein Player nötig. */
        const val DISCOVER_TIMEOUT_MS = 20_000L
        const val POLL_MS = 400L
        const val POKE_MS = 3_000L
        const val GRACE_MS = 2_000L
        /** Zeit für die erste Seite: hier steht meist nur die Hoster-Liste. */
        const val FIRST_PAGE_MS = 7_000L
        /** Zeit je Hoster-Embed, bis zum nächsten Kandidaten gewechselt wird. */
        const val EMBED_MS = 13_000L
        const val WIDTH = 1280
        const val HEIGHT = 720
        const val MAX_DESCRIPTION = 5000

        /**
         * Sammelt Hoster-/Embed-Links einer Seite, die den Player nicht direkt
         * einbettet. Reihenfolge: erst die typischen Streaming-Weiterleitungen
         * (serienstream/aniworld `/redirect/…`), dann eingebettete Frames, dann
         * jeder Link auf eine bekannte Hoster-Domain.
         */
        val HOSTER_LINKS = """
            (function () {
              function abs(u) { try { return new URL(u, location.href).href } catch (e) { return null } }
              var out = [], seen = {};
              function add(h) { if (h && !seen[h]) { seen[h] = 1; out.push(h) } }
              document.querySelectorAll(
                'a[href*="/redirect/"], a.watchEpisode, .hosterSiteVideo a[href], a[href*="/out/"], a[href*="/goto/"]'
              ).forEach(function (a) { add(abs(a.getAttribute('href'))) });
              document.querySelectorAll('iframe[src]').forEach(function (f) { add(abs(f.getAttribute('src'))) });
              var re = /(voe\.|filemoon|filelions|streamtape|dood|vidoza|upstream|mixdrop|vidmoly|luluvdo|supervideo|streamlare|savefiles|vinovo|streamvid|vidhide|streamwish)/i;
              document.querySelectorAll('a[href]').forEach(function (a) {
                var h = abs(a.getAttribute('href'));
                if (h && re.test(h)) add(h);
              });
              return JSON.stringify(out.slice(0, 10));
            })();
        """.trimIndent()
    }
}
