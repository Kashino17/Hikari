package com.hikari.app.ui.ebooks

import com.hikari.app.data.api.dto.EbookCardDto
import com.hikari.app.data.api.dto.EbookListDto
import com.hikari.app.data.db.LocalEbookEntity
import com.hikari.app.domain.ebooks.BookState
import com.hikari.app.domain.ebooks.EbookFilter
import com.hikari.app.domain.ebooks.EbookRepository
import com.hikari.app.domain.ebooks.ReadingPosition
import com.hikari.app.domain.ebooks.Stamped
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BooksViewModelTest {
    private val repo = mockk<EbookRepository>(relaxed = true)
    private val effi = LocalEbookEntity("gutenberg:1", "Effi Briest", "Theodor Fontane", "epub", null, "/x.epub", 10, 5)
    private val pdf = LocalEbookEntity("bpb:2", "Demokratie", "", "pdf", null, "/y.pdf", 10, 9)

    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun macNichtErreichbarZeigtHinweisUndGeladeneBuecher() {
        coEvery { repo.home(any()) } returns Result.failure(IOException("weg"))
        every { repo.local() } returns flowOf(listOf(effi, pdf))
        every { repo.allStates() } returns flowOf(emptyMap())
        val vm = BooksViewModel(repo)
        val s = vm.state.value
        assertTrue(s.offline)
        assertEquals(listOf("gutenberg:1", "bpb:2"), s.local.map { it.id })
    }

    @Test fun weiterlesenNachZuletztGeoeffnetOhneFertige() {
        coEvery { repo.home(any()) } returns Result.failure(IOException("weg"))
        every { repo.local() } returns flowOf(listOf(effi, pdf))
        every { repo.allStates() } returns flowOf(
            mapOf(
                "gutenberg:1" to BookState(position = Stamped(ReadingPosition("c", 0.4), 1), lastOpenedAt = 50),
                "bpb:2" to BookState(position = Stamped(ReadingPosition("", 0.2, 3), 1), lastOpenedAt = 90, finished = Stamped(true, 2)),
            ),
        )
        val vm = BooksViewModel(repo)
        assertEquals(listOf("gutenberg:1"), vm.state.value.continueReading.map { it.book.id })
        assertEquals(0.4, vm.state.value.continueReading.single().percent)
    }

    @Test fun filterwechselBeginntWiederVorn() {
        coEvery { repo.list(any(), any(), any()) } returns Result.success(
            EbookListDto(total = 100, books = List(30) { EbookCardDto(id = "b$it", title = "B$it") }),
        )
        val vm = BookListViewModel(repo, EbookFilter())
        vm.loadMore()
        coVerify { repo.list(EbookFilter(), 30, any()) }
        vm.setFilter(EbookFilter(format = "pdf"))
        coVerify { repo.list(EbookFilter(format = "pdf"), 0, any()) }
        assertEquals(30, vm.state.value.books.size)
    }

    @Test fun lesezeitInMinutenOderStunden() {
        assertEquals("ca. 45 Min.", readingTime(45))
        assertEquals("ca. 1 Std.", readingTime(60))
        assertEquals("ca. 7 Std.", readingTime(400))
    }
}
