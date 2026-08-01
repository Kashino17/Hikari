package com.hikari.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hikari.app.ui.navigation.HikariNavHost
import com.hikari.app.ui.theme.HikariTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Ziel-Route aus Intent-Extras (z.B. navigate_to=news aus der News-Notification). */
    private var deepLinkRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkRoute = intent.deepLinkRoute()
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
