package com.hikari.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hikari.app.ui.navigation.HikariNavHost
import com.hikari.app.ui.theme.HikariTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HikariTheme {
                HikariNavHost()
            }
        }
    }
}
