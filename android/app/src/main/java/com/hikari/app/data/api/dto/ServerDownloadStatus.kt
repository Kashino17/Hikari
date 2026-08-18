package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

/** Antwort von POST /videos/{id}/download — "ready" | "queued". */
@Serializable
data class ServerDownloadStatus(val status: String)
