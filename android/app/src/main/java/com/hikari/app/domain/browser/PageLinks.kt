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

    /** Liefert Titel, Beschreibung, direkte <video>-Quellen und alle Links als JSON. */
    val SCAN = """
        (function () {
          function abs(u) { try { return new URL(u, location.href).href } catch (e) { return null } }
          function meta(sel) {
            var el = document.querySelector(sel);
            return el ? (el.getAttribute('content') || '') : '';
          }
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
          // og:description ist das gepflegtere Feld; die klassische
          // description ist der Fallback, leer wenn nichts da ist.
          var description = meta('meta[property="og:description"]') || meta('meta[name="description"]');
          return JSON.stringify({
            title: document.title || '',
            url: location.href,
            description: description,
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

    fun episodeNumber(text: String): Int? {
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

/**
 * Prüft den Seitentitel, bevor er als Videotitel übernommen wird.
 *
 * Seiten hinter einem Bot-Schutz (Cloudflare, DDoS-Guard) tragen während der
 * Prüfung einen Platzhaltertitel. Wird in genau dem Moment eingesammelt — und
 * das ist der Normalfall, weil der Player erst nach der Prüfung startet —,
 * landet dieser Platzhalter als Videotitel in der Bibliothek. Eine Folge
 * Modern Family hieß deshalb "Security Check" und war unter dem Namen nicht
 * wiederzufinden.
 *
 * Lieber gar kein Titel als ein falscher: Ohne Titel setzt die Übersicht
 * Serie und Folgennummer ein, was ohnehin die bessere Beschriftung ist.
 */
object PageTitleFilter {

    private val BLOCKED = listOf(
        "security check",
        "just a moment",
        "attention required",
        "ddos-guard",
        "checking your browser",
        "bitte warten",
        "einen moment",
        "access denied",
        "cloudflare",
        "verify you are human",
        "captcha",
        "403 forbidden",
        "404 not found",
    )

    /** Maximale Titellänge — Seitentitel enthalten oft ganze Beschreibungen. */
    private const val MAX_LENGTH = 200

    /** Liefert den brauchbaren Titel oder null, wenn er nichts taugt. */
    fun clean(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        if (t.length < 3) return null
        val lower = t.lowercase()
        if (BLOCKED.any { it in lower }) return null
        return if (t.length > MAX_LENGTH) t.take(MAX_LENGTH) else t
    }
}
