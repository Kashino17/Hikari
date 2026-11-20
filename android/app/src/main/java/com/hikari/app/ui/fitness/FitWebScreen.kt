package com.hikari.app.ui.fitness

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import android.media.ExifInterface
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.hikari.app.player.MusicPlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

/**
 * Fitness (Training mit KI-Coach, Calisthenics-Skills, Ernährung) als Web-Oberfläche.
 *
 * Die Oberfläche liegt in assets/fit-web (Preact ohne Build-Schritt); diese Datei ist die Brücke:
 *  - Dateien:  https://appassets.androidplatform.net/… → Assets
 *  - Server:   …/api/… → Hikari-Server über den App-eigenen OkHttp-Client (GET hier, POST über [FitWebBridge.httpPost])
 *  - Kamera:   Foto der Mahlzeit (Kamera-App oder Galerie), verkleinert als Base64 an die Oberfläche
 *  - Ansagen:  Android-Sprachausgabe auf Deutsch, Haptik, Bildschirm wach halten
 *  - Videos:   YouTube-Tutorials/Rezeptvideos im Player der App
 *
 * Vorschau ohne App-Build: tools/fit_web/server.mjs.
 */
private const val HOST = "appassets.androidplatform.net"
private const val BASE = "https://$HOST"

@HiltViewModel
class FitWebViewModel @Inject constructor(
    val okHttp: OkHttpClient,
    val music: MusicPlayerController,
) : ViewModel()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FitWebScreen(
    onClose: () -> Unit,
    onOpenVideo: (id: String, title: String) -> Unit,
    /** "fit" (Training, Skills, Ernährung) oder "mind" (Achtsamkeit & Spiritualität, eigener Bereich). */
    area: String = "fit",
    /** Zum anderen Bereich springen (z. B. Ruhetag → Achtsamkeit). */
    onOpenArea: (String) -> Unit = {},
    vm: FitWebViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val bridge = remember { FitWebBridge(context.applicationContext, vm.okHttp, onClose, onOpenVideo, vm.music) }
    bridge.openAreaFn = onOpenArea
    bridge.activity = context

    val photoFile = remember { File(context.cacheDir, "fit_photo.jpg") }
    val photoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        bridge.onPhoto(if (ok) Uri.fromFile(photoFile) else null)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> bridge.onPhoto(uri) }
    bridge.launchCamera = { runCatching { camera.launch(photoUri) }.onFailure { bridge.onPhoto(null) } }
    bridge.launchGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val health = rememberLauncherForActivityResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()) { bridge.onHealthResult() }
    bridge.launchHealth = { runCatching { health.launch(FitHealth.PERMISSIONS) }.onFailure { bridge.onHealthResult() } }
    // Offline entstandene Ergebnisse nachsenden, sobald der Bereich geöffnet wird.
    LaunchedEffect(Unit) { bridge.flushQueue() }

    // Randmaße in dp (= CSS-Pixel) — Statusleiste/Kamera-Aussparung oben, Gestenleiste unten.
    val density = LocalDensity.current
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val insetTop = with(density) { bars.getTop(this).toDp().value }
    // Offene Tastatur: die WebView weicht nach oben aus (imePadding), unten braucht es dann keinen Rand.
    val imeOpen = WindowInsets.ime.getBottom(density) > 0
    val insetBottom = if (imeOpen) 0f else with(density) { bars.getBottom(this).toDp().value }
    LaunchedEffect(insetTop, insetBottom) { bridge.pushInsets(insetTop, insetBottom) }

    // Barcode-Scanner-Modul der Play-Dienste schon vorab laden, damit der erste Scan nicht scheitert.
    LaunchedEffect(Unit) {
        runCatching {
            val scanner = com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context)
            com.google.android.gms.common.moduleinstall.ModuleInstall.getClient(context).installModules(
                com.google.android.gms.common.moduleinstall.ModuleInstallRequest.newBuilder().addApi(scanner).build(),
            )
        }
    }
    // Musik läuft im Fitness-Bereich weiter (früher wurde sie hier pausiert) — Rems Ansagen drehen
    // sie nur kurz leiser (bridge.duck).

    AndroidView(
        modifier = Modifier.fillMaxSize().imePadding(),
        factory = { ctx ->
            WebView(ctx).apply {
                // Ohne eigene LayoutParams rechnet Chromium mit Viewport-Höhe 0.
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                // Grundfarbe des Bereichs, damit beim Laden kein grauer Blitz entsteht.
                setBackgroundColor(Color.parseColor(if (area == "mind") "#0A0F26" else "#000000"))
                overScrollMode = WebView.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.textZoom = 100
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                addJavascriptInterface(bridge, "HikariNative")
                webViewClient = FitWebClient(ctx.applicationContext, vm.okHttp)
                bridge.webView = this
                loadUrl("$BASE/index.html?area=$area#/heute")
            }
        },
        onRelease = { it.destroy() },
    )

    BackHandler {
        val web = bridge.webView
        if (web == null) onClose()
        else web.evaluateJavascript("(window.__hkBack && window.__hkBack()) === true") { handled -> if (handled != "true") onClose() }
    }
    DisposableEffect(Unit) { onDispose { bridge.close() } }
}

/** Liefert die Oberfläche aus den Assets und reicht GET-Anfragen an den Hikari-Server durch. */
private class FitWebClient(private val app: Context, private val okHttp: OkHttpClient) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (request.url.host == HOST) return false
        runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return true
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.host != HOST) return null
        val path = url.path.orEmpty().ifEmpty { "/" }
        return if (path.startsWith("/api/")) proxy(path.removePrefix("/api") + (url.encodedQuery?.let { "?$it" } ?: ""), request)
        else asset("fit-web${if (path == "/") "/index.html" else path}")
    }

    private fun asset(name: String): WebResourceResponse {
        if (".." in name) return status(400, "Bad Request")
        val stream = runCatching { app.assets.open(name) }.getOrNull() ?: return status(404, "Not Found")
        val mime = when (name.substringAfterLast('.', "")) {
            "html" -> "text/html"
            "js", "mjs" -> "text/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "woff2" -> "font/woff2"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "ogg" -> "audio/ogg"
            else -> "application/octet-stream"
        }
        return WebResourceResponse(mime, if (mime.startsWith("text/") || "json" in mime || "javascript" in mime) "utf-8" else null, 200, "OK", mapOf("Cache-Control" to "no-cache"), stream)
    }

    private fun proxy(pathAndQuery: String, request: WebResourceRequest): WebResourceResponse {
        if (request.method != "GET") return status(405, "Method Not Allowed")
        val range = request.requestHeaders["Range"]
        // Übungsvideos/-bilder: einmal geladen, dann immer vom Gerät (spart Netz, geht offline).
        if (FitCache.isMedia(pathAndQuery)) FitCache.get(app, pathAndQuery)?.let { return FitCache.serve(it, range, offline = false) }
        if (FitCache.cacheable(pathAndQuery)) {
            return try {
                okHttp.newCall(Request.Builder().url("http://hikari.invalid$pathAndQuery").build()).execute().use { res ->
                    val type = res.header("Content-Type") ?: "application/octet-stream"
                    val bytes = res.body?.bytes() ?: ByteArray(0)
                    if (res.isSuccessful) FitCache.put(app, pathAndQuery, type, bytes)
                    if (res.isSuccessful) FitCache.serve(FitCache.Entry(bytes, type), range, offline = false)
                    else WebResourceResponse(type.substringBefore(';'), "utf-8", res.code, res.message.ifBlank { "Error" }, mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(bytes))
                }
            } catch (e: Exception) {
                // Mac nicht erreichbar → letzter Stand vom Gerät
                FitCache.get(app, pathAndQuery)?.let { FitCache.serve(it, range, offline = true) }
                    ?: WebResourceResponse("application/json", "utf-8", 502, "Bad Gateway", mapOf("Cache-Control" to "no-store"),
                        ByteArrayInputStream("""{"error":"backend_unreachable"}""".toByteArray()))
            }
        }
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

    private fun status(code: Int, reason: String) =
        WebResourceResponse("text/plain", "utf-8", code, reason, emptyMap(), ByteArrayInputStream(ByteArray(0)))
}

/**
 * `window.HikariNative` im Fitness-Bereich. Antworten laufen über `window.__hkCb(id, {...})`.
 * Die @JavascriptInterface-Methoden kommen auf einem Hintergrund-Thread an.
 */
class FitWebBridge(
    private val app: Context,
    private val okHttp: OkHttpClient,
    private val onClose: () -> Unit,
    private val onOpenVideo: (String, String) -> Unit,
    private val music: com.hikari.app.player.MusicPlayerController? = null,
) {
    var webView: WebView? = null
    var launchCamera: () -> Unit = {}
    var launchGallery: () -> Unit = {}
    /** Für den Barcode-Scanner (startet seine eigene Oberfläche über die Activity). */
    var activity: Context? = null

    private val main = Handler(Looper.getMainLooper())
    private val prefs = app.getSharedPreferences("hikari_fit", Context.MODE_PRIVATE)
    private var closed = false
    private var photoId: String? = null

    private fun js(code: String) = main.post { if (!closed) webView?.evaluateJavascript(code, null) }
    private fun reply(id: String, payload: JSONObject) = js("window.__hkCb && window.__hkCb(${JSONObject.quote(id)}, $payload)")

    fun close() {
        closed = true
        main.removeCallbacksAndMessages(null)
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        setAwake(false)
        setDuck(false)
        ambient?.release()
        ambient = null
        runCatching { scope.cancel() }
        webView = null
    }

    @JavascriptInterface fun exit() { main.post { if (!closed) onClose() } }
    var openAreaFn: (String) -> Unit = {}
    @JavascriptInterface fun openArea(area: String) { if (area == "mind" || area == "fitness") main.post { if (!closed) openAreaFn(area) } }

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    // ── Offline: vorladen, Warteschlange ──
    @JavascriptInterface fun prefetch(pathsJson: String) {
        val list = runCatching { org.json.JSONArray(pathsJson).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())
        scope.launch { FitCache.prefetch(app, okHttp, list) }
    }
    @JavascriptInterface fun pendingCount(): Int = FitCache.pending(app)
    fun flushQueue() {
        scope.launch {
            val n = FitCache.flush(app, okHttp)
            if (n > 0) js("window.__hkEvent && window.__hkEvent('synced', {count:$n})")
        }
    }

    // ── Erinnerungen ──
    @JavascriptInterface fun getReminders(): String = FitReminders.toJson(FitReminders.read(app))
    @JavascriptInterface fun setReminders(json: String) { runCatching { FitReminders.apply(app, json) } }

    // ── Health Connect ──
    var launchHealth: () -> Unit = {}
    private var healthId: String? = null
    @JavascriptInterface fun healthStatus(id: String) {
        scope.launch { reply(id, JSONObject().put("status", runCatching { FitHealth.status(app) }.getOrDefault("unavailable"))) }
    }
    @JavascriptInterface fun healthConnect(id: String) {
        scope.launch {
            when (runCatching { FitHealth.status(app) }.getOrDefault("unavailable")) {
                "unavailable" -> reply(id, JSONObject().put("status", "unavailable"))
                "update" -> {
                    main.post {
                        runCatching {
                            activity?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                    reply(id, JSONObject().put("status", "update"))
                }
                else -> { healthId = id; main.post { launchHealth() } }
            }
        }
    }
    fun onHealthResult() {
        val id = healthId ?: return
        healthId = null
        scope.launch { reply(id, JSONObject().put("status", runCatching { FitHealth.status(app) }.getOrDefault("unavailable"))) }
    }
    @JavascriptInterface fun healthRead(id: String, days: Int) {
        scope.launch {
            val data = runCatching { FitHealth.read(app, days.coerceIn(1, 30)) }.getOrDefault("[]")
            reply(id, JSONObject().put("days", org.json.JSONArray(data)))
        }
    }

    // ── Randmaße: die Seite läuft bis unter Statusleiste und Gestenleiste und polstert selbst ──
    @Volatile var insetTop = 0f
    @Volatile var insetBottom = 0f
    @JavascriptInterface fun insets(): String = "{\"t\":$insetTop,\"b\":$insetBottom}"
    fun pushInsets(top: Float, bottom: Float) {
        insetTop = top
        insetBottom = bottom
        js("window.__hkInsets && window.__hkInsets()")
    }

    // ── Kleiner Speicher (Zwischenstände, Vorlieben) ──
    @JavascriptInterface fun storeGet(key: String): String = prefs.getString(key, "") ?: ""
    @JavascriptInterface fun storeSet(key: String, value: String) { prefs.edit().putString(key, value).apply() }

    // ── Hikari-Server (POST) ──
    @JavascriptInterface
    fun httpPost(id: String, path: String, json: String) {
        if (!path.startsWith("/")) { reply(id, JSONObject().put("status", 400).put("body", "")); return }
        val req = Request.Builder().url("http://hikari.invalid$path").post(json.toRequestBody("application/json".toMediaTypeOrNull())).build()
        // Essensfotos und KI-Pläne brauchen Zeit — eigener Client mit langer Wartezeit.
        slow.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                // Offline: Ergebnisse (Training, Skill, Achtsamkeit …) warten und gehen später raus.
                if (FitCache.queueable(path)) {
                    FitCache.enqueue(app, path, json)
                    reply(id, JSONObject().put("status", 202).put("body", JSONObject().put("queued", true).toString()))
                } else reply(id, JSONObject().put("status", 0).put("body", ""))
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.use { runCatching { it.body?.string().orEmpty() }.getOrDefault("") }
                reply(id, JSONObject().put("status", response.code).put("body", body))
                if (response.code < 500) flushQueue()
            }
        })
    }

    private val slow: OkHttpClient by lazy {
        okHttp.newBuilder()
            .readTimeout(java.time.Duration.ofSeconds(240))
            .callTimeout(java.time.Duration.ofSeconds(300))
            .writeTimeout(java.time.Duration.ofSeconds(60))
            .build()
    }

    // ── Foto der Mahlzeit ──
    @JavascriptInterface
    fun pickPhoto(id: String, source: String) {
        main.post {
            photoId = id
            if (source == "gallery") launchGallery() else launchCamera()
        }
    }

    fun onPhoto(uri: Uri?) {
        val id = photoId ?: return
        photoId = null
        if (uri == null) { reply(id, JSONObject()); return }
        Thread {
            val b64 = runCatching { encodePhoto(uri) }.getOrNull()
            reply(id, if (b64 == null) JSONObject() else JSONObject().put("base64", b64).put("mime", "image/jpeg"))
        }.start()
    }

    /** Liest das Foto, dreht es nach EXIF, verkleinert auf höchstens 1280 px und gibt JPEG als Base64 zurück. */
    private fun encodePhoto(uri: Uri): String? {
        val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rotation = runCatching {
            when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val scale = minOf(1f, 1280f / max(bmp.width, bmp.height))
        val m = Matrix().apply { if (scale < 1f) postScale(scale, scale); if (rotation != 0f) postRotate(rotation) }
        val out = if (scale < 1f || rotation != 0f) Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true) else bmp
        val buf = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 86, buf)
        return Base64.encodeToString(buf.toByteArray(), Base64.NO_WRAP)
    }

    // ── Barcode (Google Code Scanner, Modul kommt über die Play-Dienste) ──
    @JavascriptInterface
    fun scanBarcode(id: String) {
        main.post {
            val ctx = activity ?: app
            val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_E,
                )
                .enableAutoZoom()
                .build()
            com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(ctx, options).startScan()
                .addOnSuccessListener { code -> reply(id, JSONObject().put("code", code.rawValue ?: "")) }
                .addOnCanceledListener { reply(id, JSONObject()) }
                .addOnFailureListener { e -> reply(id, JSONObject().put("error", e.message ?: "scanner")) }
        }
    }

    // ── Ansagen, Haptik, Bildschirm ──
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending: String? = null

    @JavascriptInterface
    fun ttsSpeak(id: String, text: String, lang: String, rate: Double) {
        main.post {
            if (closed) return@post
            val engine = tts
            if (engine == null) {
                pending = text
                tts = TextToSpeech(app) { status ->
                    main.post {
                        ttsReady = status == TextToSpeech.SUCCESS
                        if (ttsReady) {
                            tts?.language = Locale.GERMANY
                            tts?.setSpeechRate(1.05f)
                            pending?.let { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "fit") }
                        }
                        pending = null
                    }
                }
            } else if (ttsReady) {
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            } else {
                pending = text
            }
        }
    }

    @JavascriptInterface fun ttsStop() { main.post { runCatching { tts?.stop() } } }

    @JavascriptInterface
    fun haptic(kind: String) {
        main.post {
            webView?.performHapticFeedback(if (kind == "go") HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /**
     * Musik kurz leiser, solange Rem spricht oder ein Ton kommt — und danach wieder normal.
     * Rems Stimme läuft über WebAudio (fordert KEINEN Audio-Fokus an, die Musik läuft also weiter);
     * hier nur eine »darf ducken«-Anfrage: Musik-Apps und Hikaris eigene Musik (ExoPlayer) werden
     * leiser statt zu pausieren. Früher holte sich die WebView für jeden Clip den vollen Fokus
     * (Medien) — jede Musik blieb stehen.
     */
    @JavascriptInterface fun duck(on: Boolean) { main.post { if (!closed) { ambient?.duck(on); setDuck(on) } } }

    // ── Klangbett unter Meditation, Yoga, Atem (MindAmbient) ──
    private var ambient: MindAmbient? = null
    private fun ambient(): MindAmbient = ambient ?: MindAmbient(app, okHttp, music) { st, id ->
        js("window.__hkEvent && window.__hkEvent('ambient', ${JSONObject().put("state", st).put("id", id)})")
    }.also { ambient = it }
    @JavascriptInterface fun ambientPlay(id: String, ver: String, volume: Float, loopFromMs: Double, loopToMs: Double) {
        main.post { if (!closed) ambient().play(id, ver, volume, loopFromMs.toLong(), loopToMs.toLong()) }
    }
    @JavascriptInterface fun ambientVolume(volume: Float) { main.post { ambient?.setVolume(volume) } }
    @JavascriptInterface fun ambientPause(on: Boolean) { main.post { ambient?.pause(on) } }
    @JavascriptInterface fun ambientStop(fadeMs: Int) { main.post { ambient?.stop(fadeMs.toLong()) } }
    /** Läuft gerade Hikaris eigene Musik? (Dann schlägt die Einheit »Stille« statt eines Betts vor.) */
    @JavascriptInterface fun musicPlaying(): Boolean = music?.isPlaying?.value == true

    private val audio by lazy { app.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
    private var duckRequest: android.media.AudioFocusRequest? = null

    private var duckingOwnMusic = false

    private fun setDuck(on: Boolean) {
        // Hikaris eigene Musik: sanft auf etwa 45 % statt der 20 %, auf die ExoPlayer beim Ducken fest geht.
        val own = music
        if (on && own != null && own.isPlaying.value) {
            if (!duckingOwnMusic) { duckingOwnMusic = true; own.rampVolume(0.45f, 250); com.hikari.app.player.PlayerTrace.log("fitness: Musik leiser (45 %)") }
            return
        }
        if (!on && duckingOwnMusic) { duckingOwnMusic = false; own?.rampVolume(1f, 600); com.hikari.app.player.PlayerTrace.log("fitness: Musik wieder voll") }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        if (on) {
            if (duckRequest != null) return
            val req = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener { }
                .build()
            if (audio.requestAudioFocus(req) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) duckRequest = req
        } else {
            duckRequest?.let { audio.abandonAudioFocusRequest(it) }
            duckRequest = null
        }
    }

    /**
     * Bildschirm wach halten (Training, Yoga, Meditation). Über das FENSTER, nicht nur die WebView:
     * Chromium setzt keepScreenOn der WebView beim Starten/Stoppen der Übungsvideos selbst um und
     * löschte so unser »an« — dann dunkelte das Display mitten in der Einheit ab.
     */
    @JavascriptInterface fun keepAwake(on: Boolean) { main.post { if (!closed) setAwake(on) } }

    private fun setAwake(on: Boolean) {
        val window = (activity?.findActivity())?.window
        if (on) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        webView?.keepScreenOn = on
    }

    private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    @JavascriptInterface
    fun openVideo(id: String, title: String) {
        if (!Regex("^[\\w-]{11}$").matches(id)) return
        main.post { if (!closed) onOpenVideo(id, title) }
    }
}
