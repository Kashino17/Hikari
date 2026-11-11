package com.hikari.app.cast

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.cast.CastKind
import com.hikari.app.domain.cast.CastMedia
import com.hikari.app.domain.cast.CastRemote
import com.hikari.app.domain.repo.PlaybackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Ein Fernseher/Chromecast im WLAN. */
data class CastTarget(val id: String, val name: String, val connecting: Boolean)

/** Der Musikplayer behält seine Warteschlange — der Fernseher meldet ihm nur, was passiert. */
interface CastMusicHandler {
    fun next()
    fun previous()

    /** Das Lied ist auf dem Fernseher zu Ende gelaufen. */
    fun ended()

    /** Der Fernseher konnte das Lied nicht laden. */
    fun failed()
}

/**
 * Die Übertragung auf den Fernseher, für die ganze App: Suche, Verbindung, was gerade läuft
 * und die Fernbedienung dazu. Lebt so lange wie die App — deshalb bleibt die Leiste stehen,
 * wenn man den Player verlässt, und Videos wie Musik teilen sich dieselbe Steuerung.
 *
 * Alles läuft auf dem Main-Thread. Ohne Google Play Services bleibt [supported] false und
 * die Oberfläche blendet Cast einfach aus.
 */
@Singleton
@androidx.annotation.OptIn(UnstableApi::class)
class CastController @Inject constructor(
    @ApplicationContext context: Context,
    private val api: HikariApi,
    private val settings: SettingsStore,
    private val playback: PlaybackRepository,
    private val okHttp: OkHttpClient,
) {
    private val appContext = context.applicationContext
    private val castContext: CastContext? = runCatching { CastContext.getSharedInstance(appContext) }.getOrNull()
    private val router: MediaRouter? = castContext?.let { runCatching { MediaRouter.getInstance(appContext) }.getOrNull() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val supported: Boolean get() = castContext != null && router != null

    var targets by mutableStateOf<List<CastTarget>>(emptyList())
        private set
    var casting by mutableStateOf(false)
        private set
    var deviceName by mutableStateOf<String?>(null)
        private set

    /** Was auf dem Fernseher läuft; null, solange verbunden ist, aber noch nichts geschickt wurde. */
    var media by mutableStateOf<CastMedia?>(null)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var playing by mutableStateOf(false)
        private set

    /** Der Fernseher lädt noch (oder puffert nach). */
    var loading by mutableStateOf(false)
        private set
    var ended by mutableStateOf(false)
        private set

    /** Hinweis für die Steuerung, z. B. wenn der Server im WLAN nicht zu finden war. */
    var warning by mutableStateOf<String?>(null)
        private set

    private var player: CastPlayer? = null
    private var searching = false
    private var musicHandler: CastMusicHandler? = null

    private var loadJob: Job? = null
    private var detailJob: Job? = null
    private var ticker: Job? = null

    /** Adresse, die zuletzt an den Fernseher ging — daran wird »unser« Stück erkannt. */
    private var sentUrl: String? = null
    private var sentAt = 0L

    /** Der Fernseher hat das aktuelle Stück tatsächlich angespielt. */
    private var started = false
    private var finishHandled = false
    private var lastSaveAt = 0L
    private var replay: (() -> Unit)? = null

    private val routes = object : MediaRouter.Callback() {
        override fun onRouteAdded(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteRemoved(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteChanged(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
    }

    private val availability = object : SessionAvailabilityListener {
        override fun onCastSessionAvailable() {
            deviceName = castContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName
            casting = true
            startTicker()
        }

        override fun onCastSessionUnavailable() {
            saveNow()
            loadJob?.cancel()
            detailJob?.cancel()
            ticker?.cancel()
            casting = false
            deviceName = null
            media = null
            playing = false
            loading = false
            ended = false
            warning = null
            sentUrl = null
            started = false
            replay = null
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()
    }

    init {
        if (supported) {
            player = CastPlayer(castContext!!).also {
                it.addListener(playerListener)
                it.setSessionAvailabilityListener(availability)
                if (it.isCastSessionAvailable) availability.onCastSessionAvailable()
            }
        }
    }

    private val activePlayer: CastPlayer? get() = player?.takeIf { casting }

    // ── Geräte ───────────────────────────────────────────────────────────────

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
        r.addCallback(selector, routes, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        refresh()
    }

    fun stopSearch() {
        if (!searching) return
        searching = false
        router?.removeCallback(routes)
    }

    fun connect(target: CastTarget) {
        val r = router ?: return
        r.routes.firstOrNull { it.id == target.id }?.let { r.selectRoute(it) }
    }

    /** Beendet die Übertragung; der Fernseher hört auf zu spielen. */
    fun disconnect() {
        saveNow()
        castContext?.sessionManager?.endCurrentSession(true)
    }

    // ── Abspielen ────────────────────────────────────────────────────────────

    /**
     * Schickt ein Video an den Fernseher. Ohne [startMs] geht es an der gemerkten Stelle weiter.
     * Titel, Folge und Vorschaubild kommen vom Server; [title] und [channel] überbrücken, bis sie da sind.
     */
    fun playVideo(videoId: String, title: String, channel: String, startMs: Long? = null) {
        val p = activePlayer ?: return
        val owner = channel.takeIf { it.isNotBlank() }
        begin(CastMedia(CastKind.VIDEO, videoId, title, owner = owner), startMs ?: 0L)
        replay = { playVideo(videoId, title, channel, 0L) }
        loadJob = scope.launch {
            val backend = settings.backendUrl.first().trimEnd('/')
            val detail = runCatching { api.getVideo(videoId) }.getOrNull()
            val base = tvBase(backend)
            val length = (detail?.duration_seconds ?: 0) * 1000L
            val start = startMs ?: CastRemote.resumePoint(playback.getPosition(videoId), length)
            val shown = CastMedia(
                kind = CastKind.VIDEO,
                id = videoId,
                title = title.ifBlank { detail?.title.orEmpty() },
                owner = detail?.series_title?.takeIf { it.isNotBlank() } ?: owner,
                episode = CastRemote.episodeLabel(detail?.season, detail?.episode),
                thumbnailUrl = absolute(backend, detail?.thumbnail_url),
            )
            media = shown
            positionMs = start
            if (length > 0L) durationMs = length
            send(p, "$base/stream/video/$videoId", MimeTypes.VIDEO_MP4, shown, absolute(base, detail?.thumbnail_url), start)
        }
    }

    /** Schickt ein Lied an den Fernseher. Die Warteschlange bleibt beim Musikplayer. */
    fun playMusic(songId: String, title: String, artist: String, thumbnailUrl: String?, startMs: Long = 0L) {
        val p = activePlayer ?: return
        val shown = CastMedia(
            CastKind.MUSIC, songId, title,
            owner = artist.takeIf { it.isNotBlank() },
            thumbnailUrl = thumbnailUrl?.takeIf { it.startsWith("http") },
        )
        begin(shown, startMs)
        replay = { playMusic(songId, title, artist, thumbnailUrl, 0L) }
        loadJob = scope.launch {
            val backend = settings.backendUrl.first().trimEnd('/')
            val base = tvBase(backend)
            // Der Server löst das Lied erst beim Abruf auf. Der Probeabruf wärmt das vor und
            // verrät das Format — und zeigt gleich, wenn der Server gerade nicht liefern kann.
            val probe = probe("$backend/music/audio/$songId")
            if (probe.status != 0 && probe.status !in 200..299) {
                fail()
                return@launch
            }
            send(p, "$base/music/audio/$songId", probe.mime ?: "audio/mp4", shown, shown.thumbnailUrl, startMs)
        }
    }

    private fun begin(next: CastMedia, startMs: Long) {
        saveNow()
        loadJob?.cancel()
        detailJob?.cancel()
        media = next
        positionMs = startMs.coerceAtLeast(0L)
        durationMs = 0L
        playing = false
        loading = true
        ended = false
        warning = null
        sentUrl = null
        started = false
        finishHandled = false
    }

    private fun send(p: CastPlayer, url: String, mime: String, shown: CastMedia, artwork: String?, startMs: Long) {
        val item = MediaItem.Builder()
            .setUri(url)
            .setMimeType(mime)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(shown.title)
                    .setSubtitle(CastRemote.remoteLine(shown))
                    .setArtist(shown.owner.takeIf { shown.kind == CastKind.MUSIC })
                    .setArtworkUri(artwork?.takeIf { it.startsWith("http") }?.let(Uri::parse))
                    .build(),
            )
            .build()
        sentUrl = url
        sentAt = System.currentTimeMillis()
        p.setMediaItem(item, startMs.coerceAtLeast(0L))
        p.prepare()
        p.play()
    }

    /** Adresse des Servers, die der Fernseher erreicht: die im WLAN, sonst die eingestellte. */
    private suspend fun tvBase(backend: String): String {
        val lan = runCatching { api.castInfo().lanBaseUrls.firstOrNull() }.getOrNull()?.trimEnd('/')
        if (lan == null && !CastRemote.isLanHost(runCatching { Uri.parse(backend).host }.getOrNull().orEmpty())) {
            warning = "Server im WLAN nicht gefunden — der Fernseher kommt evtl. nicht ran"
        }
        return lan ?: backend
    }

    private fun absolute(base: String, path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("http")) it else base + it }

    private class Probe(val status: Int, val mime: String?)

    /** Holt die ersten Bytes — Status 0 heißt: der Abruf selbst kam nicht zustande. */
    private suspend fun probe(url: String): Probe = withContext(Dispatchers.IO) {
        runCatching {
            val client = okHttp.newBuilder().callTimeout(25, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url(url).header("Range", "bytes=0-1").build()).execute().use { res ->
                Probe(res.code, res.header("Content-Type")?.substringBefore(';')?.trim()?.takeIf { it.isNotBlank() })
            }
        }.getOrElse { Probe(0, null) }
    }

    // ── Fernbedienung ────────────────────────────────────────────────────────

    fun togglePlay() {
        val p = activePlayer ?: return
        if (ended) {
            replay?.invoke()
            return
        }
        if (p.isPlaying) p.pause() else p.play()
        sync()
    }

    fun seekTo(ms: Long) {
        val target = if (durationMs > 0L) ms.coerceIn(0L, durationMs) else ms.coerceAtLeast(0L)
        activePlayer?.seekTo(target)
        positionMs = target
    }

    fun seekBy(deltaMs: Long) = seekTo(CastRemote.seekTarget(positionMs, deltaMs, durationMs))

    /** Der Musikplayer meldet sich an, damit »voriges/nächstes Lied« seine Warteschlange bewegt. */
    fun attachMusic(handler: CastMusicHandler) {
        musicHandler = handler
    }

    fun nextSong() {
        musicHandler?.next()
    }

    fun previousSong() {
        musicHandler?.previous()
    }

    // ── Abgleich mit dem Fernseher ───────────────────────────────────────────

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                sync()
                delay(TICK_MS)
            }
        }
    }

    private fun sync() {
        val p = activePlayer ?: return
        val current = media
        if (current == null) {
            if (loadJob?.isActive != true) restore(p)
            return
        }
        if (loadJob?.isActive == true) return // Adresse wird noch besorgt
        val url = sentUrl ?: return
        val state = p.playbackState
        val mine = p.currentMediaItem?.localConfiguration?.uri?.toString() == url
        if (mine && (state == Player.STATE_READY || state == Player.STATE_BUFFERING)) {
            if (state == Player.STATE_READY) started = true
            if (!started) return
            val wasPlaying = playing
            loading = state == Player.STATE_BUFFERING
            playing = p.isPlaying
            positionMs = p.currentPosition.coerceAtLeast(0L)
            p.duration.takeIf { it > 0L }?.let { durationMs = it }
            val now = System.currentTimeMillis()
            // Pause ist ein natürlicher Speicherpunkt; sonst alle paar Sekunden.
            if ((wasPlaying && !playing) || (playing && now - lastSaveAt > SAVE_EVERY_MS)) saveNow()
            return
        }
        if (started) {
            if (!finishHandled) finish()
        } else if (!finishHandled && sentAt > 0L) {
            // Nie angelaufen: der Fernseher meldet einen Fehler oder bleibt stumm.
            val waited = System.currentTimeMillis() - sentAt
            val reason = remoteIdleReason()
            if ((reason == MediaStatus.IDLE_REASON_ERROR && waited > 2_000L) || waited > LOAD_TIMEOUT_MS) fail()
        }
    }

    /** Das Stück lief und ist jetzt weg: zu Ende gespielt, gescheitert oder von außen gestoppt. */
    private fun finish() {
        finishHandled = true
        playing = false
        loading = false
        val reason = remoteIdleReason()
        val interrupted = reason == MediaStatus.IDLE_REASON_ERROR ||
            reason == MediaStatus.IDLE_REASON_CANCELED ||
            reason == MediaStatus.IDLE_REASON_INTERRUPTED
        val finished = reason == MediaStatus.IDLE_REASON_FINISHED ||
            (!interrupted && durationMs > 0L && positionMs >= durationMs - 3_000L)
        when {
            finished -> {
                if (durationMs > 0L) positionMs = durationMs
                saveNow()
                ended = true
                if (media?.kind == CastKind.MUSIC) musicHandler?.ended()
            }
            reason == MediaStatus.IDLE_REASON_ERROR -> {
                saveNow()
                fail()
            }
            else -> saveNow()
        }
    }

    private fun fail() {
        finishHandled = true
        loading = false
        playing = false
        if (media?.kind == CastKind.MUSIC && musicHandler != null) {
            musicHandler?.failed()
        } else {
            warning = "Der Fernseher konnte das nicht abspielen"
        }
    }

    private fun remoteIdleReason(): Int? =
        castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.idleReason

    /**
     * Die App wurde neu gestartet, der Fernseher spielt noch: aus dem, was er meldet, wieder
     * herleiten, was läuft — damit Leiste und Steuerung sofort wieder da sind.
     */
    private fun restore(p: CastPlayer) {
        val item = p.currentMediaItem ?: return
        val url = item.localConfiguration?.uri?.toString() ?: return
        val (kind, id) = CastRemote.parseStreamUrl(url) ?: return
        val meta = item.mediaMetadata
        sentUrl = url
        sentAt = System.currentTimeMillis()
        started = false
        finishHandled = false
        media = CastMedia(
            kind = kind,
            id = id,
            title = meta.title?.toString().orEmpty(),
            owner = (meta.artist ?: meta.subtitle)?.toString()?.takeIf { it.isNotBlank() },
            thumbnailUrl = meta.artworkUri?.toString(),
        )
        if (kind != CastKind.VIDEO) return
        detailJob = scope.launch {
            val backend = settings.backendUrl.first().trimEnd('/')
            val detail = runCatching { api.getVideo(id) }.getOrNull() ?: return@launch
            if (media?.id != id) return@launch
            media = CastMedia(
                kind = CastKind.VIDEO,
                id = id,
                title = detail.title,
                owner = detail.series_title?.takeIf { it.isNotBlank() },
                episode = CastRemote.episodeLabel(detail.season, detail.episode),
                thumbnailUrl = absolute(backend, detail.thumbnail_url),
            )
        }
    }

    /** Merkt die Stelle eines Videos, damit »Weiterschauen« stimmt — auch ohne offenen Player. */
    private fun saveNow() {
        val current = media ?: return
        if (current.kind != CastKind.VIDEO || !started) return
        val pos = positionMs
        if (pos <= 0L) return
        lastSaveAt = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) { runCatching { playback.savePosition(current.id, pos) } }
    }

    private companion object {
        const val TICK_MS = 500L
        const val SAVE_EVERY_MS = 15_000L
        const val LOAD_TIMEOUT_MS = 45_000L
    }
}
