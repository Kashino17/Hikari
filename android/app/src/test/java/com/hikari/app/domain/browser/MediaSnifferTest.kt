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
}
