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
    private var request: Job? = null
    private var lastActivity = SystemClock.elapsedRealtime()
    private var pendingSeconds = 0L
    private var pendingDay = today()
    init { refreshStatus() }
    fun today() = LocalDate.now().toEpochDay()
    fun touch() { lastActivity = SystemClock.elapsedRealtime() }
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
    fun start(confidence: Int = 2) {
        if (busy) return
        flushTime()
        save(RuChatSession(UUID.randomUUID().toString(), settings, RuChatLearning.supportStage(progress.value), confidenceBefore = confidence))
        touch()
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
                else "Gesprächs-KI nicht erreichbar oder Antwort unvollständig. Starte LM Studio mit einem Chatmodell und dem lokalen Server. Deine Antwort bleibt erhalten."
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
        val s = session ?: return
        if (s.finished || busy || SystemClock.elapsedRealtime() - lastActivity > 90_000) return
        if (pendingDay != today()) { flushTime(); pendingDay = today() }
        pendingSeconds++
        session = s.copy(activeSeconds = s.activeSeconds + 1)
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
