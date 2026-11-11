# Hungry Hole in 3D

Quelltext des Spiels »Hungry Hole«. Gebaut wird nach `android/app/src/main/assets/hole3d/`;
die App zeigt es in einem WebView (`ui/games/HungryHoleGame.kt`) und liefert die Dateien selbst
aus — das Spiel braucht kein Netz.

## Aufbau

| Datei | Aufgabe |
|---|---|
| `src/sim.js` | Spiellogik ohne Darstellung: Loch, Verschlucken, Wachsen, einstürzende Stapel |
| `src/levels.js` | Bausteine (Reihe, Ring, Spirale, Turm, Pyramide), 15 feste Level, danach erzeugte Level ohne Ende (`buildEndless`), die Zeitjagd |
| `src/stage.js` | Darstellung mit three.js: Tischdecke mit Loch, Schacht, Instanzen, Kamera |
| `src/main.js` | Ablauf, Anzeige, Ton, Steuerung, Verbindung zur App (`window.HoleNative`) |
| `src/index.html` | Seite, Menüs und Stil |
| `src/foods.json` | Höhen der Modelle (entsteht beim Packen) |

## Bauen und testen

```sh
npm install
npm test          # Logik und Level (jedes Level muss lösbar sein, nichts darf überlappen)
npm run build     # schreibt game.js und index.html in die App-Dateien
```

Im Browser ansehen: im Zielordner `python3 -m http.server 8790` starten und
`http://127.0.0.1:8790/` öffnen. Ohne App speichert das Spiel in `localStorage`.
Über `window.__hole` lässt sich der Stand lesen und ein Level starten.

## 3D-Modelle

Die Modelle stammen aus dem **Kenney Food Kit 2.0** (Creative Commons Zero, CC0),
<https://kenney.nl/assets/food-kit>. Das Kit selbst liegt nicht im Repository.

Neu packen (nur nötig, wenn sich `models.json` ändert): Kit herunterladen, entpacken, dann

```sh
/Applications/Blender.app/Contents/MacOS/Blender -b -P pack_assets.py -- \
  "<Kit>/Models/GLB format" \
  ../../android/app/src/main/assets/hole3d/foods.glb \
  src/foods.json
```

Blender verbindet je Modell alle Teile zu einem Netz, legt den Ursprung unten in die Mitte
der Standfläche, skaliert die Standfläche auf 1 × 1 und schreibt alles mit einem gemeinsamen
Material in eine Datei. Ein neues Modell braucht außerdem seine Größenklasse in `TYPES`
(`src/levels.js`).

Die **Bombe** gibt es im Kit nicht. `pack_assets.py` baut sie aus Grundkörpern (Kugel, Kappe,
Zündschnur, Funke) und färbt sie über die Eckpunkte ein — sie braucht kein Bild und bekommt im
Spiel ein eigenes Material.

## Level

Die ersten 15 Level stehen von Hand gebaut in `LEVELS`. Danach erzeugt `buildEndless(nummer)`
jedes weitere: Die Tafel ist ein Raster aus Feldern (`PATCHES`: leicht, mittel, schwer, Falle),
gewichtet nach der Levelnummer. Dieselbe Nummer ergibt immer dasselbe Level. Mit der Nummer
wachsen Tafel und Brocken, es kommen mehr Bomben auf die Kreuzungen und die Zeit je Ding sinkt.
Unlösbares (zu wenig Kleines für die dicken Brocken) wird neu gewürfelt.

**Bomben** (`Builder.bomb`) zählen nicht zum Aufessen. Fährt das Loch über eine, ist das Level
verloren. Sie rutschen nicht von selbst hinein, tragen einen roten Ring und pochen, wenn der
Lochrand näher kommt.

Ein neues Feld für erzeugte Level: in `PATCHES` eintragen; es muss innerhalb von 3,5 um seine
Mitte bleiben. Die Tests bauen über hundert Level und prüfen jedes: genug Kleines zum Wachsen,
nichts überlappt oder ragt über den Rand, der Startplatz des Lochs ist frei.

## Steuerung

Der Finger verschiebt einen Zielpunkt, das Loch gleitet ihm nach (`PACES` in `src/main.js`:
Übersetzung und Folgetempo für Ruhig, Mittel, Flink).
