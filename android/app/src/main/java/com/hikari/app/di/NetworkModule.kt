package com.hikari.app.di

import com.hikari.app.BuildConfig
import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.prefs.DEFAULT_BACKEND_URL
import com.hikari.app.data.prefs.SettingsStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Gespiegelte Einstellungen für die Interceptoren. Früher las jeder
     * Request sie per runBlocking aus dem DataStore — das blockierte den
     * Netzwerk-Thread bei jedem einzelnen Call. Jetzt werden sie einmalig
     * gesammelt und bei Änderung nachgezogen; die Interceptoren lesen nur
     * noch die volatile Kopie.
     */
    @Volatile private var cachedBackendUrl: String = DEFAULT_BACKEND_URL
    @Volatile private var cachedAuthToken: String = ""

    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides @Singleton
    fun provideOkHttpClient(store: SettingsStore): OkHttpClient {
        settingsScope.launch { store.backendUrl.collect { cachedBackendUrl = it } }
        settingsScope.launch { store.authToken.collect { cachedAuthToken = it } }
        val baseUrlInterceptor = Interceptor { chain ->
            val base = cachedBackendUrl.toHttpUrlOrNull()
                ?: return@Interceptor chain.proceed(chain.request())
            val orig = chain.request()
            val newUrl = orig.url.newBuilder()
                .scheme(base.scheme)
                .host(base.host)
                .port(base.port)
                .build()
            chain.proceed(orig.newBuilder().url(newUrl).build())
        }
        // Attach the bearer token when the user configured one (backend with
        // HIKARI_AUTH_TOKEN enabled). Empty token → request passes through, so
        // the open localhost default is unaffected.
        val authInterceptor = Interceptor { chain ->
            val token = cachedAuthToken
            if (token.isEmpty()) {
                chain.proceed(chain.request())
            } else {
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build(),
                )
            }
        }
        // Request-Logging nur im Debug-Build — Release bleibt still.
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)   // no overall timeout — streaming calls run until done
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        // Nur Platzhalter — der baseUrlInterceptor schreibt jeden Request auf
        // die aktuell eingestellte Backend-URL um.
        val initialBase = cachedBackendUrl.let { if (it.endsWith("/")) it else "$it/" }
        return Retrofit.Builder()
            .baseUrl(initialBase)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides @Singleton
    fun provideHikariApi(retrofit: Retrofit): HikariApi = retrofit.create(HikariApi::class.java)
}
