package com.hikari.app.whatsapp

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import com.hikari.app.domain.whatsapp.WhatsAppNotificationParser
import com.hikari.app.player.PlayerTrace
import java.security.MessageDigest

/**
 * Messprotokoll: hält roh fest, was WhatsApp mit seinen Benachrichtigungen tut — welche
 * Nachrichten im Stapel stehen, wann WhatsApp neu postet, wann und warum es entfernt.
 *
 * Grund: Der Wächter wurde zehn Versionen lang auf Annahmen gebaut, was WhatsApp beim Löschen
 * und Bearbeiten postet. Das hier misst es am echten Handy. Die Zeilen landen über
 * [PlayerTrace] auf dem Mac in ~/.hikari/client-trace.log (Präfix `wa`).
 *
 * Ohne Nachrichteninhalte: Texte erscheinen nur als kurzer Fingerabdruck + Länge, sodass man
 * „gleiche Nachricht, anderer Text“ erkennt, ohne mitzulesen. Klartext nur für Lösch-Platzhalter
 * und reine Medien-Bezeichnungen („📷 Foto“) — die sind Teil von WhatsApp, nicht der Unterhaltung.
 */
object WhatsAppProbe {

    fun posted(sbn: StatusBarNotification) = runCatching {
        val n = sbn.notification ?: return@runCatching
        val extras = n.extras ?: Bundle.EMPTY
        val msgs = messages(extras, Notification.EXTRA_MESSAGES)
        val historic = messages(extras, Notification.EXTRA_HISTORIC_MESSAGES)
        val line = buildString {
            append("wa post ").append(head(sbn))
            append(" when=").append(n.`when`)
            append(" flags=").append(Integer.toHexString(n.flags))
            append(" ch=").append(n.channelId)
            append(" grp=").append(n.group?.let(::fp))
            append(" summary=").append((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0)
            append(" alertOnce=").append((n.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0)
            append(" lastRow=").append(extras.getLong("last_row_id", -1L))
            append(" title=").append(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.let(::fp))
            append(" text=").append(shape(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()))
            append(" n=").append(msgs.size).append(" hist=").append(historic.size)
        }
        PlayerTrace.log(line)
        PlayerTrace.log("wa   keys=[${extras.keySet().sorted().joinToString(",")}]")
        msgs.forEachIndexed { i, m -> PlayerTrace.log("wa   #$i ${message(m)}") }
        historic.forEachIndexed { i, m -> PlayerTrace.log("wa   h$i ${message(m)}") }
    }

    fun removed(sbn: StatusBarNotification, reason: Int, context: String = "") = runCatching {
        val n = sbn.notification
        val extras = n?.extras ?: Bundle.EMPTY
        val msgs = messages(extras, Notification.EXTRA_MESSAGES)
        val summary = n != null && (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        PlayerTrace.log(
            "wa removed ${head(sbn)} reason=$reason(${reasonName(reason)}) summary=$summary n=${msgs.size} " +
                "last=${msgs.lastOrNull()?.let(::message)} $context",
        )
    }

    fun note(text: String) {
        PlayerTrace.log("wa $text")
    }

    private fun head(sbn: StatusBarNotification): String =
        "pkg=${if (sbn.packageName == "com.whatsapp.w4b") "biz" else "wa"} id=${sbn.id} " +
            "tag=${sbn.tag?.let(::fp)} post=${sbn.postTime}"

    private fun message(b: Bundle): String {
        val text = b.getCharSequence("text")?.toString()
        val sender = b.getCharSequence("sender")?.toString()
        val personName = runCatching {
            @Suppress("DEPRECATION")
            (b.get("sender_person") as? android.app.Person)?.name?.toString()
        }.getOrNull()
        val nested = b.getBundle("extras")
        return buildString {
            append("t=").append(b.getLong("time", 0L))
            append(" from=").append((personName ?: sender)?.let(::fp) ?: "ICH")
            append(" text=").append(shape(text))
            b.getString("type")?.let { append(" mime=").append(it) }
            if (b.containsKey("uri")) append(" uri=").append(uriShape(b))
            append(" keys=[").append(b.keySet().sorted().joinToString(",")).append(']')
            if (nested != null && !nested.isEmpty) {
                append(" ex=[").append(nested.keySet().sorted().joinToString(",")).append(']')
            }
        }
    }

    private fun uriShape(b: Bundle): String {
        @Suppress("DEPRECATION")
        val uri = b.get("uri")?.toString() ?: return "null"
        // Nur Schema + Anbieter, kein Dateiname.
        return uri.substringBefore("://") + "://" + uri.substringAfter("://").substringBefore('/')
    }

    private fun messages(extras: Bundle, key: String): List<Bundle> {
        @Suppress("DEPRECATION")
        val raw: Array<Parcelable>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(key, Parcelable::class.java)
        } else {
            extras.getParcelableArray(key)
        }
        return raw?.mapNotNull { it as? Bundle }.orEmpty()
    }

    /** Text als Fingerabdruck — Klartext nur für WhatsApp-eigene Bezeichnungen. */
    private fun shape(text: String?): String {
        if (text == null) return "null"
        val clean = WhatsAppNotificationParser.stripInvisibleMarks(text)
        return when {
            WhatsAppNotificationParser.isDeletedPlaceholder(clean) -> "DEL«$clean»"
            WhatsAppNotificationParser.isBareMediaLabel(clean) -> "MEDIA«$clean»"
            else -> {
                val marker = if (EDIT_MARKERS.any { clean.contains(it, ignoreCase = true) }) "+EDITWORT" else ""
                "${fp(clean)}/${clean.length}$marker"
            }
        }
    }

    private fun fp(s: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return d.take(3).joinToString("") { "%02x".format(it) }
    }

    private fun reasonName(r: Int): String = when (r) {
        1 -> "CLICK"
        2 -> "CANCEL"
        3 -> "CANCEL_ALL"
        8 -> "APP_CANCEL"
        9 -> "APP_CANCEL_ALL"
        10 -> "LISTENER_CANCEL"
        12 -> "GROUP_SUMMARY_CANCELED"
        18 -> "SNOOZED"
        19 -> "TIMEOUT"
        else -> "?"
    }

    private val EDIT_MARKERS = listOf("bearbeitet", "edited")
}
