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
import com.hikari.app.ui.navigation.HikariNavHost
import com.hikari.app.ui.theme.HikariTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Ziel-Route aus Intent-Extras (z.B. navigate_to=news aus der News-Notification). */
    private var deepLinkRoute by mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkRoute = intent.deepLinkRoute()
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
                HikariNavHost(deepLinkRoute = deepLinkRoute)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinkRoute = intent.deepLinkRoute()
    }

    private fun Intent?.deepLinkRoute(): String? =
        this?.getStringExtra("navigate_to")?.takeIf { it.isNotBlank() }
}
