# Russisch-Audios für Hikari

Quelle aller Inhalte: `android/app/src/main/assets/russian/course.json`
(32 Lektionen, Betonung als `+` vor dem Vokal, z. B. `Спас+ибо`).

## Werkzeuge (alle offline, Open Source)

| Schritt | Werkzeug | Lizenz |
|---|---|---|
| Sprachausgabe | [Vosk TTS 0.9 multi](https://huggingface.co/drakulavich/vosk-tts-ru-0.9-multi) (alphacep), 5 Sprecher | Apache 2.0 |
| Betonungs-Gegenprobe | Aussprachewörterbuch des Vosk-Modells (2 Mio. Einträge) | Apache 2.0 |
| Verständlichkeit | Whisper large-v3 (`mlx-community/whisper-large-v3-mlx`) | MIT |
| Vokalreduktion | wav2vec2 Phonemerkenner (`facebook/wav2vec2-xlsr-53-espeak-cv-ft`) | Apache 2.0 |

## Ablauf

```bash
uv venv -p 3.12 ttsenv && source ttsenv/bin/activate
uv pip install torch numpy soundfile vosk-tts huggingface_hub mlx-whisper transformers librosa
python -c "from huggingface_hub import snapshot_download as d; d('drakulavich/vosk-tts-ru-0.9-multi', local_dir='models/vosk')"

python validate_course.py                   # Kyrillisch sauber, jede Betonung gesetzt, IDs gültig
python generate.py --model models/vosk      # alle Clips (normal + langsam), Best-of-4
python qa.py --only-normal                  # Whisper-Abschlussprüfung → qa_report.json
```

`generate.py` setzt jede Betonung exakt (Phoneme a1/o1 …) und meldet in `report.json`
jedes Wort, dessen Betonung vom Wörterbuch abweicht — so wurden пережива́й, по-мо́ему
und у́чишь im Kurs korrigiert. Jeder Clip wird bis zu viermal erzeugt; behalten wird ein
Kandidat, den Whisper exakt versteht und in dem der Phonemerkenner genau so viele [o]
hört, wie betonte о/ё im Text stehen (unbetontes о muss zu [a] reduziert sein).

Der App-Test `RussianCourseTest.audiosPassenZumAktuellenText` schlägt fehl, sobald ein
Text in `course.json` geändert wurde, ohne die Audios neu zu erzeugen.


## Lautlabor-Clips

`generate_sounds.py` erzeugt aus `assets/russian/sounds.json` die Einzelwort-Clips
(`audio/lab_<sha1(ru)[:10]>.ogg`, normal + `_slow`) für Minimalpaare:

```bash
python generate_sounds.py --model models/vosk
```

## Phonem-Aussprachebewertung (Server)

Der Hikari-Server vergleicht die Aufnahme des Lernenden mit dem Muttersprachler-Clip
auf Phonem-Ebene (wav2vec2-xlsr-53-espeak, IPA). Einmalig einrichten:

```bash
./setup_phoneme_server.sh        # legt ~/.hikari/phoneme-env an (~2 GB inkl. Modell)
```

Danach startet `backend/src/api/russian.ts` `phoneme_server.py` bei Bedarf selbst
(Port 3940, nach 15 Min Leerlauf beendet). Ohne Einrichtung antwortet
`POST /russian/pronunciation` mit 503 und die App zeigt einen Hinweis.

## Vertiefung (Deepdive) und Satzbau-Muster

Inhalte stehen als Python-Quellen in `deepdive_src/` (Satz-Vertiefungen) und `pattern_src/` (Satzbau-Muster);
die JSON-Dateien in `assets/russian/` werden daraus gebaut — nie von Hand bearbeiten.

```bash
python validate_deepdive.py                                   # Schreibung und Betonung der Deepdive-Quellen
python generate_deepdive.py --model ~/.hikari/tts/models/vosk  # deepdive.json + fehlende Clips (dd_<sha1>)
python generate_patterns.py --check --model ~/.hikari/tts/models/vosk   # Betonung gegen das Wörterbuch
python generate_patterns.py --model ~/.hikari/tts/models/vosk           # patterns.json + fehlende Clips
```

Clip-Name = `dd_` + sha1(Text mit "+")[:10]; Partnerinnen-Zeilen hashen `"p|" + Text`. Die Tests
`RussianDeepdiveTest` und `RussianPatternsTest` schlagen fehl, wenn ein Clip fehlt.
Einrichtung des Werkzeugs: `uv venv -p 3.12 ~/.hikari/tts/ttsenv`, dort `numpy soundfile vosk-tts huggingface_hub`
installieren und das Modell `drakulavich/vosk-tts-ru-0.9-multi` nach `~/.hikari/tts/models/vosk` laden.

## Podcastkurs (100 Lektionen)

`build_podcast_course.py` erzeugt `assets/russian/podcast.json` aus den 100 öffentlichen
RusslandJournal-Lektionsseiten und den nummerierten YouTube-Folgen. Pro Lektion enthält das
Asset Video-ID, Quellenlink, Lernziel, kompakte Notizen und geprüfte russisch-deutsche
Lernphrasen. Die App spricht diese Phrasen über den vorhandenen russischen TTS-Fallback.

```bash
python3 build_podcast_course.py          # Asset neu aufbauen (Netz + yt-dlp erforderlich)
python3 build_podcast_course.py --check  # vorhandenes Asset offline validieren
```

## Jahreskurs: Plan, Lektionen, Gesprächstage

Der Sprechkurs ist ein Jahresplan mit 365 Plan-Tagen in Blöcken zu fünf Tagen: vier Lektionen, dann ein
Gesprächstag ohne neuen Stoff. Die Lektionen stehen in `assets/russian/course.json` (fortlaufend nummeriert),
der Plan in `plan_src/year_plan.py` → `assets/russian/plan.json`:

```
Lektion L  → Plan-Tag L + (L-1)//4          Plan-Tag d → Gesprächstag, wenn d % 5 == 0
```

Gebaut sind die Blöcke 1–8 (Plan-Tage 1–40, Lektionen 1–32). Alles danach steht im Plan nur mit Titel und
Lernziel und ist in der App gesperrt sichtbar.

### Neue Kurstage bauen (nächster Block = vier Lektionen)

1. In `plan_src/year_plan.py` die Titel und Lernziele des nächsten Blocks lesen. Soll eine Lektion ein
   Satzbau-Muster lehren, das Muster in `pattern_src/` anlegen und in `PATTERN_OF_LESSON` eintragen.
2. Je Lektion eine Datei `dNN.json` schreiben (Format und Regeln: Kopf von `check_lesson.py`; Vorbild: die
   Lektionen 21–32 in course.json): 14 Sätze, 3–4 Fragen mit `replies`, Laut-Tipp, Satzbau-Hinweis, Rollenspiel
   mit 8 Zeilen. Männliche Form in `ru`, weibliche in `ruF`. Betonung als `+` VOR dem betonten Vokal.
3. Prüfen, bis keine Fehler bleiben, und jede PRÜFEN-Zeile am Satz entscheiden:
   `python check_lesson.py d33.json d34.json d35.json d36.json`
   (Das Wörterbuch irrt selten, aber es irrt: переживаю und обожал stehen dort falsch. Im Zweifel ein anderes Wort nehmen.)
4. Einbauen: `python add_lessons.py d33.json …` (hängt an course.json an, trägt Themen ins Satzbuch ein —
   vorher das Thema der Lektion in `THEMES` ergänzen).
5. In `plan_src/year_plan.py` `BUILT_BLOCKS` erhöhen, die vier vorgeplanten Lektionen des Blocks durch `[]` ersetzen
   (Titel und Ziel kommen ab jetzt aus course.json), dann `python build_plan.py`.
6. Wort-für-Wort-Bedeutungen: `python build_glosses.py extract <ordner>` → jede Zeile glossieren →
   `python build_glosses.py merge <ordner>` → `python build_glosses.py check`.
7. Audio in der Kursstimme (normal + langsam gesprochen), mit Hörprüfung:
   `python generate_missing.py --model ~/.hikari/tts/models/vosk --only course`, danach `--finalize`.
   Die ungelösten Clips im Bericht `missing_report_course_*.json` ansehen.
8. App-Tests: in `RussianCourseTest` die Zahl der Lektionen anpassen, dann `./gradlew test`.

## Fehlende Clips ergänzen

`generate_missing.py` erzeugt nur, was fehlt oder dessen Text sich geändert hat, und rührt geprüfte Clips nie an:

```bash
python stress_podcast.py --model ~/.hikari/tts/models/vosk     # Betonung der Podcast-Phrasen (einmalig bzw. nach Neuaufbau)
python generate_missing.py --model ~/.hikari/tts/models/vosk --only course|deepdive|patterns|podcast [--fast-judge]
python generate_missing.py --finalize                           # manifest.json nachziehen
python generate_missing.py --prune                              # verwaiste dd_-Clips löschen
```

Nicht mehrere Läufe parallel starten: die Hörprüfung teilt sich die Grafikeinheit, parallel ist langsamer als nacheinander.
Seit 2026-11 haben alle Kurssätze, Vertiefungen, Podcast-Phrasen und jede Kombination der Satzbau-Bausteine
einen Clip in normalem und in langsam gesprochenem Tempo.

## Kursstimme auf Abruf

Sätze, die erst beim Üben entstehen (Kombi-Meister, Gespräch mit Anja, eigene Sätze), spricht
`tts_server.py` in denselben Stimmen. Der Hikari-Server startet ihn bei Bedarf (Port 3941, Leerlauf-Ende nach
zehn Minuten) und legt Ergebnisse in `~/.hikari/tts/cache/` ab:

```
GET /russian/voice/status?warm=1                    → { available, ready }
GET /russian/voice?text=…&voice=m|f|p&rate=0.7      → audio/ogg, oder 503 solange das Modell lädt
```

Die App fällt bei 503 oder ohne Verbindung sofort auf die Gerätestimme zurück.
