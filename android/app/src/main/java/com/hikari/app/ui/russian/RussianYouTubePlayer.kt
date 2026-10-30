package com.hikari.app.ui.russian

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RussianYouTubePlayer(
    videoId: String,
    onEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val safeVideoId = remember(videoId) { videoId.filter { it.isLetterOrDigit() || it == '_' || it == '-' } }
    val webView = remember(videoId) {
        WebView(context).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun ended() {
                        post { onEnded() }
                    }
                },
                "HikariPlayer",
            )
            loadDataWithBaseURL(
                "https://hikari.app",
                playerHtml(safeVideoId),
                "text/html",
                "UTF-8",
                null,
            )
        }
    }
    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f).background(androidx.compose.ui.graphics.Color.Black),
    )
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
}

private fun playerHtml(videoId: String): String = """
    <!doctype html>
    <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
        <style>html,body,#player{margin:0;width:100%;height:100%;background:#000;overflow:hidden}</style>
      </head>
      <body>
        <div id="player"></div>
        <script>
          var tag = document.createElement('script');
          tag.src = 'https://www.youtube.com/iframe_api';
          document.head.appendChild(tag);
          var player;
          function onYouTubeIframeAPIReady() {
            player = new YT.Player('player', {
              videoId: '$videoId',
              playerVars: {playsinline: 1, rel: 0, origin: 'https://hikari.app'},
              events: {
                onStateChange: function(event) {
                  if (event.data === YT.PlayerState.ENDED) HikariPlayer.ended();
                }
              }
            });
          }
        </script>
      </body>
    </html>
""".trimIndent()
