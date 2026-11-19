#!/bin/zsh
# Klangbetten für Achtsamkeit (Meditation, Yoga, Atem) bauen.
#   tools/mind_sounds/build.sh <arbeitsordner>          (lädt fehlende Rohdateien, baut alle Betten)
# Ergebnis: ~/.hikari/fitness/mind-sounds/<id>.ogg — 24 min, Ende in den Anfang überblendet
# (nahtlose Schleife), auf −20 LUFS gebracht, Opus 72 kbit/s. Katalog: backend/fitness/mind/sounds.json.
#
# Liste: id  YouTube-ID  Ausschnitt-Start  Versatz-im-Ausschnitt(s)
# Prüfung (gemacht 20.11.2026): Spektrum je Bett angesehen, Whisper auf 3×30 s → keine Sprache.
set -e
WORK=${1:?arbeitsordner}
OUT=~/.hikari/fitness/mind-sounds
L=1440   # Länge der Schleife (s)
X=8      # Überblendung Ende → Anfang (s)
P=0.5    # Polster vorn/hinten (s) — Schleife in der App: [P, P+L] (sounds.json: loop)
mkdir -p $WORK/raw $OUT
while read id vid st off; do
  [[ -z $id || $id == \#* ]] && continue
  raw=$(ls $WORK/raw/$id.* 2>/dev/null | head -1)
  if [[ -z $raw ]]; then
    # Standard-Client: nur er bietet reine Audiospuren; der Ausschnitt-Download läuft in Echtzeit.
    timeout 2400 yt-dlp -4 -q --no-warnings -f "251/140/bestaudio" --download-sections "*$st-inf" -o "$WORK/raw/$id.%(ext)s" -- "$vid" </dev/null || true
    raw=$(ls $WORK/raw/$id.* | head -1)
  fi
  graph="[0:a]atrim=${X}:${L},asetpts=PTS-STARTPTS[b];[0:a]atrim=${L}:$((L+X)),asetpts=PTS-STARTPTS,afade=t=out:d=${X}:curve=qsin[t];[0:a]atrim=0:${X},asetpts=PTS-STARTPTS,afade=t=in:d=${X}:curve=qsin[h];[t][h]amix=inputs=2:normalize=0[m];[b][m]concat=n=2:v=0:a=1"
  [[ -f $WORK/$id.loop.wav ]] || ffmpeg -nostdin -v error -y -ss $off -t $((L+X)) -i $raw -filter_complex "$graph" -ar 48000 -ac 2 $WORK/$id.loop.wav
  m=$(ffmpeg -nostdin -hide_banner -nostats -i $WORK/$id.loop.wav -af loudnorm=I=-20:TP=-2:LRA=14:print_format=json -f null - 2>&1 | sed -n '/^{/,/^}/p')
  j() { print -r -- $m | python3 -c "import json,sys;print(json.load(sys.stdin)['$1'])"; }
  # Polster: vorn das Schleifenende, hinten der Schleifenanfang (je $P s). Die App spielt nur
  # [P, P+L] in Schleife (Media3-Clipping) — die Codec-Ränder der Datei (Opus-Vorlauf, Auffüllung am
  # Ende) liegen so außerhalb, die Naht ist nahtlos (gemessen: ohne Polster sprang das Signal).
  ffmpeg -nostdin -v error -y -i $WORK/$id.loop.wav -filter_complex "[0:a]asplit=3[x][y][z];[x]atrim=start=$((L-P)),asetpts=PTS-STARTPTS[a];[z]atrim=end=${P},asetpts=PTS-STARTPTS[c];[a][y][c]concat=n=3:v=0:a=1,loudnorm=I=-20:TP=-2:LRA=14:measured_I=$(j input_i):measured_TP=$(j input_tp):measured_LRA=$(j input_lra):measured_thresh=$(j input_thresh):offset=$(j target_offset):linear=true" \
    -ar 48000 -c:a libopus -b:a 72k -vbr on $OUT/$id.ogg
  echo "$id → $(du -h $OUT/$id.ogg | cut -f1)"
done <<'LIST'
tibet    -4rtl36Cz48  00:20:00  30
kristall gq8snFSEwlU  00:30:00  300
hz432    C9hMaqQFKXk  00:30:00  30
hz528    0MM5NwGbg7c  00:30:00  30
schlaf   txQ6t4yPIM0  00:40:00  30
regen    M0qWBKQ7ldY  00:30:00  30
wald     29XymHesxa0  00:30:00  30
handpan  uwEaQk5VeS4  00:02:00  30
fokus    5LXhPbmoHmU  00:30:00  30
LIST
