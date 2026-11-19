package com.hikari.app.ui.fitness

import android.content.Context
import android.media.AudioAttributes as SysAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.hikari.app.player.MusicPlayerController
import com.hikari.app.player.PlayerTrace
import java.io.File
import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Klangbett unter geführten Einheiten (Meditation, Yoga, Atem): eine lange, nahtlos geschnittene
 * Musikdatei vom Server, einmal geladen und dann auf dem Gerät behalten (filesDir/mind-sounds).
 *
 * - Eigener ExoPlayer, der selbst KEINEN Audio-Fokus verwaltet — sonst würde er sich bei jeder
 *   Ansage von Rem (kurzer Fokus zum Leiserdrehen) selbst pausieren. Stattdessen hält dieses Objekt
 *   einen dauerhaften Fokus, solange das Bett läuft: Musik anderer Apps hält dann an.
 * - Läuft Hikaris eigene Musik, wird sie für die Einheit angehalten und danach fortgesetzt.
 * - Lautstärke: Grundwert (Regler in der App) × 75 % (≈ −5 dB), während Rem spricht (duck) — weich geblendet.
 *
 * Alle Methoden auf dem Hauptthread aufrufen.
 */
class MindAmbient(
    private val app: Context,
    private val okHttp: OkHttpClient,
    private val music: MusicPlayerController?,
    private val onState: (state: String, id: String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val dir = File(app.filesDir, "mind-sounds").apply { mkdirs() }
    private val audio by lazy { app.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private var player: ExoPlayer? = null
    private var want: String? = null // "<id>-<v>" — was gerade laufen soll
    private var download: Call? = null
    private var base = 0.5f
    private var ducked = false
    private var paused = false
    private var level = 0f
    private var ramp: Runnable? = null
    private var focus: AudioFocusRequest? = null
    private var pausedOwnMusic = false

    private var loopFrom = 0L
    private var loopTo = C.TIME_END_OF_SOURCE

    /** loopFromMs/loopToMs: der nahtlose Bereich der Datei (Polster an den Rändern bleibt draußen). */
    fun play(id: String, ver: String, volume: Float, loopFromMs: Long = 0, loopToMs: Long = 0) {
        loopFrom = loopFromMs.coerceAtLeast(0)
        loopTo = if (loopToMs > loopFrom) loopToMs else C.TIME_END_OF_SOURCE
        val key = "$id-$ver"
        base = volume.coerceIn(0f, 1f)
        if (key == want && player != null) { paused = false; player?.play(); fadeTo(target(), 1500); return }
        stopPlayer(0)
        want = key
        paused = false
        val file = File(dir, "$key.ogg")
        if (file.exists()) { start(file, key); return }
        onState("laden", id)
        download?.cancel()
        val tmp = File(dir, "$key.part")
        download = okHttp.newCall(Request.Builder().url("http://hikari.invalid/fitness/mind/sound/$id").build()).also {
            it.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { main.post { if (want == key) onState("fehler", id) } }
                override fun onResponse(call: Call, response: Response) {
                    val ok = response.use { r ->
                        r.isSuccessful && runCatching { r.body!!.byteStream().use { inp -> tmp.outputStream().use { inp.copyTo(it) } }; true }.getOrDefault(false)
                    }
                    if (ok && tmp.renameTo(file)) {
                        // Ältere Fassungen desselben Betts aufräumen.
                        dir.listFiles()?.filter { f -> f.name.startsWith("$id-") && f.name != file.name }?.forEach { f -> f.delete() }
                        main.post { if (want == key) start(file, key) }
                    } else {
                        tmp.delete()
                        main.post { if (want == key) onState("fehler", id) }
                    }
                }
            })
        }
    }

    private fun start(file: File, key: String) {
        holdFocus(true)
        val own = music
        if (own != null && own.isPlaying.value) { own.toggle(); pausedOwnMusic = true; PlayerTrace.log("achtsam: eigene Musik angehalten (Klangbett)") }
        val p = ExoPlayer.Builder(app).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            setMediaItem(
                MediaItem.Builder().setUri(Uri.fromFile(file))
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(loopFrom).setEndPositionMs(loopTo).build())
                    .build(),
            )
            prepare()
            // Jedes Mal an einer anderen Stelle einsteigen — das Bett klingt so nicht immer gleich.
            seekTo((Math.random() * 12 * 60_000).toLong())
            play()
        }
        player = p
        level = 0f
        fadeTo(target(), 5000)
        onState("spielt", key.substringBeforeLast('-'))
    }

    fun setVolume(v: Float) { base = v.coerceIn(0f, 1f); fadeTo(target(), 300) }

    fun duck(on: Boolean) {
        if (ducked == on) return
        ducked = on
        fadeTo(target(), if (on) 400 else 1400)
    }

    fun pause(on: Boolean) {
        paused = on
        val p = player ?: return
        if (on) fadeTo(0f, 1200) { p.pause() } else { p.play(); fadeTo(target(), 1500) }
    }

    /** Ausblenden und anhalten; eigene Musik danach wieder an. */
    fun stop(fadeMs: Long = 3000) {
        want = null
        download?.cancel()
        download = null
        stopPlayer(fadeMs)
    }

    private fun stopPlayer(fadeMs: Long) {
        val p = player ?: return
        player = null
        val done = {
            p.release()
            if (player == null) {
                holdFocus(false)
                if (pausedOwnMusic) {
                    pausedOwnMusic = false
                    music?.let { if (!it.isPlaying.value) it.toggle() }
                    PlayerTrace.log("achtsam: eigene Musik geht weiter")
                }
            }
        }
        if (fadeMs <= 0) { ramp?.let(main::removeCallbacks); done(); return }
        rampOn(p, level, 0f, fadeMs, done)
    }

    private fun target() = if (paused) 0f else base * (if (ducked) 0.75f else 1f)

    private fun fadeTo(to: Float, ms: Long, then: (() -> Unit)? = null) {
        val p = player ?: return
        rampOn(p, level, to, ms) { then?.invoke() }
    }

    /**
     * Lautstärke in kleinen Schritten führen (quadratisch — klingt gleichmäßiger als linear).
     * Nur die Rampe des laufenden Players wird ersetzt; das Ausblenden eines abgelösten Players
     * läuft unabhängig zu Ende (sonst bliebe er bei einem schnellen Bettwechsel hängen).
     */
    private fun rampOn(p: ExoPlayer, from: Float, to: Float, ms: Long, then: () -> Unit) {
        val own = p === player
        if (own) ramp?.let(main::removeCallbacks)
        val steps = (ms / 40).coerceAtLeast(1)
        var k = 0L
        val r = object : Runnable {
            override fun run() {
                k++
                val v = (from + (to - from) * (k.toFloat() / steps)).coerceIn(0f, 1f)
                if (p === player) level = v
                runCatching { p.volume = v * v }
                if (k < steps) main.postDelayed(this, 40) else { if (ramp === this) ramp = null; then() }
            }
        }
        if (own) ramp = r
        main.post(r)
    }

    private fun holdFocus(on: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (on) {
            if (focus != null) return
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(SysAudioAttributes.Builder().setUsage(SysAudioAttributes.USAGE_MEDIA).setContentType(SysAudioAttributes.CONTENT_TYPE_MUSIC).build())
                // Rems kurze Ansagen holen sich Fokus zum Leiserdrehen — das ignorieren wir bewusst.
                .setOnAudioFocusChangeListener { }
                .build()
            audio.requestAudioFocus(req)
            focus = req
        } else {
            focus?.let { audio.abandonAudioFocusRequest(it) }
            focus = null
        }
    }

    fun release() {
        stop(0)
    }
}
