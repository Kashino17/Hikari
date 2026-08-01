package com.hikari.app.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.prefs.BIO_MAX_LENGTH
import com.hikari.app.data.prefs.ProfileStore
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.domain.model.NewsItem
import com.hikari.app.domain.repo.ChannelsRepository
import com.hikari.app.domain.repo.DownloadsRepository
import com.hikari.app.domain.repo.FeedRepository
import com.hikari.app.domain.repo.MangaRepository
import com.hikari.app.domain.repo.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NicknameError(val msg: String)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val store: ProfileStore,
    private val feedRepo: FeedRepository,
    private val downloadsRepo: DownloadsRepository,
    private val channelsRepo: ChannelsRepository,
    private val newsRepo: NewsRepository,
    private val mangaRepo: MangaRepository,
    settings: SettingsStore,
) : ViewModel() {

    private val backendUrl: StateFlow<String> = settings.backendUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Daten für den Bereichs-Hub: aktuelles News-Bild + echte Manga-Cover. */
    private val _hubNews = MutableStateFlow<NewsItem?>(null)
    val hubNews: StateFlow<NewsItem?> = _hubNews.asStateFlow()

    private val _hubNewsCount = MutableStateFlow(0)
    val hubNewsCount: StateFlow<Int> = _hubNewsCount.asStateFlow()

    private val _hubMangaCovers = MutableStateFlow<List<String>>(emptyList())
    val hubMangaCovers: StateFlow<List<String>> = _hubMangaCovers.asStateFlow()

    private val _hubMangaLabel = MutableStateFlow<String?>(null)
    val hubMangaLabel: StateFlow<String?> = _hubMangaLabel.asStateFlow()

    val name: StateFlow<String> =
        store.name.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val nickname: StateFlow<String> =
        store.nickname.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val bio: StateFlow<String> =
        store.bio.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val avatarPath: StateFlow<String?> =
        store.avatarPath.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bioMax = BIO_MAX_LENGTH

    /** One-shot user-facing messages (e.g. avatar save failures) for a Toast. */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _saved = MutableStateFlow<List<FeedItem>>(emptyList())
    val saved: StateFlow<List<FeedItem>> = _saved.asStateFlow()

    private val _channelsCount = MutableStateFlow(0)
    val channelsCount: StateFlow<Int> = _channelsCount.asStateFlow()

    private val _downloadsCount = MutableStateFlow(0)
    val downloadsCount: StateFlow<Int> = _downloadsCount.asStateFlow()

    val savedCount: StateFlow<Int> = _saved
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        refreshAll()
    }

    /** Reloads all profile-tab data — used on screen-resume so counts stay live. */
    fun refreshAll() {
        refreshSaved()
        refreshChannelsCount()
        refreshDownloadsCount()
        refreshHub()
    }

    /** Lädt die Bilder und Zeilen für den Bereichs-Hub (News + Manga). */
    fun refreshHub() {
        viewModelScope.launch {
            runCatching { newsRepo.getBriefing(force = false) }
                .onSuccess { items ->
                    _hubNewsCount.value = items.size
                    _hubNews.value = items.firstOrNull { it.imageUrls.isNotEmpty() }
                        ?: items.firstOrNull()
                }
        }
        viewModelScope.launch {
            runCatching { mangaRepo.getContinue() }
                .onSuccess { cont ->
                    val base = backendUrl.value
                    _hubMangaCovers.value = cont.mapNotNull { c ->
                        c.coverPath?.let { mangaRepo.coverImageUrl(base, it) }
                    }.take(3)
                    _hubMangaLabel.value = cont.firstOrNull()?.let { "${it.title} · Seite ${it.pageNumber}" }
                }
        }
    }

    fun refreshSaved() {
        viewModelScope.launch {
            runCatching { feedRepo.fetchSaved() }
                .onSuccess { _saved.value = it }
        }
    }

    fun refreshChannelsCount() {
        viewModelScope.launch {
            runCatching { channelsRepo.list() }
                .onSuccess { _channelsCount.value = it.size }
        }
    }

    fun refreshDownloadsCount() {
        viewModelScope.launch {
            // downloadsRepo.load() greift bei Server-Fehler auf den lokalen
            // Bestand zurück → der Counter zeigt offline genau das, was auf
            // dem Gerät liegt.
            val d = downloadsRepo.load()
            _downloadsCount.value =
                d.series.sumOf { it.episode_count } +
                d.channels.sumOf { it.video_count } +
                d.movies.size
        }
    }

    fun setName(v: String) = viewModelScope.launch { store.setName(v) }

    /**
     * Saves nickname after validation.
     * Returns null on success, or a NicknameError describing the violation.
     *
     * Rules: a-z, 0-9, "_", "."; 3–20 chars; must not start/end with ".".
     * Lowercase is enforced server-side at write-time (store.setNickname trims+lowercases).
     */
    fun trySetNickname(raw: String): NicknameError? {
        val candidate = raw.trim().lowercase()
        validateNickname(candidate)?.let { return it }
        viewModelScope.launch { store.setNickname(candidate) }
        return null
    }

    fun setBio(v: String) = viewModelScope.launch { store.setBio(v) }

    /**
     * Imports the picked image into app storage and persists its path.
     *
     * Robustness fixes over the previous version:
     *  - Errors are no longer swallowed: a failure emits a user-facing message
     *    instead of silently doing nothing (which looked like "save doesn't work").
     *  - Large gallery photos are downsampled (inSampleSize) before writing, so a
     *    20MP image can't OOM the import.
     *  - The file is written under a UNIQUE name (avatar_<ts>.jpg). That busts
     *    Coil's cache via a distinct key while keeping a VALID file path — the
     *    old "avatar.jpg?v=<ts>" string was not a real path, so the image never
     *    loaded. Older avatar files are pruned.
     */
    fun pickAvatar(uri: Uri) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val dir = ctx.filesDir

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                } ?: error("Bild konnte nicht geöffnet werden")

                val targetPx = 1024
                var sample = 1
                var w = bounds.outWidth
                var h = bounds.outHeight
                while (w / 2 >= targetPx && h / 2 >= targetPx) {
                    w /= 2; h /= 2; sample *= 2
                }

                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = ctx.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                } ?: error("Bild konnte nicht gelesen werden")

                val file = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                bitmap.recycle()

                dir.listFiles { f -> f.name.startsWith("avatar") && f.name != file.name }
                    ?.forEach { it.delete() }

                file.absolutePath
            }
        }
        result
            .onSuccess { store.setAvatarPath(it) }
            .onFailure {
                _events.tryEmit(it.message ?: "Profilbild konnte nicht gespeichert werden")
            }
    }

    fun clearAvatar() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            ctx.filesDir.listFiles { f -> f.name.startsWith("avatar") }?.forEach { it.delete() }
        }
        store.setAvatarPath(null)
    }

    companion object {
        private val NICKNAME_REGEX = Regex("^[a-z0-9_.]+$")

        fun validateNickname(value: String): NicknameError? {
            if (value.length < 3) return NicknameError("Mindestens 3 Zeichen.")
            if (value.length > 20) return NicknameError("Maximal 20 Zeichen.")
            if (!NICKNAME_REGEX.matches(value))
                return NicknameError("Nur a–z, 0–9, _ und .")
            if (value.startsWith(".") || value.endsWith("."))
                return NicknameError("Darf nicht mit . anfangen oder enden.")
            return null
        }
    }
}
