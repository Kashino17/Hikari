package com.hikari.app.domain.whatsapp

/**
 * Vergleicht den Nachrichtenstapel einer WhatsApp-Benachrichtigung mit dem, was Hikari
 * über diesen Chat schon weiß, und leitet daraus ab, was wirklich passiert ist.
 *
 * Grundsatz: Eine Nachricht hat eine Identität aus Absender und Zeitstempel. Entschieden wird
 * nur, was sich aus dieser Identität beweisen lässt — es wird nie die "wahrscheinlich gemeinte"
 * Nachricht geraten. Lieber eine Löschung verpassen als eine erfinden.
 *
 * Was als gelöscht gilt:
 *  1. An der Stelle einer bekannten Nachricht (gleicher Absender, gleiche Sekunde) steht jetzt
 *     der Platzhalter "Diese Nachricht wurde gelöscht".
 *  2. Eine Nachricht fehlt im Stapel, obwohl eine ältere, früher gesehene Nachricht desselben
 *     Chats noch ungelesen darin steht. Lesen räumt immer den ganzen Chat ab und die
 *     Stapel-Obergrenze schneidet nur die ältesten Nachrichten ab — beides kann eine Lücke
 *     hinter einer noch vorhandenen älteren Nachricht nicht erklären.
 *  3. Die laufende Benachrichtigung verliert eine Nachricht, ohne dass etwas Neues dazukam,
 *     oder ein frischer Platzhalter nimmt ihren Platz ein.
 *
 * Was als bearbeitet gilt: In derselben, noch sichtbaren Benachrichtigung wird der Text einer
 * Nachricht an Ort und Stelle ersetzt (gleiche Identität, alter Text weg, neuer Text da) und
 * beide Texte sind echte, bearbeitbare Inhalte.
 */
internal object WhatsAppStackDiff {

    /** Eine Zeile aus Hikaris Chat-Protokoll (Room). */
    data class Known(
        val id: Long,
        val sender: String,
        val text: String,
        val timestamp: Long,
        val isDeleted: Boolean,
    )

    enum class DeletionSignal { PLACEHOLDER, VANISHED }

    sealed interface Event {
        /** Bisher unbekannte Nachricht -> ins Protokoll aufnehmen. */
        data class New(val message: ParsedWhatsAppMessage) : Event

        /** Die protokollierte Nachricht wurde für alle gelöscht. */
        data class Deleted(val knownId: Long, val signal: DeletionSignal) : Event

        /** Der Text der protokollierten Nachricht wurde vom Absender geändert. */
        data class Edited(val knownId: Long, val oldText: String, val message: ParsedWhatsAppMessage) : Event

        /** Gleiche Nachricht, neue Beschriftung (z. B. "📷 Foto" -> "📷 2 Fotos"): still übernehmen. */
        data class Relabeled(val knownId: Long, val message: ParsedWhatsAppMessage) : Event

        /** Eine als gelöscht vermerkte Nachricht steht wieder im Stapel -> Vermerk zurücknehmen. */
        data class Restored(val knownId: Long) : Event
    }

    /** WhatsApp erlaubt "Für alle löschen" rund zweieinhalb Tage lang. */
    private const val MAX_DELETABLE_AGE_MS = 61L * 60L * 60L * 1000L

    /**
     * Selbstlöschende Nachrichten laufen frühestens nach 24 Stunden ab und verschwinden dann als
     * älteste aus dem Stapel. Darunter kann ein reines Schrumpfen nur eine Löschung sein.
     */
    private const val MAX_SHRINK_AGE_MS = 23L * 60L * 60L * 1000L

    /**
     * @param previous Stapel derselben Benachrichtigung beim letzten Mal, `null` wenn die
     *   Benachrichtigung frisch erscheint (nach Lesen, Wegwischen oder Neustart).
     * @param current  aktueller Stapel.
     * @param known    Protokoll dieses Chats.
     */
    fun diff(
        previous: List<ParsedWhatsAppMessage>?,
        current: List<ParsedWhatsAppMessage>,
        known: List<Known>,
        now: Long,
    ): List<Event> {
        val events = mutableListOf<Event>()
        val hasPrevious = previous != null
        val previousMessages = previous.orEmpty().filter { !it.isDeletionNotice }
        val previousPlaceholders = previous.orEmpty().filter { it.isDeletionNotice }.toMutableList()

        // Noch nicht zugeordnete Protokollzeilen bzw. Einträge des vorherigen Stapels.
        val alive = known.filter { !it.isDeleted }.toMutableList()
        val alreadyDeleted = known.filter { it.isDeleted }.toMutableList()
        val previousLeft = previousMessages.toMutableList()

        // Protokollzeilen von Nachrichten, die weiterhin ungelesen im Stapel stehen.
        val survivors = mutableListOf<Known>()
        val unmatched = mutableListOf<ParsedWhatsAppMessage>()
        var somethingAdded = false

        // ── 1. Unveränderte Nachrichten abhaken ─────────────────────────────────────────
        for (item in current) {
            if (item.isDeletionNotice) continue

            val previousIndex = previousLeft.indexOfFirst { it.sameIdentityAs(item) && it.text == item.text }
            if (previousIndex >= 0) previousLeft.removeAt(previousIndex) else somethingAdded = true

            val exact = alive.indexOfFirst { item.sameIdentityAs(it) && it.text == item.text }
            if (exact >= 0) {
                survivors += alive.removeAt(exact)
                continue
            }
            // Kontakt wurde umbenannt/gespeichert: gleiche Sekunde, gleicher Text, anderer Name.
            val renamed = alive.indexOfFirst { it.timestamp == item.timestamp && it.text == item.text }
            if (renamed >= 0) {
                survivors += alive.removeAt(renamed)
                continue
            }
            // Steht wieder im Stapel, obwohl als gelöscht vermerkt -> war keine Löschung.
            val healed = alreadyDeleted.indexOfFirst { item.sameIdentityAs(it) && it.text == item.text }
            if (healed >= 0) {
                val row = alreadyDeleted.removeAt(healed)
                survivors += row
                events += Event.Restored(row.id)
                continue
            }
            unmatched += item
        }

        // ── 2. Platzhalter "Diese Nachricht wurde gelöscht" ─────────────────────────────
        val freshPlaceholders = mutableListOf<ParsedWhatsAppMessage>()
        for (placeholder in current) {
            if (!placeholder.isDeletionNotice) continue

            // Für diese Identität ist die Löschung schon vermerkt (Stapel wurde nur neu gepostet).
            val done = alreadyDeleted.indexOfFirst { placeholder.sameIdentityAs(it) }
            if (done >= 0) {
                alreadyDeleted.removeAt(done)
                continue
            }
            // Gleicher Absender, gleiche Sekunde, Text weg, Platzhalter da: eindeutig.
            val target = alive.indexOfFirst { placeholder.sameIdentityAs(it) }
            if (target >= 0) {
                val row = alive.removeAt(target)
                previousLeft.removeFirstMatching { it.sameIdentityAs(placeholder) && it.text == row.text }
                events += Event.Deleted(row.id, DeletionSignal.PLACEHOLDER)
                continue
            }
            // Stand beim letzten Mal schon da -> damals behandelt oder nicht auflösbar.
            val seenBefore = previousPlaceholders.indexOfFirst { it.sameIdentityAs(placeholder) }
            if (seenBefore >= 0) {
                previousPlaceholders.removeAt(seenBefore)
                continue
            }
            if (hasPrevious) freshPlaceholders += placeholder
        }

        // ── 3. Neuer Text an bekannter Stelle: Bearbeitung, Umbeschriftung oder neue Nachricht ──
        for (item in unmatched) {
            val orphanIndex = previousLeft.indexOfFirst { it.sameIdentityAs(item) }
            val rowIndex = if (orphanIndex >= 0) {
                alive.indexOfFirst { item.sameIdentityAs(it) && it.text == previousLeft[orphanIndex].text }
            } else {
                -1
            }
            if (orphanIndex >= 0 && rowIndex >= 0) {
                val old = previousLeft.removeAt(orphanIndex)
                val row = alive.removeAt(rowIndex)
                survivors += row
                events += if (isRealEdit(old, item)) {
                    Event.Edited(row.id, old.text, item)
                } else {
                    Event.Relabeled(row.id, item)
                }
                continue
            }
            events += Event.New(item)
            // Ohne vorherigen Stapel lässt sich "weitere Nachricht in derselben Sekunde" nicht von
            // "Text geändert" unterscheiden. Dann wird über diese Identität nichts gefolgert.
            alive.removeAll { item.sameIdentityAs(it) }
        }

        // ── 4. Fehlende Nachrichten: gelöscht oder nur gelesen/abgeschnitten? ───────────
        if (survivors.isEmpty() && freshPlaceholders.isEmpty()) return events

        val wasInPrevious = { row: Known ->
            previousMessages.any { it.sameIdentityAs(row) && it.text == row.text }
        }
        val lostFromPrevious = alive.count(wasInPrevious)
        val pureShrink = hasPrevious && !somethingAdded && survivors.isNotEmpty()
        // Ein frischer Platzhalter ersetzt genau eine Nachricht. Fehlen mehr Nachrichten als
        // Platzhalter dazukamen, ist nicht mehr eindeutig, welche gemeint ist -> nichts zuordnen.
        val placeholdersAreUnambiguous = lostFromPrevious <= freshPlaceholders.size

        for (row in alive.sortedBy { it.id }) {
            val age = now - row.timestamp
            if (age > MAX_DELETABLE_AGE_MS) continue
            val inPrevious = wasInPrevious(row)

            if (inPrevious && placeholdersAreUnambiguous && freshPlaceholders.isNotEmpty()) {
                var partner = freshPlaceholders.indexOfFirst { it.senderName.equals(row.sender, ignoreCase = true) }
                if (partner < 0 && freshPlaceholders.size == 1 && lostFromPrevious == 1) partner = 0
                if (partner >= 0) {
                    freshPlaceholders.removeAt(partner)
                    events += Event.Deleted(row.id, DeletionSignal.PLACEHOLDER)
                    continue
                }
            }

            val olderStillUnread = survivors.any { it.id < row.id && it.timestamp <= row.timestamp }
            val shrankInPlace = inPrevious && pureShrink && age <= MAX_SHRINK_AGE_MS
            if (olderStillUnread || shrankInPlace) {
                events += Event.Deleted(row.id, DeletionSignal.VANISHED)
            }
        }

        return events
    }

    private fun isRealEdit(old: ParsedWhatsAppMessage, new: ParsedWhatsAppMessage): Boolean {
        if (old.mediaType != new.mediaType) return false
        if (!WhatsAppNotificationParser.isEditableMessage(old.text, old.mediaType)) return false
        if (!WhatsAppNotificationParser.isEditableMessage(new.text, new.mediaType)) return false
        return normalize(old.text) != normalize(new.text)
    }

    private fun normalize(text: String): String = text.trim().replace(Regex("\\s+"), " ")

    private fun ParsedWhatsAppMessage.sameIdentityAs(other: ParsedWhatsAppMessage): Boolean =
        timestamp == other.timestamp && senderName.equals(other.senderName, ignoreCase = true)

    private fun ParsedWhatsAppMessage.sameIdentityAs(row: Known): Boolean =
        timestamp == row.timestamp && senderName.equals(row.sender, ignoreCase = true)

    private inline fun <T> MutableList<T>.removeFirstMatching(predicate: (T) -> Boolean) {
        val index = indexOfFirst(predicate)
        if (index >= 0) removeAt(index)
    }
}
