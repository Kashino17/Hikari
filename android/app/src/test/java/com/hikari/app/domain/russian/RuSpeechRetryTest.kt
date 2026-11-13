package com.hikari.app.domain.russian

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class RuSpeechRetryTest {
    @Test fun netzUndServerfehlerDirektNachDemStartWerdenStillWiederholt() {
        // Der Fall aus der Praxis: zweiter Versuch, sofort »Netzwerk« (2) oder »Server« (4).
        for (code in listOf(1, 2, 4, 5)) {
            assertTrue(RuSpeechRetry.shouldRetry(code, elapsedMs = 300, retries = 0), "Code $code")
        }
    }

    @Test fun belegtUndVerbindungVerlorenWerdenImmerWiederholt() {
        for (code in listOf(8, 10, 11)) {
            assertTrue(RuSpeechRetry.shouldRetry(code, elapsedMs = 9_000, retries = 0), "Code $code")
        }
    }

    @Test fun spaeterNetzfehlerIstEinEchterAusfall() {
        assertFalse(RuSpeechRetry.shouldRetry(2, elapsedMs = 6_000, retries = 0))
        assertFalse(RuSpeechRetry.shouldRetry(1, elapsedMs = 12_000, retries = 0))
    }

    @Test fun nachDreiAnlaeufenWirdGemeldet() {
        assertTrue(RuSpeechRetry.shouldRetry(2, 200, retries = 2))
        assertFalse(RuSpeechRetry.shouldRetry(2, 200, retries = 3))
        assertFalse(RuSpeechRetry.shouldRetry(11, 200, retries = 3))
    }

    @Test fun andereFehlerWerdenNichtWiederholt() {
        // Nichts gehört (6, 7), Berechtigung fehlt (9), Sprache nicht installiert (12, 13).
        for (code in listOf(3, 6, 7, 9, 12, 13)) {
            assertFalse(RuSpeechRetry.shouldRetry(code, elapsedMs = 100, retries = 0), "Code $code")
        }
    }

    @Test fun pauseWaechstMitJedemAnlauf() {
        assertEquals(listOf(350L, 650L, 950L), (0..2).map(RuSpeechRetry::delayMs))
    }

    // ── Geduld ──────────────────────────────────────────────────────────────

    @Test fun gibtDieErkennungOhneTextAufWirdWeitergewartet() {
        // Vom Handy (13.11.): Jede Sitzung endete nach rund 2 s. Wer da noch liest oder
        // nachdenkt, bekam »Ich habe nichts gehört«, ohne je gesprochen zu haben.
        for (code in listOf(6, 7)) {
            assertTrue(RuSpeechRetry.shouldKeepWaiting(code, sinceTapMs = 2_100, reopened = 0), "Code $code")
        }
        assertTrue(RuSpeechRetry.shouldKeepWaiting(7, sinceTapMs = 9_000, reopened = 3))
    }

    @Test fun nachDerWartezeitWirdGemeldet() {
        assertFalse(RuSpeechRetry.shouldKeepWaiting(7, sinceTapMs = RuSpeechRetry.PATIENCE_MS, reopened = 4))
        assertFalse(RuSpeechRetry.shouldKeepWaiting(6, sinceTapMs = 3_000, reopened = RuSpeechRetry.MAX_REOPEN))
    }

    @Test fun echteFehlerWartenNicht() {
        // Berechtigung, belegter Dienst, fehlendes Sprachpaket: dafür gibt es eigene Wege und Meldungen.
        for (code in listOf(5, 8, 9, 10, 11, 12, 13)) {
            assertFalse(RuSpeechRetry.shouldKeepWaiting(code, sinceTapMs = 500, reopened = 0), "Code $code")
        }
    }

    @Test fun haengtDasNetzWirdInDerWartezeitNeuGeoeffnet() {
        // Vom Handy (13.11., 23:15): »ONLINE_NO_PROGRESS« nach 5 s → Code 2. Zu spät für
        // shouldRetry, früher flog man damit aus der KI-Prüfung.
        assertFalse(RuSpeechRetry.shouldRetry(2, elapsedMs = 5_023, retries = 0))
        for (code in listOf(1, 2, 4)) {
            assertTrue(RuSpeechRetry.shouldKeepWaiting(code, sinceTapMs = 5_023, reopened = 0), "Code $code")
        }
        assertFalse(RuSpeechRetry.shouldKeepWaiting(2, sinceTapMs = 12_500, reopened = 1))
    }

    @Test fun ohneSprachpaketBleibtNurDasInternet() {
        assertFalse(RuSpeechRetry.nextOnDevice(wasOnDevice = false, packInstalled = false, networkError = false))
        assertFalse(RuSpeechRetry.nextOnDevice(wasOnDevice = false, packInstalled = false, networkError = true))
    }

    @Test fun haengtDasNetzUebernimmtDasGeraet() {
        assertTrue(RuSpeechRetry.nextOnDevice(wasOnDevice = false, packInstalled = true, networkError = true))
    }

    @Test fun ohneNetzBleibtEsBeimGeraet() {
        // Im Flugmodus hörte der Internet-Erkenner sonst sekundenlang ins Leere.
        assertTrue(RuSpeechRetry.nextOnDevice(wasOnDevice = true, packInstalled = true, networkError = false, online = false))
    }

    @Test fun nichtsVerstandenDannVersuchtEsDerAndereErkenner() {
        assertFalse(RuSpeechRetry.nextOnDevice(wasOnDevice = true, packInstalled = true, networkError = false))
        assertTrue(RuSpeechRetry.nextOnDevice(wasOnDevice = false, packInstalled = true, networkError = false))
    }

    @Test fun fehlendesSprachpaketWirdErkannt() {
        assertTrue(RuSpeechRetry.isPackMissing(12))
        assertTrue(RuSpeechRetry.isPackMissing(13))
        assertFalse(RuSpeechRetry.isPackMissing(7))
        assertTrue(listOf(1, 2, 4).all(RuSpeechRetry::isNetwork))
        assertFalse(listOf(5, 6, 7, 8, 13).any(RuSpeechRetry::isNetwork))
    }

    @Test fun stimmeWirdAmPegelErkannt() {
        // Entscheidet nur den Wortlaut: »nichts gehört« oder »gehört, aber nicht verstanden«.
        assertFalse(RuSpeechRetry.heard(Float.NEGATIVE_INFINITY)) // kein einziger Pegel gemeldet
        assertFalse(RuSpeechRetry.heard(0.4f)) // Stille
        assertFalse(RuSpeechRetry.heard(3.4f)) // Raumgeräusch (im Emulator gemessen)
        assertTrue(RuSpeechRetry.heard(8f)) // Stimme nah am Mikrofon
    }
}
