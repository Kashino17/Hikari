package com.hikari.app.whatsapp

import android.Manifest
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hikari.app.MainActivity
import com.hikari.app.R
import com.hikari.app.domain.whatsapp.RawMessagingItem
import com.hikari.app.domain.whatsapp.RawNotificationPayload
import com.hikari.app.domain.whatsapp.WhatsAppGuardAlert
import com.hikari.app.domain.whatsapp.WhatsAppMediaVault
import com.hikari.app.domain.whatsapp.WhatsAppNotificationParser
import com.hikari.app.domain.whatsapp.WhatsAppRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Hintergrund-Wächter für WhatsApp & WhatsApp Business.
 *
 * Liest die Chat-Benachrichtigungen mit und reicht jeden Nachrichtenstapel an das
 * [WhatsAppRepository] weiter. Dort — und nur dort — wird entschieden, ob eine Nachricht
 * gelöscht oder bearbeitet wurde. Dieser Dienst trifft selbst keine solche Entscheidung:
 * insbesondere ist das Verschwinden einer Benachrichtigung nie ein Löschsignal, denn WhatsApp
 * entfernt sie genauso, wenn der Chat gelesen wurde (am Handy, im Web, per Antwort).
 */
@AndroidEntryPoint
class WhatsAppNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var repository: WhatsAppRepository

    @Inject
    lateinit var mediaVault: WhatsAppMediaVault

    private val alertScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Streng der Reihe nach, damit ein schneller Folge-Stapel den vorherigen nie überholt.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val processingScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(1),
    )

    // Mediensicherung wartet teils über eine Minute auf den Download und läuft daher nebenher.
    private val mediaScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val pendingClearJobs = ConcurrentHashMap<String, Job>()

    override fun onCreate() {
        super.onCreate()
        alertScope.launch {
            repository.guardAlerts.collect { alert ->
                showGuardNotification(alert)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Bereits sichtbare WhatsApp-Benachrichtigungen einlesen, damit der Vergleichsstapel
        // nach einem Neustart sofort wieder steht.
        runCatching {
            activeNotifications?.forEach { sbn ->
                if (sbn != null && WhatsAppNotificationParser.isSupportedPackage(sbn.packageName.orEmpty())) {
                    enqueueNotification(sbn)
                }
            }
        }
    }

    override fun onDestroy() {
        processingScope.cancel()
        mediaScope.cancel()
        alertScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (!WhatsAppNotificationParser.isSupportedPackage(pkg)) return
        enqueueNotification(sbn)
    }

    private fun buildRawPayload(sbn: StatusBarNotification): RawNotificationPayload? {
        val pkg = sbn.packageName ?: return null
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: Bundle.EMPTY

        val isSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val isOngoing = sbn.isOngoing || (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        // Sammel-Benachrichtigungen wiederholen nur die Chat-Benachrichtigungen.
        if (isOngoing || isSummary) return null

        return RawNotificationPayload(
            packageName = pkg,
            notificationKey = sbn.key,
            notificationTag = sbn.tag,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString(),
            tickerText = notification.tickerText?.toString(),
            conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            isGroupConversation = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false),
            isSummary = false,
            isOngoing = false,
            category = notification.category,
            postTime = sbn.postTime,
            messagingStyleItems = extractMessagingItems(notification, extras),
        )
    }

    private fun enqueueNotification(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        val notification = sbn.notification ?: return
        val payload = buildRawPayload(sbn) ?: return

        // WhatsApp hat die Benachrichtigung nur kurz entfernt und neu gepostet:
        // der Vergleichsstapel bleibt erhalten.
        sbn.key?.let { key -> pendingClearJobs.remove(key)?.cancel() }

        val rawChatName = payload.conversationTitle?.takeIf { it.isNotBlank() } ?: payload.title.orEmpty()
        val cleanChat = WhatsAppNotificationParser.cleanChatTitle(rawChatName.substringBefore(":"))
        val placeholderCount = payload.messagingStyleItems.count {
            WhatsAppNotificationParser.isDeletedPlaceholder(it.text)
        }
        val lastRowId = notification.extras?.getLong("last_row_id", -1L) ?: -1L

        processingScope.launch {
            val avatarPath = saveAvatarIfPresent(notification, pkg, sbn.tag ?: cleanChat)
            val result = runCatching { repository.processNotification(payload, avatarPath) }
                .onFailure { Log.w(TAG, "processNotification failed", it) }
                .getOrNull() ?: return@launch

            // Bewusst ohne Nachrichteninhalte: nur Struktur, damit sich das Verhalten von
            // WhatsApp per `adb logcat -s HikariWaGuard` nachvollziehen lässt.
            Log.d(
                TAG,
                "post chat=${chatLogId(sbn)} channel=${notification.channelId} items=${payload.messagingStyleItems.size} " +
                    "placeholders=$placeholderCount lastRow=$lastRowId ${result.summary} media=${result.pendingMedia.size}",
            )

            for (pending in result.pendingMedia) {
                mediaScope.launch {
                    val path = mediaVault.captureForMessage(pending)
                    Log.d(TAG, "media type=${pending.mediaType} row=${pending.rowId} saved=${path != null}")
                    if (path != null) repository.attachMedia(pending.rowId, path)
                }
            }
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int,
    ) {
        if (sbn == null) return
        val pkg = sbn.packageName.orEmpty()
        if (!WhatsAppNotificationParser.isSupportedPackage(pkg)) return
        val key = sbn.key ?: return
        val notification = sbn.notification
        if (notification != null && (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        Log.d(TAG, "removed chat=${chatLogId(sbn)} reason=$reason")

        // Trägt die entfernte Benachrichtigung selbst den Lösch-Platzhalter (WhatsApp aktualisiert
        // und entfernt in einem Zug), wird dieser letzte Stand noch ausgewertet.
        if (notification != null) {
            val extras = notification.extras ?: Bundle.EMPTY
            val carriesPlaceholder =
                WhatsAppNotificationParser.isDeletedPlaceholder(
                    extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                ) || extractMessagingItems(notification, extras).any {
                    WhatsAppNotificationParser.isDeletedPlaceholder(it.text)
                }
            if (carriesPlaceholder) enqueueNotification(sbn)
        }

        pendingClearJobs.remove(key)?.cancel()
        when (reason) {
            // Angetippt oder weggewischt: der Vergleichsstapel endet sofort.
            REASON_CLICK, REASON_CANCEL, REASON_CANCEL_ALL -> {
                processingScope.launch { repository.onNotificationCleared(key) }
            }

            // Von WhatsApp entfernt — meist weil der Chat gelesen wurde. Kein Löschsignal.
            // Der Stapel wird erst nach kurzer Wartezeit verworfen: postet WhatsApp die
            // Benachrichtigung sofort neu, bricht das den Job ab und der Vergleich läuft weiter.
            else -> {
                pendingClearJobs[key] = alertScope.launch {
                    delay(1_200L)
                    pendingClearJobs.remove(key)
                    processingScope.launch { repository.onNotificationCleared(key) }
                }
            }
        }
    }

    /** Kurze, nicht rückrechenbare Kennung eines Chats fürs Protokoll. */
    private fun chatLogId(sbn: StatusBarNotification): String =
        Integer.toHexString((sbn.tag ?: sbn.key ?: "").hashCode() and 0xFFFFF)

    private fun extractMessagingItems(
        notification: Notification,
        extras: Bundle,
    ): List<RawMessagingItem> {
        runCatching {
            val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            if (style != null && style.messages.isNotEmpty()) {
                return style.messages.mapNotNull { msg ->
                    val txt = msg.text?.toString()?.trim().orEmpty()
                    if (txt.isEmpty()) return@mapNotNull null
                    RawMessagingItem(
                        // Ohne Person stammt die Zeile vom Nutzer selbst (Antwort aus der Benachrichtigung).
                        sender = msg.person?.name?.toString(),
                        text = txt,
                        timestamp = msg.timestamp,
                        dataUri = msg.dataUri?.toString(),
                        dataMimeType = msg.dataMimeType,
                    )
                }
            }
        }

        // Fallback: Direktes Auslesen von EXTRA_MESSAGES (Parcelable-Array von Bundles)
        @Suppress("DEPRECATION")
        val rawArray: Array<Parcelable>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
        } else {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        }
        if (rawArray.isNullOrEmpty()) return emptyList()

        return rawArray.mapNotNull { parcelable ->
            val bundle = parcelable as? Bundle ?: return@mapNotNull null
            val msgText = bundle.getCharSequence("text")?.toString()?.trim().orEmpty()
            if (msgText.isEmpty()) return@mapNotNull null
            RawMessagingItem(
                sender = bundle.getCharSequence("sender")?.toString(),
                text = msgText,
                timestamp = bundle.getLong("time", 0L),
            )
        }
    }

    private fun saveAvatarIfPresent(
        notification: Notification,
        packageName: String,
        cleanChatTitle: String,
    ): String? {
        if (cleanChatTitle.isBlank()) return null
        return runCatching {
            val icon = notification.getLargeIcon() ?: return null
            val drawable = icon.loadDrawable(applicationContext) ?: return null
            val bitmap = drawableToBitmap(drawable, maxSize = 128) ?: return null
            val dir = File(applicationContext.filesDir, WhatsAppRepository.AVATAR_DIR).apply { mkdirs() }
            val safeKey = "${packageName}_${cleanChatTitle.lowercase()}"
                .replace(Regex("[^a-z0-9_-]"), "_")
                .take(64)
            val file = File(dir, "$safeKey.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            file.absolutePath
        }.getOrNull()
    }

    private fun drawableToBitmap(drawable: Drawable, maxSize: Int): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.coerceIn(1, maxSize)
        val height = drawable.intrinsicHeight.coerceIn(1, maxSize)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun showGuardNotification(alert: WhatsAppGuardAlert) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "whatsapp")
        }
        val pending = PendingIntent.getActivity(
            this,
            4300,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val (title, body, notifId) = when (alert) {
            is WhatsAppGuardAlert.MessageDeleted -> {
                val who = if (alert.isGroup) "${alert.senderName} (${alert.chatTitle})" else alert.senderName
                Triple(
                    "Gelöschte Nachricht gerettet",
                    "$who: „${alert.preservedText}“",
                    (4301 + (alert.chatTitle.hashCode() and 0x7FF)),
                )
            }
            is WhatsAppGuardAlert.MessageEdited -> {
                val who = if (alert.isGroup) "${alert.senderName} (${alert.chatTitle})" else alert.senderName
                Triple(
                    "Nachricht wurde bearbeitet",
                    "$who: „${alert.oldText}“ → „${alert.newText}“",
                    (5301 + (alert.chatTitle.hashCode() and 0x7FF)),
                )
            }
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(this).notify(notifId, notification)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WhatsApp-Wächter",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Benachrichtigt dich, wenn eine WhatsApp-Nachricht für alle gelöscht oder bearbeitet wurde."
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "whatsapp_guard_alerts"
        private const val TAG = "HikariWaGuard"

        /**
         * Prüft, ob Hikari in den Android-Einstellungen als NotificationListener freigeschaltet ist.
         */
        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            if (enabledPackages.contains(context.packageName)) return true
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            return flat.contains(context.packageName)
        }

        /**
         * Stellt nach einem App-Update oder beim Öffnen der WhatsApp-Ecke sicher,
         * dass Android den NotificationListenerService sofort wieder aktiv bindet.
         */
        fun ensureServiceBound(context: Context) {
            if (!isNotificationAccessGranted(context)) return
            runCatching {
                val cn = ComponentName(context, WhatsAppNotificationListenerService::class.java)
                requestRebind(cn)
            }
        }
    }
}
