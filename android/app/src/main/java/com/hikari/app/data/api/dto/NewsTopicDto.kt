package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

/** Ein vom Backend angebotenes Nachrichten-Thema (GET news/topics). */
@Serializable
data class NewsTopicDto(
    val key: String,
    val label: String,
    val lang: String = "de",
)
