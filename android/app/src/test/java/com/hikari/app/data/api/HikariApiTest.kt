package com.hikari.app.data.api

import com.hikari.app.data.api.dto.AddChannelRequest
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

class HikariApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: HikariApi

    @Before
    fun setUp() {
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

    @Test fun getFeed_parsesLiveBackendShape() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                [{
                  "videoId":"Y7ImxZ_YhJk","title":"Escher","durationSeconds":102,
                  "aspectRatio":"9:16","thumbnailUrl":"https://thumb",
                  "channelId":"UC1","channelTitle":"3B1B","category":"art",
                  "reasoning":"good","addedAt":1777048562119,"saved":0
                }]
                """.trimIndent()
            )
        )
        val feed = api.getFeed()
        assertEquals(1, feed.size)
        assertEquals("Y7ImxZ_YhJk", feed[0].videoId)
        assertEquals("9:16", feed[0].aspectRatio)
    }

    @Test fun getFeed_acceptsNullableAspectRatioAndThumbnail() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                [{
                  "videoId":"voe_fsz0jl0y8u39","title":"Dragonball","durationSeconds":1209,
                  "aspectRatio":null,"thumbnailUrl":null,
                  "channelId":"manual","channelTitle":"Manuell hinzugefügt","category":"other",
                  "reasoning":"ok","addedAt":1777134579454,"saved":0
                }]
                """.trimIndent()
            )
        )
        val feed = api.getFeed()
        assertEquals(1, feed.size)
        assertEquals(null, feed[0].aspectRatio)
        assertEquals(null, feed[0].thumbnailUrl)
    }

    @Test fun addChannel_sendsJsonBody() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"UC1","title":"Test","url":"https://yt.com/@test"}"""
            )
        )
        val res = api.addChannel(AddChannelRequest("https://yt.com/@test"))
        assertEquals("UC1", res.id)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/channels", recorded.path)
    }
}
