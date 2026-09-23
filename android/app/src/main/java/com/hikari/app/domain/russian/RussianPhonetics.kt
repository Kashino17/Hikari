package com.hikari.app.domain.russian

/**
 * Betonte Kurs-Texte ("Прив+ет") → Anzeige-Kyrillisch und eine Aussprache-
 * Umschrift für deutsche Muttersprachler.
 *
 * Die Umschrift schreibt, was man HÖRT, nicht was dasteht: unbetontes о wird
 * a, unbetontes е/я wird i, Auslautverhärtung und Stimmangleichung (вчера́ →
 * ftschirá), г als w in -ого/-его, что als schto. Buchstaben folgen der
 * deutschen Leselogik (в = w, ш = sch, ч = tsch, х = ch, ц = z, с vor Vokal
 * = ss). Die betonte Silbe kommt als eigener Abschnitt zurück, damit die UI
 * sie hervorheben kann.
 */
object RussianPhonetics {

    data class Span(val text: String, val stressed: Boolean)

    private const val ACUTE = '́'
    private const val VOWELS = "аеёиоуыэюя"

    /** Einsilber ohne eigene Betonung — sie lehnen sich an das Nachbarwort. */
    private val CLITICS = setOf(
        "в", "во", "на", "по", "за", "из", "с", "со", "к", "ко", "у", "о", "об", "от",
        "до", "без", "при", "и", "а", "но", "не", "ни", "же", "ли", "бы",
    )

    /** Präpositionen, die lautlich mit dem folgenden Wort verschmelzen. */
    private val PROCLITICS = setOf(
        "в", "во", "на", "по", "за", "из", "с", "со", "к", "ко", "у", "о", "об", "от",
        "до", "без", "при", "не", "ни",
    )

    /** Gesprochene Schreibung für Wörter, die gegen die Regeln laufen. */
    private val SPOKEN = mapOf(
        "что" to "што",
        "чтобы" to "шт+обы",
        "конечно" to "кон+ешно",
        "скучно" to "ск+ушно",
        "сегодня" to "сев+одня",
        "сейчас" to "сич+ас",
        "легко" to "лехк+о",
        "здравствуйте" to "здр+аствуйте",
        "здравствуй" to "здр+аствуй",
        "счастливо" to "щасл+иво",
        "пожалуйста" to "пож+алуйста",
        // Fremdwörter mit hartem Konsonanten vor е
        "кафе" to "каф+э",
        "аниме" to "аним+э",
    )

    /** -ого/-его ohne г→w (г gehört zum Stamm). */
    private val OGO_EXCEPTIONS = setOf("много", "немного", "дорого", "недорого", "строго", "убого")

    /** Anzeige: "+" vor dem Vokal → Akut nach dem Vokal, wie in Lehrbüchern. */
    fun display(src: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            if (c == '+' && i + 1 < src.length) {
                val v = src[i + 1]
                sb.append(v)
                if (v.lowercaseChar() != 'ё') sb.append(ACUTE)
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /** Ohne Betonungszeichen — für Spracherkennung und Vergleiche. */
    fun plain(src: String): String = src.replace("+", "").replace(ACUTE.toString(), "")

    /** Umschrift als einfacher String, betonter Vokal mit Akut (für Tests/Debug). */
    fun transliterateFlat(src: String): String =
        transliterate(src).joinToString("") { s -> if (s.stressed) accentFirstVowel(s.text) else s.text }

    private fun accentFirstVowel(s: String): String {
        val idx = s.indexOfFirst { it.lowercaseChar() in "aeiouy" }
        if (idx < 0) return s
        val accented = when (s[idx]) {
            'a' -> 'á'; 'e' -> 'é'; 'i' -> 'í'; 'o' -> 'ó'; 'u' -> 'ú'; 'y' -> 'ý'
            'A' -> 'Á'; 'E' -> 'É'; 'I' -> 'Í'; 'O' -> 'Ó'; 'U' -> 'Ú'; 'Y' -> 'Ý'
            else -> s[idx]
        }
        return s.substring(0, idx) + accented + s.substring(idx + 1)
    }

    // ── Tokenisierung ────────────────────────────────────────────────────────

    private sealed interface Token
    private data class Word(val raw: String) : Token
    private data class Sep(val text: String) : Token

    private fun isWordChar(c: Char) = c == '+' || c == '-' || c.lowercaseChar() in 'а'..'я' || c.lowercaseChar() == 'ё'

    private fun tokenize(src: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < src.length) {
            val start = i
            if (isWordChar(src[i]) && src[i] != '-') {
                while (i < src.length && isWordChar(src[i])) i++
                // Ein Bindestrich am Wortende gehört nicht zum Wort.
                var end = i
                while (end > start && src[end - 1] == '-') end--
                out += Word(src.substring(start, end))
                if (end < i) out += Sep(src.substring(end, i))
            } else {
                while (i < src.length && !(isWordChar(src[i]) && src[i] != '-')) i++
                out += Sep(src.substring(start, i))
            }
        }
        return out
    }

    // ── Laute ────────────────────────────────────────────────────────────────

    private enum class Cons(val latin: String, val voiced: Boolean = false, val obstruent: Boolean = true) {
        P("p"), F("f"), K("k"), T("t"), SH("sch"), S("s"),
        B("b", voiced = true), V("w", voiced = true), G("g", voiced = true),
        D("d", voiced = true), ZH("zh", voiced = true), Z("s", voiced = true),
        H("ch"), TS("z"), CH("tsch"), SHCH("sch"),
        L("l", obstruent = false), M("m", obstruent = false), N("n", obstruent = false),
        R("r", obstruent = false), J("j", obstruent = false);

        fun devoiced(): Cons = when (this) {
            B -> P; V -> F; G -> K; D -> T; ZH -> SH; Z -> S; else -> this
        }

        fun voicedForm(): Cons = when (this) {
            P -> B; F -> V; K -> G; T -> D; SH -> ZH; S -> Z; else -> this
        }
    }

    private val CONS_OF = mapOf(
        'б' to Cons.B, 'в' to Cons.V, 'г' to Cons.G, 'д' to Cons.D, 'ж' to Cons.ZH,
        'з' to Cons.Z, 'к' to Cons.K, 'л' to Cons.L, 'м' to Cons.M, 'н' to Cons.N,
        'п' to Cons.P, 'р' to Cons.R, 'с' to Cons.S, 'т' to Cons.T, 'ф' to Cons.F,
        'х' to Cons.H, 'ц' to Cons.TS, 'ч' to Cons.CH, 'ш' to Cons.SH, 'щ' to Cons.SHCH,
        'й' to Cons.J,
    )

    private sealed interface Unit
    private data class C(var cons: Cons, val upper: Boolean) : Unit
    private data class Vw(val text: String, val stressed: Boolean, val upper: Boolean) : Unit
    private data class Mark(val text: String) : Unit // ' für ь, - für Bindestrich

    /** Wort mit Betonungsinfo: Kleinbuchstaben ohne "+", Index des betonten Vokals. */
    private data class Parsed(val letters: String, val upperMask: List<Boolean>, val stressAt: Int?, val explicit: Boolean)

    private fun parseWord(raw: String): Parsed {
        val letters = StringBuilder()
        val upper = mutableListOf<Boolean>()
        var stress: Int? = null
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '+') {
                stress = letters.length
                i++
                continue
            }
            letters.append(c.lowercaseChar())
            upper += c.isUpperCase()
            i++
        }
        val s = letters.toString()
        val explicit = stress != null
        if (stress == null) {
            val yo = s.indexOf('ё')
            if (yo >= 0) stress = yo
        }
        return Parsed(s, upper, stress, explicit)
    }

    private fun vowelCount(s: String) = s.count { it in VOWELS }

    // ── Hauptfunktion ────────────────────────────────────────────────────────

    fun transliterate(src: String): List<Span> {
        val tokens = tokenize(src)
        // 1) Betonung je Wort bestimmen.
        data class W(val parsed: Parsed, val stressAt: Int?, val lower: String) {
            /** Einsilber ohne "+" haben keine Wahl — dort nichts hervorheben. */
            val highlight: Boolean get() = parsed.explicit || vowelCount(lower) >= 2
        }
        val words = mutableListOf<Pair<Int, W>>() // tokenIndex → Wort
        var afterStressedNe = false
        // Allein stehende Wörter (Karte "но") sind immer betont.
        val single = tokens.count { it is Word } == 1
        tokens.forEachIndexed { idx, t ->
            when (t) {
                is Sep -> if (t.text.any { it in ".,!?;:…" }) afterStressedNe = false
                is Word -> {
                    val p = parseWord(t.raw)
                    val lower = p.letters
                    val vc = vowelCount(lower.replace("-", ""))
                    var stress = p.stressAt
                    // что als Konjunktion ("потому что", ", что …") ist unbetont.
                    val prevWord = words.lastOrNull()?.second?.lower
                    val prevSep = (tokens.getOrNull(idx - 1) as? Sep)?.text.orEmpty()
                    val conjunction = lower == "что" && (prevWord == "потому" || ',' in prevSep)
                    if (stress == null && vc == 1 && (single || lower !in CLITICS) && !afterStressedNe && !conjunction) {
                        stress = lower.indexOfFirst { it in VOWELS }
                    }
                    if (p.explicit) afterStressedNe = lower == "не"
                    words += idx to W(p, stress, lower)
                }
            }
        }

        // 2) Gesprochene Schreibung einsetzen (Ausnahmen + -ого/-его).
        data class Spoken(val letters: String, val stressAt: Int?, val upper: Boolean, val highlight: Boolean)
        val spoken = words.map { (_, w) ->
            val key = w.lower
            val special = SPOKEN[key]
            val firstUpper = w.parsed.upperMask.firstOrNull() == true
            if (special != null) {
                val p = parseWord(special)
                val st = when {
                    p.stressAt != null -> if (w.stressAt == null) null else p.stressAt
                    else -> w.stressAt
                }
                Spoken(p.letters, st, firstUpper, w.highlight)
            } else if ((key.endsWith("ого") || key.endsWith("его")) && key.length >= 3 &&
                key !in OGO_EXCEPTIONS
            ) {
                val chars = key.toCharArray()
                chars[chars.size - 2] = 'в'
                Spoken(String(chars), w.stressAt, firstUpper, w.highlight)
            } else {
                Spoken(key, w.stressAt, firstUpper, w.highlight)
            }
        }

        // 3) Laute erzeugen; Proklitika verschmelzen mit dem nächsten Wort
        //    (Stimmangleichung über die Wortgrenze).
        val unitsPerWord = spoken.map { buildUnits(it.letters, it.stressAt, it.upper, it.highlight) }
        val groups = mutableListOf<MutableList<Int>>()
        var current = mutableListOf<Int>()
        for (i in spoken.indices) {
            current += i
            val w = words[i].second.lower
            val nextTokenIsSpace = run {
                val tokenIdx = words[i].first
                val next = tokens.getOrNull(tokenIdx + 1)
                next is Sep && next.text.isNotEmpty() && next.text.all { it == ' ' }
            }
            val attach = w in PROCLITICS && i + 1 < spoken.size && nextTokenIsSpace
            if (!attach) {
                groups += current
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) groups += current
        for (g in groups) assimilate(g.flatMap { unitsPerWord[it] })

        // 4) Rendern — Separatoren bleiben erhalten.
        val out = mutableListOf<Span>()
        val wordAt = words.mapIndexed { i, (tokenIdx, _) -> tokenIdx to i }.toMap()
        tokens.forEachIndexed { idx, t ->
            when (t) {
                is Sep -> out += Span(t.text, false)
                is Word -> render(unitsPerWord[wordAt.getValue(idx)], out)
            }
        }
        return merge(out)
    }

    private fun merge(spans: List<Span>): List<Span> {
        val out = mutableListOf<Span>()
        for (s in spans) {
            if (s.text.isEmpty()) continue
            val last = out.lastOrNull()
            if (last != null && !last.stressed && !s.stressed) {
                out[out.size - 1] = Span(last.text + s.text, false)
            } else {
                out += s
            }
        }
        return out
    }

    private fun buildUnits(word: String, stressAt: Int?, upperFirst: Boolean, highlight: Boolean): List<Unit> {
        val units = mutableListOf<Unit>()
        // Konsonant direkt davor (für weich/hart-Regeln).
        var prevCons: Cons? = null
        var prevWasVowelOrStart = true
        var i = 0
        val n = word.length
        // Einsilber (не, до …) reduzieren wie im Wortinneren.
        val multi = vowelCount(word) > 1
        fun isFinal(pos: Int): Boolean = multi && pos == n - 1
        while (i < n) {
            val ch = word[i]
            val upper = upperFirst && units.isEmpty()
            // -тся / -ться am Wortende → ц + а
            if (ch == 'т' && (word.endsWith("тся") && i == n - 3 || word.endsWith("ться") && i == n - 4)) {
                units += C(Cons.TS, upper)
                prevCons = Cons.TS
                prevWasVowelOrStart = false
                i = n - 1 // steht jetzt auf я
                continue
            }
            // сч / зч → щ
            if ((ch == 'с' || ch == 'з') && i + 1 < n && word[i + 1] == 'ч') {
                units += C(Cons.SHCH, upper)
                prevCons = Cons.SHCH
                prevWasVowelOrStart = false
                i += 2
                continue
            }
            val cons = CONS_OF[ch]
            if (cons != null) {
                // з/с vor ж/ш gleichen sich ganz an (приезжа́й → prijizhzháj).
                val next = word.getOrNull(i + 1)
                val c = when {
                    (cons == Cons.Z || cons == Cons.S) && next == 'ж' -> Cons.ZH
                    (cons == Cons.Z || cons == Cons.S) && next == 'ш' -> Cons.SH
                    else -> cons
                }
                units += C(c, upper)
                prevCons = cons
                prevWasVowelOrStart = cons == Cons.J
                i++
                continue
            }
            when (ch) {
                'ь' -> {
                    // Nach ж/ш/ч/щ ist ь nur Rechtschreibung.
                    if (prevCons !in HISSING) units += Mark("'")
                    prevWasVowelOrStart = true // folgender Vokal bekommt ein j
                    prevCons = null
                }
                'ъ' -> {
                    prevWasVowelOrStart = true
                    prevCons = null
                }
                '-' -> {
                    units += Mark("-")
                }
                else -> if (ch in VOWELS) {
                    val stressed = stressAt == i
                    units += Vw(vowel(ch, stressed, prevCons, prevWasVowelOrStart, isFinal(i)), stressed && highlight, upper)
                    prevCons = null
                    prevWasVowelOrStart = true
                } else {
                    units += Mark(ch.toString())
                }
            }
            i++
        }
        return units
    }

    private val HARD_ONLY = setOf(Cons.ZH, Cons.SH, Cons.TS)
    private val HISSING = setOf(Cons.ZH, Cons.SH, Cons.CH, Cons.SHCH)
    private val SOFT_ONLY = setOf(Cons.CH, Cons.SHCH)

    private fun vowel(ch: Char, stressed: Boolean, prev: Cons?, iotated: Boolean, final: Boolean): String {
        val hard = prev in HARD_ONLY
        val soft = prev in SOFT_ONLY
        // "iotated": Wortanfang, nach Vokal, nach ь/ъ oder nach й
        val afterJ = prev == Cons.J
        return when (ch) {
            'а' -> if (!stressed && soft) "i" else "a"
            'о' -> if (stressed) "o" else "a"
            'у' -> "u"
            'ы' -> "y"
            'э' -> "e"
            'и' -> if (hard) "y" else "i"
            'е' -> when {
                stressed -> if (hard || soft || afterJ) "e" else "je"
                final -> if (iotated && !afterJ) "je" else "e"
                hard -> "y"
                iotated && !afterJ -> "ji"
                else -> "i"
            }
            'ё' -> if (hard || soft || afterJ) "o" else "jo"
            'ю' -> if (hard || soft || afterJ) "u" else "ju"
            'я' -> when {
                stressed || final -> if (soft || hard || afterJ) "a" else "ja"
                iotated && !afterJ -> "ji"
                else -> "i"
            }
            else -> ch.toString()
        }
    }

    /** Stimmangleichung von rechts nach links + Auslautverhärtung. */
    private fun assimilate(units: List<Unit>) {
        // Nur Konsonanten und Vokale zählen; ' und - sind durchlässig.
        val seq = units.filter { it is C || it is Vw }
        for (i in seq.indices.reversed()) {
            val u = seq[i] as? C ?: continue
            if (!u.cons.obstruent) continue
            val next = seq.getOrNull(i + 1)
            when {
                next == null -> u.cons = u.cons.devoiced()
                next is C && next.cons.obstruent && !next.cons.voiced -> u.cons = u.cons.devoiced()
                next is C && next.cons.obstruent && next.cons.voiced && next.cons != Cons.V ->
                    u.cons = u.cons.voicedForm()
            }
        }
    }

    private fun render(units: List<Unit>, out: MutableList<Span>) {
        var prev: Unit? = null
        for ((idx, u) in units.withIndex()) {
            when (u) {
                is C -> {
                    val next = units.getOrNull(idx + 1)
                    var t = u.cons.latin
                    // Stimmloses s vor Vokal (am Wortanfang oder nach Vokal) als "ss",
                    // sonst liest man es deutsch weich.
                    if (u.cons == Cons.S && next is Vw && (prev == null || prev is Vw)) t = "ss"
                    if (u.upper) t = t.replaceFirstChar { it.uppercaseChar() }
                    out += Span(t, false)
                }
                is Vw -> {
                    val t = if (u.upper) u.text.replaceFirstChar { it.uppercaseChar() } else u.text
                    out += Span(t, u.stressed)
                }
                is Mark -> out += Span(u.text, false)
            }
            prev = u
        }
    }

    // ── Buchstaben-Lupe ──────────────────────────────────────────────────────

    /** Grundlaut jedes kyrillischen Buchstabens — zur Gewöhnung, nicht zum Pauken. */
    val LETTER_SOUNDS: Map<Char, String> = mapOf(
        'а' to "a", 'б' to "b", 'в' to "w", 'г' to "g", 'д' to "d", 'е' to "je", 'ё' to "jo",
        'ж' to "zh", 'з' to "s", 'и' to "i", 'й' to "j", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "ss", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "ch", 'ц' to "z", 'ч' to "tsch", 'ш' to "sch", 'щ' to "sch'",
        'ъ' to "–", 'ы' to "y", 'ь' to "'", 'э' to "e", 'ю' to "ju", 'я' to "ja",
    )

    /** Buchstabenpaare eines Textes (ohne Satzzeichen), Wortgrenzen als null. */
    fun letterPairs(src: String): List<Pair<Char, String>?> {
        val out = mutableListOf<Pair<Char, String>?>()
        for (c in plain(src)) {
            val sound = LETTER_SOUNDS[c.lowercaseChar()]
            when {
                sound != null -> out += c to sound
                c == ' ' || c == '-' -> if (out.isNotEmpty() && out.last() != null) out += null
            }
        }
        while (out.isNotEmpty() && out.last() == null) out.removeAt(out.size - 1)
        return out
    }
}
