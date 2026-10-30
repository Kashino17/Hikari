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
 * Flüchtiger Ringpuffer-Eintrag für eingehende Nachrichten.
 * Reguläre Nachrichten liegen NUR hier im flüchtigen Puffer (niemals in der Room-Datenbank!),
 * damit wir den Originaltext kennen, falls der Absender die Nachricht kurz darauf
 * „Für alle löscht“ oder bearbeitet.
 */
@Serializable
internal data class TransientCandidate(
    val chatKey: String,
    val chatTitle: String,
    val senderName: String,
    val isGroup: Boolean,
    val packageName: String,
    val text: String,
    val mediaType: String? = null,
    val mediaPreviewPath: String? = null,
    val avatarPath: String? = null,
    val timestamp: Long,
    val notificationKey: String? = null,
    val notificationTag: String? = null,
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
     * Kleiner flüchtiger Puffer pro Chat (max. [MAX_TRANSIENT_PER_CHAT] Nachrichten),
     * damit normale Nachrichten NICHT in der Datenbank gespeichert werden, aber bei einer
     * nachträglichen Löschung/Bearbeitung sofort als Originaltext bereitstehen.
     */
    private val transientRamCache = ConcurrentHashMap<String, MutableList<TransientCandidate>>().apply {
        putAll(loadTransientCacheFromPrefs())
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

    private fun loadTransientCacheFromPrefs(): Map<String, MutableList<TransientCandidate>> {
        val raw = runCatching { prefs.getString(KEY_TRANSIENT_CACHE, null) }.getOrNull()
            ?: return emptyMap()
        val list = runCatching { json.decodeFromString<List<TransientCandidate>>(raw) }
            .getOrDefault(emptyList())
        val cutoff = System.currentTimeMillis() - TRANSIENT_RAM_TTL_MS
        return list
            .filter { it.timestamp >= cutoff }
            .groupBy { it.chatKey }
            .mapValues { (_, items) -> items.take(MAX_TRANSIENT_PER_CHAT).toMutableList() }
    }

    private fun persistTransientCacheToPrefs() {
        runCatching {
            val flat = transientRamCache.values
                .flatten()
                .sortedByDescending { it.timestamp }
                .take(60)
            prefs.edit().putString(KEY_TRANSIENT_CACHE, json.encodeToString(flat)).apply()
        }
    }

    fun onNotificationCleared(notificationKey: String?) {
        if (notificationKey.isNullOrBlank()) return
        val keysToRemove = activeSnapshots.entries
            .filter { (_, list) -> list.any { it.notificationKey == notificationKey } }
            .map { it.key }
        keysToRemove.forEach { activeSnapshots.remove(it) }
    }

    /**
     * Wird aufgerufen, wenn der Nutzer auf die Benachrichtigung getippt hat (`REASON_CLICK`).
     * WICHTIG: Wir leeren NUR den aktiven Statusleisten-Snapshot (`activeSnapshots`),
     * löschen aber NIEMALS die Zwischenspeicher-Nachrichten in SQLite oder RAM!
     * Denn wenn der Absender die Nachricht kurz nach dem Antippen „Für alle löscht“,
     * muss der Originaltext weiterhin in SQLite bereitstehen!
     */
    suspend fun onNotificationClicked(notificationKey: String?) = mutex.withLock {
        onNotificationCleared(notificationKey)
    }

    /**
     * Wird aufgerufen, wenn WhatsApp selbst eine aktive Chat-Benachrichtigung zurückzieht
     * (`REASON_APP_CANCEL`, `REASON_APP_CANCEL_ALL` oder `REASON_GROUP_SUMMARY_CANCELED`),
     * ohne dass der Nutzer darauf getippt (`REASON_CLICK`) oder sie weggewischt (`REASON_CANCEL`)
     * hat und ohne dass binnen 1,2 Sekunden eine Ersatz-Benachrichtigung kam.
     */
    suspend fun onNotificationRetractedByApp(
        notificationKey: String?,
        notificationTag: String? = null,
        chatKey: String? = null,
        nowMs: Long = System.currentTimeMillis(),
    ) = mutex.withLock {
        val retractWindowMs = 15L * 60L * 1000L // 15 Minuten

        val affectedChatKeys = linkedSetOf<String>()
        if (!chatKey.isNullOrBlank()) {
            affectedChatKeys.add(chatKey)
        }
        if (!notificationKey.isNullOrBlank()) {
            activeSnapshots.entries
                .filter { (_, list) -> list.any { it.notificationKey == notificationKey } }
                .forEach { affectedChatKeys.add(it.key) }
            transientRamCache.entries
                .filter { (_, list) -> list.any { it.notificationKey == notificationKey } }
                .forEach { affectedChatKeys.add(it.key) }
        }
        if (!notificationTag.isNullOrBlank()) {
            activeSnapshots.entries
                .filter { (_, list) -> list.any { it.notificationTag == notificationTag } }
                .forEach { affectedChatKeys.add(it.key) }
            transientRamCache.entries
                .filter { (_, list) -> list.any { it.notificationTag == notificationTag } }
                .forEach { affectedChatKeys.add(it.key) }
        }

        for (resolvedChatKey in affectedChatKeys) {
            val snapshotItems = activeSnapshots[resolvedChatKey].orEmpty().filter { !it.isDeletionNotice }
            val ramList = transientRamCache[resolvedChatKey].orEmpty().toMutableList()
            val savedInDb = dao.getRecentMessagesForChat(resolvedChatKey, limit = 40).toMutableList()
            var rescuedAny = false

            // 1. Prüfe alle noch nicht als gelöscht markierten Nachrichten in SQLite (Staging + Bearbeitet)
            for (dbMsg in savedInDb.filter { !it.isDeleted && abs(nowMs - (it.editedAt ?: it.timestamp)) <= retractWindowMs }) {
                val matchesSnapshot = snapshotItems.isEmpty() || snapshotItems.any {
                    it.text == dbMsg.text && it.senderName.equals(dbMsg.senderName, ignoreCase = true)
                }
                if (!matchesSnapshot) continue

                val updated = dbMsg.copy(
                    isDeleted = true,
                    deletedAt = nowMs,
                )
                dao.update(updated)
                replaceInList(savedInDb, updated)
                ramList.firstOrNull { it.text == dbMsg.text && it.senderName.equals(dbMsg.senderName, ignoreCase = true) }
                    ?.let { removeFromTransientCache(it) }
                rescuedAny = true
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

            // 2. Prüfe aktive Snapshot-Nachrichten im RAM-Puffer (falls noch nicht in SQLite)
            if (!rescuedAny) {
                for (snap in snapshotItems) {
                    val ramMatch = ramList.firstOrNull {
                        it.text == snap.text &&
                            it.senderName.equals(snap.senderName, ignoreCase = true) &&
                            abs(nowMs - it.timestamp) <= retractWindowMs
                    }
                    if (ramMatch != null) {
                        removeFromTransientCache(ramMatch)
                        saveRescuedDeletion(
                            ramTarget = ramMatch,
                            now = nowMs,
                            mediaPreviewPath = ramMatch.mediaPreviewPath,
                            avatarPath = ramMatch.avatarPath,
                            savedInDb = savedInDb,
                        )
                        rescuedAny = true
                    }
                }
            }

            // 3. Fallback: Falls activeSnapshots schon leer war, rette die neueste Nachricht aus ramList
            if (!rescuedAny) {
                val newestCandidate = ramList.firstOrNull { abs(nowMs - it.timestamp) <= retractWindowMs }
                if (newestCandidate != null) {
                    removeFromTransientCache(newestCandidate)
                    saveRescuedDeletion(
                        ramTarget = newestCandidate,
                        now = nowMs,
                        mediaPreviewPath = newestCandidate.mediaPreviewPath,
                        avatarPath = newestCandidate.avatarPath,
                        savedInDb = savedInDb,
                    )
                }
            }

            activeSnapshots.remove(resolvedChatKey)
        }
        persistTransientCacheToPrefs()
    }

    /**
     * Bereinigt:
     * 1. Reguläre Zwischenspeicher-Nachrichten (`isDeleted = 0 AND isEdited = 0`), die älter als 24h sind.
     * 2. Alle gelöschten/bearbeiteten Nachrichten in der DB, die älter als 7 Tage sind.
     * 3. Alle lokalen Medien- & Avatar-Dateien, die älter als 7 Tage sind oder zu keiner
     *    gespeicherten Nachricht mehr gehören.
     * 4. Abgelaufene Einträge im flüchtigen RAM-Puffer.
     */
    suspend fun pruneExpiredData(nowMs: Long = System.currentTimeMillis()) = mutex.withLock {
        pruneInternal(nowMs)
    }

    private suspend fun pruneInternal(nowMs: Long) {
        // 1. Normale Zwischenspeicher-Nachrichten (nicht gelöscht & nicht bearbeitet) nach 24h entfernen
        val stagingCutoff = nowMs - STAGING_RETENTION_MS
        dao.deleteExpiredNormalMessages(stagingCutoff)

        // 2. Alles älter als 7 Tage (RETENTION_MS) aus der DB + Disk löschen
        val cutoff = nowMs - RETENTION_MS
        val expired = dao.getExpiredMessages(cutoff)
        for (msg in expired) {
            msg.mediaPreviewPath?.let { path -> runCatching { File(path).delete() } }
        }
        dao.deleteExpiredMessages(cutoff)

        // 3. Flüchtigen RAM-Cache begrenzen (max. 6 Stunden im RAM)
        val ramCutoff = nowMs - TRANSIENT_RAM_TTL_MS
        var cacheChanged = false
        for ((key, list) in transientRamCache) {
            if (list.removeAll { it.timestamp < ramCutoff }) {
                cacheChanged = true
            }
            if (list.isEmpty()) {
                transientRamCache.remove(key)
                cacheChanged = true
            }
        }
        if (cacheChanged) {
            persistTransientCacheToPrefs()
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
     *   nur kurzzeitig im flüchtigen Puffer (`transientRamCache`) vorgehalten.
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
        val savedInDb = dao.getRecentMessagesForChat(chatKey, limit = 40).toMutableList()
        val nonDeletedParsed = parsedList.filter { !it.isDeletionNotice }
        val hasMessagingStyle = payload.messagingStyleItems.isNotEmpty()

        // ── SPEZIALFALL A: Nachricht verschwand aus aktivem ungelesenen Stack ohne Platzhalter ──
        // 1) Vergleich mit previousSnapshot: Wenn mindestens eine Nachricht aus previousSnapshot
        //    weiterhin ungelesen in parsedList steht, aber eine andere Nachricht aus
        //    previousSnapshot (egal ob älter oder neuer!) plötzlich fehlt, wurde sie für alle gelöscht!
        if (previousSnapshot.size > parsedList.size &&
            nonDeletedParsed.isNotEmpty() &&
            parsedList.none { it.isDeletionNotice }
        ) {
            val sharesUnreadWithPrevious = nonDeletedParsed.any { cur ->
                previousSnapshot.any { prev ->
                    !prev.isDeletionNotice &&
                        prev.text == cur.text &&
                        prev.senderName.equals(cur.senderName, ignoreCase = true)
                }
            }
            if (sharesUnreadWithPrevious) {
                val vanishedFromSnapshot = filterUnconsumedSnapshotItems(previousSnapshot, nonDeletedParsed)
                for (vanished in vanishedFromSnapshot) {
                    val dbMatch = savedInDb.firstOrNull {
                        !it.isDeleted &&
                            it.text == vanished.text &&
                            it.senderName.equals(vanished.senderName, ignoreCase = true)
                    }
                    if (dbMatch != null) {
                        val updated = dbMatch.copy(
                            isDeleted = true,
                            deletedAt = now,
                            avatarPath = avatarPath ?: dbMatch.avatarPath,
                        )
                        dao.update(updated)
                        replaceInList(savedInDb, updated)
                        ramList.firstOrNull {
                            it.text == vanished.text && it.senderName.equals(vanished.senderName, ignoreCase = true)
                        }?.let { removeFromTransientCache(it) }
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
                        continue
                    }

                    val ramMatch = ramList.firstOrNull {
                        it.text == vanished.text &&
                            it.senderName.equals(vanished.senderName, ignoreCase = true)
                    }
                    if (ramMatch != null) {
                        removeFromTransientCache(ramMatch)
                        saveRescuedDeletion(ramMatch, now, mediaPreviewPath, avatarPath, savedInDb)
                    }
                }
            }
        }

        // 2) Direkter Vergleich mit ramList (funktioniert selbst dann, wenn previousSnapshot
        //    durch Service-Neustart oder Notification-Cancel leer war!):
        //    Da ramList neueste Nachrichten zuerst speichert (Index 0 = neueste), gilt:
        //    Wenn eine ÄLTERE Nachricht (Index `oldestUnreadIdx > 0` in ramList) noch immer
        //    ungelesen im MessagingStyle-Stack steht, eine NEUERE Nachricht (`0 until oldestUnreadIdx`)
        //    aber nicht mehr im Stack ist (und auch kein Edit ist), wurde die neuere Nachricht
        //    zu 100 % für alle gelöscht!
        if (hasMessagingStyle &&
            nonDeletedParsed.isNotEmpty() &&
            parsedList.none { it.isDeletionNotice } &&
            ramList.size >= 2
        ) {
            val unconsumedInRam = buildEligibleRamCandidates(ramList, nonDeletedParsed)
            val oldestStillUnreadInRamIdx = ramList.indexOfLast { ramItem ->
                nonDeletedParsed.any { cur ->
                    cur.text == ramItem.text &&
                        cur.senderName.equals(ramItem.senderName, ignoreCase = true)
                }
            }
            if (oldestStillUnreadInRamIdx > 0) {
                val newerVanishedCandidates = ramList
                    .subList(0, oldestStillUnreadInRamIdx)
                    .filter { it in unconsumedInRam }
                    .toList()
                for (vanishedRam in newerVanishedCandidates) {
                    // Nicht als gelöscht werten, falls eine der Nachrichten in parsedList ein Edit davon ist
                    val isBeingEditedNow = nonDeletedParsed.any { cur ->
                        cur.isExplicitEdit ||
                            (cur.senderName.equals(vanishedRam.senderName, ignoreCase = true) &&
                                cur.timestamp == vanishedRam.timestamp &&
                                cur.text != vanishedRam.text)
                    }
                    if (!isBeingEditedNow) {
                        removeFromTransientCache(vanishedRam)
                        saveRescuedDeletion(vanishedRam, now, mediaPreviewPath, avatarPath, savedInDb)
                    }
                }
            }
        }

        // ── PASS 1: Zuerst ALLE Löschnachrichten („Diese Nachricht wurde gelöscht“) verarbeiten! ──
        // Dadurch wird verhindert, dass eine gelöschte Nachricht bei gleichem Timestamp oder
        // verschobenem Slot fälschlicherweise in Pass 2 als „Bearbeitet“ konsumiert wird.
        for ((index, parsed) in parsedList.withIndex()) {
            if (!parsed.isDeletionNotice) continue
            val prevAtSlot = previousSnapshot.getOrNull(index)

            // 1. Zuerst in SQLite (Chat-spezifisch + Cross-Chat-Fallback) nach der Originalnachricht suchen
            val existingDbMatch = findDbDeletionTarget(
                parsed = parsed,
                prevAtSlot = prevAtSlot,
                previousSnapshot = previousSnapshot,
                nonDeletedParsed = nonDeletedParsed,
                savedInDb = savedInDb,
            ) ?: findCrossChatDbDeletionTarget(
                parsed = parsed,
                now = now,
            )

            if (existingDbMatch != null) {
                if (!existingDbMatch.isDeleted) {
                    val updated = existingDbMatch.copy(
                        isDeleted = true,
                        deletedAt = now,
                        avatarPath = avatarPath ?: existingDbMatch.avatarPath,
                    )
                    dao.update(updated)
                    replaceInList(savedInDb, updated)
                    ramList.firstOrNull {
                        it.text == existingDbMatch.text &&
                            it.senderName.equals(existingDbMatch.senderName, ignoreCase = true)
                    }?.let { removeFromTransientCache(it) }
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

            // 2. Zweiter Fallback: Aus dem flüchtigen RAM-Puffer holen (inkl. Cross-Chat/Tag-Fallback)
            val ramTarget = findRamDeletionTarget(
                parsed = parsed,
                prevAtSlot = prevAtSlot,
                previousSnapshot = previousSnapshot,
                ramList = ramList,
                nonDeletedParsed = nonDeletedParsed,
            )
            if (ramTarget != null) {
                removeFromTransientCache(ramTarget)
                saveRescuedDeletion(ramTarget, now, mediaPreviewPath, avatarPath, savedInDb)
                continue
            }

            // 3. Letzter Fallback (wie in v0.89.46): Falls die Originalnachricht bei geöffnetem
            //    WhatsApp-Chat (ohne Push-Benachrichtigung) oder vor Aktivierung des Wächters
            //    empfangen wurde und WhatsApp nun „🚫 Diese Nachricht wurde gelöscht“ meldet:
            val alreadyLoggedRecent = savedInDb.any {
                it.isDeleted && abs((it.deletedAt ?: it.timestamp) - now) < 15_000L
            }
            if (!alreadyLoggedRecent && !WhatsAppNotificationParser.isGenericOrSummaryTitle(parsed.chatTitle)) {
                val fallback = WhatsAppMessageEntity(
                    chatKey = parsed.chatKey,
                    chatTitle = parsed.chatTitle,
                    senderName = parsed.senderName,
                    isGroup = parsed.isGroup,
                    packageName = parsed.packageName,
                    text = "Nachricht wurde gelöscht (Original bei geöffnetem Chat oder vor Wächter-Start empfangen)",
                    isDeleted = true,
                    deletedAt = now,
                    avatarPath = avatarPath,
                    timestamp = parsed.timestamp,
                    notificationKey = parsed.notificationKey,
                    isReadInHikari = false,
                )
                val newId = dao.insert(fallback)
                savedInDb.add(0, fallback.copy(id = newId))
            }
        }

        // ── PASS 2: Reguläre & bearbeitete Nachrichten verarbeiten ──────────────────────
        for ((index, parsed) in parsedList.withIndex()) {
            if (parsed.isDeletionNotice) continue

            // ── FALL 2: Wurde eine bestehende Nachricht BEARBEITET? ────────────────
            // 2a) Wurde eine bereits als "Bearbeitet" in der DB liegende Nachricht nochmals editiert?
            val dbEditTarget = findDbEditTarget(
                parsed = parsed,
                index = index,
                parsedList = parsedList,
                nonDeletedParsed = nonDeletedParsed,
                previousSnapshot = previousSnapshot,
                hasMessagingStyle = hasMessagingStyle,
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
                // Auch im RAM-Puffer auf den neuen Text aktualisieren, falls sie später gelöscht wird
                val oldRam = ramList.firstOrNull {
                    it.text == dbEditTarget.text && it.senderName.equals(dbEditTarget.senderName, ignoreCase = true)
                }
                if (oldRam != null) {
                    val idxInRam = ramList.indexOf(oldRam)
                    if (idxInRam >= 0) {
                        ramList[idxInRam] = oldRam.copy(text = parsed.text)
                    }
                }
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
                parsedList = parsedList,
                nonDeletedParsed = nonDeletedParsed,
                previousSnapshot = previousSnapshot,
                hasMessagingStyle = hasMessagingStyle,
                ramList = ramList,
            )
            if (ramEditTarget != null && ramEditTarget.text != parsed.text) {
                val idxInRam = ramList.indexOf(ramEditTarget)
                if (idxInRam >= 0) {
                    ramList[idxInRam] = ramEditTarget.copy(text = parsed.text)
                } else {
                    removeFromTransientCache(ramEditTarget)
                }
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

            // ── FALL 3: Reguläre Nachricht -> im unsichtbaren SQLite-Staging + RAM-Puffer sichern ──
            val occurrencesInPayloadUpToHere = parsedList
                .subList(0, index + 1)
                .count {
                    !it.isDeletionNotice &&
                        it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                        it.text == parsed.text
                }

            val nonDeletedInDb = savedInDb.filter { !it.isDeleted }
            val matchingInDb = nonDeletedInDb.count {
                it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                    it.text == parsed.text
            }
            val hasExactTimestampInDb = nonDeletedInDb.any {
                it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                    it.text == parsed.text &&
                    abs(it.timestamp - parsed.timestamp) <= 1_500L
            }
            val alreadyInDb = matchingInDb >= occurrencesInPayloadUpToHere &&
                (hasExactTimestampInDb || (hasMessagingStyle && matchingInDb >= occurrencesInPayloadUpToHere))

            if (!alreadyInDb) {
                val stagingEntity = WhatsAppMessageEntity(
                    chatKey = parsed.chatKey,
                    chatTitle = parsed.chatTitle,
                    senderName = parsed.senderName,
                    isGroup = parsed.isGroup,
                    packageName = parsed.packageName,
                    text = parsed.text,
                    originalText = if (parsed.isExplicitEdit) parsed.text else null,
                    isEdited = parsed.isExplicitEdit,
                    editedAt = if (parsed.isExplicitEdit) now else null,
                    isDeleted = false,
                    mediaType = parsed.mediaType,
                    mediaPreviewPath = mediaPreviewPath,
                    avatarPath = avatarPath,
                    timestamp = parsed.timestamp,
                    notificationKey = parsed.notificationKey,
                    isReadInHikari = false,
                )
                val insertedId = dao.insert(stagingEntity)
                savedInDb.add(0, stagingEntity.copy(id = insertedId))
            } else {
                // Optional Avatar oder MediaPreview an bestehendem Staging-Eintrag ergänzen
                val existing = nonDeletedInDb.firstOrNull {
                    it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                        it.text == parsed.text
                }
                if (existing != null &&
                    ((avatarPath != null && existing.avatarPath == null) ||
                        (mediaPreviewPath != null && existing.mediaPreviewPath == null))
                ) {
                    val enriched = existing.copy(
                        avatarPath = avatarPath ?: existing.avatarPath,
                        mediaPreviewPath = mediaPreviewPath ?: existing.mediaPreviewPath,
                    )
                    dao.update(enriched)
                    replaceInList(savedInDb, enriched)
                }
            }

            val matchingInRam = ramList.count {
                it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                    it.text == parsed.text
            }
            val hasExactTimestampInRam = ramList.any {
                it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                    it.text == parsed.text &&
                    abs(it.timestamp - parsed.timestamp) <= 1_500L
            }
            val alreadyInRam = matchingInRam >= occurrencesInPayloadUpToHere &&
                (hasExactTimestampInRam || (hasMessagingStyle && matchingInRam >= occurrencesInPayloadUpToHere))

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
                        notificationTag = parsed.notificationTag,
                    ),
                )
                while (ramList.size > MAX_TRANSIENT_PER_CHAT) {
                    ramList.removeAt(ramList.lastIndex)
                }
            }
        }

        persistTransientCacheToPrefs()
        if (!WhatsAppNotificationParser.isGenericOrSummaryTitle(parsedList.first().chatTitle)) {
            activeSnapshots[chatKey] = parsedList
        }
    }

    private suspend fun saveRescuedDeletion(
        ramTarget: TransientCandidate,
        now: Long,
        mediaPreviewPath: String?,
        avatarPath: String?,
        savedInDb: MutableList<WhatsAppMessageEntity>,
    ) {
        val alreadySavedDeleted = savedInDb.any {
            it.isDeleted &&
                it.senderName.equals(ramTarget.senderName, ignoreCase = true) &&
                it.text == ramTarget.text &&
                abs(it.timestamp - ramTarget.timestamp) <= 1_000L
        }
        if (alreadySavedDeleted) return

        // Falls die Nachricht bereits als unsichtbarer Staging-Eintrag in SQLite liegt -> in-place auf isDeleted = true setzen!
        val existingUnmarked = savedInDb.firstOrNull {
            !it.isDeleted &&
                it.senderName.equals(ramTarget.senderName, ignoreCase = true) &&
                it.text == ramTarget.text &&
                abs(it.timestamp - ramTarget.timestamp) <= 5_000L
        } ?: savedInDb.firstOrNull {
            !it.isDeleted &&
                it.senderName.equals(ramTarget.senderName, ignoreCase = true) &&
                it.text == ramTarget.text
        }

        if (existingUnmarked != null) {
            val updated = existingUnmarked.copy(
                isDeleted = true,
                deletedAt = now,
                mediaPreviewPath = existingUnmarked.mediaPreviewPath ?: ramTarget.mediaPreviewPath ?: mediaPreviewPath,
                avatarPath = existingUnmarked.avatarPath ?: ramTarget.avatarPath ?: avatarPath,
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
            return
        }

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

    private fun removeFromTransientCache(candidate: TransientCandidate) {
        transientRamCache[candidate.chatKey]?.remove(candidate)
        for ((key, list) in transientRamCache) {
            list.remove(candidate)
            if (list.isEmpty()) {
                transientRamCache.remove(key)
            }
        }
    }

    /**
     * Gibt alle Einträge aus `previousSnapshot` zurück, die nicht mehr durch `nonDeletedParsed`
     * abgedeckt sind (unter Berücksichtigung mehrfach identischer Nachrichtentexte).
     */
    private fun filterUnconsumedSnapshotItems(
        previousSnapshot: List<ParsedWhatsAppMessage>,
        nonDeletedParsed: List<ParsedWhatsAppMessage>,
    ): List<ParsedWhatsAppMessage> {
        val remainingActive = nonDeletedParsed.toMutableList()
        val vanished = mutableListOf<ParsedWhatsAppMessage>()
        for (prev in previousSnapshot) {
            if (prev.isDeletionNotice) continue
            val matchIdx = remainingActive.indexOfFirst {
                it.text == prev.text && it.senderName.equals(prev.senderName, ignoreCase = true)
            }
            if (matchIdx >= 0) {
                remainingActive.removeAt(matchIdx)
            } else {
                vanished.add(prev)
            }
        }
        return vanished
    }

    /**
     * Gibt alle Kandidaten aus `ramList` zurück, die NICHT mehr im aktuellen ungelesenen
     * Notification-Stack (`nonDeletedParsed`) stehen.
     */
    private fun buildEligibleRamCandidates(
        ramList: List<TransientCandidate>,
        nonDeletedParsed: List<ParsedWhatsAppMessage>,
    ): List<TransientCandidate> {
        if (nonDeletedParsed.isEmpty()) return ramList
        val remainingRam = ramList.toMutableList()
        for (active in nonDeletedParsed) {
            val exactIdx = remainingRam.indexOfFirst {
                it.senderName.equals(active.senderName, ignoreCase = true) &&
                    it.text == active.text &&
                    abs(it.timestamp - active.timestamp) <= 2_000L
            }
            if (exactIdx >= 0) {
                remainingRam.removeAt(exactIdx)
                continue
            }
            val fallbackIdx = remainingRam.indexOfLast {
                it.senderName.equals(active.senderName, ignoreCase = true) &&
                    it.text == active.text
            }
            if (fallbackIdx >= 0) {
                remainingRam.removeAt(fallbackIdx)
            }
        }
        return remainingRam
    }

    private fun findRamDeletionTarget(
        parsed: ParsedWhatsAppMessage,
        prevAtSlot: ParsedWhatsAppMessage?,
        previousSnapshot: List<ParsedWhatsAppMessage>,
        ramList: List<TransientCandidate>,
        nonDeletedParsed: List<ParsedWhatsAppMessage>,
    ): TransientCandidate? {
        // 1. Exakter Slot-Treffer aus dem vorherigen Notification-Snapshot
        if (prevAtSlot != null && !prevAtSlot.isDeletionNotice) {
            ramList.firstOrNull {
                it.senderName.equals(prevAtSlot.senderName, ignoreCase = true) &&
                    it.text == prevAtSlot.text
            }?.let { return it }
        }

        // 1b. Falls sich die Slot-Position verschoben hat: Prüfe Einträge aus previousSnapshot,
        //     die jetzt nicht mehr in nonDeletedParsed vorkommen
        val vanishedFromSnapshot = filterUnconsumedSnapshotItems(previousSnapshot, nonDeletedParsed)
        for (vanished in vanishedFromSnapshot) {
            ramList.firstOrNull {
                it.senderName.equals(vanished.senderName, ignoreCase = true) &&
                    it.text == vanished.text
            }?.let { return it }
        }

        // Kandidaten im aktuellen Chat, die NICHT mehr im aktiven Notification-Stack stehen
        val eligibleInChat = buildEligibleRamCandidates(ramList, nonDeletedParsed).ifEmpty { ramList }

        // 2. Gleicher Absender + fast identischer Timestamp
        eligibleInChat.firstOrNull {
            it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                abs(it.timestamp - parsed.timestamp) <= 5_000L
        }?.let { return it }

        // 3. Gleicher Absender im selben Chat (neueste zuerst)
        eligibleInChat.firstOrNull {
            it.senderName.equals(parsed.senderName, ignoreCase = true)
        }?.let { return it }

        // 4. Beliebiger Kandidat im selben Chat
        eligibleInChat.firstOrNull()?.let { return it }

        // 5. Cross-Chat Fallback: Falls die Löschnachricht einen anderen Titel hatte
        //    (z. B. "WhatsApp" oder abgewandelter Gruppentitel), prüfe alle gecachten Chats:
        val allCandidates = transientRamCache.values
            .flatten()
            .filter { it.packageName == parsed.packageName }
            .sortedByDescending { it.timestamp }

        if (allCandidates.isEmpty()) return null

        // 5a. Gleicher NotificationTag oder NotificationKey
        if (!parsed.notificationTag.isNullOrBlank()) {
            allCandidates.firstOrNull { it.notificationTag == parsed.notificationTag }?.let { return it }
        }
        if (!parsed.notificationKey.isNullOrBlank()) {
            allCandidates.firstOrNull { it.notificationKey == parsed.notificationKey }?.let { return it }
        }

        // 5b. Gleicher Absendername oder Chat-Titel über alle Chats hinweg
        if (!WhatsAppNotificationParser.isGenericOrSummaryTitle(parsed.senderName)) {
            allCandidates.firstOrNull {
                it.senderName.equals(parsed.senderName, ignoreCase = true) ||
                    it.chatTitle.equals(parsed.senderName, ignoreCase = true) ||
                    it.chatTitle.equals(parsed.chatTitle, ignoreCase = true)
            }?.let { return it }
        }

        // 5c. Wenn der Titel generisch ("WhatsApp") war, nimm die neueste Nachricht der letzten 30 Min.
        val thirtyMinMs = 30L * 60L * 1000L
        return allCandidates.firstOrNull { abs(parsed.timestamp - it.timestamp) <= thirtyMinMs }
    }

    private fun buildEligibleDbCandidates(
        nonDeletedDb: List<WhatsAppMessageEntity>,
        nonDeletedParsed: List<ParsedWhatsAppMessage>,
    ): List<WhatsAppMessageEntity> {
        if (nonDeletedParsed.isEmpty()) return nonDeletedDb
        val remainingDb = nonDeletedDb.toMutableList()
        for (active in nonDeletedParsed) {
            val exactIdx = remainingDb.indexOfFirst {
                it.senderName.equals(active.senderName, ignoreCase = true) &&
                    it.text == active.text &&
                    abs(it.timestamp - active.timestamp) <= 2_000L
            }
            if (exactIdx >= 0) {
                remainingDb.removeAt(exactIdx)
                continue
            }
            val fallbackIdx = remainingDb.indexOfLast {
                it.senderName.equals(active.senderName, ignoreCase = true) &&
                    it.text == active.text
            }
            if (fallbackIdx >= 0) {
                remainingDb.removeAt(fallbackIdx)
            }
        }
        return remainingDb
    }

    private fun findDbDeletionTarget(
        parsed: ParsedWhatsAppMessage,
        prevAtSlot: ParsedWhatsAppMessage?,
        previousSnapshot: List<ParsedWhatsAppMessage>,
        nonDeletedParsed: List<ParsedWhatsAppMessage>,
        savedInDb: List<WhatsAppMessageEntity>,
    ): WhatsAppMessageEntity? {
        val nonDeletedDb = savedInDb.filter { !it.isDeleted }
        if (nonDeletedDb.isEmpty()) return null

        if (prevAtSlot != null && !prevAtSlot.isDeletionNotice) {
            nonDeletedDb.firstOrNull {
                it.senderName.equals(prevAtSlot.senderName, ignoreCase = true) &&
                    it.text == prevAtSlot.text
            }?.let { return it }
        }

        val vanishedFromSnapshot = filterUnconsumedSnapshotItems(previousSnapshot, nonDeletedParsed)
        for (vanished in vanishedFromSnapshot) {
            nonDeletedDb.firstOrNull {
                it.senderName.equals(vanished.senderName, ignoreCase = true) &&
                    it.text == vanished.text
            }?.let { return it }
        }

        val eligibleDb = buildEligibleDbCandidates(nonDeletedDb, nonDeletedParsed).ifEmpty { nonDeletedDb }

        eligibleDb.firstOrNull {
            it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                abs(it.timestamp - parsed.timestamp) <= 5_000L
        }?.let { return it }

        eligibleDb.firstOrNull {
            it.senderName.equals(parsed.senderName, ignoreCase = true)
        }?.let { return it }

        return eligibleDb.firstOrNull()
    }

    private suspend fun findCrossChatDbDeletionTarget(
        parsed: ParsedWhatsAppMessage,
        now: Long,
    ): WhatsAppMessageEntity? {
        val candidates = dao.getRecentUnmarkedMessages(
            packageName = parsed.packageName,
            sinceTimestamp = now - STAGING_RETENTION_MS,
            limit = 40,
        )
        if (candidates.isEmpty()) return null

        if (!parsed.notificationKey.isNullOrBlank()) {
            candidates.firstOrNull { it.notificationKey == parsed.notificationKey }?.let { return it }
        }

        if (!WhatsAppNotificationParser.isGenericOrSummaryTitle(parsed.senderName)) {
            candidates.firstOrNull {
                it.senderName.equals(parsed.senderName, ignoreCase = true) ||
                    it.chatTitle.equals(parsed.senderName, ignoreCase = true) ||
                    it.chatTitle.equals(parsed.chatTitle, ignoreCase = true)
            }?.let { return it }
        }

        val thirtyMinMs = 30L * 60L * 1000L
        return candidates.firstOrNull { abs(parsed.timestamp - it.timestamp) <= thirtyMinMs }
    }

    private fun findRamEditTarget(
        parsed: ParsedWhatsAppMessage,
        index: Int,
        parsedList: List<ParsedWhatsAppMessage>,
        nonDeletedParsed: List<ParsedWhatsAppMessage>,
        previousSnapshot: List<ParsedWhatsAppMessage>,
        hasMessagingStyle: Boolean,
        ramList: List<TransientCandidate>,
    ): TransientCandidate? {
        // Medien wie Sprachnachrichten, Audios, Sticker, GIFs, Dokumente oder reine
        // "📷 Foto"/"🎥 Video"-Platzhalter können in WhatsApp NIEMALS bearbeitet werden!
        if (!WhatsAppNotificationParser.isEditableMessage(parsed.text, parsed.mediaType)) {
            return null
        }

        // Wenn dieser Text bereits unverändert im vorherigen Snapshot oder im RAM-Puffer lag,
        // ist es eine bestehende unveränderte Nachricht und NIEMALS das Ergebnis einer Bearbeitung!
        if (!parsed.isExplicitEdit) {
            val existedInPrevious = previousSnapshot.any {
                !it.isDeletionNotice &&
                    it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                    it.text == parsed.text
            }
            val existedInRam = ramList.any {
                it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                    it.text == parsed.text
            }
            if (existedInPrevious || existedInRam) return null

            // Wenn die aktuelle Benachrichtigung ein Lösch-Event ist (enthält Löschnachricht oder
            // der Stack ist geschrumpft), darf keine heuristische Bearbeitung geraten werden!
            if (parsedList.any { it.isDeletionNotice } ||
                (previousSnapshot.isNotEmpty() && parsedList.size < previousSnapshot.size)
            ) {
                return null
            }
        }

        val editWindowMs = 16 * 60 * 1000L
        val unconsumedRam = buildEligibleRamCandidates(ramList, nonDeletedParsed)
        val editableRam = unconsumedRam.filter {
            WhatsAppNotificationParser.isEditableMessage(it.text, it.mediaType) &&
                it.mediaType == parsed.mediaType
        }
        if (editableRam.isEmpty()) return null

        // 1. Explizite "(Bearbeitet)"-Kennzeichnung im Text
        if (parsed.isExplicitEdit) {
            return editableRam.firstOrNull {
                it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                    abs(parsed.timestamp - it.timestamp) <= editWindowMs
            }
        }

        // 2. Identischer Erstellungs-Timestamp in MessagingStyle, aber geänderter Text
        editableRam.firstOrNull {
            it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                it.timestamp == parsed.timestamp &&
                it.text != parsed.text
        }?.let { return it }

        // 3. In-Place-Änderung innerhalb eines aktiven MessagingStyle-Stacks mit >= 2 ungelesenen
        //    Nachrichten, bei dem mindestens eine andere Nachricht im Stack unverändert blieb
        //    (verhindert zuverlässig, dass zwei einzelne Nachrichten nacheinander als Edit gelten!).
        if (hasMessagingStyle &&
            previousSnapshot.size >= 2 &&
            parsedList.size == previousSnapshot.size
        ) {
            val otherSlotUnchanged = parsedList.indices.any { i ->
                i != index &&
                    previousSnapshot.getOrNull(i)?.text == parsedList[i].text &&
                    previousSnapshot.getOrNull(i)?.senderName.equals(parsedList[i].senderName, ignoreCase = true)
            }
            val prevSlot = previousSnapshot.getOrNull(index)
            if (otherSlotUnchanged &&
                prevSlot != null &&
                !prevSlot.isDeletionNotice &&
                WhatsAppNotificationParser.isEditableMessage(prevSlot.text, prevSlot.mediaType) &&
                prevSlot.mediaType == parsed.mediaType &&
                prevSlot.senderName.equals(parsed.senderName, ignoreCase = true) &&
                prevSlot.text != parsed.text
            ) {
                return editableRam.firstOrNull {
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
        parsedList: List<ParsedWhatsAppMessage>,
        nonDeletedParsed: List<ParsedWhatsAppMessage>,
        previousSnapshot: List<ParsedWhatsAppMessage>,
        hasMessagingStyle: Boolean,
        savedInDb: List<WhatsAppMessageEntity>,
    ): WhatsAppMessageEntity? {
        if (!WhatsAppNotificationParser.isEditableMessage(parsed.text, parsed.mediaType)) {
            return null
        }

        if (!parsed.isExplicitEdit) {
            val existedInPrevious = previousSnapshot.any {
                !it.isDeletionNotice &&
                    it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                    it.text == parsed.text
            }
            val existedInDb = savedInDb.any {
                !it.isDeleted &&
                    it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                    it.text == parsed.text
            }
            if (existedInPrevious || existedInDb) return null

            if (parsedList.any { it.isDeletionNotice } ||
                (previousSnapshot.isNotEmpty() && parsedList.size < previousSnapshot.size)
            ) {
                return null
            }
        }

        val activeTexts = nonDeletedParsed.map { it.text }.toSet()
        val editWindowMs = 16 * 60 * 1000L
        val editableDb = savedInDb.filter {
            !it.isDeleted &&
                it.text !in activeTexts &&
                WhatsAppNotificationParser.isEditableMessage(it.text, it.mediaType) &&
                it.mediaType == parsed.mediaType
        }
        if (editableDb.isEmpty()) return null

        if (parsed.isExplicitEdit) {
            return editableDb.firstOrNull {
                it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                    abs(parsed.timestamp - it.timestamp) <= editWindowMs
            }
        }

        editableDb.firstOrNull {
            it.senderName.equals(parsed.senderName, ignoreCase = true) &&
                it.timestamp == parsed.timestamp &&
                it.text != parsed.text
        }?.let { return it }

        if (hasMessagingStyle &&
            previousSnapshot.size >= 2 &&
            parsedList.size == previousSnapshot.size
        ) {
            val otherSlotUnchanged = parsedList.indices.any { i ->
                i != index &&
                    previousSnapshot.getOrNull(i)?.text == parsedList[i].text &&
                    previousSnapshot.getOrNull(i)?.senderName.equals(parsedList[i].senderName, ignoreCase = true)
            }
            val prevSlot = previousSnapshot.getOrNull(index)
            if (otherSlotUnchanged &&
                prevSlot != null &&
                !prevSlot.isDeletionNotice &&
                WhatsAppNotificationParser.isEditableMessage(prevSlot.text, prevSlot.mediaType) &&
                prevSlot.mediaType == parsed.mediaType &&
                prevSlot.senderName.equals(parsed.senderName, ignoreCase = true) &&
                prevSlot.text != parsed.text
            ) {
                return editableDb.firstOrNull {
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
        runCatching { prefs.edit().remove(KEY_TRANSIENT_CACHE).apply() }
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
        private const val KEY_TRANSIENT_CACHE = "transient_ram_cache_v1"

        /** Aufbewahrungsdauer für gelöschte & bearbeitete Nachrichten: exakt 7 Tage. */
        const val RETENTION_DAYS = 7
        const val RETENTION_MS = 7L * 24L * 60L * 60L * 1000L

        /** Aufbewahrungsdauer für unsichtbare Zwischenspeicher-Nachrichten in SQLite: 24 Stunden. */
        const val STAGING_RETENTION_MS = 24L * 60L * 60L * 1000L

        /** Flüchtige Vorhaltezeit im RAM für noch nicht gelöschte Nachrichten: 6 Stunden. */
        private const val TRANSIENT_RAM_TTL_MS = 6L * 60L * 60L * 1000L
        private const val MAX_TRANSIENT_PER_CHAT = 30
    }
}
