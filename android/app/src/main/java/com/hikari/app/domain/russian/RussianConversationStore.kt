package com.hikari.app.domain.russian

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class RussianConversationStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("hikari_russian_conversation", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(runCatching {
        ruJson.decodeFromString<RuChatProgress>(prefs.getString("progress", null) ?: "{}")
    }.getOrDefault(RuChatProgress()))
    val state = mutable.asStateFlow()
    @Synchronized fun update(transform: (RuChatProgress) -> RuChatProgress) {
        mutable.value = transform(mutable.value)
        prefs.edit().putString("progress", ruJson.encodeToString(RuChatProgress.serializer(), mutable.value)).apply()
    }
    fun loadSession(): RuChatSession? = prefs.getString("session", null)?.let {
        runCatching { ruJson.decodeFromString<RuChatSession>(it) }.getOrNull()
    }
    fun save(session: RuChatSession) {
        prefs.edit().putString("session", ruJson.encodeToString(RuChatSession.serializer(), session)).apply()
    }
    fun clearSession() { prefs.edit().remove("session").apply() }
}
