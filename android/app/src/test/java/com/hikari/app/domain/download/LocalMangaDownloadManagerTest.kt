package com.hikari.app.domain.download

import android.content.Context
import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.MangaArcManifestDto
import com.hikari.app.data.api.dto.MangaArcManifestPageDto
import com.hikari.app.data.db.LocalMangaArcEntity
import com.hikari.app.data.db.LocalMangaDao
import com.hikari.app.data.db.LocalMangaPageEntity
import com.hikari.app.data.prefs.SettingsStore
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
 * Sichert die Schlüssel-Invariante des Download-Managers ab: Pages haben einen
 * Foreign Key auf den Arc, also MUSS der Arc vor (oder atomar mit) seinen Pages
 * geschrieben werden. Vorher wurden Pages einzeln per upsertPage geschrieben
 * BEVOR der Arc existierte → SQLiteConstraintException → Result.failure →
 * silently ignored im VM. Bug.
 *
 * Test-Strategie: Mock-DAO + echter OkHttpClient gegen MockWebServer. Wir
 * verifizieren, dass am Ende GENAU EIN saveArcWithPages-Aufruf erfolgt und
 * upsertPage NICHT direkt benutzt wird.
 */
class LocalMangaDownloadManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File
    private val ctx: Context = mockk()
    private val dao: LocalMangaDao = mockk()
    private val settings: SettingsStore = mockk()
    private val api: HikariApi = mockk()

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        tempDir = createTempDirectory("hikari-test").toFile()
        every { ctx.filesDir } returns tempDir
        every { settings.backendUrl } returns flowOf(server.url("/").toString().trimEnd('/'))
        // observeArcIds wird im Property-Initializer des Managers aufgerufen
        every { dao.observeArcIds() } returns flowOf(emptyList())
    }

    @After fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    @Test fun download_savesArcAndPagesAtomically_inSingleTransactionCall() = runTest {
        val arcId = "src:slug:arc-1"
        val pageId = "src:slug:p-1"

        // Backend ist nach POST sofort "ready" — der Manifest-Poll-Loop bricht
        // beim ersten Aufruf gleich aus.
        coEvery { api.startMangaArcDownload(arcId) } just Runs
        coEvery { api.getMangaArcManifest(arcId) } returns MangaArcManifestDto(
            arcId = arcId,
            arcOrder = 1,
            arcTitle = "Arc 1",
            seriesId = "src:slug",
            seriesSlug = "slug",
            seriesTitle = "Slug Series",
            chapters = 1,
            totalBytes = 4,
            readyPages = 1,
            pages = listOf(
                MangaArcManifestPageDto(
                    pageId = pageId,
                    chapterId = "src:slug:ch-1",
                    chapterNumber = 1.0,
                    pageNumber = 1,
                    bytes = 4,
                    ready = true,
                ),
            ),
        )

        // Backend liefert die eigentliche Page als kleines JPG-Sample.
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "image/jpeg")
                .setBody(Buffer().write(byteArrayOf(0x01, 0x02, 0x03, 0x04))),
        )

        coEvery { dao.saveArcWithPages(any(), any()) } just Runs

        val client = OkHttpClient.Builder().build()
        val manager = LocalMangaDownloadManager(ctx, dao, client, settings, api)

        val result = manager.download(arcId)

        assertTrue(result.isSuccess, "download should succeed: $result")

        val arcSlot = slot<LocalMangaArcEntity>()
        val pagesSlot = slot<List<LocalMangaPageEntity>>()
        coVerify(exactly = 1) { dao.saveArcWithPages(capture(arcSlot), capture(pagesSlot)) }
        // Niemand darf upsertPage direkt aufrufen — alles muss durch die
        // Transaktion gehen, sonst kommt der FK-Bug zurück.
        coVerify(exactly = 0) { dao.upsertPage(any()) }
        coVerify(exactly = 0) { dao.upsertArc(any()) }

        assertEquals(arcId, arcSlot.captured.arcId)
        assertEquals(1, arcSlot.captured.expectedPageCount)
        assertEquals(4L, arcSlot.captured.totalByteSize)
        assertEquals(1, pagesSlot.captured.size)
        assertEquals(pageId, pagesSlot.captured[0].pageId)
        assertEquals(arcId, pagesSlot.captured[0].arcId)
        assertTrue(File(pagesSlot.captured[0].localFilePath).exists())
    }

    @Test fun download_propagatesFailure_whenBackendNeverReady() = runTest {
        // readyPages < pages.size → Polling-Loop läuft, aber unser Test setzt
        // hier KEIN Timeout-Override; um den Loop nicht 10 Min zu pollen,
        // simulieren wir stattdessen einen API-Fehler beim Manifest-Call.
        val arcId = "src:slug:arc-2"
        coEvery { api.startMangaArcDownload(arcId) } just Runs
        coEvery { api.getMangaArcManifest(arcId) } throws
            IllegalStateException("network down")

        val client = OkHttpClient.Builder().build()
        val manager = LocalMangaDownloadManager(ctx, dao, client, settings, api)

        val result = manager.download(arcId)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { dao.saveArcWithPages(any(), any()) }
    }
}
