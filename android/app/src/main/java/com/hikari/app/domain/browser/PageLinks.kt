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
          // Was die Seite selbst über Serie/Folge/Film sagt — verlässlicher als
          // der URL-Slug ("american-horror-story-die-dunkle-seite-in-dir").
          function text(sel) {
            var el = document.querySelector(sel);
            return el ? (el.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 200) : '';
          }
          var ld = { series: '', episode: '', season: null, number: null, movie: false, name: '' };
          document.querySelectorAll('script[type="application/ld+json"]').forEach(function (s) {
            try {
              var data = JSON.parse(s.textContent || '');
              var list = Array.isArray(data) ? data : (data['@graph'] || [data]);
              list.forEach(function (d) {
                if (!d || typeof d !== 'object') return;
                var t = String(d['@type'] || '').toLowerCase();
                if (t === 'tvepisode' || t === 'episode') {
                  if (d.partOfSeries && d.partOfSeries.name && !ld.series) ld.series = String(d.partOfSeries.name);
                  if (d.partOfSeason && d.partOfSeason.seasonNumber != null && ld.season == null) ld.season = Number(d.partOfSeason.seasonNumber);
                  if (d.episodeNumber != null && ld.number == null) ld.number = Number(d.episodeNumber);
                  if (d.name && !ld.episode) ld.episode = String(d.name);
                } else if (t === 'tvseries' || t === 'tvseason') {
                  if (d.name && !ld.series) ld.series = String(d.name);
                } else if (t === 'movie') {
                  ld.movie = true;
                  if (d.name && !ld.name) ld.name = String(d.name);
                }
              });
            } catch (e) {}
          });
          var seriesName = ld.series || text(
            '.series-title h1 span, .series-title h1, h1[itemprop="name"] span, h1[itemprop="name"], ' +
            '.seriesTitle, .anime-title h1, [data-series-title], .show-title h1'
          );
          var episodeName = ld.episode || text(
            '.episodeGermanTitle, .episodeEnglishTitle, .episode-title, h2.episodeTitle, [itemprop="episodeTitle"], .episode-name'
          );
          // Überschriften der Seite (h1–h3): viele Seiten nennen den Folgentitel
          // nur dort ("S01E02: Schulanfang (2)"), ohne JSON-LD und ohne feste
          // CSS-Klasse. Die Auswertung passiert in Kotlin (PageMetaParser).
          var headings = [];
          document.querySelectorAll('h1, h2, h3').forEach(function (h) {
            var t = (h.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 200);
            if (t && headings.length < 12) headings.push(t);
          });
          return JSON.stringify({
            headings: headings,
            title: document.title || '',
            url: location.href,
            description: description,
            videos: videos.filter(Boolean),
            links: links,
            ogTitle: meta('meta[property="og:title"]'),
            h1: text('h1'),
            seriesName: seriesName,
            episodeName: episodeName,
            season: ld.season,
            episode: ld.number,
            isMovie: ld.movie && !ld.series && !ld.episode && ld.number == null,
            movieName: ld.name
          });
        })();
    """.trimIndent()

    /**
     * true, wenn die Seite gerade eine Bot-Prüfung zeigt (Cloudflare Turnstile,
     * ALTCHA "I'm not a robot", "Just a moment"). Gemessen an serienstream.to:
     * Vor jedem Stream öffnet sich "Video wird vorbereitet … Schließe den Schritt
     * ab und tippe auf Weiter". Das ist gewollt nur für Menschen lösbar — die App
     * umgeht es nicht, sie erkennt es nur, um sauber zu warten oder abzubrechen.
     */
    val GATE_CHECK = """
        (function () {
          if (document.querySelector('#playerPrepareModal.show')) return true;
          if (document.querySelector('altcha-widget, .cf-turnstile, iframe[src*="challenges.cloudflare.com"]')) return true;
          var t = ((document.body && document.body.innerText) || '').slice(0, 3000);
          return /verify you are human|ich bin kein roboter|i.m not a robot|just a moment|bitte warten, bis wir deinen browser/i.test(t);
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
        // Steht die Staffel in der Seiten-URL, gehören Links in eine ANDERE
        // Staffel nicht dazu — sonst fallen Folge 3 aus Staffel 1 und Folge 3
        // aus Staffel 2 unter dieselbe Nummer zusammen.
        val pageSeason = seasonOf(pageUrl)
        val seen = HashSet<String>()
        val out = ArrayList<PageLink>()

        for (link in links) {
            if (hostOf(link.url) != host) continue
            // Die aktuelle Seite selbst ist keine weitere Folge.
            if (link.url.substringBefore('#') == pageUrl.substringBefore('#')) continue
            val linkSeason = seasonOf(link.url)
            if (pageSeason != null && linkSeason != null && linkSeason != pageSeason) continue
            val episode = episodeNumber(link.url) ?: episodeNumber(link.label) ?: continue
            val key = link.url.substringBefore('#')
            if (!seen.add(key)) continue
            out.add(link.copy(url = key, episode = episode))
        }
        return out.sortedBy { it.episode ?: Int.MAX_VALUE }
    }

    private val SEASON_IN_URL = Regex(
        """(?:^|[/\-_])(?:staffel|season|s)[\-_]?(\d{1,3})(?:$|[/\-_.?])""",
        RegexOption.IGNORE_CASE,
    )

    /** Staffelnummer aus dem URL-PFAD, null wenn keine genannt ist. */
    internal fun seasonOf(url: String): Int? {
        val path = runCatching { java.net.URI(url).path }.getOrNull() ?: return null
        return SEASON_IN_URL.find(path)?.groupValues?.getOrNull(1)?.toIntOrNull()
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

/**
 * Macht aus einem Seitentitel einen reinen Folgen-/Filmtitel.
 *
 * Seitentitel von Streaming-Seiten sind Mischtitel ("Ted Staffel 1 Episode 2 -
 * Serienstream"). Daraus wurde vorher der Folgentitel — mit Serienname und
 * Seitenname drin, und je nach Seite quer durcheinander. Hier fliegen Serienname,
 * Staffel-/Folgen-Angaben und der Seitenname raus; bleibt nichts übrig, gibt es
 * keinen Titel (die Übersicht beschriftet dann mit Serie + Folge).
 */
object EpisodeTitleCleaner {

    private val SEPARATORS = Regex("""\s+[-–—|·»:]\s+|\s*\|\s*""")
    private val NUMBERING = Regex(
        """(?i)\b(?:staffel|season|folge|episode|ep\.?)\s*\d{1,4}\b|\bs\d{1,2}\s*e\d{1,4}\b""",
    )
    private val SITE_NOISE = Regex(
        """(?i)\b(?:stream(?:en)?|online|kostenlos|anschauen|ansehen|gucken|deutsch|german|ger-?sub|ger-?dub|hd|free|watch)\b""",
    )

    fun clean(raw: String?, seriesTitle: String?, pageUrl: String? = null): String? {
        val siteLabel = pageUrl?.let { siteLabelOf(it) }
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val parts = text.split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }
        val series = seriesTitle?.trim()?.lowercase()
        val kept = parts.mapNotNull { part ->
            // Der Seitenname ("Serienstream") ist nie der Folgentitel.
            if (siteLabel != null && part.lowercase().replace(" ", "").contains(siteLabel)) return@mapNotNull null
            var p = part
            if (series != null) p = p.replace(Regex(Regex.escape(series), RegexOption.IGNORE_CASE), " ")
            p = p.replace(NUMBERING, " ").replace(SITE_NOISE, " ")
                .replace(Regex("""[\s\-–—|·:]+"""), " ").trim()
            p.takeIf { it.length >= 2 && it.any(Char::isLetter) }
        }
        // Mehrere übrige Teile = der Seitenname hängt dran; der Folgentitel steht vorn.
        return kept.firstOrNull()?.take(200)
    }

    /** "serienstream" aus "www.serienstream.to". */
    private fun siteLabelOf(url: String): String? {
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return null
        val labels = host.removePrefix("www.").split('.')
        return (if (labels.size >= 2) labels[labels.size - 2] else labels.firstOrNull())
            ?.takeIf { it.length >= 3 }
    }
}
