package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.MangaProgressRequest
import com.hikari.app.data.api.dto.MangaSeriesDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class MangaRepositoryTest {
    private val api = mockk<HikariApi>(relaxUnitFun = true)
    private val repo = MangaRepository(api)

    @Test fun pageImageUrl_concatsWithSlash() {
        val url = repo.pageImageUrl("http://example.test", "p1")
        assertEquals("http://example.test/api/manga/page/p1", url)
    }

    @Test fun pageImageUrl_stripsTrailingSlashFromBase() {
        val url = repo.pageImageUrl("http://example.test/", "p1")
        assertEquals("http://example.test/api/manga/page/p1", url)
    }

    @Test fun listSeries_passThroughToApi() = runTest {
        coEvery { api.listMangaSeries() } returns listOf(
            MangaSeriesDto(id = "s1", source = "x", title = "X")
        )
        val out = repo.listSeries()
        assertEquals(1, out.size)
        coVerify { api.listMangaSeries() }
    }

    @Test fun setProgress_buildsRequestBody() = runTest {
        repo.setProgress("s1", "ch-1", 5)
        coVerify {
            api.setMangaProgress("s1", MangaProgressRequest("ch-1", 5))
        }
    }
}
