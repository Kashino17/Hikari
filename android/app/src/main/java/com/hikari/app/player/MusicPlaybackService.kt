package com.hikari.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.hikari.app.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Stellt die Musikwiedergabe dem System bereit: Sperrbildschirm-Widget,
 * Benachrichtigungs-Controls, Kopfhörer-/Bluetooth-Tasten. Teilt sich die
 * ExoPlayer-Instanz des [MusicPlayerController] — die Queue mit Autoplay,
 * Shuffle und Fehler-Skip bleibt dort, next/previous aus dem System werden
 * deshalb an den Controller weitergereicht statt an den Player.
 */
@AndroidEntryPoint
class MusicPlaybackService : MediaSessionService() {

    @Inject
    lateinit var controller: MusicPlayerController

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val forwarding = object : ForwardingPlayer(controller.playerForSession()) {
            override fun isCommandAvailable(command: Int): Boolean =
                command == Player.COMMAND_SEEK_TO_NEXT ||
                    command == Player.COMMAND_SEEK_TO_PREVIOUS ||
                    super.isCommandAvailable(command)

            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .build()

            override fun seekToNext() = controller.next()

            override fun seekToPrevious() = controller.previous()
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, forwarding)
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App weggewischt: Wiedergabe immer komplett beenden — ohne App soll
        // kein Hintergrund-Player weiterlaufen (expliziter Wunsch).
        controller.stop()
        stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
