package com.hikari.app.domain.whatsapp

/**
 * Ordnet eine von WhatsApp heruntergeladene Datei der Nachricht zu, zu der sie gehört — und
 * umgekehrt. WhatsApp verrät die Zuordnung nirgends; bekannt ist nur, wann die Nachricht
 * geschrieben wurde und wann die Datei auf dem Handy ankam.
 *
 * Gemessen am 20.11. (Kadirs Fold): WhatsApp lädt Bilder teils Minuten nach der Benachrichtigung
 * und mehrere wartende Bilder dann in der Reihenfolge ihres Eintreffens. Daraus die Regeln:
 *  - Eine Datei gehört zu einer Nachricht, die *vor* ihr geschrieben wurde (kleiner Spielraum für
 *    Uhrabweichungen), höchstens [WINDOW_MS] vorher.
 *  - Mehrere Kandidaten: die älteste zuerst — so wie WhatsApp sie lädt.
 *  - Wurde die Nachricht schon vor dem Eintreffen der Datei gelöscht, lädt WhatsApp sie nie
 *    mehr; eine später eintreffende Datei gehört also zu einer anderen Nachricht.
 *  - Der Zeitpunkt, zu dem Hikari eine Nachricht *gesehen* hat, zählt nicht: nach einem Neustart
 *    liest der Wächter auch tagealte Nachrichten neu ein.
 */
object WhatsAppMediaMatcher {

    data class Message(val id: Long, val timestamp: Long, val deletedAt: Long?)

    data class IncomingFile(val path: String, val arrivedAt: Long)

    const val WINDOW_MS = 30L * 60L * 1000L
    const val CLOCK_SLACK_MS = 5_000L

    /** Die Nachricht, zu der eine gerade angekommene Datei gehört, oder null. */
    fun messageFor(arrivedAt: Long, waiting: List<Message>): Message? =
        waiting
            .filter { fits(it, arrivedAt) }
            .minWithOrNull(compareBy<Message>({ it.timestamp }, { it.id }))

    /** Die bereits angekommene, noch freie Datei zu einer neu protokollierten Nachricht, oder null. */
    fun fileFor(message: Message, unclaimed: List<IncomingFile>): IncomingFile? =
        unclaimed
            .filter { fits(message, it.arrivedAt) }
            .minByOrNull { it.arrivedAt }

    private fun fits(message: Message, arrivedAt: Long): Boolean {
        if (message.timestamp > arrivedAt + CLOCK_SLACK_MS) return false
        if (message.timestamp < arrivedAt - WINDOW_MS) return false
        val deletedAt = message.deletedAt
        return deletedAt == null || deletedAt >= arrivedAt - CLOCK_SLACK_MS
    }
}
