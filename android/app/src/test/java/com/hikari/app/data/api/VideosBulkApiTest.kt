package com.hikari.app.data.api

import com.hikari.app.data.api.dto.AnalyzeRequest
import com.hikari.app.data.api.dto.BulkImportItem
import com.hikari.app.data.api.dto.BulkImportRequest
import com.hikari.app.data.api.dto.ImportItemMetadata
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideosBulkApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: HikariApi

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(HikariApi::class.java)
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun analyzeVideo_parsesAiMeta() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"url":"https://x.test/abc","title":"Auto Title","description":"d",
             "thumbnailUrl":"https://x.test/t.jpg",
             "aiMeta":{"seriesTitle":"X","season":1,"episode":7,
                       "dubLanguage":"de","subLanguage":null,"isMovie":false}}
        """.trimIndent()))
        val r = api.analyzeVideo(AnalyzeRequest("https://x.test/abc"))
        assertEquals("Auto Title", r.title)
        assertEquals("X", r.aiMeta?.seriesTitle)
        assertEquals(1, r.aiMeta?.season)
        assertEquals(7, r.aiMeta?.episode)
    }

    @Test fun importVideosBulk_serializesPerItemMetadata() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"queued":2}"""))
        val r = api.importVideosBulk(BulkImportRequest(listOf(
            BulkImportItem(url = "https://x.test/1"),
            BulkImportItem(
                url = "https://x.test/2",
                metadata = ImportItemMetadata(
                    title = "Custom Title",
                    seriesTitle = "One Piece",
                    season = 1, episode = 7,
                    dubLanguage = "de", subLanguage = null,
                ),
            ),
        )))
        assertEquals(2, r.queued)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path?.contains("/videos/import/bulk") == true)
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"url\":\"https://x.test/1\""))
        assertTrue(body.contains("\"title\":\"Custom Title\""))
        assertTrue(body.contains("\"seriesTitle\":\"One Piece\""))
        assertTrue(body.contains("\"episode\":7"))
    }

    @Test fun listSeries_parsesArray() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            [{"id":"s1","title":"One Piece"},{"id":"s2","title":"Naruto"}]
        """.trimIndent()))
        val list = api.listSeries()
        assertEquals(2, list.size)
        assertEquals("One Piece", list[0].title)
    }
}
