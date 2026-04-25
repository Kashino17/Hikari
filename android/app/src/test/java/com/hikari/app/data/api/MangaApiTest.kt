package com.hikari.app.data.api

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

class MangaApiTest {
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

    @Test fun listMangaSeries_parsesBackendShape() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            [{"id":"onepiecetube:one-piece","source":"onepiecetube","title":"One Piece",
              "author":null,"description":null,"coverPath":null,
              "totalChapters":762,"lastSyncedAt":1777148725270}]
        """.trimIndent()))
        val list = api.listMangaSeries()
        assertEquals(1, list.size)
        assertEquals("One Piece", list[0].title)
        assertEquals(762, list[0].totalChapters)
    }

    @Test fun getMangaSeries_parsesArcsAndChapters() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"id":"onepiecetube:one-piece","source":"onepiecetube","title":"One Piece",
             "author":"Eiichiro Oda","totalChapters":2,
             "arcs":[{"id":"arc-1","title":"East Blue","arcOrder":0,
                      "chapterStart":1,"chapterEnd":2}],
             "chapters":[
               {"id":"ch-1","number":1.0,"title":"Romance Dawn","arcId":"arc-1","pageCount":50,"isRead":1},
               {"id":"ch-2","number":2.0,"title":"Strawhat","arcId":"arc-1","pageCount":20,"isRead":0}
             ]}
        """.trimIndent()))
        val d = api.getMangaSeries("onepiecetube:one-piece")
        assertEquals(1, d.arcs.size)
        assertEquals(2, d.chapters.size)
        assertEquals(1.0, d.chapters[0].number, 0.0001)
        assertEquals(1, d.chapters[0].isRead)
    }

    @Test fun getMangaChapterPages_parsesReadyFlag() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            [{"id":"p1","pageNumber":1,"ready":true},
             {"id":"p2","pageNumber":2,"ready":false}]
        """.trimIndent()))
        val pages = api.getMangaChapterPages("ch-1")
        assertEquals(2, pages.size)
        assertEquals(true, pages[0].ready)
        assertEquals(false, pages[1].ready)
    }

    @Test fun listMangaSyncJobs_parsesSnakeCase() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            [{"id":"job-1","source":"onepiecetube","status":"running",
              "total_chapters":762,"done_chapters":42,
              "total_pages":1234,"done_pages":1200,
              "error_message":null,
              "started_at":1777149096017,"finished_at":null}]
        """.trimIndent()))
        val jobs = api.listMangaSyncJobs()
        assertEquals(1, jobs.size)
        assertEquals("running", jobs[0].status)
        assertEquals(762, jobs[0].totalChapters)
        assertEquals(42, jobs[0].doneChapters)
        assertEquals(1777149096017L, jobs[0].startedAt)
        assertTrue(jobs[0].finishedAt == null)
    }

    @Test fun setMangaProgress_serializesBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        api.setMangaProgress(
            "onepiecetube:one-piece",
            com.hikari.app.data.api.dto.MangaProgressRequest("ch-1", 5)
        )
        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertTrue(req.path?.contains("manga/progress/") == true)
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"chapterId\":\"ch-1\""))
        assertTrue(body.contains("\"pageNumber\":5"))
    }
}
