package com.dan.glacier.wiring;

import com.dan.cloud.CloudType;
import com.dan.cloud.Clouds;
import com.dan.fog.Fog;
import com.dan.lightning.Lightning;
import com.dan.rain.Rain;
import com.dan.sky.Sky;
import com.dan.weather.Weather;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Die Verdrahtung der App (Paketvertrag Punkt 4): Hier und nur hier reden die Packages miteinander. Jedes Package
 * bietet Eingänge an und meldet Ereignisse über die kleinen Schnittstellen dieses Pakets; die Phasen 2 bis 15 hängen
 * ihre Verbindungen hier ein. Wo ein Package eine eigene Kopie einer Schnittstelle mitbringt (gleicher Name, gleiche
 * Form), übersetzt {@code wiring} mit einer Zeile.
 * <p>
 * Phase 1: Uhr, Wind von Hand (Westwind vom Eisfeld, Bft 4 [G]), fester Seespiegel, Brazo Rico ohne Stau; alle
 * Schnittstellen aus dem Werkbuch (Abschnitt 4) stehen. Phase 2: das Gelände aus Copernicus GLO-30 als {@link Terrain};
 * der See ({@code com.dan.ocean}, Preset „Lago“) bekommt Wind, Seespiegel und das Gelände als Grund; Wald
 * ({@code com.dan.forest}: Lenga, Ñire, Guindo), Steppe und Ufergras ({@code com.dan.reed}) und die Bodendecke
 * ({@code com.dan.ground}) bekommen Gelände, Wind und den Tag im Jahr – um ein halbes Jahr versetzt, weil die Packages
 * ihr Laubjahr auf der Nordhalbkugel zählen ({@link #northernDay}). Phase 3 (wie Haithabu): die Wetterregie
 * ({@code com.dan.weather}) mit dem Klima Patagoniens ({@link #PATAGONIA}: Westwind vom Eisfeld, Föhnsturm, selten
 * Gewitter) setzt je Bild Wind, Böen, Bedeckung, Wolkenart, Regen, Sicht, Nebel und Gewitter; daraus bekommen Himmel
 * ({@code sky}), Wolken ({@code cloud}, mit Lentikularis im Westwind), Regen ({@code rain}), Seenebel ({@code fog})
 * und Blitz ({@code lightning}) ihre Eingänge. {@link #planned()} nennt, was nach dem Phasenplan kommt,
 * {@link #links()} was schon verbunden ist.
 */
public final class Wiring {
    /** Eine Verbindung: von wem, an wen, über welche Schnittstelle, in welcher Phase. */
    public record Link(String from, String to, String via, int phase) {
        @Override public String toString() { return from + " → " + to + " (" + via + ", Phase " + phase + ")"; }
    }

    /** Wind von Hand, bis {@code weather} ihn in Phase 3 setzt. Start: Bft 4 aus West vom Eisfeld [G]. */
    public static final class ManualWind implements Wind {
        public volatile double speed = Beaufort.mid(4), fromDeg = 270, gust = 0.35;
        @Override public double speed() { return speed; }
        @Override public double fromDeg() { return fromDeg; }
        @Override public double gustiness() { return gust; }
    }

    /** Seespiegel von Hand: Hauptsee und Brazo Rico (Stau in m über dem Hauptsee). */
    public static final class ManualLevel implements WaterLevel {
        public volatile double level, stau;
        @Override public double level() { return level; }
        @Override public double brazoRico() { return level + stau; }
    }

    public final Clock clock;
    public final ManualWind wind = new ManualWind();
    public final ManualLevel waterLevel = new ManualLevel();
    /** Phase 11: das Jahr (com.dan.season, Klima vor der Front, Südhalbkugel). */
    public final Jahr jahr = new Jahr();
    /** Phase 2: das Gelände (die App setzt es, sobald es geladen ist). */
    public volatile Terrain terrain;

    // ------------------------------------------------------------------ Phase 3: Wetter
    /** Die Wetterregie mit dem Klima Patagoniens; Tagesgang in argentinischer Zeit. */
    public final Weather weather = new Weather();
    /** Ob die Regie die Eingänge setzt (die App schaltet sie ein; Werkzeuge und Prüfungen setzen die Felder selbst). */
    public volatile boolean regie;
    /** Wind in der laufenden Böe (m/s), Luftdruck (hPa) und Tendenz, Lufttemperatur (°C). */
    public volatile double gustNow = Beaufort.mid(4), pressureHPa = 1010, pressureTendency, airTempC = 13;
    /** Sicht in km (klare Andenluft), Regen in mm/h, Bedeckung in Achteln, hohe Schicht 0..1. */
    public volatile double visibilityKm = 70, rainRate = 0, oktas = 2, highCover = 0.15;
    public volatile CloudType cloudType = CloudType.CUMULUS;
    /** Gewitter 0..4 wie {@link Lightning.Level} (in Patagonien selten). */
    public volatile Lightning.Level thunderstorm = Lightning.Level.OFF;
    /** Seenebel 0..1 aus der Regie; Nebel zeichnen (Schalter im Bedienfeld). */
    public volatile double fogAmount;
    public volatile boolean fogOn = true;
    /** Lage nach einem Schauer für einen Regenbogen (0..1); Regenbogen von Hand, negativ = aus der Regie. */
    public volatile double showerEnd, rainbowHand = -1;
    /** Lentikularis 0..1 (geglättet), von Hand (negativ = aus Lage und Wind). */
    public volatile double lens, lensHand = -1;
    /** Zeichnet die App echte Wolken? Dann bekommt der Himmel keinen Schleier mehr. */
    public volatile boolean cloudsDrawn;
    /** Unter- und Obergrenze des Nebels zuletzt (m über dem See; für HUD und Prüfung). */
    public volatile double fogBase, fogTop;
    private double lastLensT = Double.NaN;
    private final List<Link> links = new ArrayList<>();
    /** Feste Körper der Szene (Phase 5: der Gletscher), für Kamera, später Gischt und Regen (Collider). */
    public final List<Collider> colliders = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Der Plan aus dem Werkbuch, Abschnitt 4: wer wem was gibt, ab welcher Phase. */
    private static final List<Link> PLANNED = List.of(
            new Link("App (Stege, Katamaran)", "rain (Tropfen auf Deck und Rost, Klang der Tropfen)", "Collider, SurfaceHit", 16));

    public Wiring(Clock clock) {
        this.clock = clock;
        weather.zone = java.time.ZoneOffset.ofHours(-3);
        weather.targets = PATAGONIA;
        weather.transitions = PATAGONIA_TRANSITIONS;
        weather.dwellHours = PATAGONIA_DWELL;
        // Phase 11: die Regie kennt die Jahreszeit (Monatsmittel vor der Front, Sommer im Januar)
        weather.climate = jahr.klima.temp.clone();
        weather.summerDay = 15;
        weather.seed = 2026;
        link("Uhr (SceneClock)", "Himmel-Platzhalter, Bühne, Kamera, HUD", "Clock (Datum, Sonne über Norden)", 1);
        link("App (Hand)", "Bühne (Platzhalter-Wellen, ohne Höhendaten)", "Wind, WaterLevel", 1);
        link("Gelände (Copernicus GLO-30)", "Kamera, Ansichten", "Terrain (Höhe, Eis, Stege)", 2);
        link("App (Hand)", "ocean (Lago)", "Wind, WaterLevel", 2);
        link("Gelände", "ocean (Lago)", "Grund als Höhentextur (Uferdämpfung, Trübung, Schaumsaum)", 2);
        link("Gelände", "forest (Lenga, Ñire, Guindo)", "Terrain, Dichte je Art aus Vegetation", 2);
        link("Gelände", "reed (Steppe, Ufergras)", "Terrain, Dichte je Art aus Vegetation", 2);
        link("Gelände", "ground (Biom Patagonien)", "Terrain, Bedeckung (Moräne, Steppe, Waldboden)", 2);
        link("Uhr", "forest, ground, reed", "Tag im Jahr, um ein halbes Jahr versetzt (Südhalbkugel)", 2);
        link("App (Hand)", "forest, reed, ground", "Wind", 2);
        link("forest", "Gelände, ground", "Sonnenschatten (eine Kaskade)", 2);
        link("weather (Klima Patagonien)", "Wind → ocean, forest, reed, ground, cloud, rain, fog", "Wind, Böe (Westwind vom Eisfeld, Föhnsturm)", 3);
        link("weather", "cloud, rain, fog, sky, lightning", "Bedeckung, Art, Lentikularis, Regen, Sicht, Nebel, Gewitter", 3);
        link("Uhr", "sky", "Clock → Zeit, Breite, Länge (Sonne über Norden, Südhimmel, Magellansche Wolken)", 3);
        link("sky", "Gelände, ocean, forest, ground, reed, cloud, fog, rain", "Lighting (Sonne, Mond, Himmel, Dunst, vorbelichtet)", 3);
        link("sky, cloud", "Nachbearbeitung", "Augenanpassung, Stäbchensehen", 3);
        link("cloud", "Gelände, ocean, forest, ground, reed", "Wolkenschatten (Karte um das Auge)", 3);
        link("cloud", "rain, lightning", "Regenfeld, Durchlässigkeit", 3);
        link("lightning", "cloud, ocean, Gelände", "Flash (Lichtpunkte)", 3);
        link("Gelände", "lightning", "Terrain (Einschlag am Boden)", 3);
        link("sky", "ice (Eisberge, Gletscher)", "Lighting (Licht im Eis, vorbelichtet, Himmelsrundbild)", 4);
        link("App (Seespiegel)", "ice", "WaterLevel (Wasserlinie der Eisberge, Front, Schmelzkerbe)", 5);
        link("Gelände (GLO-30, Grund unter dem Eis)", "ice", "Terrain → Raster des Gletschers (Höhe, Grund, Eis, See)", 5);
        link("ice", "Kamera, Ansichten, Fahrt", "Collider (Oberfläche mit Spalten)", 5);
        link("Uhr", "ice", "Vorstoß der Front nach der Jahreszeit (Juni bis Dezember vor, Dezember bis April zurück)", 5);
        link("ice", "river (Schmelzbäche)", "Gefälle der Zunge, Rinnen", 5);
        link("Gelände, Gletscher (Oberfläche mit Spalten), Seespiegel", "fracture", "Ground, Water (Boden und Wasser der Stücke im Faden der Physik)", 6);
        link("ice (FrontBlock: Abschnitt der Front, Spalten schwächen)", "fracture", "Cells (Umriss, Dichte, Festigkeit je Ort)", 7);
        link("fracture (gebrochene Flächen)", "ice (Netz des Abschnitts)", "Crack (der Riss als Verdeckung, bevor er bricht)", 7);
        link("fracture (onImpact)", "ocean (Ringwellen, bis die Welle steht), spray (Einschlag ohne Körper)", "Impact (Ringwelle, Schwall)", 7);
        link("weather", "spray", "Wind", 7);
        link("Uhr (Tag im Jahr, Zeitraffer)", "Kalbung", "Clock (Abbrüche je Stunde, Kerbe in Szenenzeit)", 7);
        link("App (Seespiegel)", "wave", "WaterLevel (Ruhespiegel der Welle)", 8);
        link("Gelände (Grund unter dem See), Gletscher (Front als Wand)", "wave", "Terrain → Ruhetiefe beider Gitter", 8);
        link("fracture (onImpact)", "wave", "Impact (verdrängtes Volumen je Schritt ist die Quelle)", 8);
        link("wave", "ocean", "Höhentextur der langen Welle (Spiegel, Neigung, Schaum an der Stirn)", 8);
        link("ocean + wave", "fracture", "Sea (Stücke schwimmen auf der Welle)", 8);
        link("wave (Auflaufen)", "Gelände", "Runup (nasser Saum an Fels und Ufer)", 8);
        link("App (Seespiegel), wave, Gelände", "buoyancy (Eisberge)", "Sea (Höhe = Spiegel + Welle, Grund, die Front als Wand)", 9);
        link("weather", "buoyancy (Eisberge, Treibeis)", "Wind (treiben mit wenigen Prozent)", 9);
        link("fracture (onSettle)", "buoyancy", "Stücke, die auf dem Wasser zur Ruhe kommen, werden Eisberge", 9);
        link("fracture (onImpact)", "buoyancy (Treibeis)", "Impact (was beim Aufschlag zerbröselt)", 9);
        link("buoyancy", "ocean", "Umrisse der Eisberge (Brandungsringe), Treibeis als Textur", 9);
        link("Uhr (Zeitraffer)", "buoyancy", "Clock (Schmelze in Szenenzeit)", 9);
        link("season (Klima vor der Front, Südhalbkugel)", "weather", "Monatsmittel und Abweichung des Tags (die Lagen liegen um das Mittel, Sommer im Januar)", 11);
        link("weather (Temperatur, Niederschlag, Bedeckung, Wind), Sonne", "season", "das Wetter des Augenblicks (Schnee, Schmelze, Eis, Reif)", 11);
        link("season", "Gelände", "Season (Schneegrenze auf den Hängen, Schnee am Ufer)", 11);
        link("season", "ice (Zunge)", "Season (Schnee auf dem Eis nach der Schneegrenze)", 11);
        link("season", "forest, ground", "Season (Laub, Lenga-Rot, Schnee in den Kronen und am Boden)", 11);
        link("season", "ocean (Brazo Rico)", "Season (Eis in den flachen Buchten: Dicke und Grenze als Wassertiefe)", 11);
        link("season", "Kalbung", "Season (Schmelzkerbe nach dem Wasser vor der Front)", 11);
        link("season", "rain", "Season (unter 0,5 °C fällt Schnee: die Flocken zeichnet season)", 11);
        link("season (Jahresgang)", "App (Seespiegel des Brazo Rico)", "WaterLevel.brazoRico (Stau in einem Jahr mit Damm)", 11);
        link("weather", "soundscape", "Wind, Böen, Regen, Temperatur (Schallgeschwindigkeit), Seegang", 12);
        link("App (Kamera)", "soundscape", "Ohr (Ort, Blick; über Eis, Wald oder Wasser: Rauigkeit für den Wind)", 12);
        link("fracture (onCrack, onBreak)", "soundscape", "Crack (Knacken; der Weg des Risses wird der Kanal des Donners der Kalbung)", 12);
        link("fracture (onImpact)", "soundscape", "SoundEvent (Aufschlag, der größte je Fünftelsekunde)", 12);
        link("wave (onRunup)", "soundscape", "Runup (Rauschen des Auflaufens am Ufer)", 12);
        link("flock (onCall)", "soundscape", "SoundEvent (Möwe, Kondor)", 12);
        link("buoyancy (onCapsize, Katamaran)", "soundscape", "SoundEvent (kenternder Berg), Diesel (an Bord gleichmäßig)", 12);
        link("lightning", "soundscape", "SoundEvent (Donner)", 12);
        link("App (Stege)", "soundscape", "Collider (Geländer singen im Wind, Pfähle glucksen)", 12);
        link("App (Katamaran)", "ocean", "Kielwasser (Ringwellen von den Rümpfen)", 12);
        // Phase 13: die Ruptura
        link("ice (IceDam)", "fracture", "Cells (der Eisdamm an der Halbinsel: Rinne, Krone mit Spalten, Brücke fester)", 13);
        link("season (Jahresgang)", "App (Ruptura)", "Season (Jahr mit Damm, Stau; Anfang März bricht der Damm)", 13);
        link("App (Ruptura)", "ocean", "WaterLevel.brazoRico (zweiter Spiegel in der Maske des Brazo Rico)", 13);
        link("App (Ruptura)", "fracture", "Sea (Wasser im Brazo Rico und in der Rinne), Current (die Strömung trägt Stücke)", 13);
        link("App (Ruptura)", "wave", "Impact (Abfluss bis 8 000 m³/s als Quelle im Kanal)", 13);
        link("App (Ruptura)", "spray", "Impact (Schwälle an der Mündung)", 13);
        link("App (Ruptura)", "soundscape", "Torrent (Tosen nach dem Durchfluss)", 13);
        link("App (Ruptura)", "forest, reed, ground", "Terrain (wo der Damm liegt, wächst nichts)", 13);
        link("weather", "flock", "Wind (Möwen stehen im Aufwind, Kondore im Hangwind, Sturm vertreibt)", 10);
        link("buoyancy (Eisberge)", "flock", "Sitzplätze, die mit den Bergen treiben (die höchste Ecke)", 10);
        link("buoyancy (Katamaran)", "flock", "Störer (das Boot scheucht die Möwen)", 10);
        link("App (Seespiegel), rain (Regenrate), sky (Sonne)", "weathering (Stege)", "WaterLevel, Regen, Sonne (nass und trocken)", 10);
        link("wave (onRunup)", "weathering (nasse Stege)", "Runup (Auflaufen am Anleger setzt die nasse Linie)", 10);
        link("App (Stege: Pfähle)", "spray", "Collider (Körper, an denen Gischt anschlägt)", 10);
        link("spray (onHit)", "weathering (Stege)", "SurfaceHit (ein Spritzer macht den Pfahl bis dort nass)", 10);
        link("ocean + wave", "buoyancy (Katamaran)", "Sea (Spiegel + lange Welle + Windsee, Strömung der Welle)", 10);
        link("buoyancy (Katamaran)", "Kamera", "Mitfahrt (Auge an Bord, Stampfen und Rollen)", 10);
    }

    // ------------------------------------------------------------------ Phase 5: der Gletscher

    /** Weite des Vorstoßes über das Jahr (m) [G]: die Front steht im Dezember am weitesten vorn, im Juni am weitesten hinten. */
    public static final double ADVANCE_AMPLITUDE = 40;

    /** Vorstoß der Front (m) am Tag im Jahr (Südhalbkugel): +40 m um den 11. Dezember, −40 m um den 10. Juni [G]. */
    public static double glacierAdvance(int dayOfYear) {
        return ADVANCE_AMPLITUDE * Math.cos(2 * Math.PI * (dayOfYear - 345) / 365.0);
    }

    /** Der Gletscher als fester Körper: Abstand zur Oberfläche (mit Spalten) über dem Eis, sonst weit weg. */
    /** Phase 7: ein Abschnitt der Front als {@link Cells} (was {@code fracture} vom Eis wissen muss). */
    public static Cells cells(com.dan.ice.FrontBlock b) {
        return new Cells() {
            @Override public double[] bounds() { return b.bounds(); }
            @Override public double[] planes() { return b.planes(); }
            @Override public double top(double x, double z) { return b.top(x, z); }
            @Override public double bottom(double x, double z) { return b.bottom(x, z); }
            @Override public double[] front(double x, double z) { return b.front(x, z); }
            @Override public boolean inside(double x, double y, double z) { return b.inside(x, y, z); }
            @Override public double density(double x, double y, double z) { return b.density(x, y, z); }
            @Override public double strength(double x, double y, double z) { return b.strength(x, y, z); }
        };
    }

    /** Phase 13: der Eisdamm an der Halbinsel als {@link Cells} (die Rinne längs, Krone mit Spalten, Brücke fester). */
    public static Cells cells(com.dan.ice.IceDam d) {
        return new Cells() {
            @Override public double[] bounds() { return d.bounds(); }
            @Override public double[] planes() { return d.planes(); }
            @Override public double top(double x, double z) { return d.top(x, z); }
            @Override public double bottom(double x, double z) { return d.bottom(x, z); }
            @Override public double[] front(double x, double z) { return null; }
            @Override public boolean inside(double x, double y, double z) { return d.inside(x, y, z); }
            @Override public double density(double x, double y, double z) { return d.density(x, y, z); }
            @Override public double strength(double x, double y, double z) { return d.strength(x, y, z); }
        };
    }

    public static Collider glacierCollider(com.dan.ice.Glacier g) {
        return (x, y, z) -> g.covers(x, z) ? y - g.surface(x, z) : 1e6;
    }

    // ------------------------------------------------------------------ Phase 3: das Klima Patagoniens [G]

    /**
     * Zielwerte je Lage am Perito Moreno im Sommer [G] (Spalten wie {@link Weather.Var}: Wind m/s, Richtung, Böigkeit,
     * Bedeckung /8, hohe Schicht, Wolkenart, Regen mm/h, Sicht km, Nebel, Druck hPa, Temperatur °C, Feuchte %, Dünung m,
     * Gewitter). Westwind vom Südlichen Patagonischen Eisfeld, sehr klare Luft, kühl; Fronten bringen Regen aus West,
     * Gewitter sind selten; Föhnsturm im Lee der Anden: stürmisch, trocken, warm, große Sicht.
     */
    public static final double[][] PATAGONIA = {
            //  Wind  Richt Böig Bed  hoch Wolke Regen Sicht Nebel Druck Temp Feuchte Dünung Gewitter
            {6.5, 275, 0.25, 2.0, 0.15, 0, 0.0, 70, 0.0, 1010, 13, 55, 0.3, 0},   // klar
            {10.0, 300, 0.25, 5.0, 0.70, 1, 0.0, 45, 0.0, 1004, 12, 65, 0.4, 0},  // aufziehend
            {14.0, 290, 0.28, 8.0, 0.20, 3, 3.0, 12, 0.0, 994, 8, 92, 0.6, 0},    // Front
            {21.0, 270, 0.32, 7.0, 0.05, 1, 1.5, 25, 0.0, 988, 7, 80, 0.8, 0},    // Sturm
            {14.0, 280, 0.30, 6.5, 0.00, 4, 9.0, 8, 0.0, 996, 10, 88, 0.6, 2},    // Gewitter
            {12.0, 255, 0.25, 5.0, 0.10, 1, 0.5, 35, 0.0, 1000, 9, 75, 0.5, 0},   // abklingend
            {9.0, 250, 0.22, 3.0, 0.10, 0, 0.0, 80, 0.0, 1008, 11, 60, 0.4, 0},   // aufklarend
            {1.5, 90, 0.08, 1.5, 0.20, 2, 0.0, 15, 0.6, 1016, 6, 97, 0.1, 0},     // Seenebel am Morgen
            {20.0, 285, 0.38, 2.0, 0.10, 0, 0.0, 100, 0.0, 1000, 18, 30, 0.6, 0}}; // Föhnsturm

    /** Übergänge der freien Regie in Patagonien [G] (Zeile: von, Spalte: nach; Reihenfolge wie {@link Weather.Phase}). */
    public static final double[][] PATAGONIA_TRANSITIONS = {
            {0, 0.45, 0, 0, 0, 0, 0, 0.15, 0.40},
            {0.15, 0, 0.60, 0, 0, 0, 0, 0, 0.25},
            {0, 0, 0, 0.45, 0.05, 0.50, 0, 0, 0},
            {0, 0, 0, 0, 0.10, 0.90, 0, 0, 0},
            {0, 0, 0, 0, 0, 1, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 1, 0, 0},
            {0.60, 0, 0, 0, 0, 0, 0, 0.10, 0.30},
            {0.70, 0.30, 0, 0, 0, 0, 0, 0, 0},
            {0.20, 0.50, 0, 0.30, 0, 0, 0, 0, 0}};

    /** Mittlere Verweildauer je Lage in Stunden [G]. */
    public static final double[] PATAGONIA_DWELL = {12, 6, 5, 8, 1.5, 5, 4, 3, 9};

    /** Je Bild: Wind (in Schritten, weil der See sein Spektrum bei jeder Änderung neu baut) und Spiegel an den See. */
    public void feed(com.dan.ocean.Ocean o) {
        double ws = Math.min(wind.speed(), 20.7), wd = wind.fromDeg();   // höchstens Bft 8 auf dem See
        if (Math.abs(ws - o.windSpeed) >= 0.5 || (ws < 0.5) != (o.windSpeed < 0.5)) o.windSpeed = Math.round(ws * 2) / 2.0;
        if (Math.abs(((wd - o.windFromDeg) % 360 + 540) % 360 - 180) >= 5) o.windFromDeg = Math.round(wd / 5) * 5.0 % 360;
        o.waterLevel = waterLevel.level();
    }

    /**
     * Phase 11: je Bild das Jahr einen Schritt weiter mit dem Wetter des Augenblicks; der Stau geht an den Seespiegel des
     * Brazo Rico, die Abweichung des Tags an die Regie.
     */
    public Season feedSeason(double sceneDt, double sunAltDeg) {
        java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli((long) Math.floor(clock.epochMillis())).atZone(java.time.ZoneOffset.ofHours(-3));
        java.time.LocalDate d = z.toLocalDate();
        double hour = z.getHour() + z.getMinute() / 60.0 + z.getSecond() / 3600.0;
        jahr.step(sceneDt, d, hour, airTempC, rainRate, sunAltDeg, oktas, wind.speed());
        waterLevel.stau = jahr.stau();
        weather.anomaly = jahr.year.anomaly(d.getYear(), Math.min(365, d.getDayOfYear()));
        return jahr;
    }

    /** Je Bild: Wind, Böigkeit und Jahreszeit an die Halme (mit der Böe des Augenblicks, wenn die Regie läuft). */
    public void feed(com.dan.reed.Reed r, int dayOfYear) {
        r.windSpeed = regie ? gustNow : wind.speed();
        r.windFromDeg = wind.fromDeg();
        r.gust = wind.gustiness() + 0.1;
        r.season = reedSeason(northernDay(dayOfYear));
    }

    /**
     * Der Tag im Jahr für die Packages, die ihr Laubjahr auf der Nordhalbkugel zählen ({@code forest}, {@code ground},
     * {@code reed}): um ein halbes Jahr versetzt. Der 16. Januar (Hochsommer am Gletscher) wird zum 17. Juli.
     */
    public static int northernDay(int dayOfYear) { return (dayOfYear - 1 + 182) % 365 + 1; }

    /** Jahreszeit der Halme 0..1 (0 Sommer, 0,75 Herbst, 1 Winter) aus dem Tag der Nordhalbkugel. */
    static double reedSeason(int d) {
        if (d < 100) return 1 - 0.8 * d / 100.0;                 // Winter → Frühjahr
        if (d < 200) return 0.2 * (1 - (d - 100) / 100.0);       // Frühjahr → Sommer
        if (d < 300) return 0.75 * (d - 200) / 100.0;            // Sommer → Herbst
        return 0.75 + 0.25 * (d - 300) / 65.0;                   // Herbst → Winter
    }

    // ------------------------------------------------------------------ Phase 3

    /** Die Regie einen Schritt weiter (Szenenzeit aus der Uhr, Böen in Echtzeit) und ihre Werte übernehmen. */
    public Weather.Snapshot feedWeather(double realDt) {
        Weather.Snapshot s = weather.update(clock.epochMillis() / 1000.0, realDt);
        apply(s);
        return s;
    }

    /** Handregler: eine Größe der Regie sofort setzen und halten, bis „Regie weiter“. */
    public void hand(Weather.Var v, double value) {
        weather.set(v, value);
        Weather.Snapshot s = weather.snapshot();
        if (s != null) apply(s);
    }

    /** „Regie weiter“: alle Handregler lösen. */
    public void resumeRegie() {
        weather.release();
        rainbowHand = -1;
        lensHand = -1;
    }

    /** Freie Regie ab jetzt, beginnend mit einer Lage. */
    public void startFree(Weather.Phase p) {
        weather.startFree(clock.epochMillis() / 1000.0, p);
        apply(weather.update(clock.epochMillis() / 1000.0, 0));
        lastLensT = Double.NaN;
    }

    private void apply(Weather.Snapshot s) {
        if (s == null) return;
        wind.speed = s.wind();
        wind.fromDeg = s.fromDeg();
        wind.gust = s.gustiness();
        gustNow = s.gustNow();
        oktas = s.oktas();
        highCover = s.high();
        cloudType = CloudType.valueOf(s.cloud().name());
        rainRate = Math.max(0, s.rain());
        visibilityKm = Math.max(0.2, s.visibilityKm());
        fogAmount = s.fog();
        thunderstorm = Lightning.Level.values()[Math.max(0, Math.min(4, s.thunder()))];
        airTempC = s.temperature();
        pressureHPa = s.pressure();
        pressureTendency = s.tendency();
        showerEnd = s.showerEnd();
        // Lentikularis: Ziel aus Lage und Westwind, geglättet über 20 Minuten Szenenzeit
        double t = s.time(), target = lensTarget(s.phase(), wind.speed, wind.fromDeg, oktas);
        if (Double.isNaN(lastLensT) || Math.abs(t - lastLensT) > 6 * 3600) lens = target;
        else lens += (target - lens) * (1 - Math.exp(-Math.abs(t - lastLensT) / 1200));
        lastLensT = t;
    }

    /**
     * Lentikularis im Westwind über den Anden [G]: im Föhnsturm fast immer (0,9), vor einer Front oft (0,45), sonst je
     * stärker der Wind aus West, desto mehr; unter einer geschlossenen tiefen Decke sieht man sie nicht.
     */
    public static double lensTarget(Weather.Phase p, double windMs, double fromDeg, double oktas) {
        double west = Math.max(0, Math.cos(Math.toRadians(fromDeg - 270)));
        double base = smooth(7, 16, windMs) * west * 0.5;
        double ph = p == Weather.Phase.FOEHN ? 0.9 : p == Weather.Phase.AUFZIEHEND ? 0.45 : 0;
        return Math.max(base, ph) * (1 - smooth(6, 8, oktas));
    }

    /** Dunst als Auslöschung je m aus der Sicht (Koschmieder). */
    public double hazePerM() { return 2.996 / (Math.max(0.5, visibilityKm) * 1000); }

    /** Dem Himmel Zeit, Ort, Augenhöhe über dem Meer, Sicht und Bedeckung (je Bild, vor {@code sky.step}). */
    public void feed(Sky sky, double eyeAsl) {
        sky.epochMillis = clock.epochMillis();
        sky.latDeg = clock.latDeg();
        sky.lonDeg = clock.lonDeg();
        sky.eyeHeight = Math.max(1, eyeAsl);
        sky.visibilityKm = visibilityKm;
        sky.groundAlbedo = 0.18;    // See, Wald, Fels, dazu Eis und Schnee [G]
        sky.cloudCover = cloudsDrawn ? 0 : oktas / 8;
        sky.cloudVeil = !cloudsDrawn;
        Sky.State st = sky.state();
        double alt = st != null ? st.sunAlt : 10;
        double low = smooth(1, 6, alt) * (1 - smooth(36, 42, alt));
        sky.rainbow = rainbowHand >= 0 ? rainbowHand : 0.85 * showerEnd * low;
    }

    /** Den Wolken Bedeckung, Art, hohe Schicht, Lentikularis und den Wind in Wolkenhöhe (1,6-fach, 20° rechtsdrehend) [G]. */
    public void feed(Clouds c) {
        c.oktas = oktas;
        c.type = cloudType;
        c.highCover = highCover;
        c.windSpeed = Math.max(3, wind.speed() * 1.6);
        c.windFromDeg = (wind.fromDeg() + 20) % 360;
        c.lens = lensHand >= 0 ? lensHand : lens;
        c.lensFromDeg = wind.fromDeg();
    }

    /** Dem Regen Rate, Wind, Wolkenuntergrenze, Spiegel und Auge; mit gezeichneten Wolken nur unter ihren dichten Stellen. */
    public void feed(Rain r, Clouds c, Clouds.Snapshot cs, boolean cloudsShown, double[] eye) {
        double rate = Math.max(0, rainRate);
        c.precipitation = rate > 0 ? Math.min(1, 0.35 + rate / 25) : 0;
        r.rate = rate;
        r.windSpeed = regie ? gustNow : wind.speed();
        r.windFromDeg = wind.fromDeg();
        r.gust = wind.gustiness();
        r.waterLevel = waterLevel.level();
        r.cloudBase = cs.type().base;
        r.eyeX = eye[0];
        r.eyeY = eye[1];
        r.eyeZ = eye[2];
        if (!cloudsShown || rate <= 0) { r.curtain = null; return; }
        double p = Math.max(1e-3, cs.precipitation());
        r.curtain = (x, z) -> Math.min(1, Clouds.rain(cs, x, z) / p * 2);
    }

    /** Dem Gewitter Stärke, Wolken, Boden und Ohr (je Bild, vor {@code lightning.step}); Einschläge ins Gelände. */
    public void feed(Lightning l, Clouds.Snapshot cs, boolean cloudsShown, double[] eye) {
        if (l.level() != thunderstorm) l.setLevel(thunderstorm);
        l.cx = eye[0];
        l.cz = eye[2];
        l.radius = 16_000;
        l.listenerX = eye[0];
        l.listenerY = eye[1];
        l.listenerZ = eye[2];
        l.groundLevel = waterLevel.level();
        Terrain t = terrain;
        double lv = waterLevel.level();
        l.ground = t == null ? null : (x, z) -> Math.max(lv, t.height(x, z));
        l.temperatureC = airTempC;
        if (cloudsShown && cs != null) {
            double base = cs.type().base, top = base + cs.type().maxThickness;
            l.cloudBase = base;
            l.cells = (x, z) -> Math.max(Clouds.rain(cs, x, z) * 1.5, Clouds.coverage(cs, x, z) * 0.35);
            l.occlusion = (px, py, pz, ex, ey, ez) -> cloudTransmittance(cs, px, py, pz, ex, ey, ez, base, top);
        } else {
            l.cloudBase = 1500;
            l.cells = null;
            l.occlusion = null;
        }
    }

    /** Durchlässigkeit der Wolken zwischen Kanal und Auge (zehn Proben in der Schicht, halbe Auslöschung; wie Haithabu). */
    static double cloudTransmittance(Clouds.Snapshot cs, double px, double py, double pz, double ex, double ey, double ez, double base, double top) {
        double dx = ex - px, dy = ey - py, dz = ez - pz, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1) return 1;
        double t0 = 0, t1 = 1;
        if (Math.abs(dy) > 1e-6) {
            double ta = (base - py) / dy, tb = (top - py) / dy;
            t0 = Math.max(0, Math.min(ta, tb));
            t1 = Math.min(1, Math.max(ta, tb));
        } else if (py < base || py > top) return 1;
        if (t1 <= t0) return 1;
        double tau = 0, step = (t1 - t0) / 10;
        for (int i = 0; i < 10; i++) {
            double t = t0 + (i + 0.5) * step;
            tau += Clouds.density(cs, px + dx * t, py + dy * t, pz + dz * t, false) * step * len;
            if (tau > 20) break;
        }
        return Math.exp(-0.5 * tau);
    }

    /**
     * Dem Seenebel Menge, Sicht, Bedeckung, Höhe und Untergrenze [G]: Nebel n (0..1) gibt die Menge (ab 0,02 bis 0,6
     * voll), die Sicht in der Bank 3 km · (50/3000)^(n^0,6), die Obergrenze 10 + 70 n² m über dem See (die Front ragt
     * mit gut 40 m aus dünnem Nebel heraus). Steigt die Sonne über 2°, hebt er sich; Wind über 5 m/s reißt ihn auf.
     */
    public void feed(Fog f, double sunAltDeg) {
        double n = Math.max(0, Math.min(1, fogAmount));
        double windCut = 1 - smooth(5, 10, wind.speed());
        double lift = smooth(2, 16, sunAltDeg);
        double top = (10 + 70 * n * n) * (1 + 0.6 * lift);
        f.amount = fogOn ? smooth(0.02, 0.6, n) * windCut : 0;
        f.visibilityM = 3000 * Math.pow(50 / 3000.0, Math.pow(n, 0.6));
        f.coverage = Math.min(1, 0.5 + 0.6 * n);
        f.top = top;
        f.base = 0.85 * top * lift;
        f.bankSize = 1500;
        f.wisps = 0.45;
        f.windSpeed = wind.speed();
        f.windFromDeg = wind.fromDeg();
        f.waterLevel = waterLevel.level();
        f.hazeVisibilityM = visibilityKm * 1000;
        fogTop = top;
        fogBase = f.base;
    }

    /** Licht des Himmels als Schnittstelle für andere Packages (Lighting). */
    public static Lighting lighting(Sky sky) {
        return new Lighting() {
            Sky.State st() { Sky.State s = sky.state(); return s != null ? s : sky.update(); }
            @Override public double[] sunDir() { return st().sunDir.clone(); }
            @Override public double[] moonDir() { return st().moonDir.clone(); }
            @Override public double[] sunColor() { return st().sunColor.clone(); }
            @Override public double[] moonColor() { return st().moonColor.clone(); }
            @Override public double[] skyAmbient() { return st().skyIrradiance.clone(); }
            @Override public double haze() { return st().groundExtinction; }
        };
    }

    /** „Wind Bft 7 aus 285° (15,6 m/s, Böe 19,2) · freie Regie, Föhnsturm“ */
    public String weatherLine() {
        Weather.Snapshot s = weather.snapshot();
        String src = !regie || s == null ? "von Hand" : (s.scenario().isEmpty() ? "freie Regie, " + s.phase().label : s.scenario() + ": " + s.stepText())
                + (s != null && regie && weather.overrides() > 0 ? ", " + weather.overrides() + " von Hand" : "");
        return String.format(Locale.GERMAN, "Wind Bft %d aus %03.0f° (%.1f m/s, Böe %.1f) · %s", wind.beaufort(), wind.fromDeg(), wind.speed(),
                regie ? gustNow : wind.speed(), src);
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Eine Verbindung eintragen (das Bedienfeld zeigt die Liste). */
    public void link(String from, String to, String via, int phase) { links.add(new Link(from, to, via, phase)); }

    public List<Link> links() { return Collections.unmodifiableList(links); }

    /** Was nach dem Phasenplan noch kommt (ohne das, was schon verbunden ist). */
    public static List<Link> planned() { return PLANNED; }

    /** Die Schnittstellen des Pakets, wie sie im Werkbuch stehen (Prüfung). */
    public static final String[] INTERFACES = {"Clock", "Wind", "WaterLevel", "Terrain", "Lighting", "Flash", "Sea", "Cells", "Impact", "Crack",
            "Collider", "SurfaceHit", "Runup", "Season", "SoundEvent"};

    /** „12 Verbindungen, 12 geplant“ */
    public String summary() {
        return String.format(Locale.GERMAN, "%d %s, %d geplant", links.size(), links.size() == 1 ? "Verbindung" : "Verbindungen", PLANNED.size());
    }
}
