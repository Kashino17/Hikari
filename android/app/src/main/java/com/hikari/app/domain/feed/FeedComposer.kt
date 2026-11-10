package com.hikari.app.domain.feed

import com.hikari.app.domain.russian.RuCard
import com.hikari.app.domain.russian.RuCourseIndex
import com.hikari.app.domain.russian.RuDay
import com.hikari.app.domain.russian.RuDeepEntry
import com.hikari.app.domain.russian.RuPhrase
import com.hikari.app.domain.russian.RuProgress
import com.hikari.app.domain.russian.RuSrs
import com.hikari.app.domain.russian.RussianGlossary
import com.hikari.app.domain.russian.RussianPhonetics
import kotlin.random.Random

/** Ein Platz im Feed: eine Wissenskarte, ein Mini-Spiel oder die Tagesziel-Marke. */
sealed interface FeedEntry {
    val id: String
    val module: MindfulModuleType?

    data class Card(val card: MindfulCard) : FeedEntry {
        override val id get() = card.id
        override val module get() = card.type
    }

    data class Game(val game: FeedGame) : FeedEntry {
        override val id get() = game.id
        override val module get() = game.module
    }

    /** Erscheint einmal am Tag, sobald das Tagesziel geschafft ist — danach geht der Feed weiter. */
    data class Milestone(override val id: String) : FeedEntry {
        override val module: MindfulModuleType? get() = null
    }
}

/** Was der Feed über den Russisch-Kurs wissen muss, um passende Übungen zu bauen. */
class RussianFeedContext(
    val index: RuCourseIndex,
    val progress: RuProgress,
    val today: Long,
    val alphabet: RuAlphabet,
    val letterProgress: Map<String, Int>,
    /** Vertiefungen des Kurses nach Satz-ID. */
    val deepdive: Map<String, RuDeepEntry> = emptyMap(),
    /** Geschätztes Sprachniveau (A0 … B2), wie es die Statistik des Russisch-Bereichs zeigt. */
    val level: String = "A0",
    /** Löst eine Satz-ID auf — Sprechkurs und Podcast-Kurs; Standard: nur der Sprechkurs. */
    val resolve: (String) -> RuPhrase? = { index.phrase(it, progress.settings) },
) {
    /** Sitzen fast alle Buchstaben, braucht es keine Umschrift mehr als Lesehilfe. */
    val readsCyrillic: Boolean get() = RuAlphabetLogic.mastered(letterProgress) >= RuAlphabetLogic.READS_CYRILLIC
}

/** Die Arten, einen gelernten Satz abzufragen — von leicht (erkennen) bis schwer (selbst bilden). */
enum class RuExerciseKind { MEANING, LISTEN, STRESS, RECALL, SPELL, GAP, BUILD }

/** Spielarten im Bereich Gehirnjogging. */
enum class BrainGameKind { SERIES, MEMORY_GRID, STROOP, ODD_ONE_OUT, DIGIT_SPAN, SEQUENCE, ANAGRAM, DOTS, SYLLOGISM, TIME }

/** Spielarten im Bereich Kopfrechnen. */
enum class MathGameKind { CHAIN, PERCENT, COMPARE }

/** Wie eine Antwort im Feed auf den Wiederholungsplan des Kurses wirkt (pur, testbar). */
object RuFeedGrading {
    /** Punkte für eine richtige Antwort: fällige Wiederholungen zählen mehr als Zusatzübungen. */
    fun xp(due: Boolean, correct: Boolean): Int = when {
        !correct -> 0
        due -> 10
        else -> 4
    }

    /**
     * Zählt die Antwort für den Wiederholungsplan? Fällige Sätze immer. Noch nicht fällige
     * nur bei einem Fehler — richtig geratene Zusatzübungen sollen den Abstand nicht aufblähen.
     * Sätze, die im Kurs noch gar nicht dran waren, nie.
     */
    fun counts(card: RuCard?, due: Boolean, correct: Boolean): Boolean =
        card != null && (due || !correct)

    /** Ein Satz, der dem Lernenden sagt, was seine Antwort bewirkt hat. */
    fun message(card: RuCard?, due: Boolean, correct: Boolean, today: Long): String {
        if (card == null) return if (correct) "Richtig. Der Satz kommt erst mit seiner Lektion in deine Wiederholungen." else "Noch neu für dich. Er kommt mit seiner Lektion dran."
        if (!correct) return "Kommt heute noch einmal dran."
        if (!due) {
            val days = (card.due - today).coerceAtLeast(1)
            return "Sitzt. War noch nicht fällig, nächster Termin bleibt ${inDays(days)}."
        }
        val box = (card.box + 1).coerceAtMost(RuSrs.MAX_BOX)
        return "Sitzt. Nächste Wiederholung ${inDays(RuSrs.INTERVALS[box].toLong())}."
    }

    private fun inDays(days: Long) = if (days == 1L) "morgen" else "in $days Tagen"
}

/**
 * Stellt den Feed zusammen. Jeder Aufruf von [batch] liefert einen neuen Schwung Einträge;
 * der Feed hat damit kein Ende. Wissenskarten wandern über [FeedRotation] durch den ganzen
 * Vorrat, Spiele werden frisch erzeugt.
 */
class FeedComposer(
    private val content: (MindfulModuleType) -> List<MindfulCard>,
    private val rng: Random = Random.Default,
) {
    /** In dieser Sitzung schon gezeigt — kommt nicht noch einmal, auch wenn der Vorrat klein ist. */
    private val used = mutableSetOf<String>()
    private val usedPhrases = mutableSetOf<String>()
    private var russianTurn = 0
    private var puzzleTurn = rng.nextInt(2)
    private var mathTurn = rng.nextInt(2)
    private var brainGameTurn = 0

    fun batch(
        enabled: Set<MindfulModuleType>,
        ranks: Map<MindfulModuleType, ModuleRank>,
        language: LearningLanguage,
        lastSeen: Map<String, Long>,
        russian: RussianFeedContext?,
        memoryLevel: Int,
        legacyLanguageCards: List<MindfulCard> = emptyList(),
        /** Überwischte Karten (ID → Runde) und die Nummer dieser Runde. */
        skipped: Map<String, Int> = emptyMap(),
        round: Int = 0,
        /** Stufen der Spiele mit steigender Schwierigkeit. */
        levels: Map<String, Int> = emptyMap(),
    ): List<FeedEntry> {
        if (enabled.isEmpty()) return emptyList()
        this.skipped = skipped
        this.round = round
        this.levels = levels + ("memory" to memoryLevel)
        val order = moduleOrder(enabled, ranks, rng)
        val out = mutableListOf<FeedEntry>()
        var breathworkDone = false
        for (module in order) {
            val rank = ranks[module] ?: ModuleRank.RANK_1
            val entry: FeedEntry? = when (module) {
                MindfulModuleType.LANGUAGE ->
                    if (language == LearningLanguage.RUSSIAN && russian != null) nextRussian(russian, rank)
                    else card(legacyLanguageCards, lastSeen, rank)
                MindfulModuleType.BRAIN_PUZZLE ->
                    if (puzzleTurn++ % 2 == 0) card(content(module), lastSeen, rank) ?: brainGame(russian)
                    else brainGame(russian)
                MindfulModuleType.SPEED_MATH ->
                    if (mathTurn++ % 2 == 0) card(content(module), lastSeen, rank) ?: mathGame()
                    else mathGame()
                MindfulModuleType.BREATHWORK ->
                    // Eine Atemübung pro Schwung reicht, egal wie hoch der Bereich gewichtet ist.
                    if (breathworkDone) null else card(content(module), lastSeen, rank).also { breathworkDone = true }
                else -> card(content(module), lastSeen, rank)
            }
            if (entry != null) out += entry
        }
        return spread(out).also { lastModule = it.lastOrNull()?.module ?: lastModule }
    }

    private var lastModule: MindfulModuleType? = null

    /**
     * Fällt ein Bereich aus (Vorrat leer), rücken seine Nachbarn zusammen — dann könnten zwei
     * Einträge desselben Bereichs aufeinander folgen. Hier wird das wieder auseinandergezogen,
     * auch über die Grenze zum vorigen Schwung hinweg.
     */
    private fun spread(list: List<FeedEntry>): List<FeedEntry> {
        val out = list.toMutableList()
        for (i in out.indices) {
            val prev = if (i == 0) lastModule else out[i - 1].module
            if (out[i].module != prev) continue
            val j = (i + 1 until out.size).firstOrNull { out[it].module != prev } ?: continue
            out.add(i, out.removeAt(j))
        }
        return out
    }

    private var skipped: Map<String, Int> = emptyMap()
    private var round = 0
    private var levels: Map<String, Int> = emptyMap()

    /**
     * Die nächste Karte eines Bereichs, mit dem Rang des Bereichs versehen. Zuerst kommen überwischte
     * Karten, deren Pause um ist; sonst die Rotation. Frisch überwischte setzen so lange aus.
     */
    private fun card(pool: List<MindfulCard>, lastSeen: Map<String, Long>, rank: ModuleRank): FeedEntry? {
        val ids = pool.map { it.id }
        val id = FeedSkips.due(ids, skipped, round).filter { it !in used }.randomOrNull(rng)
            ?: FeedRotation.pick(ids, lastSeen, 1, rng, exclude = used + skipped.keys).firstOrNull()
            ?: FeedRotation.pick(ids, lastSeen, 1, rng, exclude = used).firstOrNull()
            ?: return null
        used += id
        val card = pool.first { it.id == id }
        return FeedEntry.Card(card.withRank(rank))
    }

    /** Die Spielarten in einer pro Sitzung neu gewürfelten Reihenfolge — erst wenn alle dran waren, geht es von vorn los. */
    private val brainKinds = BrainGameKind.entries.shuffled(rng)
    private val mathKinds = MathGameKind.entries.shuffled(rng)

    private fun brainGame(russian: RussianFeedContext?): FeedEntry = FeedEntry.Game(
        when (brainKinds[brainGameTurn++ % brainKinds.size]) {
            BrainGameKind.SERIES -> FeedGameFactory.numberSeries(rng)
            BrainGameKind.MEMORY_GRID -> FeedGameFactory.memoryGrid(rng, levels["memory"] ?: 4)
            BrainGameKind.STROOP -> FeedGameFactory.stroop(rng)
            BrainGameKind.ODD_ONE_OUT -> FeedGameFactory.oddOneOut(rng, russian?.alphabet)
            BrainGameKind.DIGIT_SPAN -> FeedGameFactory.digitSpan(rng, levels["digits"] ?: FeedGameFactory.DIGITS_START)
            BrainGameKind.SEQUENCE -> FeedGameFactory.sequence(rng, levels["sequence"] ?: FeedGameFactory.SEQUENCE_START)
            BrainGameKind.ANAGRAM -> FeedGameFactory.anagram(rng, usedWords)
            BrainGameKind.DOTS -> FeedGameFactory.dots(rng)
            BrainGameKind.SYLLOGISM -> FeedGameFactory.syllogism(rng)
            BrainGameKind.TIME -> FeedGameFactory.timePuzzle(rng)
        },
    )

    private val usedWords = mutableSetOf<String>()

    private fun mathGame(): FeedEntry = FeedEntry.Game(
        when (mathKinds[mathTurn++ % mathKinds.size]) {
            MathGameKind.CHAIN -> FeedGameFactory.chainMath(rng, stepCount = 4 + rng.nextInt(2))
            MathGameKind.PERCENT -> FeedGameFactory.percent(rng)
            MathGameKind.COMPARE -> FeedGameFactory.compare(rng)
        },
    )

    // ── Russisch ────────────────────────────────────────────────────────────

    private enum class RuKind { ALPHABET, EXERCISE, NEW_PHRASE, PAIRS }

    /** Reihenfolge der Russisch-Einträge: Übungen wechseln sich mit Alphabet, neuen Sätzen und Paaren ab. */
    private val russianCycle = listOf(
        RuKind.ALPHABET, RuKind.EXERCISE, RuKind.NEW_PHRASE, RuKind.EXERCISE,
        RuKind.ALPHABET, RuKind.EXERCISE, RuKind.PAIRS, RuKind.EXERCISE,
    )
    private var exerciseTurn = 0
    private var alphabetSkip = 0

    private fun nextRussian(ctx: RussianFeedContext, rank: ModuleRank): FeedEntry? {
        // Passt eine Art gerade nicht (z. B. noch nichts gelernt), rückt die nächste nach.
        repeat(russianCycle.size) {
            var kind = russianCycle[russianTurn++ % russianCycle.size]
            // Wer das Alphabet beherrscht, bekommt es nur noch gelegentlich zur Auffrischung.
            if (kind == RuKind.ALPHABET && ctx.readsCyrillic && alphabetSkip++ % 3 != 0) kind = RuKind.EXERCISE
            val entry = when (kind) {
                RuKind.ALPHABET -> alphabet(ctx)
                RuKind.NEW_PHRASE -> newPhrase(ctx, rank)
                RuKind.PAIRS -> pairs(ctx)
                RuKind.EXERCISE -> exercise(ctx)
            }
            if (entry != null) return entry
        }
        return null
    }

    private fun settings(ctx: RussianFeedContext) = ctx.progress.settings

    /** Gelernte Sätze (stehen im Wiederholungsplan), aufgelöst in der Form des Lernenden. */
    private fun learned(ctx: RussianFeedContext): List<RuPhrase> =
        ctx.progress.cards.keys.mapNotNull { ctx.resolve(it) }

    /** Fällige Sätze zuerst, danach die wackligsten — jeder höchstens einmal pro Sitzung. */
    private fun nextPhrase(ctx: RussianFeedContext): Pair<RuPhrase, Boolean>? {
        val due = RuSrs.dueIds(ctx.progress.cards, ctx.today)
            .filter { it !in usedPhrases }
            .firstNotNullOfOrNull { ctx.resolve(it) }
        if (due != null) return due to true
        val practice = ctx.progress.cards.entries
            .filter { it.key !in usedPhrases }
            .shuffled(rng)
            .sortedBy { it.value.box }
            .firstNotNullOfOrNull { e -> ctx.resolve(e.key) }
        return practice?.let { it to false }
    }

    /**
     * Eine Übung zu einem gelernten Satz. Wie gefragt wird, hängt davon ab, wie gut der Satz
     * schon sitzt (siehe [exerciseKinds]) — neue Sätze erkennt man, sichere bildet man selbst.
     */
    private fun exercise(ctx: RussianFeedContext): FeedEntry? {
        val pool = learned(ctx)
        if (pool.size < 4) return null
        val (phrase, due) = nextPhrase(ctx) ?: return null
        val box = ctx.progress.cards[phrase.id]?.box ?: 1
        val kinds = exerciseKinds(box, phrase.wordCount)
        val origin = ctx.index.dayOfItem[phrase.id]?.let { "Lektion $it" } ?: "Podcast-Kurs"
        val context = "$origin  ·  Niveau ${ctx.level}"
        // Geht eine Art für diesen Satz nicht auf (zu wenige Ablenker), kommt die nächste dran.
        val start = exerciseTurn++
        val game = kinds.indices.firstNotNullOfOrNull { i ->
            when (kinds[(start + i) % kinds.size]) {
                RuExerciseKind.MEANING -> FeedGameFactory.meaning(phrase, pool, due, rng)?.copy(context = context)
                RuExerciseKind.LISTEN -> FeedGameFactory.listen(phrase, pool, due, rng)?.copy(context = context)
                RuExerciseKind.RECALL -> FeedGameFactory.recall(phrase, pool, due, rng)?.copy(context = context)
                RuExerciseKind.BUILD -> FeedGameFactory.build(phrase, pool, due, rng)?.copy(context = context)
                RuExerciseKind.STRESS -> FeedGameFactory.stress(phrase, due, rng)?.copy(context = context)
                RuExerciseKind.SPELL -> FeedGameFactory.spell(phrase, pool, due, rng)?.copy(context = context)
                RuExerciseKind.GAP -> FeedGameFactory.gap(phrase, pool, due, rng)?.copy(context = context)
            }
        } ?: return null
        usedPhrases += phrase.id
        return FeedEntry.Game(game)
    }

    private fun pairs(ctx: RussianFeedContext): FeedEntry? =
        FeedGameFactory.phrasePairs(learned(ctx), rng)?.let { FeedEntry.Game(it) }

    private var introducedThisSession = 0

    private fun alphabet(ctx: RussianFeedContext): FeedEntry? {
        val alphabet = ctx.alphabet
        if (alphabet.letters.isEmpty()) return null
        // Der Lernstand der Sitzung läuft dem gespeicherten voraus: vorgestellte Buchstaben gelten sofort.
        val progress = ctx.letterProgress + sessionLetters.associateWith { (ctx.letterProgress[it] ?: 0).coerceAtLeast(1) }
        val known = RuAlphabetLogic.weakest(alphabet, progress)
        val next = RuAlphabetLogic.nextLetter(alphabet, progress)
        val fresh = known.count { (progress[it.l] ?: 0) <= 1 }
        // Neuer Buchstabe erst, wenn höchstens einer der bisherigen noch ungeübt ist.
        if (next != null && (known.isEmpty() || fresh <= 1) && introducedThisSession < MAX_NEW_LETTERS) {
            introducedThisSession += 1
            sessionLetters += next.l
            return FeedEntry.Game(FeedGameFactory.letterIntro(alphabet, next, rng))
        }
        if (known.isEmpty()) return null
        val target = known.filter { it.hasSound }.take(4).randomOrNull(rng) ?: known.first()
        val readable = RuAlphabetLogic.readable(alphabet, progress).filter { "read:${it.ru}" !in used }
        val game: FeedGame? = when (alphabetTurn++ % 4) {
            0 -> FeedGameFactory.letterSound(alphabet, target, rng)
            1 -> readable.randomOrNull(rng)?.let { w ->
                used += "read:${w.ru}"
                FeedGameFactory.readWord(w, alphabet.reading + alphabet.letters.flatMap { it.words }, rng)
            }
            2 -> FeedGameFactory.soundLetter(alphabet, target, rng)
            else -> FeedGameFactory.letterPairs(known, rng)
        } ?: FeedGameFactory.letterSound(alphabet, target, rng).takeIf { target.hasSound }
        return game?.let { FeedEntry.Game(it) }
    }

    private val sessionLetters = mutableSetOf<String>()
    private var alphabetTurn = 0

    /**
     * Ein Satz aus der nächsten Lektion zum Anhören und Nachsprechen — die ausführliche Karte
     * mit Aussprache-Prüfung. Ist die Lektion schon durch, kommt ein gelernter Satz zum Sprechen.
     */
    private fun newPhrase(ctx: RussianFeedContext, rank: ModuleRank): FeedEntry? {
        val s = settings(ctx)
        val unlocked = ctx.progress.unlockedDay(ctx.index.days.size)
        val day = ctx.index.day(unlocked) ?: ctx.index.days.firstOrNull() ?: return null
        val fresh = ctx.index.dayPhrases(day, s).filter { it.id !in ctx.progress.cards && it.id !in usedPhrases }
        val phrase = fresh.randomOrNull(rng)
            ?: learned(ctx).filter { it.id !in usedPhrases }.randomOrNull(rng)
            ?: return null
        usedPhrases += phrase.id
        val isNew = phrase.id !in ctx.progress.cards
        val origin = ctx.index.day(phrase.day) ?: day
        return FeedEntry.Card(languageCard(ctx, phrase, origin, isNew, rank))
    }

    /** Baut die Satz-Karte zu einer Satz-ID neu auf — für gemerkte Karten nach einem Neustart. */
    fun languageCardFor(ctx: RussianFeedContext, phraseId: String, rank: ModuleRank = ModuleRank.RANK_3): LanguageCardItem? {
        val p = ctx.resolve(phraseId) ?: return null
        val day = ctx.index.day(p.day) ?: ctx.index.days.firstOrNull() ?: return null
        return languageCard(ctx, p, day, isNew = phraseId !in ctx.progress.cards, rank = rank)
    }

    private fun languageCard(ctx: RussianFeedContext, p: RuPhrase, day: RuDay, isNew: Boolean, rank: ModuleRank): LanguageCardItem {
        val s = settings(ctx)
        // Mini-Dialog: Was antwortest du? Nur wenn der Kurs passende Antworten kennt.
        val item = ctx.index.itemById[p.id]
        val replies = item?.replies.orEmpty().mapNotNull { ctx.index.phrase(it, s) }
        val right = replies.randomOrNull(rng)
        val wrong = if (right == null) emptyList() else ctx.index.phrasesUpTo(day.day, s)
            .filter { c -> c.id != p.id && replies.none { it.id == c.id } && c.day != p.day && !c.plain.trim().endsWith("?") }
            .distinctBy { it.de }
            .shuffled(rng)
            .take(2)
        val options = if (right != null && wrong.size == 2) (wrong + right).shuffled(rng) else emptyList()
        return LanguageCardItem(
            id = "ru:${if (isNew) "new" else "speak"}:${p.id}",
            language = LearningLanguage.RUSSIAN,
            foreignWord = p.display,
            nativeTranslation = p.de,
            phonetic = RussianPhonetics.transliterateFlat(p.ru),
            exampleForeign = p.display,
            exampleTranslation = p.de,
            dialogueScenario = if (isNew) "Neu aus Lektion ${day.day}" else "Nachsprechen",
            dialoguePartner = day.dialog.partner,
            dialoguePrompt = if (options.isNotEmpty()) "${day.dialog.partner}: »${p.display}« Was antwortest du?" else "",
            dialogueReplies = options.map { it.display },
            correctReplyIndex = options.indexOf(right).coerceAtLeast(0),
            rank = rank,
            audioRes = p.audio,
            dayNumber = day.day,
            dayTitle = day.title,
            isDueReview = false,
            currentStreak = ctx.progress.streak,
            totalXp = ctx.progress.xp,
            phraseId = p.id,
            note = p.note,
            literalTranslation = p.lit,
            isNewPhrase = isNew,
            deepRoute = if (p.id in ctx.deepdive) "russian-deep/${p.id}" else null,
            deep = russianDeep(ctx, p, day),
        )
    }

    /** Vertiefung aus dem Kursmaterial: Varianten, Beispiele, Grammatik und der Aussprache-Tipp der Lektion. */
    private fun russianDeep(ctx: RussianFeedContext, p: RuPhrase, day: RuDay): FeedDeepDive {
        val entry = ctx.deepdive[p.id]
        val sections = buildList {
            if (entry != null) {
                if (entry.variants.isNotEmpty()) {
                    add(FeedDeepSection("So kannst du es auch sagen", "", entry.variants.map { FeedDeepPhrase(it.ru, it.de, it.use) }))
                }
                if (entry.examples.isNotEmpty()) {
                    add(FeedDeepSection("Im Gespräch", "", entry.examples.map { FeedDeepPhrase(it.ru, it.de, it.why) }))
                }
                entry.gender?.let { g -> add(FeedDeepSection(g.title, g.text, g.forms.map { FeedDeepPhrase(it.ru, it.de, it.use) })) }
                entry.grammar?.takeIf { it.isNotBlank() }?.let { add(FeedDeepSection("Grammatik dahinter", it)) }
            }
            // Wort für Wort aus dem Glossar des Kurses — nur bei Sätzen, einzelne Wörter erklären sich selbst.
            val glossed = RussianGlossary.chunks(p.ru).filter { it.gloss != null }
            if (p.wordCount >= 2 && glossed.size >= 2) {
                add(
                    FeedDeepSection(
                        "Wort für Wort",
                        glossed.joinToString("\n") { c ->
                            val cyr = c.words.joinToString(" ") { it.cyrillic }
                            val latin = c.words.joinToString(" ") { w -> w.latin.joinToString("") { it.text } }
                            "$cyr ($latin) = ${c.gloss}"
                        },
                    ),
                )
            }
            val wordForWord = listOfNotNull(
                p.lit?.takeIf { it.isNotBlank() }?.let { "Wörtlich: »$it«." },
                p.note?.takeIf { it.isNotBlank() },
            ).joinToString(" ")
            if (wordForWord.isNotBlank()) add(FeedDeepSection("Gut zu wissen", wordForWord))
            add(FeedDeepSection("Aussprache: ${day.sound.title}", day.sound.text))
            day.grammar?.takeIf { it.isNotBlank() }?.let { add(FeedDeepSection("Grammatik der Lektion ${day.day}", it)) }
        }
        return FeedDeepDive(
            lead = entry?.summary?.takeIf { it.isNotBlank() } ?: "»${p.display}« heißt: ${p.de}",
            sections = sections,
            tryIt = "Sag den Satz heute dreimal laut: einmal langsam, einmal normal, einmal so, als würdest du ihn wirklich meinen.",
            takeaways = listOfNotNull(entry?.register?.takeIf { it.isNotBlank() }?.let { "Tonlage: $it" }),
        )
    }

    companion object {
        /** So viele neue Buchstaben stellt eine Sitzung höchstens vor — mehr bleibt nicht hängen. */
        const val MAX_NEW_LETTERS = 4

        /** Karten, die man am Tag erledigt haben soll, damit die Serie weiterläuft. */
        const val DAILY_GOAL = 12

        /**
         * Abfrage nach Lernstand des Satzes (Box 1–6 im Wiederholungsplan):
         * frisch → erkennen (lesen, hören), gefestigt → abrufen, sicher → selbst bilden.
         */
        fun exerciseKinds(box: Int, words: Int): List<RuExerciseKind> {
            val canBuild = words in 2..6
            val canGap = words >= 3
            return when {
                box <= 2 -> listOf(RuExerciseKind.MEANING, RuExerciseKind.LISTEN, RuExerciseKind.STRESS)
                box <= 4 -> listOfNotNull(
                    RuExerciseKind.RECALL, RuExerciseKind.LISTEN, RuExerciseKind.GAP.takeIf { canGap },
                    RuExerciseKind.SPELL, RuExerciseKind.BUILD.takeIf { canBuild },
                )
                else -> listOfNotNull(
                    RuExerciseKind.BUILD.takeIf { canBuild }, RuExerciseKind.RECALL,
                    RuExerciseKind.GAP.takeIf { canGap }, RuExerciseKind.SPELL,
                )
            }
        }

        /** Wie oft ein Bereich je Runde drankommt. Top-Fokus heißt: gut jede vierte Karte. */
        fun weight(rank: ModuleRank) = when (rank) {
            ModuleRank.RANK_3 -> 5
            ModuleRank.RANK_2 -> 2
            ModuleRank.RANK_1 -> 1
        }

        /**
         * Ein Schwung: jeder Bereich so oft, wie sein Rang es vorgibt, gemischt — und so
         * gelegt, dass möglichst nie derselbe Bereich zweimal hintereinander kommt.
         */
        fun moduleOrder(
            enabled: Set<MindfulModuleType>,
            ranks: Map<MindfulModuleType, ModuleRank>,
            rng: Random,
        ): List<MindfulModuleType> {
            val left = enabled.associateWith { weight(ranks[it] ?: ModuleRank.RANK_1) }.toMutableMap()
            val out = mutableListOf<MindfulModuleType>()
            while (left.isNotEmpty()) {
                val last = out.lastOrNull()
                val candidates = left.keys.filter { it != last }.ifEmpty { left.keys.toList() }
                // Wer noch am häufigsten dran ist, zuerst — sonst stauen sich seine Karten am Ende.
                val most = candidates.maxOf { left.getValue(it) }
                val pick = candidates.filter { left.getValue(it) == most }.random(rng)
                out += pick
                val rest = left.getValue(pick) - 1
                if (rest == 0) left.remove(pick) else left[pick] = rest
            }
            return out
        }
    }
}

/** Dieselbe Karte mit dem Rang ihres Bereichs (steuert die »Top-Fokus«-Marke). */
fun MindfulCard.withRank(rank: ModuleRank): MindfulCard = when (this) {
    is QuoteCardItem -> copy(rank = rank)
    is BrainPuzzleCardItem -> copy(rank = rank)
    is LanguageCardItem -> copy(rank = rank)
    is HistoryCardItem -> copy(rank = rank)
    is MentalModelCardItem -> copy(rank = rank)
    is BreathworkCardItem -> copy(rank = rank)
    is ScienceCardItem -> copy(rank = rank)
    is FinanceCardItem -> copy(rank = rank)
    is GeographyCardItem -> copy(rank = rank)
    is SpeedMathCardItem -> copy(rank = rank)
    is ArtCultureCardItem -> copy(rank = rank)
    is VocabularyCardItem -> copy(rank = rank)
    is PhilosophyCardItem -> copy(rank = rank)
}
