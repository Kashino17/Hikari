package com.hikari.app.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.prefs.BIO_MAX_LENGTH
import com.hikari.app.data.prefs.ProfileStore
import com.hikari.app.domain.repo.ChannelsRepository
import com.hikari.app.domain.repo.DownloadsRepository
import com.hikari.app.domain.repo.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** One-shot user-facing messages (e.g. avatar save failures) for a Toast. */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _channelsCount = MutableStateFlow(0)
    val channelsCount: StateFlow<Int> = _channelsCount.asStateFlow()

    private val _downloadsCount = MutableStateFlow(0)
    val downloadsCount: StateFlow<Int> = _downloadsCount.asStateFlow()

    init {
        refreshAll()
    }

    /** Reloads all profile-tab data — used on screen-resume so counts stay live. */
    fun refreshAll() {
        refreshChannelsCount()
        refreshDownloadsCount()
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

                val resolver = ctx.contentResolver
                fun open() = resolver.openInputStream(uri) ?: error("Bild konnte nicht geöffnet werden")

                // Nur die Maße lesen. decodeStream gibt dabei mit Absicht null zurück —
                // ob das Bild lesbar ist, sagen allein outWidth/outHeight.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                open().use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Das Bild lässt sich nicht lesen")

                val opts = BitmapFactory.Options().apply {
                    inSampleSize = avatarSampleSize(bounds.outWidth, bounds.outHeight)
                }
                val decoded = open().use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: error("Das Bild lässt sich nicht lesen")

                // Handyfotos liegen oft gedreht in der Datei und tragen die Lage nur als Vermerk.
                val degrees = runCatching { open().use { exifDegrees(ExifInterface(it)) } }.getOrDefault(0)
                val bitmap = if (degrees == 0) decoded else {
                    val turn = Matrix().apply { postRotate(degrees.toFloat()) }
                    Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, turn, true)
                        .also { if (it !== decoded) decoded.recycle() }
                }

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

        /** Kantenlänge, auf die große Fotos beim Übernehmen etwa verkleinert werden. */
        private const val AVATAR_TARGET_PX = 1024

        /** Verkleinerungsstufe (Zweierpotenz), mit der beide Kanten noch mindestens die Zielgröße haben. */
        fun avatarSampleSize(width: Int, height: Int, target: Int = AVATAR_TARGET_PX): Int {
            var sample = 1
            var w = width
            var h = height
            while (w / 2 >= target && h / 2 >= target) {
                w /= 2; h /= 2; sample *= 2
            }
            return sample
        }

        private fun exifDegrees(exif: ExifInterface): Int =
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

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
