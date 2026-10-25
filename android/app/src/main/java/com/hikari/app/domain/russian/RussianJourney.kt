package com.hikari.app.domain.russian

import kotlinx.serialization.Serializable

data class RuJourneyWord(val id: String, val ru: String, val de: String, val note: String, val art: String) {
    val plain get() = RussianPhonetics.plain(ru)
    val display get() = RussianPhonetics.display(ru)
    val reading get() = RussianPhonetics.transliterateFlat(ru)
    val phrase get() = RuChatPhrase(plain, de)
}
data class RuJourneyLine(
    val ru: String, val de: String, val situation: String, val instruction: String,
    val answers: List<String>, val answerDe: String, val success: String,
)
data class RuJourneyLesson(
    val id: String, val title: String, val subtitle: String, val place: String,
    val words: List<RuJourneyWord>, val lines: List<RuJourneyLine>, val scenario: String = "cafe",
)
@Serializable data class RuFoundationRun(
    val id: String, val lessonId: String, val phase: String = "learn", val index: Int = 0,
    val draft: String = "", val spoken: Boolean = false, val helped: Boolean = false,
    val result: String = "", val recalled: Int = 0, val spokenAnswers: Int = 0,
)
@Serializable data class RuFoundationProgress(
    val introduced: Set<String> = emptySet(), val completed: Set<String> = emptySet(),
    val run: RuFoundationRun? = null,
)

/** Curated input comes before production. No model can add unknown words to these lessons. */
object RuJourney {
    val lessons = listOf(
        RuJourneyLesson("hello", "Dein erstes Hallo", "Ankommen. Einen Tee annehmen. Danke sagen.", "Ein Platz im Café", listOf(
            RuJourneyWord("hello", "Прив+ет", "Hallo", "So begrüßt du Anja. Locker, wie unter Freunden.", "hello"),
            RuJourneyWord("tea", "чай", "Tee", "Ein Wort für eine warme Tasse. Mehr brauchst du gerade nicht.", "tea"),
            RuJourneyWord("yes", "да", "Ja", "Damit nimmst du Anjas Einladung zum Tee an.", "yes"),
            RuJourneyWord("thanks", "Спас+ибо", "Danke", "Ein kleines Wort, das ein Gespräch wärmer macht.", "thanks"),
        ), listOf(
            RuJourneyLine("Привет!", "Hallo!", "Anja winkt dir zu.", "Begrüße Anja zurück.", listOf("Привет!"), "Hallo!", "Du hast Anja begrüßt."),
            RuJourneyLine("Чай?", "Tee?", "Anja bietet dir einen Tee an.", "Nimm den Tee mit einem Ja an.", listOf("Да.", "Да, спасибо."), "Ja.", "Anja weiß: Du möchtest einen Tee."),
            RuJourneyLine("Чай.", "Tee.", "Anja stellt die Tasse vor dich.", "Bedanke dich bei Anja.", listOf("Спасибо!"), "Danke!", "Du hast dich auf Russisch bedankt."),
        )),
        RuJourneyLesson("choice", "Lieber einen Kaffee", "Du entscheidest, was in deine Tasse kommt.", "Dein Lieblingsplatz", listOf(
            RuJourneyWord("coffee", "к+офе", "Kaffee", "Heute darf es auch ein Kaffee sein.", "coffee"),
            RuJourneyWord("no", "нет", "Nein", "Du darfst freundlich ablehnen. Ein Wort reicht.", "no"),
            RuJourneyWord("please", "Пож+алуйста", "Bitte · gern geschehen", "Beim Bestellen heißt es bitte. Nach einem Danke: gern geschehen.", "thanks"),
            RuJourneyWord("or", "+или", "oder", "Чай или кофе? bedeutet: Tee oder Kaffee? Die Getränke kennst du schon.", "choice"),
        ), listOf(
            RuJourneyLine("Чай?", "Tee?", "Anja erinnert sich an deinen Tee.", "Heute möchtest du keinen Tee. Sage Nein.", listOf("Нет, спасибо.", "Нет."), "Nein, danke.", "Du hast freundlich abgelehnt."),
            RuJourneyLine("Чай или кофе?", "Tee oder Kaffee?", "Anja lässt dir die Wahl.", "Bestelle einen Kaffee. Bitte kennst du jetzt auch.", listOf("Кофе, пожалуйста.", "Кофе."), "Kaffee, bitte.", "Du hast selbst einen Kaffee bestellt."),
            RuJourneyLine("Кофе.", "Kaffee.", "Dein Kaffee ist da.", "Bedanke dich wie im ersten Gespräch.", listOf("Спасибо!"), "Danke!", "Deine ersten Wörter kommen wieder zum Einsatz."),
        )),
        RuJourneyLesson("likes", "Das mag ich", "Aus bekannten Wörtern wird dein erster eigener Satz.", "Ein Gespräch mit Anja", listOf(
            RuJourneyWord("i", "я", "ich", "Mit diesem Wort beginnt ein Satz über dich.", "hello"),
            RuJourneyWord("like", "любл+ю", "ich mag · ich liebe", "Я люблю чай. heißt: Ich mag Tee. Keine Regel auswendig lernen — erst benutzen.", "heart"),
            RuJourneyWord("you", "ты", "du", "So sprichst du Anja an. Ihr seid per du.", "hello"),
            RuJourneyWord("and", "а", "und · aber", "А ты? heißt hier: Und du? Ein kleines Wort zum Zurückfragen.", "choice"),
        ), listOf(
            RuJourneyLine("Чай или кофе?", "Tee oder Kaffee?", "Ihr sucht euch etwas zu trinken aus.", "Wähle Tee oder Kaffee — beides passt.", listOf("Чай.", "Кофе.", "Чай, пожалуйста.", "Кофе, пожалуйста."), "Tee.", "Du hast deine eigene Wahl getroffen."),
            RuJourneyLine("Я люблю чай. А ты?", "Ich mag Tee. Und du?", "Anja erzählt von ihrer Vorliebe.", "Sage mit я люблю, welches Getränk du magst.", listOf("Я люблю чай.", "Я люблю кофе."), "Ich mag Tee.", "Das war ein eigener russischer Satz."),
            RuJourneyLine("Спасибо!", "Danke!", "Anja bedankt sich für eure kleine Pause.", "Antworte mit Gern geschehen.", listOf("Пожалуйста!"), "Gern geschehen!", "Du kannst nicht nur antworten, sondern ein Gespräch abrunden."),
        ), "friend"),
        RuJourneyLesson("feeling", "Wie geht es dir?", "Ein bisschen mehr über dich. Ganz ohne Druck.", "Wiedersehen mit Anja", listOf(
            RuJourneyWord("how", "как", "wie", "Dieses kleine Fragewort hörst du gleich wieder.", "hello"),
            RuJourneyWord("things", "дел+а", "Dinge · Angelegenheiten", "Zusammen: Как дела? — Wie geht es dir? Lerne es als ganzen Ausdruck.", "choice"),
            RuJourneyWord("good", "Хорош+о", "Gut", "Eine vollständige Antwort auf Wie geht es dir?.", "yes"),
            RuJourneyWord("okay", "Норм+ально", "Ganz okay", "Du musst nicht immer gut drauf sein. Ganz okay ist genauso richtig.", "heart"),
        ), listOf(
            RuJourneyLine("Как дела?", "Wie geht es dir?", "Anja freut sich, dich wiederzusehen.", "Sage Gut oder Ganz okay — wie es für dich passt.", listOf("Хорошо.", "Нормально.", "Хорошо, спасибо."), "Gut.", "Du hast gesagt, wie es dir geht."),
            RuJourneyLine("Чай.", "Tee.", "Anja bringt dir einen Tee. Jetzt möchtest du wissen, wie es ihr geht.", "Frage Anja: Wie geht es dir? Du kennst как und дела.", listOf("Как дела?", "А ты?"), "Wie geht es dir?", "Du hast selbst eine Frage gestellt."),
            RuJourneyLine("Хорошо, спасибо!", "Gut, danke!", "Anja freut sich über deine Frage.", "Antworte mit Gern geschehen.", listOf("Пожалуйста!"), "Gern geschehen!", "Ihr habt euch gegenseitig zugehört."),
        ), "friend"),
    )
    val words get() = lessons.flatMap { it.words }
    fun lesson(id: String) = lessons.first { it.id == id }
    fun available(p: RuFoundationProgress, id: String): Boolean {
        val index = lessons.indexOfFirst { it.id == id }
        return index >= 0 && lessons.take(index).all { it.id in p.completed }
    }
    fun next(p: RuFoundationProgress) = lessons.firstOrNull { it.id !in p.completed } ?: lessons.last()
    fun freeReady(p: RuFoundationProgress) = lessons.take(3).all { it.id in p.completed }
    fun known(p: RuFoundationProgress) = words.filter { it.id in p.introduced }
    fun start(p: RuFoundationProgress, lessonId: String, runId: String): RuFoundationProgress =
        if (available(p, lessonId)) p.copy(run = RuFoundationRun(runId, lessonId)) else p
    fun line(run: RuFoundationRun): RuJourneyLine {
        val lines = lesson(run.lessonId).lines
        // Recall changes the order, so it isn't just the last heard phrase repeated.
        val index = if (run.phase == "recall" && lines.size > 1) (run.index + 1) % lines.size else run.index
        return lines[index.coerceIn(lines.indices)]
    }
    fun matches(run: RuFoundationRun, text: String): Boolean =
        line(run).answers.any { normalize(it) == normalize(text) }
    fun normalize(text: String) = RussianPhonetics.plain(text).lowercase().replace('ё', 'е')
        .replace(Regex("[^а-яa-z ]"), " ").trim().replace(Regex("\\s+"), " ")
    fun nextStep(p: RuFoundationProgress): RuFoundationProgress {
        val run = p.run ?: return p
        val lesson = lesson(run.lessonId)
        if (run.phase == "learn") {
            val introduced = p.introduced + lesson.words[run.index].id
            val next = if (run.index + 1 < lesson.words.size) run.copy(index = run.index + 1)
                else run.copy(phase = "guided", index = 0)
            return p.copy(introduced = introduced, run = next)
        }
        if (run.result != "correct" || run.phase == "complete") return p
        val clean = run.copy(draft = "", spoken = false, helped = false, result = "")
        if (run.index + 1 < lesson.lines.size) return p.copy(run = clean.copy(index = run.index + 1))
        return if (run.phase == "guided") p.copy(run = clean.copy(phase = "recall", index = 0))
        else p.copy(completed = p.completed + lesson.id, run = clean.copy(phase = "complete", index = 0))
    }
}
