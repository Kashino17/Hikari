package com.hikari.app.ui.ebooks

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.db.LocalEbookEntity
import com.hikari.app.domain.ebooks.BookState
import com.hikari.app.domain.ebooks.EbookRepository
import com.hikari.app.domain.ebooks.Mark
import com.hikari.app.domain.ebooks.ReadingPosition
import com.hikari.app.domain.ebooks.Stamped
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Leser-Einstellungen — gelten für alle Bücher. */
@Serializable
data class ReaderSettings(
    val theme: String = "papier",
    val font: String = "literata",
    val size: Int = 19,
    val lineHeight: Double = 1.5,
    val margin: String = "mittel",
    val volumeKeys: Boolean = true,
)

data class ReaderUiState(
    val book: LocalEbookEntity? = null,
    val missing: Boolean = false,
    val startCfi: String? = null,
    val startPage: Int = 0,
    val ready: Boolean = false,
    val location: ReaderLocation? = null,
    val toc: List<TocEntry> = emptyList(),
    val bookState: BookState = BookState(),
    val selection: TextSelection? = null,
    val error: String? = null,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repo: EbookRepository,
    @ApplicationContext ctx: Context,
    handle: SavedStateHandle,
) : ViewModel() {
    val id: String = checkNotNull(handle.get<String>("id"))
    private val prefs = ctx.getSharedPreferences("hikari_ebook_reader", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()
    private val _settings = MutableStateFlow(
        runCatching { json.decodeFromString<ReaderSettings>(prefs.getString("settings", null) ?: "{}") }.getOrDefault(ReaderSettings()),
    )
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val book = repo.localBook(id)
            val st = repo.state(id).first()
            _state.update {
                it.copy(
                    book = book, missing = book == null,
                    startCfi = st.position?.value?.cfi?.takeIf { c -> c.isNotBlank() },
                    startPage = st.position?.value?.page ?: 0,
                )
            }
            repo.updateState(id) { s -> s.copy(lastOpenedAt = System.currentTimeMillis()) }
        }
        viewModelScope.launch { repo.state(id).collect { s -> _state.update { it.copy(bookState = s) } } }
    }

    fun onReady() = _state.update { it.copy(ready = true) }
    fun onToc(toc: List<TocEntry>) = _state.update { it.copy(toc = toc) }
    fun onSelection(sel: TextSelection?) = _state.update { it.copy(selection = sel) }
    fun onError(msg: String) = _state.update { it.copy(error = msg) }

    /** Stelle merken — gedrosselt, damit schnelles Blättern nicht jede Seite schreibt. */
    fun onLocation(loc: ReaderLocation) {
        _state.update { it.copy(location = loc) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(2_000)
            savePosition(loc.cfi, loc.fraction, null)
        }
    }

    /** PDF: Seite merken. */
    fun onPdfPage(page: Int, pages: Int) {
        lastPdf = page to pages
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1_000)
            savePosition("", if (pages > 0) (page + 1).toDouble() / pages else 0.0, page)
        }
    }

    private var lastPdf: Pair<Int, Int>? = null

    /** Beim Verlassen sofort speichern — unabhängig vom Bildschirm, der gerade verschwindet. */
    fun flush() {
        saveJob?.cancel()
        val now = System.currentTimeMillis()
        val loc = _state.value.location
        val pdf = lastPdf
        val pos = when {
            loc != null -> ReadingPosition(loc.cfi, loc.fraction, null)
            pdf != null -> ReadingPosition("", if (pdf.second > 0) (pdf.first + 1).toDouble() / pdf.second else 0.0, pdf.first)
            else -> return
        }
        repo.updateStateDetached(id) { s ->
            s.copy(
                position = Stamped(pos, now),
                lastOpenedAt = now,
                finished = if (pos.percent >= 0.99 && s.finished?.value != true) Stamped(true, now) else s.finished,
            )
        }
    }

    private suspend fun savePosition(cfi: String, fraction: Double, page: Int?) {
        val now = System.currentTimeMillis()
        repo.updateState(id) { s ->
            s.copy(
                position = Stamped(ReadingPosition(cfi, fraction, page), now),
                lastOpenedAt = now,
                finished = if (fraction >= 0.99 && s.finished?.value != true) Stamped(true, now) else s.finished,
            )
        }
    }

    fun toggleBookmark() {
        val loc = _state.value.location ?: return
        val existing = _state.value.bookState.liveBookmarks.firstOrNull { it.cfi == loc.cfi }
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            repo.updateState(id) { s ->
                if (existing != null) {
                    s.copy(bookmarks = s.bookmarks.map { if (it.id == existing.id) it.copy(deleted = true, at = now) else it })
                } else {
                    s.copy(bookmarks = s.bookmarks + Mark(id = UUID.randomUUID().toString(), cfi = loc.cfi, label = loc.chapter.ifBlank { "${(loc.fraction * 100).toInt()} %" }, at = now))
                }
            }
        }
    }

    fun pdfBookmark(page: Int) {
        val existing = _state.value.bookState.liveBookmarks.firstOrNull { it.cfi == "page:$page" }
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            repo.updateState(id) { s ->
                if (existing != null) s.copy(bookmarks = s.bookmarks.map { if (it.id == existing.id) it.copy(deleted = true, at = now) else it })
                else s.copy(bookmarks = s.bookmarks + Mark(id = UUID.randomUUID().toString(), cfi = "page:$page", label = "Seite ${page + 1}", at = now))
            }
        }
    }

    fun highlight(color: String) {
        val sel = _state.value.selection ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            repo.updateState(id) { s -> s.copy(highlights = s.highlights + Mark(id = UUID.randomUUID().toString(), cfi = sel.cfi, text = sel.text, color = color, at = now)) }
        }
        _state.update { it.copy(selection = null) }
    }

    fun deleteMark(markId: String) {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            repo.updateState(id) { s ->
                s.copy(
                    bookmarks = s.bookmarks.map { if (it.id == markId) it.copy(deleted = true, at = now) else it },
                    highlights = s.highlights.map { if (it.id == markId) it.copy(deleted = true, at = now) else it },
                )
            }
        }
    }

    fun updateSettings(f: (ReaderSettings) -> ReaderSettings) {
        _settings.update(f)
        prefs.edit().putString("settings", json.encodeToString(ReaderSettings.serializer(), _settings.value)).apply()
    }

    fun settingsJson(columns: Int): String = ReaderMessages.settingsJson(_settings.value, columns)
}
