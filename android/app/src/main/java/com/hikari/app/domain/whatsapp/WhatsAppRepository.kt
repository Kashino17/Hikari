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
import java.io.File
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

data class WhatsAppStats(
    val deletedCount: Int = 0,
    val editedCount: Int = 0,
)

/**
 * Flüchtiger RAM-Eintrag für eingehende Nachrichten.
 * Reguläre Nachrichten liegen NUR hier im Arbeitsspeicher (niemals in der Datenbank!),
 * damit wir den Originaltext kennen, falls der Absender die Nachricht kurz darauf
 * „Für alle löscht“ oder bearbeitet.
 */
private data class TransientCandidate(
    val chatKey: String,
    val chatTitle: String,
    val senderName: String,
    val isGroup: Boolean,
    val packageName: String,
    val text: String,
    val mediaType: String?,
    val mediaPreviewPath: String?,
    val avatarPath: String?,
    val timestamp: Long,
    val notificationKey: String?,
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
     */
    private val activeSnapshots = ConcurrentHashMap<String, List<ParsedWhatsAppMessage>>()

    /**
     * Kleiner flüchtiger RAM-Ringpuffer pro Chat (max. [MAX_TRANSIENT_PER_CHAT] Nachrichten),
     * damit normale Nachrichten NICHT in der Datenbank gespeichert werden, aber bei einer
     * nachträglichen Löschung/Bearbeitung sofort als Originaltext bereitstehen.
     */
    private val transientRamCache = ConcurrentHashMap<String, MutableList<TransientCandidate>>()

    val allMessages: Flow<List<WhatsAppMessageEntity>> = dao.observeAllMessages()
    val deletedMessages: Flow<List<WhatsAppMessageEntity>> = dao.observeDeletedMessages()
    val editedMessages: Flow<List<WhatsAppMessageEntity>> = dao.observeEditedMessages()
    val deletedCount: Flow<Int> = dao.observeDeletedCount()
    val editedCount: Flow<Int> = dao.observeEditedCount()

    val stats: Flow<WhatsAppStats> = allMessages.map { messages ->
        WhatsAppStats(
            deletedCount = messages.count { it.isDeleted },
            editedCount = messages.count { it.isEdited },
        )
    }

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

    fun onNotificationCleared(notificationKey: String?) {
        if (notificationKey.isNullOrBlank()) return
        val keysToRemove = activeSnapshots.entries
            .filter { (_, list) -> list.any { it.notificationKey == notificationKey } }
            .map { it.key }
        keysToRemove.forEach { activeSnapshots.remove(it) }
    }

    /**
     * Bereinigt:
     * 1. Alle normalen (nicht gelöschten & nicht bearbeiteten) Altlasten aus der DB.
     * 2. Alle gelöschten/bearbeiteten Nachrichten in der DB, die älter als 7 Tage sind.
     * 3. Alle lokalen Medien- & Avatar-Dateien, die älter als 7 Tage sind oder zu keiner
     *    gespeicherten Nachricht mehr gehören.
     * 4. Abgelaufene Einträge im flüchtigen RAM-Puffer.
     */
    suspend fun pruneExpiredData(nowMs: Long = System.currentTimeMillis()) = mutex.withLock {
        pruneInternal(nowMs)
    }

    private suspend fun pruneInternal(nowMs: Long) {
        // 1. Sicherstellen, dass keinerlei normale Nachrichten in der DB liegen
        dao.deleteNormalMessages()

        // 2. Alles älter als 7 Tage (RETENTION_MS) aus der DB + Disk löschen
        val cutoff = nowMs - RETENTION_MS
        val expired = dao.getExpiredMessages(cutoff)
        for (msg in expired) {
            msg.mediaPreviewPath?.let { path -> runCatching { File(path).delete() } }
        }
        dao.deleteExpiredMessages(cutoff)

        // 3. Flüchtigen RAM-Cache begrenzen (max. 6 Stunden im RAM)
        val ramCutoff = nowMs - TRANSIENT_RAM_TTL_MS
        for ((key, list) in transientRamCache) {
            list.removeAll { it.timestamp < ramCutoff }
            if (list.isEmpty()) {
                transientRamCache.remove(key)
            }
        }

        // 4. Alte Dateien im whatsapp_media & whatsapp_avatars Ordner bereinigen:
        //    - Mediendateien, die zu KEINER gelöschten/bearbeiteten Nachricht gehören,
        //      werden schon nach 30 Minuten gelöscht (Speicher bleibt immer frei!).
        //    - Mediendateien von gelöschten Nachrichten werden nach 7 Tagen gelöscht.
        runCatching {
            val activePaths = dao.getActiveMediaPaths().toSet()
            val unreferencedCutoff = nowMs - (30L * 60L * 1000L) // 30 Minuten
            val mediaDir = File(context.filesDir, "whatsapp_media")
            if (mediaDir.exists()) {
                mediaDir.listFiles()?.forEach { file ->
                    val isReferenced = file.absolutePath in activePaths
                    val isExpired = if (isReferenced) {
                        file.lastModified() < cutoff
                    } else {
                        file.lastModified() < unreferencedCutoff
                    }
                    if (isExpired) {
                        file.delete()
                    }
                }
            }
            val avatarDir = File(context.filesDir, "whatsapp_avatars")
            if (avatarDir.exists()) {
                avatarDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoff) {
                        file.delete()
                    }
                }
            }
        }
    }

    /**
     * Verarbeitet eine eingehende WhatsApp-Benachrichtigung:
     * - Normale Nachrichten werden **NICHT** in der Datenbank gespeichert, sondern
     *   nur kurzzeitig im RAM-Puffer (`transientRamCache`) vorgehalten.
     * - **Nur** wenn eine Nachricht tatsächlich „Für alle gelöscht“ oder „Bearbeitet“ wird,
     *   landet sie in der Room-Datenbank (und wird nach 7 Tagen automatisch wieder gelöscht).
     */
    suspend fun processNotification(
        payload: RawNotificationPayload,
        avatarPath: String? = null,
        mediaPreviewPath: String? = null,
    ) = mutex.withLock {
        val now = System.currentTimeMillis()
        pruneInternal(now)

        val parsedList = WhatsAppNotificationParser.parse(payload)
        if (parsedList.isEmpty()) return@withLock

        val chatKey = parsedList.first().chatKey
        val previousSnapshot = activeSnapshots[chatKey].orEmpty()
        val ramList = transientRamCache.getOrPut(chatKey) { mutableListOf() }
        val savedInDb = dao.getRecentMessagesForChat(chatKey, limit = 25).toMutableList()

        for ((index, parsed) in parsedList.withIndex()) {
            val prevAtSlot = previousSnapshot.getOrNull(index)

            if (parsed.isDeletionNotice) {
                // ── FALL 1: Nachricht wurde für alle gelöscht! ────────────────────
                // Zuerst prüfen, ob eine bereits bearbeitete Nachricht in der DB gelöscht wurde
                val existingDbMatch = findDbDeletionTarget(parsed, prevAtSlot, savedInDb)
                if (existingDbMatch != null) {
                    if (!existingDbMatch.isDeleted) {
                        val updated = existingDbMatch.copy(
                            isDeleted = true,
                            deletedAt = now,
                            avatarPath = avatarPath ?: existingDbMatch.avatarPath,
                        )
                        dao.update(updated)
                        replaceInList(savedInDb, updated)
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
                    continue
                }

                // Sonst aus dem flüchtigen RAM-Puffer holen und JETZT in der DB sichern!
                val ramTarget = findRamDeletionTarget(parsed, prevAtSlot, ramList)
                if (ramTarget != null) {
                    ramList.remove(ramTarget)
                    val alreadySaved = savedInDb.any {
                        it.isDeleted &&
                            it.senderName == ramTarget.senderName &&
                            it.text == ramTarget.text &&
                            abs(it.timestamp - ramTarget.timestamp) < 60_000L
                    }
                    if (!alreadySaved) {
                        val rescuedEntity = WhatsAppMessageEntity(
                            chatKey = ramTarget.chatKey,
                            chatTitle = ramTarget.chatTitle,
                            senderName = ramTarget.senderName,
                            isGroup = ramTarget.isGroup,
                            packageName = ramTarget.packageName,
                            text = ramTarget.text,
                            isDeleted = true,
                            deletedAt = now,
                            mediaType = ramTarget.mediaType,
                            mediaPreviewPath = ramTarget.mediaPreviewPath ?: mediaPreviewPath,
                            avatarPath = ramTarget.avatarPath ?: avatarPath,
                            timestamp = ramTarget.timestamp,
                            notificationKey = ramTarget.notificationKey,
                            isReadInHikari = false,
                        )
                        val newId = dao.insert(rescuedEntity)
                        savedInDb.add(0, rescuedEntity.copy(id = newId))
                        if (_alertsEnabled.value) {
                            _guardAlerts.tryEmit(
                                WhatsAppGuardAlert.MessageDeleted(
                                    chatTitle = rescuedEntity.chatTitle,
                                    senderName = rescuedEntity.senderName,
                                    preservedText = rescuedEntity.text,
                                    isGroup = rescuedEntity.isGroup,
                                ),
                            )
                        }
                    }
                }
                continue
            }

            // ── FALL 2: Wurde eine bestehende Nachricht BEARBEITET? ────────────────
            // 2a) Wurde eine bereits als "Bearbeitet" in der DB liegende Nachricht nochmals editiert?
            val dbEditTarget = findDbEditTarget(
                parsed = parsed,
                index = index,
                currentSnapshotSize = parsedList.size,
                previousSnapshot = previousSnapshot,
                savedInDb = savedInDb,
            )
            if (dbEditTarget != null && dbEditTarget.text != parsed.text) {
                val firstOriginal = dbEditTarget.originalText ?: dbEditTarget.text
                val previousVersions = decodeEditHistory(dbEditTarget.editHistoryJson).toMutableList()
                previousVersions.add(
                    WhatsAppEditVersion(
                        text = dbEditTarget.text,
                        timestamp = dbEditTarget.editedAt ?: dbEditTarget.timestamp,
                    ),
                )
                val updated = dbEditTarget.copy(
                    text = parsed.text,
                    originalText = firstOriginal,
                    editHistoryJson = json.encodeToString(previousVersions),
                    isEdited = true,
                    editedAt = now,
                    avatarPath = avatarPath ?: dbEditTarget.avatarPath,
                )
                dao.update(updated)
                replaceInList(savedInDb, updated)
                if (_alertsEnabled.value) {
                    _guardAlerts.tryEmit(
                        WhatsAppGuardAlert.MessageEdited(
                            chatTitle = updated.chatTitle,
                            senderName = updated.senderName,
                            oldText = dbEditTarget.text,
                            newText = updated.text,
                            isGroup = updated.isGroup,
                        ),
                    )
                }
                continue
            }

            // 2b) Wurde eine bisher normale Nachricht aus dem RAM-Puffer bearbeitet?
            val ramEditTarget = findRamEditTarget(
                parsed = parsed,
                index = index,
                currentSnapshotSize = parsedList.size,
                previousSnapshot = previousSnapshot,
                ramList = ramList,
            )
            if (ramEditTarget != null && ramEditTarget.text != parsed.text) {
                ramList.remove(ramEditTarget)
                val history = listOf(
                    WhatsAppEditVersion(
                        text = ramEditTarget.text,
                        timestamp = ramEditTarget.timestamp,
                    ),
                )
                val editedEntity = WhatsAppMessageEntity(
                    chatKey = parsed.chatKey,
                    chatTitle = parsed.chatTitle,
                    senderName = parsed.senderName,
                    isGroup = parsed.isGroup,
                    packageName = parsed.packageName,
                    text = parsed.text,
                    originalText = ramEditTarget.text,
                    editHistoryJson = json.encodeToString(history),
                    isEdited = true,
                    editedAt = now,
                    mediaType = parsed.mediaType ?: ramEditTarget.mediaType,
                    mediaPreviewPath = mediaPreviewPath ?: ramEditTarget.mediaPreviewPath,
                    avatarPath = avatarPath ?: ramEditTarget.avatarPath,
                    timestamp = ramEditTarget.timestamp,
                    notificationKey = parsed.notificationKey,
                    isReadInHikari = false,
                )
                val newId = dao.insert(editedEntity)
                savedInDb.add(0, editedEntity.copy(id = newId))
                if (_alertsEnabled.value) {
                    _guardAlerts.tryEmit(
                        WhatsAppGuardAlert.MessageEdited(
                            chatTitle = editedEntity.chatTitle,
                            senderName = editedEntity.senderName,
                            oldText = ramEditTarget.text,
                            newText = editedEntity.text,
                            isGroup = editedEntity.isGroup,
                        ),
                    )
                }
                continue
            }

            // ── FALL 3: Reguläre Nachricht -> NUR im flüchtigen RAM-Puffer merken! ──
            val alreadyInRam = ramList.any {
                it.senderName == parsed.senderName &&
                    it.text == parsed.text &&
                    abs(it.timestamp - parsed.timestamp) < 120_000L
            }
            if (!alreadyInRam) {
                ramList.add(
                    0,
                    TransientCandidate(
                        chatKey = parsed.chatKey,
                        chatTitle = parsed.chatTitle,
                        senderName = parsed.senderName,
                        isGroup = parsed.isGroup,
                        packageName = parsed.packageName,
                        text = parsed.text,
                        mediaType = parsed.mediaType,
                        mediaPreviewPath = mediaPreviewPath,
                        avatarPath = avatarPath,
                        timestamp = parsed.timestamp,
                        notificationKey = parsed.notificationKey,
                    ),
                )
                while (ramList.size > MAX_TRANSIENT_PER_CHAT) {
                    ramList.removeAt(ramList.lastIndex)
                }
            }
        }

        activeSnapshots[chatKey] = parsedList
    }

    private fun findRamDeletionTarget(
        parsed: ParsedWhatsAppMessage,
        prevAtSlot: ParsedWhatsAppMessage?,
        ramList: List<TransientCandidate>,
    ): TransientCandidate? {
        if (prevAtSlot != null && !prevAtSlot.isDeletionNotice) {
            ramList.firstOrNull {
                it.senderName == prevAtSlot.senderName && it.text == prevAtSlot.text
            }?.let { return it }
        }
        ramList.firstOrNull {
            it.senderName == parsed.senderName && abs(it.timestamp - parsed.timestamp) <= 2_000L
        }?.let { return it }
        ramList.firstOrNull {
            it.senderName.equals(parsed.senderName, ignoreCase = true)
        }?.let { return it }
        return ramList.firstOrNull()
    }

    private fun findDbDeletionTarget(
        parsed: ParsedWhatsAppMessage,
        prevAtSlot: ParsedWhatsAppMessage?,
        savedInDb: List<WhatsAppMessageEntity>,
    ): WhatsAppMessageEntity? {
        if (prevAtSlot != null && !prevAtSlot.isDeletionNotice) {
            savedInDb.firstOrNull {
                !it.isDeleted &&
                    it.senderName == prevAtSlot.senderName &&
                    it.text == prevAtSlot.text
            }?.let { return it }
        }
        return savedInDb.firstOrNull {
            !it.isDeleted &&
                it.senderName == parsed.senderName &&
                abs(it.timestamp - parsed.timestamp) <= 2_000L
        }
    }

    private fun findRamEditTarget(
        parsed: ParsedWhatsAppMessage,
        index: Int,
        currentSnapshotSize: Int,
        previousSnapshot: List<ParsedWhatsAppMessage>,
        ramList: List<TransientCandidate>,
    ): TransientCandidate? {
        val editWindowMs = 16 * 60 * 1000L

        if (parsed.isExplicitEdit) {
            return ramList.firstOrNull {
                it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                    abs(parsed.timestamp - it.timestamp) <= editWindowMs
            }
        }

        ramList.firstOrNull {
            it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                it.timestamp == parsed.timestamp &&
                it.text != parsed.text
        }?.let { return it }

        if (previousSnapshot.isNotEmpty() && currentSnapshotSize == previousSnapshot.size) {
            val prevSlot = previousSnapshot.getOrNull(index)
            if (prevSlot != null &&
                !prevSlot.isDeletionNotice &&
                prevSlot.senderName.equals(parsed.senderName, ignoreCase = true) &&
                prevSlot.text != parsed.text
            ) {
                return ramList.firstOrNull {
                    it.senderName.equals(prevSlot.senderName, ignoreCase = true) &&
                        it.text == prevSlot.text &&
                        abs(parsed.timestamp - it.timestamp) <= editWindowMs
                }
            }
        }
        return null
    }

    private fun findDbEditTarget(
        parsed: ParsedWhatsAppMessage,
        index: Int,
        currentSnapshotSize: Int,
        previousSnapshot: List<ParsedWhatsAppMessage>,
        savedInDb: List<WhatsAppMessageEntity>,
    ): WhatsAppMessageEntity? {
        val editWindowMs = 16 * 60 * 1000L

        if (parsed.isExplicitEdit) {
            return savedInDb.firstOrNull {
                !it.isDeleted &&
                    it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                    abs(parsed.timestamp - it.timestamp) <= editWindowMs
            }
        }

        savedInDb.firstOrNull {
            !it.isDeleted &&
                it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                it.timestamp == parsed.timestamp &&
                it.text != parsed.text
        }?.let { return it }

        if (previousSnapshot.isNotEmpty() && currentSnapshotSize == previousSnapshot.size) {
            val prevSlot = previousSnapshot.getOrNull(index)
            if (prevSlot != null &&
                !prevSlot.isDeletionNotice &&
                prevSlot.senderName.equals(parsed.senderName, ignoreCase = true) &&
                prevSlot.text != parsed.text
            ) {
                return savedInDb.firstOrNull {
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

    suspend fun deleteMessage(id: Long) {
        val msg = dao.getById(id)
        msg?.mediaPreviewPath?.let { path -> runCatching { File(path).delete() } }
        dao.deleteById(id)
    }

    suspend fun clearAll() {
        activeSnapshots.clear()
        transientRamCache.clear()
        dao.deleteAll()
    }

    /**
     * Erzeugt einen realistischen Test-Durchlauf (1 bearbeitete Nachricht mit
     * Vorher/Nachher-Vergleich und 1 für alle gelöschte Nachricht).
     * Die normale Nachricht ("Kommt ihr heute alle pünktlich um 20 Uhr?") wird
     * dabei NICHT in der Datenbank gespeichert!
     */
    suspend fun runLiveDemoScenario() {
        val baseTime = System.currentTimeMillis() - 180_000L

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

        /** Aufbewahrungsdauer für gelöschte & bearbeitete Nachrichten: exakt 7 Tage. */
        const val RETENTION_DAYS = 7
        const val RETENTION_MS = 7L * 24L * 60L * 60L * 1000L

        /** Flüchtige Vorhaltezeit im RAM für noch nicht gelöschte Nachrichten: 6 Stunden. */
        private const val TRANSIENT_RAM_TTL_MS = 6L * 60L * 60L * 1000L
        private const val MAX_TRANSIENT_PER_CHAT = 30
    }
}
