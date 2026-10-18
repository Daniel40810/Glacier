# com.dan.glacier – die App

Glacier – Weißer Donner: der Perito-Moreno-Gletscher am Lago Argentino als 3D-Echtzeitszene. Die App verdrahtet die Element-Packages und zeichnet; sie selbst enthält keine Physik der Elemente.

**Stand: Phase 16, Schliff: Kinomodus, Dauerlauf, Qualitätsstufen durchgängig, Verknüpfung, Quellenliste. Davor Phase 15: Oberfläche und Datenbank.**

| Paket | Inhalt |
|---|---|
| `app` | `GlacierApp`: Startbild, FFrame maximiert, Szene, Bedienfeld, Statuszeile, Fehlerdatei `daten/fehler.txt`, Vollbild im Kino; `Dauerlauf` (Phase 16) |
| `gl` | Engine aus Haithabu, entschlackt: `Renderer` (Himmel aus com.dan.sky, Wolken, Rundumbild, Augenanpassung, Gelände, Wald mit Sonnenschatten, Bodendecke, See, Halme, Regen, Seenebel, Blitz, Nachbearbeitung, HUD, Zeitmessung je Durchgang, Auflösungsregelung, Foto F12, Shader neu F5), `SceneView`, `Shader`, `Gpu`, `Mat4`, `HudOverlay`, `ComputeProbe`, `Quality`, `TerrainLod` (CDLOD aus Haithabu), `SunShadow` |
| `gl/shader` | `common.glsl` (Platzhalter-Himmel), `sky.frag`, `skypano.frag`, `terrain.vert`, `terrain_height.glsl`, `terrain.frag`, `post.frag`, `overlay.frag`, `probe.comp` |
| `camera` | `Camera` (Kreisen, Frei, Fahrt, Mitfahrt), `Fahrt`, `Ansichten` 1–7 |
| `scene` | `Site` (Ort und Maße, Abschnitt 2), `Geo` (transversale Mercator, UTM 18 Süd), `Sun`, `SceneClock` (ART, UTC−3), `Terrain` (Gelände 12,8 km zu 6,25 m), `Vegetation` (Karten zu 25 m), `Bodendecke`, `Buehne` (Ersatz ohne Höhendaten), `Physik` (eigener Faden, fester Schritt 1/120 s) |
| `wiring` | 15 Schnittstellen und `Wiring` – siehe [wiring/README.md](wiring/README.md) |
| `ui` | `ControlPanel` (ZEIT, WETTER, SEE, REGIEBÜCHER, EIS, KALBUNG, RUPTURA, WELLE, EISBERGE, LEBEN, KLANG, KAMERA, BILD, CHRONIK, DATEN, TECHNIK), `Startbild` (mit Datenbank und „Im Kino“), `Weiter` („Weiter wie zuletzt“, Regiebuch im Kino), `Karte` (Säulen- und Eisbergkarte), `AppIcon`, `Theme` |
| `data` | Datenseite (Phase 15): `Daten`, `Speicher`, `DbSpeicher` (Schema GLACIER), `DateiSpeicher` (`daten/`), `Verbindung` (Einrichter), `Startdaten` und `StartdatenSql` (→ `db/sql/04_startdaten.sql`), `Zustand`, `Chronik`, `Tageskarte`, `Uebernahme`; Textdateien `quellen.txt`, `befunde.txt`, `ruptura.txt` |
| `tools` | `Tempo`: Tempo-Probe und Standbild ohne Fenster (seit Phase 16 mit `KINO=1`); `GelaendeImport` und `GeoTiff`: Höhendaten einlesen; `StartdatenSql`: schreibt `db/sql/04_startdaten.sql`; `Quellenliste`: schreibt `docs/QUELLEN.md` |

## Gelände (Phase 2)

`tools.GelaendeImport` liest das Höhenmodell Copernicus GLO-30 (GeoTIFF, auch LZW) und schreibt `daten/gelaende`:

```
java -cp build/classes com.dan.glacier.tools.GelaendeImport templates/rasters_COP30/output_hh.tif daten/gelaende docs/status/Gelaende_Import.png
```

- Raster 2048² zu 6,25 m (12,8 km), Höhen über dem See in 16 bit; Ursprung die Spitze der Front.
- Doppelte Spalten fallen weg (GLO-30 hat hier 1,5″ in der Länge). Seespiegel = häufigster Wert (178,5 m ü. M.).
- Eis = die größte glatte Fläche am Wasser, Masken bilinear (keine 30-m-Treppen); der Fuß der Front wird See.
- Seegrund und Grund unter dem Eis sind modelliert [G] (Tiefe 160 m · (1 − e^(−s/350 m)), Eis 170 → 380 m).
- Die Stege: auf der Halbinsel 25 bis 60 m über dem See, gut 300 m vor der Spitze.
- Fehlt `daten/gelaende`, zeigt die App die Bühne von Phase 1 (`Terrain.flat()`).

Wo was wächst, steht in `scene.Vegetation` [G]: Lenga bis 700 m über dem See, Guindo unten am feuchten Südhang und
nahe am Eis, Ñire an den trockenen Rändern nach Osten, Steppe auf offenen Hängen nach Osten hin, Ufergras, Moräne
neben dem Eis. Die Packages bekommen den Tag im Jahr um ein halbes Jahr versetzt (Südhalbkugel).

## Himmel und Wetter (Phase 3)

- Die Wetterregie (`com.dan.weather`) läuft frei mit dem Klima Patagoniens (`Wiring.PATAGONIA`): klar, aufziehend,
  Front, Sturm, selten Gewitter, abklingend, aufklarend, Seenebel am Morgen und der Föhnsturm im Lee der Anden [G].
  Im Bedienfeld (WETTER) frei ab einer Lage oder von Hand; Handregler halten eine Größe, bis „Regie weiter“.
- Lentikularis: im Föhnsturm fast immer, vor einer Front oft, sonst nach Stärke des Westwinds; von Hand mit dem Regler.
- Der Himmel (`com.dan.sky`) kennt den Südhimmel: Kreuz des Südens, Zentaur, Kiel des Schiffs, Südliches Dreieck
  (ZEIT → „Sternbilder einzeichnen“) und die Magellanschen Wolken.
- Die Augenanpassung folgt dem Himmel im Blickfeld (wie Haithabu); nachts sieht das Auge farblos und bläulich.
- `Tempo` kennt `WETTER=FOEHN`, `LENTIKULARIS=0..1`, `BEDECKUNG=Achtel`, `FIGUREN`.

## Eis (Phase 4)

- Zwei Probe-Eisberge aus `com.dan.ice` schwimmen in der Blickschneise vor dem Steg: 7 m Halbmesser rund 120 m entfernt, 12 m Halbmesser rund 260 m entfernt [G]. Sie rücken hinaus, bis unter ihnen mindestens 15 m Wasser sind.
- Jeder Eisberg liegt mit 90 % unter Wasser (ρ 900 auf Süßwasser 1000). Die Wasserlinie kommt aus dem Volumen des Netzes, der Spiegel aus `ocean.waterLevel`, dazu 4 cm Wiegen.
- Sonne, Mond, Himmelsrundbild und Dunst kommen wie bei Wald und See. Der Fuß unter Wasser nimmt das Türkis des Lago an (`Ocean.LAGO_SSS`). Regen macht den Schmelzfilm voll.
- Eigener Durchgang „Eis“ zwischen Boden und See. Die Stufe kommt aus `Quality.ice`, auf „Mittel“ sind es 4 Proben durch die Risse. Die Ebene „Eisberge“ lässt sich im Bedienfeld abschalten.
- Der Gletscher selbst bleibt bis Phase 5 die weiße Zunge des Geländes.

## Gletscher (Phase 5)

- `scene/Gletscher` baut den Perito Moreno aus dem Gelände. Das Raster reicht von x −7 000 bis 900 und z −1 900 bis 5 100, 6,25 m je Zelle. Die Front kommt auf 74 m (GLO-30: 44 m), die Geschwindigkeit an der Front ist 2 m/Tag [G]. Der Bau läuft im Hintergrund (rund 1,5 s), danach kommt der Gletscher auf die Grafikkarte.
- Unter dem Eis sinkt das Gelände auf den Grund. Die weiße Zunge aus Phase 2 ist abgelöst, der Gletscher liegt als eigener Körper darüber. An den Rändern tauchen die Kacheln unter das Gelände.
- Kacheln mit Stufen nach dem Abstand: auf „Mittel“ 2 m bis 420 m, 4 m bis 1,1 km, 8 m bis 2,4 km, 16 m bis 5 km, dahinter 32 m. „Niedrig“ nimmt das 0,6-Fache, „Hoch“ das 1,4-Fache, „Ultra“ das Doppelte.
- Vorstoß nach dem Tag im Jahr (`Wiring.glacierAdvance`). Steg und Probe-Eisberge bleiben auch bei vollem Vorstoß frei.
- Schmelzbäche: Quellen auf ruhigen Stellen der Zunge, das Gefälle hinab, bis zu einer Spalte (Gletschermühle). Die Strömung nach Manning kommt aus `com.dan.river`, das Band mit Schlieren und weißen Wellen zeichnet `melt.frag`.
- Kamera und Ansichten stehen auf der Oberfläche mit Spalten (`Renderer.groundAt`, `Ansichten.iceSurface`).
- Fahrt „Über die Zunge“: 7,1 km in 2½ Minuten, 70 m über dem Eis, vom Eisfeld bis über die Front. Knopf im Block KAMERA. Als Bildfolge ohne Fenster: `FAHRT=Zunge BILDER=8 Tempo 1 1920 1080 docs/status/Fahrt.png`.
- Ebenen „Gletscher“ und „Schmelzbäche“ im Bedienfeld. Der Gletscher ist eine eigene HUD-Zeile; die Zeit steckt im Durchgang „Eis“.

## Bruch (Phase 6)

- `com.dan.fracture` ist die Engine für Kalbung, Felssturz und Einsturz; Beschreibung, Prüfung und Grenzen stehen in ihrem [README](../fracture/README.md). Vorführung ohne App: `fracture/demo/FractureDemo.java` → Run File; Prüfung: `FractureCheck.java`.
- In der App: `Renderer.bruch` (Preset `glacier()`) läuft als Aufgabe „fracture“ im Faden der Physik mit 1/120 s. Boden ist `groundAt` (Gletscher mit Spalten oder Gelände), Wasser der Seespiegel. Die Qualitätsstufe der App setzt `Fracture.Quality`. Die HUD-Zeile der Physik nennt Körper, Stücke und die Zeiten für Kräfte, Berührungen und Stöße.
- Befunde aus Status_Phase5 behoben: Die Augenanpassung zählt Eis unter der Kimm fünfmal heller als Wasser (`Renderer.iceInView`); die Kacheln des Gletschers werden von vorn nach hinten gezeichnet; die Spalten haben schwankende Abstände.

## Kalbung (Phase 7)

- `scene/Kalbung` ist die Regie. Ein Abbruch beginnt mit einem Abschnitt der Front (`ice.FrontBlock`, 70–110 m breit, 65–75 m tief), den ein eigener Faden in 450 Zellen zerlegt (Befund Status_Phase7: mit 900 waren die Berührungen großer Stücke zu teuer); dann setzt er ihn in `fracture`. Hinten, seitlich und am Grund hält ihn der Gletscher (Halt und Anker).
- Drei Arten nach der Statistik [G]:
  - **Turm** (50 %): eine Spalte 14–22 m hinter der Front und Flanken schwächen ihn; die Kerbe frisst sich unter ihm hinein, bis er nach vorn kippt.
  - **Scheibe** (35 %): eine dünne Platte 5–8 m hinter der Front sackt ab und kippt ins Wasser.
  - **Fuß** (15 %): der Teil unter Wasser löst sich unten und an den Seiten und schießt auf.
- Die Uhren: die Schmelzkerbe wächst mit 0,6 m je Minute Szenenzeit (beim Auslösen von Hand 0,7 m je Sekunde). Ist das Hauptstück frei, hält die Kerbe an, und der Rest wird fester (frisches Eis ohne Spalten), damit nicht die ganze Front nachstürzt.
- Die Regie würfelt nach Poisson: im Sommer 4 Abbrüche je Stunde, im Winter 0,5 (Tag im Jahr aus der Uhr), höchstens zwei zugleich. Jeder Abbruch kommt als Ereignis ins Protokoll (Zeit, Art, Volumen, Stücke, wie lange der Riss vorher zu sehen war).
- Gezeichnet wird mit dem Eis aus `com.dan.ice` (`gl/KalbungGl`): Wo ein Abschnitt steht, schneiden Kacheln und Wand einen Quader aus, die Zellen füllen ihn. Ein Riss ist zu sehen, bevor er bricht: Ecken nahe gebrochener Flächen werden dunkel und blau. Die Stücke sind frisches, blaues Eis.
- Aufschlag: Stücke, die ins Wasser schlagen, geben Gischt (`spray.impact`) und eine Ringwelle auf dem See (Ersatz bis zur Welle aus Phase 8).
- Bedienfeld, Block KALBUNG: „Regie würfelt Abbrüche“, Knöpfe Turm, Scheibe, Fuß (an der Front nächst dem Blickpunkt der Kamera), Protokoll. HUD-Zeile „Kalbung“.
- Als Bildfolge ohne Fenster: `KALBUNG=Turm BILDER=12 ABSTAND=4 ANSICHT=VomSteg Tempo 1 1920 1080 docs/status/Turm.png` (auch Scheibe, Fuss).
- Befunde aus Status_Phase7 (App hing, lief wie in Zeitlupe):
  - `Glacier.covers` suchte bei Vorstoß ≠ 0 die Front bis 400 m weit ab, mit Tausenden Abfragen je Aufruf; jeder Punkt eines Stücks fragt den Boden je Schritt. Jetzt nur so weit, wie der Vorstoß reicht, und in Ringen nach außen: Berührungen 40 ms → 2 ms.
  - Das Bild wartete auf die Sperre von `fracture` (Lagen, Zähler), während die Spannung rechnete. Jetzt liest es ohne Sperre, und die Spannung läuft in einem eigenen Faden (`asyncSolve`).
  - Die Zellen reichten bis an den Quader 12 m vor der Wand und am Ende einer Frontlinie über die Front hinaus; der Teil unter Wasser leuchtete durch den See. Jetzt enden sie an der Front (`Cells.front`), der Abschnitt rückt längs der Front, unter Wasser wird es dunkel wie an der Wand.
  - Höchstens 150 Stücke zerschellen weiter; Brocken unter 25 m³ gehen im Wasser nach 4 s im Treibeis auf.
  - Steht das Hauptstück nach der vollen Kerbe noch, ermüdet das Eis (alle 2,5 s 15 % schwächer, höchstens zwölfmal). Als Abbruch zählt auch, was binnen 4 s in vielen Stücken fällt.
- Befund aus Status_Phase6a: die Zunge war von oben noch weiß; die Augenanpassung zählt Eis unter der Kimm jetzt zwölfmal heller als Wasser (`Renderer.ICE_GAIN`, Albedo von Schnee zu Wasser).

## Welle (Phase 8)

- `com.dan.wave` ist der Löser (Beschreibung, Prüfung und Grenzen in seinem [README](../wave/README.md)); Vorführung ohne App: `wave/demo/WaveDemo.java`, Prüfung: `WaveCheck.java`.
- In der App: `Renderer.wave` im eigenen Faden „Welle“ (`Physik("Welle")`). `scene/Welle` legt die Gitter: das feine (2 m, 1 km) zwischen den Steg und die nächste Stelle der Front mit mindestens 40 m Wasser davor, das grobe (8 m, 8 km) um dieselbe Mitte über beide Seearme. Grund ist das Gelände unter dem See, die Front eine Wand.
- Quelle: `fracture.onImpact` liefert je Schritt das Volumen, das ein Stück beim Eintauchen verdrängt; es geht als Hügel von 12 m Halbmesser an die Welle. Die Ringwellen von `ocean` sind nur noch Ersatz, solange die Gitter angelegt werden.
- Die Stücke schwimmen auf der Welle (`bruch.water` = Seespiegel + Welle). `ocean` hebt die Fläche um die Welle, nimmt ihre Neigung und malt Schaum an die Stirn. Das Gelände wird, wo sie aufgelaufen ist, dunkel und glänzend und trocknet in einer Viertelstunde.
- Pegel vor dem Steg (die nächste Stelle mit 3 m Wasser); seine Kurve steht im Block WELLE des Bedienfelds. Dort auch Probe-Wellen (5 000, 20 000, 50 000 m³ in 5 s an der Front im Blick), „Welle zeichnen“ und „See beruhigen“. HUD-Zeile „wave“.
- Als Bildfolge ohne Fenster mit Kurve: `KALBUNG=Probe VOLUMEN=25000 BILDER=12 ABSTAND=3 ANSICHT=VomSteg Tempo 1 1920 1080 docs/status/Welle.png` (auch `KALBUNG=Turm`); die Kurve kommt als `Welle_pegel.png`.
- Gemessen am Gelände (Pruefung): ein Bruch von 25 000 m³ in 5 s an der Front 355 m vor dem Steg ist nach 12 s am Pegel, 2,2 m hoch nach 16 s; Rechnung rund 210 ms je Sekunde Welle im Faden „Welle“, nichts, wenn der See ruhig ist.

## Eisberge (Phase 9)

- Kommt ein Stück aus `fracture` auf dem Wasser zur Ruhe (`onSettle`, seit Phase 10 nach 12 s nass), baut ein eigener Faden aus seinem Netz einen `Berg` (`com.dan.buoyancy`); im Faden der Physik übernimmt der Berg die Lage und Bewegung des Stücks, das Stück verlässt `fracture`. Gezeichnet wird mit demselben Netz in `KalbungGl` (Eis wie die Probe-Eisberge, mit der Schrumpfung der Schmelze).
- Wasser der Berge: Seespiegel + Welle; Grund: das Gelände, die Gletscherfront ist eine Wand. Wind aus der Wetterregie, Schmelze in Szenenzeit (Zeitraffer).
- Treibeis: ein Feld von 2 km um die Mitte der Welle (4 m), gefüllt von jedem Aufschlag (8 % des Volumens) und vom „Tag Kalbung“; `ocean` zeichnet es. Um die Eisberge im Umkreis von 1,5 km legt `ocean` Brandungsringe.
- `scene/Eisberge`: konvexe Formen (Quader mit abgeschlagenen Ecken), `fillDay` (ein Tag Kalbung auf einmal: je Abbruch ein großer Berg und Brocken, getrieben und geschmolzen nach ihrem Alter, nur wo es tief genug ist), `toppler` (ein hoher, schmaler Berg, der kentert).
- Bedienfeld, Block EISBERGE: „Ein Tag Kalbung“, „Ein Berg kippt“ (vor der Front im Blick), „Eisberge zeichnen“, „Alle weg“. HUD-Zeile „Eisberge“.
- Ansicht 7 „Unter Wasser“: seit Phase 10 6 m tief, 24 m vor der Front mit tiefem Wasser, Blick zur Front; die Kamera taucht (`Camera.diving`). `underwater.frag` legt das trübe, türkise Wasser über das Bild (Sicht 30 m, Licht von oben nimmt mit der Tiefe ab, Snellsches Fenster).
- Als Bildfolge: `EISBERGE=Tag KALBUNG=Kippen BILDER=8 ABSTAND=4 ANSICHT=UnterWasser Tempo 1 1920 1080 docs/status/Eisberge.png`.
- Befund aus Status_Phase8: der Pegel liegt jetzt im Wasser, das mit dem See verbunden ist (Füllung vom tiefen Wasser vor der Front), und eine Probe-Welle an einer flachen Front schlägt an der Front mit tiefem Wasser ein.

## Leben (Phase 10)

- **Vögel** (`com.dan.flock`): 16 Dominikanermöwen zwischen Front, Steg und Anleger, sie sitzen auf den Geländern der Balkone, auf den Pfählen des Anlegers und auf den zehn größten Eisbergen in der Nähe (die höchste Ecke, sie treibt mit); vier Andenkondore kreisen in der Thermik über den Hängen östlich des Stegs. Wind aus der Wetterregie, Licht aus dem Sonnenstand; das Boot scheucht die Möwen.
- **Stege** (`scene/Stege`): vom Hang oben in Kehren zum großen Balkon am Steg (Ansicht 1 steht jetzt auf dem Deck), weiter zum unteren Balkon und zum Anleger am Ufer (33 m hinaus, Deck 1,6 m über dem See, Pfähle bis auf den Grund). Wege höchstens 27 % steil, Pfosten alle 2 m, Handlauf 1,1 m, Zwischenholm. Nass macht sie `com.dan.weathering`: Regen, Auflaufen der Welle am Anleger (`wave.onRunup`) und Gischt an den Pfählen (`spray.onHit`) setzen die nasse Linie; trocknen mit Wind und Sonne. Das Netz wird neu gebaut, wenn sich die Nässe ändert.
- **Katamaran** (`com.dan.buoyancy.Catamaran`, `scene/Bootsfahrt`): liegt am Anleger; „Bootsfahrt“ legt ab, fährt 300 m vor der Front entlang (3,2 m/s, sonst 6 m/s), draußen zurück, 3 km in 12 min, überall mindestens 4,9 m Wasser. Er schwimmt auf Seespiegel + langer Welle + Windsee und wird von der Strömung der Welle geschoben; gerechnet im Faden „Welle“. Die Kamera fährt mit (Auge auf dem Sonnendeck, Blick nach Backbord zur Front, Ziehen schaut umher, Stampfen und Rollen nimmt das Auge mit).
- **Lenga im Herbst** (vorbereitet für Phase 11): „Lenga im Herbst“ springt auf den 22. April, die Wälder sind rot.
- Bedienfeld, Block LEBEN: „Bootsfahrt“, „Aussteigen“, „Lenga im Herbst“, „Stege nass“, Schalter Vögel, Boot, Stege. HUD-Zeile „Leben“.
- Als Bildfolge: `FAHRT=Boot BILDER=12 ABSTAND=12 Tempo 1 1920 1080 docs/status/Bootsfahrt.png` (vor der Front bricht ein Turm ab; der Hub des Boots als `Bootsfahrt_hub.png`).
- Befunde aus Status_Phase9: die Physik kam nicht nach (Boden des Gletschers je Abfrage teuer: jetzt `GroundCache`; Übergabe an `buoyancy` nach 12 statt 30 s; Stücke, die trocken auf dem Eis liegen, gehen nach 90 s; höchstens 100 Stücke). Die Schmelze der Eisberge rafft höchstens 60-fach (ohne Nachschub der Kalbung war der See nach einer Woche Zeitraffer leer). Die Regie kalbt zu 70 % an der Front im Blick (1,2 km), sonst kam keine Welle am Pegel an. Unter Wasser: 24 m vor der Wand, 6 m tief, Sicht 30 m, das Licht von oben nimmt mit der Tiefe ab, die Oberfläche von unten zeigt das Snellsche Fenster (außerhalb spiegelt sie das Wasser).

## Das Jahr (Phase 11)

- `wiring/Jahr` rechnet mit `com.dan.season` das Jahr vor der Front (Klima `Climate.peritoMoreno()`, Südhalbkugel, Startwert 2026) aus dem Wetter der Regie. Die Regie selbst kennt die Jahreszeit (Monatsmittel, Sommer im Januar).
- **Schneegrenze** auf den Hängen: wo das Monatsmittel mit der Abweichung der letzten zehn Tage bei 0,65 K je 100 m unter +4,5 °C fällt (im Hochsommer um 1 200 bis 1 400 m), liegt Schnee am Ufer, bis an den See. Das Gelände zeichnet sie (`uSnowLine`), die Zunge bekommt Schnee, wenn die Grenze unter 450 m sinkt, die Kronen unter 350 m, die Bodendecke nach dem Schnee am Ufer.
- **Fallender Schnee**: was bei 0,5 bis 2,5 °C am Auge fällt, wird anteilig Schnee (`season.gl.SnowfallGl`), der Rest Regen.
- **Eis** in den flachen Buchten des Brazo Rico (`season.gl.IceGl`, nur südlich der Front), Dicke und Grenze aus dem gewürfelten Jahr.
- **Lenga** rot im April, kahl bis Ende Oktober; **Schmelzkerbe** im Sommer schneller, **Kalbung** im Sommer häufiger.
- **Stau** des Brazo Rico als Jahresgang (Schalter „Jahr mit Eisdamm“): ab 1. Juni 4 bis 12 cm am Tag, bis zum Bruch am 10. März rund 24 m [G]. Bis Phase 13 nur Zahl und HUD; der Arm wird erst mit der Ruptura eigens gezeichnet.
- Bedienfeld, Block ZEIT: Januar, April, Juli, Oktober (die vier Tage der Abnahme), „Jahr mit Eisdamm“. HUD-Zeile „Jahr“.
- Als Bildfolge: `JAHR=1 ANSICHT=VomSteg Tempo 1 1920 1080 docs/status/Jahr.png` (Jahr_jan, _apr, _jul, _okt). Vorschau: `docs/status/Vorschau_Phase11_Jahr.png` (Temperatur, Schneegrenze, Schnee, Eis, Stau und Lenga über 2026).
- Befunde aus Status_Phase10: Eisberge zeigten 0 bei 42 übergebenen und 0 geschmolzenen – „Alle weg“ zählt jetzt eigens („entfernt“), und die Stöße stehen auf 0, wenn weniger als zwei Berge da sind.

## Klang (Phase 12)

- `scene/Klang` verdrahtet `com.dan.soundscape`.
  - **Ereignisse** kommen als `SoundEvent` aus jedem Faden:
    - aus `fracture` das Knacken jedes Risses, der Donner der Kalbung aus dem Weg des letzten Risses (ab 300 m³) und der Aufschlag (der größte je Fünftelsekunde);
    - aus `wave` das Auflaufen am Ufer in 700 m um das Ohr;
    - aus `flock` Möwen und Kondore;
    - aus `buoyancy` ein kenternder Berg;
    - aus `lightning` der Donner.
  - **Von selbst** ächzt die Front alle 30 s (im Winter seltener) und knackt in der Ferne.
  - **Dazu:** Wind (über dem Eis glatt, im Wald gebremst), See (am Hang leiser), Regen, Geländer, die im Wind singen, Pfähle, an denen es gluckst, und der Diesel des Katamarans (Drehzahl nach der Fahrt, an Bord gleichmäßig).
- Der Lautsprecher (`AudioOut`) läuft in einem eigenen Faden. Abschalten mit `-Dglacier.ton=false` oder im Block KLANG (Ton, Lautstärke). HUD-Zeile „Klang“.
- Kielwasser: Die Rümpfe des Katamarans stoßen bei Fahrt Ringwellen in den See.
- **Hörproben ohne App:** `tools/Hoerproben.java` (Run File) schreibt nach `docs/hoerproben`:
  - die vier WAV-Dateien der Abnahme: Steg am Morgen mit einem großen Bruch, auf dem Eis im Wind, Bootsfahrt, die Nacht;
  - dazu `Spektrogramme.png`.
- **Befunde aus Status_Phase11:**
  - Im April stand „Turm nicht ausgelöst“: `FrontBlock.near` fand an einer gewellten Front keinen geraden Abschnitt. Jetzt reicht der beste Versuch, wenn die Enden höchstens 12 % der Breite neben der Front liegen; an 92 von 100 Stellen der Front geht ein Abschnitt.
  - Der Wald backt nur bei Änderungen des Schnees in Zehnteln neu (im Juli 42 Bilder/s).

## Die Ruptura (Phase 13)

- **Der Damm:** `scene/Ruptura` legt an der Spitze der Front einen Eisdamm an (`com.dan.ice.IceDam`). Er füllt den Spülsaum zwischen Gletscher und Halbinsel längs einer Rinne von 530 m vom Brazo Rico zum Canal de los Témpanos.
  - Krone 36 m über dem See, über der Brücke 44 m; zum Ufer hin dünner [G].
  - Er steht in einem Jahr mit Eisdamm (Block ZEIT), solange der Brazo Rico gestaut ist, und im Regiebuch.
  - Wo er liegt, wächst kein Wald.
- **Der Stau:** Der Brazo Rico wird zum zweiten Spiegel. `com.dan.ocean` zeichnet ein zweites Mal, auf dem Spiegel des Staus und nur in der Maske des Arms. Die Maske ist eine Füllung des Geländes unter 30,5 m, die weder über den Gletscher noch über den Damm geht (18 km²).
- **Der Ablauf** in Szenenzeit:
  1. Stau
  2. Sickern: der Tunnel frisst sich an der Sohle in 80 m je Tag durch [G].
  3. Durchbruch
  4. Tunnel: der Durchfluss weitet ihn mit der Wurzel des Durchflusses [G].
  5. Rinne: die Decke fällt.
  6. Brücke: sie steht allein und stürzt zuletzt.
  7. Abfluss: der Brazo Rico läuft leer.
  8. Ruhe.
- **Wie es bricht:** Das Wasser schmilzt Zellen aus dem Körper. Was dann fällt, entscheidet die Spannung in `com.dan.fracture`. Wo nichts fällt, ermüdet das Eis wie bei der Kalbung. Nach 45 s reißt die Brücke an ihren Enden.
- **Das Wasser:**
  - Durchfluss nach Torricelli mit Beiwert 0,5, höchstens 8 000 m³/s (belegt).
  - Der Brazo Rico sinkt mit Durchfluss durch 70 km² [G].
  - In der Rinne ein Spiegel vom Stau bis zur Grenztiefe an der Mündung.
  - Das Band zeichnet `gl/RupturaGl` mit `flut.frag`: trüb, mit Walzen und weißem Wasser.
- **Was die Ruptura weitergibt:**
  - Der Abfluss ist eine Quelle für `wave` 40 m vor der Mündung.
  - Schwälle für `spray`.
  - Das Tosen für `soundscape` (`Torrent`).
  - Die Strömung trägt Stücke (`fracture.current`).
- **Regiebuch „Die Ruptura“** (Block RUPTURA): 4. März 2027, 15 Uhr, die Uhr steht. Der Brazo Rico steigt von 21 auf 25 m, dann der Rest mit eigenem Zeitraffer je Abschnitt, in rund 5 Minuten.
  - Von Hand: „Durchbruch“, „Einsturz“, „Neuer Damm“.
  - Ohne Regiebuch kommt die Ruptura Anfang März von selbst, im Zeitraffer der Uhr.
- **Ansichten:** Taste 8 „Die Brücke“ (vom Balkon des Stegs); Taste 5 „Brazo Rico“ steht seit Phase 13 am Ufer der Halbinsel und blickt auf den Damm.
- **Bildfolge ohne Fenster:** `RUPTURA=1` mit `tools/Tempo` (BILDER, ABSTAND, ANSICHT), dazu die Kurve des Abflusses.

## Regiebücher und Kameras (Phase 14)

- **Regiebücher als Text** (`scene/Regie`, `scene/regie/*.txt`, wie in Haithabu). Je Zeile ein Stichwort, Zeit in echten Minuten:Sekunden, Befehl, Argumente, nach „ | “ der Text fürs HUD.
  - Befehle: `zeit`, `raffer`, `wetter` (Verlauf im Kopf, `com.dan.weather.Scenario`), `wetter_frei`, `ansicht`, `fahrt`, `boot`, `aussteigen`, `kalbung`, `kalbung_regie`, `ruptura`, `damm`, `figuren`, `foto`, `blatt`, `sage`.
  - Am Ende legt `blatt` alle Fotos als ein Blatt nach `docs/status/Regiebuch_….png`.
- **Die drei Regiebücher:**
  - **Ein Sommertag an der Front:** 16.1.2026, 6 bis 22 Uhr, Zeitraffer 60, 16 Minuten, zehn Fotos. Seenebel am Morgen, eine Scheibe an der Südflanke, die Bootsfahrt mit einem Turm, auf dem Eis, durch die Spalte, vom Berg, ein Turm vom Steg, unter Wasser, abends Licht von Westen.
  - **Die Ruptura:** 4.3.2027, die Uhr steht, die Ruptura mit ihrem eigenen Zeitraffer. 5½ Minuten, neun Fotos; die Fotos liegen in der Mitte der Abschnitte.
  - **Südhimmel:** 19.7.2026, 20:30 bis 23:30, Zeitraffer 30, sechs Fotos. Zunehmender Mond (36 %) im Nordwesten über dem Eis, Kreuz des Südens, ein Bruch im Dunkeln.
- **Bedienfeld:** Block REGIEBÜCHER (Auswahl, „Von vorn“, „Anhalten“). HUD-Zeile „Regie“.
- **Fahrt „Durch die Spalte“** (`Gletscher.spaltenWeg`): Gesucht wird eine Spalte, 150 bis 1 500 m hinter der Front.
  - Sie muss mindestens 14 m tief sein und fast gerade laufen.
  - Die Kamera fährt in halber Tiefe ihr entlang, die Wände mindestens 2 m entfernt.
  - Anflug aus 25 m und Aufstieg am Ende.
  - Die Suche dauert gut 2 s und läuft nach dem Start nebenher.
- **Ohne Fenster:** `REGIE=Sommertag` (oder `Ruptura`, `Südhimmel`) mit `tools/Tempo` spielt ein Regiebuch mit Blatt durch.
- **Befunde aus Phase 13:**
  - `Bitte_Fixen.png`: Zellen des Damms ragten als Splitter über Fels, Ufer und Wasser. Jetzt füllen Platzhalter-Zellen den Quader neben dem Damm und schmelzen nach dem Zerlegen weg. Die Prüfung zählt die Splitter (0).
  - Das Netz des Damms entsteht höchstens alle 0,5 s neu.
  - Die Gischt an der Mündung ist halb so häufig.
  - Die Brücke steht mindestens 20 s, bevor das Wasser unter ihr aufholt.

## Oberfläche und Datenbank (Phase 15)

- **Schema GLACIER** (F20, F23–F36 „alle A“): 21 Tabellen, 9 räumliche Spalten in 8307, 9 Sichten; Skripte, Einrichter und QGIS in [db/README.md](../../../../db/README.md). Ohne Datenbank dieselben Daten in `daten/` (die Chronik als CSV mit WKT); „Übernehmen“ bringt sie später in die Datenbank.
- **Chronik** (`data/Chronik`, eigener Faden, schreibt in einem zweiten): jede Kalbung (Punkt, Riss an der Wand, Volumen, Stücke, Fallzeit (seit Phase 16 ab dem Bruch des Hauptstücks), Vorwarnung, Welle am Pegel „Steg“ nach 60 s), jeder Eisberg ab 200 m³ mit seinem Weg und seinem Ende, die Front einmal je Szenentag um 12 Uhr, nach Kalbungen ab 10 000 m³ und auf Knopfdruck, jeder Lauf der Ruptura und der Regiebücher (mit Blatt). Die Kalbung schreibt sie 30 s Physik nach dem Bruch, wenn die Stücke gefallen sind.
- **Block CHRONIK:** Zähler des Tages, die letzten Kalbungen (hinfliegen), „Front merken“, **„Tageskarte“** (PNG nach `docs/status`, wie `GLC_TAGESKARTE_V` in QGIS), Chronik an/aus, eine Sitzung leeren.
- **Block DATEN:** Speicherort, Verbinden, Einrichten, Prüfen, Ohne Datenbank, Weiter wie zuletzt, Übernehmen, Presets je Package, gemerkte Ansichten (Taste V), Protokoll.
- **Block EIS:** Vorstoß von Hand (sonst der Jahresgang), Schmelzkerbe, Zugfestigkeit des Eises.
- **Säulen- und Eisbergkarte** (`ui/Karte`, `Renderer.pick`): ein Klick auf die Front zeigt die Säule (Höhe, Grund, Abschnitt, Auslastung, Reserve, Kalbungen im Umkreis), „Schwächen“ löst dort aus (Auslöser KARTE); ein Klick auf einen Eisberg zeigt Volumen, Tiefgang, Alter, Weg und Ursprung.
- **Startbild** mit Datenbank (Passwort, Einrichten, Stand, letzte Sitzung) und **„Weiter wie zuletzt“** (`ui/Weiter`): Uhr, Wetter, Kamera, Regiebuch an seiner Stelle, Regler aller Packages, Ruptura, dazu die offenen Eisberge aus der Chronik (Form neu aus dem Seed, ihre Zeile wird weitergeschrieben).
- **Befunde aus Phase 14:** Lichtungen an den Ansichten Südflanke und Brazo Rico (`Vegetation.viewClearing`); „Unter Wasser“ sucht den Ort von der heutigen Front aus.

## Schliff und Kino (Phase 16)

- **Kinomodus** mit `com.dan.kino` ([README](../kino/README.md)):
  - Starten im Startbild („Im Kino“) oder im Block REGIEBÜCHER (Haken „Im Kino zeigen“).
  - Die App wird zum Vollbild ohne Bedienfeld, Statuszeile, Titelleiste, HUD und Mauszeiger, mit Balken auf 2,39 : 1.
  - Vorspann mit Titel und Beschreibung des Regiebuchs; Untertitel aus den Texten der Stichworte (`Regie.beiText`) und aus `sage`.
  - Überblendung bei jedem Schnitt: Ansicht, Fahrt, Boot und Aussteigen laufen über `Renderer.schnitt`, das Bild vor dem Schnitt merkt sich `KinoGl`.
  - In stehenden Einstellungen ein Schwenk von ±3°; dazu Filmkorn und Randabdunklung. Der Ton folgt dem Schwarz.
  - Abspann mit Ort, Szenenzeit, Gelände, Packages und der Zahl der Fotos.
  - Die Fotos des Blatts bleiben ohne Balken; F12 fotografiert mit Balken.
  - Tasten: Esc heraus (das Regiebuch läuft weiter), Leertaste hält an.
  - Ohne Fenster: `Tempo` mit `REGIE=… KINO=1` schreibt Kinobilder `png_kino_01.png` … (Titel, Aufblende, jedes Foto, Abspann).
- **Dauerlauf** (`app/Dauerlauf`, Block TECHNIK oder `-Dglacier.dauerlauf=Minuten`):
  - Ablauf: die drei Regiebücher im Kino, dann drei Minuten frei im Zeitraffer 120, im Wechsel. Blätter schreibt er dabei nicht.
  - Alle 5 s misst er Bilder/s, Speicher, Fäden, Eisberge, Stücke, die Physik und Fehler.
  - Bericht: `docs/status/Dauerlauf_….png` und `.txt`, Urteil „bestanden“ oder „nicht bestanden“.
- **Qualitätsstufen durchgängig:** Auch Gischt, Nässe der Stege und Vögel folgen der Stufe. Fest bleiben die Welle (ihr Gitter entsteht beim Start) und der Klang (die Abtastrate läuft im Lautsprecher).
- **Befunde aus Phase 14 und 15:**
  - Das Treibeis beim Start steht nicht mehr als 147 Eisberge in der Chronik; nur Eisberge aus Kalbung oder Ruptura werden geschrieben.
  - Das Deck der Stege ist ein Rost aus Stäben mit Fugen statt einer grauen Fläche.
  - „Auf dem Eis“: In der Nähe gibt es eine zweite, feinere Lage Buckel (rund 1 m) und Staub in Flecken (bis 22 % dunkler).
  - Die Fallzeit zählt jetzt ab dem Bruch des Hauptstücks (`Kalbung.Ereignis.mainBreak`), nicht mehr ab dem ersten kleinen Brocken.
- **Verknüpfung:** `Glacier_Verknuepfung.bat` legt sie mit `Glacier.ico` auf den Desktop und ins Startmenü. **Quellenliste:** `docs/QUELLEN.md`.

## Raum und Zeit

- 1 Einheit = 1 m, x Ost, y oben, z Süd (Nord = −z), Ursprung an der Spitze der Front auf dem Seespiegel (50°28′23″ S, 73°02′13″ W); Lage in SRID 8307.
- Die Uhr zählt argentinische Zeit (UTC−3). Start 16. Januar 2026, 15:00 Uhr [G]. Die Sonne steht mittags im Norden, gegen 14 Uhr.

## Schalter

`-Dglacier.splash=false` ohne Startbild · `-Dglacier.dauerlauf=Minuten` Dauerlauf gleich nach dem Start · `-Dglacier.gljpanel=true` leichter GL-Rückfall · `-Dglacier.shaders=<ordner>` Shader von der Platte (F5 lädt neu) · `-Dglacier.daten=<ordner>` statt `daten/`.

## Prüfen

- `ant pruefung` (oder `com.dan.glacier.test.Pruefung` mit „Run File“) endet mit „ERGEBNIS: alles in Ordnung“.
- `ant check-packages` übersetzt jedes Element-Package einzeln; läuft nach jedem Übersetzen.
- `com.dan.glacier.test.DbPruefung` mit „Run File“: die Prüfung gegen die Datenbank (Schema GLACIER), endet mit „Alles in Ordnung.“
- Dauerlauf (Phase 16): Block TECHNIK → „Dauerlauf 30 min“; Bericht in `docs/status/Dauerlauf_….txt`, Urteil „Dauerlauf bestanden“.
