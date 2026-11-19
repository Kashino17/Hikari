package com.hikari.app.data.net

import java.io.IOException
import java.nio.file.Files
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(okhttp3.ExperimentalOkHttpApi::class)
class OfflineResponseCacheTest {
    private lateinit var server: MockWebServer
    private val dir = Files.createTempDirectory("offline").toFile()
    private val cache = OfflineResponseCache(dir, OfflineResponseCache.API_PATHS)
    private val client = OkHttpClient.Builder().addInterceptor(cache.interceptor()).build()

    @Before fun up() { server = MockWebServer(); server.start() }
    @After fun down() { runCatching { server.close() } }

    private fun get(path: String) = client.newCall(Request.Builder().url(server.url(path)).build()).execute()

    @Test fun `online geladen, offline dieselbe Antwort`() {
        server.enqueue(MockResponse.Builder().body("""[{"id":"s"}]""").addHeader("Content-Type", "application/json").build())
        assertEquals("""[{"id":"s"}]""", get("/api/manga/series").body.string())
        server.close() // Mac weg
        val offline = get("/api/manga/series")
        assertEquals(200, offline.code)
        assertEquals("""[{"id":"s"}]""", offline.body.string())
        assertEquals("1", offline.header("X-Hikari-Offline"))
    }

    @Test fun `Abfrageparameter gehören zum Schlüssel`() {
        server.enqueue(MockResponse.Builder().body("de").build())
        server.enqueue(MockResponse.Builder().body("en").build())
        get("/api/manga/catalog/home?lang=de").body.string()
        get("/api/manga/catalog/home?lang=en").body.string()
        server.close()
        assertEquals("de", get("/api/manga/catalog/home?lang=de").body.string())
        assertEquals("en", get("/api/manga/catalog/home?lang=en").body.string())
    }

    @Test fun `Leser-Seiten und unbekannte Pfade werden nicht gemerkt`() {
        assertFalse(cache.covers("/api/manga/chapters/x/pages"))
        assertFalse(cache.covers("/api/manga/sync/jobs"))
        assertFalse(cache.covers("/api/ebooks/b/file"))
        assertTrue(cache.covers("/api/manga/series/mangadex:abc"))
        assertTrue(cache.covers("/api/ebooks/home"))
        server.enqueue(MockResponse.Builder().body("seiten").build())
        get("/api/manga/chapters/x/pages").body.string()
        server.close()
        var failed = false
        try { get("/api/manga/chapters/x/pages") } catch (e: IOException) { failed = true }
        assertTrue(failed)
    }

    @Test fun `Fehlerantworten überschreiben den gemerkten Stand nicht`() {
        server.enqueue(MockResponse.Builder().body("gut").build())
        server.enqueue(MockResponse.Builder().code(500).body("kaputt").build())
        get("/api/manga/continue").body.string()
        get("/api/manga/continue").body.string()
        server.close()
        assertEquals("gut", get("/api/manga/continue").body.string())
    }
}
