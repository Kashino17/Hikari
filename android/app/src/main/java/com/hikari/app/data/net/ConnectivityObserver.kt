package com.hikari.app.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * Meldet, ob das Gerät gerade eine nutzbare Internetverbindung hat.
 *
 * Bewusst grob: `NET_CAPABILITY_VALIDATED` unterscheidet ein echtes Netz von
 * einem WLAN ohne Durchgang (Captive Portal). Ein Backend, das offline ist,
 * während das Handy online ist, bleibt Sache der jeweiligen Repositories —
 * die fangen ihre Fehler ohnehin selbst ab.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val manager = ctx.getSystemService(ConnectivityManager::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val isOnline: StateFlow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentlyOnline())
            }

            override fun onLost(network: Network) {
                trySend(currentlyOnline())
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(currentlyOnline())
            }
        }
        trySend(currentlyOnline())
        manager?.registerDefaultNetworkCallback(callback)
        awaitClose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, currentlyOnline())

    fun currentlyOnline(): Boolean {
        val network = manager?.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
