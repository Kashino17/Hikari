package com.hikari.app.player

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.hikari.app.data.prefs.SponsorBlockPrefs
import com.hikari.app.data.sponsor.SegmentBehavior
import com.hikari.app.data.sponsor.SegmentCategories
import com.hikari.app.data.sponsor.SponsorBlockClient
import com.hikari.app.data.sponsor.SponsorSegment
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Was der Player gerade über die Abschnitte des laufenden Videos weiß. */
data class SponsorSkipState(
    val videoId: String? = null,
    val segments: List<SponsorSegment> = emptyList(),
    /** Abschnitt mit „Knopf zeigen“, in dem die Wiedergabe gerade steht. */
    val prompt: SponsorSegment? = null,
    /** Zuletzt automatisch übersprungener Abschnitt — für „Rückgängig“. */
    val lastAuto: AutoSkip? = null,
)

data class AutoSkip(val segment: SponsorSegment, val fromMs: Long, val token: Int)

/**
 * Überspringt markierte Abschnitte (Sponsor, Intro, Outro …) im laufenden
 * Video. Hängt am Player statt am Bildschirm: so springt die Wiedergabe auch
 * weiter, wenn das Video nur noch in der Mini-Leiste läuft.
 */
@Singleton
@OptIn(UnstableApi::class)
class SponsorSkipEngine @Inject constructor(
    private val client: SponsorBlockClient,
    private val prefs: SponsorBlockPrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(SponsorSkipState())
    val state: StateFlow<SponsorSkipState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var job: Job? = null
    private var skipToken = 0

    /** Vom Nutzer zurückgeholte Abschnitte — die bleiben für dieses Video in Ruhe. */
    private val ignored = mutableSetOf<SponsorSegment>()
    private var lastSkipped: SponsorSegment? = null
    private var lastSkippedAt = 0L

    @Volatile private var behaviors: Map<String, SegmentBehavior> =
        SegmentCategories.all.associate { it.apiKey to it.defaultBehavior }

    /** Wie von SponsorBlock geliefert — daraus entsteht mit Einstellungen und Kapiteln die Sprungliste. */
    private var rawSegments: List<SponsorSegment> = emptyList()
    private var chapterStartsMs: List<Long> = emptyList()

    init {
        scope.launch {
            prefs.behaviors.collect {
                behaviors = it
                rebuildSegments()
            }
        }
    }

    /** Kapitelanfänge des laufenden Videos — über sie hinweg werden Abschnitte nicht zusammengefasst. */
    fun setChapters(videoId: String, startsMs: List<Long>) {
        if (_state.value.videoId != videoId || chapterStartsMs == startsMs) return
        chapterStartsMs = startsMs
        rebuildSegments()
    }

    private fun rebuildSegments() {
        val merged = SponsorSkipListener.mergeAutoSegments(rawSegments, behaviors, chapterStartsMs)
        _state.update { it.copy(segments = merged) }
    }

    /** Koppelt die Überwachung an [player]; ein erneuter Aufruf fürs selbe Video ist folgenlos. */
    fun bind(videoId: String, player: ExoPlayer) {
        if (this.player === player && _state.value.videoId == videoId) return
        unbind()
        this.player = player
        _state.value = SponsorSkipState(videoId = videoId)
        job = scope.launch {
            rawSegments = client.fetchSegments(videoId)
            rebuildSegments()
            if (rawSegments.isEmpty()) return@launch
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    fun unbind() {
        job?.cancel()
        job = null
        player = null
        rawSegments = emptyList()
        chapterStartsMs = emptyList()
        ignored.clear()
        lastSkipped = null
        _state.value = SponsorSkipState()
    }

    /** Der Nutzer tippt auf „… überspringen“. */
    fun skipPrompt() {
        val p = player ?: return
        val segment = _state.value.prompt ?: return
        val from = p.currentPosition
        seekExact(p, endMs(segment, p))
        recordSkip(segment, from)
        _state.update { it.copy(prompt = null) }
    }

    /** Springt zurück an die Stelle vor dem letzten automatischen Sprung. */
    fun undoLastAuto() {
        val p = player ?: return
        val last = _state.value.lastAuto ?: return
        ignored += last.segment
        seekExact(p, last.fromMs)
        _state.update { it.copy(lastAuto = null) }
    }

    private fun tick() {
        val p = player ?: return
        val current = _state.value
        val pos = p.currentPosition
        val active = current.segments.filterNot { it in ignored }
        when (val decision = SponsorSkipListener.evaluate(pos, active, behaviors)) {
            is SponsorSkipListener.Decision.Auto -> {
                if (current.prompt != null) _state.update { it.copy(prompt = null) }
                // Nur im laufenden Bild springen — nicht, während der Nutzer spult oder pausiert.
                if (!p.isPlaying) return
                val segment = decision.segment
                val now = SystemClock.elapsedRealtime()
                if (segment == lastSkipped && now - lastSkippedAt < LOOP_GUARD_MS) {
                    // Derselbe Abschnitt zweimal kurz hintereinander: der Sprung
                    // greift nicht (z. B. Stream ohne Zielposition). Nicht endlos wiederholen.
                    ignored += segment
                    return
                }
                val target = endMs(segment, p)
                if (target <= pos) return
                lastSkipped = segment
                lastSkippedAt = now
                seekExact(p, target)
                recordSkip(segment, pos)
                _state.update { it.copy(lastAuto = AutoSkip(segment, pos, ++skipToken)) }
            }
            is SponsorSkipListener.Decision.Manual -> {
                if (current.prompt != decision.segment) _state.update { it.copy(prompt = decision.segment) }
            }
            SponsorSkipListener.Decision.None -> {
                if (current.prompt != null) _state.update { it.copy(prompt = null) }
            }
        }
    }

    private fun endMs(segment: SponsorSegment, p: ExoPlayer): Long {
        val end = (segment.endSeconds * 1000).toLong()
        val duration = p.duration
        return if (duration != C.TIME_UNSET && duration > 0) end.coerceAtMost(duration) else end
    }

    /**
     * Der Player spult sonst auf das nächste Schlüsselbild (CLOSEST_SYNC) —
     * das kann wieder im Abschnitt liegen. Für den Sprung gilt deshalb kurz
     * die exakte Position; die Reihenfolge der drei Aufrufe bleibt im Player erhalten.
     */
    private fun seekExact(p: ExoPlayer, positionMs: Long) {
        val previous = p.seekParameters
        p.setSeekParameters(SeekParameters.EXACT)
        p.seekTo(positionMs)
        p.setSeekParameters(previous)
    }

    private fun recordSkip(segment: SponsorSegment, fromMs: Long) {
        val saved = (segment.endSeconds * 1000).toLong() - fromMs
        scope.launch(Dispatchers.IO) { runCatching { prefs.recordSkip(saved) } }
    }

    private companion object {
        const val TICK_MS = 250L
        const val LOOP_GUARD_MS = 2_000L
    }
}
