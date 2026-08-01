package com.hikari.app.domain.news

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Stadt + aus dem Land abgeleitete Sprache, wie sie der News-Feed nutzt. */
data class NewsLocation(val city: String, val lang: String)

/**
 * Ermittelt Standort → Stadt/Sprache ohne Google Play Services:
 * letzter bekannter Standort des LocationManager (GPS/NETWORK), dann Geocoder.
 * Setzt voraus, dass ACCESS_COARSE_LOCATION bereits gewährt wurde.
 */
@Singleton
class NewsLocationHelper @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    /** @return ermittelte Stadt + Sprache, oder null wenn nichts ermittelbar war. */
    suspend fun resolve(): NewsLocation? {
        val location = lastKnownLocation() ?: return null
        return try {
            val address = geocode(location.latitude, location.longitude) ?: return null
            val city = address.locality ?: address.subAdminArea ?: return null
            NewsLocation(city = city, lang = langForCountry(address.countryCode))
        } catch (_: Exception) {
            // Geocoder-Backend nicht verfügbar — kein harter Fehler, nur kein Standort.
            null
        }
    }

    @SuppressLint("MissingPermission") // Permission wird vor dem Aufruf angefragt
    private fun lastKnownLocation(): android.location.Location? {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        return try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
    }

    private suspend fun geocode(lat: Double, lon: Double) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1) { addresses ->
                    cont.resume(addresses.firstOrNull())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            withContext(Dispatchers.IO) {
                Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            }
        }

    private fun langForCountry(countryCode: String?): String =
        if (countryCode?.uppercase() in setOf("DE", "AT", "CH")) "de" else "en"
}
