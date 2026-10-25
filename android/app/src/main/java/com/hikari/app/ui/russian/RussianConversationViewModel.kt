package com.hikari.app.ui.russian

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.HikariApi
import com.hikari.app.domain.russian.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.HttpException

@HiltViewModel
class RussianConversationViewModel @Inject constructor(
    private val api: HikariApi,
    private val store: RussianConversationStore,
) : ViewModel() {
    val progress = store.state
    var session by mutableStateOf(store.loadSession()); private set
    var settings by mutableStateOf(store.state.value.settings); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var available by mutableStateOf<Boolean?>(null); private set
    var repair by mutableStateOf(false); private set
    var page by mutableStateOf("home"); private set
    private var request: Job? = null
    private var lastActivity = SystemClock.elapsedRealtime()
    private var pendingSeconds = 0L
    private var pendingDay = today()
    init { refreshStatus() }
    fun today() = LocalDate.now().toEpochDay()
    fun touch() { lastActivity = SystemClock.elapsedRealtime() }
    fun navigate(value: String) { flushTime(); page = value; error = null; touch() }
    fun beginLesson(id: String) {
        if (!RuJourney.available(progress.value.foundation, id)) return
        store.update { it.copy(foundation = RuJourney.start(it.foundation, id, UUID.randomUUID().toString())) }
        navigate("lesson")
    }
    fun resumeLesson() { if (progress.value.foundation.run != null) navigate("lesson") }
    fun foundationDraft(text: String, spoken: Boolean = false) {
        touch()
        store.update { p -> p.copy(foundation = p.foundation.copy(run = p.foundation.run?.copy(
            draft = text.take(180), spoken = spoken, result = "",
        ))) }
    }
    fun foundationHelp() {
        touch()
        store.update { p -> p.copy(foundation = p.foundation.copy(run = p.foundation.run?.copy(helped = true))) }
    }
    fun checkFoundation() {
        val run = progress.value.foundation.run ?: return
        if (run.phase !in listOf("guided", "recall") || run.result == "correct" || run.draft.isBlank()) return
        val correct = RuJourney.matches(run, run.draft)
        val independent = run.phase == "recall" && !run.helped
        store.update { p ->
            val next = p.copy(foundation = p.foundation.copy(run = run.copy(
                result = if (correct) "correct" else "retry",
                recalled = run.recalled + if (correct && independent) 1 else 0,
                spokenAnswers = run.spokenAnswers + if (correct && run.spoken) 1 else 0,
            )))
            if (correct && run.phase == "recall") RuChatLearning.record(next, RuChatObservation(
                "${run.id}:${run.phase}:${run.index}", today(), 0, RuJourney.lesson(run.lessonId).scenario,
                run.draft, true, independent, run.spoken,
            )) else next
        }
        touch()
    }
    fun nextFoundation() {
        store.update { p ->
            val foundation = RuJourney.nextStep(p.foundation)
            var next = p.copy(foundation = foundation)
            if (foundation.run?.phase == "complete") {
                RuJourney.lesson(foundation.run.lessonId).words.forEach { next = RuChatLearning.saveCard(next, it.phrase, today()) }
            }
            next
        }
        touch()
    }
    fun refreshStatus() { viewModelScope.launch {
        available = try { api.russianConversationStatus().available } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
    } }
    fun configure(value: RuChatSettings) {
        settings = value
        store.update { it.copy(settings = value) }
    }
    private fun save(value: RuChatSession) { session = value; store.save(value) }
    fun draft(text: String, spoken: Boolean = false) {
        touch()
        session?.let { save(it.copy(draft = text.take(1500), draftSpoken = spoken)) }
    }
    fun hint() {
        touch()
        session?.let { save(it.copy(hint = (it.hint + 1).coerceAtMost(4), helped = true)) }
    }
    fun assistance() { session?.let { save(it.copy(helped = true)) }; touch() }
    fun learnTurnWords() {
        val words = session?.turns?.lastOrNull()?.reply?.words ?: return
        store.update { p ->
            var next = p.copy(conversationVocabulary = (p.conversationVocabulary + words)
                .distinctBy { RuJourney.normalize(it.ru) }.takeLast(100))
            words.forEach { next = RuChatLearning.saveCard(next, it, today()) }
            next
        }
        touch()
    }
    fun start(confidence: Int = 2) {
        if (busy) return
        if (!RuJourney.freeReady(progress.value.foundation)) { navigate("home"); return }
        flushTime()
        save(RuChatSession(UUID.randomUUID().toString(), settings, RuChatLearning.supportStage(progress.value), confidenceBefore = confidence))
        touch()
        page = "chat"
        send("start")
    }
    fun send(action: String = if (repair) "repair" else "reply") {
        val current = session ?: return
        if (busy || current.finished) return
        val answering = action == "reply" || action == "repair"
        if (answering && current.draft.isBlank()) return
        val history = if (action == "repair") current.history.dropLast(2) else current.history
        if (history.size > 77) { finish(); return }
        error = null; busy = true; touch()
        request = viewModelScope.launch {
            try {
                val response = api.russianConversationTurn(RuChatRequest(
                    stage = current.stage, scenario = RuChatCurriculum.scenario(current.settings.scenario).let { "${it.title}: ${it.scene} ${it.goal}" },
                    interest = current.settings.interest, mood = current.settings.mood, goal = current.settings.goal,
                    action = action, message = if (answering) current.draft.trim() else "", history = history,
                    review = RuChatLearning.due(progress.value, today()).take(3).map { it.phrase.ru },
                    knownWords = (RuJourney.known(progress.value.foundation).map { it.phrase } + progress.value.conversationVocabulary)
                        .distinctBy { RuJourney.normalize(it.ru) }.take(120),
                ))
                val turnId = UUID.randomUUID().toString()
                val turn = RuChatTurn(turnId, response, if (answering) current.draft.trim() else "",
                    current.draftSpoken, current.helped || action == "repair", action)
                val nextHistory = history + RuChatMessage("user", if (answering) current.draft.trim() else "Gesprächsaktion: $action") +
                    RuChatMessage("assistant", response.partnerRu)
                val turns = (if (action == "repair") current.turns.dropLast(1) else current.turns) + turn
                save(current.copy(turns = turns, history = nextHistory, draft = "", draftSpoken = false, hint = 0,
                    helped = action == "simplify", activeSeconds = session?.activeSeconds ?: current.activeSeconds))
                if (action == "reply" && response.feedback != null) {
                    store.update { RuChatLearning.record(it, RuChatObservation(turnId, today(), current.stage,
                        current.settings.scenario, current.draft.trim(), response.feedback.meaning == "clear",
                        !current.helped, current.draftSpoken)) }
                }
                repair = false; available = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                error = if (e is HttpException && e.code() == 429)
                    "Anja ist gerade beschäftigt. Deine Antwort bleibt erhalten — versuch es gleich erneut."
                else "Anja konnte gerade keine passende Antwort vorbereiten. Dein Text bleibt erhalten. Versuche es erneut. Falls es wieder nicht klappt, prüfe die Verbindung zu deinem KI-Server."
            } finally { busy = false }
        }
    }
    fun retryAnswer() {
        val last = session?.turns?.lastOrNull() ?: return
        if (last.answer.isBlank()) return
        repair = true
        draft(last.answer, last.spoken)
        assistance()
    }
    fun cancelRepair() { repair = false; draft("") }
    fun savePhrase(phrase: RuChatPhrase) { store.update { RuChatLearning.saveCard(it, phrase, today()) } }
    fun gradeReview(card: RuChatCard, correct: Boolean) {
        store.update { p -> p.copy(cards = p.cards.map { if (it.phrase.ru == card.phrase.ru) RuChatLearning.grade(it, correct, today()) else it }) }
    }
    fun finish() {
        request?.cancel(); busy = false; repair = false; flushTime()
        session?.let { save(it.copy(finished = true)) }
    }
    fun confidence(value: Int) { session?.let { save(it.copy(confidenceAfter = value)) } }
    fun setup() { request?.cancel(); busy = false; flushTime(); session = null; store.clearSession(); repair = false; error = null }
    fun tick() {
        val s = session
        val foundationActive = page == "lesson" && progress.value.foundation.run?.phase in listOf("learn", "guided", "recall")
        val chatActive = page == "chat" && s?.finished == false
        if ((!foundationActive && !chatActive) || busy || SystemClock.elapsedRealtime() - lastActivity > 90_000) return
        if (pendingDay != today()) { flushTime(); pendingDay = today() }
        pendingSeconds++
        if (chatActive && s != null) session = s.copy(activeSeconds = s.activeSeconds + 1)
        if (pendingSeconds >= 15) flushTime()
    }
    fun flushTime() {
        if (pendingSeconds > 0) {
            val seconds = pendingSeconds; pendingSeconds = 0
            store.update { p -> p.copy(activeSecondsByDay = (p.activeSecondsByDay +
                (pendingDay to ((p.activeSecondsByDay[pendingDay] ?: 0) + seconds))).filterKeys { it >= today() - 365 }) }
        }
        session?.let(store::save)
    }
    override fun onCleared() { flushTime() }
}
