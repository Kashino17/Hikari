package com.hikari.app.ui.russian

import android.content.Context
import android.speech.SpeechRecognizer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.domain.russian.RuExercise
import com.hikari.app.domain.russian.RuLessonBuilder
import com.hikari.app.domain.russian.RuSessionResult
import com.hikari.app.domain.russian.RuSettings
import com.hikari.app.domain.russian.RuSrs
import com.hikari.app.domain.russian.RussianCourseRepository
import com.hikari.app.domain.russian.RussianProgressStore
import com.hikari.app.domain.russian.apply
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RuSessionMode { LESSON, REVIEW, DIALOG }

data class RuFeedback(val correct: Boolean, val skipped: Boolean = false, val requeued: Boolean = false)

data class RuSummary(
    val mode: RuSessionMode,
    val day: Int?,
    val xp: Int,
    val correct: Int,
    val total: Int,
    val stars: Int,
    val streak: Int,
    val empty: Boolean = false,
)

data class RuSessionUi(
    val steps: List<RuExercise> = emptyList(),
    val pos: Int = 0,
    val feedback: RuFeedback? = null,
    val summary: RuSummary? = null,
) {
    val current: RuExercise? get() = steps.getOrNull(pos)
    val progress: Float get() = if (steps.isEmpty()) 0f else pos.toFloat() / steps.size
}

@HiltViewModel
class RussianSessionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: RussianCourseRepository,
    private val store: RussianProgressStore,
    val audio: RussianAudioPlayer,
    @ApplicationContext context: Context,
) : ViewModel() {

    val mode: RuSessionMode = when (savedState.get<String>("mode")) {
        "review" -> RuSessionMode.REVIEW
        "dialog" -> RuSessionMode.DIALOG
        else -> RuSessionMode.LESSON
    }
    val day: Int = savedState.get<String>("day")?.toIntOrNull() ?: 1
    val settings: RuSettings = store.state.value.settings
    val speechAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private val _ui = MutableStateFlow(RuSessionUi())
    val ui: StateFlow<RuSessionUi> = _ui.asStateFlow()

    /** Erste Antwort je Item (nur sie zählt fürs Leitner-System). */
    private val firstTry = mutableMapOf<String, Boolean>()
    /** Positionen, deren Übung schon einmal hinten angehängt wurde … */
    private val retried = mutableSetOf<Int>()
    /** … und die Positionen dieser Wiederholungs-Kopien. */
    private val retriedPositions = mutableSetOf<Int>()
    private var xp = 0
    private var correct = 0
    private var total = 0
    private var spokenOk = 0

    init {
        viewModelScope.launch { audio.warmUp() }
        val index = repo.index
        val progress = store.state.value
        val rng = Random(System.nanoTime())
        val steps = when (mode) {
            RuSessionMode.LESSON -> RuLessonBuilder.lesson(index, day, settings, speechAvailable, rng)
            RuSessionMode.REVIEW -> RuLessonBuilder.review(
                index,
                RuSrs.dueIds(progress.cards, store.today()),
                progress.unlockedDay(index.days.size),
                settings,
                speechAvailable,
                rng = rng,
            )
            RuSessionMode.DIALOG -> index.day(day)?.let { listOf(RuExercise.Dialog(it, index.dialog(it, settings))) }.orEmpty()
        }
        _ui.value = if (steps.isEmpty()) {
            RuSessionUi(summary = RuSummary(mode, day, 0, 0, 0, 0, progress.streak, empty = true))
        } else {
            RuSessionUi(steps = steps)
        }
    }

    /** Antwort auf die aktuelle Übung. Falsch → dieselbe Übung kommt am Ende noch einmal. */
    fun answer(isCorrect: Boolean) {
        val state = _ui.value
        val step = state.current ?: return
        if (state.feedback != null) return
        val id = step.gradedId
        val isRetry = state.pos in retriedPositions
        if (id != null) {
            if (id !in firstTry) firstTry[id] = isCorrect
            total++
            if (isCorrect) {
                correct++
                xp += if (isRetry) 5 else 10
            }
        }
        var steps = state.steps
        val requeue = !isCorrect && id != null && state.pos !in retried && !isRetry
        if (requeue) {
            retried += state.pos
            // Vor dem abschließenden Rollenspiel einreihen — das Gespräch bleibt das Finale.
            val dialogAt = steps.indexOfLast { it is RuExercise.Dialog }
            val insertAt = if (dialogAt > state.pos) dialogAt else steps.size
            steps = steps.toMutableList().apply { add(insertAt, step) }
            // Positionen hinter der Einfügestelle rücken eins weiter.
            val shifted = retriedPositions.map { if (it >= insertAt) it + 1 else it }
            retriedPositions.clear()
            retriedPositions += shifted
            retriedPositions += insertAt
        }
        _ui.value = state.copy(steps = steps, feedback = RuFeedback(isCorrect, requeued = requeue))
    }

    /** Sprechübung überspringen — zählt weder richtig noch falsch. */
    fun skip() {
        val state = _ui.value
        if (state.feedback != null) return
        _ui.value = state.copy(feedback = RuFeedback(correct = true, skipped = true))
    }

    fun spokenCorrectly() {
        spokenOk++
    }

    /** Rollenspiel beendet: [right] von [lines] Zeilen im ersten Versuch richtig. */
    fun dialogDone(right: Int, lines: Int) {
        xp += right * 3 + 10
        correct += right
        total += lines
        next()
    }

    fun next() {
        audio.stop()
        val state = _ui.value
        val pos = state.pos + 1
        if (pos >= state.steps.size) finish() else _ui.value = state.copy(pos = pos, feedback = null)
    }

    private fun finish() {
        val index = repo.index
        val today = store.today()
        val stars = RuSrs.stars(correct, total)
        val bonus = when (mode) {
            RuSessionMode.LESSON -> 30
            RuSessionMode.REVIEW -> 10
            RuSessionMode.DIALOG -> 0
        }
        val dayItems = index.day(day)?.items?.map { it.id }?.toSet().orEmpty()
        val result = RuSessionResult(
            xp = xp + bonus,
            graded = firstTry.toMap(),
            introduced = if (mode == RuSessionMode.LESSON) dayItems else emptySet(),
            completedDay = if (mode == RuSessionMode.LESSON) day else null,
            stars = stars,
            spokenOk = spokenOk,
        )
        store.update { it.apply(result, today) }
        _ui.value = _ui.value.copy(
            pos = _ui.value.steps.size,
            feedback = null,
            summary = RuSummary(mode, day, xp + bonus, correct, total, stars, store.state.value.streak),
        )
    }

    override fun onCleared() {
        audio.stop()
    }
}
