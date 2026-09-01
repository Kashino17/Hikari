package com.hikari.app.domain.browser

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class MediaSnifferTest {

    private fun sniffer() = MediaSniffer()

    @Test
    fun erkenntProgressiveMp4() {
        val s = sniffer()
        s.onRequest("https://cdn.example/video/folge1.mp4", emptyMap())
        val found = s.findings()
        assertEquals(1, found.size)
        assertEquals(MediaKind.PROGRESSIVE, found[0].kind)
    }

    @Test
    fun erkenntHlsPlaylist() {
        val s = sniffer()
        s.onRequest("https://cdn.example/hls/master.m3u8", emptyMap())
        assertEquals(MediaKind.HLS, s.findings()[0].kind)
    }

    @Test
    fun erkenntDashManifest() {
        val s = sniffer()
        s.onRequest("https://cdn.example/dash/manifest.mpd", emptyMap())
        assertEquals(MediaKind.DASH, s.findings()[0].kind)
    }

    @Test
    fun erkenntMediaAnContentType() {
        val s = sniffer()
        // Viele Hoster liefern den Stream ohne sprechende Endung aus.
        s.onResponse("https://cdn.example/stream/abc123?token=xyz", "video/mp4")
        assertEquals(MediaKind.PROGRESSIVE, s.findings()[0].kind)
    }

    // Segmente sind das lauteste Rauschen im Interceptor: Ein einziger HLS-Stream
    // feuert hunderte .ts-Requests. Aufnehmen dürfen wir davon keinen einzigen —
    // ein Segment allein ist unabspielbar.
    @Test
    fun ignoriertHlsSegmente() {
        val s = sniffer()
        s.onRequest("https://cdn.example/hls/seg-0001.ts", emptyMap())
        s.onRequest("https://cdn.example/hls/seg-0002.ts", emptyMap())
        s.onRequest("https://cdn.example/hls/init.m4s", emptyMap())
        assertTrue(s.findings().isEmpty())
    }

    @Test
    fun ignoriertNichtMedien() {
        val s = sniffer()
        for (u in listOf(
            "https://example.com/app.js",
            "https://example.com/style.css",
            "https://example.com/bild.jpg",
            "https://example.com/seite.html",
        )) s.onRequest(u, emptyMap())
        assertTrue(s.findings().isEmpty())
    }

    // Werbe-Preroll ist der häufigste Fehlgriff: Er lädt zuerst und würde ohne
    // Filter als "das Video dieser Seite" gelten.
    @Test
    fun filtertWerbeUndTrackingHosts() {
        val s = sniffer()
        s.onRequest("https://googleads.g.doubleclick.net/pagead/ads/spot.mp4", emptyMap())
        s.onRequest("https://imasdk.googleapis.com/preroll.mp4", emptyMap())
        // Die Ad-Kurzlinks, die das Hauptfenster umleiten, laden mitunter
        // selbst Werbevideos — auch die dürfen nicht als Fund landen.
        s.onRequest("https://s.lazada.co.th/s.ZRRUaS/werbespot.mp4", emptyMap())
        s.onRequest("https://cdn.example/echte-folge.mp4", emptyMap())
        val found = s.findings()
        assertEquals(1, found.size)
        assertTrue(found[0].url.contains("echte-folge"))
    }

    @Test
    fun dedupliziertGleicheUrl() {
        val s = sniffer()
        repeat(5) { s.onRequest("https://cdn.example/folge.mp4", emptyMap()) }
        assertEquals(1, s.findings().size)
    }

    // Referer und Cookie entscheiden darüber, ob der Server den Download später
    // überhaupt beantwortet — Filehoster prüfen beides.
    @Test
    fun merktSichRefererUndCookieAusDenRequestHeadern() {
        val s = sniffer()
        s.onRequest(
            "https://cdn.example/folge.mp4",
            mapOf("Referer" to "https://voe.sx/e/abc", "Cookie" to "sid=42"),
        )
        val f = s.findings()[0]
        assertEquals("https://voe.sx/e/abc", f.referer)
        assertEquals("sid=42", f.cookie)
    }

    @Test
    fun besterFundBevorzugtPlaylistVorProgressiv() {
        val s = sniffer()
        s.onRequest("https://cdn.example/niedrig.mp4", emptyMap())
        s.onRequest("https://cdn.example/master.m3u8", emptyMap())
        // Eine HLS-Playlist enthält alle Qualitätsstufen; eine einzelne
        // progressive Datei ist meist die kleinste Variante.
        assertEquals(MediaKind.HLS, s.best()?.kind)
    }

    @Test
    fun besterFundIstNullOhneTreffer() {
        assertNull(sniffer().best())
    }

    @Test
    fun zuruecksetzenBeimSeitenwechsel() {
        val s = sniffer()
        s.onRequest("https://cdn.example/folge.mp4", emptyMap())
        s.reset()
        assertTrue(s.findings().isEmpty())
    }

    // Der Interceptor kennt nur den Request, nie den Content-Type der Antwort —
    // die Erkennung muss also allein aus der URL kommen. Viele Hoster liefern
    // ihre Streams ohne sprechende Endung aus, weshalb Pfadmuster zusaetzlich
    // zu Endungen geprueft werden.
    @Test
    fun erkenntStreamsOhneDateiendungAmPfadmuster() {
        val faelle = listOf(
            "https://delivery.voe-network.net/engine/hls2/01/00123/abc_,n,.urlset/master.txt",
            "https://cdn.example/hls/playlist?token=abc",
            "https://cdn.example/manifest/video.f4m?x=1",
            "https://rr3---sn-x.googlevideo.com/videoplayback?expire=123&mime=video%2Fmp4",
        )
        for (u in faelle) {
            val s = sniffer()
            s.onRequest(u, emptyMap())
            assertTrue(s.findings().isNotEmpty(), "nicht erkannt: $u")
        }
    }

    @Test
    fun zaehltAlleGesehenenRequestsFuerDieDiagnose() {
        val s = sniffer()
        s.onRequest("https://example.com/app.js", emptyMap())
        s.onRequest("https://example.com/style.css", emptyMap())
        s.onRequest("https://cdn.example/folge.mp4", emptyMap())
        assertEquals(3, s.inspectedCount())
        assertEquals(1, s.findings().size)
    }

    // Ohne diese Liste ist bei "es wird nichts erkannt" nicht zu unterscheiden,
    // ob der Interceptor gar nicht laeuft oder ob der Filter zu streng ist.
    @Test
    fun merktSichDieZuletztGesehenenUrls() {
        val s = sniffer()
        for (i in 1..5) s.onRequest("https://example.com/datei$i.js", emptyMap())
        val recent = s.recentUrls()
        assertTrue(recent.isNotEmpty())
        assertTrue(recent.first().contains("datei5"), "neueste zuerst: $recent")
    }

    @Test
    fun zaehlerUeberlebtSeitenwechselNicht() {
        val s = sniffer()
        s.onRequest("https://example.com/a.js", emptyMap())
        s.reset()
        assertEquals(0, s.inspectedCount())
    }
}
