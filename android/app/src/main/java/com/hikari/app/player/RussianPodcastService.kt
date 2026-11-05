package com.hikari.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.hikari.app.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Hält den Russisch-Podcast im Hintergrund am Leben: Sperrbildschirm,
 * Benachrichtigung, Kopfhörer-/Bluetooth-Tasten. Eigene Session-ID, damit
 * sie neben der Musik-Session bestehen kann.
 */
@AndroidEntryPoint
class RussianPodcastService : MediaSessionService() {

    @Inject
    lateinit var podcast: RussianPodcastPlayer

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val openApp = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_to", "russian/podcast")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, podcast.playerForSession())
            .setId("russian-podcast")
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App weggewischt: wie bei der Musik komplett beenden, Stand ist gesichert.
        podcast.close()
        stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
