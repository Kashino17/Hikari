package com.hikari.app.voice

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Unsichtbares Teilen-Ziel für Sprachnachrichten. Kopiert die Dateien, gibt
 * sie an den Hintergrund-Worker und schließt sofort wieder — man bleibt in
 * WhatsApp, die Zusammenfassung kommt als Benachrichtigung.
 */
class VoiceShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uris = VoiceIntake.urisFrom(intent)
        if (uris.isEmpty()) {
            toast("Keine Audiodatei gefunden")
            finish()
            return
        }
        lifecycleScope.launch {
            val batch = withContext(Dispatchers.IO) { VoiceIntake.copyToBatch(applicationContext, uris) }
            if (batch == null) {
                toast("Die Audios ließen sich nicht lesen")
            } else {
                val n = VoiceIntake.batchFiles(batch).size
                VoiceIntake.enqueue(applicationContext, batch)
                toast(
                    if (n == 1) "Sprachnachricht geht an Hikari"
                    else "$n Sprachnachrichten gehen an Hikari",
                )
            }
            finish()
        }
    }

    private fun toast(msg: String) = Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
}
