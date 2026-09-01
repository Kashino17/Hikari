package com.hikari.app.domain.browser

import kotlin.test.assertTrue
import org.junit.Test

// Das Scan-Script läuft erst im WebView; was sich hier prüfen lässt, ist der
// Vertrag, den das Script mit parseScan eingeht.
class PageScriptsTest {

    @Test
    fun scanLiestOgDescriptionMitFallback() {
        assertTrue(PageScripts.SCAN.contains("""meta[property="og:description"]"""))
        assertTrue(PageScripts.SCAN.contains("""meta[name="description"]"""))
        assertTrue(PageScripts.SCAN.contains("description:"))
    }
}
