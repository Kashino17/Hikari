package com.hikari.app.domain.browser

/** Ein auf der Seite gefundener Link, der nach einer Folge aussieht. */
data class PageLink(
    val url: String,
    val label: String,
    val episode: Int? = null,
)

/**
 * JavaScript, das im WebView läuft und die Seite auswertet.
 *
 * Das ist der "Extension"-Teil: Android hat keine Chrome-Extension-API im
 * WebView, aber ein injiziertes Script plus JS-Bridge leistet dasselbe — es
 * sieht das fertig gerenderte DOM, also auch alles, was erst per JavaScript
 * nachgeladen wurde.
 */
object PageScripts {

    /** Liefert Titel, direkte <video>-Quellen und alle Links als JSON. */
    val SCAN = """
        (function () {
          function abs(u) { try { return new URL(u, location.href).href } catch (e) { return null } }
          var videos = [];
          document.querySelectorAll('video').forEach(function (v) {
            if (v.currentSrc) videos.push(abs(v.currentSrc));
            if (v.src) videos.push(abs(v.src));
            v.querySelectorAll('source').forEach(function (s) { if (s.src) videos.push(abs(s.src)) });
          });
          var links = [];
          document.querySelectorAll('a[href]').forEach(function (a) {
            var href = abs(a.getAttribute('href'));
            if (!href) return;
            var text = (a.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 120);
            links.push({ url: href, label: text });
          });
          return JSON.stringify({
            title: document.title || '',
            url: location.href,
            videos: videos.filter(Boolean),
            links: links
          });
        })();
    """.trimIndent()

    /**
     * Stößt die Wiedergabe an. Ohne das startet der Player auf vielen Seiten
     * nie von selbst — und ohne laufenden Player gibt es keinen Stream-Request,
     * den der Sniffer mitlesen könnte.
     */
    val AUTOPLAY = """
        (function () {
          var v = document.querySelector('video');
          if (v) { v.muted = true; var p = v.play(); if (p && p.catch) p.catch(function(){}); return 'video' }
          var sel = ['.jw-icon-display', '.vjs-big-play-button', '.plyr__control--overlaid',
                     '[class*="play-button"]', '[id*="play"]', '.play'];
          for (var i = 0; i < sel.length; i++) {
            var el = document.querySelector(sel[i]);
            if (el) { el.click(); return sel[i] }
          }
          return 'none'
        })();
    """.trimIndent()
}

/**
 * Filtert aus allen Links einer Seite die heraus, die plausibel Folgen
 * derselben Serie sind.
 *
 * Bewusst konservativ: Lieber ein paar Folgen übersehen, als dem Nutzer die
 * halbe Navigationsleiste der Seite als "Folgen" anzubieten. Deshalb müssen
 * Kandidaten von derselben Domain stammen und eine erkennbare Folgennummer
 * tragen.
 */
object EpisodeLinkFilter {

    private val EPISODE_PATTERNS = listOf(
        Regex("""(?:^|[/\-_])(?:folge|episode|ep|e)[\-_]?(\d{1,4})(?:$|[/\-_.?])""", RegexOption.IGNORE_CASE),
        Regex("""[sS]\d{1,2}[eE](\d{1,4})"""),
        Regex("""(?:^|\s)(?:folge|episode|ep\.?)\s*(\d{1,4})(?:\s|$)""", RegexOption.IGNORE_CASE),
    )

    fun extract(pageUrl: String, links: List<PageLink>): List<PageLink> {
        val host = hostOf(pageUrl) ?: return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<PageLink>()

        for (link in links) {
            if (hostOf(link.url) != host) continue
            // Die aktuelle Seite selbst ist keine weitere Folge.
            if (link.url.substringBefore('#') == pageUrl.substringBefore('#')) continue
            val episode = episodeNumber(link.url) ?: episodeNumber(link.label) ?: continue
            val key = link.url.substringBefore('#')
            if (!seen.add(key)) continue
            out.add(link.copy(url = key, episode = episode))
        }
        return out.sortedBy { it.episode ?: Int.MAX_VALUE }
    }

    private fun episodeNumber(text: String): Int? {
        for (p in EPISODE_PATTERNS) {
            val m = p.find(text) ?: continue
            val n = m.groupValues.getOrNull(1)?.toIntOrNull() ?: continue
            if (n in 1..2000) return n
        }
        return null
    }

    private fun hostOf(url: String): String? =
        runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()
}
