package com.hikari.app.domain.browser

import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

/**
 * Brave-artiger Schutz für den In-App-Browser: Werbe-Requests, Pop-ups und
 * Weiterleitungen auf fremde Apps werden gestoppt, Werbeflächen im DOM
 * ausgeblendet.
 *
 * Dreistufig, weil keine Stufe allein reicht:
 *  1. Netzwerk — [shouldBlockRequest] verwirft Requests an bekannte Ad-Hosts
 *     ([AdHosts]) und an typische Werbe-Pfade.
 *  2. Fenster — [judgePopup] / [judgeNavigation] lassen nur durch, was der
 *     Nutzer erkennbar wollte. Die Werbe-Hostliste allein reicht dafür nicht:
 *     Pop-under-Netze wechseln ihre Domains ständig.
 *  3. DOM — [COSMETIC_SCRIPT] blendet Werbecontainer aus und neutralisiert
 *     window.open ohne Geste.
 *
 * Der Streaming-Schutz hat Vorrang: Was nach Videostream aussieht (m3u8, mp4,
 * Segmente), wird NIE blockiert, auch wenn der Pfad "ads" enthält — sonst
 * verlöre der Sniffer die eigentliche Quelle.
 */
/** Urteil über ein Pop-up oder einen Seitenwechsel. */
enum class NavVerdict {
    ALLOW,
    /** Gesperrt, ohne Rückfrage (Werbe-Host, App-Sprung, Pop-up ohne Fingertipp). */
    BLOCK,
    /** Gesperrt, aber dem Nutzer zum bewussten Öffnen angeboten. */
    OFFER,
}

/** Was der Finger zuletzt getroffen hat (Hit-Test des WebViews). */
sealed interface Tap {
    /** Ein Link mit Text; [href] ist sein Ziel. */
    data class TextLink(val href: String?) : Tap

    /** Ein Bild-Link — das Ziel verrät der Hit-Test hier nicht. */
    data object ImageLink : Tap

    /** Kein Link (Fläche, Knopf, Video) oder gar nichts. */
    data object Other : Tap

    /** true, wenn dieser Tipp erkennbar zu [url] wollte. */
    fun backs(url: String): Boolean = when (this) {
        is TextLink -> href != null && (href == url || Sites.sameSite(href, url))
        ImageLink -> true
        Other -> false
    }
}

/** »Dieselbe Seite« heißt: dieselbe Hauptdomain, egal welche Subdomain. */
object Sites {
    private val SECOND_LEVEL = setOf("co", "com", "net", "org", "gov", "edu", "ac", "or", "ne", "go")

    fun siteOf(url: String?): String? {
        val host = runCatching { URI(url ?: return null).host?.lowercase() }.getOrNull() ?: return null
        val labels = host.trimEnd('.').split('.')
        if (labels.size <= 2 || labels.all { it.toIntOrNull() != null }) return host
        // example.co.uk: Länder-Endung mit allgemeiner Stufe davor → drei Teile gehören zusammen.
        val three = labels.last().length == 2 && labels[labels.size - 2] in SECOND_LEVEL
        return labels.takeLast(if (three) 3 else 2).joinToString(".")
    }

    fun sameSite(a: String?, b: String?): Boolean {
        val site = siteOf(a) ?: return false
        return site == siteOf(b)
    }
}

class AdBlocker {

    @Volatile
    var enabled: Boolean = true

    // Zustand der laufenden Navigation — nur vom Hauptthread angefasst.
    private var pageStartedAt = 0L
    private var touchedThisPage = false
    /** Die laufende Kette begann mit etwas, das der Nutzer wirklich wollte (angetippter Link, Adressleiste). */
    private var chainTrusted = true
    private var chainHadGesture = false

    /** Eine neue Seite beginnt zu laden: Ab jetzt zählt, ob der Nutzer sie anfasst. */
    fun onPageStarted(nowMs: Long) {
        pageStartedAt = nowMs
        touchedThisPage = false
    }

    /** Der Finger hat die Seite berührt. */
    fun onTouch() {
        touchedThisPage = true
    }

    /** Die nächste Kette ist gewollt (Adressleiste, Durchlauf, »Öffnen« nach einer Sperre). */
    fun trustNextChain() {
        chainTrusted = true
    }

    private val _blocked = AtomicInteger(0)

    /** Anzahl blockierter Requests/Pop-ups seit dem letzten [resetCount]. */
    val blockedCount: Int get() = _blocked.get()

    fun resetCount() = _blocked.set(0)

    /**
     * true, wenn ein Request verworfen werden soll.
     *
     * @param allowedByUser Hauptframe-Aufruf mit Nutzer-Geste bzw. bewusst
     *   eingegebene URL — der wird nie blockiert, damit man auch eine
     *   Werbe-Domain absichtlich besuchen kann.
     */
    fun shouldBlockRequest(url: String, isMainFrame: Boolean, allowedByUser: Boolean): Boolean {
        if (!enabled) return false
        if (isMainFrame && allowedByUser) return false
        if (isStream(url)) return false
        val blocked = AdHosts.isAdUrl(url) || matchesAdPath(url)
        if (blocked) _blocked.incrementAndGet()
        return blocked
    }

    /**
     * Neues Fenster (window.open / target=_blank). Der Browser hat nur einen Tab, ein erlaubtes
     * Pop-up lädt also in ihm — deshalb streng:
     *  - ohne Fingertipp oder auf einen Werbe-Host: gesperrt,
     *  - auf eine fremde Seite: gesperrt, aber zum bewussten Öffnen angeboten
     *    (Pop-under hängen sich an jeden beliebigen Tipp, die Geste beweist nichts),
     *  - auf dieselbe Seite: erlaubt (Hoster-Links wie /redirect/123 mit target=_blank).
     */
    fun judgePopup(url: String, fromUrl: String?, hasGesture: Boolean, tap: Tap): NavVerdict {
        if (!enabled) return NavVerdict.ALLOW
        val scheme = schemeOf(url)
        if (scheme !in WEB_SCHEMES) return blocked(NavVerdict.BLOCK)
        if (!hasGesture || AdHosts.isAdUrl(url)) return blocked(NavVerdict.BLOCK)
        if (isWeb(fromUrl) && !Sites.sameSite(url, fromUrl)) return blocked(NavVerdict.OFFER)
        chainTrusted = tap.backs(url)
        chainHadGesture = true
        return NavVerdict.ALLOW
    }

    /**
     * Seitenwechsel im Hauptfenster. Erlaubt ist, was der Nutzer erkennbar wollte:
     *  - bewusst angesteuert (Adressleiste, Durchlauf, »Öffnen«),
     *  - innerhalb derselben Seite,
     *  - auf eine fremde Seite nur über einen wirklich angetippten Link dorthin ([tap]) —
     *    ein Tipp auf irgendetwas, der woandershin führt, ist die klassische Klick-Falle,
     *  - Weiterleitungen einer Kette, die so begonnen hat,
     *  - die automatische Weiterleitung einer frisch geladenen, unberührten Seite
     *    (Hoster schicken so auf ihre Spiegel-Domain).
     * Alles andere wird gesperrt und zum bewussten Öffnen angeboten; Werbe-Hosts und
     * App-Sprünge (intent://, market://) ohne Angebot.
     */
    fun judgeNavigation(
        url: String,
        fromUrl: String?,
        hasGesture: Boolean,
        isRedirect: Boolean,
        intended: Boolean,
        tap: Tap,
        nowMs: Long,
    ): NavVerdict {
        if (!enabled) return NavVerdict.ALLOW
        val scheme = schemeOf(url)
        if (scheme in INTERNAL_SCHEMES) return NavVerdict.ALLOW
        if (scheme !in WEB_SCHEMES) return blocked(NavVerdict.BLOCK) // die Seite darf keine Apps starten
        if (intended) {
            chainTrusted = true
            return NavVerdict.ALLOW
        }
        val foreign = isWeb(fromUrl) && !Sites.sameSite(url, fromUrl)
        val adHost = AdHosts.isAdUrl(url)

        if (isRedirect) {
            if (!foreign) return NavVerdict.ALLOW
            if (adHost) return blocked(NavVerdict.BLOCK)
            // Suchmaschinen leiten über eine eigene Adresse weiter: Dann zählt der angetippte Link.
            val wanted = chainTrusted || (chainHadGesture && tap.backs(url))
            return if (wanted) NavVerdict.ALLOW else blocked(NavVerdict.OFFER)
        }

        val linkBacked = hasGesture && tap.backs(url)
        chainHadGesture = hasGesture
        if (!foreign) {
            chainTrusted = linkBacked
            return NavVerdict.ALLOW
        }
        if (adHost) return blocked(if (linkBacked) NavVerdict.OFFER else NavVerdict.BLOCK)
        if (linkBacked) {
            chainTrusted = true
            return NavVerdict.ALLOW
        }
        val autoForward = !hasGesture && !touchedThisPage && nowMs - pageStartedAt <= AUTO_FORWARD_MS
        return if (autoForward) NavVerdict.ALLOW else blocked(NavVerdict.OFFER)
    }

    private fun blocked(verdict: NavVerdict): NavVerdict {
        _blocked.incrementAndGet()
        return verdict
    }

    companion object {
        private val WEB_SCHEMES = setOf("http", "https")
        private val INTERNAL_SCHEMES = setOf("about", "data", "blob", "javascript", "file")

        /** So lange nach dem Laden gilt eine Weiterleitung ohne Berührung noch als Teil der Seite. */
        const val AUTO_FORWARD_MS = 8_000L

        private fun schemeOf(url: String) = url.substringBefore(':', "").lowercase()

        private fun isWeb(url: String?) = url != null && schemeOf(url) in WEB_SCHEMES

        private val STREAM_EXT = Regex("""\.(m3u8|mpd|mp4|webm|mkv|ts|m4s|m4a|mp3|aac|vtt|srt)(\?|$)""", RegexOption.IGNORE_CASE)

        // Pfadfragmente, die fast nur Werbung ausliefern. Bewusst eng gehalten:
        // "ads" allein träfe auch "uploads"/"downloads".
        private val AD_PATH = Regex(
            """/(ads?|adserver|adframe|advert(s|ising)?|banners?|popunder|popup|pagead|prebid|sponsor(ed)?)[/._?-]""" +
                """|/(ads?|adsbygoogle|adsense|popads|pop|banner)\.js(\?|$)""" +
                """|[?&](adzone|ad_unit|adslot|clickid_ad)=""",
            RegexOption.IGNORE_CASE,
        )

        fun isStream(url: String): Boolean {
            val path = runCatching { URI(url).path }.getOrNull() ?: return false
            return STREAM_EXT.containsMatchIn(path) || path.contains("/hls/", ignoreCase = true)
        }

        fun matchesAdPath(url: String): Boolean {
            val u = runCatching { URI(url) }.getOrNull() ?: return false
            val rest = (u.rawPath.orEmpty() + (u.rawQuery?.let { "?$it" } ?: ""))
            return AD_PATH.containsMatchIn(rest)
        }

        /**
         * Blendet Werbeflächen aus, kappt unsichtbare Klick-Overlays (die
         * klassische Pop-up-Falle: ein transparenter Layer über dem Player) und
         * lässt window.open nur in den ersten Sekunden nach einer Nutzer-Geste
         * zu. Läuft vor und nach dem Seitenaufbau — Overlays entstehen oft spät.
         */
        val COSMETIC_SCRIPT = """
            (function () {
              if (window.__hikariShield) return;
              window.__hikariShield = true;
              var lastGesture = 0;
              ['pointerdown', 'touchstart', 'keydown'].forEach(function (t) {
                window.addEventListener(t, function (e) { if (e.isTrusted) lastGesture = Date.now(); }, true);
              });
              var realOpen = window.open;
              window.open = function () {
                if (Date.now() - lastGesture > 1200) return null;
                return realOpen.apply(window, arguments);
              };
              var css = [
                'ins.adsbygoogle', '[id^="google_ads"]', '[id^="div-gpt-ad"]', '[class*="adsbygoogle"]',
                'iframe[src*="doubleclick"]', 'iframe[src*="googlesyndication"]', 'iframe[src*="adsterra"]',
                '[id*="banner-ad"]', '[class*="banner-ad"]', '[class*="ad-banner"]', '[class*="ad-container"]',
                '[class*="advert"]', '[id*="advert"]', '[class*="popunder"]', '[id*="popunder"]',
                'a[href*="doubleclick.net"]', 'a[href*="propellerads"]', 'a[href*="adsterra"]'
              ].join(',');
              var style = document.createElement('style');
              style.textContent = css + '{display:none!important;visibility:hidden!important;}';
              (document.head || document.documentElement).appendChild(style);
              // Billiger Check statt alle Elemente durchzurechnen: Was liegt
              // mitten auf dem Bildschirm? Ist es ein riesiges, leeres,
              // fixiertes Overlay ohne Medien, ist es die Klick-Falle.
              function sweep() {
                if (document.hidden) return;
                var el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
                if (!el || el === document.body || el === document.documentElement) return;
                var cs = getComputedStyle(el);
                if (cs.position !== 'fixed' && cs.position !== 'absolute') return;
                var r = el.getBoundingClientRect();
                var big = r.width >= window.innerWidth * 0.9 && r.height >= window.innerHeight * 0.9;
                var z = parseInt(cs.zIndex, 10) || 0;
                var invisible = parseFloat(cs.opacity) < 0.05 || cs.backgroundColor === 'rgba(0, 0, 0, 0)';
                var hasMedia = el.querySelector('video,canvas,iframe');
                if (big && z > 999 && invisible && !hasMedia && !(el.innerText || '').trim()) el.remove();
              }
              setInterval(sweep, 2500);
              // Klick-Falle als Link: ein fremder Link, der den halben Bildschirm bedeckt,
              // unsichtbar ist oder groß und leer. Der Tipp wird geschluckt, die Falle entfernt.
              function site(h) { return h.replace(/^www\./, '').split('.').slice(-2).join('.'); }
              document.addEventListener('click', function (e) {
                var a = e.target && e.target.closest ? e.target.closest('a[href]') : null;
                if (!a || !/^https?:/i.test(a.href)) return;
                var host;
                try { host = new URL(a.href).hostname; } catch (x) { return; }
                if (site(host) === site(location.hostname)) return;
                var r = a.getBoundingClientRect();
                var share = (r.width * r.height) / (window.innerWidth * window.innerHeight);
                var hollow = !(a.innerText || '').trim() && !a.querySelector('img,svg,picture,video,canvas');
                var faint = parseFloat(getComputedStyle(a).opacity) < 0.1;
                if (share >= 0.5 || faint || (hollow && share >= 0.15)) {
                  e.preventDefault();
                  e.stopImmediatePropagation();
                  a.remove();
                }
              }, true);
            })();
        """.trimIndent()
    }
}
