package com.hikari.app.domain.model

data class Channel(
    val id: String,
    val url: String,
    val title: String,
    val handle: String? = null,
    val description: String? = null,
    val subscribers: Long? = null,
    val thumbnail: String? = null,
    val bannerUrl: String? = null,
    val lastPolledAt: Long? = null,
    val autoApprove: Boolean = false,
)
