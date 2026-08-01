package com.hikari.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
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

@HiltAndroidApp
class HikariApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scheduler: SmartDownloadScheduler
    @Inject lateinit var newsScheduler: NewsBriefingScheduler
    @Inject lateinit var settings: SettingsStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
    }
}
