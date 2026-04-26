package com.hikari.app.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.data.prefs.BIO_MAX_LENGTH
import com.hikari.app.data.prefs.ProfileStore
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.domain.repo.ChannelsRepository
import com.hikari.app.domain.repo.DownloadsRepository
import com.hikari.app.domain.repo.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
) : ViewModel() {

    val name: StateFlow<String> =
        store.name.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val nickname: StateFlow<String> =
        store.nickname.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val bio: StateFlow<String> =
        store.bio.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val avatarPath: StateFlow<String?> =
        store.avatarPath.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bioMax = BIO_MAX_LENGTH

    private val _saved = MutableStateFlow<List<FeedItem>>(emptyList())
    val saved: StateFlow<List<FeedItem>> = _saved.asStateFlow()

    private val _channelsCount = MutableStateFlow(0)
    val channelsCount: StateFlow<Int> = _channelsCount.asStateFlow()

    private val _downloadsCount = MutableStateFlow(0)
    val downloadsCount: StateFlow<Int> = _downloadsCount.asStateFlow()

    private val _continueWatching = MutableStateFlow<List<LibraryVideoDto>>(emptyList())
    val continueWatching: StateFlow<List<LibraryVideoDto>> = _continueWatching.asStateFlow()

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
        refreshLibrary()
        refreshDownloadsCount()
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

    fun refreshLibrary() {
        viewModelScope.launch {
            runCatching { feedRepo.getLibrary() }
                .onSuccess { lib ->
                    _continueWatching.value = lib.recentlyAdded.filter { v ->
                        val p = v.progress_seconds ?: 0f
                        // In-progress = at least started, not yet 95% done
                        p > 0f && p < v.duration_seconds.toFloat() * 0.95f
                    }
                }
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

    fun pickAvatar(uri: Uri) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val target = File(ctx.filesDir, "avatar.jpg")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            // bust Coil's cache by appending a fresh mtime each time
            store.setAvatarPath("${target.absolutePath}?v=${System.currentTimeMillis()}")
        }
    }

    fun clearAvatar() = viewModelScope.launch { store.setAvatarPath(null) }

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
