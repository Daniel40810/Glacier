package com.dan.glacier.data;

import com.dan.store.Db;
import com.dan.store.Einrichter;
import com.dan.weather.Weather;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Das Schema GLACIER (F20 A, Phase 15): Ort von {@code db/}, Zugang aus {@code db/db.properties} und der Einrichter mit
 * allem, was er für Glacier prüft. Schema und Benutzer legt {@code db/USER.sql} an (Glacier auf PDBORCL).
 */
public final class Verbindung {
    private Verbindung() { }

    public static final String PRAEFIX = "GLC_";
    public static final String ENV = "GLACIER_DB_PASSWORD";
    /** Die Ausbaustufe dieser App (GLC_SCHEMA.VERSION). */
    public static final int VERSION = 1;
    public static final String[] SICHTEN = {"GLC_FRONT_V", "GLC_KALBUNG_V", "GLC_KALBUNG_TAG_V", "GLC_KALBUNG_MONAT_V", "GLC_EISBERG_V", "GLC_TAGESKARTE_V",
            "GLC_RUPTURA_V", "GLC_BEFUND_V", "GLC_LETZTE_SITZUNG_V"};
    public static final String[] METADATEN = {"GLC_GELAENDE.AUSSCHNITT", "GLC_FRONT.LINIE", "GLC_KALBUNG.LAGE", "GLC_KALBUNG.RISS", "GLC_EISBERG.LAGE",
            "GLC_EISBERG.WEG", "GLC_RUPTURA_LAUF.DAMM", "GLC_RUPTURA_LAUF.RINNE_LINIE", "GLC_ANSICHT.LAGE"};

    /** Die Wetterspalten von GLC_WETTERLAGE in der Reihenfolge von {@code Weather.Var} (die Dünung hat keine). */
    public static final Map<Weather.Var, String> WETTER = new LinkedHashMap<>();
    static {
        WETTER.put(Weather.Var.WIND, "WIND_MS");
        WETTER.put(Weather.Var.DIRECTION, "RICHTUNG_GRAD");
        WETTER.put(Weather.Var.GUST, "BOEIGKEIT");
        WETTER.put(Weather.Var.COVER, "BEDECKUNG_ACHTEL");
        WETTER.put(Weather.Var.HIGH, "HOHE_SCHICHT");
        WETTER.put(Weather.Var.CLOUD, "WOLKENART");
        WETTER.put(Weather.Var.RAIN, "REGEN_MMH");
        WETTER.put(Weather.Var.VISIBILITY, "SICHT_KM");
        WETTER.put(Weather.Var.FOG, "NEBEL");
        WETTER.put(Weather.Var.PRESSURE, "LUFTDRUCK_HPA");
        WETTER.put(Weather.Var.TEMPERATURE, "TEMPERATUR_C");
        WETTER.put(Weather.Var.HUMIDITY, "FEUCHTE_PROZ");
        WETTER.put(Weather.Var.THUNDER, "GEWITTER");
    }

    /** Der Ordner db: -Dglacier.db.dir, sonst ./db, sonst db neben dem Jar. */
    public static File ordner() { return Db.ordner("glacier.db.dir", Verbindung.class); }

    public static File skripte() { return new File(ordner(), "sql"); }

    /**
     * Der Zugang aus db/db.properties. Fehlt die Datei, wird sie aus der Vorlage db.properties.example angelegt (ohne
     * Passwort); das Startbild fragt dann danach und trägt es nach dem Verbinden ein.
     */
    public static Db.Konfig konfig() throws IOException {
        File f = new File(ordner(), "db.properties"), vorlage = new File(ordner(), "db.properties.example");
        if (!f.isFile() && vorlage.isFile()) Files.copy(vorlage.toPath(), f.toPath());
        return Db.konfig(f, "Glacier", ENV);
    }

    /** Die räumlichen Indizes (je Geometriespalte einer: GLC_KALBUNG_LAGE_SX …). */
    public static String[] raeumlich() {
        String[] r = new String[METADATEN.length];
        for (int i = 0; i < r.length; i++) r[i] = METADATEN[i].replace('.', '_') + "_SX";
        return r;
    }

    /** Die Zahlen der Startdaten aus der Zeile {@code -- ZAHLEN: Art=n; …} von 04_startdaten.sql. */
    public static Map<String, Integer> zahlen(File dir) {
        Map<String, Integer> m = new LinkedHashMap<>();
        try {
            for (String z : Files.readAllLines(new File(dir, "04_startdaten.sql").toPath(), StandardCharsets.UTF_8)) {
                if (!z.startsWith("-- ZAHLEN:")) continue;
                for (String p : z.substring(10).split(";")) {
                    String[] kv = p.strip().split("=");
                    if (kv.length == 2) m.put(kv[0].strip(), Integer.parseInt(kv[1].strip()));
                }
                break;
            }
        } catch (IOException | RuntimeException e) { /* dann prüft der Einrichter ohne Zahlen */ }
        return m;
    }

    /** Die SQL-Abfrage je Zahl der Startdaten. */
    static final Map<String, String> ZAEHLEN = new LinkedHashMap<>();
    static {
        String m = " WHERE HERKUNFT = 'MITGELIEFERT'";
        ZAEHLEN.put("Quellen", "SELECT COUNT(*) FROM GLC_QUELLE" + m);
        ZAEHLEN.put("Befunde", "SELECT COUNT(*) FROM GLC_BEFUND" + m);
        ZAEHLEN.put("Belege", "SELECT COUNT(*) FROM GLC_BEFUND_QUELLE x JOIN GLC_BEFUND b ON b.ID = x.BEFUND_ID WHERE b.HERKUNFT = 'MITGELIEFERT'");
        ZAEHLEN.put("Gelände", "SELECT COUNT(*) FROM GLC_GELAENDE" + m);
        ZAEHLEN.put("Fronten", "SELECT COUNT(*) FROM GLC_FRONT" + m);
        ZAEHLEN.put("Rupturas", "SELECT COUNT(*) FROM GLC_RUPTURA" + m);
        ZAEHLEN.put("Wetterlagen", "SELECT COUNT(*) FROM GLC_WETTERLAGE" + m);
        ZAEHLEN.put("Regiebücher", "SELECT COUNT(*) FROM GLC_REGIEBUCH" + m);
        ZAEHLEN.put("Stichworte", "SELECT COUNT(*) FROM GLC_STICHWORT x JOIN GLC_REGIEBUCH r ON r.ID = x.REGIEBUCH_ID WHERE r.HERKUNFT = 'MITGELIEFERT'");
        ZAEHLEN.put("Ansichten", "SELECT COUNT(*) FROM GLC_ANSICHT" + m + " AND ART = 'FEST'");
        ZAEHLEN.put("Fahrten", "SELECT COUNT(*) FROM GLC_FAHRT" + m);
        ZAEHLEN.put("Presets", "SELECT COUNT(*) FROM GLC_PAKET_PRESET" + m);
        ZAEHLEN.put("Presetwerte", "SELECT COUNT(*) FROM GLC_PAKET_WERT x JOIN GLC_PAKET_PRESET p ON p.ID = x.PAKET_PRESET_ID WHERE p.HERKUNFT = 'MITGELIEFERT'");
    }

    /** Der Einrichter für das Schema GLACIER. */
    public static Einrichter einrichter() {
        Einrichter e = new Einrichter();
        e.praefix = PRAEFIX;
        e.version = VERSION;
        e.sichten = SICHTEN.clone();
        e.metadaten = METADATEN.clone();
        e.raeumlich = raeumlich();
        e.startdaten = () -> {
            List<Einrichter.Soll> l = new ArrayList<>();
            Map<String, Integer> z = zahlen(skripte());
            for (Map.Entry<String, String> q : ZAEHLEN.entrySet()) if (z.containsKey(q.getKey())) l.add(new Einrichter.Soll(q.getKey(), q.getValue(), z.get(q.getKey())));
            return l;
        };
        e.rechnet = () -> List.of(
                new Einrichter.Soll("Front des Geländes (km auf dem Ellipsoid, Szene 5,2)", "SELECT TO_CHAR(MAX(LAENGE_M) / 1000, 'FM990.00') FROM GLC_FRONT_V WHERE ART = 'GELAENDE'", -1),
                new Einrichter.Soll("Ausschnitt des Geländes (km², Szene 12,8 × 12,8 = 163,8)",
                        "SELECT TO_CHAR(MDSYS.SDO_GEOM.SDO_AREA(AUSSCHNITT, 0.05, 'unit=SQ_KM'), 'FM9990.0') FROM GLC_GELAENDE WHERE ROWNUM = 1", -1),
                new Einrichter.Soll("Vorstoß der Monatsfronten (m, Dezember gegen Juni)",
                        "SELECT TO_CHAR(MAX(VORSTOSS_M) - MIN(VORSTOSS_M), 'FM990.0') FROM GLC_FRONT WHERE ART = 'MODELL'", -1),
                new Einrichter.Soll("Rupturas belegt (Jahre)", "SELECT TO_CHAR(COUNT(DISTINCT JAHR)) || ', zuletzt ' || TO_CHAR(MAX(DATUM), 'DD.MM.YYYY') FROM GLC_RUPTURA", -1),
                new Einrichter.Soll("Chronik: Kalbungen, Eisberge, Läufe der Ruptura",
                        "SELECT (SELECT COUNT(*) FROM GLC_KALBUNG) || ', ' || (SELECT COUNT(*) FROM GLC_EISBERG) || ', ' || (SELECT COUNT(*) FROM GLC_RUPTURA_LAUF) FROM DUAL", -1),
                new Einrichter.Soll("Tageskarte: Objekte in GLC_TAGESKARTE_V", "SELECT TO_CHAR(COUNT(*)) FROM GLC_TAGESKARTE_V", -1));
        return e;
    }
}
