package com.hikari.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "hikari_settings")

private val BACKEND_URL_KEY = stringPreferencesKey("backend_url")
private val DAILY_BUDGET_KEY = intPreferencesKey("daily_budget")

const val DEFAULT_BACKEND_URL = "http://kadir-laptop.tail1234.ts.net:3000"
const val DEFAULT_DAILY_BUDGET = 15

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    val backendUrl: Flow<String> = ctx.dataStore.data.map {
        it[BACKEND_URL_KEY] ?: DEFAULT_BACKEND_URL
    }

    val dailyBudget: Flow<Int> = ctx.dataStore.data.map {
        it[DAILY_BUDGET_KEY] ?: DEFAULT_DAILY_BUDGET
    }

    suspend fun setBackendUrl(url: String) {
        ctx.dataStore.edit { it[BACKEND_URL_KEY] = url.trimEnd('/') }
    }

    suspend fun setDailyBudget(value: Int) {
        ctx.dataStore.edit { it[DAILY_BUDGET_KEY] = value.coerceIn(1, 100) }
    }
}
