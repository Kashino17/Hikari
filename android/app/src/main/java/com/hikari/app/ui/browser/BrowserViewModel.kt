package com.hikari.app.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.ImportItemMetadata
import com.hikari.app.data.api.dto.SniffedImportItem
import com.hikari.app.domain.browser.EpisodeLinkFilter
import com.hikari.app.domain.browser.MediaFinding
import com.hikari.app.domain.browser.MediaSniffer
import com.hikari.app.domain.browser.PageLink
import com.hikari.app.domain.browser.PageMetaParser
import com.hikari.app.domain.browser.PageTitleFilter
import com.hikari.app.domain.repo.ChannelsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ein eingesammelter Fund samt der Seite, auf der er gefunden wurde. */
data class BasketItem(
    val pageUrl: String,
    val pageTitle: String,
    val finding: MediaFinding,
    val episode: Int? = null,
)

/** Stand eines automatischen Durchlaufs durch mehrere Folgenseiten. */
data class CrawlState(
    val queue: List<PageLink>,
    val index: Int,
    val collected: Int,
    val skipped: Int,
)

data class BrowserUiState(
    val currentUrl: String = "",
    val pageTitle: String = "",
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val findings: List<MediaFinding> = emptyList(),
    val episodeLinks: List<PageLink> = emptyList(),
    val basket: List<BasketItem> = emptyList(),
    val crawl: CrawlState? = null,
    val seriesTitle: String = "",
    val season: Int? = null,
    /** true, sobald der Nutzer das Feld selbst angefasst hat — die URL-Vorbefüllung überschreibt dann nicht mehr. */
    val seriesEdited: Boolean = false,
    val seasonEdited: Boolean = false,
    val submitting: Boolean = false,
    val message: String? = null,
    /** Diagnose: wie viele Requests der Interceptor auf dieser Seite sah. */
    val inspected: Int = 0,
    val recentUrls: List<String> = emptyList(),
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val repo: ChannelsRepository,
) : ViewModel() {

    /** Der Sniffer lebt im ViewModel, damit er einen Rotationswechsel überlebt. */
    val sniffer = MediaSniffer()

    private val _ui = MutableStateFlow(BrowserUiState())
    val ui: StateFlow<BrowserUiState> = _ui.asStateFlow()

    /** Navigationsbefehle an den WebView (der Auto-Durchlauf steuert darüber). */
    private val _navigate = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val navigate: SharedFlow<String> = _navigate.asSharedFlow()

    private var crawlTimeout: Job? = null

    // ---- Seiten-Ereignisse aus dem WebView -------------------------------

    fun onPageStarted(url: String) {
        sniffer.reset()
        _ui.update {
            it.copy(
                currentUrl = url,
                loading = true,
                findings = emptyList(),
                episodeLinks = emptyList(),
                inspected = 0,
                recentUrls = emptyList(),
            )
        }
        prefillFromUrl(url)
    }

    fun onPageFinished(url: String, title: String, canGoBack: Boolean) {
        _ui.update { it.copy(currentUrl = url, pageTitle = title, loading = false, canGoBack = canGoBack) }
    }

    /** Ergebnis des injizierten Scan-Scripts. */
    fun onPageScanned(url: String, title: String, domVideos: List<String>, links: List<PageLink>) {
        // <video src> direkt aus dem DOM zählt wie ein mitgelesener Request.
        for (v in domVideos) sniffer.onRequest(v, emptyMap())
        val episodes = EpisodeLinkFilter.extract(url, links)
        val clean = PageTitleFilter.clean(title)
        _ui.update {
            it.copy(
                pageTitle = clean ?: it.pageTitle,
                episodeLinks = episodes,
                findings = sniffer.findings(),
            )
        }
        prefillFromUrl(url)
    }

    /**
     * Füllt Serie und Staffel aus der URL vor — aber nur solange der Nutzer
     * das Feld nicht selbst bearbeitet hat ([BrowserUiState.seriesEdited] /
     * [BrowserUiState.seasonEdited]).
     */
    private fun prefillFromUrl(url: String) {
        val meta = PageMetaParser.parse(url)
        _ui.update { st ->
            st.copy(
                seriesTitle = if (!st.seriesEdited && meta.seriesTitle != null) meta.seriesTitle else st.seriesTitle,
                season = if (!st.seasonEdited && meta.season != null) meta.season else st.season,
            )
        }
    }

    /** Übernimmt, was der Interceptor inzwischen gesehen hat. */
    fun refreshFindings() {
        val found = sniffer.findings()
        _ui.update {
            it.copy(
                findings = found,
                inspected = sniffer.inspectedCount(),
                recentUrls = sniffer.recentUrls(),
            )
        }
        // Im Auto-Durchlauf reicht der erste brauchbare Fund, dann weiter.
        if (found.isNotEmpty() && _ui.value.crawl != null) collectAndAdvance()
    }

    // ---- Sammeln ---------------------------------------------------------

    /** Den besten Fund der aktuellen Seite in den Korb legen. */
    fun collectCurrent(episode: Int? = null) {
        val s = _ui.value
        val best = sniffer.best() ?: return
        addToBasket(s.currentUrl, s.pageTitle, best, episode ?: nextEpisode(s))
    }

    fun collectSpecific(finding: MediaFinding) {
        val s = _ui.value
        addToBasket(s.currentUrl, s.pageTitle, finding, nextEpisode(s))
    }

    /**
     * Nächste freie Folgennummer im Korb — nur wenn eine Serie eingetragen
     * ist, sonst bleibt das Feld leer (kein Raten ohne Kontext).
     */
    private fun nextEpisode(s: BrowserUiState): Int? =
        if (s.seriesTitle.isBlank()) null
        else (s.basket.mapNotNull { it.episode }.maxOrNull() ?: 0) + 1

    private fun addToBasket(pageUrl: String, title: String, finding: MediaFinding, episode: Int?) {
        // Steht die Seite gerade hinter einem Bot-Schutz, traegt sie dessen
        // Platzhaltertitel — und genau dann wird eingesammelt, weil der Player
        // erst nach der Pruefung startet. Lieber kein Titel als "Security
        // Check": Ohne ihn beschriftet die Uebersicht mit Serie und Folge.
        val clean = PageTitleFilter.clean(title).orEmpty()
        _ui.update { st ->
            if (st.basket.any { it.pageUrl == pageUrl }) st
            else st.copy(basket = st.basket + BasketItem(pageUrl, clean, finding, episode))
        }
    }

    fun removeFromBasket(pageUrl: String) {
        _ui.update { it.copy(basket = it.basket.filterNot { b -> b.pageUrl == pageUrl }) }
    }

    fun clearBasket() = _ui.update { it.copy(basket = emptyList()) }

    fun setSeriesTitle(v: String) = _ui.update { it.copy(seriesTitle = v, seriesEdited = true) }

    fun setSeason(v: Int?) = _ui.update { it.copy(season = v, seasonEdited = true) }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    // ---- Automatischer Durchlauf ----------------------------------------

    /**
     * Geht die erkannten Folgenseiten der Reihe nach durch, lässt auf jeder den
     * Player anlaufen und sammelt den Stream ein.
     *
     * Genau der Punkt, an dem sich der Browser vom Link-Einfügen abhebt: einmal
     * klicken statt zwanzig Folgen einzeln zu öffnen und zu kopieren.
     */
    fun startCrawl(links: List<PageLink>) {
        if (links.isEmpty()) return
        _ui.update { it.copy(crawl = CrawlState(links, 0, 0, 0)) }
        goToCrawlPage(0)
    }

    fun stopCrawl() {
        crawlTimeout?.cancel()
        _ui.update { it.copy(crawl = null) }
    }

    private fun goToCrawlPage(index: Int) {
        val crawl = _ui.value.crawl ?: return
        if (index >= crawl.queue.size) {
            crawlTimeout?.cancel()
            _ui.update {
                it.copy(
                    crawl = null,
                    message = "Durchlauf fertig — ${crawl.collected} gefunden, ${crawl.skipped} ohne Stream",
                )
            }
            return
        }
        _ui.update { it.copy(crawl = crawl.copy(index = index)) }
        viewModelScope.launch { _navigate.emit(crawl.queue[index].url) }

        // Ohne Zeitlimit bliebe der Durchlauf an einer Seite hängen, deren
        // Player nie startet (Captcha, toter Hoster, Geoblock).
        crawlTimeout?.cancel()
        crawlTimeout = viewModelScope.launch {
            delay(PAGE_TIMEOUT_MS)
            if (_ui.value.crawl?.index == index) {
                _ui.update { st -> st.copy(crawl = st.crawl?.copy(skipped = st.crawl.skipped + 1)) }
                goToCrawlPage(index + 1)
            }
        }
    }

    private fun collectAndAdvance() {
        val crawl = _ui.value.crawl ?: return
        val best = sniffer.best() ?: return
        val link = crawl.queue.getOrNull(crawl.index)
        addToBasket(
            link?.url ?: _ui.value.currentUrl,
            _ui.value.pageTitle,
            best,
            link?.episode,
        )
        _ui.update { st -> st.copy(crawl = st.crawl?.copy(collected = st.crawl.collected + 1)) }
        crawlTimeout?.cancel()
        goToCrawlPage(crawl.index + 1)
    }

    // ---- Absenden --------------------------------------------------------

    fun submit() {
        val s = _ui.value
        if (s.basket.isEmpty() || s.submitting) return
        _ui.update { it.copy(submitting = true, message = null) }

        viewModelScope.launch {
            val items = s.basket.map { b ->
                SniffedImportItem(
                    pageUrl = b.pageUrl,
                    mediaUrl = b.finding.url,
                    // Fällt der Referer aus dem Interceptor weg, ist die
                    // Herkunftsseite die beste Annahme — der Hoster erwartet
                    // ohnehin genau sie.
                    referer = b.finding.referer ?: b.pageUrl,
                    cookie = b.finding.cookie,
                    userAgent = b.finding.userAgent,
                    title = b.pageTitle.ifBlank { null },
                    metadata = ImportItemMetadata(
                        seriesTitle = s.seriesTitle.ifBlank { null },
                        season = s.season,
                        episode = b.episode,
                    ),
                )
            }
            val result = runCatching { repo.importSniffed(items) }
            _ui.update {
                it.copy(
                    submitting = false,
                    basket = if (result.isSuccess) emptyList() else it.basket,
                    message = result.fold(
                        onSuccess = { n -> "$n zum Download eingereiht" },
                        onFailure = { e -> "Fehlgeschlagen: ${e.message}" },
                    ),
                )
            }
        }
    }

    private companion object {
        /** Wartezeit je Seite im Auto-Durchlauf, bevor übersprungen wird. */
        const val PAGE_TIMEOUT_MS = 20_000L
    }
}
