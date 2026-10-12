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

    fun hints(sc: RuAnswerCheck.SpeechScore, max: Int = 2): List<RuSoundHint> {
        if (sc.heard.isBlank()) return emptyList()
        val targets = sc.words
        val heardWords = RuLessonBuilder.norm(sc.heard).split(' ').filter { it.isNotBlank() }.toMutableList()
        // Getroffene Wörter verbrauchen ihr Gegenstück — übrig bleiben die Kandidaten für Verfehltes.
        for ((w, ok) in targets) {
            if (!ok) continue
            val hit = heardWords.indexOfFirst { RuAnswerCheck.similarity(it, w) >= 0.75 }
            if (hit >= 0) heardWords.removeAt(hit)
        }
        val out = mutableListOf<RuSoundHint>()
        val seen = mutableSetOf<String>()
        for ((word, ok) in targets) {
            if (ok || out.size >= max) continue
            val cand = heardWords.maxByOrNull { RuAnswerCheck.similarity(it, word) } ?: continue
            if (RuAnswerCheck.similarity(cand, word) < 0.4) continue
            heardWords.remove(cand)
            val tip = tipFor(word, cand) ?: continue
            if (seen.add(tip)) out += RuSoundHint(word, cand, tip)
        }
        return out
    }

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
