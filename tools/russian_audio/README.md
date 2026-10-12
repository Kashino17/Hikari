# Russisch-Audios für Hikari

Quelle aller Inhalte: `android/app/src/main/assets/russian/course.json`
(14 Tage, Betonung als `+` vor dem Vokal, z. B. `Спас+ибо`).

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
