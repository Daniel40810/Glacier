# db – Datenbank von Glacier

Schema `GLACIER` auf PDBORCL (angelegt mit `USER.sql`, Rolle DB_CONNECT), Präfix `GLC_`, Spatial mit SRID 8307 (WGS 84, Länge und Breite). Werkbuch Abschnitt 8, Fragen F23–F36 „alle A“ (18.10.2026). Der Zugang steht in `db.properties` (Vorlage `db.properties.example`, nie ins Git); fehlt die Datei, legt die App sie aus der Vorlage an, fragt im Startbild nach dem Passwort und trägt es nach dem ersten erfolgreichen Verbinden ein. Ohne Datenbank läuft die App weiter und speichert in den Ordner `daten/` des Projekts.

## Einrichten

Startbild oder Block DATEN, Knopf **Einrichten**. Der Einrichter (`com.dan.store.Einrichter`) führt die Skripte in `sql/` der Reihe nach aus und prüft danach alles: Tabellen, Spalten mit Typ, Sequenzen, Trigger, Sichten, Spatial-Metadaten, räumliche Indizes und die Zahl der mitgelieferten Zeilen; dazu rechnet er Front und Ausschnitt auf dem Ellipsoid nach. Am Ende steht „alles in Ordnung“ oder genau, was fehlt. Er löscht nie etwas und lässt sich beliebig oft wiederholen.

| Skript | Inhalt |
|---|---|
| `sql/01_tabellen.sql` | 21 Tabellen, 20 Sequenzen, 19 Trigger (Sequence + Trigger, nie IDENTITY), 23 Indizes |
| `sql/02_spatial.sql` | Metadaten für neun Geometriespalten und neun räumliche Indizes |
| `sql/03_sichten.sql` | Neun Sichten: `GLC_FRONT_V`, `GLC_KALBUNG_V`, `GLC_KALBUNG_TAG_V`, `GLC_KALBUNG_MONAT_V`, `GLC_EISBERG_V`, **`GLC_TAGESKARTE_V`** (alles eines Tags als eine Ebene für QGIS), `GLC_RUPTURA_V` (belegt und gespielt), `GLC_BEFUND_V`, `GLC_LETZTE_SITZUNG_V` |
| `sql/04_startdaten.sql` | 23 Quellen, 27 Befunde, das Gelände mit Prüfsummen, 13 Fronten (die des Geländes und zwölf Monatsfronten), 32 belegte Rupturas 1917–2019, 12 Wetterlagen, 3 Regiebücher mit 77 Stichworten, 8 Ansichten, 3 Fahrten, 64 Presets. Erzeugt aus `com.dan.glacier.data.Startdaten` mit `tools.StartdatenSql`, nicht von Hand ändern |
| `sql/99_entfernen.sql` | Entfernt alle `GLC_`-Objekte samt Inhalt. Nur von Hand für einen Neuanfang, die App führt es nie aus |

Dieselben Skripte lassen sich in SQL Developer als Skript ausführen (F5). Bei einem zweiten Lauf meldet Oracle ORA-00955 (Name schon vergeben); das ist harmlos.

Meldet der räumliche Index ORA-01031 oder ORA-13249, fehlt dem Schema ein direkt erteiltes Recht (eine Rolle reicht dafür nicht). Als DBA: `GRANT CREATE TABLE, CREATE SEQUENCE TO GLACIER;`, dann noch einmal einrichten.

## Prüfen

`test/com/dan/glacier/test/DbPruefung.java` in NetBeans mit „Run File“ starten. Sie richtet ein, läuft den Einrichter ein zweites Mal, prüft mit ihm, rechnet Front und Ausschnitt auf dem Ellipsoid nach, prüft alle Geometrien auf Gültigkeit, schreibt eine Chronik für den 31.12.2099 (Fronten, Kalbungen mit Riss, Welle, Eisberge mit Weg, Lauf der Ruptura, Lauf eines Regiebuchs mit Blatt), liest sie über die Sichten und als Tageskarte zurück, speichert Regiebuch, Ansicht und Preset unter „~Prüfung …“, sichert eine Sitzung, übernimmt Dateien und räumt danach auf. Ende: „Alles in Ordnung.“

## Tabellen

| Thema | Tabelle | Inhalt |
|---|---|---|
| Betrieb | `GLC_SCHEMA` | Ausbaustufe (Phase 15 = 1) |
| | `GLC_SITZUNG` | Sitzungen; Zustand (CLOB) alle zwei Minuten und beim Ende, Szenenzeit als TIMESTAMP in Ortszeit ART (UTC−3), Zeitraffer, Regiebuch mit Stelle, Vorstoß, Kerbe, Stand der Ruptura, Zahl der Eisberge |
| | `GLC_LOG` | Protokoll; älter als 90 Tage räumt die App beim Start weg |
| Quellen | `GLC_QUELLE`, `GLC_BEFUND`, `GLC_BEFUND_QUELLE` | Quellen; Befunde je Gegenstand: `BELEGT`, `GEMESSEN` oder `GESTALTET`, mit ihren Quellen |
| Gelände, Front | `GLC_GELAENDE` | Ausschnitt (Fläche), Ursprung der Szenenmeter, Raster, Seespiegel, Zeitraum des DGM, SHA-256 der Dateien in `daten/gelaende` |
| | `GLC_FRONT` | Die Front als Multilinie je Zeitpunkt: `GELAENDE`, `MODELL` (mitgeliefert), `SITZUNG` (je Szenentag um 12 Uhr und nach Kalbungen ab 10 000 m³), `MERKEN`, `BELEGT` (in QGIS nachgezeichnet) |
| Chronik | `GLC_KALBUNG` | Jeder Bruch: Szenenzeit, Art, Auslöser (Regie, Hand, Karte, Regiebuch), Punkt, Riss als Linie, Volumen, Stücke, Höhe, Fallzeit, Vorwarnung, Welle am Pegel „Steg“ |
| | `GLC_EISBERG` | Jedes Stück ab 200 m³: Herkunft, Geburt, Weg als Linie, Tiefgang, Kenterungen, Ende |
| | `GLC_RUPTURA` | Die belegten Rupturas 1917–2019 mit Datum (so genau wie bekannt), Stau, Abfluss, Dauer, Quelle |
| | `GLC_RUPTURA_LAUF` | Jeder Lauf in der App: Zeitpunkte der Abschnitte, größter Stau und Abfluss, Damm als Fläche, Rinne als Linie |
| Regie | `GLC_WETTERLAGE`, `GLC_REGIEBUCH`, `GLC_STICHWORT`, `GLC_REGIELAUF` | Wetterlagen; Regiebücher als Text und in Stichworten; jeder Lauf mit Blatt (PNG) |
| Kamera | `GLC_ANSICHT`, `GLC_FAHRT`, `GLC_STUETZPUNKT` | Feste Ansichten 1–8 und gemerkte (Taste V) mit Vorschau; Fahrten |
| Packages | `GLC_PAKET_PRESET`, `GLC_PAKET_WERT` | Presets je Package, ein öffentliches Feld je Zeile |

Mitgelieferte Zeilen (`HERKUNFT = 'MITGELIEFERT'`) stellt der Einrichter bei jedem Lauf wieder her; eigene Zeilen (`EIGEN`) fasst er nie an. Die App überschreibt Mitgeliefertes nicht: ein Regiebuch oder Preset bekommt einen eigenen Namen.

## QGIS

Neue Verbindung zu Oracle (Datenbank `PDBORCL`, Benutzer `Glacier`), Ebene `GLC_TAGESKARTE_V` mit Geometriespalte `GEOM` und Schlüssel `OBJEKT_ID`; Filter `"TAG" = '2026-01-16'`, Stil nach `EBENE` und `ART`. Ohne Datenbank: `daten/chronik/*.csv` als „Getrennte Texte“ (Semikolon, Geometrie aus der Spalte `WKT`, EPSG:4326). Dieselbe Karte zeichnet die App als PNG (Block CHRONIK, „Tageskarte“).
