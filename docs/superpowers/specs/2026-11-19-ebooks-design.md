# E-Books in Hikari — Design (19.11.2026)

## Absicht

Kadir möchte in Hikari neben Manga auch **E-Books lesen**. Die Bücher sollen kostenlos und legal sein, in deutscher Sprache und vollständig. Gemeint sind **gemeinfreie Klassiker** und **frei veröffentlichte Sachbücher**.

**Themen:** alles, was es gibt. Ausdrücklich genannt: Mystery, Thriller und Krimi, Romane, Wissen.

**Was er sich wünscht:**
- **Stöbern in der App, kein Browser.** Die Angebote der Quellen sind in der App erfasst und immer aktuell, solange Internet da ist.
- **Kategorien und Suche** über alle Quellen hinweg.
- **Laden und lesen** direkt in der App.
- **Ein Leser, der sich wie ein Kindle anfühlt.** Er ist bewusst anders als der Manga-Leser.

Kadir hat am 19.11. **Weg A** gewählt: Der Mac sammelt die Kataloge, die App zeigt sie an.

## Ziele

1. **Ein Katalog aller Quellen.** Ein einheitlicher deutscher Katalog über alle Quellen, mit gemeinsamen Kategorien (Genres und Sachgebiete), Titelbildern, Autor, Jahr, Länge und Kurzbeschreibung. Er wird jede Nacht aktualisiert, und neue Bücher erscheinen von selbst.
2. **Stöbern in der App.** Startseite mit Reihen je Kategorie und „Neu dazugekommen“. Dazu Filter (Kategorie, Quelle, Länge), Volltextsuche über Titel, Autor und Beschreibung, und Sortieren nach Beliebtheit, Titel, Autor oder Jahr.
3. **Nur ganze Bücher.** Jeder Eintrag ist ein vollständiges Werk. Leseproben, Auszüge, Zeitschriftenhefte, Einzelgedichte, Inhaltsverzeichnis-Seiten und Bruchstücke werden beim Sammeln ausgefiltert.
4. **Laden.**
   - Ein Tippen lädt das Buch auf den Mac (Bibliothek, bleibt) und aufs Handy.
   - Geladene Bücher sind auf dem Handy ohne Netz und ohne Mac lesbar.
5. **Kindle-artiger Leser.**
   - Echte Seiten mit Umblättern (Tippen am Rand oder Wischen).
   - Lesefarben Papier, Sepia und Nacht, dazu Schriftart, Schriftgröße, Zeilenabstand und Rand.
   - Inhaltsverzeichnis, Lesezeichen und Markierungen.
   - Fortschritt als Prozent und „noch ca. X Min. im Kapitel“.
   - Er merkt sich die Stelle; ein markiertes Wort lässt sich über das Android-Textmenü nachschlagen (Systemfunktion, kein eigenes Wörterbuch — *Rems Entscheidung*).
6. **Meine Bücher.** Weiterlesen (zuletzt gelesen), Geladen, Merkliste, Gelesen.
7. **Fold 7.**
   - Aufgeklappt zeigt der Leser zwei Buchseiten nebeneinander.
   - Der Katalog nutzt die Breite (mehr Spalten).

## Nicht-Ziele (diese Runde)

- **Onleihe und Bibliotheksausleihe, DRM-Bücher, kostenpflichtige Shops.**
- **Bestseller und Neuerscheinungen** gibt es legal nicht gratis. Kadir hat zugestimmt, dass sie wegfallen.
- **Stöbern ohne Mac** (Weg C). Das kann später kommen, wenn er es vermisst.
- **Vorlesen per Sprachausgabe** und **Übersetzen.**
- **Eigene Dateien importieren.** Nicht verlangt; das wäre eine kleine Erweiterung für später.

## Anti-Ziele (wäre ein Fehlschlag, auch wenn es technisch läuft)

- **Illegale Quellen.** Seiten, die urheberrechtlich geschützte Bücher gratis verteilen, kommen nie in den Katalog.
- **Leseproben oder Bruchstücke** als „Buch“ im Katalog.
- **Ein Browser oder eine fremde Webseite**, die sich zum Stöbern öffnet.
- **Ein Leser, der wie eine Webseite scrollt** statt Seiten zu blättern, oder der beim Wiederöffnen die Stelle verliert.
- **Grau in Grau.** Der Bereich bekommt eine eigene Farbe (siehe feedback-ui-design-geschmack). Keine Emojis.

## Rahmen

- **Repo und Releases sind öffentlich.** Keine Schlüssel und keine Zugangsdaten im Code.
- **Server:** Hikari-Backend auf dem Mac (Fastify, better-sqlite3, node-cron, Port 3939), Tests mit System-Node 25.
- **App:** Kotlin/Compose. Der Manga-Bereich ist nativ (`ui/manga/*`). WebView-Inhalte laufen nach dem Muster des Russisch-Teils (`assets/…`, `WebViewAssetLoader`).
- **Netzlage, gemessen am 19.11.:**
  - `gutenberg.org` antwortete 503 mit `retry-after: 3600`; der offizielle Spiegel `gutenberg.pglaf.org` lieferte den Katalog.
  - DOAB und OAPEN antworten mit 403 „address not allowed“.
  - Wikisource-API und `ws-export.wmcloud.org` antworten.
  - Kadir reist; die Mac-Adresse kann gesperrt oder gedrosselt sein. Jede Quelle braucht Wiederholen mit Pause, und eine fehlende Quelle darf den Katalog nicht leeren (letzter guter Stand bleibt).
- **Höflich sammeln:** eigener User-Agent ohne persönliche Daten, wenige Anfragen pro Sekunde, nachts, Pausen bei 429/503 nach `retry-after`.

## Wege (Approaches)

**A — Mac sammelt, App zeigt (gewählt).**
- Der Server holt nachts die Kataloge aller Quellen und führt sie in einer SQLite-Tabelle mit einheitlichen Kategorien zusammen.
- Die App stöbert über die Server-API. Bücher landen auf Mac und Handy.
- Vorteile: schnelle gemeinsame Suche, einheitliche Kategorien, Ausfiltern von Bruchstücken an einer Stelle, gleiches Muster wie der Manga-Abgleich.
- Nachteil: Stöbern braucht den Mac. Geladenes ist trotzdem offline lesbar.

**B — App fragt jede Quelle direkt.** Verworfen:
- Jede Quelle sortiert anders.
- Gemeinsame Suche und Kategorien wären langsam und lückenhaft.
- Gutenberg drosselt.

**C — Wie A plus Katalog-Kopie auf dem Handy.** Vertagt: mehr Aufwand für einen seltenen Fall.

## Was entschieden ist

### Quellen (erste Runde)

| Quelle | Was | Katalog | Buchdatei |
|---|---|---|---|
| Project Gutenberg | ~2.500 deutsche Werke: Prosa, Krimi, Drama, Lyrik, Sachbuch, Kinderbuch | `pg_catalog.csv` (Spiegel `gutenberg.pglaf.org`, Hauptseite als Zweitweg); Felder Title, Authors, Subjects, Bookshelves, LoCC, Language | EPUB3 mit Bildern; Titelbild aus dem Cache |
| Wikisource (de) | Vollständige, geprüfte Werke (Romane, Erzählungen, Märchen, Sachtexte) | MediaWiki-API; nur Werk-Hauptseiten aus Werk-Kategorien, Bearbeitungsstand „fertig“ oder „korrigiert“, keine Unterseiten und Zeitschriftenhefte | EPUB über `ws-export` |
| bpb (Bundeszentrale für politische Bildung) | Frei herunterladbare Sachbücher | Bücher-Shop-Seiten auswerten; nur Titel mit freiem EPUB- oder PDF-Download | EPUB, sonst PDF |
| Open Access (DOAB/OAPEN) | Deutschsprachige Sachbücher unter freier Lizenz | REST-API. Ist derzeit für die Mac-Adresse gesperrt; als Quelle bauen, die bei 403 still pausiert und es später wieder versucht | Meist PDF, teils EPUB |

**Quellen-Adapter-Muster:** Wie bei `manga/sources`: eine Schnittstelle je Quelle (`listCatalog()`, `fetchBook()`), damit weitere Quellen ohne Umbau dazukommen. Zeno.org und das Deutsche Textarchiv bieten nur HTML oder TEI und kommen später mit eigener EPUB-Umwandlung.

### Nur in Deutschland gemeinfrei (*Rems Entscheidung, folgt aus „nur legal“*)

- **Warum:** Gutenberg richtet sich nach US-Recht. Manche deutschen Titel dort sind in Deutschland noch geschützt (Schutzfrist: 70 Jahre nach dem Tod; deshalb war Gutenberg 2018–2020 in Deutschland gesperrt).
- **Regel:** Alle beteiligten Personen müssen spätestens **1955** gestorben sein. Das gilt für Autor, Übersetzer, Herausgeber und Illustrator. Die Grenze rückt jedes Jahr am 1. Januar um ein Jahr weiter.
- **Bei Gutenberg:** Die Lebensdaten stehen im Feld Authors (`Name, Vorname, 1821-1881 [Translator]`).
- **Unbekanntes Todesjahr:** Das Werk wird ausgeschlossen.
- **Ausnahmen:** bpb und Open Access (freie Lizenzen wie CC-BY) und Wikisource (prüft selbst auf deutsche Gemeinfreiheit).
- **Folge:** Übersetzte Krimis (Doyle, Poe) kommen nur mit Übersetzungen, deren Übersetzer früh genug gestorben sind.
- **Tests** mit echten Katalogzeilen.

### Ganze Bücher statt Bruchstücke

Jeder Adapter filtert vor dem Eintrag:
- **Sprache Deutsch.**
- **Mindestlänge** (ca. 8.000 Wörter oder ein entsprechender Dateiumfang; Lyrikbände und Dramen haben eigene, niedrigere Grenzen).
- **Keine Teile von Sammelwerken oder Zeitschriften**, keine „Leseprobe“ oder „Auszug“ im Titel oder in den Metadaten.
- **Länge prüfen beim ersten Laden:** Nach dem Laden einer Datei wird die echte Länge gemessen. Ist das Werk zu kurz, wird der Eintrag versteckt.

### Einheitliche Kategorien

Ein fester Satz deutscher Kategorien, auf den die Quell-Schlagwörter abgebildet werden (Tabelle im Code, mit Tests):
- **Belletristik:** Roman, Krimi & Mystery, Abenteuer, Fantastik & Grusel, Historischer Roman, Liebe, Humor, Erzählungen, Märchen & Sagen, Drama, Lyrik, Kinder & Jugend.
- **Sachbuch:** Geschichte, Philosophie, Wissenschaft & Natur, Politik & Gesellschaft, Reisen, Biografien & Briefe, Religion, Kunst & Musik, Psychologie.

Ein Buch kann mehrere Kategorien haben. Was sich nicht zuordnen lässt, landet unter „Weitere“.

### Server

- **Tabellen:** `ebooks` (Katalog), `ebook_categories`, `ebook_library` (Geladenes und Lesestand-Sicherung) und eine FTS5-Suche.
- **Abgleich:** nachts per node-cron (nach dem Manga-Abgleich) plus Knopf „Jetzt aktualisieren“.
  - Ein Fehler einer Quelle betrifft nur diese Quelle.
  - Neue Einträge bekommen `addedAt` (für „Neu dazugekommen“).
- **Dateien und Titelbilder:** Buchdateien unter `~/.hikari/ebooks/`, Titelbilder in den vorhandenen Cover-Ordner.
  - Fehlt ein Titelbild, baut der Server ein schlichtes typografisches Titelbild aus Titel und Autor.
- **API (unter `/api/ebooks`):**
  - Stöbern: Startseite mit Reihen, Liste mit Filtern und Sortierung, Suche.
  - Buch: Detailseite, Datei laden.
  - Bibliothek: Merkliste, Gelesen, Lesestand sichern und holen.
- **Lesestand auf dem Mac sichern:** Stelle, Lesezeichen, Markierungen, Merkliste, Gelesen. So verliert eine Neuinstallation nichts (Lehre aus dem Lernstand-Verlust).
  - Speichern: ein JSON je Buch.
  - Zusammenführen: Einträge mit Zeitstempel. Je Lesezeichen oder Markierung gewinnt die neuere Änderung; Löschen ist ein Eintrag mit `deleted`.
  - *Rems Entscheidung.*
- **Ohne Mac geht alles Eigene trotzdem.**
  - Was man auf dem Handy merkt, als gelesen markiert, liest oder markiert, wird sofort lokal gespeichert und später nachgereicht.
  - Ohne Mac geht nur das Laden neuer Bücher nicht, und der Knopf sagt das.
  - *Rems Entscheidung.*
- **Wird ein Buch nach dem Laden aus dem Katalog genommen** (zu kurz, Quelle weg), bleibt es auf Handy und Mac lesbar; nur der Katalog zeigt es nicht mehr. *Rems Entscheidung.*

### App

- **Einstieg.** Die Profil-Kachel „Manga“ wird zu **„Lesen“**, mit dem Umschalter **Manga | Bücher** oben (Manga bleibt unverändert).
  - Bücher-Startseite: Weiterlesen, Neu dazugekommen, Reihen je Kategorie, Suche oben, Filter-Chips.
  - Buchdetail: Titelbild, Beschreibung, Länge, Quelle, Laden/Lesen, Merken.
- **Leser:** WebView mit einem bewährten freien EPUB-Leser (Kandidat: **foliate-js**, MIT, macht Seitenumbruch und Spalten). Er liegt in den Assets, damit er offline läuft.
  - Bedienung: Tippen links/rechts oder Wischen blättert, Tippen in der Mitte zeigt Leiste und Einstellungen.
  - Dauer-Einstellungen: Lesefarben Papier/Sepia/Nacht mit Kindle-artiger Schrift (Serifen, Literata o. ä., eingebettet), Größe, Zeilenabstand, Rand.
  - Aufgeklappter Fold: zwei Spalten. Lautstärketasten blättern (abschaltbar).
  - Statusleiste aus, ruhiger Rand.
- **PDF-Bücher** (Open Access, bpb ohne EPUB): einfache Seitenansicht mit Android `PdfRenderer` (Zoom, Blättern, Stelle merken). Ein Kindle-Umbruch ist bei PDF nicht möglich; die Detailseite zeigt das als „PDF“ an.
- **Offline:** Geladene Bücher liegen im App-Speicher; Liste und Leser funktionieren ohne Mac. Ist der Mac nicht erreichbar, zeigt der Katalog einen klaren Hinweis statt leer zu sein.
- **Bereichsfarbe:** eigene Farbe für „Lesen/Bücher“ im AreaColors-System.

## Was dem Builder überlassen ist

- Genaue Schwellen der Mindestlänge je Gattung (mit Tests an echten Beispielen).
- Abbildungstabelle Quell-Schlagwort → Kategorie (Startfassung aus den Gutenberg-Regalen und Wikisource-Kategorien).
- Wahl zwischen foliate-js und einer vergleichbaren Bibliothek nach kurzem Test der Seitenumbruch-Qualität. Readium Kotlin ist der native Ausweichweg.
- Wie „Beliebtheit“ gemessen wird (Gutenberg-Downloadzahlen, wo vorhanden; sonst Titel).
- Aufteilung in Arbeitsschritte und Reihenfolge (Vorschlag: Server-Katalog → Stöbern → Leser → weitere Quellen).

## Prüfung, bevor es fertig heißt

- **Server-Tests:** Abbildung der Kategorien, Bruchstück-Filter (echte Beispiele: Zeitschriftenheft, Einzelgedicht, Leseprobe werden verworfen), Ausfall einer Quelle leert den Katalog nicht.
- **Echter Abgleich gegen alle Quellen auf dem Mac**, mit Zahlen je Quelle und Kategorie im Log.
- **Im Emulator:** Stöbern, Suchen, Laden, Lesen mit Blättern, Farben und Wiederöffnen an derselben Stelle; Lesen ohne Mac; Breitbild-Ansicht.
