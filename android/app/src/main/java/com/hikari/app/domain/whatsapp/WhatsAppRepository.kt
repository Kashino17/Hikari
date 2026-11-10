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

/** Eine frisch protokollierte Mediennachricht, deren Datei noch gesichert werden soll. */
data class PendingMedia(
    val rowId: Long,
    val mediaType: String,
    val dataUri: String?,
    val dataMimeType: String?,
    val seenAt: Long,
)

/** Was die Verarbeitung einer Benachrichtigung ergeben hat (ohne Nachrichteninhalte). */
data class GuardResult(
    val stackSize: Int = 0,
    val summary: String = "",
    val pendingMedia: List<PendingMedia> = emptyList(),
)

/**
 * Der Nachrichten-Tresor hinter dem WhatsApp-Wächter.
 *
 * Jede Nachricht, die WhatsApp per Benachrichtigung zeigt, kommt als unsichtbare Protokollzeile
 * in Room (24 Stunden, bzw. solange sie noch im Benachrichtigungsstapel steht). Ob etwas
 * gelöscht oder bearbeitet wurde, entscheidet allein [WhatsAppStackDiff]; erst dann wird die
 * Zeile sichtbar und bleibt 7 Tage.
 */
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

    /** Letzter bekannter Stapel jeder gerade sichtbaren Chat-Benachrichtigung. */
    private val activeSnapshots = ConcurrentHashMap<String, List<ParsedWhatsAppMessage>>()

    private var lastPruneAt = 0L

    init {
        // Überbleibsel des früheren Zwischenspeichers (bis v0.89.79) entfernen.
        runCatching { prefs.edit().remove("transient_ram_cache_v1").apply() }
    }

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

    /**
     * Die Benachrichtigung ist weg (gelesen, weggewischt, von WhatsApp entfernt). Das sagt
     * nichts darüber, ob etwas gelöscht wurde — es endet nur der Vergleichsstapel.
     */
    fun onNotificationCleared(notificationKey: String?) {
        if (notificationKey.isNullOrBlank()) return
        val keysToRemove = activeSnapshots.entries
            .filter { (_, list) -> list.any { it.notificationKey == notificationKey } }
            .map { it.key }
        keysToRemove.forEach { activeSnapshots.remove(it) }
    }

    suspend fun pruneExpiredData(nowMs: Long = System.currentTimeMillis()) = mutex.withLock {
        pruneInternal(nowMs)
    }

    private suspend fun pruneInternal(nowMs: Long) {
        lastPruneAt = nowMs

        // 1. Protokollzeilen nach 24 Stunden entfernen — außer die Nachricht steht noch im Stapel
        //    einer sichtbaren Benachrichtigung (dort liegen auch mal tagealte ungelesene Nachrichten).
        val stillVisible = activeSnapshots.values.flatten()
            .mapTo(HashSet()) { identityKey(it.chatKey, it.senderName, it.timestamp, it.text) }
        val expiredStaging = dao.getExpiredStagingMessages(nowMs - STAGING_RETENTION_MS)
            .filter { identityKey(it.chatKey, it.senderName, it.timestamp, it.text) !in stillVisible }
        expiredStaging.forEach { row -> row.mediaPreviewPath?.let { runCatching { File(it).delete() } } }
        expiredStaging.map { it.id }.chunked(500).forEach { dao.deleteByIds(it) }

        // 2. Gerettete Nachrichten nach 7 Tagen samt Mediendatei entfernen.
        val cutoff = nowMs - RETENTION_MS
        dao.getExpiredMessages(cutoff).forEach { row ->
            row.mediaPreviewPath?.let { runCatching { File(it).delete() } }
        }
        dao.deleteExpiredMessages(cutoff)

        // 3. Dateien aufräumen.
        runCatching {
            val referenced = dao.getActiveMediaPaths().toSet()
            val rescued = dao.getRescuedMediaPaths().toSet()
            val mediaFiles = File(context.filesDir, MEDIA_DIR).listFiles().orEmpty()

            // Verwaiste Dateien (keine Zeile zeigt darauf) nach einer Viertelstunde löschen.
            val orphanCutoff = nowMs - ORPHAN_MEDIA_GRACE_MS
            mediaFiles.filter { it.absolutePath !in referenced && it.lastModified() < orphanCutoff }
                .forEach { it.delete() }

            // Vorsorglich gesicherte Medien (noch nichts gelöscht) dürfen nur begrenzt Platz belegen.
            val staged = mediaFiles
                .filter { it.exists() && it.absolutePath in referenced && it.absolutePath !in rescued }
                .sortedBy { it.lastModified() }
            var stagedBytes = staged.sumOf { it.length() }
            for (file in staged) {
                if (stagedBytes <= MAX_STAGED_MEDIA_BYTES) break
                stagedBytes -= file.length()
                file.delete()
            }

            File(context.filesDir, AVATAR_DIR).listFiles().orEmpty()
                .filter { it.lastModified() < cutoff }
                .forEach { it.delete() }
        }
    }

    /**
     * Verarbeitet den Nachrichtenstapel einer WhatsApp-Benachrichtigung.
     * Normale Nachrichten werden nur protokolliert; sichtbar wird eine Nachricht erst, wenn
     * [WhatsAppStackDiff] eine Löschung oder Bearbeitung belegt.
     */
    suspend fun processNotification(
        payload: RawNotificationPayload,
        avatarPath: String? = null,
    ): GuardResult = mutex.withLock {
        val parsed = WhatsAppNotificationParser.parse(payload)
        if (parsed.isEmpty()) return@withLock GuardResult()

        val now = System.currentTimeMillis()
        if (now - lastPruneAt > PRUNE_INTERVAL_MS) pruneInternal(now)

        val chatKey = parsed.first().chatKey
        val previous = activeSnapshots[chatKey]
        val oldestRelevant = (parsed + previous.orEmpty()).minOf { it.timestamp }
        val rows = dao.getMessagesForChatSince(chatKey, oldestRelevant)
            .associateByTo(LinkedHashMap()) { it.id }

        val events = WhatsAppStackDiff.diff(
            previous = previous,
            current = parsed,
            known = rows.values.map {
                WhatsAppStackDiff.Known(it.id, it.senderName, it.text, it.timestamp, it.isDeleted)
            },
            now = now,
        )

        val pendingMedia = mutableListOf<PendingMedia>()
        var newCount = 0
        var deletedCount = 0
        var editedCount = 0
        var otherCount = 0

        for (event in events) {
            when (event) {
                is WhatsAppStackDiff.Event.New -> {
                    val message = event.message
                    val id = dao.insert(
                        WhatsAppMessageEntity(
                            chatKey = message.chatKey,
                            chatTitle = message.chatTitle,
                            senderName = message.senderName,
                            isGroup = message.isGroup,
                            packageName = message.packageName,
                            text = message.text,
                            mediaType = message.mediaType,
                            avatarPath = avatarPath,
                            timestamp = message.timestamp,
                            notificationKey = message.notificationKey,
                        ),
                    )
                    newCount++
                    if (message.mediaType != null) {
                        pendingMedia += PendingMedia(id, message.mediaType, message.dataUri, message.dataMimeType, now)
                    }
                }

                is WhatsAppStackDiff.Event.Deleted -> {
                    val row = rows[event.knownId] ?: continue
                    val updated = row.copy(
                        isDeleted = true,
                        deletedAt = now,
                        isReadInHikari = false,
                        avatarPath = avatarPath ?: row.avatarPath,
                    )
                    dao.update(updated)
                    rows[updated.id] = updated
                    deletedCount++
                    alert(
                        WhatsAppGuardAlert.MessageDeleted(
                            chatTitle = updated.chatTitle,
                            senderName = updated.senderName,
                            preservedText = updated.text,
                            isGroup = updated.isGroup,
                        ),
                    )
                }

                is WhatsAppStackDiff.Event.Edited -> {
                    val row = rows[event.knownId] ?: continue
                    val history = decodeEditHistory(row.editHistoryJson) +
                        WhatsAppEditVersion(text = row.text, timestamp = row.editedAt ?: row.timestamp)
                    val updated = row.copy(
                        text = event.message.text,
                        originalText = row.originalText ?: row.text,
                        editHistoryJson = json.encodeToString(history),
                        isEdited = true,
                        editedAt = now,
                        isReadInHikari = false,
                        avatarPath = avatarPath ?: row.avatarPath,
                    )
                    dao.update(updated)
                    rows[updated.id] = updated
                    editedCount++
                    alert(
                        WhatsAppGuardAlert.MessageEdited(
                            chatTitle = updated.chatTitle,
                            senderName = updated.senderName,
                            oldText = event.oldText,
                            newText = updated.text,
                            isGroup = updated.isGroup,
                        ),
                    )
                }

                is WhatsAppStackDiff.Event.Relabeled -> {
                    val row = rows[event.knownId] ?: continue
                    val updated = row.copy(
                        text = event.message.text,
                        mediaType = event.message.mediaType ?: row.mediaType,
                    )
                    dao.update(updated)
                    rows[updated.id] = updated
                    otherCount++
                }

                is WhatsAppStackDiff.Event.Restored -> {
                    val row = rows[event.knownId] ?: continue
                    val updated = row.copy(isDeleted = false, deletedAt = null)
                    dao.update(updated)
                    rows[updated.id] = updated
                    otherCount++
                }
            }
        }

        activeSnapshots[chatKey] = parsed
        GuardResult(
            stackSize = parsed.size,
            summary = "stack=${parsed.size} fresh=${previous == null} new=$newCount " +
                "deleted=$deletedCount edited=$editedCount other=$otherCount",
            pendingMedia = pendingMedia,
        )
    }

    /**
     * Hängt eine gesicherte Mediendatei an ihre Protokollzeile. Gibt es die Zeile nicht mehr
     * oder hat sie schon eine Datei, wird die überzählige Kopie verworfen.
     */
    suspend fun attachMedia(rowId: Long, path: String) = mutex.withLock {
        val row = dao.getById(rowId)
        if (row == null || (row.mediaPreviewPath != null && File(row.mediaPreviewPath).exists())) {
            if (row?.mediaPreviewPath != path) runCatching { File(path).delete() }
            return@withLock
        }
        dao.update(row.copy(mediaPreviewPath = path))
    }

    suspend fun markAllSeen() {
        dao.markAllSeen()
    }

    private fun alert(alert: WhatsAppGuardAlert) {
        if (_alertsEnabled.value) _guardAlerts.tryEmit(alert)
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

    suspend fun clearAll() = mutex.withLock {
        activeSnapshots.clear()
        dao.deleteAll()
        runCatching {
            File(context.filesDir, MEDIA_DIR).listFiles().orEmpty().forEach { it.delete() }
        }
    }

    /**
     * Spielt ein Beispiel durch den echten Erkennungsweg: eine bearbeitete und eine für alle
     * gelöschte Nachricht. Die unveränderte Nachricht daneben taucht nicht im Tresor auf.
     */
    suspend fun runLiveDemoScenario() {
        val baseTime = (System.currentTimeMillis() / 1000L) * 1000L - 180_000L

        fun payload(
            tag: String,
            title: String,
            conversationTitle: String?,
            postTime: Long,
            items: List<RawMessagingItem>,
        ) = RawNotificationPayload(
            packageName = "com.whatsapp",
            notificationKey = tag,
            notificationTag = tag,
            title = title,
            text = items.last().text,
            bigText = null,
            subText = null,
            conversationTitle = conversationTitle,
            isGroupConversation = conversationTitle != null,
            isSummary = false,
            isOngoing = false,
            category = "msg",
            postTime = postTime,
            messagingStyleItems = items,
        )

        val leonTag = "demo_leon_$baseTime"
        processNotification(
            payload(
                leonTag, "Leon (Beispiel)", null, baseTime,
                listOf(RawMessagingItem("Leon (Beispiel)", "Ich überweise dir gleich die 50 € für das Konzertticket!", baseTime)),
            ),
        )
        processNotification(
            payload(
                leonTag, "Leon (Beispiel)", null, baseTime + 45_000L,
                listOf(RawMessagingItem("Leon (Beispiel)", "Ich überweise dir gleich die 25 € für das Konzertticket!", baseTime)),
            ),
        )
        onNotificationCleared(leonTag)

        val groupTag = "demo_group_$baseTime"
        val groupTime = baseTime + 120_000L
        val first = RawMessagingItem("Sarah", "Kommt ihr heute alle pünktlich um 20 Uhr?", groupTime - 30_000L)
        processNotification(
            payload(
                groupTag, "Wochenend-Crew (Beispiel): Sarah", "Wochenend-Crew (Beispiel) (2 Nachrichten)", groupTime,
                listOf(first, RawMessagingItem("Sarah", "Das WLAN-Passwort für heute Abend heißt Hikari2026", groupTime)),
            ),
        )
        processNotification(
            payload(
                groupTag, "Wochenend-Crew (Beispiel): Sarah", "Wochenend-Crew (Beispiel) (2 Nachrichten)", groupTime + 20_000L,
                listOf(first, RawMessagingItem("Sarah", "Diese Nachricht wurde gelöscht", groupTime)),
            ),
        )
        onNotificationCleared(groupTag)
    }

    private fun identityKey(chatKey: String, sender: String, timestamp: Long, text: String): String =
        "$chatKey|${sender.lowercase()}|$timestamp|$text"

    companion object {
        private const val KEY_ALERTS_ENABLED = "alerts_enabled"
        private const val KEY_DIRECT_HISTORY = "direct_chat_history"

        const val MEDIA_DIR = "whatsapp_media"
        const val AVATAR_DIR = "whatsapp_avatars"

        /** Aufbewahrungsdauer für gelöschte & bearbeitete Nachrichten: 7 Tage. */
        const val RETENTION_DAYS = 7
        const val RETENTION_MS = 7L * 24L * 60L * 60L * 1000L

        /** Aufbewahrungsdauer der unsichtbaren Protokollzeilen: 24 Stunden. */
        const val STAGING_RETENTION_MS = 24L * 60L * 60L * 1000L

        private const val PRUNE_INTERVAL_MS = 10L * 60L * 1000L
        private const val ORPHAN_MEDIA_GRACE_MS = 15L * 60L * 1000L

        /** Obergrenze für vorsorglich gesicherte Medien, deren Nachricht (noch) nicht gelöscht wurde. */
        private const val MAX_STAGED_MEDIA_BYTES = 300L * 1024L * 1024L
    }
}
