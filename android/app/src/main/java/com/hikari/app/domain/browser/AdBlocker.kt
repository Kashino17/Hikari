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
 *  2. Fenster — [shouldBlockPopup] / [shouldBlockNavigation] verhindern
 *     Pop-ups ohne Nutzer-Geste, Ad-Weiterleitungen und App-Sprünge
 *     (intent://, market://).
 *  3. DOM — [COSMETIC_SCRIPT] blendet Werbecontainer aus und neutralisiert
 *     window.open ohne Geste.
 *
 * Der Streaming-Schutz hat Vorrang: Was nach Videostream aussieht (m3u8, mp4,
 * Segmente), wird NIE blockiert, auch wenn der Pfad "ads" enthält — sonst
 * verlöre der Sniffer die eigentliche Quelle.
 */
class AdBlocker {

    @Volatile
    var enabled: Boolean = true

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

    /** Pop-up-Fenster (window.open / target=_blank): nur mit Nutzer-Geste und nie auf Ad-Hosts. */
    fun shouldBlockPopup(url: String?, hasGesture: Boolean): Boolean {
        if (!enabled) return false
        val blocked = !hasGesture || (url != null && AdHosts.isAdUrl(url))
        if (blocked) _blocked.incrementAndGet()
        return blocked
    }

    /**
     * Hauptframe-Navigation. Blockiert werden App-Sprünge (intent://, market://,
     * …) und Ad-Weiterleitungen ohne Geste.
     */
    fun shouldBlockNavigation(url: String, hasGesture: Boolean, intended: Boolean): Boolean {
        if (!enabled) return false
        val scheme = url.substringBefore(':', "").lowercase()
        val blocked = when {
            scheme in WEB_SCHEMES || scheme in INTERNAL_SCHEMES -> {
                !hasGesture && !intended && AdHosts.isAdUrl(url)
            }
            else -> true // intent:, market:, tel:, … — die Seite darf keine Apps starten
        }
        if (blocked) _blocked.incrementAndGet()
        return blocked
    }

    companion object {
        private val WEB_SCHEMES = setOf("http", "https")
        private val INTERNAL_SCHEMES = setOf("about", "data", "blob", "javascript", "file")

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
              function sweep() {
                var w = window.innerWidth, h = window.innerHeight;
                var nodes = document.querySelectorAll('div,a,iframe');
                for (var i = 0; i < nodes.length; i++) {
                  var el = nodes[i];
                  var cs = getComputedStyle(el);
                  if (cs.position !== 'fixed' && cs.position !== 'absolute') continue;
                  var r = el.getBoundingClientRect();
                  var big = r.width >= w * 0.9 && r.height >= h * 0.9;
                  var z = parseInt(cs.zIndex, 10) || 0;
                  var invisible = parseFloat(cs.opacity) < 0.05 || cs.backgroundColor === 'rgba(0, 0, 0, 0)';
                  var hasMedia = el.querySelector('video,canvas,iframe[src*="embed"],iframe[src*="player"]');
                  if (big && z > 999 && invisible && !hasMedia && !el.innerText.trim()) el.remove();
                }
              }
              setInterval(sweep, 1500);
            })();
        """.trimIndent()
    }
}
