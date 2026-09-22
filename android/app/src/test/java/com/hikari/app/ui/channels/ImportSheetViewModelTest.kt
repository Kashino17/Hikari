package com.hikari.app.ui.channels

import com.hikari.app.data.api.dto.AiMeta
import com.hikari.app.data.api.dto.AnalyzeResponse
import com.hikari.app.data.api.dto.BulkImportItem
import com.hikari.app.data.api.dto.LanguagesResponse
import com.hikari.app.data.api.dto.SeriesItemDto
import com.hikari.app.domain.browser.HeadlessOutcome
import com.hikari.app.domain.browser.HeadlessSniffer
import com.hikari.app.domain.repo.ChannelsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImportSheetViewModelTest {
    private val repo = mockk<ChannelsRepository>(relaxUnitFun = true)

    // Der Headless-Sniffer greift nur, wenn die yt-dlp-Analyse scheitert; die
    // Tests hier stubben analyzeVideo auf Erfolg, der Sniff liefert also nie.
    private val sniffer = mockk<HeadlessSniffer>(relaxed = true) {
        coEvery { sniff(any(), any()) } returns null
        coEvery { sniffDetailed(any(), any()) } returns HeadlessOutcome(null, "")
    }

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        coEvery { repo.listSeries() } returns listOf(SeriesItemDto("s1", "One Piece"))
        coEvery { repo.listLanguages() } returns LanguagesResponse(
            dub = listOf("Japanisch", "Deutsch"),
            sub = listOf("Deutsch", "Englisch"),
        )
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test(timeout = 5_000) fun init_loadsSeriesList() = runTest {
        val vm = ImportSheetViewModel(repo, sniffer)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(1, s.allSeries.size)
        assertEquals("One Piece", s.allSeries[0].title)
    }

    @Test(timeout = 5_000) fun onInputChanged_debouncesUrlParse() = runTest {
        coEvery { repo.analyzeVideo(any()) } returns AnalyzeResponse(
            url = "https://x.test/1", title = "T",
        )
        val vm = ImportSheetViewModel(repo, sniffer)
        advanceUntilIdle()
        vm.onInputChanged("https://x.test/1")
        advanceTimeBy(200) // less than 500ms debounce
        assertTrue(vm.uiState.value.cards.isEmpty())
        advanceTimeBy(500)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.cards.size)
    }

    @Test(timeout = 10_000) fun analyze_success_fillsReadyCard() = runTest {
        coEvery { repo.analyzeVideo("https://x.test/1") } returns AnalyzeResponse(
            url = "https://x.test/1",
            title = "Title One",
            thumbnailUrl = "https://x.test/t.jpg",
            aiMeta = AiMeta(seriesTitle = "One Piece", season = 1, episode = 7),
        )
        val vm = ImportSheetViewModel(repo, sniffer)
        advanceUntilIdle()
        vm.onInputChanged("https://x.test/1")
        advanceTimeBy(700)
        advanceUntilIdle()
        val card = vm.uiState.value.cards.first()
        assertTrue(card is ImportCardState.Ready)
        val ready = card as ImportCardState.Ready
        assertEquals("Title One", ready.title)
        assertEquals("One Piece", ready.seriesTitle)
        assertEquals(7, ready.episode)
    }

    @Test(timeout = 10_000) fun analyze_failure_marksCardFailed() = runTest {
        coEvery { repo.analyzeVideo("https://x.test/1") } throws RuntimeException("yt-dlp failed")
        val vm = ImportSheetViewModel(repo, sniffer)
        advanceUntilIdle()
        vm.onInputChanged("https://x.test/1")
        advanceTimeBy(700)
        advanceUntilIdle()
        val card = vm.uiState.value.cards.first()
        assertTrue(card is ImportCardState.Failed)
    }

    @Test(timeout = 10_000) fun removeCard_removesFromCardsAndRawInput() = runTest {
        coEvery { repo.analyzeVideo(any()) } returns AnalyzeResponse(url = "x", title = "T")
        val vm = ImportSheetViewModel(repo, sniffer)
        advanceUntilIdle()
        vm.onInputChanged("https://x.test/1\nhttps://x.test/2")
        advanceTimeBy(700)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.cards.size)
        vm.removeCard("https://x.test/1")
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.cards.size)
        assertTrue(!vm.uiState.value.rawInput.contains("https://x.test/1"))
    }

    @Test(timeout = 10_000) fun submit_buildsRequestWithDefaultsFallback() = runTest {
        coEvery { repo.analyzeVideo("https://x.test/1") } returns AnalyzeResponse(
            url = "https://x.test/1", title = "T1",
        )
        val captured = slot<List<BulkImportItem>>()
        coEvery { repo.importVideosBulk(capture(captured)) } returns 1
        val vm = ImportSheetViewModel(repo, sniffer)
        advanceUntilIdle()
        vm.onInputChanged("https://x.test/1")
        advanceTimeBy(700)
        advanceUntilIdle()
        // Set shared defaults
        vm.updateDefaults { copy(seriesTitle = "One Piece", dubLanguage = "de") }
        val n = vm.submit()
        advanceUntilIdle()
        assertEquals(1, n)
        coVerify { repo.importVideosBulk(any()) }
        val item = captured.captured.first()
        assertEquals("One Piece", item.metadata?.seriesTitle)
        assertEquals("de", item.metadata?.dubLanguage)
    }

    // Karte 2 hat keine Folge, gehoert aber zur selben Serie (Groß-/Klein-
    // schreibung egal) wie Karte 1 — sie bekommt die naechste Nummer.
    @Test(timeout = 10_000) fun fillsMissingEpisodeFromPreviousCard() = runTest {
        coEvery { repo.analyzeVideo("https://x.test/1") } returns AnalyzeResponse(
            url = "https://x.test/1", title = "T1",
            aiMeta = AiMeta(seriesTitle = "X", season = 1, episode = 5),
        )
        coEvery { repo.analyzeVideo("https://x.test/2") } returns AnalyzeResponse(
            url = "https://x.test/2", title = "T2",
            aiMeta = AiMeta(seriesTitle = "x", season = 1),
        )
        val vm = ImportSheetViewModel(repo, sniffer)
        advanceUntilIdle()
        vm.onInputChanged("https://x.test/1\nhttps://x.test/2")
        advanceTimeBy(700)
        advanceUntilIdle()

        val cards = vm.uiState.value.cards.filterIsInstance<ImportCardState.Ready>()
        assertEquals(2, cards.size)
        assertEquals(5, cards[0].episode)
        assertEquals(6, cards[1].episode)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ImportSheetViewModelSniffTest {
    private val repo = mockk<ChannelsRepository>(relaxUnitFun = true)
    private val sniffer = mockk<HeadlessSniffer>(relaxed = true)

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        coEvery { repo.listSeries() } returns emptyList()
        coEvery { repo.listLanguages() } returns LanguagesResponse()
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun finding(url: String) = com.hikari.app.domain.browser.MediaFinding(
        url = url, kind = com.hikari.app.domain.browser.MediaKind.HLS,
        referer = "https://s.to/e", cookie = null, userAgent = "UA", contentType = null,
    )

    // Direktlink + gesniffter Stream gehen in EINEM Bulk-Request raus.
    @Test(timeout = 10_000) fun submit_sendsDirectAndSniffedInOneRequest() = runTest {
        coEvery { repo.analyzeVideo("https://youtube.com/watch?v=1") } returns AnalyzeResponse(
            url = "https://youtube.com/watch?v=1", title = "YT",
        )
        coEvery { repo.analyzeVideo("https://s.to/serie/ted/staffel-1/episode-7") } throws RuntimeException("500")
        coEvery { sniffer.sniffDetailed("https://s.to/serie/ted/staffel-1/episode-7", any()) } returns HeadlessOutcome(
            com.hikari.app.domain.browser.HeadlessResult(
                pageUrl = "https://s.to/serie/ted/staffel-1/episode-7",
                title = "Ted S01E07 | SerienStream",
                description = null,
                finding = finding("https://cdn.voe/master.m3u8?t=1"),
                meta = com.hikari.app.domain.browser.PageMetaParser.parse("https://s.to/serie/ted/staffel-1/episode-7")
                    .mergedWith(com.hikari.app.domain.browser.PageMetaParser.PageMeta(seriesTitle = "Ted", episodeTitle = "Der Gänsebraten")),
            ),
            "",
        )
        val captured = slot<List<BulkImportItem>>()
        coEvery { repo.importVideosBulk(capture(captured)) } returns 2

        val vm = ImportSheetViewModel(repo, sniffer)
        advanceUntilIdle()
        vm.onInputChanged("https://youtube.com/watch?v=1\nhttps://s.to/serie/ted/staffel-1/episode-7")
        advanceTimeBy(700)
        advanceUntilIdle()

        val ready = vm.uiState.value.cards.filterIsInstance<ImportCardState.Ready>()
        assertEquals(2, ready.size)
        val sniffedCard = ready.first { it.sniffed != null }
        // DOM-Metadaten gewinnen: Serienname und echter Folgentitel von der Seite.
        assertEquals("Ted", sniffedCard.seriesTitle)
        assertEquals(1, sniffedCard.season)
        assertEquals(7, sniffedCard.episode)
        assertEquals("Der Gänsebraten", sniffedCard.title)

        val n = vm.submit()
        advanceUntilIdle()
        assertEquals(2, n)
        coVerify(exactly = 1) { repo.importVideosBulk(any()) }
        coVerify(exactly = 0) { repo.importSniffed(any()) }
        val items = captured.captured
        assertEquals(2, items.size)
        val direct = items.first { it.url != null }
        val sniffed = items.first { it.pageUrl != null }
        assertEquals("https://youtube.com/watch?v=1", direct.url)
        assertEquals("https://cdn.voe/master.m3u8?t=1", sniffed.mediaUrl)
        assertEquals("https://s.to/e", sniffed.referer)
        assertEquals(7, sniffed.metadata?.episode)
    }

    // Staffel erkannt, Folgen werden noch analysiert — ein Tastendruck im
    // Eingabefeld darf die wartenden Folgenkarten nicht wegräumen.
    @Test(timeout = 10_000) fun expandSeason_keepsPendingEpisodeCardsAcrossInputChanges() = runTest {
        val season = "https://s.to/serie/ted/staffel-1"
        val eps = (1..3).map { "https://s.to/serie/ted/staffel-1/episode-$it" }
        coEvery { sniffer.discoverEpisodes(season, any()) } returns com.hikari.app.domain.browser.EpisodeDiscovery(
            seriesTitle = "Ted", season = 1,
            episodes = eps.mapIndexed { i, u -> com.hikari.app.domain.browser.EpisodeRef(u, i + 1, "Folge ${i + 1}") },
            diagnostics = "",
        )
        eps.forEach { u ->
            coEvery { repo.analyzeVideo(u) } coAnswers {
                kotlinx.coroutines.delay(2_000)
                AnalyzeResponse(url = u, title = "Folge")
            }
        }
        val vm = ImportSheetViewModel(repo, sniffer)
        advanceUntilIdle()
        vm.onInputChanged(season)
        advanceTimeBy(600)
        // Staffel wurde aufgeteilt, Analysen laufen (2 parallel, 1 wartet).
        assertEquals(3, vm.uiState.value.cards.size)
        // Nutzer tippt weiter — früher verschwand hier die dritte Karte.
        vm.onInputChanged("https://other.test/x")
        coEvery { repo.analyzeVideo("https://other.test/x") } returns AnalyzeResponse(url = "https://other.test/x", title = "X")
        advanceUntilIdle()
        val urls = vm.uiState.value.cards.map { it.url }.toSet()
        assertTrue(eps.all { it in urls }, "Folgenkarten verloren: $urls")
        assertEquals(3, vm.uiState.value.cards.count { it is ImportCardState.Ready && it.url in eps })
    }

    // Kein Folgenlink ließ sich einlesen: Die Staffel-URL bleibt als
    // Fehlerkarte mit Neuversuch — nicht spurlos weg.
    @Test(timeout = 10_000) fun expandSeason_allFailed_keepsSeasonCard() = runTest {
        val season = "https://s.to/serie/ted/staffel-1"
        val eps = listOf("https://s.to/serie/ted/staffel-1/episode-1")
        coEvery { sniffer.discoverEpisodes(season, any()) } returns com.hikari.app.domain.browser.EpisodeDiscovery(
            "Ted", 1, eps.map { com.hikari.app.domain.browser.EpisodeRef(it, 1, "Folge 1") }, "",
        )
        coEvery { repo.analyzeVideo(any()) } throws RuntimeException("500")
        coEvery { sniffer.sniffDetailed(any(), any()) } returns HeadlessOutcome(null, "WAF")
        val vm = ImportSheetViewModel(repo, sniffer)
        advanceUntilIdle()
        vm.onInputChanged(season)
        advanceTimeBy(700)
        advanceUntilIdle()
        val cards = vm.uiState.value.cards
        assertEquals(1, cards.size)
        val failed = cards.single() as ImportCardState.Failed
        assertEquals(season, failed.url)
        assertTrue(failed.error.contains("1 Folgen erkannt"), failed.error)
    }
}
