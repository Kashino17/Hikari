package com.hikari.app.domain.russian

/**
 * Bausteine für einen fairen Abgleich »Zielsatz ↔ Spracherkennung«.
 *
 * Die Erkennung schreibt, was sie für das wahrscheinlichste Wort hält — nicht,
 * was du gesagt hast. Drei Dinge verfälschen dabei den Vergleich, ohne dass
 * deine Aussprache falsch war:
 *  1. Zahlen kommen als Ziffern zurück (»30«, »21-й«, »8:30«).
 *  2. Unbetontes о klingt wie а, unbetontes е/я wie и, ein д am Wortende wie т.
 *     Schreibt die Erkennung »эта« statt »это«, klang es trotzdem richtig.
 *  3. Namen kommen lateinisch (»Kadir«).
 * Alles andere bleibt streng: betonte Vokale, falsche Konsonanten, fehlende
 * Wörter.
 */
object RuSpeechMatch {

    /** Ein Wort des Ziel- oder Hörtextes. */
    data class Tok(
        val word: String,
        /** Index des betonten Vokals in [word], null = unbekannt. */
        val stress: Int? = null,
        /** Aus einer Ziffer entstanden (Erkennung) bzw. Zahlwort mit Wert. */
        val number: Int? = null,
    )

    private const val VOWELS = "аеиоуыэюя"

    // ── Zahlen ───────────────────────────────────────────────────────────────

    private val UNITS = listOf("ноль", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять")
    private val TEENS = listOf(
        "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать",
        "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать",
    )
    private val TENS = listOf("", "", "двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто")
    private val HUNDREDS = listOf("", "сто", "двести", "триста", "четыреста", "пятьсот", "шестьсот", "семьсот", "восемьсот", "девятьсот")

    private val ORD_UNITS = listOf("нулевой", "первый", "второй", "третий", "четвертый", "пятый", "шестой", "седьмой", "восьмой", "девятый")
    private val ORD_TEENS = listOf(
        "десятый", "одиннадцатый", "двенадцатый", "тринадцатый", "четырнадцатый",
        "пятнадцатый", "шестнадцатый", "семнадцатый", "восемнадцатый", "девятнадцатый",
    )
    private val ORD_TENS = listOf("", "", "двадцатый", "тридцатый", "сороковой", "пятидесятый", "шестидесятый", "семидесятый", "восьмидесятый", "девяностый")

    /** Gebeugte Formen der kleinen Zahlen, die sich nicht über den Wortanfang erkennen lassen. */
    private val OBLIQUE = mapOf(
        "один" to setOf("одна", "одно", "одного", "одному", "одной", "одну", "одним", "одном", "одни", "одних"),
        "два" to setOf("две", "двух", "двум", "двумя"),
        "три" to setOf("трех", "трем", "тремя"),
        "четыре" to setOf("четырех", "четырем", "четырьмя"),
        "сто" to setOf("ста"),
        "сорок" to setOf("сорока"),
        "девяносто" to setOf("девяноста"),
    )

    /** Grundzahl als Wörter (Nominativ), 0–9999. */
    fun cardinal(n: Int): List<String> {
        if (n < 0 || n > 9999) return emptyList()
        if (n == 0) return listOf(UNITS[0])
        val out = mutableListOf<String>()
        val th = n / 1000
        if (th > 0) {
            out += when (th) {
                1 -> listOf("тысяча")
                2 -> listOf("две", "тысячи")
                3, 4 -> listOf(UNITS[th], "тысячи")
                else -> listOf(UNITS[th], "тысяч")
            }
        }
        val rest = n % 1000
        if (rest / 100 > 0) out += HUNDREDS[rest / 100]
        val r = rest % 100
        when {
            r in 10..19 -> out += TEENS[r - 10]
            else -> {
                if (r / 10 > 0) out += TENS[r / 10]
                if (r % 10 > 0) out += UNITS[r % 10]
            }
        }
        return out
    }

    /** Ordnungszahl: alles wie [cardinal], nur das letzte Wort als Ordinal. */
    fun ordinal(n: Int): List<String> {
        val c = cardinal(n)
        if (c.isEmpty()) return c
        val r = n % 100
        val last = when {
            n % 1000 == 0 && n > 0 -> "тысячный"
            n % 100 == 0 && n % 1000 != 0 -> HUNDREDS[(n % 1000) / 100].let { if (it == "сто") "сотый" else it.dropLast(1) + "ый" }
            r in 10..19 -> ORD_TEENS[r - 10]
            r % 10 == 0 -> ORD_TENS[r / 10]
            else -> ORD_UNITS[r % 10]
        }
        return c.dropLast(1) + last
    }

    private val NUMBER_TOKEN = Regex("""(\d+)(?:[:.](\d+))?(?:-?([а-яё]+))?(%)?""")

    /** Ziffern-Token → Wörter (z. B. »21-й« → двадцать первый, »8:30« → восемь тридцать). */
    private fun expandNumber(m: MatchResult): List<Tok> {
        val a = m.groupValues[1].toIntOrNull() ?: return emptyList()
        val b = m.groupValues[2].toIntOrNull()
        val suffix = m.groupValues[3]
        val pct = m.groupValues[4].isNotEmpty()
        val first = if (suffix.isNotEmpty() && b == null) ordinal(a) else cardinal(a)
        val out = first.map { Tok(it, number = a) }.toMutableList()
        if (b != null) out += cardinal(b).map { Tok(it, number = b) }
        if (pct) out += Tok("процент")
        return out
    }

    /** Ist [word] (Zielwort) eine Form des Zahlworts [numberWord]? */
    fun sameNumeral(word: String, numberWord: String): Boolean {
        if (word == numberWord) return true
        if (OBLIQUE[numberWord]?.contains(word) == true) return true
        // пять/пяти/пятью/пятое/пятого: gleicher Stamm, nur die Endung anders.
        val stem = numberWord.trimEnd('ь', 'й', 'ы', 'о', 'а', 'и')
        return stem.length >= 3 && word.startsWith(stem.take(maxOf(3, stem.length - 1))) &&
            word.length - stem.length <= 3
    }

    // ── Zerlegen ─────────────────────────────────────────────────────────────

    private val TOKEN = Regex("""\d+(?:[:.]\d+)?(?:-?[а-яё]+)?%?|[a-zа-яё+]+""")

    /** Text (mit oder ohne "+"/Akut) → Wörter; Ziffern werden zu Zahlwörtern, ё zu е. */
    fun tokens(text: String): List<Tok> {
        val src = RussianPhonetics.fromDisplay(text).lowercase()
        val out = mutableListOf<Tok>()
        for (m in TOKEN.findAll(src)) {
            val v = m.value
            if (v[0].isDigit()) {
                NUMBER_TOKEN.matchEntire(v)?.let { out += expandNumber(it) }
                continue
            }
            var stress: Int? = null
            val sb = StringBuilder()
            for (c in v) {
                when (c) {
                    '+' -> stress = sb.length
                    'ё' -> { if (stress == null) stress = sb.length; sb.append('е') }
                    else -> sb.append(c)
                }
            }
            val w = sb.toString()
            if (w.isEmpty()) continue
            if (stress == null && w.count { it in VOWELS } == 1) stress = w.indexOfFirst { it in VOWELS }
            out += Tok(w, stress)
        }
        return out
    }

    // ── Lautbasierter Vergleich ──────────────────────────────────────────────

    private val VOICING = mapOf('б' to 'п', 'в' to 'ф', 'г' to 'к', 'д' to 'т', 'ж' to 'ш', 'з' to 'с')
    private val VOICELESS = "пфктшсхцчщ"
    private val VOICED_OBSTRUENT = "бгджз"

    /**
     * Klingen Soll-Buchstabe [t] (an Position [i] des Zielworts) und gehörter
     * Buchstabe [h] gleich? Nur in Stellungen, in denen Russisch sie wirklich
     * gleich ausspricht.
     */
    private fun soundsSame(target: Tok, i: Int, h: Char): Boolean {
        val w = target.word
        val t = w[i]
        if (t == h) return true
        val tv = t in VOWELS
        val hv = h in VOWELS
        if (tv && hv) {
            // Betonte Vokale müssen stimmen — dort hört man den Unterschied.
            if (target.stress == i) return false
            val a = setOf('о', 'а')
            val i2 = setOf('е', 'и', 'я')
            return (t in a && h in a) || (t in i2 && h in i2)
        }
        if (!tv && !hv) {
            val next = w.getOrNull(i + 1)
            val final = i == w.lastIndex || (next == 'ь' && i + 1 == w.lastIndex)
            val voiced = VOICING[t] == h || VOICING[h] == t
            if (!voiced) return false
            // Auslautverhärtung und Angleichung an den folgenden Konsonanten.
            return final || (next != null && (next in VOICELESS || next in VOICED_OBSTRUENT))
        }
        return false
    }

    /** 1.0 = klingt gleich, 0.0 = nichts gemeinsam. Gewichtete Levenshtein-Distanz. */
    fun soundSimilarity(target: Tok, heard: String): Double {
        val a = target.word.replace("ться", "тся")
        val t = if (a == target.word) target else target.copy(word = a)
        val b = heard.replace("ться", "тся")
        val n = a.length
        val m = b.length
        if (n == 0 && m == 0) return 1.0
        val dp = Array(n + 1) { DoubleArray(m + 1) }
        for (i in 0..n) dp[i][0] = i.toDouble()
        for (j in 0..m) dp[0][j] = j.toDouble()
        for (i in 1..n) for (j in 1..m) {
            val sub = if (soundsSame(t, i - 1, b[j - 1])) 0.0 else 1.0
            dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + sub)
        }
        return 1.0 - dp[n][m] / maxOf(n, m)
    }

    /** Lateinische Umschrift-Näherung für Namen, die die Erkennung lateinisch schreibt. */
    private val LATIN = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ж' to "zh", 'з' to "z",
        'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p",
        'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
    )

    fun latinOf(word: String): String = word.map { LATIN[it] ?: it.toString() }.joinToString("")

    const val WORD_PASS = 0.75

    private val NUMERAL_BASES: List<String> by lazy {
        (UNITS + TEENS + TENS + HUNDREDS + ORD_UNITS + ORD_TEENS + ORD_TENS + listOf("тысяча", "сотый", "тысячный"))
            .filter { it.isNotEmpty() }.sortedByDescending { it.length }
    }

    /** Grundform des Zahlworts, dessen Form [w] ist (тридцати → тридцать), sonst null. */
    fun numeralBase(w: String): String? = NUMERAL_BASES.firstOrNull { sameNumeral(w, it) }

    /** Trifft das gehörte Wort das Zielwort? */
    fun matches(target: Tok, heard: Tok): Boolean {
        // Aus Ziffern: nur dieselbe Zahl zählt (13 ist nicht 30).
        if (heard.number != null) return sameNumeral(target.word, heard.word)
        if (heard.word.any { it in 'a'..'z' }) {
            return RuAnswerCheck.similarity(latinOf(target.word), heard.word) >= WORD_PASS
        }
        // Zwei verschiedene Zahlwörter sind nie »ähnlich genug« (тринадцать ≠ тридцать).
        val tb = numeralBase(target.word)
        val hb = numeralBase(heard.word)
        if (tb != null && hb != null && tb != hb) return false
        if (soundSimilarity(target, heard.word) < WORD_PASS) return false
        // Der betonte Vokal muss stimmen — sonst klingt das Wort für Russen falsch.
        val st = target.stress
        if (st != null && target.word.length == heard.word.length && target.word[st] != heard.word[st]) return false
        return true
    }
}
