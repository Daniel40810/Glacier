# com.dan.flock – Möwen und andere Vögel

Silbermöwen um einen Turm auf See: Sie fliegen im Schwarm, stehen im Aufwind an der Luvseite, landen gegen den Wind auf Geländern und Tonnen, schlafen nachts am Turm und suchen im Sturm das Lee. Das Package braucht nur Java 21, der Ordner `gl/` zusätzlich JOGL, der Ordner `demo/` zusätzlich `FStyle.jar`. Zum Herauskopieren reicht `src/com/dan/flock/`.

## Was es kann

- **Flug:** 8 bis 17 m/s gegen die Luft, im Gleitflug 0,6 m/s Sinken, mit Flügelschlag (3,1 Hz) bis 2 m/s Steigen. Gesteuert nach Zielen und nach drei Schwarmregeln (Abstand 4 m, Richtung und Zusammenhalt in 25 m), Hindernissen ausweichen, über den Wellen bleiben. Der Wind folgt dem logarithmischen Profil über See.
- **Aufwind am Turm:** `updraft(x, y, z)`: vor der Luvseite eines Zylinders am stärksten (2,5 m vor der Wand bei Bft 5 gut 5 m/s), nach außen und über der Oberkante abklingend, im Lee nichts. Ab 5,5 m/s Wind stellen sich bis zu fünf Möwen dort hinein und stehen fast still in der Luft.
- **Windschatten:** `windAt(x, y, z)` schwächt den Wind hinter einem Hindernis bis auf ein Fünftel. Dort halten sie im Sturm aus.
- **Landen und Sitzen:** Sitzplätze (`Perch`, auch bewegte wie Tonnen oder zeitweise fehlende wie eine Platte bei Flut) werden über einen Punkt im Lee gegen den Wind angeflogen; die letzten Meter flattert die Möwe gerade auf den Platz. Sie sitzt mit dem Kopf im Wind, angelegten Flügeln und Beinen, ruft beim Landen und Abfliegen.
- **Tag, Nacht, Sturm:** Abends suchen sie einen Platz am Turm, wer keinen findet, fliegt zur Küste; nachts fliegt keine und keine ruft. Ab Bft 8 landen sie nur noch im Lee oder fliegen weg, ab Bft 10 sind alle weg.
- **Rufe:** zufällig mit Abstand, als Ereignis `onCall` (für einen Klang in einem anderen Package).
- **Netz und Flügel:** `GullMesh` baut die Möwe (Rumpf, Kopf, Schnabel, Mantel, Innen- und Außenflügel mit schwarzen Spitzen, Beine); der Vertex-Shader biegt die Flügel um Schulter und Handgelenk: schlagen, gleiten, im Aufwind geknickt, beim Sitzen angelegt.
- **Presets:** `calm()`, `breeze()`, `gale()`, `storm()`, `beaufort(n)`. **Reproduzierbar:** gleicher `seed`, gleicher Flug. **Stufen:** `Quality.LOW` bis `ULTRA` (6 bis 28 Möwen).

## Weitere Arten (seit Haithabu Phase 13)

`Species` nennt sechs Arten mit Maßen, Flug (Geschwindigkeiten, Sinken, Steigen, Schlagfrequenz, Anteil im Gleitflug), Fähigkeiten und Fluchtdistanz [G]:

| Art | Länge / Spannweite | Kann | Flucht |
|---|---|---|---|
| Silbermöwe | 0,60 / 1,40 m | fliegt, sitzt, schwimmt | 6 m |
| Nebelkrähe | 0,47 / 0,98 m | fliegt, sitzt auf Firsten, geht am Boden und pickt | 18 m |
| Rauchschwalbe | 0,19 / 0,33 m | fliegt tief und wendig, nur Mitte April bis Mitte September | 2 m |
| Stockente | 0,58 / 0,90 m | schwimmt, gründelt, fliegt steil auf (Erpel und Weibchen) | 25 m |
| Höckerschwan | 1,50 / 2,30 m | schwimmt, gründelt, schwimmt weg statt aufzufliegen | 12 m |
| Kormoran | 0,85 / 1,40 m | schwimmt tief, taucht, trocknet auf Pfählen die Flügel | 30 m |

- **Zahl:** `mix` gibt die Zahl je Art. Die Möwen kommen weiter aus `quality`. Alle Werte 0: nur Möwen wie bisher.
- **Wasser:** `water` gibt die Höhe der Oberfläche oder NaN, wo kein offenes Wasser ist (Land, Eis, zu flach). `waters` sind die Wasserflächen der Schwimmer, `feeding` die Futterplätze der Krähen.
- **Störer:** `disturbers` sind Auge, Hund, Boote. Wer näher kommt als die Fluchtdistanz, scheucht die Vögel auf. Enten fliegen auf und landen woanders wieder, Schwäne schwimmen weg, Kormorane tauchen, Krähen und Möwen fliegen ab.
- **Jahr:** `dayOfYear` steuert die Schwalben. Liegt Eis auf dem ganzen Wasser, sind die Schwimmer fort.
- **Nacht:** Krähen und Schwalben schlafen außerhalb der Szene, Enten und Schwäne auf dem Wasser.
- **Neue Zustände:** `SWIM`, `DIVE`, `WALK`.
- **Sitzplätze:** Eine Maske gibt an, welche Arten einen Platz nehmen (`Perch.only`). Seit Haithabu Phase 14 hat jeder Platz eine Beliebtheit (`Perch.appeal`, 1 üblich): Ein Vogel wählt unter fünf freien Plätzen den mit dem kleinsten (Abstand + 20 m) / Beliebtheit.
- **Netze** (`BirdMesh`): wie die Möwe gebaut, nach den Maßen der Art gestreckt.
  - Der Hals von Schwan und Kormoran ist eine glatte Röhre: sitzend als S, im Flug gestreckt (zweite Spielart).
  - Die Schwalbe hat einen Gabelschwanz, die Ente einen blauen Spiegel.
  - `FlockGl` zeichnet ein Netz je Art und Spielart mit einem Aufruf.
  - Gründeln neigt den Körper Kopf voran unter Wasser, der Kormoran sitzt aufrecht.

## Patagonien (seit Glacier Phase 10)

- **Andenkondor** (`Species.KONDOR`, 1,20 m lang, 3,00 m Spannweite): gleitet mit 16 m/s von Thermik zu Thermik und sinkt dabei, kreist darin mit 40 bis 70 m Halbmesser (die Mitte treibt mit dem Wind) und steigt mit `condorLift()` (Sonne aus `ambientLux`, dazu Hangwind) bis an die Decke. Er schlägt nur, wenn er unter die Hälfte von `condorFloor` sinkt. Thermiken: `thermals` (Kreise über sonnigen Hängen), sonst Kreise im Revier um `condorX`, `condorZ` mit `condorRadius`. Nachts sitzt er außerhalb der Szene an seinem Felsen. Netz mit gefingerten Handschwingen, weißem Feld auf den Armschwingen und grauem Kopf.
- **Dominikanermöwe** (`kelpGulls = true`, Larus dominicanus): verhält sich wie die Silbermöwe, hat aber einen schwarzen Mantel (eigenes Netz, Spielart 1 der Möwe).
- **Sitzplätze, die treiben:** ein `Perch` mit eigenem `Spot` (etwa die höchste Ecke eines Eisbergs) bewegt die sitzende Möwe mit; ist der Platz nicht mehr `usable()`, fliegt sie ab.
- **Prüfung** (`FlockCheck`, Abschnitt Glacier): Kondore bleiben zwischen 110 und 700 m über Grund, sind die Hälfte der Zeit im Kreis und steigen dort mit 1,7 m/s, schlagen fast nie; eine Möwe sitzt 9 min auf einem treibenden Eisberg und treibt mit; nachts sind die Kondore fort.

## Benutzung

```java
Flock f = Flock.beaufort(5);
f.windFromDeg = 250;
f.obstacles.add(new Flock.Obstacle(0, 0, 3.5, -2, 27.6));                 // der Turm
f.perches.add(new Flock.Perch("Geländer", 4.3, 24.1, 0));
f.perches.add(new Flock.Perch("Tonne", () -> buoy.lightPosition(), () -> true));
f.ground = (x, z) -> ocean.heightAt(x, z);
f.onCall = (x, y, z, s, g) -> sound.gull(x, y, z, s);
f.ambientLux = lux;
f.step(dt);                                                             // je Bild

flockGl.draw(gl, f, view, light);
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Flock` | Kern: Möwen, Zustände, Flug, Schwarm, Aufwind, Windschatten, Landen, Tag und Nacht, Rufe, Presets |
| `GullMesh` | Netz der Möwe und Flügelstellung je Zustand |
| `gl/FlockGl` | eine Instanz je Möwe, Flügel im Vertex-Shader (`gull.vert`), Licht in `gull.frag`, Selbsttest |
| `demo/FlockDemo` | Fenster mit Reglern und Möwenflug; `--bild datei.png [breite]` schreibt vier Lagen, den Aufwind im Schnitt und die Bahnen von oben |
| `demo/FlockScene` | eigene Szene ohne Dreiecke: See, Turm mit Galerie |
| `demo/FlockCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Demo und Selbsttest

- `com.dan.flock.demo.FlockDemo` zeigt Möwen um einen Turm; „Möwenflug“ reitet auf einer.
- `com.dan.flock.demo.FlockCheck` prüft das Aufwindfeld, das Stehen im Aufwind, die Geschwindigkeiten, nie im Turm und nie im Wasser, die Schlagfrequenz, Landen und Sitzen, Nacht, Lee bei Bft 9, Wegfliegen bei Bft 11, seed und die Flügelstellung.
- `FlockGl.selfCheck` rechnet die Flügelspitzen auf der Grafikkarte und vergleicht mit dem Prozessor.

## Grenzen

- Aufwind und Windschatten sind Näherungen nach der Form eines Zylinders, kein Strömungsfeld.
- Fischen und Fressen sind nur Haltungen: gründeln, tauchen, picken. Es gibt keine Beute.
- Kein Gefieder im Wind, keine einzelnen Federn; die Flügel haben zwei Glieder.
- Rufe sind Ereignisse ohne Klang; den macht ein anderes Package.
