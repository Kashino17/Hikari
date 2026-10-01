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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val listeners = CopyOnWriteArraySet<Player.Listener>()
        val forwarding = object : ForwardingPlayer(controller.playerForSession()) {
            override fun addListener(listener: Player.Listener) {
                listeners.add(listener)
                super.addListener(listener)
            }

            override fun removeListener(listener: Player.Listener) {
                listeners.remove(listener)
                super.removeListener(listener)
            }

            override fun isCommandAvailable(command: Int): Boolean =
                command == Player.COMMAND_SEEK_TO_NEXT ||
                    command == Player.COMMAND_SEEK_TO_PREVIOUS ||
                    command == Player.COMMAND_SET_REPEAT_MODE ||
                    command == Player.COMMAND_SET_SHUFFLE_MODE ||
                    super.isCommandAvailable(command)

            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SET_REPEAT_MODE)
                    .add(Player.COMMAND_SET_SHUFFLE_MODE)
                    .build()

            override fun seekToNext() = controller.next()

            override fun seekToPrevious() = controller.previous()

            // Repeat-Umschaltungen aus dem System-Widget müssen durch den
            // Controller — sonst laufen ExoPlayer-Repeat-Modus und der
            // Controller-State (_repeatMode, Queue-Logik) auseinander.
            override fun setRepeatMode(repeatMode: Int) =
                controller.setRepeatModeFromSystem(repeatMode)

            override fun getRepeatMode(): Int = when (controller.repeatMode.value) {
                MusicPlayerController.REPEAT_ONE -> Player.REPEAT_MODE_ONE
                MusicPlayerController.REPEAT_ALL -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }

            override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
                if (controller.shuffle.value != shuffleModeEnabled) {
                    controller.toggleShuffle()
                }
            }

            override fun getShuffleModeEnabled(): Boolean = controller.shuffle.value
        }

        // Controller-State (Repeat / Shuffle) an registrierte Player.Listener propagieren,
        // damit Benachrichtigungsleiste, Sperrbildschirm und Bluetooth-Geräte stets den
        // synchronisierten Zustand anzeigen und Repeat/Shuffle-Buttons bedienbar bleiben.
        scope.launch {
            controller.repeatMode.collect { mode ->
                val exoMode = when (mode) {
                    MusicPlayerController.REPEAT_ONE -> Player.REPEAT_MODE_ONE
                    MusicPlayerController.REPEAT_ALL -> Player.REPEAT_MODE_ALL
                    else -> Player.REPEAT_MODE_OFF
                }
                listeners.forEach { runCatching { it.onRepeatModeChanged(exoMode) } }
            }
        }
        scope.launch {
            controller.shuffle.collect { shuffle ->
                listeners.forEach { runCatching { it.onShuffleModeEnabledChanged(shuffle) } }
            }
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
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }
}
