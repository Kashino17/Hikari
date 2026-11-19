package com.hikari.app.domain.ebooks

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.db.EbookStateDao
import com.hikari.app.data.db.EbookStateEntity
import com.hikari.app.data.db.LocalEbookDao
import com.hikari.app.data.db.LocalEbookEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Test
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EbookRepositoryTest {
    private class FakeLocalDao : LocalEbookDao {
        val rows = MutableStateFlow<Map<String, LocalEbookEntity>>(emptyMap())
        override fun observeAll(): Flow<List<LocalEbookEntity>> = rows.map { it.values.sortedByDescending { e -> e.addedAt } }
        override suspend fun get(id: String): LocalEbookEntity? = rows.value[id]
        override suspend fun upsert(e: LocalEbookEntity) { rows.value = rows.value + (e.id to e) }
        override suspend fun delete(id: String) { rows.value = rows.value - id }
    }

    private class FakeStateDao : EbookStateDao {
        val rows = MutableStateFlow<Map<String, EbookStateEntity>>(emptyMap())
        override fun observe(id: String): Flow<EbookStateEntity?> = rows.map { it[id] }
        override suspend fun get(id: String): EbookStateEntity? = rows.value[id]
        override suspend fun all(): List<EbookStateEntity> = rows.value.values.toList()
        override suspend fun dirty(): List<EbookStateEntity> = rows.value.values.filter { it.dirty }
        override suspend fun upsert(e: EbookStateEntity) { rows.value = rows.value + (e.bookId to e) }
    }

    private val api = mockk<HikariApi>()
    private val local = FakeLocalDao()
    private val states = FakeStateDao()
    private val dir: File = Files.createTempDirectory("ebooks").toFile()
    private val repo = EbookRepository(api, local, states, EbookFileStore(dir), Json { ignoreUnknownKeys = true })

    private fun detail(format: String = "epub") = com.hikari.app.data.api.dto.EbookDetailDto(
        id = "gutenberg:1", title = "Effi Briest", authors = listOf("Theodor Fontane"), format = format,
        source = "gutenberg", sourceUrl = "https://x", sourceName = "Project Gutenberg",
    )

    @Test fun downloadDoppeltGleichzeitigLaedtEinmal() = runTest {
        coEvery { api.getEbook("gutenberg:1") } returns detail()
        coEvery { api.downloadEbookFile("gutenberg:1") } coAnswers {
            delay(50)
            Response.success("EPUB-INHALT".toResponseBody("application/epub+zip".toMediaType()))
        }
        val a = async { repo.download("gutenberg:1") {} }
        val b = async { repo.download("gutenberg:1") {} }
        assertTrue(a.await().isSuccess && b.await().isSuccess)
        coVerify(exactly = 1) { api.downloadEbookFile("gutenberg:1") }
        val saved = local.get("gutenberg:1")!!
        assertEquals("EPUB-INHALT", File(saved.filePath).readText())
        assertEquals("epub", saved.format)
    }

    @Test fun abbruchMittenImLadenHinterlaesstKeineDatei() = runTest {
        coEvery { api.getEbook("gutenberg:1") } returns detail()
        val broken = object : ResponseBody() {
            override fun contentType() = "application/epub+zip".toMediaType()
            override fun contentLength() = 1000L
            override fun source(): BufferedSource = object : ForwardingSource(Buffer().writeUtf8("halb")) {
                var once = false
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (once) throw IOException("Netz weg")
                    once = true
                    return super.read(sink, byteCount)
                }
            }.buffer()
        }
        coEvery { api.downloadEbookFile("gutenberg:1") } returns Response.success(broken)
        val r = repo.download("gutenberg:1") {}
        assertTrue(r.isFailure)
        assertNull(local.get("gutenberg:1"))
        assertEquals(emptyList(), dir.walk().filter { it.isFile }.map { it.name }.toList())
    }

    @Test fun lesestandOhneNetzBleibtLokalUndSchmutzig() = runTest {
        coEvery { api.putEbookState(any(), any()) } throws IOException("offline")
        repo.updateState("gutenberg:1") { it.copy(saved = Stamped(true, 5)) }
        val row = states.get("gutenberg:1")!!
        assertTrue(row.dirty)
        assertEquals(true, repo.state("gutenberg:1").first().saved?.value)
    }

    @Test fun syncSchicktSchmutzigesUndUebernimmtDenMac() = runTest {
        repo.updateStateLocal("gutenberg:1") { it.copy(saved = Stamped(true, 5)) }
        coEvery { api.putEbookState("gutenberg:1", any()) } returns BookState(saved = Stamped(true, 5), finished = Stamped(true, 9))
        coEvery { api.getEbookStates() } returns mapOf(
            "gutenberg:2" to BookState(bookmarks = listOf(Mark(id = "m1", cfi = "c", at = 3))),
        )
        repo.syncStates()
        assertEquals(false, states.get("gutenberg:1")!!.dirty)
        assertEquals(true, repo.state("gutenberg:1").first().finished?.value)
        assertEquals("m1", repo.state("gutenberg:2").first().bookmarks.single().id)
    }
}
