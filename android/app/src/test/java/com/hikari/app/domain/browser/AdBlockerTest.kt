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

    @Test
    fun popupsNurMitGesteUndNieAufAdHosts() {
        assertTrue(blocker.shouldBlockPopup("https://foo.com/", hasGesture = false))
        assertFalse(blocker.shouldBlockPopup("https://foo.com/", hasGesture = true))
        assertTrue(blocker.shouldBlockPopup("https://s.lazada.co.th/s.x", hasGesture = true))
    }

    @Test
    fun appSprungUndAdRedirectsWerdenGestoppt() {
        assertTrue(blocker.shouldBlockNavigation("intent://scan/#Intent;scheme=zxing;end", true, false))
        assertTrue(blocker.shouldBlockNavigation("market://details?id=x", false, false))
        assertTrue(blocker.shouldBlockNavigation("https://exoclick.com/c", hasGesture = false, intended = false))
        assertFalse(blocker.shouldBlockNavigation("https://exoclick.com/c", hasGesture = true, intended = false))
        assertFalse(blocker.shouldBlockNavigation("https://voe.sx/e/abc", hasGesture = false, intended = false))
    }

    @Test
    fun deaktiviertBlocktNichts() {
        blocker.enabled = false
        assertFalse(blocker.shouldBlockRequest("https://doubleclick.net/x", false, false))
        assertFalse(blocker.shouldBlockPopup("https://foo.com/", false))
        assertFalse(blocker.shouldBlockNavigation("intent://x", false, false))
    }
}
