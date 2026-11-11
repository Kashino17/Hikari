package com.hikari.app.domain.browser

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AdBlockerTest {

    private val blocker = AdBlocker()

    @Test
    fun blockiertAdHostsUndWerbePfade() {
        assertTrue(blocker.shouldBlockRequest("https://pagead2.googlesyndication.com/x.js", false, false))
        assertTrue(blocker.shouldBlockRequest("https://static.foo.com/ads/banner/300x250.gif", false, false))
        assertTrue(blocker.shouldBlockRequest("https://foo.com/js/popads.js", false, false))
        assertEquals(3, blocker.blockedCount)
    }

    @Test
    fun laesstNormaleSeitenUndUploadsDurch() {
        assertFalse(blocker.shouldBlockRequest("https://aniworld.to/anime/stream/x/staffel-1", true, false))
        assertFalse(blocker.shouldBlockRequest("https://foo.com/uploads/cover.jpg", false, false))
        assertFalse(blocker.shouldBlockRequest("https://foo.com/downloads/list", false, false))
        assertEquals(0, blocker.blockedCount)
    }

    @Test
    fun streamsWerdenNieBlockiert() {
        assertFalse(blocker.shouldBlockRequest("https://cdn.exoclick.com/ads/video/master.m3u8?t=1", false, false))
        assertFalse(blocker.shouldBlockRequest("https://x.com/ads/seg-1.ts", false, false))
    }

    @Test
    fun absichtlicherHauptframeAufrufGehtDurch() {
        assertFalse(blocker.shouldBlockRequest("https://www.popads.net/", true, true))
        assertTrue(blocker.shouldBlockRequest("https://www.popads.net/", true, false))
    }

    // ── Pop-ups ──────────────────────────────────────────────────────────────

    private val serie = "https://s.to/serie/stream/dark/staffel-1/episode-1"

    @Test
    fun popupOhneFingertippOderAufWerbeHostWirdStummGesperrt() {
        assertEquals(NavVerdict.BLOCK, blocker.judgePopup("https://foo.com/", serie, hasGesture = false, tap = Tap.Other))
        assertEquals(NavVerdict.BLOCK, blocker.judgePopup("https://s.lazada.co.th/s.x", serie, true, Tap.ImageLink))
        assertEquals(NavVerdict.BLOCK, blocker.judgePopup("intent://scan/#Intent;end", serie, true, Tap.Other))
        assertEquals(3, blocker.blockedCount)
    }

    @Test
    fun popupAufFremdeSeiteWirdGesperrtUndAngeboten_auchMitFingertipp() {
        // Pop-under hängen sich an jeden Tipp — auch an den auf einen echten Link.
        val unbekannt = "https://brandneue-werbedomain.xyz/lp?id=7"
        assertEquals(NavVerdict.OFFER, blocker.judgePopup(unbekannt, serie, true, Tap.Other))
        assertEquals(NavVerdict.OFFER, blocker.judgePopup(unbekannt, serie, true, Tap.TextLink(unbekannt)))
    }

    @Test
    fun popupAufDieselbeSeiteIstErlaubt() {
        assertEquals(NavVerdict.ALLOW, blocker.judgePopup("https://s.to/redirect/123", serie, true, Tap.ImageLink))
        assertEquals(NavVerdict.ALLOW, blocker.judgePopup("https://www.s.to/redirect/123", serie, true, Tap.Other))
    }

    // ── Seitenwechsel ────────────────────────────────────────────────────────

    private fun nav(
        url: String,
        from: String? = serie,
        gesture: Boolean = false,
        redirect: Boolean = false,
        intended: Boolean = false,
        tap: Tap = Tap.Other,
        now: Long = 60_000,
    ) = blocker.judgeNavigation(url, from, gesture, redirect, intended, tap, now)

    @Test
    fun appSpruengeWerdenGestoppt_interneAdressenNicht() {
        assertEquals(NavVerdict.BLOCK, nav("intent://scan/#Intent;scheme=zxing;end", gesture = true))
        assertEquals(NavVerdict.BLOCK, nav("market://details?id=x"))
        assertEquals(NavVerdict.ALLOW, nav("about:blank"))
    }

    @Test
    fun innerhalbDerSeiteUndBewusstAngesteuertGehtImmer() {
        assertEquals(NavVerdict.ALLOW, nav("https://s.to/serie/stream/dark/staffel-1/episode-2"))
        assertEquals(NavVerdict.ALLOW, nav("https://exoclick.com/c", intended = true))
        assertEquals(NavVerdict.ALLOW, nav("https://fremd.de/", from = null))
    }

    @Test
    fun angetippterLinkZuFremderSeiteGehtDurch() {
        val ziel = "https://voe.sx/e/abc"
        assertEquals(NavVerdict.ALLOW, nav(ziel, gesture = true, tap = Tap.TextLink(ziel)))
        assertEquals(NavVerdict.ALLOW, nav(ziel, gesture = true, tap = Tap.TextLink("https://www.voe.sx/")))
        assertEquals(NavVerdict.ALLOW, nav(ziel, gesture = true, tap = Tap.ImageLink))
    }

    @Test
    fun tippAufIrgendetwasDerWoandershinFuehrtIstEineKlickFalle() {
        val werbung = "https://brandneue-werbedomain.xyz/lp"
        // Tipp auf den Player (kein Link) …
        assertEquals(NavVerdict.OFFER, nav(werbung, gesture = true, tap = Tap.Other))
        // … oder auf einen Link, der eigentlich woandershin zeigt.
        assertEquals(NavVerdict.OFFER, nav(werbung, gesture = true, tap = Tap.TextLink("https://s.to/serie/stream/dark")))
    }

    @Test
    fun werbeHostWirdAuchMitFingertippGesperrt() {
        assertEquals(NavVerdict.BLOCK, nav("https://exoclick.com/c"))
        assertEquals(NavVerdict.BLOCK, nav("https://exoclick.com/c", gesture = true, tap = Tap.Other))
        // Wirklich angetippter Link dorthin: gesperrt, aber angeboten.
        assertEquals(NavVerdict.OFFER, nav("https://exoclick.com/c", gesture = true, tap = Tap.TextLink("https://exoclick.com/c")))
    }

    @Test
    fun frischGeladeneUnberuehrteSeiteDarfWeiterleiten_spaeterOderNachBeruehrungNicht() {
        val spiegel = "https://jilliandescribecompany.com/e/abc"
        blocker.onPageStarted(nowMs = 10_000)
        assertEquals(NavVerdict.ALLOW, nav(spiegel, from = "https://voe.sx/e/abc", now = 11_500))
        // Dieselbe Weiterleitung, nachdem der Nutzer die Seite angefasst hat:
        blocker.onTouch()
        assertEquals(NavVerdict.OFFER, nav(spiegel, from = "https://voe.sx/e/abc", now = 12_000))
        // … oder lange nach dem Laden, ohne Berührung (Zeitschalter-Werbung):
        blocker.onPageStarted(nowMs = 20_000)
        assertEquals(NavVerdict.OFFER, nav(spiegel, from = "https://voe.sx/e/abc", now = 20_000 + AdBlocker.AUTO_FORWARD_MS + 1))
    }

    @Test
    fun weiterleitungenFolgenDerKette_dieDerNutzerBegonnenHat() {
        val hoster = "https://voe.sx/e/abc"
        // Angetippter Hoster-Link auf der Serienseite (eigene Adresse), der Server leitet weiter.
        assertEquals(
            NavVerdict.ALLOW,
            nav("https://s.to/redirect/123", gesture = true, tap = Tap.TextLink("https://s.to/redirect/123")),
        )
        assertEquals(NavVerdict.ALLOW, nav(hoster, redirect = true, tap = Tap.TextLink("https://s.to/redirect/123")))

        // Dieselbe Weiterleitung, aber angestoßen von einem Skript statt von einem Link:
        assertEquals(NavVerdict.ALLOW, nav("https://s.to/out/7", gesture = true, tap = Tap.Other))
        assertEquals(NavVerdict.OFFER, nav("https://brandneue-werbedomain.xyz/", redirect = true))
        assertEquals(NavVerdict.BLOCK, nav("https://exoclick.com/c", redirect = true))
    }

    @Test
    fun suchtrefferUeberDieEigeneAdresseDerSuchmaschineKommtAn() {
        val google = "https://www.google.com/search?q=dark"
        val treffer = "https://www.netflix.com/title/80100172"
        // Der Tipp galt dem Treffer, geladen wird zuerst die Zähl-Adresse der Suchmaschine.
        assertEquals(
            NavVerdict.ALLOW,
            nav("https://www.google.com/url?q=x", from = google, gesture = true, tap = Tap.TextLink(treffer)),
        )
        assertEquals(NavVerdict.ALLOW, nav(treffer, from = google, redirect = true, tap = Tap.TextLink(treffer)))
    }

    @Test
    fun nachOeffnenAusDemHinweisGiltDieKetteAlsGewollt() {
        assertEquals(NavVerdict.ALLOW, nav("https://s.to/out/7", gesture = true, tap = Tap.Other))
        blocker.trustNextChain()
        assertEquals(NavVerdict.ALLOW, nav("https://ziel.example/", from = "https://kurz.link/x", redirect = true))
    }

    @Test
    fun dieselbeSeiteHeisstDieselbeHauptdomain() {
        assertEquals("s.to", Sites.siteOf("https://www.s.to/x"))
        assertEquals("lazada.co.th", Sites.siteOf("https://s.lazada.co.th/s.x"))
        assertEquals("192.168.1.142", Sites.siteOf("http://192.168.1.142:3939/health"))
        assertTrue(Sites.sameSite("https://cdn.voe.sx/a", "https://voe.sx/e/abc"))
        assertFalse(Sites.sameSite("https://voe.sx/", "https://voe.network/"))
        assertFalse(Sites.sameSite("about:blank", "about:blank"))
    }

    @Test
    fun deaktiviertBlocktNichts() {
        blocker.enabled = false
        assertFalse(blocker.shouldBlockRequest("https://doubleclick.net/x", false, false))
        assertEquals(NavVerdict.ALLOW, blocker.judgePopup("https://foo.com/", serie, false, Tap.Other))
        assertEquals(NavVerdict.ALLOW, nav("intent://x"))
    }
}
