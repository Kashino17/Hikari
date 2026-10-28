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
import java.io.File

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
            every { filesDir } returns File(System.getProperty("java.io.tmpdir"))
        }
        repository = WhatsAppRepository(context, fakeDao)
    }

    @Test
    fun `parser detects deleted message placeholders in multiple languages and with unicode marks`() {
        assertTrue(WhatsAppNotificationParser.isDeletedPlaceholder("Diese Nachricht wurde gelöscht"))
        assertTrue(WhatsAppNotificationParser.isDeletedPlaceholder("🚫 Diese Nachricht wurde gelöscht."))
        assertTrue(WhatsAppNotificationParser.isDeletedPlaceholder("\u200E🚫\uFE0F Diese Nachricht wurde gelöscht."))
        assertTrue(WhatsAppNotificationParser.isDeletedPlaceholder("\u200EDiese Nachricht wurde gelöscht\u200F"))
        assertTrue(WhatsAppNotificationParser.isDeletedPlaceholder("Anna: \u200E🚫\uFE0F Diese Nachricht wurde gelöscht"))
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
    fun `normal messages are NOT saved to database, only deleted messages are persisted`() = runTest {
        val t0 = System.currentTimeMillis() - 60_000L

        // 1. Eingehende Nachricht von Anna -> darf NICHT in der Datenbank landen!
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
        assertTrue("Normale Nachrichten dürfen nicht in der DB gespeichert werden", savedBeforeDelete.isEmpty())

        // 2. Anna löscht die Nachricht für alle -> JETZT wird sie aus dem RAM-Puffer in die DB gerettet!
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

        val deletedList = repository.deletedMessages.first()
        assertEquals(1, deletedList.size)
        val preserved = deletedList.first()
        assertEquals("Hier ist der geheime Code: 9481", preserved.text)
        assertTrue(preserved.isDeleted)
        assertNotNull(preserved.deletedAt)
    }

    @Test
    fun `rescues deleted message when WhatsApp uses invisible LRM marks, summary flags or top-level text`() = runTest {
        val t0 = System.currentTimeMillis() - 45_000L

        // 1. Eingehende Nachricht mit Unicode-Bidi-Markierungen im Namen
        val incoming = RawNotificationPayload(
            packageName = "com.whatsapp",
            notificationKey = "0|com.whatsapp|1|49176@s.whatsapp.net|101",
            notificationTag = "49176@s.whatsapp.net",
            title = "\u200EMax (2 neue Nachrichten)",
            text = "Diesen Satz lösche ich gleich wieder!",
            bigText = "Hallo\nDiesen Satz lösche ich gleich wieder!",
            subText = null,
            conversationTitle = null,
            isGroupConversation = false,
            isSummary = false,
            isOngoing = false,
            category = "msg",
            postTime = t0,
            messagingStyleItems = listOf(
                RawMessagingItem(sender = "\u200EMax", text = "Hallo", timestamp = t0 - 10_000L),
                RawMessagingItem(sender = "\u200EMax", text = "Diesen Satz lösche ich gleich wieder!", timestamp = t0),
            ),
        )
        repository.processNotification(incoming)
        assertTrue(repository.allMessages.first().isEmpty())

        // 2. Max löscht die 2. Nachricht:
        //    WhatsApp entfernt die 2. Nachricht aus EXTRA_MESSAGES (nur "Hallo" bleibt drin)
        //    und setzt EXTRA_TEXT auf "\u200E🚫\uFE0F Diese Nachricht wurde gelöscht"
        val deletionPayload = incoming.copy(
            title = "Max",
            text = "\u200E🚫\uFE0F Diese Nachricht wurde gelöscht",
            bigText = "Hallo\n\u200E🚫\uFE0F Diese Nachricht wurde gelöscht",
            postTime = t0 + 5_000L,
            messagingStyleItems = listOf(
                RawMessagingItem(sender = "Max", text = "Hallo", timestamp = t0 - 10_000L),
            ),
        )
        repository.processNotification(deletionPayload)

        val deletedList = repository.deletedMessages.first()
        assertEquals(1, deletedList.size)
        assertEquals("Diesen Satz lösche ich gleich wieder!", deletedList.first().text)
    }

    @Test
    fun `rescues deleted messages when stack shrinks without placeholder or when testing identical text repeatedly`() = runTest {
        val t0 = System.currentTimeMillis() - 60_000L

        // 1. Zwei ungelesene Nachrichten im Chat: "Erste Nachricht" und "Zweite geheime Nachricht"
        val twoMsgs = RawNotificationPayload(
            packageName = "com.whatsapp",
            notificationKey = "notif_shrink",
            notificationTag = "shrink",
            title = "Tom",
            text = "Zweite geheime Nachricht",
            bigText = "Erste Nachricht\nZweite geheime Nachricht",
            subText = null,
            conversationTitle = null,
            isGroupConversation = false,
            isSummary = false,
            isOngoing = false,
            category = "msg",
            postTime = t0,
            messagingStyleItems = listOf(
                RawMessagingItem(sender = "Tom", text = "Erste Nachricht", timestamp = t0 - 5_000L),
                RawMessagingItem(sender = "Tom", text = "Zweite geheime Nachricht", timestamp = t0),
            ),
        )
        repository.processNotification(twoMsgs)

        // Selbst wenn previousSnapshot zwischendurch geleert wurde (z. B. durch Cancel+Repost):
        repository.onNotificationCleared("notif_shrink")

        // Tom löscht "Zweite geheime Nachricht", WhatsApp schickt nur noch ["Erste Nachricht"] ohne Platzhaltertext!
        val shrunkPayload = twoMsgs.copy(
            text = "Erste Nachricht",
            bigText = "Erste Nachricht",
            postTime = t0 + 4_000L,
            messagingStyleItems = listOf(
                RawMessagingItem(sender = "Tom", text = "Erste Nachricht", timestamp = t0 - 5_000L),
            ),
        )
        repository.processNotification(shrunkPayload)

        val deletedAfterShrink = repository.deletedMessages.first()
        assertEquals(1, deletedAfterShrink.size)
        assertEquals("Zweite geheime Nachricht", deletedAfterShrink.first().text)

        // 2. Wiederholter Test mit exakt demselben Wort ("Test") innerhalb von 15 Sekunden:
        val test1 = twoMsgs.copy(
            notificationKey = "notif_repeat",
            title = "Lisa",
            text = "Test",
            postTime = t0 + 10_000L,
            messagingStyleItems = listOf(
                RawMessagingItem(sender = "Lisa", text = "Test", timestamp = t0 + 10_000L),
            ),
        )
        repository.processNotification(test1)
        repository.processNotification(
            test1.copy(
                text = "🚫 Diese Nachricht wurde gelöscht",
                postTime = t0 + 12_000L,
                messagingStyleItems = listOf(
                    RawMessagingItem(sender = "Lisa", text = "🚫 Diese Nachricht wurde gelöscht", timestamp = t0 + 10_000L),
                ),
            ),
        )

        val test2 = test1.copy(
            text = "Test",
            postTime = t0 + 20_000L,
            messagingStyleItems = listOf(
                RawMessagingItem(sender = "Lisa", text = "Test", timestamp = t0 + 20_000L),
            ),
        )
        repository.processNotification(test2)
        repository.processNotification(
            test2.copy(
                text = "🚫 Diese Nachricht wurde gelöscht",
                postTime = t0 + 22_000L,
                messagingStyleItems = listOf(
                    RawMessagingItem(sender = "Lisa", text = "🚫 Diese Nachricht wurde gelöscht", timestamp = t0 + 20_000L),
                ),
            ),
        )

        val lisaDeleted = repository.deletedMessages.first().filter { it.senderName == "Lisa" }
        assertEquals("Beide 'Test'-Nachrichten müssen gerettet werden", 2, lisaDeleted.size)
    }

    @Test
    fun `rescues single message when retracted by WhatsApp via REASON_APP_CANCEL or summary notification`() = runTest {
        val t0 = System.currentTimeMillis() - 30_000L

        val singleMsg = RawNotificationPayload(
            packageName = "com.whatsapp",
            notificationKey = "notif_retract",
            notificationTag = "retract_tag",
            title = "Paul",
            text = "Kurz geschickt und sofort zurückgezogen",
            bigText = null,
            subText = null,
            conversationTitle = null,
            isGroupConversation = false,
            isSummary = false,
            isOngoing = false,
            category = "msg",
            postTime = t0,
            messagingStyleItems = listOf(
                RawMessagingItem(sender = "Paul", text = "Kurz geschickt und sofort zurückgezogen", timestamp = t0),
            ),
        )
        repository.processNotification(singleMsg)
        assertTrue(repository.deletedMessages.first().isEmpty())

        // WhatsApp zieht die Benachrichtigung komplett zurück (REASON_APP_CANCEL):
        repository.onNotificationRetractedByApp("notif_retract", nowMs = t0 + 3_000L)

        val deletedList = repository.deletedMessages.first()
        assertEquals(1, deletedList.size)
        assertEquals("Kurz geschickt und sofort zurückgezogen", deletedList.first().text)
    }

    @Test
    fun `repository detects edited message from RAM cache and persists only when edited`() = runTest {
        val t0 = System.currentTimeMillis() - 120_000L

        // 1. Ursprüngliche Nachricht von Lukas -> nur im RAM, DB bleibt leer
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
        assertTrue(repository.allMessages.first().isEmpty())

        // 2. Lukas bearbeitet die Nachricht (MessagingStyle behält den Original-Timestamp t0)
        val editedPayload = initialPayload.copy(
            text = "Treffen um 20:30 Uhr vorm Kino",
            postTime = t0 + 60_000L,
            messagingStyleItems = listOf(
                RawMessagingItem(
                    sender = "Lukas",
                    text = "Treffen um 20:30 Uhr vorm Kino",
                    timestamp = t0,
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
    }

    @Test
    fun `separate photo and audio messages or separate single messages are NEVER falsely marked as edited`() = runTest {
        val t0 = System.currentTimeMillis() - 90_000L

        // 1. Freund schickt ein Bild ("📷 Foto")
        val photoPayload = RawNotificationPayload(
            packageName = "com.whatsapp",
            notificationKey = "notif_friend",
            notificationTag = "friend",
            title = "Kumpel",
            text = "📷 Foto",
            bigText = null,
            subText = null,
            conversationTitle = null,
            isGroupConversation = false,
            isSummary = false,
            isOngoing = false,
            category = "msg",
            postTime = t0,
            messagingStyleItems = listOf(
                RawMessagingItem(sender = "Kumpel", text = "📷 Foto", timestamp = t0),
            ),
        )
        repository.processNotification(photoPayload)

        // 2. Freund schickt danach eine Sprachnachricht ("🎤 Sprachnachricht (0:07)")
        val audioPayload = photoPayload.copy(
            text = "🎤 Sprachnachricht (0:07)",
            postTime = t0 + 20_000L,
            messagingStyleItems = listOf(
                RawMessagingItem(sender = "Kumpel", text = "🎤 Sprachnachricht (0:07)", timestamp = t0 + 20_000L),
            ),
        )
        repository.processNotification(audioPayload)

        // 3. Freund schickt danach noch zwei einzelne Textnachrichten zu unterschiedlichen Zeiten
        val text1 = photoPayload.copy(
            text = "Hast du das gesehen?",
            postTime = t0 + 35_000L,
            messagingStyleItems = listOf(
                RawMessagingItem(sender = "Kumpel", text = "Hast du das gesehen?", timestamp = t0 + 35_000L),
            ),
        )
        repository.processNotification(text1)

        val text2 = photoPayload.copy(
            text = "Gib mir Bescheid!",
            postTime = t0 + 50_000L,
            messagingStyleItems = listOf(
                RawMessagingItem(sender = "Kumpel", text = "Gib mir Bescheid!", timestamp = t0 + 50_000L),
            ),
        )
        repository.processNotification(text2)

        assertTrue(
            "Einzelne Bild-, Audio- oder Folgeschrichten dürfen NIEMALS als Bearbeitet gespeichert werden",
            repository.editedMessages.first().isEmpty(),
        )
        assertTrue(repository.allMessages.first().isEmpty())
    }

    @Test
    fun `pruneExpiredData automatically deletes messages older than 7 days`() = runTest {
        val now = System.currentTimeMillis()
        val eightDaysAgo = now - (8L * 24L * 60L * 60L * 1000L)
        val twoDaysAgo = now - (2L * 24L * 60L * 60L * 1000L)

        fakeDao.insert(
            WhatsAppMessageEntity(
                chatKey = "com.whatsapp:alt",
                chatTitle = "Alt",
                senderName = "Alt",
                text = "Nachricht von vor 8 Tagen",
                isDeleted = true,
                deletedAt = eightDaysAgo,
                timestamp = eightDaysAgo,
            ),
        )
        fakeDao.insert(
            WhatsAppMessageEntity(
                chatKey = "com.whatsapp:frisch",
                chatTitle = "Frisch",
                senderName = "Frisch",
                text = "Nachricht von vor 2 Tagen",
                isDeleted = true,
                deletedAt = twoDaysAgo,
                timestamp = twoDaysAgo,
            ),
        )

        repository.pruneExpiredData(nowMs = now)

        val remaining = repository.deletedMessages.first()
        assertEquals(1, remaining.size)
        assertEquals("Nachricht von vor 2 Tagen", remaining.first().text)
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
        items.map { list -> list.filter { it.isDeleted || it.isEdited }.sortedByDescending { it.timestamp } }

    override fun observeDeletedMessages(): Flow<List<WhatsAppMessageEntity>> =
        items.map { list -> list.filter { it.isDeleted }.sortedByDescending { it.deletedAt ?: it.timestamp } }

    override fun observeEditedMessages(): Flow<List<WhatsAppMessageEntity>> =
        items.map { list -> list.filter { it.isEdited }.sortedByDescending { it.editedAt ?: it.timestamp } }

    override suspend fun getRecentMessagesForChat(chatKey: String, limit: Int): List<WhatsAppMessageEntity> =
        items.value.filter { it.chatKey == chatKey && (it.isDeleted || it.isEdited) }
            .sortedByDescending { it.timestamp }
            .take(limit)

    override suspend fun getById(id: Long): WhatsAppMessageEntity? =
        items.value.firstOrNull { it.id == id }

    override suspend fun deleteById(id: Long) {
        items.value = items.value.filterNot { it.id == id }
    }

    override suspend fun deleteChat(chatKey: String) {
        items.value = items.value.filterNot { it.chatKey == chatKey }
    }

    override suspend fun deleteAll() {
        items.value = emptyList()
    }

    override suspend fun deleteNormalMessages() {
        items.value = items.value.filter { it.isDeleted || it.isEdited }
    }

    override suspend fun getExpiredMessages(cutoffTimestamp: Long): List<WhatsAppMessageEntity> =
        items.value.filter { (it.deletedAt ?: it.editedAt ?: it.timestamp) < cutoffTimestamp }

    override suspend fun deleteExpiredMessages(cutoffTimestamp: Long): Int {
        val before = items.value.size
        items.value = items.value.filterNot { (it.deletedAt ?: it.editedAt ?: it.timestamp) < cutoffTimestamp }
        return before - items.value.size
    }

    override fun observeDeletedCount(): Flow<Int> =
        items.map { list -> list.count { it.isDeleted } }

    override fun observeEditedCount(): Flow<Int> =
        items.map { list -> list.count { it.isEdited } }

    override suspend fun getActiveMediaPaths(): List<String> =
        items.value.filter { it.isDeleted || it.isEdited }.mapNotNull { it.mediaPreviewPath }
}
