package com.hikari.app.domain.russian

/**
 * Wann ein Fehler der Android-Spracherkennung still mit einem neuen Anlauf beantwortet wird,
 * statt ihn dem Lernenden zu zeigen.
 *
 * Der Erkennungsdienst stolpert typischerweise beim zweiten Versuch kurz nach dem ersten: Er
 * meldet dann sofort »Netzwerk« oder »Server«, obwohl das Netz da ist — die alte Sitzung ist
 * auf seiner Seite noch nicht abgeräumt. Ein neuer Anlauf einen Moment später klappt.
 * Echte Ausfälle (kein Netz, kein Sprachpaket) schlagen auch beim Wiederholen sofort fehl
 * und werden danach wie bisher gemeldet.
 */
object RuSpeechRetry {
    const val MAX_RETRIES = 3

    /** Ein Fehler, der später als so kommt, ist kein Stolpern beim Start mehr. */
    const val EARLY_MS = 4_000L

    // Fehlercodes von android.speech.SpeechRecognizer
    private const val NETWORK_TIMEOUT = 1
    private const val NETWORK = 2
    private const val SERVER = 4
    private const val CLIENT = 5
    private const val BUSY = 8
    private const val TOO_MANY_REQUESTS = 10
    private const val SERVER_DISCONNECTED = 11

    /**
     * [elapsedMs] = Zeit seit dem Start dieses Anlaufs, [retries] = bisherige stille Anläufe.
     * Hat der Lernende schon gesprochen oder selbst gestoppt, wird nie neu gestartet —
     * das entscheidet der Aufrufer vorher.
     */
    fun shouldRetry(error: Int, elapsedMs: Long, retries: Int): Boolean {
        if (retries >= MAX_RETRIES) return false
        return when (error) {
            // Der Dienst ist noch belegt, drosselt oder hat die Verbindung verloren.
            BUSY, TOO_MANY_REQUESTS, SERVER_DISCONNECTED -> true
            // »Netzwerk«/»Server«/»Client« sofort nach dem Start: Stolpern, kein echter Ausfall.
            NETWORK_TIMEOUT, NETWORK, SERVER, CLIENT -> elapsedMs < EARLY_MS
            else -> false
        }
    }

    /** Pause vor dem nächsten Anlauf — wächst, damit der Dienst Zeit zum Aufräumen bekommt. */
    fun delayMs(retries: Int): Long = 350L + 300L * retries
}
