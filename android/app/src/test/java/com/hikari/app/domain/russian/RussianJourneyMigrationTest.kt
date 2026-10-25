package com.hikari.app.domain.russian

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test

class RussianJourneyMigrationTest {
    @Test fun upgradingDisablesSurpriseAudioWithoutErasingVocabularyOrProgress() {
        val original = RuChatProgress(stage = 2, settings = RuChatSettings(autoPlay = true, audioFirst = true),
            cards = listOf(RuChatCard(RuChatPhrase("чай", "Tee"), 10, 11)))
        val prefs = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val context = mockk<Context>()
        every { context.getSharedPreferences("hikari_russian_conversation", Context.MODE_PRIVATE) } returns prefs
        every { prefs.getString("progress", null) } returns ruJson.encodeToString(RuChatProgress.serializer(), original)
        every { prefs.getString("session", null) } returns "old conversation"
        every { prefs.getInt("journeyUxVersion", 0) } returns 0
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        val updated = RussianConversationStore(context).state.value
        assertFalse(updated.settings.autoPlay)
        assertFalse(updated.settings.audioFirst)
        assertEquals(original.cards, updated.cards)
        assertEquals(2, updated.stage)
        assertTrue(updated.foundation.completed.isEmpty())
        verify { editor.putInt("journeyUxVersion", 2) }
        verify { editor.putString("preJourneySession", "old conversation") }
        verify { editor.remove("session") }
    }
}
