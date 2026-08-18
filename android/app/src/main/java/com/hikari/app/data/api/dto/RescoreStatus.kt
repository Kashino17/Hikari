package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

/** Wie viele Feed-Videos noch auf die Neubewertung warten. */
@Serializable
data class RescoreStatus(val pending: Int)
