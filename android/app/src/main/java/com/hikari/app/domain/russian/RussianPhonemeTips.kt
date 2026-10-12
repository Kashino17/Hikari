package com.hikari.app.domain.russian

/** Deutsche Aussprache-Tipps zu einer Phonem-Abweichung (IPA aus dem Server-Check). */
object RuPhonemeTips {

    private fun base(p: String): String = p.replace("͡", "").replace("ː", "").replace("ˈ", "").replace("ˌ", "")
    private fun soft(p: String): Boolean = p.endsWith("ʲ")
    private fun plain(p: String): String = p.removeSuffix("ʲ")

    private val sibilantsHard = setOf("ʂ", "ʐ")
    private val reducedVowels = setOf("ɐ", "ə", "ɪ", "ʌ", "ᵻ")
    private val fullVowels = setOf("o", "a", "e", "ɔ", "ɛ", "æ")

    /** @return Tipp oder null, wenn die Abweichung nicht sinnvoll erklärbar ist. */
    fun tip(ref: String, user: String?): String? {
        val r = base(ref)
        if (user == null) {
            return when {
                plain(r) in fullVowels || plain(r) in reducedVowels || plain(r) in setOf("i", "ɨ", "u") ->
                    "Ein Vokal fehlte. Im Russischen werden auch unbetonte Vokale gesprochen — nur schwächer und kürzer."
                else -> "Ein Laut fehlte. Sprich das Wort langsam, Laut für Laut."
            }
        }
        val u = base(user)
        return when {
            r == "ɨ" && plain(u) == "i" -> "»ы« klang wie »и«. Zunge nach hinten ziehen, Lippen locker — kein breites Lächeln."
            r == "i" && u == "ɨ" -> "»и« klang zu dunkel (wie »ы«). Zunge nach vorn, Lippen leicht breit."
            r in sibilantsHard && plain(u) in setOf("s", "z", "ɕ", "ʃ", "ʒ", "ʑ") ->
                "»ш/ж« sind tief und hart: Zunge zurück, Lippen leicht gerundet — nicht wie deutsches »s« oder »sch«."
            r == "ɕ" && u in sibilantsHard -> "»щ« ist ein langes, weiches »sch« mit angehobener Zungenmitte — nicht so hart wie »ш«."
            r == "ts" && plain(u) in setOf("s", "t", "tɕ", "tʃ") -> "»ц« ist ein schnelles »ts« — vorne ein kurzes »t« anschlagen."
            (r == "tɕ" || r == "tʃ") && (u in sibilantsHard || u == "ts") ->
                "»ч« beginnt mit »t« und endet weich — ein kompaktes »tsch«, Zungenmitte nach oben."
            plain(r) == "r" && plain(u) in setOf("l", "ɾ", "ɹ", "ʁ", "ʀ") ->
                "»р« wird mit der Zungenspitze am Gaumen gerollt — nicht hinten im Hals wie im Deutschen."
            plain(r) == "x" && plain(u) in setOf("k", "h", "ç") -> "»х« ist ein rauer Hauchlaut wie »ch« in »Bach« — ohne Verschluss."
            soft(r) && !soft(u) && plain(r) == plain(u) ->
                "Der Konsonant muss weich sein: Zungenmitte zum Gaumen heben, wie ein kleines »j« danach."
            !soft(r) && soft(u) && plain(r) == plain(u) ->
                "Der Konsonant muss hart sein: Zunge flach lassen, kein »j«-Nachklang."
            r in reducedVowels && plain(u) in fullVowels ->
                "Unbetontes »о« wird schwach und klingt wie »а« oder ein kurzes »ə« — nicht als klares »o« sprechen."
            plain(r) in setOf("o", "ɔ") && (u in reducedVowels || u == "a") ->
                "Betontes »о« klar und rund sprechen — nicht zu »а« verschlucken."
            else -> null
        }
    }

    fun hints(marks: List<Pair<String, String?>>, max: Int = 3): List<String> =
        marks.mapNotNull { (ref, user) -> tip(ref, user) }.distinct().take(max)
}
