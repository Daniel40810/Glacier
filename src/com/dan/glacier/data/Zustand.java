package com.dan.glacier.data;

import com.dan.glacier.camera.Camera;
import com.dan.glacier.gl.Quality;
import com.dan.glacier.gl.Renderer;
import com.dan.glacier.scene.Regie;
import com.dan.glacier.scene.Ruptura;
import com.dan.store.Felder;
import com.dan.store.Zahl;
import com.dan.weather.Scenario;
import com.dan.weather.Weather;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Der ganze Zustand der Szene als Text (F31 A): alle öffentlichen Felder der Packages, dazu Uhr, Kamera, Wetterregie
 * mit Handreglern, das laufende Regiebuch mit Stelle, das Jahr (Damm ja oder nein), Vorstoß und Kerbe der Front und der
 * Stand der Ruptura. Die offenen Eisberge kommen nicht aus diesem Text, sondern aus der Chronik
 * ({@link Speicher#offeneEisberge}); was gerade fällt, kommt nicht zurück. Dasselbe Format dient für gemerkte Ansichten
 * und die Dateien ohne Datenbank.
 * <p>
 * Schlüssel der Form {@code teil.feld}: {@code ocean.windSpeed}, {@code kamera.x}, {@code regie.hand.WIND}. Die
 * Szenenzeit steht als {@code uhr.datum} (ISO) und {@code uhr.stunde}.
 */
public final class Zustand {
    private Zustand() { }

    static final Set<String> OHNE = Set.of("quality");
    /** Anfragen, Zustände des Laufs, abgeleitete Größen. */
    static final Set<String> OHNE_RENDERER = Set.of("photoRequest", "frozen", "quality", "waveReady", "chronikAn");
    static final Set<String> OHNE_WIRING = Set.of("cloudsDrawn", "fogBase", "fogTop", "gustNow", "lens", "showerEnd", "pressureTendency", "quality");
    static final Set<String> OHNE_SKY = Set.of("epochMillis", "latDeg", "lonDeg", "quality");
    static final Set<String> OHNE_KALBUNG = Set.of("note", "focusX", "focusZ");
    static final Set<String> OHNE_KLANG = Set.of("summer");

    /** Die Teile mit öffentlichen Feldern: Name im Zustand → Objekt. */
    static Map<String, Object> teile(Renderer r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("renderer", r);
        if (r.wiring != null) {
            m.put("wiring", r.wiring);
            m.put("wind", r.wiring.wind);
            m.put("pegel", r.wiring.waterLevel);
            m.put("jahr", r.wiring.jahr);
        }
        m.put("ocean", r.ocean);
        m.put("reed", r.reed);
        m.put("sky", r.sky);
        m.put("clouds", r.clouds);
        m.put("rain", r.rain);
        m.put("fog", r.fog);
        m.put("lightning", r.lightning);
        m.put("spray", r.spray);
        m.put("wave", r.wave);
        m.put("fracture", r.bruch);
        m.put("eisberge", r.bergs);
        m.put("eis", r.bergIce);
        m.put("klang", r.klang);
        m.put("soundscape", r.klang.s);
        if (r.kalbung != null) m.put("kalbung", r.kalbung);
        try {
            if (r.voegel() != null) m.put("voegel", r.voegel());
            if (r.stegeNass() != null) m.put("stege", r.stegeNass());
        } catch (RuntimeException e) { /* Leben steht noch nicht */ }
        return m;
    }

    /** Das laufende Objekt einer Klasse mit Presets (Startdaten.PAKETE, einfacher Name) in der Szene, oder null. */
    public static Object objekt(Renderer r, String klasse) {
        for (Object o : teile(r).values()) if (o != null && o.getClass().getSimpleName().equals(klasse)) return o;
        return null;
    }

    private static Set<String> ohne(String teil) {
        return switch (teil) {
            case "renderer" -> OHNE_RENDERER;
            case "wiring" -> OHNE_WIRING;
            case "sky" -> OHNE_SKY;
            case "kalbung" -> OHNE_KALBUNG;
            case "klang" -> OHNE_KLANG;
            default -> OHNE;
        };
    }

    /** Erfasst den Zustand (aus dem Swing-Faden oder dem Faden der Daten; die Felder sind volatile). */
    public static LinkedHashMap<String, String> erfassen(Renderer r, boolean tonAn) {
        LinkedHashMap<String, String> z = new LinkedHashMap<>();
        z.put("app.version", Renderer.VERSION);
        z.put("app.qualitaet", r.quality().name());
        z.put("app.ton", Boolean.toString(tonAn));
        z.put("uhr.datum", r.clock.date().toString());
        z.put("uhr.stunde", Zahl.text(Math.round(r.clock.hour() * 1e6) / 1e6));
        z.put("uhr.lapse", Zahl.text(r.clock.lapse));
        Camera c = r.camera;
        z.put("kamera.modus", c.mode == Camera.Mode.KREISEN ? "KREISEN" : "FREI");
        z.put("kamera.kreisen", Boolean.toString(c.autoOrbit));
        z.put("kamera.tauchen", Boolean.toString(c.diving));
        double[] k = kamera(r);
        String[] n = {"x", "y", "z", "gier", "nick", "fov"};
        for (int i = 0; i < 6; i++) z.put("kamera." + n[i], Zahl.text(Math.round(k[i] * 1000) / 1000.0));
        z.put("kamera.ziel", Zahl.text(c.tx) + " " + Zahl.text(c.ty) + " " + Zahl.text(c.tz) + " " + Zahl.text(c.yaw) + " " + Zahl.text(c.pitch) + " " + Zahl.text(c.dist));
        if (r.wiring != null) {
            Weather w = r.wiring.weather;
            Weather.Snapshot ws = w.snapshot();
            z.put("regie.an", Boolean.toString(r.wiring.regie));
            z.put("regie.lage", ws != null ? ws.phase().name() : "KLAR");
            Scenario sc = w.scenario();
            if (sc != null && ws != null && ws.step() >= 0) {
                z.put("regie.text", sc.toText());
                z.put("regie.beginn", Zahl.text(w.scenarioStart()));
            }
            double[] h = w.handValues();
            for (int i = 0; i < h.length; i++) if (!Double.isNaN(h[i])) z.put("regie.hand." + Weather.Var.values()[i].name(), Zahl.text(h[i]));
        }
        Regie.Book bk = r.regie.book();
        if (bk != null) {
            z.put("regiebuch.name", bk.name());
            z.put("regiebuch.text", bk.text());
            z.put("regiebuch.sekunde", Zahl.text(Math.round(r.regie.time() * 100) / 100.0));
        }
        var g = r.gletscher();
        if (g != null) z.put("front.vorstoss", Zahl.text(Math.round(g.glacier.advance() * 100) / 100.0));
        var kb = r.kalbung;
        if (kb != null) {
            double notch = 0;
            for (var s : kb.sections()) notch = Math.max(notch, s.notch);
            z.put("front.kerbe", Zahl.text(Math.round(notch * 100) / 100.0));
        }
        Ruptura ru = r.ruptura;
        if (ru != null) {
            z.put("ruptura.phase", ru.phase().name());
            z.put("ruptura.stau", Zahl.text(Math.round(ru.stau() * 100) / 100.0));
        }
        z.put("szene.eisberge", Integer.toString(r.bergs.count()));
        for (Map.Entry<String, Object> e : teile(r).entrySet())
            for (Map.Entry<String, String> f : Felder.lesen(e.getValue(), ohne(e.getKey())).entrySet()) z.put(e.getKey() + "." + f.getKey(), f.getValue());
        return z;
    }

    /** Die Spalten von GLC_SITZUNG aus einem Zustand (für {@code Ablage.Sicherung}). */
    public static Map<String, String> spalten(Map<String, String> z) {
        Map<String, String> m = new LinkedHashMap<>();
        LocalDateTime t = szenenzeit(z);
        if (t != null) m.put(Speicher.SZ, t.toString());
        m.put(Speicher.ZEITRAFFER, z.getOrDefault("uhr.lapse", ""));
        if (z.containsKey("regiebuch.name")) { m.put(Speicher.REGIEBUCH, z.get("regiebuch.name")); m.put(Speicher.REGIE_SEKUNDE, z.get("regiebuch.sekunde")); }
        m.put(Speicher.VORSTOSS, z.getOrDefault("front.vorstoss", ""));
        m.put(Speicher.KERBE, z.getOrDefault("front.kerbe", ""));
        m.put(Speicher.RUPTURA, z.getOrDefault("ruptura.phase", ""));
        m.put(Speicher.STAU, z.getOrDefault("ruptura.stau", ""));
        m.put(Speicher.EISBERGE, z.getOrDefault("szene.eisberge", ""));
        return m;
    }

    /** Kamera für Ansichten: {x, y, z, Gier, Nick, Bildwinkel} aus dem laufenden Bild. */
    public static double[] kamera(Renderer r) {
        Camera c = r.camera;
        double[] e = c.eye, f = c.fwd;
        double nick = Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, f[1]))));
        return new double[]{e[0], e[1], e[2], c.heading(), nick, Camera.FOVY};
    }

    /** Szenenzeit des Zustands oder null. */
    public static LocalDateTime szenenzeit(Map<String, String> z) {
        String d = z.get("uhr.datum");
        if (d == null) return null;
        double h = Zahl.lies(z.get("uhr.stunde"));
        return LocalDate.parse(d).atStartOfDay().plusSeconds(Math.round((Double.isNaN(h) ? 0 : h) * 3600));
    }

    public static Quality qualitaet(Map<String, String> z) {
        try { return Quality.valueOf(z.get("app.qualitaet")); } catch (RuntimeException e) { return null; }
    }

    /**
     * Stellt den Zustand her (aus dem Swing-Faden). Qualität und Ton stellt der Aufrufer ein; hier alles andere außer den
     * Eisbergen. Gibt zurück, was nicht ging.
     */
    public static List<String> anwenden(Renderer r, Map<String, String> z) {
        List<String> fehler = new ArrayList<>();
        for (Map.Entry<String, Object> e : teile(r).entrySet()) {
            Map<String, String> t = Felder.teil(z, e.getKey());
            t.keySet().removeIf(x -> x.contains("."));
            for (String f : Felder.schreiben(e.getValue(), t, ohne(e.getKey()))) fehler.add(e.getKey() + "." + f);
        }
        String d = z.get("uhr.datum");
        double h = Zahl.lies(z.get("uhr.stunde"));
        if (d != null && !Double.isNaN(h)) {
            try { r.clock.set(LocalDate.parse(d), h); } catch (RuntimeException ex) { fehler.add("uhr.datum: " + ex.getMessage()); }
        }
        double lapse = Zahl.lies(z.get("uhr.lapse"));
        if (!Double.isNaN(lapse)) r.clock.lapse = lapse;
        Camera c = r.camera;
        c.stopFahrt();
        c.stopMitfahrt();
        c.diving = "true".equals(z.get("kamera.tauchen"));
        String[] ziel = z.getOrDefault("kamera.ziel", "").split(" ");
        if ("KREISEN".equals(z.get("kamera.modus")) && ziel.length == 6) {
            c.mode = Camera.Mode.KREISEN;
            c.lookAt(Zahl.lies(ziel[0]), Zahl.lies(ziel[1]), Zahl.lies(ziel[2]), Zahl.lies(ziel[3]), Zahl.lies(ziel[4]), Zahl.lies(ziel[5]));
            c.autoOrbit = "true".equals(z.get("kamera.kreisen"));
            c.snap();
        } else {
            double x = Zahl.lies(z.get("kamera.x")), y = Zahl.lies(z.get("kamera.y")), zz = Zahl.lies(z.get("kamera.z"));
            if (!Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(zz)) {
                c.place(x, y, zz, Zahl.lies(z.get("kamera.gier")), Zahl.lies(z.get("kamera.nick")));
                c.snap();
            }
        }
        if (r.wiring != null) {
            Weather w = r.wiring.weather;
            double now = r.clock.epochMillis() / 1000.0;
            String text = z.get("regie.text");
            boolean gestartet = false;
            if (text != null) {
                try {
                    Scenario sc = Scenario.parse(text);
                    double b = Zahl.lies(z.get("regie.beginn"));
                    w.start(sc, Double.isNaN(b) ? now : b);
                    gestartet = true;
                } catch (RuntimeException ex) {
                    fehler.add("regie.text: " + ex.getMessage());
                }
            }
            if (!gestartet) {
                Weather.Phase p;
                try { p = Weather.Phase.valueOf(z.getOrDefault("regie.lage", "KLAR")); } catch (IllegalArgumentException ex) { p = Weather.Phase.KLAR; }
                w.startFree(now, p);
            }
            r.wiring.resumeRegie();
            for (Weather.Var v : Weather.Var.values()) {
                double hv = Zahl.lies(z.get("regie.hand." + v.name()));
                if (!Double.isNaN(hv)) r.wiring.hand(v, hv);
            }
            r.wiring.regie = !"false".equals(z.get("regie.an"));
        }
        // die Ruptura folgt dem Jahr (Damm und Stau nach Datum); war sie durchgebrochen, bricht sie wieder durch
        Ruptura ru = r.ruptura;
        String rp = z.get("ruptura.phase");
        if (ru != null && rp != null) {
            try {
                Ruptura.Phase p = Ruptura.Phase.valueOf(rp);
                if (p.ordinal() >= Ruptura.Phase.TUNNEL.ordinal() && p.running()) ru.durchbruchJetzt();
            } catch (IllegalArgumentException ex) { fehler.add("ruptura.phase: " + rp); }
        }
        String rb = z.get("regiebuch.text");
        if (rb != null) {
            try {
                Regie.Book b = Regie.parse(rb);
                double sek = Zahl.lies(z.get("regiebuch.sekunde"));
                r.regie.fortsetzen(b, Double.isNaN(sek) ? 0 : sek);
            } catch (RuntimeException ex) {
                fehler.add("regiebuch.text: " + ex.getMessage());
            }
        }
        return fehler;
    }
}
