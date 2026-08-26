package com.hikari.app.domain.browser

import java.util.Collections

/** Art des gefundenen Mediums — bestimmt, wie yt-dlp es später anfassen muss. */
enum class MediaKind { HLS, DASH, PROGRESSIVE }

/**
 * Ein im Browser mitgelesener Medien-Fund.
 *
 * [referer] und [cookie] stammen aus den echten Request-Headern der Seite.
 * Ohne sie beantwortet ein Filehoster den späteren Download vom Server aus
 * nicht — er prüft beides und antwortet sonst mit 403.
 */
data class MediaFinding(
    val url: String,
    val kind: MediaKind,
    val referer: String? = null,
    val cookie: String? = null,
    val userAgent: String? = null,
    val contentType: String? = null,
)

/**
 * Liest die Netzwerk-Requests einer Webseite mit und sammelt daraus die
 * abspielbaren Medien-URLs.
 *
 * Das ist der Kern des In-App-Browsers: Statt die Extraktionslogik jedes
 * Hosters nachzubauen (die bricht, sobald der Hoster sein JavaScript ändert),
 * lassen wir die Seite ihren Player selbst starten und lesen mit, was er lädt.
 *
 * Wird vom WebView-Interceptor auf einem Hintergrund-Thread aufgerufen —
 * daher durchgehend synchronisiert.
 */
class MediaSniffer {

    private val found = Collections.synchronizedMap(LinkedHashMap<String, MediaFinding>())

    /**
     * Diagnose: Wie viele Requests der Interceptor insgesamt gesehen hat, und
     * die letzten davon. Ohne diese Zahlen ist bei "es wird nichts erkannt"
     * nicht zu unterscheiden, ob der Interceptor überhaupt läuft oder ob der
     * Filter zu streng greift.
     */
    private var inspected = 0
    private val recent = ArrayDeque<String>()

    /** Aufruf für jeden Request der Seite (aus `shouldInterceptRequest`). */
    fun onRequest(url: String, headers: Map<String, String>) {
        note(url)
        val kind = classify(url) ?: return
        if (isNoise(url)) return
        record(
            MediaFinding(
                url = url,
                kind = kind,
                referer = headers.entryIgnoreCase("Referer"),
                cookie = headers.entryIgnoreCase("Cookie"),
                userAgent = headers.entryIgnoreCase("User-Agent"),
            ),
        )
    }

    /**
     * Aufruf, wenn der Content-Type einer Antwort bekannt ist. Fängt Streams
     * ohne sprechende Endung ab — viele Hoster liefern unter
     * `/stream/abc?token=…` aus, da hilft nur der Content-Type.
     */
    fun onResponse(url: String, contentType: String?) {
        if (isNoise(url)) return
        val ct = contentType?.lowercase().orEmpty()
        val kind = classify(url) ?: when {
            ct.startsWith("video/") -> MediaKind.PROGRESSIVE
            ct.contains("mpegurl") -> MediaKind.HLS
            ct.contains("dash+xml") -> MediaKind.DASH
            else -> null
        } ?: return
        record(MediaFinding(url = url, kind = kind, contentType = contentType))
    }

    /** Alle Funde in Fundreihenfolge. */
    fun findings(): List<MediaFinding> = synchronized(found) { found.values.toList() }

    /**
     * Der wahrscheinlichste "das ist das Video dieser Seite"-Treffer.
     *
     * Playlists schlagen Einzeldateien: Ein HLS-Master enthält alle
     * Qualitätsstufen, während eine progressive Datei, die nebenher geladen
     * wird, meist die kleinste Variante oder eine Vorschau ist. Bei
     * Gleichstand gewinnt der erste Fund — der Hauptplayer startet vor
     * allem, was die Seite sonst noch nachlädt.
     */
    fun best(): MediaFinding? = findings().minByOrNull {
        when (it.kind) {
            MediaKind.HLS -> 0
            MediaKind.DASH -> 1
            MediaKind.PROGRESSIVE -> 2
        }
    }

    /** Anzahl aller vom Interceptor gesehenen Requests seit dem Seitenwechsel. */
    fun inspectedCount(): Int = synchronized(recent) { inspected }

    /** Die zuletzt gesehenen URLs, neueste zuerst — für die Diagnoseanzeige. */
    fun recentUrls(): List<String> = synchronized(recent) { recent.toList() }

    /** Beim Seitenwechsel leeren, sonst wandern Funde auf die nächste Seite. */
    fun reset() {
        synchronized(found) { found.clear() }
        synchronized(recent) {
            inspected = 0
            recent.clear()
        }
    }

    private fun note(url: String) {
        synchronized(recent) {
            inspected += 1
            recent.addFirst(url)
            while (recent.size > MAX_RECENT) recent.removeLast()
        }
    }

    private fun record(f: MediaFinding) {
        synchronized(found) {
            // Ersten Fund behalten: Er trägt die vollständigsten Header.
            if (!found.containsKey(f.url)) found[f.url] = f
        }
    }

    private fun classify(url: String): MediaKind? {
        val lower = url.lowercase()
        val path = lower.substringBefore('?').substringBefore('#')
        return when {
            // Segmente zuerst ausschließen: Ein einziger HLS-Stream feuert
            // hunderte davon, und ein Segment allein ist unabspielbar.
            SEGMENT_SUFFIXES.any { path.endsWith(it) } -> null
            path.endsWith(".m3u8") -> MediaKind.HLS
            path.endsWith(".mpd") -> MediaKind.DASH
            PROGRESSIVE_SUFFIXES.any { path.endsWith(it) } -> MediaKind.PROGRESSIVE
            // Der Interceptor sieht nur den Request, nie den Content-Type der
            // Antwort — die Erkennung muss allein aus der URL kommen. Viele
            // Hoster liefern ohne sprechende Endung aus, deshalb zusätzlich
            // charakteristische Pfadmuster.
            HLS_MARKERS.any { it in path } -> MediaKind.HLS
            DASH_MARKERS.any { it in path } -> MediaKind.DASH
            PROGRESSIVE_MARKERS.any { it in lower } -> MediaKind.PROGRESSIVE
            else -> null
        }
    }

    private fun isNoise(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return false
        return AD_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    private fun Map<String, String>.entryIgnoreCase(key: String): String? =
        entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

    private companion object {
        const val MAX_RECENT = 40
        val SEGMENT_SUFFIXES = listOf(".ts", ".m4s", ".m4v-seg", ".aac", ".vtt")
        /** Pfadbestandteile, die einen HLS-Stream verraten, auch ohne Endung. */
        val HLS_MARKERS = listOf("/hls/", "/hls2/", ".urlset", "/playlist", "/master")
        val DASH_MARKERS = listOf("/dash/", "/manifest")
        /** googlevideo & Co. tragen den Typ im Query-String statt im Pfad. */
        val PROGRESSIVE_MARKERS = listOf("videoplayback", "mime=video")
        val PROGRESSIVE_SUFFIXES = listOf(".mp4", ".webm", ".mkv", ".m4v", ".mov", ".avi", ".flv")
        /** Werbe-/Trackingnetze — ihr Preroll lädt vor dem echten Video. */
        val AD_HOSTS = listOf(
            "doubleclick.net",
            "googleadservices.com",
            "googlesyndication.com",
            "imasdk.googleapis.com",
            "adsystem.com",
            "adnxs.com",
            "scorecardresearch.com",
            "moatads.com",
        )
    }
}
