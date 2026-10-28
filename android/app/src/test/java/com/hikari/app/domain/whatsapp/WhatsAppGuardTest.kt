package com.hikari.app.domain.whatsapp

import android.content.Context
import android.content.SharedPreferences
import com.hikari.app.data.db.WhatsAppDao
import com.hikari.app.data.db.WhatsAppMessageEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WhatsAppGuardTest {

    private lateinit var fakeDao: FakeWhatsAppDao
    private lateinit var repository: WhatsAppRepository

    @Before
    fun setUp() {
        fakeDao = FakeWhatsAppDao()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true) {
            every { putBoolean(any<String>(), any<Boolean>()) } returns this
            every { putString(any<String>(), any<String>()) } returns this
            every { remove(any<String>()) } returns this
        }
        val prefs = mockk<SharedPreferences>(relaxed = true) {
            every { getBoolean(any<String>(), any<Boolean>()) } returns true
            every { getString(any<String>(), any<String>()) } returns null
            every { edit() } returns editor
        }
        val context = mockk<Context>(relaxed = true) {
            every { getSharedPreferences(any<String>(), any<Int>()) } returns prefs
        }
        repository = WhatsAppRepository(context, fakeDao)
    }

    @Test
    fun `parser detects deleted message placeholders in multiple languages`() {
        assertTrue(WhatsAppNotificationParser.isDeletedPlaceholder("Diese Nachricht wurde gelöscht"))
        assertTrue(WhatsAppNotificationParser.isDeletedPlaceholder("🚫 Diese Nachricht wurde gelöscht."))
        assertTrue(WhatsAppNotificationParser.isDeletedPlaceholder("This message was deleted"))
        assertTrue(WhatsAppNotificationParser.isDeletedPlaceholder("🚫 This message was deleted."))
        assertTrue(WhatsAppNotificationParser.isDeletedPlaceholder("Bu mesaj silindi"))
        assertTrue(WhatsAppNotificationParser.isDeletedPlaceholder("Это сообщение было удалено"))
        assertFalse(WhatsAppNotificationParser.isDeletedPlaceholder("Ich habe die Datei gelöscht"))
    }

    @Test
    fun `parser filters out WhatsApp summary and background noise`() {
        assertTrue(WhatsAppNotificationParser.isSystemNoise("WhatsApp", "3 neue Nachrichten"))
        assertTrue(WhatsAppNotificationParser.isSystemNoise("WhatsApp", "12 Nachrichten aus 4 Chats"))
        assertTrue(WhatsAppNotificationParser.isSystemNoise("WhatsApp", "Suche nach neuen Nachrichten..."))
        assertTrue(WhatsAppNotificationParser.isSystemNoise("WhatsApp", "WhatsApp Web ist derzeit aktiv"))
        assertFalse(WhatsAppNotificationParser.isSystemNoise("Max", "Treffen wir uns um 18 Uhr?"))
    }

    @Test
    fun `parser cleans group message count suffix`() {
        assertEquals(
            "Familien-Gruppe",
            WhatsAppNotificationParser.cleanChatTitle("Familien-Gruppe (4 Nachrichten)"),
        )
        assertEquals(
            "Projekt Team",
            WhatsAppNotificationParser.cleanChatTitle("Projekt Team (2 messages)"),
        )
    }

    @Test
    fun `repository preserves original message when sender deletes for everyone`() = runTest {
        val t0 = 1_700_000_000_000L

        // 1. Eingehende Nachricht von Anna
        val incoming = RawNotificationPayload(
            packageName = "com.whatsapp",
            notificationKey = "notif_anna",
            notificationTag = "anna",
            title = "Anna",
            text = "Hier ist der geheime Code: 9481",
            bigText = null,
            subText = null,
            conversationTitle = null,
            isGroupConversation = false,
            isSummary = false,
            isOngoing = false,
            category = "msg",
            postTime = t0,
            messagingStyleItems = listOf(
                RawMessagingItem(
                    sender = "Anna",
                    text = "Hier ist der geheime Code: 9481",
                    timestamp = t0,
                ),
            ),
        )
        repository.processNotification(incoming)

        val savedBeforeDelete = repository.allMessages.first()
        assertEquals(1, savedBeforeDelete.size)
        assertEquals("Hier ist der geheime Code: 9481", savedBeforeDelete.first().text)
        assertFalse(savedBeforeDelete.first().isDeleted)

        // 2. Anna löscht die Nachricht für alle -> WhatsApp aktualisiert die Benachrichtigung
        val deletionUpdate = incoming.copy(
            text = "🚫 Diese Nachricht wurde gelöscht.",
            postTime = t0 + 15_000L,
            messagingStyleItems = listOf(
                RawMessagingItem(
                    sender = "Anna",
                    text = "🚫 Diese Nachricht wurde gelöscht.",
                    timestamp = t0,
                ),
            ),
        )
        repository.processNotification(deletionUpdate)

        // 3. Originaltext muss erhalten bleiben und isDeleted = true gesetzt sein!
        val deletedList = repository.deletedMessages.first()
        assertEquals(1, deletedList.size)
        val preserved = deletedList.first()
        assertEquals("Hier ist der geheime Code: 9481", preserved.text)
        assertTrue(preserved.isDeleted)
        assertNotNull(preserved.deletedAt)
    }

    @Test
    fun `repository detects edited message and preserves originalText`() = runTest {
        val t0 = 1_700_000_100_000L

        // 1. Ursprüngliche Nachricht von Lukas
        val initialPayload = RawNotificationPayload(
            packageName = "com.whatsapp",
            notificationKey = "notif_lukas",
            notificationTag = "lukas",
            title = "Lukas",
            text = "Treffen um 19:00 Uhr vorm Kino",
            bigText = null,
            subText = null,
            conversationTitle = null,
            isGroupConversation = false,
            isSummary = false,
            isOngoing = false,
            category = "msg",
            postTime = t0,
            messagingStyleItems = listOf(
                RawMessagingItem(
                    sender = "Lukas",
                    text = "Treffen um 19:00 Uhr vorm Kino",
                    timestamp = t0,
                ),
            ),
        )
        repository.processNotification(initialPayload)

        // 2. Lukas bearbeitet die Nachricht innerhalb des 15-Minuten-Fensters
        val editedPayload = initialPayload.copy(
            text = "Treffen um 20:30 Uhr vorm Kino",
            postTime = t0 + 60_000L,
            messagingStyleItems = listOf(
                RawMessagingItem(
                    sender = "Lukas",
                    text = "Treffen um 20:30 Uhr vorm Kino",
                    timestamp = t0 + 60_000L,
                ),
            ),
        )
        repository.processNotification(editedPayload)

        val editedList = repository.editedMessages.first()
        assertEquals(1, editedList.size)
        val msg = editedList.first()
        assertTrue(msg.isEdited)
        assertEquals("Treffen um 19:00 Uhr vorm Kino", msg.originalText)
        assertEquals("Treffen um 20:30 Uhr vorm Kino", msg.text)

        val history = repository.decodeEditHistory(msg.editHistoryJson)
        assertEquals(1, history.size)
        assertEquals("Treffen um 19:00 Uhr vorm Kino", history.first().text)
    }

    @Test
    fun `tools helper normalizes phone numbers and builds spoiler messages`() {
        assertEquals(
            "4917612345678",
            WhatsAppToolsHelper.normalizePhoneNumber("+49", "0176 1234 5678"),
        )
        assertEquals(
            "43664987654",
            WhatsAppToolsHelper.normalizePhoneNumber("+49", "+43 664 987654"),
        )
        assertNull(WhatsAppToolsHelper.normalizePhoneNumber("+49", "123"))

        val spoiler = WhatsAppToolsHelper.buildSpoilerMessage("Achtung Spoiler", "Darth Vader ist Lukes Vater")
        assertTrue(spoiler.startsWith("Achtung Spoiler"))
        assertTrue(spoiler.endsWith("Darth Vader ist Lukes Vater"))
        assertTrue(spoiler.length > 3000)

        val invisible = WhatsAppToolsHelper.buildInvisibleMessage(3)
        assertEquals("\u2800\n\u2800\n\u2800", invisible)

        val boldSans = WhatsAppToolsHelper.applyUnicodeFont(
            "Hikari",
            WhatsAppToolsHelper.UnicodeFontStyle.BOLD_SANS,
        )
        assertEquals("𝗛𝗶𝗸𝗮𝗿𝗶", boldSans)
    }

    @Test
    fun `chat analyzer parses exported WhatsApp conversation and computes wrapped stats`() {
        val sample = WhatsAppChatAnalyzer.buildSampleExportText()
        val analysis = WhatsAppChatAnalyzer.analyzeText(sample)
        assertNotNull(analysis)
        analysis!!
        assertEquals(10, analysis.totalMessages)
        assertEquals(2, analysis.totalMedia)
        assertEquals(2, analysis.activeDaysCount)
        assertEquals(3, analysis.participants.size)
        assertEquals("Leon", analysis.participants.first().name)
        assertTrue(analysis.topEmojis.any { it.first == "🔥" })
    }
}

private class FakeWhatsAppDao : WhatsAppDao {
    private val items = MutableStateFlow<List<WhatsAppMessageEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(message: WhatsAppMessageEntity): Long {
        val assigned = if (message.id == 0L) message.copy(id = nextId++) else message
        items.value = items.value.filterNot { it.id == assigned.id } + assigned
        return assigned.id
    }

    override suspend fun update(message: WhatsAppMessageEntity) {
        items.value = items.value.map { if (it.id == message.id) message else it }
    }

    override fun observeAllMessages(): Flow<List<WhatsAppMessageEntity>> =
        items.map { list -> list.sortedByDescending { it.timestamp } }

    override fun observeDeletedMessages(): Flow<List<WhatsAppMessageEntity>> =
        items.map { list -> list.filter { it.isDeleted }.sortedByDescending { it.deletedAt ?: it.timestamp } }

    override fun observeEditedMessages(): Flow<List<WhatsAppMessageEntity>> =
        items.map { list -> list.filter { it.isEdited }.sortedByDescending { it.editedAt ?: it.timestamp } }

    override fun observeChatMessages(chatKey: String): Flow<List<WhatsAppMessageEntity>> =
        items.map { list -> list.filter { it.chatKey == chatKey }.sortedBy { it.timestamp } }

    override suspend fun getRecentMessagesForChat(chatKey: String, limit: Int): List<WhatsAppMessageEntity> =
        items.value.filter { it.chatKey == chatKey }.sortedByDescending { it.timestamp }.take(limit)

    override suspend fun getRecentMessagesByNotificationKey(
        notificationKey: String,
        limit: Int,
    ): List<WhatsAppMessageEntity> =
        items.value.filter { it.notificationKey == notificationKey }.sortedByDescending { it.timestamp }.take(limit)

    override suspend fun getById(id: Long): WhatsAppMessageEntity? =
        items.value.firstOrNull { it.id == id }

    override suspend fun markChatRead(chatKey: String) {
        items.value = items.value.map {
            if (it.chatKey == chatKey) it.copy(isReadInHikari = true) else it
        }
    }

    override suspend fun markAllRead() {
        items.value = items.value.map { it.copy(isReadInHikari = true) }
    }

    override suspend fun deleteById(id: Long) {
        items.value = items.value.filterNot { it.id == id }
    }

    override suspend fun deleteChat(chatKey: String) {
        items.value = items.value.filterNot { it.chatKey == chatKey }
    }

    override suspend fun deleteAll() {
        items.value = emptyList()
    }

    override fun observeDeletedCount(): Flow<Int> =
        items.map { list -> list.count { it.isDeleted } }

    override fun observeEditedCount(): Flow<Int> =
        items.map { list -> list.count { it.isEdited } }
}
