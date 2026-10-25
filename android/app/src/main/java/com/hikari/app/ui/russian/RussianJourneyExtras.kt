package com.hikari.app.ui.russian

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.domain.russian.*
import java.time.LocalDate
import java.util.Locale

@Composable internal fun ConversationReview(vm: RussianConversationViewModel, p: RuChatProgress, heard: String, spoken: Boolean,
    onText: (String) -> Unit, onListen: () -> Unit, onStop: () -> Unit, listening: Boolean, onPlay: (String) -> Unit, onClear: () -> Unit) {
    val due = RuChatLearning.due(p, vm.today())
    val card = due.firstOrNull()
    var revealed by remember(card?.phrase?.ru) { mutableStateOf(false) }
    var checked by remember(card?.phrase?.ru) { mutableStateOf<Boolean?>(null) }
    var archiveOpen by rememberSaveable { mutableStateOf(false) }
    JourneyTitle("Wörter, die bleiben.", small = true)
    ChatBody("${due.size} fällig · ${p.cards.size} persönliche Ausdrücke")
    if (card == null) {
        ChatBody(if (p.cards.isEmpty()) "Nach deinem ersten Mini-Gespräch kommen deine Wörter automatisch hierher. Morgen erinnern wir uns gemeinsam daran." else "Heute ist nichts fällig. Neue Wörter kommen frühestens morgen wieder. Du kannst deinen Wortschatz unten jederzeit nachschlagen.")
    } else {
        ConversationPanel {
            ChatSmall("Wie sagst du das auf Russisch?")
            Text(card.phrase.de, color = JourneyInk, fontSize = 23.sp)
            if (revealed || checked != null) {
                ChatBody(card.phrase.ru)
                TextButton(onClick = { onPlay(card.phrase.ru) }) { Text("Ausdruck anhören") }
            }
            OutlinedTextField(heard, onText, modifier = Modifier.fillMaxWidth(), label = { Text("Erinnern, sprechen, dann prüfen") })
            JourneyButton(if (listening) "Aufnahme beenden" else "🎙  Aus dem Kopf sagen", onClick = if (listening) onStop else onListen)
            if (checked == null) {
                TextButton(onClick = { revealed = true }) { Text("Ich brauche die Lösung") }
                JourneyButton("Erinnerung prüfen", enabled = heard.isNotBlank() && !listening) {
                    checked = RuAnswerCheck.speech(card.phrase.ru, listOf(heard)).passed && !revealed && !archiveOpen
                }
            } else {
                ChatBody(if (checked == true) "Aus dem Kopf erinnert!" else "Noch nicht sicher. Vergleiche den Ausdruck und probiere ihn morgen wieder.")
                ChatSmall("Dies prüft den gemerkten Ausdruck anhand des Textes. Andere Formulierungen können ebenfalls richtig sein. Für den Aufstieg zählt hier nur selbstständiges Sprechen; getippte Antworten bleiben Übung. Es ist kein Aussprachetest.")
                JourneyButton("Weiter") {
                    vm.gradeReview(card, checked == true && spoken)
                    onClear()
                }
            }
        }
    }
    if (p.cards.isNotEmpty()) {
        TextButton(onClick = { archiveOpen = !archiveOpen; if (card != null) revealed = true }) { Text(if (archiveOpen) "Sprachschatz verbergen" else "Sprachschatz nachschlagen (zeigt Lösungen)") }
        if (archiveOpen) p.cards.takeLast(10).reversed().forEach { entry ->
            ChatSmall("${entry.phrase.ru} · ${entry.phrase.de} · wieder am ${LocalDate.ofEpochDay(entry.due)}")
        }
    }
}

@Composable internal fun ConversationPlan(vm: RussianConversationViewModel, p: RuChatProgress) {
    val s = vm.settings
    val today = LocalDate.now()
    val target = runCatching { LocalDate.parse(s.targetDate) }.getOrNull()
    val plan = target?.let { RuStudyPlanner.calculate(today, it, s.daysPerWeek, s.minutesPerDay, s.blockMinutes) }
    val uri = LocalUriHandler.current
    var details by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(false) }
    JourneyLabel("KLEINE SCHRITTE, DIE IN DEINEN TAG PASSEN")
    JourneyTitle("Ein guter Rhythmus\nfür dich.")
    JourneyBody("Wann möchtest du sicherer sprechen? Wir planen deine verfügbare Zeit und schauen, was du später wirklich erinnerst.", muted = true)
    JourneyCard {
        ConversationChoices("Mein Wunschzeitraum", listOf("3" to "3 Monate", "6" to "6 Monate", "12" to "1 Jahr"), "") { vm.configure(s.copy(targetDate = today.plusMonths(it.toLong()).toString())) }
        OutlinedTextField(s.targetDate, { vm.configure(s.copy(targetDate = it.take(10))) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), label = { Text("Wunschdatum · JJJJ-MM-TT") }, isError = s.targetDate.isNotEmpty() && plan == null)
        ConversationChoices("Zeit pro Lerntag", listOf("15" to "15 Min", "30" to "30 Min", "45" to "45 Min", "60" to "1 Stunde"), s.minutesPerDay.toString()) { vm.configure(s.copy(minutesPerDay = it.toInt())) }
        TextButton(onClick = { custom = !custom }) { Text("Eigenes Zeitbudget") }
        if (custom) {
            var minutes by remember(s.minutesPerDay) { mutableStateOf(s.minutesPerDay.toString()) }
            OutlinedTextField(minutes, { value ->
                minutes = value.filter(Char::isDigit).take(3)
                minutes.toIntOrNull()?.takeIf { it in 5..600 }?.let { vm.configure(s.copy(minutesPerDay = it)) }
            }, label = { Text("Minuten pro Lerntag · 5–600") }, modifier = Modifier.fillMaxWidth())
        }
        ConversationChoices("Lerntage pro Woche", listOf("3" to "3 Tage", "5" to "5 Tage", "6" to "6 Tage", "7" to "Täglich"), s.daysPerWeek.toString()) { vm.configure(s.copy(daysPerWeek = it.toInt())) }
        ConversationChoices("Eine kleine Übungsrunde", listOf("5" to "5 Min", "10" to "10 Min", "20" to "20 Min", "30" to "30 Min"), s.blockMinutes.toString()) { vm.configure(s.copy(blockMinutes = it.toInt())) }
    }
    plan?.let {
        JourneyCard(color = JourneySage) {
            JourneyLabel("DEIN ZEITBUDGET BIS ZUM WUNSCHDATUM")
            JourneyTitle("${String.format(Locale.GERMAN, "%.1f", it.availableHours)} Stunden", small = true)
            JourneyBody("${it.studyDays} Lerntage mit je ${s.minutesPerDay} Minuten. Verteile deine ${it.blocksPerDay} Übungsblöcke nach Möglichkeit über den Tag.")
            JourneySmall("Der letzte Block kann kürzer sein. Pausen kommen dazu. Diese Rechnung ist dein Kalender, keine Vorhersage, wann du fließend sprichst.")
        }
    }
    val seconds = p.activeSecondsByDay[vm.today()] ?: 0L
    JourneyCard {
        JourneyLabel("DEIN TATSÄCHLICHER FORTSCHRITT")
        JourneyBody("Heute ${seconds / 60} Minuten aktiv geübt")
        LinearProgressIndicator(progress = { (seconds / (s.minutesPerDay * 60f)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        JourneySmall("${p.cards.count { it.delayedCorrect > 0 }} Ausdrücke an späteren Tagen erinnert. ${p.foundation.completed.size} Mini-Gespräche abgeschlossen.")
        JourneySmall("Letzte 7 Tage: " + (6L downTo 0L).joinToString(" · ") { "${(p.activeSecondsByDay[vm.today() - it] ?: 0) / 60}m" })
    }
    JourneyBody("Warum hier kein Fließend-Countdown steht")
    JourneySmall("Studien stützen verteiltes Üben und späteres Abrufen. Sie liefern keine verlässliche Formel für deine persönlichen Stunden bis zur Sprachbeherrschung. Auch eine feste tägliche Gehirn-Grenze wäre erfunden.")
    TextButton(onClick = { details = !details }) { Text(if (details) "Einordnung schließen" else "Studien, Stundenrichtwert & Grenzen") }
    if (details) {
        JourneyCard {
            JourneyBody("Was der FSI-Richtwert bedeutet")
            JourneySmall("Der archivierte Ausbildungsrichtwert nennt für Russisch etwa 1.100 Unterrichtsstunden für englischsprachige US-Diplomaten bis ILR S3/R3. Das ist keine kontrollierte Studie, keine Empfehlung für dich und keine Umrechnung in App-Stunden oder CEFR. Eigenarbeit kommt hinzu; vollständiges Filmverständnis ist damit nicht garantiert.")
            plan?.let {
                JourneyBody("Rein rechnerisch: ${String.format(Locale.GERMAN, "%.1f", it.referenceHoursPerStudyDay)} Unterrichtsstunden pro geplantem Lerntag.")
                if (it.exceedsBudget) JourneySmall("Das übersteigt dein Zeitbudget. Es bedeutet nicht, dass dein persönliches Ziel exakt 1.100 Stunden braucht.")
                if (it.referenceHoursPerStudyDay > 24) JourneySmall("Dieses Referenzpensum passt nicht einmal in einen Kalendertag. Es ist kein Lernvorschlag.")
            }
            TextButton(onClick = { uri.openUri(RuStudyPlanner.SOURCE) }) { Text("Offizielle FSI-Quelle") }
        }
        JourneySmall("Die Blocklänge wählst du selbst. 10 oder 20 Minuten sind keine wissenschaftlich festgelegten Optimalwerte. Zehn Stunden sind auch nicht automatisch zehnmal so wirksam wie eine Stunde. Prüfe nach einer Pause und am nächsten Tag, was hängen geblieben ist.")
        TextButton(onClick = { uri.openUri("https://doi.org/10.1111/lang.12479") }) { Text("Metaanalyse zu verteiltem Sprachlernen") }
        JourneySmall("Die aktive Zeit zählt ohne KI-Warten und lange Inaktivität. Erinnerungsproben und selbstständige Antworten steuern die Übungsstufe, sind aber kein normierter Sprachtest.")
    }
}

@Composable internal fun ConversationEvidence() {
    val uri = LocalUriHandler.current
    Text("Weniger raten. Mehr erinnern.", color = JourneyInk, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
    ChatBody("Die Lernprinzipien sind erforscht. Genau diese App, ihre KI und ihre Stufenschwellen sind noch nicht durch eine Wirksamkeitsstudie validiert.")
    val evidence = listOf(
        Triple("Erst ein Beispiel, dann selbst antworten", "Du lernst zuerst die Wörter, übst mit einer sichtbaren Vorlage und versuchst es danach selbst. Forschung zu schrittweise reduzierter Beispielhilfe stützt diesen Ansatz beim Fertigkeitserwerb; sie beweist nicht die optimale Russisch-Lektion oder eine feste Wortzahl.", "https://doi.org/10.1037/0022-0663.95.4.774"),
        Triple("Selbst abrufen & später wiederholen", "Erst lernst du Wörter mit Bedeutung und einem Beispiel. Im gemeinsamen Versuch siehst du die Antwort, danach rufst du sie selbst ab. Gemerkte Ausdrücke kommen an späteren Tagen wieder. Der konkrete Wiederholungsplan ist eine App-Entscheidung, kein erwiesenes Optimum.", "https://doi.org/10.1111/lang.12479"),
        Triple("Eine hilfreiche Korrektur, dann selbst versuchen", "Rückmeldung kann Sprachlernen verbessern. Deshalb gibt es einen gezielten Lernimpuls und einen eigenen Verbesserungsversuch.", "https://doi.org/10.1017/S0272263109990520"),
        Triple("Gleiche Szene, neuer Versuch", "Wiederholte Sprechaufgaben können Ausdruck und Genauigkeit stärken. Der Transfer auf völlig neue Gespräche muss zusätzlich geübt werden.", "https://doi.org/10.1016/j.system.2025.103868"),
        Triple("Hören mit dosierter Textstütze", "Text kann Hörverstehen und Wortlernen unterstützen. Im Einstieg stehen Russisch und Deutsch immer nebeneinander. Ein KI-Dialog ersetzt keine authentischen Filmszenen oder menschlichen Stimmen.", "https://doi.org/10.1016/j.system.2013.07.013"),
        Triple("Als Anfänger hören und mitlesen", "Auch eine Studie mit Russischlernenden spricht für Textstützen beim Hörverstehen. Im Einstieg siehst du Frage, Bedeutung und ein Antwortmodell. Erst danach wird die Antwort verborgen.", "https://www.lltjournal.org/item/10125-44203/"),
        Triple("Freude, Interesse & wenig Druck", "Lernfreude hängt positiv mit Lernleistung zusammen. Das beweist nicht, dass Emotion allein am schnellsten zur Sprachbeherrschung führt.", "https://doi.org/10.14746/ssllt.2022.12.2.3"),
        Triple("KI als Übungspartner", "Aktuelle Forschung zeigt Potenzial, aber auch uneinheitliche Studien und fehlerhafte Rückmeldungen. Beurteile Aussagen kritisch und übe auch mit Menschen.", "https://doi.org/10.1007/s10791-026-10015-1"),
        Triple("Spracherkennung ist kein Ausspracheprüfer", "Ein korrekt erkanntes Wort belegt keine perfekte Aussprache. Dieser Modus bewertet den bestätigten Text; für Laute gibt es Hikari-Lautlabor.", "https://doi.org/10.1017/S0958344023000113"),
        Triple("Verteilen statt nur anhäufen", "Eine Metaanalyse von 2025 unterstützt verteilte Übung. Daraus folgt keine universelle Tagesobergrenze und keine Formel für eine persönliche Fließend-Frist.", "https://doi.org/10.3390/bs15060771"),
    )
    evidence.forEach { (title, explanation, url) -> ConversationPanel {
        Text(title, color = JourneyGreen, fontWeight = FontWeight.SemiBold)
        ChatBody(explanation)
        TextButton(onClick = { uri.openUri(url) }) { Text("Studie öffnen") }
    } }
    ChatSmall("Recherche: 26.10.2026. Überwiegend Zweitsprachenforschung, oft Englisch; Übertragung auf Russisch und diesen Modus ist begründet, aber nicht direkt bewiesen. Keine Funktion garantiert vollständiges Verstehen aller Filme oder Lieder.")
}

@Composable internal fun ConversationChoices(title: String, values: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    ChatSmall(title)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (id, label) -> FilterChip(selected == id, onClick = { onSelect(id) }, label = { Text(label) }, modifier = Modifier.weight(1f)) }
        } }
    }
}
@Composable internal fun ConversationPanel(learner: Boolean = false, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(if (learner) JourneySage else androidx.compose.ui.graphics.Color.White, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
@Composable internal fun ChatBody(text: String) { Text(text, color = JourneyInk, fontSize = 15.sp, lineHeight = 22.sp) }
@Composable internal fun ChatSmall(text: String) { Text(text, color = JourneyMuted, fontSize = 12.sp, lineHeight = 18.sp) }
