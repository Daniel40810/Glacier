# com.dan.glacier.wiring – die Verdrahtung

Hier reden die Packages miteinander, und nur hier. Kein Element-Package (die achtzehn aus Haithabu und die drei neuen `ice`, `fracture`, `wave`) kennt ein anderes oder die App. Jedes bietet Eingänge an und meldet Ereignisse; die App verbindet sie in `Wiring`.

Stand Phase 5: alle Schnittstellen stehen, 26 Verbindungen. Verbunden sind die Uhr (Kamera, Himmel, HUD), das Gelände (Kamera, Ansichten, Grund des Sees, Dichten für forest, reed und ground, Einschläge der Blitze), die Wetterregie mit dem Klima Patagoniens (Wind und Böen an ocean, forest, reed, ground, cloud, rain, fog; Bedeckung, Wolkenart, Lentikularis, Regen, Sicht, Nebel, Gewitter an cloud, rain, fog, sky, lightning), der Himmel als Licht für alle (Lighting), die Wolkenschatten und das Blitzlicht; forest, ground und reed bekommen den Tag im Jahr um 182 Tage versetzt (`Wiring.northernDay`). Seit Phase 4 bekommt `ice` das Licht des Himmels. Seit Phase 5 bekommt es den Seespiegel (`WaterLevel`), das Gelände als Raster (`Terrain`) und den Vorstoß nach der Jahreszeit (`Wiring.glacierAdvance`: +40 m im Dezember, −40 m im Juni [G]). Der Gletscher steht als `Collider` in `Wiring.colliders`, seine Rinnen und sein Gefälle gehen an `river` (Schmelzbäche). `WaterLevel` und `Terrain` für wave folgen in Phase 7 und 8. `Wiring.planned()` nennt die Verbindungen aus dem Phasenplan.

Seit Phase 12 (72 Verbindungen): `soundscape` bekommt Wind und Wetter, das Ohr, die Risse (Knacken und Donner aus dem Weg des Risses), die Aufschläge, das Auflaufen, die Rufe, kenternde Berge, den Diesel, den Donner der Blitze und die Geländer und Pfähle der Stege; der Katamaran zieht Kielwasser in `ocean`. Geplant bleibt: Tropfen auf Deck und Rost (Phase 16).

Seit Phase 11 (62 Verbindungen): `season` (Klima vor der Front, Südhalbkugel) gibt der Regie Monatsmittel und Abweichung, bekommt das Wetter des Augenblicks und gibt `Season` an das Gelände (Schneegrenze), `ice` (Schnee auf der Zunge), `forest` und `ground` (Laub, Lenga-Rot, Schnee), `ocean` (Eis im Brazo Rico), die Kalbung (Schmelzkerbe), `rain` (Schnee statt Regen) und den Stau an `WaterLevel.brazoRico` (`wiring/Jahr`).

Seit Phase 10 (53 Verbindungen): der Wind an `flock` (Möwen im Aufwind, Kondore im Hangwind), die Eisberge als Sitzplätze und der Katamaran als Störer an `flock`; Seespiegel, Regen und Sonne an `weathering` (die Stege), `Runup` vom Anleger und `SurfaceHit` (Gischt an den Pfählen) an `weathering`; die Pfähle als `Collider` an `spray`; `Sea` (Spiegel + lange Welle + Windsee, Strömung der Welle) an den Katamaran, der Katamaran als Mitfahrt an die Kamera. Geplant: Kielwasser und Tropfen auf Deck (Phase 12).

Seit Phase 9 (44 Verbindungen): `buoyancy` bekommt `Sea` (Spiegel + Welle, Grund, Front als Wand), den Wind und die Uhr (Schmelze); `fracture` übergibt Stücke, die auf dem Wasser zur Ruhe kommen, als Eisberge (`onSettle`), `onImpact` füllt das Treibeis; `buoyancy` gibt `ocean` die Umrisse der Eisberge und das Treibeis als Textur.

Seit Phase 8 (38 Verbindungen): der Seespiegel und das Gelände an `wave` (Ruhetiefe, die Front als Wand), `Impact` an `wave` (verdrängtes Volumen als Quelle), die Höhentextur der Welle an `ocean`, `Sea` (Seespiegel + Welle) an `fracture`, `Runup` an das Gelände (nasser Saum). Geplant: `Runup` an `weathering` (nasse Stege, Phase 10) und an `soundscape` (Phase 12).

Seit Phase 7 (32 Verbindungen): `ice` gibt einen Abschnitt der Front als `Cells` an `fracture` (`Wiring.cells(FrontBlock)`), der `Crack` geht an `ice` (dunkle Rissspur auf den Zellen), der `Impact` an ocean (Ringwelle als Ersatz bis Phase 8) und spray (Aufschlag), der Wind an spray, die Uhr an die Kalbung (Zeitraffer, Tag im Jahr für die Rate).

## Die Schnittstellen

| Schnittstelle | Inhalt | Gibt | Nimmt | ab Phase |
|---|---|---|---|---|
| `Clock` | Laufzeit, Schritt, Uhrzeit, Ort | App (`SceneClock`) | alle | 1 |
| `Wind` | Geschwindigkeit, Richtung, Böigkeit, Bft | weather (bis dahin von Hand) | ocean, cloud, rain, fog, spray, forest, reed, flock, soundscape, Eisberge | 1 |
| `WaterLevel` | Spiegel des Hauptsees, Stau des Brazo Rico | App (nach Jahr und Regie) | ocean, wave, ice, buoyancy, weathering, Kamera | 1 |
| `Terrain` | Höhe, Wassertiefe, Normale am Ort | Gelände aus GLO-30, Grund modelliert | ice, wave, forest, ground, river, reed, Kamera | 2 |
| `Lighting`, `Flash` | Sonne, Mond, Himmel, Dunst; Blitz | sky, lightning | ocean, cloud, ice, Szene | 3 |
| `Collider`, `SurfaceHit` | fester Körper als Abstandsfeld; Einschlag auf einer Fläche | ice, App (Stege, Katamaran) | ocean, rain, spray, weathering | 5 |
| `Cells` | Hülle, Ebenen, Front als Ebene, Inneres, Dichte, Festigkeit je Ort | ice (`FrontBlock`) | fracture | 7 |
| `Crack` **neu** | Weg des Risses, Energie, frei oder nicht | fracture (`onCrack`) | soundscape, ice | 7 |
| `Sea` **neu** | Höhe als Summe: `ocean.heightAt + wave.heightAt` | App | buoyancy, spray, ice, fracture | 7–8 |
| `Impact` | Ort, Volumen, Tempo eines Einschlags | fracture (`onImpact`) | wave, spray, soundscape | 7–8 |
| `Runup` | Auflaufhöhe am Ufer | wave (`onRunup`) | Gelände (Saum), weathering, soundscape | 8 |
| `Season` | Laub, Färbung, Schnee, Eisrand, Schmelze, Kalbungsrate | season | ice, forest, reed, ocean, weather | 11 |
| `SoundEvent` | Klangereignis | fracture, wave, flock, buoyancy, lightning | soundscape | 12 |

`Beaufort` gibt die gemeinsame Sprache für alle Presets: Grenzen nach WMO und die deutschen Bezeichnungen.

## Regeln

- Jede Schnittstelle besteht nur aus Zahlen, Aufzählungen und Funktionen. Keine Klassen aus anderen Packages.
- Braucht ein Package dieselbe Form, bringt es eine eigene Kopie mit gleichem Namen mit. `Wiring` übersetzt mit einer Zeile.
- Jede neue Verbindung wird mit `link(von, an, über, Phase)` eingetragen. Das Bedienfeld zeigt die Liste.
