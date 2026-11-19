package com.hikari.app.ui.ebooks

import kotlinx.coroutines.flow.first

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.EbookCardDto
import com.hikari.app.data.api.dto.EbookDetailDto
import com.hikari.app.data.api.dto.EbookHomeDto
import com.hikari.app.data.db.LocalEbookEntity
import com.hikari.app.domain.ebooks.BookState
import com.hikari.app.domain.ebooks.EbookFilter
import com.hikari.app.domain.ebooks.EbookRepository
import com.hikari.app.domain.ebooks.Stamped
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ein angefangenes Buch auf dem Handy. */
data class ContinueItem(val book: LocalEbookEntity, val percent: Double)

data class BooksHomeState(
    val loading: Boolean = true,
    val offline: Boolean = false,
    val home: EbookHomeDto? = null,
    val local: List<LocalEbookEntity> = emptyList(),
    val continueReading: List<ContinueItem> = emptyList(),
    val saved: Set<String> = emptySet(),
    val finishedCount: Int = 0,
    /** Gemerkt / Gelesen vom Mac (auch Bücher, die nicht auf dem Handy sind). */
    val mine: Map<String, List<EbookCardDto>> = emptyMap(),
)

@HiltViewModel
class BooksViewModel @Inject constructor(private val repo: EbookRepository) : ViewModel() {
    private val _state = MutableStateFlow(BooksHomeState())
    val state: StateFlow<BooksHomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repo.local(), repo.allStates()) { local, states -> local to states }.collect { (local, states) ->
                val cont = local.mapNotNull { b ->
                    val st = states[b.id] ?: return@mapNotNull null
                    val pct = st.position?.value?.percent ?: return@mapNotNull null
                    if (st.finished?.value == true || st.lastOpenedAt == null) null else Triple(b, pct, st.lastOpenedAt)
                }.sortedByDescending { it.third }.map { ContinueItem(it.first, it.second) }
                _state.update {
                    it.copy(
                        local = local,
                        continueReading = cont,
                        saved = states.filterValues { s -> s.saved?.value == true }.keys,
                        finishedCount = states.count { (_, s) -> s.finished?.value == true },
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            // Zuletzt gelesenes Buch für „Weil du … liest“.
            val states = runCatching { repo.allStates().first() }.getOrDefault(emptyMap())
            val because = states.filter { (_, st) -> st.lastOpenedAt != null && st.finished?.value != true }
                .maxByOrNull { (_, st) -> st.lastOpenedAt ?: 0L }?.key
            val r = repo.home(because)
            _state.update { it.copy(loading = false, offline = r.isFailure, home = r.getOrNull() ?: it.home) }
            if (r.isSuccess) runCatching { repo.syncStates() }
        }
    }

    fun loadMine(kind: String) {
        viewModelScope.launch {
            repo.list(EbookFilter(state = kind, sort = "titel"), 0, 100)
                .onSuccess { r -> _state.update { it.copy(mine = it.mine + (kind to r.books)) } }
                .onFailure { _state.update { it.copy(mine = it.mine + (kind to emptyList())) } }
        }
    }

    fun toggleSaved(id: String) {
        viewModelScope.launch {
            repo.updateState(id) { s -> s.copy(saved = Stamped(!(s.saved?.value ?: false), System.currentTimeMillis())) }
        }
    }
}

data class BookListState(
    val filter: EbookFilter = EbookFilter(),
    val books: List<EbookCardDto> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val endReached: Boolean get() = books.size >= total && !loading
}

/** Liste mit Filtern; ein neuer Filter beginnt wieder vorn. */
open class BookListViewModel(private val repo: EbookRepository, initial: EbookFilter) : ViewModel() {
    private val _state = MutableStateFlow(BookListState(filter = initial))
    val state: StateFlow<BookListState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load(reset = true)
    }

    fun setFilter(f: EbookFilter) {
        if (f == _state.value.filter && _state.value.books.isNotEmpty()) return
        _state.update { it.copy(filter = f, books = emptyList(), total = 0) }
        load(reset = true)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.books.size >= s.total) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        job?.cancel()
        val offset = if (reset) 0 else _state.value.books.size
        val filter = _state.value.filter
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            repo.list(filter, offset).onSuccess { r ->
                _state.update { it.copy(books = if (reset) r.books else it.books + r.books, total = r.total, loading = false) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Fehler") }
            }
        }
    }
}

/** Hilt-Variante: holt den Startfilter aus den Navigations-Argumenten. */
@HiltViewModel
class BookListScreenViewModel @Inject constructor(repo: EbookRepository, handle: SavedStateHandle) :
    BookListViewModel(repo, filterFrom(handle))

private fun filterFrom(handle: SavedStateHandle): EbookFilter {
    fun arg(k: String) = handle.get<String>(k)?.takeIf { it.isNotBlank() }
    return EbookFilter(q = arg("q"), category = arg("category"), author = arg("author"), format = arg("format"), length = arg("length"), state = arg("state"), sort = arg("sort") ?: "beliebt")
}

data class BookDetailState(
    val detail: EbookDetailDto? = null,
    val error: String? = null,
    val local: LocalEbookEntity? = null,
    val bookState: BookState = BookState(),
    val progress: Float? = null,
    val downloadError: String? = null,
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val repo: EbookRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    val id: String = checkNotNull(handle.get<String>("id"))
    private val _state = MutableStateFlow(BookDetailState())
    val state: StateFlow<BookDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch { repo.state(id).collect { s -> _state.update { it.copy(bookState = s) } } }
        viewModelScope.launch { repo.local().collect { l -> _state.update { it.copy(local = l.firstOrNull { b -> b.id == id }) } } }
        viewModelScope.launch {
            repo.detail(id)
                .onSuccess { d -> _state.update { it.copy(detail = d) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun download() {
        if (_state.value.progress != null) return
        _state.update { it.copy(progress = 0f, downloadError = null) }
        viewModelScope.launch {
            repo.download(id) { p -> _state.update { it.copy(progress = p) } }
                .onFailure { e -> _state.update { it.copy(downloadError = e.message ?: "Laden fehlgeschlagen") } }
            _state.update { it.copy(progress = null) }
        }
    }

    fun toggleSaved() = stamp { s, now -> s.copy(saved = Stamped(!(s.saved?.value ?: false), now)) }
    fun toggleFinished() = stamp { s, now -> s.copy(finished = Stamped(!(s.finished?.value ?: false), now)) }
    fun removeDownload() {
        viewModelScope.launch { repo.removeDownload(id) }
    }

    private fun stamp(f: (BookState, Long) -> BookState) {
        viewModelScope.launch { repo.updateState(id) { f(it, System.currentTimeMillis()) } }
    }
}
