package com.hikari.app.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.prefs.BIO_MAX_LENGTH
import com.hikari.app.data.prefs.ProfileStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NicknameError(val msg: String)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val store: ProfileStore,
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
