package com.hikari.app.domain.browser

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AdHostsTest {

    @Test
    fun erkenntBekannteAdHostsUndSubdomains() {
        for (url in listOf(
            "https://googleads.g.doubleclick.net/pagead/ads",
            "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            "https://s.lazada.co.th/s.ZRRUaS?t=abc",
            "https://www.popads.net/pop.js",
            "https://syndication.exoclick.com/ads.php",
            "https://adsco.re/click",
        )) {
            assertTrue(AdHosts.isAdUrl(url), "nicht erkannt: $url")
        }
    }

    @Test
    fun laesstNormaleHostsDurch() {
        for (url in listOf(
            "https://aniworld.to/serie/stream/solo-leveling/staffel-2/episode-7",
            "https://voe.sx/e/abc",
            "https://www.google.com/search?q=test",
            "https://cdn.example.com/video.mp4",
        )) {
            assertFalse(AdHosts.isAdUrl(url), "fälschlich blockiert: $url")
        }
    }

    // Kaputte oder hostlose URLs dürfen nicht abstürzen — der Interceptor
    // läuft auf jedem Request der Seite.
    @Test
    fun toleriertUngueltigeUrls() {
        assertFalse(AdHosts.isAdUrl("about:blank"))
        assertFalse(AdHosts.isAdUrl("keine url"))
        assertFalse(AdHosts.isAdHost(null))
    }
}
