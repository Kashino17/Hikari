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

    // ── Geduld: die Erkennung gibt auf, bevor der Lernende überhaupt angefangen hat ──────────

    /**
     * So lange nach dem Tippen darf man mit dem Sprechen anfangen, ohne neu tippen zu müssen.
     * Ist bis dahin kein Wort erkannt, schließt das Mikrofon und meldet es — es bleibt nie
     * länger stumm offen (manche Erkenner warten bei Stille sonst über 30 s).
     */
    const val PATIENCE_MS = 12_000L

    /** Obergrenze für stilles Neu-Öffnen in einer Wartezeit (Schutz vor einer Endlosschleife). */
    const val MAX_REOPEN = 8

    /** Kurze Pause, bevor das Mikrofon nach einer stillen Sitzung wieder aufgeht. */
    const val REOPEN_DELAY_MS = 150L

    /**
     * Ab diesem Spitzenpegel (onRmsChanged, etwa −2 … 10 dB) gilt: Da war eine Stimme. Bestimmt nur
     * den Wortlaut der Meldung am Ende der Wartezeit. Richtwert, am Handy noch nicht geeicht (im
     * Emulator lag Stille bei 0 bis 3,4) — der Pegel jeder Sitzung steht im Logcat.
     */
    const val HEARD_RMS_DB = 5f

    private const val SPEECH_TIMEOUT = 6
    private const val NO_MATCH = 7
    private const val LANGUAGE_NOT_SUPPORTED = 12
    private const val LANGUAGE_UNAVAILABLE = 13

    /** Die Erkennung übers Internet kam nicht durch (Zeitüberschreitung, Netz, Server). */
    fun isNetwork(error: Int): Boolean = error == NETWORK_TIMEOUT || error == NETWORK || error == SERVER

    /** Auf dem Gerät verlangt, aber das Sprachpaket für Russisch ist nicht installiert. */
    fun isPackMissing(error: Int): Boolean = error == LANGUAGE_NOT_SUPPORTED || error == LANGUAGE_UNAVAILABLE

    /**
     * Welcher Erkenner als Nächstes zuhört, nachdem eine Sitzung ohne Text endete: true = der auf
     * dem Gerät, false = der übers Internet. Ohne Sprachpaket bleibt nur das Internet. Hing das
     * Netz oder gibt es gerade keins ([online] false), bleibt es beim Gerät. Hat einer nichts
     * verstanden, darf es der andere versuchen — am Handy lieferte der Internet-Erkenner für
     * ein lautes, klares Wort fünfmal nichts.
     */
    fun nextOnDevice(wasOnDevice: Boolean, packInstalled: Boolean, networkError: Boolean, online: Boolean = true): Boolean = when {
        !packInstalled -> false
        networkError || !online -> true
        else -> !wasOnDevice
    }

    /** War eine Stimme zu hören? [peakRmsDb] = lautester gemeldeter Pegel. */
    fun heard(peakRmsDb: Float): Boolean = peakRmsDb >= HEARD_RMS_DB

    /**
     * Eine Sitzung endete ohne Text, ohne dass der Lernende selbst gestoppt hat: nichts
     * verstanden ([error] 6 oder 7; ein leeres Ergebnis zählt als 7) oder das Netz hing (1, 2, 4).
     * true = Mikrofon still wieder öffnen und weiter zuhören: Er liest oder denkt noch — typisch,
     * wenn das Mikrofon von selbst aufgeht —, wurde nicht verstanden und sagt es einfach noch
     * einmal, oder die Verbindung hakte kurz. Erst nach der Wartezeit wird gemeldet.
     * [sinceTapMs] zählt ab dem Tippen, [reopened] die bisherigen Neu-Öffnungen.
     */
    fun shouldKeepWaiting(error: Int, sinceTapMs: Long, reopened: Int): Boolean {
        if (error != SPEECH_TIMEOUT && error != NO_MATCH && !isNetwork(error)) return false
        return sinceTapMs < PATIENCE_MS && reopened < MAX_REOPEN
    }
}
