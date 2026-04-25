package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.FeedItemDto
import com.hikari.app.data.db.FeedDao
import com.hikari.app.data.db.FeedItemEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class FeedRepositoryTest {
    private val api = mockk<HikariApi>(relaxUnitFun = true)
    private val dao = mockk<FeedDao>(relaxUnitFun = true)
    private val repo = FeedRepository(api, dao)

    @Test fun refresh_upsertsItemsFromApi() = runTest {
        coEvery { api.getFeed(mode = "new") } returns listOf(
            FeedItemDto(
                videoId = "v1", title = "t", durationSeconds = 60,
                aspectRatio = "9:16", thumbnailUrl = "thumb",
                channelId = "c1", channelTitle = "chan",
                category = "science", reasoning = "r",
                addedAt = 100L, saved = 0,
            )
        )
        repo.refresh()
        coVerify { dao.upsertAll(match { it.size == 1 && it[0].videoId == "v1" }) }
        coVerify { dao.pruneUnseenUnsavedNotIn(listOf("v1")) }
    }

    @Test fun refresh_whenApiReturnsNoItems_prunesUnseenUnsaved() = runTest {
        coEvery { api.getFeed(mode = "new") } returns emptyList()
        repo.refresh()
        coVerify { dao.upsertAll(emptyList()) }
        coVerify { dao.pruneAllUnseenUnsaved() }
    }

    @Test fun fetchOld_returnsItemsFromApi() = runTest {
        coEvery { api.getFeed(mode = "old") } returns listOf(
            FeedItemDto(
                videoId = "v2", title = "t2", durationSeconds = 120,
                aspectRatio = "16:9", thumbnailUrl = "thumb2",
                channelId = "c1", channelTitle = "chan",
                category = "science", reasoning = "r",
                addedAt = 200L, saved = 0, seenAt = 300L,
            )
        )
        val result = repo.fetchOld()
        assertEquals(1, result.size)
        assertEquals("v2", result[0].videoId)
    }

    @Test fun fetchSaved_returnsItemsFromApi() = runTest {
        coEvery { api.getFeed(mode = "saved") } returns listOf(
            FeedItemDto(
                videoId = "v3", title = "t3", durationSeconds = 180,
                aspectRatio = null, thumbnailUrl = null,
                channelId = "c1", channelTitle = "chan",
                category = "science", reasoning = "r",
                addedAt = 400L, saved = 1,
            )
        )
        val result = repo.fetchSaved()
        assertEquals(1, result.size)
        assertEquals("v3", result[0].videoId)
        assertEquals(true, result[0].saved)
    }

    @Test fun unseenItems_mapsEntitiesToModels() = runTest {
        coEvery { dao.unseenItems() } returns flowOf(
            listOf(
                FeedItemEntity(
                    videoId = "v1", title = "t", durationSeconds = 60,
                    aspectRatio = "9:16", thumbnailUrl = "thumb",
                    channelId = "c1", channelTitle = "chan",
                    category = "art", reasoning = "r",
                    addedAt = 100L, saved = false, seen = false,
                )
            )
        )
        val emitted = mutableListOf<List<com.hikari.app.domain.model.FeedItem>>()
        repo.unseenItems().collect { emitted += it }
        assertEquals(1, emitted[0].size)
        assertEquals("v1", emitted[0][0].videoId)
    }
}
