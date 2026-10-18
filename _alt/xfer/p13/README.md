# Glacier – Weißer Donner

Ein Gletscher, der ins Wasser kalbt: der Perito Moreno am Lago Argentino als 3D-Echtzeitszene. Die Front steht 74 m hoch über dem See, alle paar Minuten knackt es, ein paarmal am Tag bricht ein Turm aus der Wand, stürzt ins Wasser und schickt eine Welle über den Kanal, die an den Stegen aufläuft.

Glacier ist die Fortsetzung von Lighthouse und Haithabu mit denselben Packages für Himmel, Wetter, Wasser, Gischt, Wald und Klang. Neu sind drei Packages, die jede Szene brauchen kann: `ice` (Eis als Material und Körper), `fracture` (Bruch und Starrkörper) und `wave` (eine Welle, die läuft). Jedes Package folgt demselben Vertrag: Der Kern braucht nur Java 21, `gl/` dazu JOGL, `demo/` dazu FStyle. `check-packages` übersetzt jedes Package einzeln und läuft nach jedem Übersetzen.

**Stand: Phase 13 von 16 – bis zur Ruptura:** Gelände, Himmel und Wetter, Eis und Gletscher, Bruch, Kalbung, Welle, Eisberge, Leben, das Jahr, Klang und der Eisdamm, der bricht. Plan und Fortschritt stehen im Artefakt „Glacier Werkbuch“, die Statusbilder in `docs/status/`.

## Die Packages

| Package | Inhalt | Stand |
|---|---|---|
| `com.dan.ice` | Eis als Material (Beer-Lambert, Blasen, Risse), als Gletscher (Spalten, Türme, Front, Eisberge) und als Eisdamm | neu, Phase 4–5, 13 |
| `com.dan.fracture` | Zellen, Spannung, Risse, Starrkörper mit Stößen; kennt kein Eis ([README](src/com/dan/fracture/README.md)) | neu, Engine (Phase 6) |
| `com.dan.wave` | Flachwasser auf zwei Gittern: Einschlag, Lauf, Auflaufen, Reflexion | neu, Löser (Phase 8) |
| `com.dan.sky`, `cloud`, `weather`, `rain`, `fog`, `lightning` | Himmel und Wetter | aus Haithabu |
| `com.dan.ocean`, `spray`, `buoyancy`, `weathering` | See, Gischt, Schwimmen, Nässe | aus Haithabu |
| `com.dan.flock`, `forest`, `ground`, `reed`, `river`, `season` | Leben und Jahr | aus Haithabu |
| `com.dan.soundscape`, `store` | Klang, Datenseite | aus Haithabu |
| `com.dan.glacier` | Die App | [README](src/com/dan/glacier/README.md) |

Abgestellt in `_alt/packages/` (F6 A): `timber`, `smoke`, `hearth`, `herd`, `longship`, `road`, `muster`.

## Bauen und starten

- NetBeans-Projekt mit Ant, JDK 21. In `lib/`: JOGL mit Windows-Natives, `FStyle.jar`, `ojdbc11.jar`, `sdo*.jar`.
- **Clean and Build** erzeugt `dist/Glacier.jar` mit `dist/lib`. Danach startet [Glacier.bat](Glacier.bat) die App ohne NetBeans, [Glacier_Konsole.bat](Glacier_Konsole.bat) mit Konsole.
- Voraussetzung: OpenGL 4.3 mit Compute-Shadern. Ziel: AMD Radeon 680M (2 GB), „Mittel“ hält 60 Bilder/s in Full HD.
- Datenbank (ab Phase 15): Schema GLACIER in PDBORCL, Präfix `GLC_`; `db/USER.sql` legt das Schema an.
