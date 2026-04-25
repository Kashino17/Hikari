package com.hikari.app.domain.sync

import app.cash.turbine.test
import com.hikari.app.data.api.dto.MangaSyncJobDto
import com.hikari.app.domain.repo.MangaRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MangaSyncObserverTest {
    private val repo = mockk<MangaRepository>()

    @Test fun status_transitionsToActiveWhenJobIsRunning() = runTest {
        val runningJob = MangaSyncJobDto(
            id = "j", source = "x", status = "running",
            totalChapters = 100, doneChapters = 42, startedAt = 1L,
        )
        coEvery { repo.listSyncJobs() } returns listOf(runningJob)
        val observer = MangaSyncObserver(repo)

        observer.status.test {
            assertTrue(awaitItem() is SyncStatus.Idle)
            observer.startPolling()
            advanceTimeBy(50)
            val active = awaitItem()
            assertTrue(active is SyncStatus.Active)
            assertTrue((active as SyncStatus.Active).job.id == "j")
            observer.stopPolling()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun status_transitionsBackToIdleWhenJobCompletes() = runTest {
        val running = MangaSyncJobDto(id="j", source="x", status="running", startedAt=1L)
        val done = MangaSyncJobDto(id="j", source="x", status="done", startedAt=1L, finishedAt=2L)
        var call = 0
        coEvery { repo.listSyncJobs() } answers {
            if (call++ == 0) listOf(running) else listOf(done)
        }
        val observer = MangaSyncObserver(repo)
        observer.status.test {
            awaitItem() // initial Idle
            observer.startPolling()
            advanceTimeBy(50)
            awaitItem() // Active
            advanceTimeBy(2_500)
            assertTrue(awaitItem() is SyncStatus.Idle)
            observer.stopPolling()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
