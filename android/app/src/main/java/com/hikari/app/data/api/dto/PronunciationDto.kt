package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

/** Ein Soll-Phonem des Muttersprachler-Clips und das, was der Lernende gesagt hat. */
@Serializable
data class PhonemeMarkDto(
    val ref: String,
    val user: String? = null,
    /** "ok" | "sub" | "del" */
    val status: String,
)

/** Antwort von POST /russian/pronunciation. */
@Serializable
data class PronunciationDto(
    val score: Int = 0,
    val marks: List<PhonemeMarkDto> = emptyList(),
    val inserted: Int = 0,
)

@Serializable
data class PronunciationStatusDto(val available: Boolean = false)
