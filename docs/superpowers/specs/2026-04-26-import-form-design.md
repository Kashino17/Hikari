# Import-Form Design Spec — Bulk-Paste mit Per-URL-Edit

**Date:** 2026-04-26
**Author:** Kadir + Claude (Brainstorming-Session)
**Status:** Draft, awaiting user approval before plan
**Target:** Android client (`android/app/`), Backend (`backend/`)

## Goal

Erweitere die existierende Import-Funktion in der Android-App: statt eines reinen Bulk-URL-Textareas soll jeder gepastete Link als editierbare Card auftauchen — mit Title, Series-Zuweisung, Staffel/Folge, Sprache und Untertitel. Defaults werden oben einmal eingestellt und greifen für alle Cards; Per-Card-Override jederzeit möglich.

## Decisions (Brainstorming Outcome)

| # | Decision | Choice |
|---|---|---|
| 1 | Single-URL vs Bulk | C — Bulk mit Per-URL-Edit (jede URL = Card mit eigenem Form) |
| 2 | Plattform-Scope | A — Nur Android (Web-Demo bleibt unverändert) |
| 3 | Series-Picker UX | B — Typeahead-Search mit „+ Erstellen: '<text>'"-Letzteintrag |
| 4 | Bulk-Defaults | A — „Apply to all"-Block oben, Per-Card-Override |

## Architecture Overview

```
Android · ChannelsScreen · ImportSheet (ModalBottomSheet)
├── URL-Textarea (rawInput)
├── SharedDefaults Card  (Series, Staffel, Sprache, Untertitel)
├── LazyColumn der ImportCards (eine pro URL)
│   └── ImportCard (collapsed | expanded | failed | loading)
└── Submit-Bar

ImportSheetViewModel  (HiltViewModel, scoped to ChannelsScreen)
├── onInputChanged → debounced reconcileUrls
├── analyzeVideo(url) parallel × 4 (Semaphore)
├── updateDefaults / updateCard / removeCard / toggleExpanded
└── submit() → repo.importVideosBulk(items)

Backend
├── POST /videos/analyze   (existing, unchanged) — yt-dlp meta + AI extract
├── POST /videos/import    (existing, unchanged) — single-URL legacy
├── POST /videos/import/bulk  (NEW) — { items: [{ url, metadata? }, ...] }
├── GET  /series           (verify exists or add) — id+title list
└── ManualMetadata erweitert um optionales `title` Override
```

**Datenfluss beim Submit:**
1. ViewModel mappt jede `Ready`-Card zu `BulkImportItem(url, metadata)` — Per-Card-Werte überschreiben Shared-Defaults; null-Felder fallen auf Default zurück.
2. `repo.importVideosBulk(items)` → `POST /videos/import/bulk`
3. Backend antwortet 202 mit `{ queued: N }`; Background-Worker arbeiten Concurrency 4.
4. UI: Toast „N Videos werden importiert", Sheet schließt sich.

**Was NICHT enthalten ist (out of scope für v1):**
- Job-Status-Polling pro Item (keine UI für „Welcher Import ist durchgelaufen, welcher ist gefailed nach dem Sheet-Close").
- Web-Demo-Pendant.
- Edit von bereits importierten Videos (das ist eine andere Feature-Kategorie).

## Data Model

Keine Schema-Änderungen. Alle Felder existieren bereits:

| Tabelle | Spalte | Zweck |
|---|---|---|
| `videos` | `series_id`, `season`, `episode`, `dub_language`, `sub_language`, `is_movie` | Per-Video-Metadata |
| `series` | `id`, `title` | Series-Liste für Typeahead |

### DTO-Erweiterungen

#### Backend: `ManualMetadata` (in `backend/src/import/manual-import.ts`)

```typescript
export interface ManualMetadata {
  title?: string;             // NEU — überschreibt yt-dlp-Title falls gesetzt
  seriesId?: string;
  seriesTitle?: string;
  season?: number;
  episode?: number;
  dubLanguage?: string;
  subLanguage?: string;
  isMovie?: boolean;
}
```

`importDirectLink` in derselben Datei: aktuell `const title = meta.title ?? meta.id;` — ändern auf `const title = manualMeta?.title ?? meta.title ?? meta.id;`.

#### Android DTOs (`data/api/dto/ImportVideosDto.kt`)

```kotlin
@Serializable
data class ImportItemMetadata(
    val title: String? = null,
    @SerialName("seriesId")     val seriesId: String? = null,
    @SerialName("seriesTitle")  val seriesTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("dubLanguage")  val dubLanguage: String? = null,
    @SerialName("subLanguage")  val subLanguage: String? = null,
    @SerialName("isMovie")      val isMovie: Boolean? = null,
)

@Serializable
data class BulkImportItem(
    val url: String,
    val metadata: ImportItemMetadata? = null,
)

@Serializable
data class BulkImportRequest(
    val items: List<BulkImportItem>,
)

@Serializable
data class BulkImportResponse(
    val queued: Int,
)

@Serializable
data class AnalyzeRequest(val url: String)

@Serializable
data class AnalyzeResponse(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    @SerialName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerialName("aiMeta")       val aiMeta: AiMeta? = null,
)

@Serializable
data class AiMeta(
    @SerialName("seriesTitle") val seriesTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("dubLanguage") val dubLanguage: String? = null,
    @SerialName("subLanguage") val subLanguage: String? = null,
    @SerialName("isMovie")     val isMovie: Boolean? = null,
)

@Serializable
data class SeriesItemDto(
    val id: String,
    val title: String,
)
```

Existing `ImportVideosRequest(urls: List<String>)` und das alte `importVideos(urls)` werden entfernt — der bestehende Bulk-Pfad sendet sowieso ein nicht passendes Body-Format an das Single-URL-Backend-Endpoint und ist defekt.

## Backend

### `POST /videos/import/bulk` (neu, in `videos.ts`)

```typescript
app.post<{ Body: { items: { url: string; metadata?: ManualMetadata }[] } }>(
  "/videos/import/bulk",
  async (req, reply) => {
    const items = req.body.items;
    if (!Array.isArray(items) || items.length === 0) {
      return reply.code(400).send({ error: "no items" });
    }

    const queue = [...items];
    const max = 4;
    const runners = Array.from({ length: max }, async () => {
      while (queue.length > 0) {
        const item = queue.shift();
        if (!item) break;
        try {
          await importDirectLink(deps.db, item.url, deps.videoDir, item.metadata);
        } catch (err) {
          app.log.error({ err, url: item.url }, "bulk import item failed");
        }
      }
    });
    Promise.all(runners).catch((err) => app.log.error({ err }, "bulk import failed"));

    return reply.code(202).send({ queued: items.length });
  },
);
```

### `GET /series` — verifizieren

Während Plan-Phase: `grep -n "/series" backend/src/api/videos.ts` und prüfen ob ein Listen-Endpoint existiert. Falls nicht:

```typescript
app.get("/series", async () => {
  return deps.db.prepare("SELECT id, title FROM series ORDER BY title").all();
});
```

### `manual-import.ts` Erweiterung

Eine Zeile in `importDirectLink`:

```typescript
const title = manualMeta?.title ?? meta.title ?? meta.id;
```

(Existing line: `const title = meta.title ?? meta.id;`.)

## Android UI

### `ImportSheet` (`ui/channels/ChannelsScreen.kt`, erweitert)

Layout-Struktur:

```
ModalBottomSheet  (skipPartiallyExpanded = true)
└── Column (Modifier.verticalScroll, padding=16dp)
    ├── Header  ("Videos importieren", schließen-X rechts)
    ├── OutlinedTextField  (rawInput, multi-line, "URLs hier einfügen…")
    │
    ├── if (cards.isNotEmpty()):
    │   ├── SharedDefaultsBlock  (Card)
    │   │   ├── SeriesTypeahead   (applies to all)
    │   │   ├── Staffel-Number    (applies to all, leer = keine)
    │   │   ├── DubLanguage-Dropdown
    │   │   └── SubLanguage-Dropdown
    │   │
    │   └── Cards.forEach: ImportCard
    │
    └── Sticky Submit-Bar
        ├── Status-Text  ("Analyzing 3 of 8…" oder "8 bereit")
        └── Button  ("8 Importieren")
```

### `ImportCard` Composable

```kotlin
@Composable
private fun ImportCard(
    card: ImportCardState,
    onToggleExpand: () -> Unit,
    onRemove: () -> Unit,
    onPatch: (ImportCardState.Ready.() -> ImportCardState.Ready) -> Unit,
    allSeries: List<SeriesItemDto>,
) {
    when (card) {
        is ImportCardState.Loading -> LoadingCard(card.url)
        is ImportCardState.Failed -> FailedCard(card, onRemove, onRetry = { /* ... */ })
        is ImportCardState.Ready -> ReadyCard(card, onToggleExpand, onRemove, onPatch, allSeries)
    }
}
```

**`ReadyCard` collapsed:** Thumbnail (40×60dp) + Title (truncated) + URL-Host + optionaler Episode-Badge + Chevron.

**`ReadyCard` expanded:** Alle Felder editierbar (siehe Sektion 3 oben). `AnimatedVisibility` für Expand/Collapse.

**Movie-Toggle:** Switch mit Label „Film" — wenn an, werden Series + Staffel + Episode-Felder via `AnimatedVisibility(visible = !card.isMovie)` ausgeblendet.

**Lösch-Button:** kleines IconButton oben rechts in der expanded Card. Beim Tap: `onRemove()` — entfernt Card UND zugehörige Zeile aus `rawInput`.

### Series-Typeahead

Nutzt `ExposedDropdownMenuBox` (Material3, `@OptIn(ExperimentalMaterial3Api::class)`):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesTypeahead(
    value: String,
    selectedSeriesId: String?,
    allSeries: List<SeriesItemDto>,
    onChange: (input: String, seriesId: String?, seriesTitle: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(value, allSeries) {
        allSeries.filter { it.title.startsWith(value, ignoreCase = true) }
    }
    val exactMatch = matches.firstOrNull { it.title.equals(value, ignoreCase = true) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                // Tippen löst seriesId-Bindung; bleibt nur seriesTitle gesetzt bis User aus der Liste klickt.
                onChange(it, null, it.takeIf { it.isNotBlank() })
                expanded = true
            },
            label = { Text("Serie") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            matches.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.title) },
                    onClick = {
                        onChange(s.title, s.id, null)
                        expanded = false
                    },
                )
            }
            // "Erstellen"-Option, nur wenn Input nicht exakt matched und nicht leer
            if (value.isNotBlank() && exactMatch == null) {
                DropdownMenuItem(
                    text = { Text("+ Erstellen: \"$value\"", color = HikariAccent) },
                    onClick = {
                        onChange(value, null, value)
                        expanded = false
                    },
                )
            }
        }
    }
}
```

Wichtig: `seriesId` und `seriesTitle` sind im State mutually exclusive — entweder existing-id (User hat aus Liste gewählt) oder neue-title (User tippt frei). Backend `importDirectLink` interpretiert das genauso.

### `ImportSheetViewModel` (siehe Sektion 4 oben für Vollcode)

**Wichtige Implementation-Hinweise (für den Plan):**
- `init`-Block muss korrekt schreiben: nach `repo.listSeries()` sollte `_uiState.update { it.copy(allSeries = result) }` mit dem gefetchten Wert gesetzt werden, nicht mit dem alten State (in der Brainstorming-Skizze war ein offensichtlicher Tippfehler).
- `Semaphore(4)` aus `kotlinx.coroutines.sync.Semaphore`.
- `removeCard` modifiziert `rawInput` damit der Reconcile-Effect die URL nicht beim nächsten Tippen wieder hinzufügt.
- `submit()` setzt nach Erfolg `_uiState.value = ImportSheetUiState(allSeries = old.allSeries)` — Series-Liste bleibt, Rest wird zurückgesetzt.

### Repository-Erweiterung (`ChannelsRepository.kt`)

```kotlin
suspend fun analyzeVideo(url: String): AnalyzeResponse =
    api.analyzeVideo(AnalyzeRequest(url))

suspend fun importVideosBulk(items: List<BulkImportItem>): Int =
    api.importVideosBulk(BulkImportRequest(items)).queued

suspend fun listSeries(): List<SeriesItemDto> =
    api.listSeries()
```

Existing `importVideos(urls: List<String>)` (broken signature) wird **entfernt** — keine Backwards-Compatibility nötig, keiner ruft's außer der `ImportSheet` selbst.

### API-Erweiterung (`HikariApi.kt`)

```kotlin
@POST("videos/analyze")
suspend fun analyzeVideo(@Body req: AnalyzeRequest): AnalyzeResponse

@POST("videos/import/bulk")
suspend fun importVideosBulk(@Body req: BulkImportRequest): BulkImportResponse

@GET("series")
suspend fun listSeries(): List<SeriesItemDto>
```

Existing `@POST("videos/import")` mit `ImportVideosRequest(urls: List<String>)` wird **entfernt** (broken legacy).

## Error Handling

| Fehler | Strategie |
|---|---|
| Analyze 4xx/5xx | Card → `Failed(url, msg)`. Inline rotes Banner + Retry + Lösch-X. |
| Analyze Timeout (>60s) | OkHttp 60s-Timeout greift → `Failed`. Retry funktioniert. |
| Bulk-Submit 4xx | Top-Banner mit Fehlermeldung. Cards bleiben editierbar. |
| Bulk-Submit Network-Fehler | `submitError = "Backend nicht erreichbar"`. User retried. |
| Series-Liste-Fetch fehlgeschlagen | Stille Degradierung — Typeahead funktioniert nur als Text-Input für neue Series. Kleine Toast-Notice. |
| Backend-Job wirft beim `importDirectLink` (z.B. yt-dlp findet keinen Stream) | Backend loggt; UI hat optimistisches `queued: N`-Feedback. v1: kein Job-Status-Polling. |
| Duplikat-URLs | `parseUrls` macht `.distinct()`. Backend prüft `videoId`-Existenz und returnt `duplicate`-Status (geloggt, nicht im UI). |
| User schließt Sheet während Analyze | In-flight Calls laufen zu Ende. `DisposableEffect` cancelt ViewModel-Job beim Dismiss als Cleanup. |
| Movie + Series gleichzeitig gesetzt | Backend erlaubt das (Anthology). UI vereitelt nicht. |

## Testing

### Backend

- **`backend/src/import/manual-import.test.ts`** (existing) — neuer Test-Case: `importDirectLink` mit `manualMeta.title` als Override → DB-Row hat den Override-Title statt yt-dlp-Title.
- **`backend/tests/api/videos-bulk.test.ts`** (neu) — Tests für `/videos/import/bulk`:
  - 202 mit `queued: items.length` bei valid Body (3 items)
  - 400 bei leerem `items`-Array
  - 400 wenn Body kein `items`-Feld hat
  - Concurrency-Test: 8 Items → niemals mehr als 4 gleichzeitig in `importDirectLink` (mock + counter)

### Android

- **`ImportSheetViewModelTest.kt`** (neu) — Turbine + MockK:
  - `onInputChanged` parsed URLs nach 500ms-Debounce, baut Loading-Cards
  - Analyze-Erfolg → Card → Ready mit AI-Meta-Defaults
  - Analyze-Fehler → Card → Failed
  - `reconcileUrls` keept stable Cards bei rawInput-Edit (kein Re-Analyze für unveränderte URLs)
  - `removeCard` entfernt Card UND aus rawInput
  - `submit` mapped Per-Card-Werte mit Default-Fallback korrekt zu `BulkImportRequest`
  - `submit` setzt `submitting = true` während Network, `submitError` bei Failure

### Manual E2E

1. ChannelsScreen → „+ Videos importieren" → Sheet öffnet
2. 5 OnePiece-VOE-URLs paste (eine pro Zeile)
3. Defaults oben: Series „One Piece" wählen, Staffel 1, Sprache „Deutsch"
4. Cards 1–5 erscheinen mit auto-Title, Default-Werten
5. Card 3 expanden → Episode 7 setzen → Title leicht editieren
6. Submit → Toast „5 Videos werden importiert", Sheet schließt
7. Library → 5 neue Videos unter „One Piece" Series-Gruppe sichtbar

## Implementation Constraints

1. **Compose Material3 ExposedDropdownMenuBox** ist `@ExperimentalMaterial3Api`. Korrekt annotieren am Composable.
2. **`LazyColumn` innerhalb `ModalBottomSheet`** funktioniert nur, wenn der äußere Container kein `verticalScroll` hat — sonst „infinite height"-Crash. Lösung: ImportSheet-Content in `Column(verticalScroll)`, ImportCards als Items in derselben Column (manuelle `forEach`, nicht LazyColumn).
3. **Existierender `ImportSheet`-Code wird signifikant umgebaut** — nicht erhalten als Migration, sondern voll ersetzt. Da es keine User-Daten dort gibt, ist das risikofrei.

## Open Questions Deferred to the Plan

1. Ob `GET /series` schon existiert — checken im Plan, falls nein: 4 Zeilen Backend-Code dazu.
2. Welche Sprachen die Dropdowns vorbefüllen (Deutsch, Englisch, Japanisch + „Sonstige" mit Text-Input?). Plan-Detail.
3. Genauer Style-Match der existierenden Card-Komponenten in ChannelsScreen — Plan inspiziert.
