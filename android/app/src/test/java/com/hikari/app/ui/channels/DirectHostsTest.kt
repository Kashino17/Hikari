package com.hikari.app.ui.channels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectHostsTest {

    @Test
    fun `YouTube und Co brauchen keinen Seitenbesuch`() {
        assertTrue(DirectHosts.isWellSupported("https://www.youtube.com/watch?v=abc"))
        assertTrue(DirectHosts.isWellSupported("https://youtu.be/abc"))
        assertTrue(DirectHosts.isWellSupported("https://m.youtube.com/watch?v=abc"))
        assertTrue(DirectHosts.isWellSupported("https://x.com/user/status/1"))
    }

    @Test
    fun `Filehoster und Streaming-Seiten werden gesnifft`() {
        assertFalse(DirectHosts.isWellSupported("https://voe.sx/e/abc123"))
        assertFalse(DirectHosts.isWellSupported("https://aniworld.to/anime/stream/x/staffel-1/episode-1"))
        assertFalse(DirectHosts.isWellSupported("https://notyoutube.com/watch"))
        assertFalse(DirectHosts.isWellSupported("kein link"))
    }
}
