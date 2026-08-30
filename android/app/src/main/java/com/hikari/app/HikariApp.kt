package com.hikari.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.Coil
import coil.ImageLoader
import coil.map.Mapper
import com.hikari.app.data.prefs.DEFAULT_BACKEND_URL
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.download.SmartDownloadScheduler
import com.hikari.app.domain.news.NewsBriefingScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@HiltAndroidApp
class HikariApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scheduler: SmartDownloadScheduler
    @Inject lateinit var newsScheduler: NewsBriefingScheduler
    @Inject lateinit var settings: SettingsStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Gespiegelte Einstellungen für den ImageLoader — gleiches Muster wie in
    // NetworkModule: einmal sammeln, dann lesen Interceptor und Mapper nur
    // noch die volatile Kopie, statt bei jedem Bild den DataStore zu fragen.
    @Volatile private var imageBackendUrl: String = DEFAULT_BACKEND_URL
    @Volatile private var imageAuthToken: String = ""

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Always schedule the periodic Smart-Downloads job. The worker itself
        // checks the user's preference each fire and short-circuits if off.
        scheduler.schedulePeriodicSync()
        // News-Tagesbericht nur einplanen, wenn er aktiviert ist — nach einem
        // App-Update oder Neustart stellt das den Zeitplan wieder her.
        appScope.launch {
            if (runCatching { settings.newsEnabled.first() }.getOrDefault(false)) {
                newsScheduler.schedule(settings.newsTimeMinutes.first())
            }
        }
        appScope.launch { settings.backendUrl.collect { imageBackendUrl = it } }
        appScope.launch { settings.authToken.collect { imageAuthToken = it } }
        Coil.setImageLoader(buildImageLoader())
    }

    /**
     * Zentraler ImageLoader für die ganze App.
     *
     * Das Backend liefert bei Importen teils relative Thumbnail-Pfade wie
     * "/covers/vid_123.jpg" — der Mapper prefixt sie auf die aktuell
     * eingestellte Backend-URL. Absolute URLs (http/https) bleiben unberührt,
     * und das Auth-Token geht nur ans eigene Backend, nie an fremde Bild-Hosts.
     */
    private fun buildImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val token = imageAuthToken
                if (token.isNotEmpty() && req.url.toString().startsWith(imageBackendUrl)) {
                    chain.proceed(
                        req.newBuilder().header("Authorization", "Bearer $token").build(),
                    )
                } else {
                    chain.proceed(req)
                }
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .components {
                add(
                    Mapper<String, String> { data, _ ->
                        if (data.startsWith("/")) imageBackendUrl.trimEnd('/') + data else null
                    },
                )
            }
            .build()
    }
}
