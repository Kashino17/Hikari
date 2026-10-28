package com.hikari.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lokal gespeicherte WhatsApp-Benachrichtigung im Hikari Nachrichten-Tresor.
 *
 * Funktionsprinzip:
 * Sobald WhatsApp eine Push-Benachrichtigung postet, sichert der
 * `WhatsAppNotificationListenerService` den Klartext hier in Room.
 * Wenn der Absender die Nachricht später "Für alle löscht" (WhatsApp ersetzt
 * die Benachrichtigung durch "Diese Nachricht wurde gelöscht") oder bearbeitet,
 * bleibt der ursprüngliche Text in [text] bzw. [originalText] erhalten.
 */
@Entity(
    tableName = "whatsapp_messages",
    indices = [
        Index(value = ["chatKey"]),
        Index(value = ["timestamp"]),
        Index(value = ["isDeleted"]),
        Index(value = ["isEdited"]),
    ],
)
data class WhatsAppMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stabile Kennung des Chats (z.B. "com.whatsapp:Max Mustermann"). */
    val chatKey: String,
    /** Anzeigename des Chats (Kontaktname oder Gruppenname). */
    val chatTitle: String,
    /** Absendername (im Einzelchat = chatTitle, in Gruppen der jeweilige Teilnehmer). */
    val senderName: String,
    /** True, wenn es sich um eine WhatsApp-Gruppe handelt. */
    val isGroup: Boolean = false,
    /** Quell-Paket ("com.whatsapp" oder "com.whatsapp.w4b"). */
    val packageName: String = "com.whatsapp",
    /** Aktueller gesicherter Nachrichtentext (wird bei Löschung NICHT überschrieben). */
    val text: String,
    /** Falls die Nachricht bearbeitet wurde: der allererste Originaltext vor der Bearbeitung. */
    val originalText: String? = null,
    /** JSON-Liste früherer Versionen bei Mehrfach-Bearbeitungen. */
    val editHistoryJson: String? = null,
    /** Wurde diese Nachricht vom Absender nachträglich für alle gelöscht? */
    val isDeleted: Boolean = false,
    /** Zeitpunkt (Epoch ms), zu dem die Löschung erkannt wurde. */
    val deletedAt: Long? = null,
    /** Wurde diese Nachricht nachträglich vom Absender bearbeitet? */
    val isEdited: Boolean = false,
    /** Zeitpunkt (Epoch ms), zu dem die letzte Bearbeitung erkannt wurde. */
    val editedAt: Long? = null,
    /** Medientyp falls erkannt: "photo", "voice", "video", "audio", "sticker", "document", "gif" oder null. */
    val mediaType: String? = null,
    /** Pfad zu einem aus der Benachrichtigung gesicherten Vorschaubild (EXTRA_PICTURE), falls vorhanden. */
    val mediaPreviewPath: String? = null,
    /** Pfad zum lokal gecachten Profil-/Gruppenbild aus der Benachrichtigung. */
    val avatarPath: String? = null,
    /** Zeitstempel der ursprünglichen Nachricht (Epoch ms). */
    val timestamp: Long,
    /** System-Notification-Key zur Zuordnung von Updates/Abbruch-Events. */
    val notificationKey: String? = null,
    /** Wurde die Nachricht in Hikaris WhatsApp-Ecke bereits angesehen? */
    val isReadInHikari: Boolean = false,
)
