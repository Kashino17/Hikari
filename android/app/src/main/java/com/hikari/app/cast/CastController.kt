package com.hikari.app.cast

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext

/** Ein im WLAN gefundener Empfänger (Chromecast, Smart-TV mit Cast). */
data class CastTarget(val id: String, val name: String, val connecting: Boolean)

/**
 * Hält Suche, Verbindung und den [CastPlayer] für den Videoplayer.
 * Alles läuft auf dem Main-Thread. Ohne Google Play Services bleibt
 * [supported] false und die UI blendet Cast einfach aus.
 */
@UnstableApi
class CastController(context: Context) {
    private val appContext = context.applicationContext
    private val castContext: CastContext? = runCatching { CastContext.getSharedInstance(appContext) }.getOrNull()
    private val router: MediaRouter? = castContext?.let { runCatching { MediaRouter.getInstance(appContext) }.getOrNull() }

    val supported: Boolean get() = castContext != null && router != null

    var targets by mutableStateOf<List<CastTarget>>(emptyList())
        private set
    var casting by mutableStateOf(false)
        private set
    var deviceName by mutableStateOf<String?>(null)
        private set

    private var player: CastPlayer? = null
    private var searching = false

    private val callback = object : MediaRouter.Callback() {
        override fun onRouteAdded(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteRemoved(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteChanged(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
    }

    private val availability = object : SessionAvailabilityListener {
        override fun onCastSessionAvailable() {
            deviceName = castContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName
            casting = true
        }

        override fun onCastSessionUnavailable() {
            casting = false
            deviceName = null
        }
    }

    init {
        if (supported) {
            player = CastPlayer(castContext!!).also {
                it.setSessionAvailabilityListener(availability)
                if (it.isCastSessionAvailable) availability.onCastSessionAvailable()
            }
        }
    }

    /** Das Wiedergabe-Objekt für den Fernseher; null, solange keiner verbunden ist. */
    val activePlayer: CastPlayer? get() = player?.takeIf { casting }

    private fun refresh() {
        val r = router ?: return
        val selector = castContext?.mergedSelector ?: return
        targets = r.routes
            .filter { it.matchesSelector(selector) && !it.isDefaultOrBluetooth }
            .map { CastTarget(it.id, it.name, it.isConnecting) }
    }

    /** Sucht aktiv nach Geräten — nur solange die Auswahl offen ist (spart Akku). */
    fun startSearch() {
        val r = router ?: return
        val selector = castContext?.mergedSelector ?: return
        if (searching) return
        searching = true
        r.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        refresh()
    }

    fun stopSearch() {
        if (!searching) return
        searching = false
        router?.removeCallback(callback)
    }

    fun connect(target: CastTarget) {
        val r = router ?: return
        r.routes.firstOrNull { it.id == target.id }?.let { r.selectRoute(it) }
    }

    fun disconnect() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    /** Schickt das Video samt Titel und Cover an den Fernseher. */
    fun load(url: String, title: String, subtitle: String?, thumbnailUrl: String?, positionMs: Long) {
        val p = activePlayer ?: return
        val item = MediaItem.Builder()
            .setUri(url)
            .setMimeType(MimeTypes.VIDEO_MP4)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtworkUri(thumbnailUrl?.takeIf { it.startsWith("http") }?.let(android.net.Uri::parse))
                    .build(),
            )
            .build()
        p.setMediaItem(item, positionMs.coerceAtLeast(0L))
        p.prepare()
        p.play()
    }

    fun release() {
        stopSearch()
        player?.setSessionAvailabilityListener(null)
        // Die Sitzung bleibt bestehen: der Fernseher spielt weiter, auch wenn
        // man den Player in der App verlässt.
        player?.release()
        player = null
    }
}
