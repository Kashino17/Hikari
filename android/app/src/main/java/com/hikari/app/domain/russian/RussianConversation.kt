package com.hikari.app.domain.russian

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

@Serializable data class RuChatPhrase(val ru: String, val de: String)
@Serializable data class RuChatFeedback(
    val meaning: String, val praise: String, val focus: String = "", val repairCue: String = "",
    val correctedRu: String = "", val explanationDe: String = "",
)
@Serializable data class RuChatReply(
    val partnerRu: String, val partnerDe: String, val cue: String,
    val words: List<RuChatPhrase>, val starter: String, val examples: List<RuChatPhrase>,
    val feedback: RuChatFeedback? = null, val chunk: RuChatPhrase, val mission: String,
)
@Serializable data class RuChatMessage(val role: String, val content: String)
@Serializable data class RuChatRequest(
    val stage: Int = 0, val scenario: String, val interest: String = "", val mood: String = "calm",
    val goal: String = "everyday", val action: String, val message: String = "",
    val history: List<RuChatMessage> = emptyList(), val review: List<String> = emptyList(),
)
@Serializable data class RuChatStatus(val available: Boolean = false, val model: String? = null)
@Serializable data class RuChatSettings(
    val scenario: String = "cafe", val interest: String = "", val mood: String = "calm",
    val goal: String = "everyday", val turns: Int = 12, val audioFirst: Boolean = false,
    val autoPlay: Boolean = true, val speed: Float = 0.85f,
    val targetDate: String = "", val daysPerWeek: Int = 5, val minutesPerDay: Int = 30,
    val blockMinutes: Int = 10,
)
data class RuChatScenario(val id: String, val title: String, val scene: String, val goal: String, val emoji: String)
object RuChatCurriculum {
    val stages = listOf("Erste Wörter", "Kurze Sätze", "Nachfragen & Gründe", "Zusammenhängend erzählen", "Meinungen & Nuancen")
    val scenarios = listOf(
        RuChatScenario("cafe", "Ein Café, zwei Menschen", "Du triffst Anja in einem kleinen Café.", "Etwas bestellen und eine Vorliebe nennen", "☕"),
        RuChatScenario("friend", "Ein neuer Kontakt", "Ihr lernt euch kennen, ganz ohne Zeitdruck.", "Dich vorstellen und etwas zurückfragen", "👋"),
        RuChatScenario("weekend", "Ein schöner freier Tag", "Ihr plant etwas, das euch beiden Freude macht.", "Einen Vorschlag machen und euch einigen", "☀️"),
        RuChatScenario("journey", "Unterwegs", "Ihr findet gemeinsam den richtigen Weg.", "Nach einem Ort fragen und Hilfe verstehen", "🚆"),
        RuChatScenario("memory", "Eine kleine Geschichte", "Eine schöne Erinnerung — echt oder erfunden.", "Ein Erlebnis und ein Gefühl ausdrücken", "✨"),
        RuChatScenario("film", "Nach dem Film", "Ihr sprecht über eine erfundene kurze Filmszene.", "Sagen, was passiert und wie du es findest", "🎬"),
        RuChatScenario("music", "Dein Soundtrack", "Ihr sprecht über Musik und ihre Stimmung, ohne Liedtexte zu zitieren.", "Musikgeschmack und Gefühle beschreiben", "🎵"),
        RuChatScenario("choice", "Was ist dir wichtig?", "Ihr trefft eine kleine Entscheidung mit zwei guten Möglichkeiten.", "Eine Meinung sagen und einen Grund nennen", "💬"),
    )
    fun scenario(id: String) = scenarios.firstOrNull { it.id == id } ?: scenarios.first()
}
@Serializable data class RuChatTurn(
    val id: String, val reply: RuChatReply, val answer: String = "", val spoken: Boolean = false,
    val helped: Boolean = false, val action: String = "reply",
)
@Serializable data class RuChatSession(
    val id: String, val settings: RuChatSettings, val stage: Int,
    val turns: List<RuChatTurn> = emptyList(), val history: List<RuChatMessage> = emptyList(),
    val draft: String = "", val draftSpoken: Boolean = false, val hint: Int = 0,
    val helped: Boolean = false, val finished: Boolean = false, val confidenceBefore: Int = 2,
    val confidenceAfter: Int? = null, val activeSeconds: Long = 0,
) {
    val answers get() = turns.filter { it.answer.isNotBlank() }
}
@Serializable data class RuChatObservation(
    val id: String, val day: Long, val stage: Int, val scenario: String,
    val answer: String, val clear: Boolean, val independent: Boolean, val spoken: Boolean,
)
@Serializable data class RuChatCard(
    val phrase: RuChatPhrase, val created: Long, val due: Long, val box: Int = 0,
    val attempts: Int = 0, val delayedCorrect: Int = 0,
)
@Serializable data class RuChatProgress(
    val stage: Int = 0, val observations: List<RuChatObservation> = emptyList(),
    val cards: List<RuChatCard> = emptyList(), val settings: RuChatSettings = RuChatSettings(),
    val activeSecondsByDay: Map<Long, Long> = emptyMap(),
)

/** Conservative app scaffolding, not a validated CEFR/fluency assessment. See research report. */
object RuChatLearning {
    fun record(p: RuChatProgress, observation: RuChatObservation): RuChatProgress {
        if (p.observations.any { it.id == observation.id }) return p
        val next = p.copy(observations = (p.observations + observation).takeLast(500))
        return next.copy(stage = if (readyToAdvance(next)) (p.stage + 1).coerceAtMost(4) else p.stage)
    }
    fun readyToAdvance(p: RuChatProgress): Boolean {
        val recent = p.observations.filter { it.stage == p.stage }.takeLast(20)
        val independent = recent.filter { it.clear && it.independent && it.spoken }
        return p.stage < 4 && recent.size >= 15 && independent.size >= 12 &&
            independent.map { it.day }.distinct().size >= 3 &&
            independent.map { it.scenario }.distinct().size >= 2 &&
            independent.map { normalize(it.answer) }.distinct().size >= 8 &&
            p.cards.count { it.delayedCorrect > 0 } >= (p.stage + 1) * 2
    }
    fun normalize(s: String) = s.lowercase().replace('ё', 'е').replace(Regex("[^а-яa-z0-9 ]"), "").trim()
    fun saveCard(p: RuChatProgress, phrase: RuChatPhrase, today: Long): RuChatProgress {
        if (p.cards.any { normalize(it.phrase.ru) == normalize(phrase.ru) }) return p
        return p.copy(cards = (p.cards + RuChatCard(phrase, today, today + 1)).takeLast(300))
    }
    fun grade(card: RuChatCard, correct: Boolean, today: Long): RuChatCard {
        val box = if (correct) (card.box + 1).coerceAtMost(6) else 0
        val gaps = listOf(1, 1, 2, 4, 7, 14, 30)
        return card.copy(box = box, due = today + gaps[box], attempts = card.attempts + 1,
            delayedCorrect = card.delayedCorrect + if (correct && today >= card.due && today > card.created) 1 else 0)
    }
    fun due(p: RuChatProgress, today: Long) = p.cards.filter { it.due <= today }.sortedBy { it.due }
    fun supportStage(p: RuChatProgress): Int {
        val last = p.observations.takeLast(3)
        return if (last.size == 3 && last.all { !it.clear || !it.independent }) (p.stage - 1).coerceAtLeast(0) else p.stage
    }
}

data class RuStudyPlan(
    val studyDays: Int, val availableHours: Double, val referenceHoursPerStudyDay: Double,
    val blocksPerDay: Int, val exceedsBudget: Boolean,
)
object RuStudyPlanner {
    const val FSI_REFERENCE_HOURS = 1100.0
    const val SOURCE = "https://2021-2025.state.gov/foreign-language-training"
    // FSI's archived classroom benchmark is NOT a German-speaking app learner's required time.
    // Calendar arithmetic only; deliberately no fatigue multiplier or 'fluency date'.
    fun calculate(today: LocalDate, target: LocalDate, daysPerWeek: Int, minutes: Int, blockMinutes: Int): RuStudyPlan? {
        val days = ChronoUnit.DAYS.between(today, target)
        if (days !in 1..3650 || daysPerWeek !in 1..7 || minutes !in 5..600 || blockMinutes !in 5..60) return null
        val studyDays = (days / 7 * daysPerWeek + minOf(days % 7, daysPerWeek.toLong())).toInt()
        if (studyDays == 0) return null
        val reference = FSI_REFERENCE_HOURS / studyDays
        return RuStudyPlan(studyDays, studyDays * minutes / 60.0, reference,
            (minutes + blockMinutes - 1) / blockMinutes, reference > minutes / 60.0)
    }
}
