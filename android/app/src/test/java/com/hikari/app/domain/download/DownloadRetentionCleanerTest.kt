package com.hikari.app.domain.download

import com.hikari.app.data.db.LocalDownloadDao
import com.hikari.app.data.db.LocalDownloadEntity
import com.hikari.app.data.db.LocalDownloadSource
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.player.ActiveVideoInfo
import com.hikari.app.player.VideoDockManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class DownloadRetentionCleanerTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    private val dao = mockk<LocalDownloadDao>()
    private val downloads = mockk<LocalDownloadManager>(relaxUnitFun = true)
    private val settings = mockk<SettingsStore>()
    private val dock = mockk<VideoDockManager>()

    private fun entity(id: String, ageDays: Int, source: LocalDownloadSource) = LocalDownloadEntity(
        videoId = id,
        localFilePath = "/tmp/$id.mp4",
        byteSize = 1L,
        downloadedAt = now - ageDays * day,
        source = source.name,
    )

    private fun cleaner(manualDays: Int, smartDays: Int, playing: String? = null): DownloadRetentionCleaner {
        every { settings.manualDownloadRetentionDays } returns flowOf(manualDays)
        every { settings.smartDownloadRetentionDays } returns flowOf(smartDays)
        every { dock.activeVideo } returns MutableStateFlow(playing?.let { ActiveVideoInfo(it, "", "") })
        return DownloadRetentionCleaner(dao, downloads, settings, dock)
    }

    @Test fun manual_and_smart_downloads_follow_their_own_period() = runTest {
        coEvery { dao.all() } returns listOf(
            entity("manual-old", ageDays = 40, source = LocalDownloadSource.MANUAL),
            entity("smart-old", ageDays = 40, source = LocalDownloadSource.SMART),
            entity("smart-new", ageDays = 3, source = LocalDownloadSource.SMART),
        )
        // Eigene für immer, Smart 30 Tage: nur der alte Smart-Download fliegt.
        val removed = cleaner(manualDays = 0, smartDays = 30).run(now)
        assertEquals(1, removed)
        coVerify(exactly = 1) { downloads.delete("smart-old") }
        coVerify(exactly = 0) { downloads.delete("manual-old") }
        coVerify(exactly = 0) { downloads.delete("smart-new") }
    }

    @Test fun manual_downloads_expire_when_a_period_is_set() = runTest {
        coEvery { dao.all() } returns listOf(
            entity("a", ageDays = 8, source = LocalDownloadSource.MANUAL),
            entity("b", ageDays = 6, source = LocalDownloadSource.MANUAL),
        )
        val removed = cleaner(manualDays = 7, smartDays = 0).run(now)
        assertEquals(1, removed)
        coVerify(exactly = 1) { downloads.delete("a") }
        coVerify(exactly = 0) { downloads.delete("b") }
    }

    @Test fun the_video_that_is_playing_is_never_removed() = runTest {
        coEvery { dao.all() } returns listOf(entity("playing", ageDays = 90, source = LocalDownloadSource.SMART))
        val removed = cleaner(manualDays = 7, smartDays = 7, playing = "playing").run(now)
        assertEquals(0, removed)
        coVerify(exactly = 0) { downloads.delete(any()) }
    }

    @Test fun nothing_is_touched_when_both_are_kept_forever() = runTest {
        val removed = cleaner(manualDays = 0, smartDays = 0).run(now)
        assertEquals(0, removed)
        coVerify(exactly = 0) { dao.all() }
    }
}
