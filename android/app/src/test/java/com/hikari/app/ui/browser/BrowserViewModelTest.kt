package com.hikari.app.ui.browser

import com.hikari.app.data.api.dto.SniffedImportItem
import com.hikari.app.domain.browser.PageLink
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: ChannelsRepository
    private lateinit var vm: BrowserViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = mockk(relaxed = true)
        vm = BrowserViewModel(repo)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun sammeltDenBestenFundDerSeite() = runTest(dispatcher) {
        vm.onPageStarted("https://serien.test/folge-1")
        vm.sniffer.onRequest("https://cdn.test/master.m3u8", emptyMap())
        vm.refreshFindings()
        vm.collectCurrent(episode = 1)

        val basket = vm.ui.value.basket
        assertEquals(1, basket.size)
        assertEquals("https://cdn.test/master.m3u8", basket[0].finding.url)
        assertEquals(1, basket[0].episode)
    }

    // Dieselbe Seite zweimal einzusammeln würde denselben Download doppelt
    // einreihen — der Korb hält je Seite genau einen Eintrag.
    @Test
    fun sammeltJedeSeiteNurEinmal() = runTest(dispatcher) {
        vm.onPageStarted("https://serien.test/folge-1")
        vm.sniffer.onRequest("https://cdn.test/a.mp4", emptyMap())
        vm.refreshFindings()
        vm.collectCurrent()
        vm.collectCurrent()
        assertEquals(1, vm.ui.value.basket.size)
    }

    @Test
    fun seitenwechselLeertDieFundeDerVorherigenSeite() = runTest(dispatcher) {
        vm.onPageStarted("https://serien.test/folge-1")
        vm.sniffer.onRequest("https://cdn.test/a.mp4", emptyMap())
        vm.refreshFindings()
        assertTrue(vm.ui.value.findings.isNotEmpty())

        vm.onPageStarted("https://serien.test/folge-2")
        assertTrue(vm.ui.value.findings.isEmpty())
        assertNull(vm.sniffer.best())
    }

    @Test
    fun durchlaufSammeltUndGehtWeiter() = runTest(dispatcher) {
        val links = listOf(
            PageLink("https://serien.test/folge-1", "Folge 1", 1),
            PageLink("https://serien.test/folge-2", "Folge 2", 2),
        )
        vm.startCrawl(links)
        runCurrent()
        assertEquals(0, vm.ui.value.crawl?.index)

        // Erste Seite liefert einen Stream → einsammeln und weiterziehen.
        vm.onPageStarted("https://serien.test/folge-1")
        vm.sniffer.onRequest("https://cdn.test/1.m3u8", emptyMap())
        vm.refreshFindings()
        runCurrent()

        assertEquals(1, vm.ui.value.basket.size)
        assertEquals(1, vm.ui.value.basket[0].episode)
        assertEquals(1, vm.ui.value.crawl?.index)
    }

    // Eine Seite, deren Player nie startet (Captcha, Geoblock, toter Hoster),
    // darf den ganzen Durchlauf nicht anhalten.
    @Test
    fun durchlaufUeberspringtSeitenOhneStream() = runTest(dispatcher) {
        val links = listOf(
            PageLink("https://serien.test/folge-1", "Folge 1", 1),
            PageLink("https://serien.test/folge-2", "Folge 2", 2),
        )
        vm.startCrawl(links)
        runCurrent()

        advanceTimeBy(21_000)
        runCurrent()

        assertEquals(1, vm.ui.value.crawl?.index)
        assertEquals(1, vm.ui.value.crawl?.skipped)
        assertTrue(vm.ui.value.basket.isEmpty())
    }

    @Test
    fun durchlaufEndetNachDerLetztenSeite() = runTest(dispatcher) {
        vm.startCrawl(listOf(PageLink("https://serien.test/folge-1", "Folge 1", 1)))
        runCurrent()
        advanceTimeBy(21_000)
        runCurrent()

        assertNull(vm.ui.value.crawl)
        assertTrue(vm.ui.value.message?.contains("fertig") == true)
    }

    @Test
    fun uebergibtSerienDatenUndHeaderAnDenImport() = runTest(dispatcher) {
        val captured = slot<List<SniffedImportItem>>()
        coEvery { repo.importSniffed(capture(captured)) } returns 1

        vm.onPageStarted("https://voe.sx/e/abc")
        vm.sniffer.onRequest(
            "https://cdn.test/stream.m3u8",
            mapOf("Referer" to "https://voe.sx/e/abc", "Cookie" to "sid=1"),
        )
        vm.refreshFindings()
        vm.collectCurrent(episode = 7)
        vm.setSeriesTitle("Solo Leveling")
        vm.setSeason(1)
        vm.submit()
        advanceUntilIdle()

        val item = captured.captured.single()
        assertEquals("https://voe.sx/e/abc", item.pageUrl)
        assertEquals("https://cdn.test/stream.m3u8", item.mediaUrl)
        assertEquals("https://voe.sx/e/abc", item.referer)
        assertEquals("sid=1", item.cookie)
        assertEquals("Solo Leveling", item.metadata?.seriesTitle)
        assertEquals(1, item.metadata?.season)
        assertEquals(7, item.metadata?.episode)
    }

    @Test
    fun leertDenKorbNachErfolgreichemAbsenden() = runTest(dispatcher) {
        coEvery { repo.importSniffed(any()) } returns 1
        vm.onPageStarted("https://serien.test/folge-1")
        vm.sniffer.onRequest("https://cdn.test/a.mp4", emptyMap())
        vm.refreshFindings()
        vm.collectCurrent()
        vm.submit()
        advanceUntilIdle()

        assertTrue(vm.ui.value.basket.isEmpty())
        assertTrue(vm.ui.value.message?.contains("eingereiht") == true)
    }

    // Bei einem Fehler muss der Korb erhalten bleiben, sonst ist die Arbeit
    // eines ganzen Durchlaufs verloren.
    @Test
    fun behaeltDenKorbWennDasAbsendenScheitert() = runTest(dispatcher) {
        coEvery { repo.importSniffed(any()) } throws RuntimeException("kein Server")
        vm.onPageStarted("https://serien.test/folge-1")
        vm.sniffer.onRequest("https://cdn.test/a.mp4", emptyMap())
        vm.refreshFindings()
        vm.collectCurrent()
        vm.submit()
        advanceUntilIdle()

        assertEquals(1, vm.ui.value.basket.size)
        assertTrue(vm.ui.value.message?.contains("Fehlgeschlagen") == true)
        coVerify(exactly = 1) { repo.importSniffed(any()) }
    }

    @Test
    fun adresseWirdZuUrlOderSuche() {
        assertEquals("https://voe.sx/e/x", normalizeUrl("https://voe.sx/e/x"))
        assertEquals("https://serien.test", normalizeUrl("serien.test"))
        assertTrue(normalizeUrl("solo leveling stream").startsWith("https://www.google.com/search?q="))
    }

    @Test
    fun fuelltSerieUndStaffelAusDerUrlVor() = runTest(dispatcher) {
        vm.onPageStarted("https://aniworld.to/serie/stream/solo-leveling/staffel-2/episode-7")
        assertEquals("Solo Leveling", vm.ui.value.seriesTitle)
        assertEquals(2, vm.ui.value.season)
    }

    // Wer den Seriennamen selbst eingetippt hat, will ihn nicht von der
    // nächsten Seiten-URL überschreiben lassen.
    @Test
    fun vorbefuellungUeberschreibtKeineManuelleEingabe() = runTest(dispatcher) {
        vm.onPageStarted("https://aniworld.to/serie/stream/solo-leveling/staffel-1/episode-1")
        vm.setSeriesTitle("Meine Serie")
        vm.setSeason(5)
        vm.onPageStarted("https://aniworld.to/serie/stream/arcane/staffel-2/episode-1")

        assertEquals("Meine Serie", vm.ui.value.seriesTitle)
        assertEquals(5, vm.ui.value.season)
    }

    // Nach dem Einsammeln von Folge 1 ist der naechste Korb-Eintrag Folge 2 —
    // aber nur, wenn eine Serie eingetragen ist.
    @Test
    fun zaehltDieFolgennummerBeimSammelnHoch() = runTest(dispatcher) {
        vm.onPageStarted("https://aniworld.to/serie/stream/solo-leveling/staffel-1/episode-1")
        vm.sniffer.onRequest("https://cdn.test/1.m3u8", emptyMap())
        vm.refreshFindings()
        vm.collectCurrent()

        vm.onPageStarted("https://aniworld.to/serie/stream/solo-leveling/staffel-1/episode-2")
        vm.sniffer.onRequest("https://cdn.test/2.m3u8", emptyMap())
        vm.refreshFindings()
        vm.collectCurrent()

        val episodes = vm.ui.value.basket.map { it.episode }
        assertEquals(listOf(1, 2), episodes)
    }

    // Genau der beobachtete Fehler: Eine Lazada-Ad hat das Hauptfenster
    // umgeleitet, danach galt die Ad-Seite als "aktuelle Seite" und der
    // eingesammelte Stream trug ihre URL.
    @Test
    fun adRedirectUeberschreibtDieAktuelleSeiteNicht() = runTest(dispatcher) {
        vm.onPageStarted("https://serien.test/folge-1")
        vm.onPageFinished("https://serien.test/folge-1", "Folge 1", false)

        vm.onPageStarted("https://s.lazada.co.th/s.ZRRUaS?t=abc")
        vm.onPageFinished("https://s.lazada.co.th/s.ZRRUaS?t=abc", "Lazada", false)

        assertEquals("https://serien.test/folge-1", vm.ui.value.currentUrl)
        assertEquals("Folge 1", vm.ui.value.pageTitle)
    }

    // Der Ad-Redirect darf auch die bisher mitgelesenen Funde nicht verwerfen —
    // sie gehören zur echten Seite, die gleich wieder eingesammelt wird.
    @Test
    fun adRedirectLaesstDieFundeDerSeiteStehen() = runTest(dispatcher) {
        vm.onPageStarted("https://serien.test/folge-1")
        vm.sniffer.onRequest("https://cdn.test/master.m3u8", emptyMap())
        vm.refreshFindings()

        vm.onPageStarted("https://s.lazada.co.th/s.ZRRUaS?t=abc")

        assertTrue(vm.ui.value.findings.isNotEmpty())
        assertEquals("https://cdn.test/master.m3u8", vm.sniffer.best()?.url)
    }

    @Test
    fun adScanUeberschreibtSeitendatenNicht() = runTest(dispatcher) {
        vm.onPageStarted("https://serien.test/folge-1")
        vm.onPageScanned("https://serien.test/folge-1", "Folge 1", emptyList(), emptyList(), "echte Beschreibung")

        vm.onPageScanned("https://s.lazada.co.th/s.ZRRUaS", "Lazada Sale", emptyList(), emptyList(), "Werbetext")

        assertEquals("Folge 1", vm.ui.value.pageTitle)
        assertEquals("echte Beschreibung", vm.ui.value.pageDescription)
    }

    @Test
    fun uebernimmtDieBeschreibungAusDemSeitenScan() = runTest(dispatcher) {
        vm.onPageStarted("https://serien.test/folge-1")
        vm.onPageScanned("https://serien.test/folge-1", "Folge 1", emptyList(), emptyList(), "Sie rettet die Welt.")
        assertEquals("Sie rettet die Welt.", vm.ui.value.pageDescription)
    }

    @Test
    fun seitenwechselLeertDieBeschreibung() = runTest(dispatcher) {
        vm.onPageStarted("https://serien.test/folge-1")
        vm.onPageScanned("https://serien.test/folge-1", "Folge 1", emptyList(), emptyList(), "Text")
        vm.onPageStarted("https://serien.test/folge-2")
        assertEquals("", vm.ui.value.pageDescription)
    }

    @Test
    fun schicktDieBeschreibungBeimImportMit() = runTest(dispatcher) {
        val captured = slot<List<SniffedImportItem>>()
        coEvery { repo.importSniffed(capture(captured)) } returns 1

        vm.onPageStarted("https://serien.test/folge-1")
        vm.sniffer.onRequest("https://cdn.test/a.mp4", emptyMap())
        vm.refreshFindings()
        vm.onPageScanned("https://serien.test/folge-1", "Folge 1", emptyList(), emptyList(), "Beschreibungstext")
        vm.collectCurrent()
        vm.submit()
        advanceUntilIdle()

        assertEquals("Beschreibungstext", captured.captured.single().description)
    }

    @Test
    fun importOhneBeschreibungSchicktNull() = runTest(dispatcher) {
        val captured = slot<List<SniffedImportItem>>()
        coEvery { repo.importSniffed(capture(captured)) } returns 1

        vm.onPageStarted("https://serien.test/folge-1")
        vm.sniffer.onRequest("https://cdn.test/a.mp4", emptyMap())
        vm.refreshFindings()
        vm.collectCurrent()
        vm.submit()
        advanceUntilIdle()

        assertNull(captured.captured.single().description)
    }

    @Test
    fun bereinigtDieBeschreibung() {
        assertEquals("a b c", BrowserViewModel.cleanDescription("  a\n b\t c  "))
        assertNull(BrowserViewModel.cleanDescription("   "))
        assertNull(BrowserViewModel.cleanDescription(null))
        assertEquals(5000, BrowserViewModel.cleanDescription("x".repeat(9000))?.length)
    }
}
