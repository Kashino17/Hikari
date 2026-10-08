package com.hikari.app.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.ChannelVideoDto
import com.hikari.app.data.api.dto.ImportItemMetadata
import com.hikari.app.data.api.dto.PendingImportDto
import com.hikari.app.data.api.dto.SniffedImportItem
import com.hikari.app.domain.browser.AdBlocker
import com.hikari.app.domain.browser.AdHosts
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
    /** Beschreibung der Fundseite (og:description), null wenn keine da war. */
    val description: String? = null,
    /** Pro Eintrag, damit Folgen aus verschiedenen Staffeln/Serien im selben Korb stimmen. */
    val seriesTitle: String? = null,
    val season: Int? = null,
)

/** Stand eines automatischen Durchlaufs durch mehrere Folgenseiten. */
data class CrawlState(
    val queue: List<PageLink>,
    val index: Int,
    val collected: Int,
    val skipped: Int,
    /** Die Seite zeigt eine Bot-Prüfung — der Nutzer muss sie abschließen. */
    val waitingForHuman: Boolean = false,
)

data class BrowserUiState(
    val currentUrl: String = "",
    val pageTitle: String = "",
    /** Seitenbeschreibung aus den Meta-Tags (og:description, sonst description). */
    val pageDescription: String = "",
    /** Folgennummer, die die Seite selbst nennt (Überschrift/JSON-LD), sonst null. */
    val pageEpisode: Int? = null,
    /** Staffel der aktuellen Seite (URL vor Überschrift), sonst null. */
    val pageSeason: Int? = null,
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
    /** Werbeschutz an/aus und wie viele Requests/Pop-ups er gestoppt hat. */
    val shieldEnabled: Boolean = true,
    val blockedCount: Int = 0,
    /** Laufende, wartende und gescheiterte Downloads (Server-Importliste). */
    val transfers: List<PendingImportDto> = emptyList(),
    /** Zuletzt fertig gewordene Downloads des Archivs "Manuell hinzugefügt". */
    val history: List<ChannelVideoDto> = emptyList(),
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val repo: ChannelsRepository,
) : ViewModel() {

    /** Der Sniffer lebt im ViewModel, damit er einen Rotationswechsel überlebt. */
    val sniffer = MediaSniffer()

    /** Werbe-/Pop-up-Blocker — ebenfalls hier, damit der Zähler eine Rotation überlebt. */
    val adBlocker = AdBlocker()

    private val _ui = MutableStateFlow(BrowserUiState())
    val ui: StateFlow<BrowserUiState> = _ui.asStateFlow()

    /** Navigationsbefehle an den WebView (der Auto-Durchlauf steuert darüber). */
    private val _navigate = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val navigate: SharedFlow<String> = _navigate.asSharedFlow()

    private var crawlTimeout: Job? = null

    // ---- Seiten-Ereignisse aus dem WebView -------------------------------

    fun onPageStarted(url: String) {
        // Ad-Redirect ins Hauptfenster (z. B. s.lazada.co.th/s.…): Die Ad-Seite
        // ist nicht "die aktuelle Seite" — würden wir sie übernehmen, landete
        // der mitgelesene Stream im Korb unter ihrer URL und ihrem Titel.
        // Deshalb ignoriert: Sniffer und Funde der echten Seite bleiben stehen.
        if (AdHosts.isAdUrl(url)) return
        sniffer.reset()
        adBlocker.resetCount()
        val urlMeta = PageMetaParser.parse(url)
        _ui.update {
            it.copy(
                currentUrl = url,
                blockedCount = 0,
                pageDescription = "",
                pageEpisode = urlMeta.episode,
                pageSeason = urlMeta.season,
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
        // Derselbe Schutz wie in onPageStarted — Ad-Redirects setzen weder
        // URL noch Titel der aktuellen Seite.
        if (AdHosts.isAdUrl(url)) return
        _ui.update { it.copy(currentUrl = url, pageTitle = title, loading = false, canGoBack = canGoBack) }
    }

    /** Ergebnis des injizierten Scan-Scripts. */
    fun onPageScanned(
        url: String,
        title: String,
        domVideos: List<String>,
        links: List<PageLink>,
        description: String? = null,
        meta: PageMetaParser.PageMeta? = null,
    ) {
        // Der Scan einer umgeleiteten Ad-Seite darf die echten Seitendaten
        // nicht überschreiben.
        if (AdHosts.isAdUrl(url)) return
        // <video src> direkt aus dem DOM zählt wie ein mitgelesener Request.
        for (v in domVideos) sniffer.onRequest(v, emptyMap())
        val episodes = EpisodeLinkFilter.extract(url, links)
        // Der echte Folgentitel aus den Überschriften schlägt document.title —
        // der ist oft nur "Serie S01E02 | Seitenname".
        val clean = meta?.episodeTitle?.let { PageTitleFilter.clean(it) } ?: PageTitleFilter.clean(title)
        // Zahlen: die URL ("/staffel-2/episode-3") ist eindeutig, Überschriften
        // sind es nicht (Folgenlisten, Teaser) — deshalb URL zuerst.
        val urlMeta = PageMetaParser.parse(url)
        _ui.update {
            it.copy(
                pageEpisode = urlMeta.episode ?: meta?.episode,
                pageSeason = urlMeta.season ?: meta?.season,
                pageTitle = clean ?: it.pageTitle,
                pageDescription = description?.takeIf(String::isNotBlank) ?: it.pageDescription,
                episodeLinks = episodes,
                findings = sniffer.findings(),
            )
        }
        prefillFromUrl(url, meta)
    }

    /**
     * Füllt Serie und Staffel aus der URL vor — aber nur solange der Nutzer
     * das Feld nicht selbst bearbeitet hat ([BrowserUiState.seriesEdited] /
     * [BrowserUiState.seasonEdited]).
     */
    private fun prefillFromUrl(url: String, dom: PageMetaParser.PageMeta? = null) {
        val meta = PageMetaParser.parse(url)
        val series = dom?.seriesTitle?.takeIf { it.isNotBlank() } ?: meta.seriesTitle
        val season = meta.season ?: dom?.season
        _ui.update { st ->
            st.copy(
                seriesTitle = if (!st.seriesEdited && series != null) series else st.seriesTitle,
                season = if (!st.seasonEdited && season != null) season else st.season,
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
                blockedCount = adBlocker.blockedCount,
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
        addToBasket(s.currentUrl, s.pageTitle, best, episode, s.pageDescription)
    }

    fun collectSpecific(finding: MediaFinding) {
        val s = _ui.value
        addToBasket(s.currentUrl, s.pageTitle, finding, null, s.pageDescription)
    }

    /**
     * Nächste freie Folgennummer im Korb — nur wenn eine Serie eingetragen
     * ist, sonst bleibt das Feld leer (kein Raten ohne Kontext).
     */
    private fun nextEpisode(s: BrowserUiState): Int? =
        if (s.seriesTitle.isBlank()) null
        else (s.basket.mapNotNull { it.episode }.maxOrNull() ?: 0) + 1

    private fun addToBasket(
        pageUrl: String,
        title: String,
        finding: MediaFinding,
        episode: Int?,
        description: String? = null,
    ) {
        // Steht die Seite gerade hinter einem Bot-Schutz, traegt sie dessen
        // Platzhaltertitel — und genau dann wird eingesammelt, weil der Player
        // erst nach der Pruefung startet. Lieber kein Titel als "Security
        // Check": Ohne ihn beschriftet die Uebersicht mit Serie und Folge.
        val clean = PageTitleFilter.clean(title).orEmpty()
        val desc = description?.takeIf { it.isNotBlank() }
        _ui.update { st ->
            if (st.basket.any { it.pageUrl == pageUrl }) return@update st
            val urlMeta = PageMetaParser.parse(pageUrl)
            val onPage = pageUrl == st.currentUrl
            // Staffel: Eingabe des Nutzers > URL des Eintrags > Seiten-Scan > Feld.
            val season = (if (st.seasonEdited) st.season else null)
                ?: urlMeta.season
                ?: (if (onPage) st.pageSeason else null)
                ?: st.season
            // Folge: übergebene > Seite (URL vor Überschrift) > URL des Eintrags > Zählen.
            val ep = episode
                ?: (if (onPage) st.pageEpisode else null)
                ?: urlMeta.episode
                ?: nextEpisode(st)
            st.copy(
                basket = st.basket + BasketItem(
                    pageUrl, clean, finding, ep, desc,
                    seriesTitle = st.seriesTitle.ifBlank { null },
                    season = season,
                ),
            )
        }
    }

    fun removeFromBasket(pageUrl: String) {
        _ui.update { it.copy(basket = it.basket.filterNot { b -> b.pageUrl == pageUrl }) }
    }

    /** Leerer Korb = neue Runde: die Vorbefüllung aus der Seite darf wieder greifen. */
    fun clearBasket() = _ui.update { it.copy(basket = emptyList(), seriesEdited = false, seasonEdited = false) }

    /** Serie/Staffel im Kopf des Korbs gelten für alle Einträge … */
    fun setSeriesTitle(v: String) = _ui.update {
        it.copy(
            seriesTitle = v,
            seriesEdited = true,
            basket = it.basket.map { b -> b.copy(seriesTitle = v.ifBlank { null }) },
        )
    }

    fun setSeason(v: Int?) = _ui.update {
        it.copy(season = v, seasonEdited = true, basket = it.basket.map { b -> b.copy(season = v) })
    }

    /** … und lassen sich pro Eintrag nachträglich überschreiben. */
    fun setItemSeason(pageUrl: String, v: Int?) = updateItem(pageUrl) { it.copy(season = v) }

    fun setItemEpisode(pageUrl: String, v: Int?) = updateItem(pageUrl) { it.copy(episode = v) }

    private fun updateItem(pageUrl: String, change: (BasketItem) -> BasketItem) = _ui.update {
        it.copy(basket = it.basket.map { b -> if (b.pageUrl == pageUrl) change(b) else b })
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    fun setShield(enabled: Boolean) {
        adBlocker.enabled = enabled
        _ui.update { it.copy(shieldEnabled = enabled) }
    }

    // ---- Downloads im Browser ---------------------------------------------

    private var downloadPoller: Job? = null

    /**
     * Hält die Downloadliste aktuell, solange der Browser offen ist. Der
     * Aufrufer startet das (statt init), damit Tests ohne Main-Dispatcher laufen.
     */
    fun startDownloadPolling() {
        if (downloadPoller?.isActive == true) return
        downloadPoller = viewModelScope.launch {
            var lastActive = -1
            while (true) {
                val items = runCatching { repo.listImports() }.getOrNull()
                if (items != null) {
                    val active = items.count { it.status != "failed" }
                    _ui.update { it.copy(transfers = items) }
                    // Ein fertig gewordener Download wandert in den Verlauf.
                    if (lastActive < 0 || active < lastActive) loadHistory()
                    lastActive = active
                }
                val busy = _ui.value.transfers.any { it.status != "failed" }
                delay(if (busy) DOWNLOAD_POLL_MS else IDLE_POLL_MS)
            }
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            runCatching { repo.listVideos(MANUAL_CHANNEL_ID) }.onSuccess { vids ->
                val recent = vids.sortedByDescending { it.discoveredAt ?: it.addedToFeedAt ?: 0L }.take(HISTORY_LIMIT)
                _ui.update { it.copy(history = recent) }
            }
        }
    }

    fun retryTransfer(id: String) {
        viewModelScope.launch {
            runCatching { repo.retryImport(id) }
                .onFailure { e -> _ui.update { it.copy(message = "Neuversuch fehlgeschlagen: ${e.message}") } }
        }
    }

    fun dismissTransfer(id: String) {
        viewModelScope.launch {
            runCatching { repo.deleteImport(id) }
                .onSuccess { _ui.update { st -> st.copy(transfers = st.transfers.filterNot { it.id == id }) } }
        }
    }

    // ---- Absichtlich angesteuerte Navigation (Adressleiste/Durchlauf) ----

    /**
     * Die zuletzt bewusst angeforderte Hauptframe-URL (Adressleiste, Suche,
     * Auto-Durchlauf). Der WebView-Client lässt einen Ad-/Tracker-Host im
     * Hauptframe durch, wenn er genau dieser URL entspricht — automatische
     * Ad-Redirects tragen kein Nutzer-Geste und stimmen nicht hiermit
     * überein, werden also blockiert.
     */
    var intendedNavigation: String? = null
        private set

    /** Aufruf aus der Adressleiste, bevor die eingegebene URL geladen wird. */
    fun onAddressBarGo(url: String) {
        intendedNavigation = url
    }

    private fun navigateTo(url: String) {
        intendedNavigation = url
        viewModelScope.launch { _navigate.emit(url) }
    }

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

    /**
     * Meldung des Seiten-Checks ([com.hikari.app.domain.browser.PageScripts.GATE_CHECK]).
     * Steht im Durchlauf eine Bot-Prüfung an, läuft das 20-s-Limit nicht ab:
     * Es wird einmal je Seite durch ein langes ersetzt, damit der Nutzer Haken
     * und "Weiter" in Ruhe erledigen kann.
     */
    fun onGate(present: Boolean) {
        val crawl = _ui.value.crawl ?: return
        if (present == crawl.waitingForHuman) return
        _ui.update { st -> st.copy(crawl = st.crawl?.copy(waitingForHuman = present)) }
        if (present) {
            val index = crawl.index
            crawlTimeout?.cancel()
            crawlTimeout = viewModelScope.launch {
                delay(GATE_TIMEOUT_MS)
                if (_ui.value.crawl?.index == index) {
                    _ui.update { st -> st.copy(crawl = st.crawl?.copy(skipped = st.crawl.skipped + 1)) }
                    goToCrawlPage(index + 1)
                }
            }
        }
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
        _ui.update { it.copy(crawl = crawl.copy(index = index, waitingForHuman = false)) }
        navigateTo(crawl.queue[index].url)

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
            _ui.value.pageDescription,
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
                    description = b.description,
                    metadata = ImportItemMetadata(
                        seriesTitle = b.seriesTitle ?: s.seriesTitle.ifBlank { null },
                        season = b.season,
                        episode = b.episode,
                    ),
                )
            }
            val result = runCatching { repo.importSniffed(items) }
            _ui.update {
                it.copy(
                    submitting = false,
                    basket = if (result.isSuccess) emptyList() else it.basket,
                    seriesEdited = if (result.isSuccess) false else it.seriesEdited,
                    seasonEdited = if (result.isSuccess) false else it.seasonEdited,
                    message = result.fold(
                        onSuccess = { n -> "$n zum Download eingereiht — Fortschritt unter Downloads" },
                        onFailure = { e -> "Fehlgeschlagen: ${e.message}" },
                    ),
                )
            }
        }
    }

    companion object {
        /** Wartezeit je Seite im Auto-Durchlauf, bevor übersprungen wird. */
        private const val PAGE_TIMEOUT_MS = 20_000L

        /** Wartezeit je Seite, solange eine Bot-Prüfung auf den Nutzer wartet. */
        private const val GATE_TIMEOUT_MS = 120_000L

        private const val DOWNLOAD_POLL_MS = 1_500L
        private const val IDLE_POLL_MS = 6_000L
        private const val HISTORY_LIMIT = 30
        private const val MANUAL_CHANNEL_ID = "manual"

        /** Maximale Länge der mitgeschickten Seitenbeschreibung. */
        private const val MAX_DESCRIPTION_LENGTH = 5000

        /**
         * Macht aus dem Meta-Tag-Inhalt eine einzeilige, begrenzte
         * Beschreibung: Zeilenumbrüche und Mehrfach-Leerzeichen raus,
         * auf [MAX_DESCRIPTION_LENGTH] gekürzt.
         */
        internal fun cleanDescription(raw: String?): String? =
            com.hikari.app.domain.browser.DescriptionCleaner.clean(raw)
                ?.take(MAX_DESCRIPTION_LENGTH)
    }
}
