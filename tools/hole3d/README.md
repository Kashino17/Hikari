# Hungry Hole in 3D

Quelltext des Spiels »Hungry Hole«. Gebaut wird nach `android/app/src/main/assets/hole3d/`;
die App zeigt es in einem WebView (`ui/games/HungryHoleGame.kt`) und liefert die Dateien selbst
aus — das Spiel braucht kein Netz.

## Aufbau

| Datei | Aufgabe |
|---|---|
| `src/sim.js` | Spiellogik ohne Darstellung: Loch, Verschlucken, Wachsen, einstürzende Stapel |
| `src/levels.js` | Bausteine (Reihe, Ring, Spirale, Turm, Pyramide), die 15 Level, die Zeitjagd |
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

## Ein Level hinzufügen

In `LEVELS` (`src/levels.js`) eintragen und `HOLE_LEVEL_COUNT` in
`ui/games/GameCatalog.kt` anpassen. Der Test prüft, dass genug Kleines zum Wachsen da ist,
nichts über den Rand ragt und der Startplatz des Lochs frei bleibt.
