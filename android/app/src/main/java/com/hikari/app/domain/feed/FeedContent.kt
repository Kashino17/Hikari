package com.hikari.app.domain.feed

import android.content.Context
import com.hikari.app.domain.russian.RussianGlossary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Ein russischer Beispielsatz in einer Vertiefung ([ru] mit "+" vor dem betonten Vokal). */
@Serializable
data class FeedDeepPhrase(val ru: String, val de: String, val note: String = "")

/** Ein Abschnitt einer Vertiefung — eigene Überschrift, eigener Gedanke. */
@Serializable
data class FeedDeepSection(
    val title: String = "",
    val text: String = "",
    /** Sätze zum Anhören (nur bei Russisch-Karten). */
    val phrases: List<FeedDeepPhrase> = emptyList(),
)

/**
 * Geschriebene Vertiefung zu einer Karte. Anders als die alte, aus den Kartenfeldern
 * zusammengesetzte Fassung bringt sie Neues: Hintergrund, Gegenargument, Beispiel.
 */
@Serializable
data class FeedDeepDive(
    val lead: String = "",
    val sections: List<FeedDeepSection> = emptyList(),
    val tryIt: String? = null,
    val takeaways: List<String> = emptyList(),
)

// ── Rohformate der Dateien unter assets/feed ────────────────────────────────

@Serializable
private data class QuoteJson(
    val id: String, val quote: String, val author: String, val era: String = "",
    val prompt: String = "", val deep: FeedDeepDive? = null,
)

@Serializable
private data class PuzzleJson(
    val id: String, val title: String, val category: String = "Logik", val question: String,
    val options: List<String>, val correctIndex: Int, val explanation: String,
    val skill: String = "", val deep: FeedDeepDive? = null,
)

@Serializable
private data class HistoryJson(
    val id: String, val dateLabel: String, val title: String, val description: String,
    val why: String = "", val deep: FeedDeepDive? = null,
)

@Serializable
private data class MentalModelJson(
    val id: String, val name: String, val category: String = "", val explanation: String,
    val example: String = "", val defense: String = "", val deep: FeedDeepDive? = null,
)

@Serializable
private data class ScienceJson(
    val id: String, val phenomenon: String, val question: String, val explanation: String,
    val detail: String = "", val deep: FeedDeepDive? = null,
)

@Serializable
private data class FinanceJson(
    val id: String, val title: String, val principle: String, val example: String = "",
    val rule: String = "", val deep: FeedDeepDive? = null,
)

@Serializable
private data class GeographyJson(
    val id: String, val question: String, val options: List<String>, val correctIndex: Int,
    val fact: String = "", val deep: FeedDeepDive? = null,
)

@Serializable
private data class SpeedMathJson(
    val id: String, val title: String, val shortcut: String, val explanation: String,
    val challenge: String, val result: String, val deep: FeedDeepDive? = null,
)

@Serializable
private data class ArtJson(
    val id: String, val title: String, val artist: String, val origin: String = "",
    val story: String, val deep: FeedDeepDive? = null,
)

@Serializable
private data class VocabularyJson(
    val id: String, val word: String, val wordType: String = "", val definition: String,
    val etymology: String = "", val sentence: String = "", val deep: FeedDeepDive? = null,
)

@Serializable
private data class PhilosophyJson(
    val id: String, val title: String, val scenario: String, val optionA: String, val optionB: String,
    val schoolA: String = "", val schoolB: String = "", val insight: String = "",
    val deep: FeedDeepDive? = null,
)

/** Liest die Inhaltsdateien in Karten um (pur, testbar). */
object FeedContentParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Datei je Themenbereich; Bereiche ohne Datei entstehen im Code (Atemübung, Russisch). */
    val FILES: Map<MindfulModuleType, String> = mapOf(
        MindfulModuleType.QUOTE to "quotes.json",
        MindfulModuleType.BRAIN_PUZZLE to "puzzles.json",
        MindfulModuleType.HISTORY to "history.json",
        MindfulModuleType.MENTAL_MODEL to "mental_models.json",
        MindfulModuleType.SCIENCE to "science.json",
        MindfulModuleType.FINANCE to "finance.json",
        MindfulModuleType.GEOGRAPHY to "geography.json",
        MindfulModuleType.SPEED_MATH to "speed_math.json",
        MindfulModuleType.ART_CULTURE to "art.json",
        MindfulModuleType.VOCABULARY to "vocabulary.json",
        MindfulModuleType.PHILOSOPHY to "philosophy.json",
    )

    /** Karten-ID: Bereich + ID aus der Datei — bleibt über Tage gleich, damit Gemerktes wiedergefunden wird. */
    fun cardId(module: MindfulModuleType, rawId: String) = "${module.id}:$rawId"

    fun parse(module: MindfulModuleType, text: String): List<MindfulCard> {
        fun id(raw: String) = cardId(module, raw)
        return when (module) {
            MindfulModuleType.QUOTE -> json.decodeFromString(ListSerializer(QuoteJson.serializer()), text).map {
                QuoteCardItem(id(it.id), it.quote, it.author, it.era, it.prompt, deep = it.deep)
            }
            MindfulModuleType.BRAIN_PUZZLE -> json.decodeFromString(ListSerializer(PuzzleJson.serializer()), text)
                .filter { it.options.size >= 2 && it.correctIndex in it.options.indices }
                .map {
                    BrainPuzzleCardItem(
                        id(it.id), it.title, it.category, it.question, it.options, it.correctIndex,
                        it.explanation, it.skill, deep = it.deep,
                    )
                }
            MindfulModuleType.HISTORY -> json.decodeFromString(ListSerializer(HistoryJson.serializer()), text).map {
                HistoryCardItem(id(it.id), it.dateLabel, it.title, it.description, it.why, deep = it.deep)
            }
            MindfulModuleType.MENTAL_MODEL -> json.decodeFromString(ListSerializer(MentalModelJson.serializer()), text).map {
                MentalModelCardItem(id(it.id), it.name, it.category, it.explanation, it.example, it.defense, deep = it.deep)
            }
            MindfulModuleType.SCIENCE -> json.decodeFromString(ListSerializer(ScienceJson.serializer()), text).map {
                ScienceCardItem(id(it.id), it.phenomenon, it.question, it.explanation, it.detail, deep = it.deep)
            }
            MindfulModuleType.FINANCE -> json.decodeFromString(ListSerializer(FinanceJson.serializer()), text).map {
                FinanceCardItem(id(it.id), it.title, it.principle, it.example, it.rule, deep = it.deep)
            }
            MindfulModuleType.GEOGRAPHY -> json.decodeFromString(ListSerializer(GeographyJson.serializer()), text)
                .filter { it.options.size >= 2 && it.correctIndex in it.options.indices }
                .map { GeographyCardItem(id(it.id), it.question, it.options, it.correctIndex, it.fact, deep = it.deep) }
            MindfulModuleType.SPEED_MATH -> json.decodeFromString(ListSerializer(SpeedMathJson.serializer()), text).map {
                SpeedMathCardItem(id(it.id), it.title, it.shortcut, it.explanation, it.challenge, it.result, deep = it.deep)
            }
            MindfulModuleType.ART_CULTURE -> json.decodeFromString(ListSerializer(ArtJson.serializer()), text).map {
                ArtCultureCardItem(id(it.id), it.title, it.artist, it.origin, it.story, deep = it.deep)
            }
            MindfulModuleType.VOCABULARY -> json.decodeFromString(ListSerializer(VocabularyJson.serializer()), text).map {
                VocabularyCardItem(id(it.id), it.word, it.wordType, it.definition, it.etymology, it.sentence, deep = it.deep)
            }
            MindfulModuleType.PHILOSOPHY -> json.decodeFromString(ListSerializer(PhilosophyJson.serializer()), text).map {
                PhilosophyCardItem(
                    id(it.id), it.title, it.scenario, it.optionA, it.optionB, it.schoolA, it.schoolB,
                    it.insight, deep = it.deep,
                )
            }
            MindfulModuleType.BREATHWORK -> BREATHWORK
            MindfulModuleType.LANGUAGE -> emptyList()
        }
    }

    /** Atemübungen brauchen keine Datei: fünf Rhythmen, jede mit eigener Vertiefung. */
    val BREATHWORK: List<MindfulCard> = listOf(
        BreathworkCardItem(
            id = cardId(MindfulModuleType.BREATHWORK, "box"),
            title = "Box-Atmung (4-4-4-4)",
            scientificBenefit = "Vier gleich lange Phasen geben dem Atem einen festen Takt. Das beruhigt, ohne müde zu machen.",
            deep = FeedDeepDive(
                lead = "Vier Phasen, alle gleich lang: ein, halten, aus, halten.",
                sections = listOf(
                    FeedDeepSection(
                        "Warum das Zählen hilft",
                        "Wer zählt, kann nicht gleichzeitig grübeln. Der gleichmäßige Takt bindet die Aufmerksamkeit an etwas Einfaches und Körperliches. Zugleich wird die Atmung langsamer als im Alltag: Statt zwölf bis sechzehn Atemzügen pro Minute sind es bei vier Sekunden je Phase nur noch knapp vier. Langsames Atmen ist eines der wenigen Mittel, mit denen du dein vegetatives Nervensystem direkt beeinflussen kannst.",
                    ),
                    FeedDeepSection(
                        "Woher die Übung kommt",
                        "Bekannt wurde die Box-Atmung durch Einsatzkräfte und Militär, wo sie vor und nach belastenden Situationen geübt wird. Ähnliche Muster mit gleich langen Phasen kennt aber schon die Yoga-Tradition. Der Name kommt vom Quadrat: vier Seiten, vier Phasen.",
                    ),
                    FeedDeepSection(
                        "Wenn es unangenehm wird",
                        "Das Halten nach dem Ausatmen fällt vielen am schwersten. Wird dir schwindlig oder entsteht Lufthunger, verkürze alle Phasen auf drei Sekunden oder lass die zweite Pause weg. Die Übung soll ruhig machen, nicht zum Durchhalten zwingen.",
                    ),
                ),
                tryIt = "Mach vier Runden, bevor du das nächste Mal eine schwierige Nachricht beantwortest.",
                takeaways = listOf("Gleich lange Phasen, ruhiger Takt.", "Bei Lufthunger kürzer zählen."),
            ),
        ),
        BreathworkCardItem(
            id = cardId(MindfulModuleType.BREATHWORK, "exhale"),
            title = "Langes Ausatmen (4-0-6-0)",
            inhaleSeconds = 4, holdInSeconds = 0, exhaleSeconds = 6, holdOutSeconds = 0,
            scientificBenefit = "Beim Ausatmen schlägt das Herz langsamer als beim Einatmen. Wer länger ausatmet, verschiebt das Gleichgewicht Richtung Ruhe.",
            deep = FeedDeepDive(
                lead = "Länger aus als ein: der einfachste Hebel, um herunterzufahren.",
                sections = listOf(
                    FeedDeepSection(
                        "Der Herzschlag folgt dem Atem",
                        "Dein Puls ist nicht gleichmäßig. Beim Einatmen wird er etwas schneller, beim Ausatmen etwas langsamer. Mediziner nennen das respiratorische Sinusarrhythmie; sie ist ein Zeichen eines gesunden, anpassungsfähigen Herzens. Verlängerst du das Ausatmen, verbringst du mehr Zeit in der Phase, in der der bremsende Teil des Nervensystems das Sagen hat.",
                    ),
                    FeedDeepSection(
                        "Wo du es schon kennst",
                        "Ein Seufzer der Erleichterung ist nichts anderes als ein langes Ausatmen. Auch Singen, Summen und langsames Sprechen verlängern die Ausatmung, was ein Grund dafür sein dürfte, dass sie beruhigend wirken.",
                    ),
                ),
                tryIt = "Atme heute beim Warten an einer Ampel dreimal doppelt so lang aus wie ein.",
                takeaways = listOf("Ausatmen bremst.", "Kein Anhalten nötig."),
            ),
        ),
        BreathworkCardItem(
            id = cardId(MindfulModuleType.BREATHWORK, "478"),
            title = "4-7-8-Atmung",
            inhaleSeconds = 4, holdInSeconds = 7, exhaleSeconds = 8, holdOutSeconds = 0,
            scientificBenefit = "Kurz ein, lange halten, sehr lange aus. Viele nutzen den Rhythmus zum Einschlafen.",
            deep = FeedDeepDive(
                lead = "Ein Rhythmus für den Abend: Das lange Ausatmen steht im Mittelpunkt.",
                sections = listOf(
                    FeedDeepSection(
                        "Herkunft",
                        "Der amerikanische Arzt Andrew Weil hat das Muster bekannt gemacht. Es geht auf Pranayama zurück, die Atemlehre des Yoga. Die Zahlen sind ein Verhältnis, keine Stoppuhr: Wem sieben Sekunden Halten zu lang sind, zählt schneller, behält aber das Verhältnis 4 zu 7 zu 8 bei.",
                    ),
                    FeedDeepSection(
                        "Was man ehrlich sagen kann",
                        "Dass langsames Atmen mit langem Ausatmen entspannt, ist gut belegt. Dass genau diese drei Zahlen besser wirken als andere langsame Rhythmen, ist dagegen nicht gezeigt. Nimm die Übung als angenehmes Ritual, nicht als Medikament.",
                    ),
                ),
                tryIt = "Probiere heute Abend im Bett vier Runden, bevor du zum Handy greifst.",
                takeaways = listOf("Das Verhältnis zählt, nicht die Sekunden.", "Vier Runden reichen am Anfang."),
            ),
        ),
        BreathworkCardItem(
            id = cardId(MindfulModuleType.BREATHWORK, "coherent"),
            title = "Ruhiger Gleichtakt (5-0-5-0)",
            inhaleSeconds = 5, holdInSeconds = 0, exhaleSeconds = 5, holdOutSeconds = 0,
            scientificBenefit = "Sechs Atemzüge pro Minute: In diesem Tempo schwingen Atem und Herzschlag besonders gleichmäßig miteinander.",
            deep = FeedDeepDive(
                lead = "Fünf Sekunden ein, fünf aus: sechs ruhige Atemzüge in der Minute.",
                sections = listOf(
                    FeedDeepSection(
                        "Warum gerade sechs pro Minute",
                        "Bei etwa sechs Atemzügen pro Minute fallen bei den meisten Erwachsenen zwei Rhythmen zusammen: der des Atems und der des Blutdruck-Regelkreises. Die Schwankung des Herzschlags wird dann besonders groß und gleichmäßig. Diese Herzfrequenzvariabilität gilt als Maß dafür, wie gut der Körper zwischen Anspannung und Erholung wechseln kann.",
                    ),
                    FeedDeepSection(
                        "Durch die Nase, in den Bauch",
                        "Atme durch die Nase und lass den Bauch sich heben, nicht die Schultern. Die Nase bremst den Luftstrom und macht langsames Atmen leichter. Es geht nicht darum, besonders tief zu atmen, sondern ruhig und gleichmäßig.",
                    ),
                ),
                tryIt = "Stell dir einen Timer auf zwei Minuten und atme nur in diesem Takt.",
                takeaways = listOf("Sechs Atemzüge pro Minute.", "Ruhig statt tief."),
            ),
        ),
        BreathworkCardItem(
            id = cardId(MindfulModuleType.BREATHWORK, "wake"),
            title = "Wach-Atmung (6-2-4-0)",
            inhaleSeconds = 6, holdInSeconds = 2, exhaleSeconds = 4, holdOutSeconds = 0,
            scientificBenefit = "Länger ein als aus: der umgekehrte Rhythmus, wenn du müde bist und klar werden willst.",
            deep = FeedDeepDive(
                lead = "Wer länger einatmet als ausatmet, wird eher wach als ruhig.",
                sections = listOf(
                    FeedDeepSection(
                        "Die Gegenrichtung",
                        "Beim Einatmen beschleunigt sich der Puls leicht. Betonst du diese Phase, verschiebst du das Gleichgewicht ein Stück Richtung Aktivierung. Das ist kein Ersatz für Schlaf, kann aber ein Nachmittagstief abfangen, ohne dass du zum dritten Kaffee greifst.",
                    ),
                    FeedDeepSection(
                        "Dosierung",
                        "Sechs bis acht Runden reichen. Kribbeln in den Fingern oder leichter Schwindel sind ein Zeichen, dass du zu viel abgeatmet hast: Atme dann ein paar Züge ganz normal weiter.",
                    ),
                ),
                tryIt = "Nutze die Übung heute einmal statt eines Kaffees und achte darauf, was sich ändert.",
                takeaways = listOf("Einatmen aktiviert.", "Wenige Runden genügen."),
            ),
        ),
    )
}

/** Lädt die Feed-Inhalte einmalig aus den Assets — offline, ohne Server. */
@Singleton
class FeedContentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = ConcurrentHashMap<MindfulModuleType, List<MindfulCard>>()

    fun cards(module: MindfulModuleType): List<MindfulCard> = cache.getOrPut(module) {
        val file = FeedContentParser.FILES[module]
            ?: return@getOrPut FeedContentParser.parse(module, "")
        runCatching {
            context.assets.open("feed/$file").bufferedReader().use { FeedContentParser.parse(module, it.readText()) }
        }.getOrDefault(emptyList())
    }

    /** Das kyrillische Alphabet mit Beispielwörtern (assets/russian/alphabet.json). */
    val alphabet: RuAlphabet by lazy {
        runCatching {
            context.assets.open("russian/alphabet.json").bufferedReader().use {
                Json { ignoreUnknownKeys = true }.decodeFromString(RuAlphabet.serializer(), it.readText())
            }
        }.getOrDefault(RuAlphabet())
    }

    /** Lädt das Wort-Glossar des Russisch-Kurses, damit Vertiefungen Wort für Wort erklären können. */
    fun warmGlossary() {
        RussianGlossary.load(context.assets)
    }

    /** Karte zu einer gemerkten ID — auch dann, wenn der Bereich gerade abgeschaltet ist. */
    fun find(id: String): MindfulCard? {
        val module = MindfulModuleType.entries.firstOrNull { id.startsWith("${it.id}:") } ?: return null
        return cards(module).firstOrNull { it.id == id }
    }
}
