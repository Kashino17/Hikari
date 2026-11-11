package com.hikari.app.domain.cast

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class CastRemoteTest {
    private val episode = CastMedia(
        CastKind.VIDEO, "v1", "Die magischen Freuden von Stevie Nicks",
        owner = "American Horror Story", episode = "S3 · F10",
    )
    private val film = CastMedia(CastKind.VIDEO, "v2", "Interstellar")
    private val song = CastMedia(CastKind.MUSIC, "m1", "Bohemian Rhapsody", owner = "Queen")

    @Test fun leisteZeigtBeiFolgenDieSerieUndDarunterFolgeUndGeraet() {
        assertEquals("American Horror Story", CastRemote.barTitle(episode))
        assertEquals("S3 · F10 · Wohnzimmer", CastRemote.barLine(episode, "Wohnzimmer"))
    }

    @Test fun leisteZeigtBeiFilmenDenTitelUndNurDasGeraet() {
        assertEquals("Interstellar", CastRemote.barTitle(film))
        assertEquals("Wohnzimmer", CastRemote.barLine(film, "Wohnzimmer"))
        assertEquals("Fernseher", CastRemote.barLine(film, null))
    }

    @Test fun leisteZeigtBeiMusikLiedUndInterpret() {
        assertEquals("Bohemian Rhapsody", CastRemote.barTitle(song))
        assertEquals("Queen · Wohnzimmer", CastRemote.barLine(song, "Wohnzimmer"))
    }

    @Test fun steuerungNenntSerieUndFolgeUeberDemTitel() {
        assertEquals("American Horror Story · S3 · F10", CastRemote.remoteLine(episode))
        assertEquals("Queen", CastRemote.remoteLine(song))
        assertNull(CastRemote.remoteLine(film))
    }

    @Test fun folgenAngabe() {
        assertEquals("S3 · F10", CastRemote.episodeLabel(3, 10))
        assertEquals("Folge 7", CastRemote.episodeLabel(null, 7))
        assertNull(CastRemote.episodeLabel(2, null))
    }

    @Test fun spruengeBleibenImVideo() {
        assertEquals(0L, CastRemote.seekTarget(30_000, -60_000, 2_700_000))
        assertEquals(2_700_000L, CastRemote.seekTarget(2_600_000, 300_000, 2_700_000))
        assertEquals(70_000L, CastRemote.seekTarget(60_000, 10_000, 2_700_000))
        // Länge noch unbekannt: nach vorn nicht begrenzen.
        assertEquals(360_000L, CastRemote.seekTarget(60_000, 300_000, 0))
    }

    @Test fun ersterSpulbefehlGehtSofortRausWeitereWerdenGesammelt() {
        // Lange nichts gesendet: sofort.
        assertEquals(0L, CastRemote.seekDelay(nowMs = 10_000, lastSentMs = 2_000))
        // Gerade eben gesendet: bis zum Mindestabstand warten — Tipps dazwischen sammeln sich im Ziel.
        assertEquals(350L, CastRemote.seekDelay(nowMs = 10_100, lastSentMs = 10_000))
        assertEquals(0L, CastRemote.seekDelay(nowMs = 10_450, lastSentMs = 10_000))
        // Uhr springt zurück (Neustart): nie länger als der Mindestabstand warten.
        assertEquals(CastRemote.SEEK_GAP_MS, CastRemote.seekDelay(nowMs = 0, lastSentMs = 99_000))
    }

    @Test fun sprungKnoepfeSindBeschriftet() {
        assertEquals(listOf("− 5 min", "− 1 min", "+ 1 min", "+ 5 min"), CastRemote.JUMPS_MS.map(CastRemote::jumpLabel))
    }

    @Test fun zeiten() {
        assertEquals("12:40", CastRemote.clock(760_000))
        assertEquals("1:02:03", CastRemote.clock(3_723_000))
        assertEquals("noch 34:31", CastRemote.remaining(760_000, 2_831_000))
        assertEquals("", CastRemote.remaining(760_000, 0))
        assertEquals(0f, CastRemote.progress(10, 0))
        assertEquals(0.5f, CastRemote.progress(50, 100))
        assertEquals(1f, CastRemote.progress(500, 100))
    }

    @Test fun fastZuEndeGesehenesBeginntVonVorn() {
        assertEquals(760_000L, CastRemote.resumePoint(760_000, 2_831_000))
        assertEquals(0L, CastRemote.resumePoint(2_829_000, 2_831_000))
        assertEquals(760_000L, CastRemote.resumePoint(760_000, 0))
    }

    @Test fun nurLanAdressenErreichtDerFernseher() {
        assertTrue(CastRemote.isLanHost("192.168.178.20"))
        assertTrue(CastRemote.isLanHost("10.0.0.5"))
        assertTrue(CastRemote.isLanHost("macbook.local"))
        assertFalse(CastRemote.isLanHost("100.125.192.44"))
        assertFalse(CastRemote.isLanHost("macbook-pro.taile64a95.ts.net"))
    }

    @Test fun adresseAmFernseherVerraetVideoOderMusik() {
        assertEquals(CastKind.VIDEO to "sniff_1", CastRemote.parseStreamUrl("http://192.168.178.20:3939/stream/video/sniff_1"))
        assertEquals(CastKind.MUSIC to "dQw4w9WgXcQ", CastRemote.parseStreamUrl("http://192.168.178.20:3939/music/audio/dQw4w9WgXcQ?x=1"))
        assertNull(CastRemote.parseStreamUrl("http://192.168.178.20:3939/stream/video/"))
        assertNull(CastRemote.parseStreamUrl("https://example.org/film.mp4"))
    }
}
