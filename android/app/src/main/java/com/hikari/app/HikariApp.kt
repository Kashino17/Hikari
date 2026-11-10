package com.hikari.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.Coil
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.map.Mapper
import coil.request.ErrorResult
import coil.request.ImageResult
import com.hikari.app.data.prefs.DEFAULT_BACKEND_URL
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.data.net.ConnectivityObserver
import com.hikari.app.domain.download.OfflineArtworkStore
import com.hikari.app.domain.download.OfflineArtworkSync
import com.hikari.app.domain.download.SmartDownloadScheduler
import com.hikari.app.domain.news.NewsBriefingScheduler
import com.hikari.app.domain.sync.ProgressSyncScheduler
import com.hikari.app.whatsapp.WhatsAppNotificationListenerService
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
    @Inject lateinit var podcastScheduler: com.hikari.app.domain.russian.RussianPodcastReminderScheduler
    @Inject lateinit var progressSyncScheduler: ProgressSyncScheduler
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var retentionCleaner: dagger.Lazy<com.hikari.app.domain.download.DownloadRetentionCleaner>
    @Inject lateinit var artworkStore: OfflineArtworkStore
    @Inject lateinit var artworkSync: dagger.Lazy<OfflineArtworkSync>
    @Inject lateinit var connectivity: ConnectivityObserver

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
        WhatsAppNotificationListenerService.ensureServiceBound(this)
        // Always schedule the periodic Smart-Downloads job. The worker itself
        // checks the user's preference each fire and short-circuits if off.
        scheduler.schedulePeriodicSync()
        // Offline gesehener Fortschritt zieht nach, sobald wieder Netz da ist.
        progressSyncScheduler.schedulePeriodic()
        // News-Tagesbericht nur einplanen, wenn er aktiviert ist — nach einem
        // App-Update oder Neustart stellt das den Zeitplan wieder her.
        appScope.launch {
            if (runCatching { settings.newsEnabled.first() }.getOrDefault(false)) {
                newsScheduler.schedule(settings.newsTimeMinutes.first())
            }
        }
        appScope.launch {
            if (runCatching { settings.russianPodcastReminderEnabled.first() }.getOrDefault(false)) {
                podcastScheduler.schedule(settings.russianPodcastReminderMinutes.first())
            }
        }
        // Abgelaufene Downloads gleich beim Start vom Gerät räumen.
        appScope.launch { runCatching { retentionCleaner.get().run() } }
        appScope.launch { settings.backendUrl.collect { imageBackendUrl = it } }
        appScope.launch { settings.authToken.collect { imageAuthToken = it } }
        Coil.setImageLoader(buildImageLoader())
        // Bilder der Downloads aufs Gerät sichern — erst nach dem ImageLoader,
        // denn über ihn werden sie geholt.
        artworkSync.get().start()
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
                add(OfflineArtworkInterceptor())
                add(
                    Mapper<String, String> { data, _ ->
                        if (data.startsWith("/")) imageBackendUrl.trimEnd('/') + data else null
                    },
                )
            }
            .build()
    }

    /**
     * Zeigt das aufs Gerät gesicherte Bild, wenn das Netz keines liefert.
     *
     * Sitzt vor allem anderen im ImageLoader — dadurch gilt es für jedes Bild
     * der App, ohne dass eine einzige Ansicht davon wissen muss. Online bleibt
     * alles beim Alten (das Bild vom Server gewinnt und ist damit immer
     * aktuell); nur wenn das scheitert oder gar kein Netz da ist, springt die
     * gesicherte Datei ein.
     */
    private inner class OfflineArtworkInterceptor : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val request = chain.request
            val local = (request.data as? String)?.let(artworkStore::localFile)
                ?: return chain.proceed(request)
            val fromFile = request.newBuilder().data(local).build()
            if (artworkStore.preferLocal || !connectivity.currentlyOnline()) return chain.proceed(fromFile)
            val result = chain.proceed(request)
            return if (result is ErrorResult) chain.proceed(fromFile) else result
        }
    }
}
