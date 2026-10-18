package com.dan.glacier.data;

import com.dan.glacier.camera.Ansichten;
import com.dan.glacier.scene.Gletscher;
import com.dan.glacier.scene.Regie;
import com.dan.glacier.scene.Terrain;
import com.dan.glacier.wiring.Wiring;
import com.dan.store.Felder;
import com.dan.store.Zahl;
import com.dan.weather.Weather;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die mitgelieferten Daten (Phase 15, Werkbuch Abschnitt 8): Quellen, Befunde und die belegten Rupturas aus
 * {@code quellen.txt}, {@code befunde.txt} und {@code ruptura.txt} in diesem Package, Wetterlagen, Regiebücher, Ansichten,
 * Fahrten und die Presets der Packages aus dem Code, dazu das Gelände und die Fronten (Front des Geländes und zwölf
 * Monatsfronten des Jahresgangs) aus der Szene. Daraus schreibt {@link StartdatenSql} das Skript
 * {@code db/sql/04_startdaten.sql}; ohne Datenbank liest {@link DateiSpeicher} dieselben Listen.
 */
public final class Startdaten {
    private Startdaten() { }

    // ------------------------------------------------------------------ Textdateien im Package

    static List<String[]> zeilen(String datei) {
        try (InputStream in = Startdaten.class.getResourceAsStream(datei)) {
            if (in == null) throw new IllegalStateException(datei + " fehlt im Package com.dan.glacier.data");
            List<String[]> l = new ArrayList<>();
            for (String z : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (z.isBlank() || z.startsWith("#")) continue;
                String[] p = z.split("\\|", -1);
                for (int i = 0; i < p.length; i++) p[i] = p[i].strip();
                l.add(p);
            }
            return l;
        } catch (IOException e) {
            throw new IllegalStateException(datei + " nicht lesbar: " + e.getMessage(), e);
        }
    }

    private static List<String> liste(String s) {
        List<String> l = new ArrayList<>();
        for (String x : s.split(",")) if (!x.isBlank()) l.add(x.strip());
        return l;
    }

    private static Double zahl(String s) { return s == null || s.isBlank() ? null : Zahl.lies(s); }

    public static List<Daten.Quelle> quellen() {
        List<Daten.Quelle> l = new ArrayList<>();
        for (String[] p : zeilen("quellen.txt"))
            l.add(new Daten.Quelle(p[0], p[3], p[4].isEmpty() ? null : p[4], p[1], p[2].isEmpty() ? null : LocalDate.parse(p[2]), p[5].isEmpty() ? null : p[5]));
        return l;
    }

    public static List<Daten.Befund> befunde() {
        List<Daten.Befund> l = new ArrayList<>();
        for (String[] p : zeilen("befunde.txt")) l.add(new Daten.Befund(p[0], p[1], p[2], p[3], liste(p.length > 4 ? p[4] : "")));
        return l;
    }

    /** Die belegten Rupturas 1917–2019 (F29 A), recherchiert am 18.10.2026. */
    public static List<Daten.Ruptura> rupturen() {
        List<Daten.Ruptura> l = new ArrayList<>();
        for (String[] p : zeilen("ruptura.txt")) {
            String d = p[2];
            String gen = d.length() == 10 ? "TAG" : d.length() == 7 ? "MONAT" : "JAHR";
            LocalDate datum = gen.equals("TAG") ? LocalDate.parse(d) : gen.equals("MONAT") ? LocalDate.parse(d + "-01") : LocalDate.of(Integer.parseInt(d), 1, 1);
            l.add(new Daten.Ruptura(Integer.parseInt(p[0]), Integer.parseInt(p[1]), datum, gen, zahl(p[3]), zahl(p[4]), zahl(p[5]), p[6].isEmpty() ? null : p[6], liste(p[7])));
        }
        return l;
    }

    // ------------------------------------------------------------------ Wetterlagen

    public static List<Daten.Wetterlage> wetterlagen() {
        List<Daten.Wetterlage> l = new ArrayList<>();
        Map<Weather.Phase, String[]> t = new LinkedHashMap<>();
        t.put(Weather.Phase.KLAR, new String[]{"Klar", "1-12", "Hoch über dem Lago Argentino, leichter Westwind vom Eisfeld, Schönwetterwolken"});
        t.put(Weather.Phase.AUFZIEHEND, new String[]{"Aufziehend", "1-12", "Ein Tief über dem Pazifik: Cirren über dem Eisfeld, der Wind frischt auf"});
        t.put(Weather.Phase.FRONT, new String[]{"Front", "1-12", "Die Front über den Anden: geschlossene Wolken, Regen an der Front, Schnee auf der Zunge"});
        t.put(Weather.Phase.STURM, new String[]{"Sturm", "1-12", "Westwindsturm vom Eisfeld mit Schauern"});
        t.put(Weather.Phase.GEWITTER, new String[]{"Gewitter", "12-2", "Seltenes Sommergewitter über den Bergen"});
        t.put(Weather.Phase.ABKLINGEND, new String[]{"Abklingend", "1-12", "Der Sturm flaut ab, Schauer, Lücken"});
        t.put(Weather.Phase.AUFKLAREND, new String[]{"Aufklarend", "1-12", "Rückseite: klare Andenluft, weite Sicht"});
        t.put(Weather.Phase.NEBEL, new String[]{"Seenebel", "1-12", "Windstill am Morgen, Nebel liegt über dem See vor der Front"});
        t.put(Weather.Phase.FOEHN, new String[]{"Föhnsturm", "9-3", "Stürmischer, trockener, warmer Fallwind vom Eisfeld, Lentikularis über den Gipfeln, große Sicht"});
        for (Map.Entry<Weather.Phase, String[]> e : t.entrySet()) {
            double[] w = Weather.target(e.getKey());
            w[Weather.Var.SWELL.ordinal()] = Double.NaN;
            l.add(new Daten.Wetterlage(e.getValue()[0], (double) Weather.beaufort(w[0]), e.getKey().name(), w, e.getValue()[1], e.getValue()[2], Daten.MITGELIEFERT));
        }
        double n = Double.NaN;
        //                                                                         Wind Richt Böig Bed hoch Wolke Regen Sicht Nebel Druck Temp Feuchte Dünung Gewitter
        l.add(new Daten.Wetterlage("Sommertag an der Front", 3.0, "KLAR", new double[]{6, 275, 0.3, 2, 0.2, 0, 0, 60, 0, 1012, 16, 55, n, 0}, "12-2",
                "Wie der Mittag von „Ein Sommertag an der Front“: Westwind, klar, 16 °C [G]", Daten.MITGELIEFERT));
        l.add(new Daten.Wetterlage("Winternacht klar", 1.0, "KLAR", new double[]{1.2, 250, 0.1, 0, 0, n, 0, 80, 0, 1022, -4, 70, n, 0}, "6-8",
                "Wie „Südhimmel“: windstill, wolkenlos, Frost [G]", Daten.MITGELIEFERT));
        l.add(new Daten.Wetterlage("Ruptura im März", 2.0, "KLAR", new double[]{3, 270, 0.2, 3, 0.3, 0, 0, 50, 0, 1010, 12, 60, n, 0}, "3",
                "Spätsommer wie beim Einsturz der Brücke 2016: leichter Wind, aufgelockert [G]", Daten.MITGELIEFERT));
        return l;
    }

    // ------------------------------------------------------------------ Regiebücher

    /** Kopfwerte eines Regiebuchs für GLC_REGIEBUCH: Szenenzeit und Zeitraffer aus den ersten Stichworten, Fotos, Blatt. */
    public record Kopf(LocalDateTime szenenzeit, Double raffer, int fotos, boolean blatt) {
        public static Kopf aus(Regie.Book b) {
            LocalDateTime t = null;
            Double r = null;
            int fotos = 0;
            boolean blatt = false;
            for (Regie.Zeile z : b.zeilen()) {
                switch (z.befehl()) {
                    case "zeit" -> {
                        if (t != null) break;
                        try { t = LocalDateTime.parse(z.argumente().strip(), DateTimeFormatter.ofPattern("d.M.yyyy H:mm")); } catch (RuntimeException e) { /* bleibt leer */ }
                    }
                    case "raffer" -> { if (r == null) r = zahl(z.argumente()); }
                    case "foto" -> fotos++;
                    case "blatt" -> blatt = true;
                    default -> { }
                }
            }
            return new Kopf(t, r, fotos, blatt);
        }
    }

    public static List<Daten.Regiebuch> regiebuecher() {
        List<Daten.Regiebuch> l = new ArrayList<>();
        for (String d : Regie.DATEIEN) {
            String t = Regie.text(d);
            Regie.Book b = Regie.parse(t);
            l.add(new Daten.Regiebuch(b.name(), t, b.about(), d, Daten.MITGELIEFERT));
        }
        return l;
    }

    // ------------------------------------------------------------------ Ansichten und Fahrten

    /** Die acht festen Ansichten (Tasten 1–8) aus {@link Ansichten#ALLE}. Kamera: x, Augenhöhe NaN (aus dem Gelände), z, Blick, Neigung. */
    public static List<Daten.Ansicht> ansichten() {
        List<Daten.Ansicht> l = new ArrayList<>();
        for (Ansichten.Ansicht a : Ansichten.ALLE)
            l.add(new Daten.Ansicht(0, a.name(), a.key(), "FEST", new double[]{a.x(), Double.NaN, a.z(), a.heading(), a.pitch(), com.dan.glacier.camera.Camera.FOVY},
                    a.eyeAboveGround(), a.eyeAboveGround() < 0, null, null, null, Daten.MITGELIEFERT));
        return l;
    }

    /** Die drei Fahrten; ihre Wege rechnet die App aus der Szene (Zunge, gesuchte Spalte, Route des Katamarans). */
    public static List<Daten.Fahrt> fahrten() {
        return List.of(
                new Daten.Fahrt("Über die Zunge", null, "ZUNGE", false, null, "Vom Eisfall 8 km die Zunge hinab, 40 m über dem Eis, bis über das Wasser vor der Front",
                        Daten.MITGELIEFERT, List.of()),
                new Daten.Fahrt("Durch die Spalte", null, "SPALTE", false, null, "Hinab in die längste fast gerade Spalte nahe der Front, ihr entlang in halber Tiefe, "
                        + "wieder hinauf; die App sucht sie im Gletscher (Phase 14)", Daten.MITGELIEFERT, List.of()),
                new Daten.Fahrt("Bootsfahrt", null, "MITFAHRT", false, null, "An Bord des Katamarans entlang der Front in 300 m Abstand", Daten.MITGELIEFERT, List.of()));
    }

    // ------------------------------------------------------------------ Presets

    /** Die Klassen, deren Presets mitkommen: jede öffentliche statische Methode ohne Argument, die die Klasse liefert. */
    public static final String[] PAKETE = {"com.dan.ocean.Ocean", "com.dan.reed.Reed", "com.dan.cloud.Clouds", "com.dan.rain.Rain", "com.dan.fog.Fog",
            "com.dan.lightning.Lightning", "com.dan.weathering.Weathering", "com.dan.buoyancy.Buoyancy", "com.dan.flock.Flock", "com.dan.soundscape.Soundscape",
            "com.dan.spray.Spray", "com.dan.ice.Ice", "com.dan.fracture.Fracture", "com.dan.wave.Wave"};
    private static List<Daten.PaketPreset> presets;

    public static synchronized List<Daten.PaketPreset> presets() {
        if (presets != null) return presets;
        List<Daten.PaketPreset> l = new ArrayList<>();
        for (String k : PAKETE) {
            try {
                Class<?> c = Class.forName(k);
                List<Method> ms = new ArrayList<>();
                for (Method m : c.getMethods())
                    if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0 && m.getReturnType() == c) ms.add(m);
                ms.sort(Comparator.comparing(Method::getName));
                for (Method m : ms) {
                    Object o = m.invoke(null);
                    l.add(new Daten.PaketPreset(c.getPackageName(), c.getSimpleName(), m.getName(), "Preset " + m.getName() + "() aus " + c.getPackageName(),
                            Daten.MITGELIEFERT, Felder.lesen(o)));
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Preset von " + k + " nicht lesbar: " + e, e);
            }
        }
        presets = Collections.unmodifiableList(l);
        return presets;
    }

    /** Die Typen der Felder eines Presets (DOUBLE, INT, BOOLEAN, STRING, ENUM) aus seiner Klasse. */
    public static Map<String, String> typen(Daten.PaketPreset p) {
        try {
            return Felder.typen(Class.forName(p.paket() + "." + p.klasse()));
        } catch (ClassNotFoundException e) {
            return Map.of();
        }
    }

    // ------------------------------------------------------------------ aus der Szene

    /** Was aus der Szene gerechnet wird: das Gelände und die mitgelieferten Fronten. */
    public record Szene(Daten.Gelaende gelaende, List<Daten.Front> fronten) { }

    /** Zeitraum der Aufnahme des Geländes: TanDEM-X 2011–2015 (Copernicus GLO-30). */
    public static final LocalDate DGM_VON = LocalDate.of(2011, 1, 1), DGM_BIS = LocalDate.of(2015, 12, 31);

    /** Gelände und Fronten aus dem geladenen Gelände und einem dafür gebauten Gletscher (einige Sekunden). */
    public static Szene szene(Terrain t, File dir) {
        Gletscher g = new Gletscher(t);
        return new Szene(gelaende(t, dir), fronten(g.glacier));
    }

    /** Das Gelände; die Prüfsummen aus den Dateien in dir (daten/gelaende). */
    public static Daten.Gelaende gelaende(Terrain t, File dir) {
        double x1 = Terrain.X0 + Terrain.N * Terrain.CELL, z1 = Terrain.Z0 + Terrain.N * Terrain.CELL;
        Daten.Geom aus = Daten.Geom.flaeche(new double[][]{{Terrain.X0, Terrain.Z0}, {x1, Terrain.Z0}, {x1, z1}, {Terrain.X0, z1}});
        Map<String, String[]> dateien = new LinkedHashMap<>();
        for (String[] f : new String[][]{{"HOEHE", Terrain.HOEHE}, {"GRUND", Terrain.GRUND}, {"HERKUNFT", Terrain.HERKUNFT}, {"FRONT", Terrain.FRONT_CSV}})
            dateien.put(f[0], new String[]{f[1], sha256(new File(dir, f[1]))});
        return new Daten.Gelaende("Perito Moreno, GLO-30", aus, com.dan.glacier.scene.Site.LON, com.dan.glacier.scene.Site.LAT, 0, Terrain.CELL, Terrain.N, t.lakeAsl,
                DGM_VON, DGM_BIS, dateien, "GLO30", "Ausschnitt 12,8 × 12,8 km um die Spitze der Halbinsel Magallanes; Szenenmeter x nach Osten, z nach Süden, "
                + "Ursprung an der Spitze; Grund unter Wasser und Eis modelliert [G]");
    }

    /** SHA-256 einer Datei (hex) oder null, wenn sie fehlt. */
    public static String sha256(File f) {
        if (!f.isFile()) return null;
        try (InputStream in = new java.io.FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            StringBuilder b = new StringBuilder();
            for (byte x : md.digest()) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Die Monate der Modellfronten: je der 15., Jahr der Szene. */
    public static final int MODELL_JAHR = 2026;
    static final String[] MONATE = {"Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember"};

    /**
     * Die Front des Geländes (Vorstoß 0) und die zwölf Monatsfronten des Jahresgangs (Vorstoß nach
     * {@link Wiring#glacierAdvance}, je am 15.). Gerechnet aus den Frontlinien und ihren Normalen, ohne den Gletscher zu
     * verstellen (die Szene darf dabei laufen).
     */
    public static List<Daten.Front> fronten(com.dan.ice.Glacier g) {
        List<Daten.Front> l = new ArrayList<>();
        double jetzt = g.advance();
        Daten.Geom basis = linie(g, -jetzt);
        double top = Math.round(g.frontTop() * 100) / 100.0;
        l.add(new Daten.Front(0, DGM_VON.atStartOfDay(), DGM_BIS.atTime(23, 59), Daten.F_GELAENDE, "Front des Geländes (GLO-30)", basis, basis.laenge(), 0, 0.0,
                top, "GLO30", "Aus der Grenze Eis/See im GLO-30 (TanDEM-X 2011–2015); ohne Tagesdatum", Daten.MITGELIEFERT));
        for (int m = 1; m <= 12; m++) {
            LocalDate d = LocalDate.of(MODELL_JAHR, m, 15);
            double a = Wiring.glacierAdvance(d.getDayOfYear());
            Daten.Geom f = linie(g, a - jetzt);
            l.add(new Daten.Front(0, d.atTime(12, 0), null, Daten.F_MODELL, MONATE[m - 1] + " (Modell)", f, f.laenge(), Math.round(a * 100) / 100.0,
                    Math.round(a * basis.laenge()) * 1.0, top, null,
                    String.format(Locale.GERMAN, "Jahresgang ±40 m [G], Vorstoß %+.1f m am 15. %s", a, MONATE[m - 1]), Daten.MITGELIEFERT));
        }
        return l;
    }

    /** Die Frontlinien des Gletschers jetzt als Multilinie, vereinfacht auf 5 m (F26 A). */
    public static Daten.Geom linie(com.dan.ice.Glacier g) { return linie(g, 0); }

    /** Die Frontlinien, um shift Meter längs ihrer Normalen verschoben (positiv: zum Wasser), vereinfacht auf 5 m. */
    public static Daten.Geom linie(com.dan.ice.Glacier g, double shift) {
        List<double[]> lines = g.frontLines(), norms = g.frontNormals();
        List<double[][]> teile = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            double[] p = lines.get(i), n = i < norms.size() ? norms.get(i) : null;
            if (p.length < 4) continue;
            double[][] q = new double[p.length / 2][];
            for (int k = 0; k < q.length; k++) {
                double nx = n != null && 2 * k + 1 < n.length ? n[2 * k] : 0, nz = n != null && 2 * k + 1 < n.length ? n[2 * k + 1] : 0;
                q[k] = new double[]{p[2 * k] + nx * shift, p[2 * k + 1] + nz * shift};
            }
            teile.add(vereinfachen(q, 5));
        }
        return Daten.Geom.multilinie(teile);
    }

    /** Douglas-Peucker mit Toleranz tol (m). */
    public static double[][] vereinfachen(double[][] p, double tol) {
        if (p.length < 3) return p;
        boolean[] keep = new boolean[p.length];
        keep[0] = keep[p.length - 1] = true;
        dp(p, 0, p.length - 1, tol, keep);
        List<double[]> r = new ArrayList<>();
        for (int i = 0; i < p.length; i++) if (keep[i]) r.add(p[i]);
        return r.toArray(new double[0][]);
    }

    private static void dp(double[][] p, int a, int b, double tol, boolean[] keep) {
        if (b <= a + 1) return;
        double ax = p[a][0], az = p[a][1], dx = p[b][0] - ax, dz = p[b][1] - az, len = Math.hypot(dx, dz), best = -1;
        int bi = -1;
        for (int i = a + 1; i < b; i++) {
            double d = len < 1e-9 ? Math.hypot(p[i][0] - ax, p[i][1] - az) : Math.abs((p[i][0] - ax) * dz - (p[i][1] - az) * dx) / len;
            if (d > best) { best = d; bi = i; }
        }
        if (best > tol) {
            keep[bi] = true;
            dp(p, a, bi, tol, keep);
            dp(p, bi, b, tol, keep);
        }
    }

    /** Für Prüfungen: alle Zahlen auf einen Blick. */
    public static String zusammenfassung(Szene sz) {
        return String.format(Locale.GERMAN, "%d Quellen, %d Befunde, %d Rupturas, %d Fronten, %d Wetterlagen, %d Regiebücher, %d Ansichten, %d Fahrten, %d Presets",
                quellen().size(), befunde().size(), rupturen().size(), sz == null ? 0 : sz.fronten().size(), wetterlagen().size(), regiebuecher().size(),
                ansichten().size(), fahrten().size(), presets().size());
    }
}
