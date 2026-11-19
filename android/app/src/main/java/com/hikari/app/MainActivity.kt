package com.hikari.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.hikari.app.domain.update.UpdateCoordinator
import com.hikari.app.ui.navigation.HikariNavHost
import com.hikari.app.ui.navigation.SharedImport
import com.hikari.app.ui.navigation.extractSharedUrl
import com.hikari.app.ui.theme.HikariTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Ziel-Route aus Intent-Extras (z.B. navigate_to=news aus der News-Notification). */
    private var deepLinkRoute by mutableStateOf<String?>(null)

    /** Per Teilen-Menü geschickter Link (ACTION_SEND text/plain). */
    private var sharedImport by mutableStateOf<SharedImport?>(null)

    /** Callback für Video-Player: Bild-im-Bild bei Home-Geste/App-Wechsel (Android 8+). */
    var onUserLeaveHintListener: (() -> Unit)? = null

    @Inject lateinit var updateCoordinator: UpdateCoordinator

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Leser: Lautstärketasten blättern, solange ein Leser das will. */
    interface VolumePager {
        fun wantsVolumeKeys(): Boolean
        fun page(forward: Boolean)
    }
    var volumePager: VolumePager? = null

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val code = event.keyCode
        val pager = volumePager
        val isVolume = code == android.view.KeyEvent.KEYCODE_VOLUME_UP || code == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
        if (pager != null && isVolume && pager.wantsVolumeKeys()) {
            // Leiser = weiter, Lauter = zurück (wie beim Kindle). Nur beim Drücken blättern;
            // das Loslassen trotzdem verbrauchen, sonst ändert Android die Lautstärke.
            if (event.action == android.view.KeyEvent.ACTION_DOWN) pager.page(forward = code == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        onUserLeaveHintListener?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkRoute = intent.deepLinkRoute()
        sharedImport = intent.sharedImport()
        // Frischer Start (nicht bloß Drehen): nach Updates suchen und einen
        // weggeklickten Update-Hinweis wieder zulassen.
        if (savedInstanceState == null) updateCoordinator.onAppStart()
        // Ohne diese Permission zeigt Android 13+ keinerlei Notifications an —
        // weder die Media-Steuerung beim Musikhören noch den Tagesbericht.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            HikariTheme {
                HikariNavHost(deepLinkRoute = deepLinkRoute, sharedImport = sharedImport)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinkRoute = intent.deepLinkRoute()
        intent.sharedImport()?.let { sharedImport = it }
    }

    private fun Intent?.sharedImport(): SharedImport? {
        if (this?.action != Intent.ACTION_SEND) return null
        val url = extractSharedUrl(getStringExtra(Intent.EXTRA_TEXT)) ?: return null
        return SharedImport(url = url, nonce = System.currentTimeMillis())
    }

    private fun Intent?.deepLinkRoute(): String? =
        this?.getStringExtra("navigate_to")?.takeIf { it.isNotBlank() }
}
