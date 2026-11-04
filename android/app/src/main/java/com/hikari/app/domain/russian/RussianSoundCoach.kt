package com.hikari.app.domain.russian

/** Ein konkreter Aussprache-Hinweis zu einem verfehlten Wort. */
data class RuSoundHint(
    val target: String,
    /** Was die Spracherkennung stattdessen gehört hat (null = Wort fehlt ganz). */
    val heard: String?,
    val tip: String,
)

/**
 * Leitet aus dem Abgleich »Zielsatz ↔ Gehörtes« ab, WELCHER Laut vermutlich
 * danebenging. Die Erkennung gibt nur Text zurück — wenn dort statt »мы« ein
 * »ми« steht, wurde das ы zu hell gesprochen. Das ist eine Heuristik: Sie
 * nennt nur Laute mit klar bekannter Verwechslung und nie mehr als [max].
 */
object RuSoundCoach {

    /** (Soll, Gehört) → Tipp. */
    private val confusions: Map<Pair<Char, Char>, String> = buildMap {
        put('ы' to 'и', "»ы« klang wie »и«. Zieh die Zunge nach hinten und lass die Lippen locker — kein breites Lächeln.")
        put('и' to 'ы', "»и« klang zu dunkel (wie »ы«). Zunge nach vorn, Lippen leicht breit — wie in »Biene«.")
        put('ш' to 'с', "»ш« klang wie »с«. Zunge zurück, Lippen leicht gerundet — ein tiefes »sch«.")
        put('с' to 'ш', "»с« klang wie »ш«. Zunge bleibt vorn hinter den Zähnen, Lippen flach — glattes »s«.")
        put('ш' to 'щ', "»ш« klang zu weich (wie »щ«). Bei »ш« bleibt die Zungenmitte unten, der Laut ist tief und hart.")
        put('щ' to 'ш', "»щ« klang zu hart (wie »ш«). »щ« ist ein langes, weiches »sch« mit angehobener Zungenmitte.")
        put('ж' to 'ш', "»ж« klang stimmlos (wie »ш«). Lass die Stimmbänder mitschwingen — wie französisch »j« in »Journal«.")
        put('ш' to 'ж', "»ш« klang stimmhaft (wie »ж«). Hier bleibt die Stimme aus, nur Luft.")
        put('ж' to 'з', "»ж« klang wie »з«. Zunge zurück, Lippen leicht gerundet, stimmhaft zischen.")
        put('з' to 'ж', "»з« klang wie »ж«. Zunge bleibt vorn — ein weiches Summen wie »s« in »Sonne«.")
        put('ч' to 'ш', "»ч« klang wie »ш«. »ч« beginnt mit einem kurzen »t« und endet weich — ein kompaktes »tsch«.")
        put('ш' to 'ч', "»ш« klang wie »ч«. Kein »t« davor — nur ein durchgehendes »sch«.")
        put('ч' to 'ц', "»ч« klang wie »ц«. Bei »ч« liegt die Zungenmitte oben, es klingt weicher als »ts«.")
        put('ц' to 'ч', "»ц« klang wie »ч«. »ц« ist ein schnelles »ts« wie in »Zahl« — hart und kurz.")
        put('ц' to 'с', "»ц« klang wie »с«. Vorn ein kurzes »t« anschlagen: »ts«.")
        put('с' to 'ц', "»с« klang wie »ц«. Hier kein »t« davor — nur ein glattes »s«.")
        put('р' to 'л', "»р« klang wie »л«. Die Zungenspitze muss am Gaumen schwingen, nicht an den Zähnen stehen.")
        put('л' to 'р', "»л« klang wie »р«. Zunge fest gegen die Zähne drücken, nicht rollen.")
        put('х' to 'к', "»х« klang wie »к«. Nur Luft durch die Enge am hinteren Gaumen, kein Verschluss — wie »ch« in »Bach«.")
        put('г' to 'к', "»г« klang stimmlos (wie »к«). Stimme an, weich ansetzen.")
        put('д' to 'т', "»д« klang stimmlos (wie »т«). Stimmbänder schwingen lassen.")
        put('б' to 'п', "»б« klang stimmlos (wie »п«). Stimmbänder schwingen lassen.")
        put('в' to 'б', "»в« klang wie »б«. Lippen nicht schließen — Oberzähne an die Unterlippe.")
        put('я' to 'а', "»я« klang wie »а«. »я« enthält ein kurzes »j« — wie in »ja«.")
        put('ю' to 'у', "»ю« klang wie »у«. »ю« enthält ein kurzes »j« — wie in »Jule«.")
        put('е' to 'э', "»е« klang wie »э«. »е« beginnt mit einem »j«: »je«.")
        put('ё' to 'о', "»ё« klang wie »о«. »ё« beginnt mit einem »j«: »jo«.")
    }

    private const val SOFT_TIP =
        "Das weiche Zeichen »ь« fehlte. Hebe die Zungenmitte zum Gaumen — der Konsonant davor wird weich, wie ein kleines »j« danach."

    /**
     * Ein Hinweis je verfehltem Wort (höchstens [max]): bekannte Laut-
     * Verwechslung → gezielter Tipp; sonst was stattdessen ankam und wie es
     * richtig klingt (Umschrift, betonte Silbe groß); fehlt das Wort ganz,
     * wird genau das gesagt.
     */
    fun hints(sc: RuAnswerCheck.SpeechScore, max: Int = 3): List<RuSoundHint> {
        if (sc.heard.isBlank()) return emptyList()
        val heardWords = RuSpeechMatch.tokens(sc.heard).toMutableList()
        // Getroffene Wörter verbrauchen ihr Gegenstück — übrig bleiben die Kandidaten für Verfehltes.
        for ((w, ok) in sc.words) {
            if (!ok) continue
            val t = RuSpeechMatch.Tok(w)
            val hit = heardWords.indexOfFirst { RuSpeechMatch.matches(t, it) }
            if (hit >= 0) heardWords.removeAt(hit)
        }
        val out = mutableListOf<RuSoundHint>()
        val seen = mutableSetOf<String>()
        for ((word, ok) in sc.words) {
            if (ok || out.size >= max) continue
            val t = RuSpeechMatch.Tok(word)
            val cand = heardWords.maxByOrNull { RuSpeechMatch.soundSimilarity(t, it.word) }
            val close = cand != null && RuSpeechMatch.soundSimilarity(t, cand.word) >= 0.4
            val hint = if (close) {
                heardWords.remove(cand)
                val tip = tipFor(word, cand!!.word) ?: "Kam als anderes Wort an. Sprich es langsam und deutlich: " +
                    "${reading(word)} — die GROSSE Silbe ist betont, alle anderen Vokale kurz und schwach."
                RuSoundHint(word, cand.word, tip)
            } else {
                // Kurze Füllwörter verzeiht die Prüfung ohnehin — dafür keinen Hinweis.
                if (word.length < 3 && word !in setOf("не", "да")) continue
                RuSoundHint(word, null, "Dieses Wort war nicht zu hören. Nicht verschlucken — sprich es mit: ${reading(word)}.")
            }
            if (seen.add(hint.tip)) out += hint
        }
        return out
    }

    /** Umschrift mit großgeschriebener betonter Silbe, z. B. »djEssit'«. */
    internal fun reading(word: String): String =
        RussianPhonetics.transliterate(RussianGlossary.withStress(word))
            .joinToString("") { if (it.stressed) it.text.uppercase() else it.text }

    /** Erster erklärbarer Unterschied zwischen Soll und Gehörtem, sonst null. */
    internal fun tipFor(target: String, heard: String): String? {
        for ((t, h) in diff(target, heard)) {
            if (t == 'ь' && h == null) return SOFT_TIP
            if (t != null && h != null) confusions[t to h]?.let { return it }
        }
        return null
    }

    /** Edit-Skript (Soll-Zeichen?, Gehört-Zeichen?) der Unterschiede, in Wortreihenfolge. */
    internal fun diff(a: String, b: String): List<Pair<Char?, Char?>> {
        val n = a.length
        val m = b.length
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1,
            )
        }
        val ops = ArrayDeque<Pair<Char?, Char?>>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && a[i - 1] == b[j - 1] && dp[i][j] == dp[i - 1][j - 1] -> { i--; j-- }
                i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1 -> { ops.addFirst(a[i - 1] to b[j - 1]); i--; j-- }
                i > 0 && dp[i][j] == dp[i - 1][j] + 1 -> { ops.addFirst(a[i - 1] to null); i-- }
                else -> { ops.addFirst(null to b[j - 1]); j-- }
            }
        }
        return ops.toList()
    }
}
