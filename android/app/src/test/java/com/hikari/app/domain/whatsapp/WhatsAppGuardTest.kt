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

    // ── Hilfen ───────────────────────────────────────────────────────────────────

    /** Sekundengenaue Zeitstempel wie bei echtem WhatsApp. */
    private fun sec(base: Long, offsetSeconds: Int) = (base / 1000L) * 1000L + offsetSeconds * 1000L

    private fun msg(sender: String?, text: String, timestamp: Long) =
        RawMessagingItem(sender = sender, text = text, timestamp = timestamp)

    private fun payload(
        tag: String,
        chat: String,
        items: List<RawMessagingItem>,
        group: String? = null,
        text: String? = null,
        postTime: Long = System.currentTimeMillis(),
    ) = RawNotificationPayload(
        packageName = "com.whatsapp",
        notificationKey = "0|com.whatsapp|1|$tag|10426",
        notificationTag = tag,
        title = if (group != null) "$group (${items.size} Nachrichten): $chat" else chat,
        text = text ?: items.lastOrNull()?.text,
        bigText = null,
        subText = null,
        conversationTitle = group?.let { "$it (${items.size} Nachrichten)" },
        isGroupConversation = group != null,
        isSummary = false,
        isOngoing = false,
        category = "msg",
        postTime = postTime,
        messagingStyleItems = items,
    )

    private suspend fun post(tag: String, chat: String, vararg items: RawMessagingItem) =
        repository.processNotification(payload(tag, chat, items.toList()))

    private fun clear(tag: String) = repository.onNotificationCleared("0|com.whatsapp|1|$tag|10426")

    private suspend fun deletedTexts() = repository.deletedMessages.first().map { it.text }
    private suspend fun editedPairs() = repository.editedMessages.first().map { "${it.originalText} -> ${it.text}" }

    private val placeholderText = "‎🚫️ Diese Nachricht wurde gelöscht"

    // ── Löschungen ───────────────────────────────────────────────────────────────

    @Test
    fun `normal messages stay invisible, a placeholder at the same identity rescues the original`() = runTest {
        val base = System.currentTimeMillis() - 60_000L
        post("anna", "Anna", msg("Anna", "Hey, hast du kurz Zeit?", sec(base, 0)))
        assertTrue("Normale Nachrichten sind nie sichtbar", repository.allMessages.first().isEmpty())

        post("anna", "Anna", msg("Anna", placeholderText, sec(base, 0)))

        val deleted = repository.deletedMessages.first()
        assertEquals(listOf("Hey, hast du kurz Zeit?"), deleted.map { it.text })
        assertEquals("Anna", deleted.first().chatTitle)
        assertNotNull(deleted.first().deletedAt)
    }

    @Test
    fun `one real deletion stays one entry however often the stack is posted again`() = runTest {
        val base = System.currentTimeMillis() - 3_600_000L
        post(
            "mia", "Mia",
            msg("Mia", "Guten Morgen", sec(base, 0)),
            msg("Mia", "Bist du schon wach?", sec(base, 1)),
            msg("Mia", "Ich bringe Brötchen mit", sec(base, 2)),
        )
        clear("mia") // gelesen

        post("mia", "Mia", msg("Mia", "Das war nicht für dich", sec(base, 600)))
        val placeholder = msg("Mia", placeholderText, sec(base, 600))
        post("mia", "Mia", placeholder)

        // Mia schreibt weiter: jeder neue Stapel enthält den Platzhalter erneut.
        val stack = mutableListOf(placeholder)
        for (i in 1..4) {
            stack += msg("Mia", "Weiter gehts $i", sec(base, 610 + i))
            repository.processNotification(payload("mia", "Mia", stack.toList()))
        }

        assertEquals(listOf("Das war nicht für dich"), deletedTexts())
    }

    @Test
    fun `a repeated short text after reading never marks another message as deleted`() = runTest {
        val base = System.currentTimeMillis() - 3_600_000L
        post("tom", "Tom", msg("Tom", "Ok", sec(base, 0)), msg("Tom", "Bin um 8 da", sec(base, 30)))
        clear("tom")
        post("tom", "Tom", msg("Tom", "Ok", sec(base, 3000)))
        post("tom", "Tom", msg("Tom", "Ok", sec(base, 3000)), msg("Tom", "📷 Foto", sec(base, 3010)))

        assertEquals(emptyList<String>(), deletedTexts())
        assertEquals(emptyList<String>(), editedPairs())
    }

    @Test
    fun `reading a chat or swiping the notification away is never a deletion`() = runTest {
        val base = System.currentTimeMillis() - 600_000L
        post("lea", "Lea", msg("Lea", "Erste", sec(base, 0)), msg("Lea", "Zweite", sec(base, 5)))
        clear("lea")
        post("lea", "Lea", msg("Lea", "Dritte", sec(base, 120)))
        post("lea", "Lea", msg("Lea", "Dritte", sec(base, 120)), msg("Lea", "Vierte", sec(base, 130)))
        clear("lea")

        assertTrue(repository.allMessages.first().isEmpty())
    }

    @Test
    fun `a message missing behind an older unread one is deleted - live and after a fresh post`() = runTest {
        val base = System.currentTimeMillis() - 600_000L
        val a = msg("Tom", "Erste Nachricht", sec(base, 0))
        val b = msg("Tom", "Zweite geheime Nachricht", sec(base, 5))
        val c = msg("Tom", "Dritte Nachricht", sec(base, 9))

        // Live: der Stapel verliert die mittlere Nachricht.
        post("tom", "Tom", a, b, c)
        post("tom", "Tom", a, c)
        assertEquals(listOf("Zweite geheime Nachricht"), deletedTexts())

        // Nach Wegwischen: der neue Stapel zeigt die ältere noch, die neuere fehlt.
        val d = msg("Ida", "Bleibt stehen", sec(base, 20))
        val e = msg("Ida", "Wird gelöscht", sec(base, 25))
        post("ida", "Ida", d, e)
        clear("ida")
        post("ida", "Ida", d)
        assertEquals(setOf("Zweite geheime Nachricht", "Wird gelöscht"), deletedTexts().toSet())
    }

    @Test
    fun `the oldest messages dropping out of a full stack are not deletions`() = runTest {
        val base = System.currentTimeMillis() - 600_000L
        val all = (1..9).map { msg("Kai", "Nachricht $it", sec(base, it * 10)) }

        repository.processNotification(payload("kai", "Kai", all.subList(0, 7)))
        repository.processNotification(payload("kai", "Kai", all.subList(1, 8)))
        repository.processNotification(payload("kai", "Kai", all.subList(2, 9)))

        assertEquals(emptyList<String>(), deletedTexts())
    }

    @Test
    fun `a fresh placeholder replacing the only unread message rescues it even with another timestamp`() = runTest {
        val base = System.currentTimeMillis() - 60_000L
        post("eva", "Eva", msg("Eva", "Ups, falscher Chat", sec(base, 0)))
        post("eva", "Eva", msg("Eva", placeholderText, sec(base, 7)))
        post("eva", "Eva", msg("Eva", placeholderText, sec(base, 7)), msg("Eva", "Sorry", sec(base, 20)))

        assertEquals(listOf("Ups, falscher Chat"), deletedTexts())
    }

    @Test
    fun `placeholder shown only as notification text rescues the vanished message once`() = runTest {
        val base = System.currentTimeMillis() - 60_000L
        val a = msg("Noah", "Bleibt", sec(base, 0))
        val b = msg("Noah", "Verschwindet", sec(base, 4))
        post("noah", "Noah", a, b)

        repeat(2) {
            repository.processNotification(
                payload("noah", "Noah", listOf(a), text = placeholderText, postTime = System.currentTimeMillis() + it),
            )
        }

        assertEquals(listOf("Verschwindet"), deletedTexts())
    }

    @Test
    fun `without proof nothing is guessed - a lone placeholder for an unknown message stays empty`() = runTest {
        val base = System.currentTimeMillis() - 3_600_000L
        post("jan", "Jan", msg("Jan", "Alte gelesene Nachricht", sec(base, 0)))
        clear("jan")

        // Platzhalter für eine Nachricht, die Hikari nie gesehen hat (Chat war offen).
        post("jan", "Jan", msg("Jan", placeholderText, sec(base, 900)))

        assertTrue(repository.allMessages.first().isEmpty())
    }

    @Test
    fun `a single placeholder cannot stand for several vanished messages`() = runTest {
        val base = System.currentTimeMillis() - 60_000L
        post("rita", "Rita", msg("Rita", "Schon gelesen eins", sec(base, 0)), msg("Rita", "Schon gelesen zwei", sec(base, 3)))
        // Der alte Stapel hängt noch nach, als eine nie gesehene Nachricht gelöscht wird.
        post("rita", "Rita", msg("Rita", placeholderText, sec(base, 40)))

        assertTrue(repository.allMessages.first().isEmpty())
    }

    @Test
    fun `withdrawn reactions and own replies are not tracked`() = runTest {
        val base = System.currentTimeMillis() - 60_000L
        val a = msg("Ole", "Wie wars?", sec(base, 0))
        post("ole", "Ole", a, msg("Ole", "Hat mit ❤️ auf „Super“ reagiert", sec(base, 5)))
        post("ole", "Ole", a) // Reaktion zurückgenommen
        post("ole", "Ole", a, msg(null, "Meine Antwort aus der Benachrichtigung", sec(base, 30)))
        post("ole", "Ole", a)

        assertTrue(repository.allMessages.first().isEmpty())
    }

    @Test
    fun `two chats with the same name never mix`() = runTest {
        val base = System.currentTimeMillis() - 60_000L
        post("tag_privat", "Max", msg("Max", "Privat eins", sec(base, 0)), msg("Max", "Privat zwei", sec(base, 3)))
        post("tag_arbeit", "Max", msg("Max", "Arbeit eins", sec(base, 0)))
        post("tag_arbeit", "Max", msg("Max", placeholderText, sec(base, 0)))

        assertEquals(listOf("Arbeit eins"), deletedTexts())
    }

    @Test
    fun `a contact getting a new display name does not delete anything`() = runTest {
        val base = System.currentTimeMillis() - 60_000L
        post("nick", "~Nick", msg("~Nick", "Hi", sec(base, 0)), msg("~Nick", "Bin der Neue", sec(base, 3)))
        post(
            "nick", "Niklas",
            msg("Niklas", "Hi", sec(base, 0)),
            msg("Niklas", "Bin der Neue", sec(base, 3)),
            msg("Niklas", "Danke fürs Speichern", sec(base, 40)),
        )

        assertTrue(repository.allMessages.first().isEmpty())
    }

    @Test
    fun `old unread messages that expire on their own are not counted as deleted`() = runTest {
        val now = System.currentTimeMillis()
        val a = msg("Uwe", "Selbstlöschend eins", sec(now - 25L * 3_600_000L, 0))
        val b = msg("Uwe", "Selbstlöschend zwei", sec(now - 24L * 3_600_000L, 0))
        post("uwe", "Uwe", a, b)
        post("uwe", "Uwe", b)

        assertEquals(emptyList<String>(), deletedTexts())
    }

    @Test
    fun `a message that shows up again loses its deleted mark`() = runTest {
        val base = System.currentTimeMillis() - 60_000L
        val a = msg("Pia", "Eins", sec(base, 0))
        val b = msg("Pia", "Zwei", sec(base, 4))
        post("pia", "Pia", a, b)
        post("pia", "Pia", a)
        assertEquals(listOf("Zwei"), deletedTexts())

        post("pia", "Pia", a, b)
        assertEquals(emptyList<String>(), deletedTexts())
    }

    // ── Bearbeitungen ────────────────────────────────────────────────────────────

    @Test
    fun `a text replaced in place is an edit and keeps its history - a later deletion keeps both`() = runTest {
        val base = System.currentTimeMillis() - 120_000L
        val other = msg("Leon", "Bis später", sec(base, 0))
        post("leon", "Leon", other, msg("Leon", "Ich überweise dir 50 €", sec(base, 10)))
        post("leon", "Leon", other, msg("Leon", "Ich überweise dir 25 €", sec(base, 10)))

        var edited = repository.editedMessages.first()
        assertEquals(1, edited.size)
        assertEquals("Ich überweise dir 50 €", edited.first().originalText)
        assertEquals("Ich überweise dir 25 €", edited.first().text)

        post("leon", "Leon", other, msg("Leon", "Ich überweise dir 10 €", sec(base, 10)))
        edited = repository.editedMessages.first()
        assertEquals(1, edited.size)
        assertEquals("Ich überweise dir 50 €", edited.first().originalText)
        assertEquals("Ich überweise dir 10 €", edited.first().text)
        assertEquals(
            listOf("Ich überweise dir 50 €", "Ich überweise dir 25 €"),
            repository.decodeEditHistory(edited.first().editHistoryJson).map { it.text },
        )

        post("leon", "Leon", other, msg("Leon", placeholderText, sec(base, 10)))
        val entry = repository.allMessages.first().single()
        assertTrue(entry.isDeleted && entry.isEdited)
        assertEquals("Ich überweise dir 10 €", entry.text)
        assertTrue("Die unveränderte Nachricht bleibt unsichtbar", repository.allMessages.first().none { it.text == "Bis später" })
    }

    @Test
    fun `more photos arriving is never an edit`() = runTest {
        val ts = sec(System.currentTimeMillis() - 600_000L, 0)

        // WhatsApp fasst Bilder zusammen: "Foto" -> "2 Fotos" -> "3 Fotos".
        for (label in listOf("📷 Foto", "📷 2 Fotos", "📷 3 Fotos")) {
            post("lea", "Lea", msg("Lea", label, ts))
        }
        // Zweites Bild mit eigener Unterschrift in derselben Sekunde — erst daneben, dann nach dem Lesen.
        post("ben", "Ben", msg("Ben", "📷 Schau mal hier", ts))
        post("ben", "Ben", msg("Ben", "📷 Schau mal hier", ts), msg("Ben", "📷 Und das hier", ts))
        clear("ben")
        post("ben", "Ben", msg("Ben", "📷 Noch eins", ts))
        // Sprachnachricht nach Foto, Sticker nach Text.
        post("kim", "Kim", msg("Kim", "📷 Foto", ts))
        post("kim", "Kim", msg("Kim", "🎤 Sprachnachricht (0:12)", ts))

        assertEquals(emptyList<String>(), editedPairs())
        assertEquals(emptyList<String>(), deletedTexts())
    }

    @Test
    fun `a caption edit is recognised, a waiting placeholder turning into text is not`() = runTest {
        val ts = sec(System.currentTimeMillis() - 600_000L, 0)
        post("zoe", "Zoe", msg("Zoe", "📷 Unser Hotel in Rom", ts))
        post("zoe", "Zoe", msg("Zoe", "📷 Unser Hotel in Mailand", ts))
        post("ari", "Ari", msg("Ari", "Warte auf diese Nachricht. Das kann einen Moment dauern.", ts))
        post("ari", "Ari", msg("Ari", "Hallo!", ts))

        assertEquals(listOf("📷 Unser Hotel in Rom -> 📷 Unser Hotel in Mailand"), editedPairs())
    }

    // ── Medien & Aufbewahrung ────────────────────────────────────────────────────

    @Test
    fun `media messages ask for their file and keep it when deleted`() = runTest {
        val ts = sec(System.currentTimeMillis() - 60_000L, 0)
        val result = post("fee", "Fee", msg("Fee", "📷 Foto", ts))
        val pending = result.pendingMedia.single()
        assertEquals("photo", pending.mediaType)

        val file = File.createTempFile("wa_photo_", ".jpg").apply { writeText("bild") }
        repository.attachMedia(pending.rowId, file.absolutePath)
        post("fee", "Fee", msg("Fee", placeholderText, ts))

        val deleted = repository.deletedMessages.first().single()
        assertEquals("photo", deleted.mediaType)
        assertEquals(file.absolutePath, deleted.mediaPreviewPath)
        file.delete()
    }

    @Test
    fun `log rows expire after a day unless their message is still in a visible stack`() = runTest {
        val now = System.currentTimeMillis()
        val old = sec(now - 3L * 24L * 3_600_000L, 0)
        post("alt", "Alma", msg("Alma", "Seit drei Tagen ungelesen", old))
        post("weg", "Bela", msg("Bela", "Längst gelesen", old))
        clear("weg")

        repository.pruneExpiredData(nowMs = now)
        assertEquals(listOf("Seit drei Tagen ungelesen"), fakeDao.all().map { it.text })

        post("alt", "Alma", msg("Alma", placeholderText, old))
        assertEquals(listOf("Seit drei Tagen ungelesen"), deletedTexts())
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

    fun all(): List<WhatsAppMessageEntity> = items.value

    override suspend fun insert(message: WhatsAppMessageEntity): Long {
        val assigned = if (message.id == 0L) message.copy(id = nextId++) else message
        items.value = items.value.filterNot { it.id == assigned.id } + assigned
        return assigned.id
    }

    override suspend fun update(message: WhatsAppMessageEntity) {
        items.value = items.value.map { if (it.id == message.id) message else it }
    }

    override fun observeAllMessages(): Flow<List<WhatsAppMessageEntity>> =
        items.map { list -> list.filter { it.isDeleted || it.isEdited }.sortedByDescending { it.deletedAt ?: it.editedAt ?: it.timestamp } }

    override fun observeDeletedMessages(): Flow<List<WhatsAppMessageEntity>> =
        items.map { list -> list.filter { it.isDeleted }.sortedByDescending { it.deletedAt ?: it.timestamp } }

    override fun observeEditedMessages(): Flow<List<WhatsAppMessageEntity>> =
        items.map { list -> list.filter { it.isEdited }.sortedByDescending { it.editedAt ?: it.timestamp } }

    override suspend fun getMessagesForChatSince(chatKey: String, sinceTimestamp: Long): List<WhatsAppMessageEntity> =
        items.value.filter { it.chatKey == chatKey && it.timestamp >= sinceTimestamp }.sortedBy { it.id }

    override suspend fun getById(id: Long): WhatsAppMessageEntity? =
        items.value.firstOrNull { it.id == id }

    override suspend fun deleteById(id: Long) {
        items.value = items.value.filterNot { it.id == id }
    }

    override suspend fun deleteAll() {
        items.value = emptyList()
    }

    override suspend fun getExpiredStagingMessages(stagingCutoffTimestamp: Long): List<WhatsAppMessageEntity> =
        items.value.filter { !it.isDeleted && !it.isEdited && it.timestamp < stagingCutoffTimestamp }

    override suspend fun deleteByIds(ids: List<Long>) {
        items.value = items.value.filterNot { it.id in ids }
    }

    override suspend fun markAllSeen() {
        items.value = items.value.map { if (it.isDeleted || it.isEdited) it.copy(isReadInHikari = true) else it }
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
        items.value.mapNotNull { it.mediaPreviewPath }

    override suspend fun getRescuedMediaPaths(): List<String> =
        items.value.filter { it.isDeleted || it.isEdited }.mapNotNull { it.mediaPreviewPath }
}
