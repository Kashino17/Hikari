package com.hikari.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted

/**
 * Einheitliche Rückfrage vor jedem Löschen. Der Text sagt immer, WO etwas
 * verschwindet (nur Handy oder auch PC) — genau diese Unklarheit hat dazu
 * geführt, dass ein Tipp aufs Handy-Symbol wie ein Versehen wirkte.
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    confirmLabel: String = "Löschen",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HikariBg,
        title = { Text(title, color = HikariText) },
        text = { Text(message, color = HikariTextMuted) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel, color = HikariDanger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen", color = HikariTextMuted) }
        },
    )
}

/** Text für „nur vom Handy entfernen" — Videos, Folgen, Filme. */
fun deviceOnlyMessage(itemTitle: String?): String {
    val what = itemTitle?.let { "„$it“" } ?: "Die Datei"
    return "$what wird von diesem Handy gelöscht. Auf deinem PC bleibt sie erhalten, " +
        "du kannst sie jederzeit wieder herunterladen."
}
