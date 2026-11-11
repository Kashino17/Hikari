package com.hikari.app.ui.games

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.view.HapticFeedbackConstants
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hikari.app.ui.theme.HikariBg
import java.io.ByteArrayInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val HOST = "appassets.androidplatform.net"

/** Gespeicherter Stand des Spiels (JSON aus der Web-Oberfläche) und die Zahlen für die Spiele-Übersicht. */
internal const val HOLE_STATE = "hole3d_state"
internal const val HOLE_LEVELS_DONE = "hole3d_levels_done"
internal const val HOLE_BEST = "hole3d_best"

/**
 * Hungry Hole in 3D. Das Spiel selbst ist eine WebGL-Seite aus den App-Dateien
 * (assets/hole3d, Quelltext in tools/hole3d) — es läuft ohne Netz. Die App liefert die
 * Dateien aus, speichert den Stand und gibt Vibration und Zurück-Taste weiter.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HungryHoleGame(onBack: () -> Unit) {
    val exit by rememberUpdatedState(onBack)
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Zurück gehört dem Spiel: Spiel → Pause → Übersicht → raus (dann ruft es selbst exit()).
    BackHandler {
        val view = webView
        if (view == null) exit() else view.evaluateJavascript("window.hikariBack ? window.hikariBack() : HoleNative.exit()", null)
    }

    // Im Hintergrund hält der WebView an; die Seite pausiert das Spiel dabei selbst.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize().background(HikariBg),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(0xFF1F1F22.toInt())
                overScrollMode = View.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                // Töne entstehen im Spiel selbst und sollen ohne Extra-Tipp erklingen.
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                addJavascriptInterface(HoleBridge(context.applicationContext, this) { exit() }, "HoleNative")
                webViewClient = HoleAssets(context.assets)
                loadUrl("https://$HOST/index.html")
                webView = this
            }
        },
        onRelease = { view ->
            webView = null
            view.destroy()
        },
    )
}

/** Was die Seite von der App braucht. Die Methoden kommen auf einem Hintergrund-Thread an. */
private class HoleBridge(
    context: Context,
    private val view: WebView,
    private val onExit: () -> Unit,
) {
    private val prefs = context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE)

    @JavascriptInterface
    fun load(): String = prefs.getString(HOLE_STATE, null) ?: "{}"

    @JavascriptInterface
    fun save(json: String) {
        val summary = holeSummary(json) ?: return // Unlesbares überschreibt keinen guten Stand
        prefs.edit()
            .putString(HOLE_STATE, json)
            .putInt(HOLE_LEVELS_DONE, summary.levelsDone)
            .putInt(HOLE_BEST, summary.best)
            .apply()
    }

    @JavascriptInterface
    fun haptic(kind: String) {
        val effect = if (kind == "grow") HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CLOCK_TICK
        view.post { view.performHapticFeedback(effect) }
    }

    @JavascriptInterface
    fun exit() {
        view.post { onExit() }
    }
}

/** Kurzfassung des Spielstands für die Spiele-Übersicht. */
internal data class HoleSummary(val levelsDone: Int, val best: Int)

/** Liest aus dem gespeicherten JSON, wie viele Level mindestens einen Stern haben und den Zeitjagd-Rekord. */
internal fun holeSummary(json: String): HoleSummary? = runCatching {
    val state = Json.parseToJsonElement(json).jsonObject
    val stars = state["stars"]?.jsonArray.orEmpty()
    val done = stars.count { (it.jsonPrimitive.intOrNull ?: 0) > 0 }
    val best = state["best"]?.jsonPrimitive?.intOrNull ?: 0
    HoleSummary(levelsDone = done, best = best.coerceAtLeast(0))
}.getOrNull()

/** Liefert die Spieldateien aus assets/hole3d unter einer festen https-Adresse aus — nichts geht ins Netz. */
private class HoleAssets(private val assets: AssetManager) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        request.url.host != HOST // das Spiel verlässt seine Seite nie

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
        val path = request.url.path.orEmpty().trimStart('/').ifEmpty { "index.html" }
        if (request.url.host != HOST || ".." in path) return status(404)
        val stream = runCatching { assets.open("hole3d/$path") }.getOrNull() ?: return status(404)
        val mime = when (path.substringAfterLast('.', "")) {
            "html" -> "text/html"
            "js" -> "text/javascript"
            "glb" -> "model/gltf-binary"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }
        val text = mime.startsWith("text/")
        return WebResourceResponse(mime, if (text) "utf-8" else null, 200, "OK", mapOf("Cache-Control" to "no-cache"), stream)
    }

    private fun status(code: Int) =
        WebResourceResponse("text/plain", "utf-8", code, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}
