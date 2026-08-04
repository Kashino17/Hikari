package com.hikari.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Eintrag im Suchverlauf der Musik-Suche; die Query selbst ist der Schlüssel. */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long,
)
