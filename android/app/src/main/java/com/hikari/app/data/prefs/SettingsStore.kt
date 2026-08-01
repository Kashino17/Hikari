package com.hikari.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "hikari_settings")

private val BACKEND_URL_KEY = stringPreferencesKey("backend_url")
private val DAILY_BUDGET_KEY = intPreferencesKey("daily_budget")
private val SMART_DOWNLOADS_KEY = booleanPreferencesKey("smart_downloads")
private val DOWNLOADS_LIMIT_BYTES_KEY = longPreferencesKey("downloads_limit_bytes")
private val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")
private val INSTRUMENTAL_ONLY_KEY = booleanPreferencesKey("music_instrumental_only")
private val NEWS_ENABLED_KEY = booleanPreferencesKey("news_enabled")
private val NEWS_TIME_MINUTES_KEY = intPreferencesKey("news_time_minutes")
private val NEWS_TOPICS_KEY = stringPreferencesKey("news_topics")
private val NEWS_LOCATION_ENABLED_KEY = booleanPreferencesKey("news_location_enabled")
private val NEWS_CITY_KEY = stringPreferencesKey("news_city")
private val NEWS_LANG_KEY = stringPreferencesKey("news_lang")

const val DEFAULT_BACKEND_URL = "http://macbook-pro.taile64a95.ts.net:3939"
const val DEFAULT_DAILY_BUDGET = 15
const val DEFAULT_NEWS_TIME_MINUTES = 450 // 7:30 Uhr
const val DEFAULT_NEWS_TOPICS = "politik,technologie,wissen"

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

    val smartDownloads: Flow<Boolean> = ctx.dataStore.data.map {
        it[SMART_DOWNLOADS_KEY] ?: true
    }

    /** Letztes vom Server gemeldetes Storage-Limit. Wird offline weitergeführt. */
    val downloadsLimitBytes: Flow<Long> = ctx.dataStore.data.map {
        it[DOWNLOADS_LIMIT_BYTES_KEY] ?: 0L
    }

    /**
     * Optionaler Bearer-Token. Wird bei jeder Anfrage mitgeschickt, wenn der
     * Server HIKARI_AUTH_TOKEN gesetzt hat. Leer = kein Token (localhost-Default).
     */
    val authToken: Flow<String> = ctx.dataStore.data.map {
        it[AUTH_TOKEN_KEY].orEmpty()
    }

    /** Musik-Vorschläge auf Instrumentalstücke beschränken. */
    val instrumentalOnly: Flow<Boolean> = ctx.dataStore.data.map {
        it[INSTRUMENTAL_ONLY_KEY] ?: false
    }

    /** Täglicher News-Tagesbericht: Push-Benachrichtigung aktiv. */
    val newsEnabled: Flow<Boolean> = ctx.dataStore.data.map {
        it[NEWS_ENABLED_KEY] ?: false
    }

    /** Uhrzeit des Tagesberichts in Minuten seit Mitternacht (Default 7:30). */
    val newsTimeMinutes: Flow<Int> = ctx.dataStore.data.map {
        it[NEWS_TIME_MINUTES_KEY] ?: DEFAULT_NEWS_TIME_MINUTES
    }

    /** Gewählte News-Themen — gespeichert als kommagetrennter String. */
    val newsTopics: Flow<Set<String>> = ctx.dataStore.data.map {
        (it[NEWS_TOPICS_KEY] ?: DEFAULT_NEWS_TOPICS)
            .split(",")
            .map { t -> t.trim() }
            .filter { t -> t.isNotEmpty() }
            .toSet()
    }

    /** Standort für lokale Nachrichten + Sprache ableiten. */
    val newsLocationEnabled: Flow<Boolean> = ctx.dataStore.data.map {
        it[NEWS_LOCATION_ENABLED_KEY] ?: false
    }

    /** Zuletzt ermittelte (oder manuell gesetzte) Stadt für lokale Nachrichten. */
    val newsCity: Flow<String> = ctx.dataStore.data.map {
        it[NEWS_CITY_KEY].orEmpty()
    }

    /** Sprache des Tagesberichts ("de" | "en"). */
    val newsLang: Flow<String> = ctx.dataStore.data.map {
        it[NEWS_LANG_KEY] ?: "de"
    }

    suspend fun setNewsEnabled(enabled: Boolean) {
        ctx.dataStore.edit { it[NEWS_ENABLED_KEY] = enabled }
    }

    suspend fun setNewsTimeMinutes(minutes: Int) {
        ctx.dataStore.edit { it[NEWS_TIME_MINUTES_KEY] = minutes.coerceIn(0, 1439) }
    }

    suspend fun setNewsTopics(topics: Set<String>) {
        ctx.dataStore.edit {
            it[NEWS_TOPICS_KEY] = topics
                .map { t -> t.trim().lowercase() }
                .filter { t -> t.isNotEmpty() }
                .joinToString(",")
        }
    }

    suspend fun setNewsLocationEnabled(enabled: Boolean) {
        ctx.dataStore.edit { it[NEWS_LOCATION_ENABLED_KEY] = enabled }
    }

    suspend fun setNewsCity(city: String) {
        ctx.dataStore.edit { it[NEWS_CITY_KEY] = city.trim() }
    }

    suspend fun setNewsLang(lang: String) {
        ctx.dataStore.edit { it[NEWS_LANG_KEY] = lang }
    }

    suspend fun setInstrumentalOnly(enabled: Boolean) {
        ctx.dataStore.edit { it[INSTRUMENTAL_ONLY_KEY] = enabled }
    }

    suspend fun setBackendUrl(url: String) {
        ctx.dataStore.edit { it[BACKEND_URL_KEY] = url.trimEnd('/') }
    }

    suspend fun setDailyBudget(value: Int) {
        ctx.dataStore.edit { it[DAILY_BUDGET_KEY] = value.coerceIn(1, 100) }
    }

    suspend fun setSmartDownloads(enabled: Boolean) {
        ctx.dataStore.edit { it[SMART_DOWNLOADS_KEY] = enabled }
    }

    suspend fun setDownloadsLimitBytes(bytes: Long) {
        ctx.dataStore.edit { it[DOWNLOADS_LIMIT_BYTES_KEY] = bytes }
    }

    suspend fun setAuthToken(token: String) {
        ctx.dataStore.edit {
            val t = token.trim()
            if (t.isEmpty()) it.remove(AUTH_TOKEN_KEY) else it[AUTH_TOKEN_KEY] = t
        }
    }
}
