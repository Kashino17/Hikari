package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

/** Zusammenfassung von Sprachnachrichten (Qwen über LM Studio). */
@Serializable
data class VoiceSummaryDto(
    val title: String,
    val summary: String,
    val points: List<String> = emptyList(),
    val todos: List<String> = emptyList(),
    val dates: List<String> = emptyList(),
    val questions: List<String> = emptyList(),
)

/** Eintrag der Verlaufsliste (GET /voice/jobs) — ohne Transkript. */
@Serializable
data class VoiceJobListItemDto(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    /** queued | transcribing | summarizing | done | failed */
    val status: String,
    val done: Int = 0,
    val total: Int = 0,
    val label: String? = null,
    val title: String? = null,
    val preview: String? = null,
    val todoCount: Int = 0,
    /** Gesamtlänge in Sekunden, sobald alle Clips transkribiert sind. */
    val duration: Double? = null,
    val error: String? = null,
)

@Serializable
data class VoiceClipDto(
    val index: Int,
    val name: String,
    val size: Long = 0,
    val duration: Double? = null,
    val language: String? = null,
    val text: String? = null,
)

/** Vollständiger Job (GET /voice/jobs/{id}). */
@Serializable
data class VoiceJobDto(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val status: String,
    val done: Int = 0,
    val total: Int = 0,
    val label: String? = null,
    val title: String? = null,
    val duration: Double? = null,
    val error: String? = null,
    val queuePosition: Int = 0,
    val summary: VoiceSummaryDto? = null,
    val clips: List<VoiceClipDto> = emptyList(),
    val transcribeMs: Long? = null,
    val summarizeMs: Long? = null,
)

@Serializable
data class VoiceWhisperStatusDto(
    val loaded: Boolean = false,
    val lastUsedAt: Long? = null,
    val idleMinutes: Int = 10,
)

@Serializable
data class VoiceLlmStatusDto(
    val model: String = "",
    val loaded: Boolean? = null,
    val reachable: Boolean = false,
)

@Serializable
data class VoiceStatusDto(
    val whisper: VoiceWhisperStatusDto = VoiceWhisperStatusDto(),
    val llm: VoiceLlmStatusDto = VoiceLlmStatusDto(),
    val busy: Boolean = false,
)
