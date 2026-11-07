package com.hikari.app.domain.repo

/** Ein Instrument als Einstieg in den Instrumental-Modus. */
data class Instrument(
    /** Stabiler Schlüssel — die UI hängt Farbe und Icon daran. */
    val id: String,
    val title: String,
    /** Suchbegriff hinter der Kategorie (läuft durch den Instrumental-Filter). */
    val query: String,
)

/**
 * Inhalte des Instrumental-Modus. Die Reihenfolge der Instrumente ist die
 * Anzeigereihenfolge — Piano steht bewusst ganz oben und bekommt im Feed
 * zusätzlich die feste Sektion "Piano für dich".
 */
object InstrumentalCatalog {
    const val PIANO_FOCUS_TITLE = "Piano für dich"

    /** So viele Mix-Instrumentals zeigt der Feed pro Aufbau (Rest rotiert). */
    const val MIXES_PER_LOAD = 6

    val INSTRUMENTS = listOf(
        Instrument("piano", "Piano", "relaxing piano instrumental"),
        Instrument("violin", "Violine", "violin instrumental music"),
        Instrument("guitar", "Gitarre", "acoustic guitar instrumental fingerstyle"),
        Instrument("sax", "Saxophon", "saxophone instrumental smooth"),
        Instrument("cello", "Cello", "cello instrumental music"),
        Instrument("harp", "Harfe", "harp instrumental relaxing"),
        Instrument("flute", "Flöte", "flute instrumental relaxing"),
        Instrument("orchestra", "Orchester", "orchestral instrumental epic"),
        Instrument("koto", "Japanische Klänge", "japanese koto instrumental"),
        Instrument("synth", "Synthesizer", "synthwave instrumental"),
        Instrument("lofi", "Lo-Fi Beats", "lofi hip hop instrumental beats"),
        Instrument("jazz", "Jazz-Trio", "jazz piano trio instrumental"),
    )

    /** Mix-Instrumentals: Instrument-Kombinationen und Stimmungen. */
    val MIXES = listOf(
        "Piano & Violine" to "piano and violin instrumental",
        "Piano zum Einschlafen" to "sleep piano music instrumental",
        "Anime am Klavier" to "anime piano instrumental",
        "Filmmusik am Klavier" to "movie soundtrack piano cover",
        "Saxophon-Jazz" to "smooth jazz saxophone instrumental",
        "Cinematic Strings" to "cinematic strings instrumental",
        "Gitarre am Abend" to "relaxing acoustic guitar instrumental evening",
        "Klassik zum Fokussieren" to "classical music for studying instrumental",
        "Lofi Piano" to "lofi piano instrumental beats",
        "Cello & Piano" to "cello and piano instrumental",
        "Pop-Hits am Klavier" to "pop songs piano cover instrumental",
        "Game Soundtracks" to "video game music orchestral instrumental",
        "Morgens mit Piano" to "morning piano instrumental",
        "Regen & Klavier" to "piano music with rain sounds",
        "Epische Orchester" to "epic orchestral instrumental music",
        "Violinen-Covers" to "violin cover pop songs instrumental",
        "Harfe & Flöte" to "harp and flute instrumental",
        "Jazz-Café" to "jazz cafe instrumental piano",
    )
}
