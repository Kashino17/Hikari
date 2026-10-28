package com.hikari.app.domain.whatsapp

import android.content.Context
import android.content.SharedPreferences
import com.hikari.app.data.db.WhatsAppDao
import com.hikari.app.data.db.WhatsAppMessageEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Serializable
data class WhatsAppEditVersion(
    val text: String,
    val timestamp: Long,
)

@Serializable
data class DirectChatHistoryItem(
    val phoneNumber: String,
    val displayFormatted: String,
    val lastMessagePreview: String = "",
    val usedAt: Long,
)

data class WhatsAppChatSummary(
    val chatKey: String,
    val chatTitle: String,
    val isGroup: Boolean,
    val packageName: String,
    val lastMessageText: String,
    val lastSenderName: String,
    val lastTimestamp: Long,
    val totalCount: Int,
    val unreadCount: Int,
    val deletedCount: Int,
    val editedCount: Int,
    val avatarPath: String?,
)

data class WhatsAppStats(
    val totalMessages: Int = 0,
    val deletedCount: Int = 0,
    val editedCount: Int = 0,
    val totalChats: Int = 0,
)

sealed class WhatsAppGuardAlert {
    data class MessageDeleted(
        val chatTitle: String,
        val senderName: String,
        val preservedText: String,
        val isGroup: Boolean,
    ) : WhatsAppGuardAlert()

    data class MessageEdited(
        val chatTitle: String,
        val senderName: String,
        val oldText: String,
        val newText: String,
        val isGroup: Boolean,
    ) : WhatsAppGuardAlert()
}

@Singleton
class WhatsAppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: WhatsAppDao,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("hikari_whatsapp_prefs", Context.MODE_PRIVATE)
    }

    private val _alertsEnabled = MutableStateFlow(prefs.getBoolean(KEY_ALERTS_ENABLED, true))
    val alertsEnabled: StateFlow<Boolean> = _alertsEnabled.asStateFlow()

    private val _directChatHistory = MutableStateFlow(loadDirectChatHistory())
    val directChatHistory: StateFlow<List<DirectChatHistoryItem>> = _directChatHistory.asStateFlow()

    private val _guardAlerts = MutableSharedFlow<WhatsAppGuardAlert>(extraBufferCapacity = 16)
    val guardAlerts: SharedFlow<WhatsAppGuardAlert> = _guardAlerts.asSharedFlow()

    /**
     * Merkt sich den aktuellen `EXTRA_MESSAGES`-Stack pro aktivem Chat in der Statusleiste.
     * Solange die Benachrichtigung nicht vom Nutzer weggewischt wurde, können wir so
     * 1:1 erkennen, wenn sich der Text eines Slots ändert (Bearbeitung) oder zu
     * "Diese Nachricht wurde gelöscht" wechselt (Löschung).
     */
    private val activeSnapshots = ConcurrentHashMap<String, List<ParsedWhatsAppMessage>>()

    val allMessages: Flow<List<WhatsAppMessageEntity>> = dao.observeAllMessages()
    val deletedMessages: Flow<List<WhatsAppMessageEntity>> = dao.observeDeletedMessages()
    val editedMessages: Flow<List<WhatsAppMessageEntity>> = dao.observeEditedMessages()
    val deletedCount: Flow<Int> = dao.observeDeletedCount()
    val editedCount: Flow<Int> = dao.observeEditedCount()

    val chatSummaries: Flow<List<WhatsAppChatSummary>> = allMessages.map { messages ->
        messages
            .groupBy { it.chatKey }
            .map { (chatKey, chatMsgs) ->
                val latest = chatMsgs.maxByOrNull { it.timestamp } ?: chatMsgs.first()
                val latestAvatar = chatMsgs.firstOrNull { !it.avatarPath.isNullOrBlank() }?.avatarPath
                WhatsAppChatSummary(
                    chatKey = chatKey,
                    chatTitle = latest.chatTitle,
                    isGroup = latest.isGroup,
                    packageName = latest.packageName,
                    lastMessageText = latest.text,
                    lastSenderName = latest.senderName,
                    lastTimestamp = latest.timestamp,
                    totalCount = chatMsgs.size,
                    unreadCount = chatMsgs.count { !it.isReadInHikari },
                    deletedCount = chatMsgs.count { it.isDeleted },
                    editedCount = chatMsgs.count { it.isEdited },
                    avatarPath = latestAvatar,
                )
            }
            .sortedByDescending { it.lastTimestamp }
    }

    val stats: Flow<WhatsAppStats> = allMessages.map { messages ->
        WhatsAppStats(
            totalMessages = messages.size,
            deletedCount = messages.count { it.isDeleted },
            editedCount = messages.count { it.isEdited },
            totalChats = messages.map { it.chatKey }.distinct().size,
        )
    }

    fun observeChat(chatKey: String): Flow<List<WhatsAppMessageEntity>> =
        dao.observeChatMessages(chatKey)

    fun setAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALERTS_ENABLED, enabled).apply()
        _alertsEnabled.value = enabled
    }

    fun addDirectChatHistory(phoneNumber: String, displayFormatted: String, messagePreview: String) {
        val current = _directChatHistory.value.toMutableList()
        current.removeAll { it.phoneNumber == phoneNumber }
        current.add(
            0,
            DirectChatHistoryItem(
                phoneNumber = phoneNumber,
                displayFormatted = displayFormatted,
                lastMessagePreview = messagePreview.take(60),
                usedAt = System.currentTimeMillis(),
            ),
        )
        val trimmed = current.take(15)
        _directChatHistory.value = trimmed
        runCatching {
            prefs.edit().putString(KEY_DIRECT_HISTORY, json.encodeToString(trimmed)).apply()
        }
    }

    fun clearDirectChatHistory() {
        _directChatHistory.value = emptyList()
        prefs.edit().remove(KEY_DIRECT_HISTORY).apply()
    }

    private fun loadDirectChatHistory(): List<DirectChatHistoryItem> {
        val raw = prefs.getString(KEY_DIRECT_HISTORY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<DirectChatHistoryItem>>(raw) }
            .getOrDefault(emptyList())
    }

    /**
     * Wird aufgerufen, wenn eine StatusBarNotification aus der Leiste entfernt wird
     * (z.B. vom Nutzer weggewischt oder angeklickt).
     */
    fun onNotificationCleared(notificationKey: String?) {
        if (notificationKey.isNullOrBlank()) return
        val keysToRemove = activeSnapshots.entries
            .filter { (_, list) -> list.any { it.notificationKey == notificationKey } }
            .map { it.key }
        keysToRemove.forEach { activeSnapshots.remove(it) }
    }

    /**
     * Verarbeitet eine eingehende WhatsApp-Benachrichtigung und erkennt neue,
     * bearbeitete sowie für alle gelöschte Nachrichten.
     */
    suspend fun processNotification(
        payload: RawNotificationPayload,
        avatarPath: String? = null,
        mediaPreviewPath: String? = null,
    ) = mutex.withLock {
        val parsedList = WhatsAppNotificationParser.parse(payload)
        if (parsedList.isEmpty()) return@withLock

        val chatKey = parsedList.first().chatKey
        val previousSnapshot = activeSnapshots[chatKey].orEmpty()
        val recentInDb = dao.getRecentMessagesForChat(chatKey, limit = 30).toMutableList()
        val now = System.currentTimeMillis()

        for ((index, parsed) in parsedList.withIndex()) {
            val prevAtSlot = previousSnapshot.getOrNull(index)

            if (parsed.isDeletionNotice) {
                // ── FALL 1: Nachricht wurde für alle gelöscht ─────────────────────
                val targetToMark = findDeletionTarget(
                    parsed = parsed,
                    prevAtSlot = prevAtSlot,
                    recentInDb = recentInDb,
                )
                if (targetToMark != null) {
                    if (!targetToMark.isDeleted) {
                        val updated = targetToMark.copy(
                            isDeleted = true,
                            deletedAt = now,
                            isReadInHikari = false,
                            avatarPath = avatarPath ?: targetToMark.avatarPath,
                        )
                        dao.update(updated)
                        replaceInList(recentInDb, updated)
                        if (_alertsEnabled.value) {
                            _guardAlerts.tryEmit(
                                WhatsAppGuardAlert.MessageDeleted(
                                    chatTitle = updated.chatTitle,
                                    senderName = updated.senderName,
                                    preservedText = updated.text,
                                    isGroup = updated.isGroup,
                                ),
                            )
                        }
                    }
                } else {
                    // Falls die Originalnachricht vor Aktivierung des Wächters kam
                    val alreadyLoggedPlaceholder = recentInDb.any {
                        it.isDeleted && abs(it.timestamp - parsed.timestamp) < 15_000L
                    }
                    if (!alreadyLoggedPlaceholder) {
                        val fallback = WhatsAppMessageEntity(
                            chatKey = parsed.chatKey,
                            chatTitle = parsed.chatTitle,
                            senderName = parsed.senderName,
                            isGroup = parsed.isGroup,
                            packageName = parsed.packageName,
                            text = "Nachricht wurde gelöscht (Original vor Wächter-Start gesendet)",
                            isDeleted = true,
                            deletedAt = now,
                            avatarPath = avatarPath,
                            timestamp = parsed.timestamp,
                            notificationKey = parsed.notificationKey,
                        )
                        val newId = dao.insert(fallback)
                        recentInDb.add(0, fallback.copy(id = newId))
                    }
                }
                continue
            }

            // ── FALL 2: Prüfen, ob diese Nachricht bereits exakt so gespeichert ist ──
            val exactDuplicate = recentInDb.firstOrNull { existing ->
                existing.senderName == parsed.senderName &&
                    existing.text == parsed.text &&
                    (abs(existing.timestamp - parsed.timestamp) < 120_000L || existing. id == recentInDb.firstOrNull()?.id)
            }
            if (exactDuplicate != null) {
                // Optional Avatar oder MediaPreview nachreichen, falls beim ersten Mal noch null
                if ((avatarPath != null && exactDuplicate.avatarPath == null) ||
                    (mediaPreviewPath != null && exactDuplicate.mediaPreviewPath == null)
                ) {
                    val enriched = exactDuplicate.copy(
                        avatarPath = avatarPath ?: exactDuplicate.avatarPath,
                        mediaPreviewPath = mediaPreviewPath ?: exactDuplicate.mediaPreviewPath,
                    )
                    dao.update(enriched)
                    replaceInList(recentInDb, enriched)
                }
                continue
            }

            // ── FALL 3: Prüfen, ob eine bestehende Nachricht BEARBEITET wurde ──────
            val editCandidate = findEditTarget(
                parsed = parsed,
                index = index,
                currentSnapshotSize = parsedList.size,
                previousSnapshot = previousSnapshot,
                recentInDb = recentInDb,
                now = now,
            )

            if (editCandidate != null && editCandidate.text != parsed.text) {
                val firstOriginal = editCandidate.originalText ?: editCandidate.text
                val previousVersions = decodeEditHistory(editCandidate.editHistoryJson).toMutableList()
                previousVersions.add(
                    WhatsAppEditVersion(
                        text = editCandidate.text,
                        timestamp = editCandidate.editedAt ?: editCandidate.timestamp,
                    ),
                )
                val updated = editCandidate.copy(
                    text = parsed.text,
                    originalText = firstOriginal,
                    editHistoryJson = json.encodeToString(previousVersions),
                    isEdited = true,
                    editedAt = now,
                    isReadInHikari = false,
                    avatarPath = avatarPath ?: editCandidate.avatarPath,
                    mediaPreviewPath = mediaPreviewPath ?: editCandidate.mediaPreviewPath,
                )
                dao.update(updated)
                replaceInList(recentInDb, updated)
                if (_alertsEnabled.value) {
                    _guardAlerts.tryEmit(
                        WhatsAppGuardAlert.MessageEdited(
                            chatTitle = updated.chatTitle,
                            senderName = updated.senderName,
                            oldText = editCandidate.text,
                            newText = updated.text,
                            isGroup = updated.isGroup,
                        ),
                    )
                }
                continue
            }

            // ── FALL 4: Reguläre neue Nachricht speichern ──────────────────────────
            val newEntity = WhatsAppMessageEntity(
                chatKey = parsed.chatKey,
                chatTitle = parsed.chatTitle,
                senderName = parsed.senderName,
                isGroup = parsed.isGroup,
                packageName = parsed.packageName,
                text = parsed.text,
                originalText = if (parsed.isExplicitEdit) parsed.text else null,
                isEdited = parsed.isExplicitEdit,
                editedAt = if (parsed.isExplicitEdit) now else null,
                mediaType = parsed.mediaType,
                mediaPreviewPath = mediaPreviewPath,
                avatarPath = avatarPath,
                timestamp = parsed.timestamp,
                notificationKey = parsed.notificationKey,
                isReadInHikari = false,
            )
            val insertedId = dao.insert(newEntity)
            recentInDb.add(0, newEntity.copy(id = insertedId))
        }

        activeSnapshots[chatKey] = parsedList
    }

    /**
     * Findet die passende gespeicherte Originalnachricht, wenn WhatsApp
     * "Diese Nachricht wurde gelöscht" meldet.
     */
    private fun findDeletionTarget(
        parsed: ParsedWhatsAppMessage,
        prevAtSlot: ParsedWhatsAppMessage?,
        recentInDb: List<WhatsAppMessageEntity>,
    ): WhatsAppMessageEntity? {
        // 1. Wenn wir den vorherigen Text exakt an dieser Slot-Position im Notification-Stack kennen
        if (prevAtSlot != null && !prevAtSlot.isDeletionNotice) {
            recentInDb.firstOrNull {
                !it.isDeleted &&
                    it.senderName == prevAtSlot.senderName &&
                    it.text == prevAtSlot.text
            }?.let { return it }
        }

        // 2. Gleicher Zeitstempel (MessagingStyle behält oft den Original-Timestamp bei)
        recentInDb.firstOrNull {
            !it.isDeleted &&
                it.senderName == parsed.senderName &&
                abs(it.timestamp - parsed.timestamp) <= 2_000L
        }?.let { return it }

        // 3. Jüngste nicht-gelöschte Nachricht desselben Absenders in diesem Chat
        recentInDb.firstOrNull {
            !it.isDeleted && it.senderName.equals(parsed.senderName, ignoreCase = true)
        }?.let { return it }

        // 4. Fallback: jüngste nicht-gelöschte Nachricht im Chat überhaupt
        return recentInDb.firstOrNull { !it.isDeleted }
    }

    /**
     * Erkennt, ob [parsed] eine Bearbeitung einer bereits empfangenen Nachricht ist.
     *
     * WhatsApp erlaubt das Bearbeiten von Nachrichten innerhalb von 15 Minuten.
     * Erkennungsmerkmale:
     * 1. Explizites "(Bearbeitet)" / "Edited"-Flag im Text.
     * 2. Gleicher MessagingStyle-Zeitstempel bei geändertem Text.
     * 3. Im aktiven Notification-Stack (`previousSnapshot`) hat sich bei gleicher
     *    oder nicht-wachsender Stack-Größe der Text an Index `index` geändert
     *    (während eine neue Nachricht bei ungelesener Notification als weiteres
     *    Element an `EXTRA_MESSAGES` angehängt worden wäre).
     */
    private fun findEditTarget(
        parsed: ParsedWhatsAppMessage,
        index: Int,
        currentSnapshotSize: Int,
        previousSnapshot: List<ParsedWhatsAppMessage>,
        recentInDb: List<WhatsAppMessageEntity>,
        now: Long,
    ): WhatsAppMessageEntity? {
        val editWindowMs = 16 * 60 * 1000L // 15 Minuten WhatsApp-Limit + 1 Min Puffer

        // 1. Explizite Markierung "(Bearbeitet)"
        if (parsed.isExplicitEdit) {
            return recentInDb.firstOrNull {
                !it.isDeleted &&
                    it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                    abs(parsed.timestamp - it.timestamp) <= editWindowMs
            }
        }

        // 2. Exakt gleicher MessagingStyle-Zeitstempel (wenn WhatsApp den Original-Timestamp mitschickt)
        val prevByTimestamp = recentInDb.firstOrNull {
            !it.isDeleted &&
                it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                it.timestamp == parsed.timestamp &&
                it.text != parsed.text
        }
        if (prevByTimestamp != null) return prevByTimestamp

        // 3. Slot-Diff im aktiven Notification-Stack
        if (previousSnapshot.isNotEmpty() && currentSnapshotSize == previousSnapshot.size) {
            val prevSlot = previousSnapshot.getOrNull(index)
            if (prevSlot != null &&
                !prevSlot.isDeletionNotice &&
                prevSlot.senderName.equals(parsed.senderName, ignoreCase = true) &&
                prevSlot.text != parsed.text
            ) {
                return recentInDb.firstOrNull {
                    !it.isDeleted &&
                        it.senderName.equals(prevSlot.senderName, ignoreCase = true) &&
                        it.text == prevSlot.text &&
                        abs(parsed.timestamp - it.timestamp) <= editWindowMs
                }
            }
        }

        return null
    }

    fun decodeEditHistory(editHistoryJson: String?): List<WhatsAppEditVersion> {
        if (editHistoryJson.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<WhatsAppEditVersion>>(editHistoryJson) }
            .getOrDefault(emptyList())
    }

    suspend fun markChatRead(chatKey: String) {
        dao.markChatRead(chatKey)
    }

    suspend fun markAllRead() {
        dao.markAllRead()
    }

    suspend fun deleteMessage(id: Long) {
        dao.deleteById(id)
    }

    suspend fun deleteChat(chatKey: String) {
        activeSnapshots.remove(chatKey)
        dao.deleteChat(chatKey)
    }

    suspend fun clearAll() {
        activeSnapshots.clear()
        dao.deleteAll()
    }

    /**
     * Erzeugt einen realistischen Test-Durchlauf (1 reguläre Nachricht, 1 bearbeitete
     * Nachricht mit Vorher/Nachher-Vergleich und 1 für alle gelöschte Nachricht),
     * damit der Nutzer die Funktionsweise sofort live in der WhatsApp-Ecke ausprobieren kann.
     */
    suspend fun runLiveDemoScenario() {
        val baseTime = System.currentTimeMillis() - 180_000L

        // 1. Nachricht von "Leon (Demo)" senden -> und danach bearbeiten
        val leonPayload1 = RawNotificationPayload(
            packageName = "com.whatsapp",
            notificationKey = "demo_leon",
            notificationTag = "demo_leon",
            title = "Leon (Demo)",
            text = "Ich überweise dir gleich die 50 € für das Konzertticket!",
            bigText = null,
            subText = null,
            conversationTitle = null,
            isGroupConversation = false,
            isSummary = false,
            isOngoing = false,
            category = "msg",
            postTime = baseTime,
            messagingStyleItems = listOf(
                RawMessagingItem(
                    sender = "Leon (Demo)",
                    text = "Ich überweise dir gleich die 50 € für das Konzertticket!",
                    timestamp = baseTime,
                ),
            ),
        )
        processNotification(leonPayload1)

        // Leon bearbeitet die Nachricht von 50 € auf 25 €
        val leonPayload2 = leonPayload1.copy(
            text = "Ich überweise dir gleich die 25 € für das Konzertticket!",
            postTime = baseTime + 45_000L,
            messagingStyleItems = listOf(
                RawMessagingItem(
                    sender = "Leon (Demo)",
                    text = "Ich überweise dir gleich die 25 € für das Konzertticket!",
                    timestamp = baseTime,
                ),
            ),
        )
        processNotification(leonPayload2)

        // 2. Nachricht in Gruppe "Wochenend-Crew (Demo)" senden -> und danach für alle löschen
        val groupTime = System.currentTimeMillis() - 60_000L
        val groupPayload1 = RawNotificationPayload(
            packageName = "com.whatsapp",
            notificationKey = "demo_group",
            notificationTag = "demo_group",
            title = "Wochenend-Crew (Demo): Sarah",
            text = "Geheimtipp: Das Passwort für das WLAN heute Abend heißt Hikari2026 🤫",
            bigText = null,
            subText = null,
            conversationTitle = "Wochenend-Crew (Demo) (2 Nachrichten)",
            isGroupConversation = true,
            isSummary = false,
            isOngoing = false,
            category = "msg",
            postTime = groupTime,
            messagingStyleItems = listOf(
                RawMessagingItem(
                    sender = "Sarah",
                    text = "Kommt ihr heute alle pünktlich um 20 Uhr?",
                    timestamp = groupTime - 30_000L,
                ),
                RawMessagingItem(
                    sender = "Sarah",
                    text = "Geheimtipp: Das Passwort für das WLAN heute Abend heißt Hikari2026 🤫",
                    timestamp = groupTime,
                ),
            ),
        )
        processNotification(groupPayload1)

        // Sarah löscht die zweite Nachricht für alle
        val groupPayload2 = groupPayload1.copy(
            text = "🚫 Diese Nachricht wurde gelöscht.",
            postTime = groupTime + 20_000L,
            messagingStyleItems = listOf(
                RawMessagingItem(
                    sender = "Sarah",
                    text = "Kommt ihr heute alle pünktlich um 20 Uhr?",
                    timestamp = groupTime - 30_000L,
                ),
                RawMessagingItem(
                    sender = "Sarah",
                    text = "🚫 Diese Nachricht wurde gelöscht.",
                    timestamp = groupTime,
                ),
            ),
        )
        processNotification(groupPayload2)
    }

    private fun replaceInList(
        list: MutableList<WhatsAppMessageEntity>,
        updated: WhatsAppMessageEntity,
    ) {
        val idx = list.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            list[idx] = updated
        }
    }

    companion object {
        private const val KEY_ALERTS_ENABLED = "alerts_enabled"
        private const val KEY_DIRECT_HISTORY = "direct_chat_history"
    }
}
