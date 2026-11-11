package com.hikari.app.ui.russian

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.russian.RussianPodcastReminderScheduler
import com.hikari.app.domain.russian.RussianProgressStore
import com.hikari.app.player.MusicPlayerController
import com.hikari.app.player.PodcastPlayback
import com.hikari.app.player.RussianPodcastPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.ByteArrayInputStream
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/**
 * Der Russisch-Bereich als Web-Oberfläche.
 *
 * Die Oberfläche liegt in assets/russian-web (HTML/JS ohne Build-Schritt) und wird
 * offline aus den Assets geladen. Diese Datei ist die Brücke zur App:
 *
 *  - Dateien:   https://appassets.androidplatform.net/… → Assets (Oberfläche, Kursdaten, Clips)
 *  - Server:    …/api/… → Hikari-Server über den App-eigenen OkHttp-Client (GET hier,
 *               POST über [RussianWebBridge.httpPost], weil WebView POST-Körper nicht durchreicht)
 *  - Speicher:  SharedPreferences "hikari_russian" — derselbe Lernstand wie bisher
 *  - Sprechen:  Android-Spracherkennung ([RussianSpeechRecognizer]) und -Sprachausgabe
 *  - Podcast:   der vorhandene [RussianPodcastPlayer] (Hintergrund, Sperrbildschirm)
 *
 * Zum Entwerfen ohne App-Build: tools/russian_web/server.mjs liefert dieselben Dateien an einen Browser.
 */
private const val HOST = "appassets.androidplatform.net"
private const val BASE = "https://$HOST"

/** App-Routen (Deep-Links, Feed-Karte, Benachrichtigung) → Seiten der Web-Oberfläche. */
fun russianWebRoute(route: String): String {
    val r = route.removePrefix("russian").trimStart('/', '-')
    return when {
        r.isEmpty() -> "heute"
        r == "podcast/reminder" -> "podcast"
        r == "plan" -> "plan"
        r == "phrases" -> "satzbuch"
        r == "conversation" -> "anja"
        r == "patterns" -> "patterns"
        r.startsWith("pattern/") || r.startsWith("deep/") -> r
        r.startsWith("test/") -> "test"
        else -> r // lesson/3, dialog/3, review, podcast, podcast/3, podcastPractice/3, blitz, pairs, talk, reflex, kombi, blind, sounds
    }
}

@HiltViewModel
class RussianWebViewModel @Inject constructor(
    val progress: RussianProgressStore,
    val podcast: RussianPodcastPlayer,
    val settings: SettingsStore,
    val reminder: RussianPodcastReminderScheduler,
    val okHttp: OkHttpClient,
    val music: MusicPlayerController,
) : ViewModel()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RussianWebScreen(
    startRoute: String,
    onClose: () -> Unit,
    vm: RussianWebViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bridge = remember { RussianWebBridge(context.applicationContext, vm, scope, onClose) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { bridge.onMicPermission(it) }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { bridge.onNotifyPermission(it) }
    bridge.requestMic = { micPermission.launch(Manifest.permission.RECORD_AUDIO) }
    bridge.requestNotify = {
        if (Build.VERSION.SDK_INT >= 33) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else bridge.onNotifyPermission(true)
    }

    // Musik der App pausieren — sonst läuft sie unter den Sprach-Clips weiter.
    LaunchedEffect(Unit) { if (vm.music.isPlaying.value) vm.music.toggle() }
    // Änderungen, die die App selbst macht, an die Oberfläche melden.
    LaunchedEffect(Unit) { vm.podcast.state.collect { bridge.emit("podcast", podcastJson(it)) } }
    LaunchedEffect(Unit) { vm.progress.state.collect { bridge.onProgressChanged(vm.progress.encode(it)) } }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                // Ohne eigene LayoutParams rechnet Chromium mit Viewport-Höhe 0 (siehe Podcast-Player v0.89.61).
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.parseColor("#1F1F22"))
                overScrollMode = WebView.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.textZoom = 100
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                addJavascriptInterface(bridge, "HikariNative")
                webViewClient = RussianWebClient(ctx.applicationContext, vm.okHttp)
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) {
                        // Eigene Aufnahme (Nachsprechen): nur das Mikrofon, nur für unsere Seite.
                        val wantsMic = PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources
                        if (!wantsMic || request.origin.host != HOST) { request.deny(); return }
                        bridge.withMic { ok -> if (ok) request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) else request.deny() }
                    }
                }
                bridge.webView = this
                loadUrl("$BASE/index.html#/${russianWebRoute(startRoute)}")
            }
        },
        onRelease = { it.destroy() },
    )

    // Android-Zurück: erst innerhalb der Oberfläche, auf der Startseite den Bereich schließen.
    BackHandler {
        val web = bridge.webView
        if (web == null) onClose()
        else web.evaluateJavascript("(window.__hkBack && window.__hkBack()) === true") { handled -> if (handled != "true") onClose() }
    }
    DisposableEffect(Unit) { onDispose { bridge.close() } }
}

private fun podcastJson(s: PodcastPlayback): String = JSONObject().apply {
    put("day", s.day ?: JSONObject.NULL)
    put("title", s.title)
    put("videoId", s.videoId)
    put("playing", s.playing)
    put("loading", s.loading)
    put("positionMs", s.positionMs)
    put("durationMs", s.durationMs)
    put("speed", s.speed.toDouble())
    put("resumedFromMs", s.resumedFromMs ?: JSONObject.NULL)
    put("error", s.error)
}.toString()

/** Liefert die Oberfläche aus den Assets und reicht GET-Anfragen an den Hikari-Server durch. */
private class RussianWebClient(private val app: Context, private val okHttp: OkHttpClient) : WebViewClient() {

    private val clips: ByteArray by lazy {
        val names = app.assets.list("russian/audio").orEmpty().filter { it.endsWith(".ogg") }.map { it.removeSuffix(".ogg") }
        JSONArray(names).toString().toByteArray()
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (request.url.host == HOST) return false
        // Fremde Links (RusslandJournal, Studien) im Browser öffnen statt in der Lern-Oberfläche.
        runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return true
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.host != HOST) return null
        val path = url.path.orEmpty().ifEmpty { "/" }
        return when {
            path.startsWith("/api/") -> proxy(path.removePrefix("/api") + (url.encodedQuery?.let { "?$it" } ?: ""), request)
            path == "/clips.json" -> ok("application/json", ByteArrayInputStream(clips))
            path.startsWith("/data/") && path.endsWith(".json") -> asset("russian/${path.removePrefix("/data/")}")
            path.startsWith("/audio/") && path.endsWith(".ogg") -> asset("russian/audio/${path.removePrefix("/audio/")}")
            else -> asset("russian-web${if (path == "/") "/index.html" else path}")
        }
    }

    private fun asset(name: String): WebResourceResponse {
        if (".." in name) return status(400, "Bad Request")
        val stream = runCatching { app.assets.open(name) }.getOrNull() ?: return status(404, "Not Found")
        return ok(mime(name), stream)
    }

    private fun proxy(pathAndQuery: String, request: WebResourceRequest): WebResourceResponse {
        if (request.method != "GET") return status(405, "Method Not Allowed")
        return try {
            // Der Host ist ein Platzhalter — der OkHttp-Client der App schreibt ihn auf die eingestellte Server-Adresse um.
            val builder = Request.Builder().url("http://hikari.invalid$pathAndQuery")
            request.requestHeaders["Range"]?.let { builder.header("Range", it) }
            val res = okHttp.newCall(builder.build()).execute()
            val headers = mutableMapOf("Cache-Control" to "no-store")
            for (h in listOf("Content-Range", "Accept-Ranges", "Content-Length")) res.header(h)?.let { headers[h] = it }
            val type = res.header("Content-Type").orEmpty()
            WebResourceResponse(
                type.substringBefore(';').ifBlank { "application/octet-stream" },
                if ("json" in type || type.startsWith("text/")) "utf-8" else null,
                res.code.takeIf { it in 200..299 || it in 400..599 } ?: 502,
                res.message.ifBlank { "OK" },
                headers,
                res.body?.byteStream() ?: ByteArrayInputStream(ByteArray(0)),
            )
        } catch (e: Exception) {
            WebResourceResponse("application/json", "utf-8", 502, "Bad Gateway", mapOf("Cache-Control" to "no-store"),
                ByteArrayInputStream("""{"error":"backend_unreachable"}""".toByteArray()))
        }
    }

    private fun ok(mime: String, body: java.io.InputStream) =
        WebResourceResponse(mime, if (mime.startsWith("text/") || "json" in mime || "javascript" in mime) "utf-8" else null, 200, "OK", mapOf("Cache-Control" to "no-cache"), body)

    private fun status(code: Int, reason: String) =
        WebResourceResponse("text/plain", "utf-8", code, reason, emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun mime(name: String) = when (name.substringAfterLast('.', "")) {
        "html" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "ogg" -> "audio/ogg"
        "woff2" -> "font/woff2"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }
}

/**
 * Was die Web-Oberfläche von der App braucht — in JavaScript als `window.HikariNative`.
 * Antworten laufen über `window.__hkCb(id, {...})`, Ereignisse über `window.__hkEvent(name, {...})`.
 * Die @JavascriptInterface-Methoden werden auf einem Hintergrund-Thread aufgerufen.
 */
class RussianWebBridge(
    private val app: Context,
    private val vm: RussianWebViewModel,
    private val scope: CoroutineScope,
    private val onClose: () -> Unit,
) {
    var webView: WebView? = null
    var requestMic: () -> Unit = {}
    var requestNotify: () -> Unit = {}

    private val main = Handler(Looper.getMainLooper())
    private val prefs = app.getSharedPreferences("hikari_russian", Context.MODE_PRIVATE)
    private var closed = false

    // ── Rückweg zur Oberfläche ───────────────────────────────────────────────

    private fun js(code: String) = main.post { if (!closed) webView?.evaluateJavascript(code, null) }
    private fun reply(id: String, payload: JSONObject) = js("window.__hkCb && window.__hkCb(${JSONObject.quote(id)}, $payload)")
    /** [json] ist ein fertiges JSON-Objekt. */
    fun emit(name: String, json: String) = js("window.__hkEvent && window.__hkEvent(${JSONObject.quote(name)}, $json)")

    fun close() {
        closed = true
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        webView = null
    }

    /** Aus JS: `HikariNative.exit()` — zurück zur übrigen App. */
    @JavascriptInterface fun exit() { main.post { if (!closed) onClose() } }

    // ── Speicher ─────────────────────────────────────────────────────────────

    /** Vom Podcast-Player der App verwaltet — die Oberfläche liest sie nur. */
    private fun playerOwned(key: String) =
        key.startsWith("podcast_pos_") || key.startsWith("podcast_dur_") || key == "podcast_speed" || key == "podcast_last_day"

    /** Kanonische Fassung des zuletzt von der Oberfläche geschriebenen Lernstands — so erkennen wir Änderungen der App selbst. */
    @Volatile private var lastWebProgress: String = vm.progress.encode(vm.progress.state.value)

    @JavascriptInterface
    fun storeAll(): String {
        val out = JSONObject()
        for ((k, v) in prefs.all) if (v != null) out.put(k, v.toString())
        // Der Lernstand der Gesprächsreise lag bisher in einer eigenen Datei — einmalig übernehmen.
        if (!out.has("conv_progress")) {
            app.getSharedPreferences("hikari_russian_conversation", Context.MODE_PRIVATE).getString("progress", null)?.let {
                out.put("conv_progress", it)
                prefs.edit().putString("conv_progress", it).apply()
            }
        }
        return out.toString()
    }

    @JavascriptInterface
    fun storeSet(key: String, value: String) {
        if (playerOwned(key)) return
        if (key == "progress_v1") {
            val parsed = vm.progress.decode(value)
            if (parsed != null) {
                lastWebProgress = vm.progress.encode(parsed)
                vm.progress.replace(parsed, value)
                return
            }
        }
        prefs.edit().putString(key, value).apply()
    }

    @JavascriptInterface
    fun storeRemove(key: String) {
        if (!playerOwned(key) && key != "progress_v1") prefs.edit().remove(key).apply()
    }

    /** Die App hat den Lernstand geändert (z. B. »Folge gehört«) → der Oberfläche melden. */
    fun onProgressChanged(json: String) {
        if (json == lastWebProgress) return
        lastWebProgress = json
        emit("store", JSONObject().put("key", "progress_v1").put("value", json).toString())
    }

    // ── Mikrofon ─────────────────────────────────────────────────────────────

    private var micWaiters = mutableListOf<(Boolean) -> Unit>()

    /** Führt [block] mit Mikrofon-Berechtigung aus; fragt sie bei Bedarf an. Nur vom Main-Thread. */
    fun withMic(block: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { block(true); return }
        micWaiters += block
        if (micWaiters.size == 1) requestMic()
    }

    fun onMicPermission(granted: Boolean) {
        val waiters = micWaiters
        micWaiters = mutableListOf()
        waiters.forEach { it(granted) }
    }

    // ── Spracherkennung ──────────────────────────────────────────────────────

    private var recognizer: RussianSpeechRecognizer? = null
    private var listenId: String? = null

    @JavascriptInterface fun speechAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(app)

    @JavascriptInterface
    fun listenStart(id: String, lang: String) {
        main.post {
            if (closed) return@post
            withMic { granted ->
                if (!granted) { reply(id, JSONObject().put("t", "error").put("code", "denied")); return@withMic }
                val r = recognizer ?: RussianSpeechRecognizer(app).also { rec ->
                    recognizer = rec
                    scope.launch { rec.state.collect(::onListenState) }
                    // Mikrofon offen bzw. (bei einem stillen Neuanlauf) wieder zu — die Oberfläche zeigt es an.
                    scope.launch {
                        rec.ready.collect { on -> listenId?.let { reply(it, JSONObject().put("t", "ready").put("on", on)) } }
                    }
                }
                listenId = id
                r.reset()
                r.start()
            }
        }
    }

    private fun onListenState(s: RuListenState) {
        val id = listenId ?: return
        when (s) {
            is RuListenState.Partial -> reply(id, JSONObject().put("t", "partial").put("text", s.text))
            is RuListenState.Done -> { listenId = null; reply(id, JSONObject().put("t", "done").put("hyps", JSONArray(s.hypotheses))) }
            is RuListenState.Failed -> {
                listenId = null
                val code = when {
                    "Berechtigung" in s.message -> "denied"
                    s.missingLanguage -> "network"
                    "nicht erreichbar" in s.message -> "busy"
                    else -> "unavailable"
                }
                reply(id, JSONObject().put("t", "error").put("code", code).put("message", s.message))
            }
            else -> Unit
        }
    }

    @JavascriptInterface fun listenStop(id: String) { main.post { if (listenId == id) recognizer?.stopListening() } }

    @JavascriptInterface
    fun listenAbort(id: String) {
        main.post { if (listenId == id) { listenId = null; recognizer?.destroy(); recognizer?.reset() } }
    }

    // ── Sprachausgabe ────────────────────────────────────────────────────────

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsPending: (() -> Unit)? = null

    @JavascriptInterface
    fun ttsSpeak(id: String, text: String, lang: String, rate: Double) {
        main.post {
            if (closed) return@post
            val speak: () -> Unit = {
                val engine = tts
                val locale = Locale.forLanguageTag(lang)
                if (engine == null || !ttsReady || engine.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) {
                    reply(id, JSONObject().put("ok", false))
                } else {
                    engine.setSpeechRate(rate.toFloat().coerceIn(0.5f, 1.3f))
                    if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) reply(id, JSONObject().put("ok", false))
                }
            }
            if (tts == null) {
                ttsPending = speak
                tts = TextToSpeech(app) { status ->
                    main.post {
                        ttsReady = status == TextToSpeech.SUCCESS
                        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit
                            override fun onDone(utteranceId: String?) { utteranceId?.let { reply(it, JSONObject().put("ok", true)) } }
                            @Deprecated("Android callback") override fun onError(utteranceId: String?) { utteranceId?.let { reply(it, JSONObject().put("ok", false)) } }
                            override fun onStop(utteranceId: String?, interrupted: Boolean) { utteranceId?.let { reply(it, JSONObject().put("ok", false)) } }
                        })
                        ttsPending?.invoke()
                        ttsPending = null
                    }
                }
            } else if (!ttsReady && ttsPending != null) {
                ttsPending = speak
            } else {
                speak()
            }
        }
    }

    @JavascriptInterface fun ttsStop() { main.post { runCatching { tts?.stop() } } }

    // ── Hikari-Server (POST) ─────────────────────────────────────────────────

    private fun send(id: String, request: Request) {
        vm.okHttp.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) { reply(id, JSONObject().put("status", 0).put("body", "")) }
            override fun onResponse(call: Call, response: Response) {
                val body = response.use { runCatching { it.body?.string().orEmpty() }.getOrDefault("") }
                reply(id, JSONObject().put("status", response.code).put("body", body))
            }
        })
    }

    @JavascriptInterface
    fun httpPost(id: String, path: String, json: String) {
        if (!path.startsWith("/")) { reply(id, JSONObject().put("status", 400).put("body", "")); return }
        send(id, Request.Builder().url("http://hikari.invalid$path").post(json.toRequestBody("application/json".toMediaTypeOrNull())).build())
    }

    /** Aussprache-Prüfung: Clip-Name und eigene Aufnahme (Base64) als Multipart — das Feld »clip« muss vor der Datei stehen. */
    @JavascriptInterface
    fun pronounce(id: String, clip: String, base64: String, mime: String) {
        val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
        if (bytes == null) { reply(id, JSONObject().put("status", 400).put("body", "")); return }
        val ext = if ("mp4" in mime) "m4a" else if ("ogg" in mime) "ogg" else "webm"
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("clip", clip)
            .addFormDataPart("file", "take.$ext", bytes.toRequestBody(mime.substringBefore(';').toMediaTypeOrNull()))
            .build()
        send(id, Request.Builder().url("http://hikari.invalid/russian/pronunciation").post(body).build())
    }

    // ── Podcast-Player ───────────────────────────────────────────────────────

    @JavascriptInterface fun podcastState(): String = podcastJson(vm.podcast.state.value)
    @JavascriptInterface fun podcastLoad(day: Int, videoId: String, title: String, autoplay: Boolean) { main.post { vm.podcast.load(day, videoId, title, autoplay) } }
    @JavascriptInterface fun podcastPlay() { main.post { vm.podcast.play() } }
    @JavascriptInterface fun podcastPause() { main.post { vm.podcast.pause() } }
    @JavascriptInterface fun podcastToggle() { main.post { vm.podcast.toggle() } }
    @JavascriptInterface fun podcastSeek(ms: Double) { main.post { vm.podcast.seekTo(ms.toLong()) } }
    @JavascriptInterface fun podcastCycleSpeed() { main.post { vm.podcast.cycleSpeed() } }
    @JavascriptInterface fun podcastClose() { main.post { vm.podcast.close() } }
    @JavascriptInterface fun podcastMarkListened(day: Int) { main.post { vm.podcast.markListened(day) } }

    // ── Tägliche Erinnerung ──────────────────────────────────────────────────

    private var pendingReminderMinutes: Int? = null

    @JavascriptInterface
    fun reminderGet(): String = runBlocking {
        JSONObject().put("enabled", vm.settings.russianPodcastReminderEnabled.first()).put("minutes", vm.settings.russianPodcastReminderMinutes.first()).toString()
    }

    @JavascriptInterface
    fun reminderSet(enabled: Boolean, minutes: Int) {
        main.post {
            val m = minutes.coerceIn(0, 1439)
            scope.launch { vm.settings.setRussianPodcastReminderMinutes(m) }
            if (!enabled) { applyReminder(false, m); return@post }
            val granted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (granted) applyReminder(true, m) else { pendingReminderMinutes = m; requestNotify() }
        }
    }

    fun onNotifyPermission(granted: Boolean) {
        val m = pendingReminderMinutes ?: return
        pendingReminderMinutes = null
        applyReminder(granted, m)
    }

    private fun applyReminder(enabled: Boolean, minutes: Int) {
        scope.launch {
            vm.settings.setRussianPodcastReminderEnabled(enabled)
            if (enabled) vm.reminder.schedule(minutes) else vm.reminder.cancel()
        }
    }
}
