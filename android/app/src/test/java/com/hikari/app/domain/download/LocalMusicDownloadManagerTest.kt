package com.hikari.app.domain.download

import android.content.Context
import com.hikari.app.data.db.LocalMusicDownloadDao
import com.hikari.app.data.db.LocalMusicDownloadEntity
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.MusicRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Sichert die Invarianten des Musik-Downloads ab:
 *  - eine DB-Zeile entsteht NUR nach vollständigem Download,
 *  - ein Fehlschlag hinterlässt keine Teildatei und keine Zeile,
 *  - der Fortschritts-Eintrag verschwindet am Ende in jedem Fall.
 * Sonst würde die Offline-Ansicht Songs anbieten, die gar nicht abspielbar sind.
 */
class LocalMusicDownloadManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File
    private val ctx: Context = mockk()
    private val dao: LocalMusicDownloadDao = mockk()
    private val repo: MusicRepository = mockk()
    private val client = OkHttpClient()

    private val song = MusicSong(
        videoId = "dQw4w9WgXcQ",
        title = "Testsong",
        uploader = "Testartist",
        uploaderUrl = "",
        thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
        duration = 213,
        views = 0,
    )

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        tempDir = createTempDirectory("hikari-music-test").toFile()
        every { ctx.filesDir } returns tempDir
        every { dao.observeIds() } returns flowOf(emptyList())
        every { dao.observeAll() } returns flowOf(emptyList())
        coEvery { dao.get(any()) } returns null
        coEvery { repo.recordPlayed(any(), any()) } just Runs
    }

    @After fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    private fun manager() = LocalMusicDownloadManager(ctx, dao, repo, client)

    @Test fun download_writesFileAndRow_onSuccess() = runTest {
        val audio = Buffer().apply { write(ByteArray(4096) { 7 }) }
        server.enqueue(MockResponse().setResponseCode(200).setBody(audio))
        coEvery { repo.getAudioStream(song.videoId) } returns server.url("/audio.m4a").toString()
        val saved = slot<LocalMusicDownloadEntity>()
        coEvery { dao.upsert(capture(saved)) } just Runs

        val result = manager().download(song)

        assertTrue(result.isSuccess, "Download sollte erfolgreich sein")
        coVerify(exactly = 1) { dao.upsert(any()) }
        assertEquals(song.videoId, saved.captured.videoId)
        assertEquals(4096L, saved.captured.byteSize)
        assertEquals("Testsong", saved.captured.title)
        assertTrue(File(saved.captured.localFilePath).exists(), "Datei muss auf der Platte liegen")
    }

    @Test fun download_failsWithoutRow_whenNoStreamUrl() = runTest {
        coEvery { repo.getAudioStream(song.videoId) } returns null

        val result = manager().download(song)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test fun download_cleansUpPartialFile_onHttpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        coEvery { repo.getAudioStream(song.videoId) } returns server.url("/audio.m4a").toString()

        val mgr = manager()
        val result = mgr.download(song)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { dao.upsert(any()) }
        assertFalse(
            File(File(tempDir, "music"), "${song.videoId}.m4a").exists(),
            "Teildatei muss aufgeräumt sein",
        )
        assertTrue(mgr.progress.value.isEmpty(), "Fortschritt darf nicht hängenbleiben")
    }

    @Test fun download_isSkipped_whenAlreadyOnDisk() = runTest {
        val existing = File(tempDir, "music").apply { mkdirs() }
            .let { File(it, "${song.videoId}.m4a") }
        existing.writeBytes(ByteArray(10))
        val row = LocalMusicDownloadEntity(
            videoId = song.videoId,
            localFilePath = existing.absolutePath,
            byteSize = 10,
            downloadedAt = 1L,
            title = song.title,
            uploader = song.uploader,
            thumbnailUrl = song.thumbnailUrl,
            durationSeconds = song.duration,
        )
        coEvery { dao.get(song.videoId) } returns row

        val result = manager().download(song)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { repo.getAudioStream(any()) }
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test fun localFile_dropsStaleRow_whenFileMissing() = runTest {
        coEvery { dao.get(song.videoId) } returns LocalMusicDownloadEntity(
            videoId = song.videoId,
            localFilePath = File(tempDir, "weg.m4a").absolutePath,
            byteSize = 1,
            downloadedAt = 1L,
        )
        coEvery { dao.delete(song.videoId) } just Runs

        val file = manager().localFile(song.videoId)

        assertEquals(null, file)
        coVerify(exactly = 1) { dao.delete(song.videoId) }
    }
}
