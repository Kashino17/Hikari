package com.hikari.app.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions
import com.hikari.app.MainActivity

/**
 * Vom Cast-Framework per Manifest-Eintrag geladen. Der Standard-Empfänger
 * von Google spielt jede erreichbare MP4-URL ab — keine eigene Receiver-App nötig.
 */
class HikariCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            // Verbindung übersteht Bildschirm-aus, App-Wechsel und kurze WLAN-Aussetzer: die
            // Sitzung wird gemerkt und im Hintergrund wieder aufgebaut.
            .setResumeSavedSession(true)
            .setEnableReconnectionService(true)
            // »Cast beenden« räumt auch den Fernseher ab, statt ein Standbild stehen zu lassen.
            .setStopReceiverApplicationWhenEndingSession(true)
            .setCastMediaOptions(
                CastMediaOptions.Builder()
                    // Die Benachrichtigung mit Pause/Stopp führt beim Antippen zurück in die App.
                    .setNotificationOptions(
                        NotificationOptions.Builder()
                            .setTargetActivityClassName(MainActivity::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
