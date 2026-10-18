package com.dan.glacier.data;

import com.dan.glacier.gl.Renderer;
import com.dan.glacier.scene.Gletscher;
import com.dan.glacier.scene.Kalbung;
import com.dan.glacier.scene.Ruptura;
import com.dan.store.Ablage;
import com.dan.store.Dienst;
import com.dan.store.Protokoll;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Die Chronik der Szene (Phase 15, F26–F29, F32): beobachtet die laufende Szene alle 0,5 s und schreibt über den
 * gültigen {@link Speicher} in die Datenbank oder in die Dateien. Das Bild wartet nie: beobachtet wird in einem eigenen
 * Faden, geschrieben in einem zweiten (der Reihe nach, damit eine Kalbung ihre Nummer hat, bevor ihr Eisberg sie braucht).
 * <ul>
 * <li><b>Kalbungen</b>: jeder Eintrag aus {@link Kalbung#log}; nach 30 s Physik geschrieben (dann sind die Stücke des Abbruchs gefallen und
 * gezählt), mit dem Riss an der Wand (Wege aus {@code fracture.onCrack} in 80 m), der Fallzeit (erster Aufschlag) und
 * nach 60 s der Wellenhöhe am Pegel „Steg“. Auslöser: von Hand, mit der Karte, aus dem Regiebuch oder die Regie.</li>
 * <li><b>Eisberge</b> ab 200 m³: Geburt, Weg (ein Punkt je Szenenminute, wenn er 2 m weiter ist), Kenterungen, Ende;
 * geschrieben alle 2 min und am Ende.</li>
 * <li><b>Front</b>: einmal je Szenentag ab 12 Uhr, nach jeder Kalbung ab 10 000 m³, wenn sich der Vorstoß um 1 m
 * geändert hat, und auf Knopfdruck („Front merken“).</li>
 * <li><b>Ruptura</b>: ein Lauf je Stau bis zum Ende, mit den Zeitpunkten der Abschnitte.</li>
 * <li><b>Regiebücher</b>: jeder Lauf mit Blatt (oder abgebrochen).</li>
 * </ul>
 */
public final class Chronik {
    /** Ab welchem Volumen ein Stück als Eisberg in die Chronik kommt (m³, F28 A). */
    public static final double EISBERG_MIN = 200;
    /**
     * Nach so vielen Sekunden der Physik (ab dem ersten freien Stück) wird eine Kalbung geschrieben (dann ist der Turm
     * gefallen und gezählt), nach so vielen die Welle nachgetragen; spätestens nach der doppelten (Welle) oder vierfachen Zeit (Kalbung) auf der Uhr, falls die Physik hängt.
     */
    public static final double SCHREIBEN_S = 30, WELLE_S = 60;
    /** Ab diesem Volumen schreibt eine Kalbung auch die Front (m³). */
    public static final double FRONT_NACH_KALBUNG = 10_000;

    private final Renderer r;
    private final ScheduledExecutorService schauen;
    private final ExecutorService schreiben;
    private volatile boolean an = true;
    /** Zähler für Block CHRONIK und Prüfung. */
    public volatile int kalbungen, eisberge, fronten, laeufe, regielaeufe, fehler;
    public volatile String letzte = "";

    /** Eine Kalbung zwischen Bruch und Welle. */
    private static final class Offen {
        int index;
        Kalbung.Ereignis e;
        LocalDateTime zeit;
        long gesehenMs;
        double welt;
        String ausloeser, regiebuch;
        Double fallzeit;
        final AtomicLong id = new AtomicLong(-1);
        boolean geschrieben, welleFertig;
    }

    /** Ein Eisberg unterwegs. */
    private static final class Spur {
        int nr;
        LocalDateTime geburt;
        double gx, gz, v0;
        final List<double[]> weg = new ArrayList<>();
        double letzteMinute;
        long geschriebenMs;
        Integer kalbungIndex;
        Long laufId;
        boolean grounded;
        long seed;
        Long kalbungId;
        /** Fortgesetzt aus einer früheren Sitzung („Weiter wie zuletzt“): ihre Nummer; sonst null (die laufende). */
        Long sitzung;
        Daten.Eisberg zuletzt;
    }

    private final List<Offen> offen = new ArrayList<>();
    private final List<Offen> fertig = new ArrayList<>();
    private final Map<Integer, Spur> spuren = new HashMap<>();
    private final Map<Integer, Integer> herkunft = new HashMap<>();
    private final Set<Integer> geschmolzen = new HashSet<>();
    private final Set<Integer> treibeis = new HashSet<>();
    private final Map<Integer, Long> erstGesehen = new HashMap<>();
    private final ArrayDeque<Object[]> risse = new ArrayDeque<>();
    private final ArrayDeque<double[]> aufschlaege = new ArrayDeque<>();
    private final ArrayDeque<double[]> marken = new ArrayDeque<>();
    private int gelesen;
    private Kalbung kalbungVorher;
    private LocalDate frontTag;
    private double frontVorstoss = Double.NaN;
    private volatile boolean frontJetzt;
    private String frontArt = Daten.F_SITZUNG;
    // Ruptura
    private Ruptura.Phase phase = Ruptura.Phase.AUS;
    private Daten.RupturaLauf lauf;
    private final AtomicLong laufId = new AtomicLong(-1);
    private long laufGeschriebenMs;
    private Ruptura rupturaVorher;

    public Chronik(Renderer r) {
        this.r = r;
        schauen = Executors.newSingleThreadScheduledExecutor(x -> faden(x, "Glacier-Chronik"));
        schreiben = Executors.newSingleThreadExecutor(x -> faden(x, "Glacier-Chronik-Schreiben"));
        r.chronikAn = true;
        schauen.scheduleWithFixedDelay(this::schauen, 500, 500, TimeUnit.MILLISECONDS);
    }

    private static Thread faden(Runnable x, String n) {
        Thread t = new Thread(x, n);
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    }

    /** Die Chronik schreibt (true) oder nicht; die Beobachtung läuft weiter. */
    public void schreiben(boolean ja) { an = ja; }
    public boolean schreibt() { return an; }

    /** Die Säulenkarte hat an (x, z) geschwächt: die nächste Kalbung dort kommt mit dem Auslöser KARTE. */
    public void karte(double x, double z) {
        synchronized (marken) { marken.add(new double[]{x, z, System.currentTimeMillis()}); }
    }

    /**
     * „Weiter wie zuletzt“ (F31 A): ein Berg der Szene setzt einen offenen Eisberg einer früheren Sitzung fort; seine
     * Zeile wird weitergeschrieben (gleiche Sitzung und Nummer, Geburt und Weg bleiben).
     */
    public synchronized void fortsetzen(com.dan.buoyancy.Berg b, Daten.Eisberg alt, long altSitzung) {
        Spur s = new Spur();
        s.nr = alt.nr();
        s.geburt = alt.geburt();
        s.gx = alt.geburtX();
        s.gz = alt.geburtZ();
        s.v0 = alt.volumenStartM3();
        s.weg.addAll(alt.weg());
        s.letzteMinute = r.clock.epochMillis() / 60_000.0;
        s.seed = alt.formSeed();
        s.kalbungId = alt.kalbungId();
        s.laufId = alt.rupturaLaufId();
        s.sitzung = altSitzung;
        s.geschriebenMs = System.currentTimeMillis();
        spuren.put(b.id, s);
    }

    /** „Front merken“ (F26): die Front jetzt schreiben. */
    public void frontMerken() { frontArt = Daten.F_MERKEN; frontJetzt = true; }

    /** Beim Beenden: offene Eisberge und Läufe sichern, dann zu (höchstens 5 s). */
    public void beenden() {
        r.chronikAn = false;
        schauen.shutdown();
        try { schauen.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        synchronized (this) {
            for (Spur s : spuren.values()) if (s.zuletzt != null) aufschreiben(s.zuletzt);
            if (lauf != null) laufSchreiben(lauf);
        }
        schreiben.shutdown();
        try { schreiben.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private Long sitzung() { long s = Dienst.sitzung(); return s >= 0 ? s : null; }

    /** Eine Schreibarbeit. */
    @FunctionalInterface private interface Arbeit { void tun() throws Ablage.Fehler; }

    private void tun(String was, Arbeit a) {
        if (!an) return;
        schreiben.execute(() -> {
            try {
                a.tun();
            } catch (Ablage.Fehler | RuntimeException e) {
                fehler++;
                Protokoll.warnung("Chronik", was + " nicht geschrieben: " + e.getMessage());
            }
        });
    }

    /** Für Prüfungen: warten, bis alles geschrieben ist. */
    public void abwarten() {
        try { schreiben.submit(() -> { }).get(10, TimeUnit.SECONDS); } catch (Exception e) { /* dann nicht */ }
    }

    private LocalDateTime jetzt() { return Daten.szenenzeit(r.clock.epochMillis()); }

    // ------------------------------------------------------------------ beobachten

    private synchronized void schauen() {
        try {
            ereignisse();
            kalbungen();
            eisberge();
            front();
            ruptura();
            regie();
        } catch (RuntimeException e) {
            fehler++;
            Protokoll.fehler("Chronik", "Beobachten: " + e, e);
        }
    }

    private void ereignisse() {
        Object[] e;
        long now = System.currentTimeMillis();
        while ((e = r.chronikEreignisse.poll()) != null) {
            switch ((String) e[0]) {
                case "riss" -> { risse.add(new Object[]{now, e[1]}); while (risse.size() > 400) risse.poll(); }
                case "aufschlag" -> {
                    double[] a = {(double) e[1], (double) e[2], (double) e[4], now};
                    // der erste Aufschlag einer offenen Kalbung gleich (viele Brocken schlagen auf; die Liste behält nur die letzten 400)
                    for (Offen o : offen)
                        if (o.fallzeit == null && a[2] >= o.welt - 0.5 && Math.hypot(a[0] - o.e.x(), a[1] - o.e.z()) < 200) o.fallzeit = Math.round(Math.max(0, a[2] - o.welt) * 100) / 100.0;
                    aufschlaege.add(a);
                    while (aufschlaege.size() > 400) aufschlaege.poll();
                }
                case "geboren" -> herkunft.put((Integer) e[1], (Integer) e[2]);
                case "geschmolzen" -> geschmolzen.add((Integer) e[1]);
                default -> { }
            }
        }
        while (!risse.isEmpty() && (long) risse.peek()[0] < now - 120_000) risse.poll();
        while (!aufschlaege.isEmpty() && aufschlaege.peek()[3] < now - 120_000) aufschlaege.poll();
        synchronized (marken) { while (!marken.isEmpty() && marken.peek()[2] < now - 180_000) marken.poll(); }
    }

    private void kalbungen() {
        Kalbung k = r.kalbung;
        if (k == null) return;
        if (k != kalbungVorher) { kalbungVorher = k; gelesen = 0; }
        List<Kalbung.Ereignis> log;
        synchronized (k.log) { log = new ArrayList<>(k.log); }
        long now = System.currentTimeMillis();
        // neue Einträge
        for (; gelesen < log.size(); gelesen++) {
            Kalbung.Ereignis e = log.get(gelesen);
            Offen o = new Offen();
            o.index = gelesen;
            o.e = e;
            // Szenenzeit des Bruchs: jetzt minus die Szenenzeit, die die Kalbung seither gerechnet hat
            double seit = Math.max(0, k.sceneTime() - e.time());
            o.zeit = Daten.szenenzeit(r.clock.epochMillis() - seit * 1000);
            o.gesehenMs = now;
            // Zeit der Physik beim ersten freien Stück des Abschnitts (die Fallzeit zählt von dort)
            o.welt = Double.isNaN(e.mainBreak()) ? r.bruch.time() : e.mainBreak();
            com.dan.glacier.scene.Regie.Book b = r.regie.book();
            o.regiebuch = b == null ? null : b.name();
            o.ausloeser = Daten.REGIE;
            if (e.hand()) {
                o.ausloeser = b != null ? Daten.REGIEBUCH : Daten.HAND;
                synchronized (marken) {
                    for (double[] m : marken) if (Math.hypot(m[0] - e.x(), m[1] - e.z()) < 150) { o.ausloeser = Daten.KARTE; marken.remove(m); break; }
                }
            }
            offen.add(o);
        }
        for (Offen o : offen) {
            o.e = o.index < log.size() ? log.get(o.index) : o.e;   // Volumen und Stücke wachsen noch
            double seit = r.bruch.time() - o.welt;
            if (!o.geschrieben && (seit >= SCHREIBEN_S || now - o.gesehenMs >= 4 * SCHREIBEN_S * 1000)) {
                o.geschrieben = true;
                Daten.Kalbung z = zeile(o);
                tun("Kalbung", () -> { o.id.set(Datenseite.speicher().kalbung(z, sitzung())); kalbungen++; letzte = Daten.text(z.szenenzeit()) + " " + Daten.artLabel(z.art()); });
                if (o.e.volume() >= FRONT_NACH_KALBUNG) { frontArt = Daten.F_SITZUNG; frontNachKalbung = true; }
            }
            if (o.geschrieben && !o.welleFertig && (seit >= WELLE_S || now - o.gesehenMs >= 2 * WELLE_S * 1000)) {
                o.welleFertig = true;
                com.dan.wave.Wave.Gauge g = r.pegel;
                if (g != null) {
                    float[][] sr = g.series(Math.min(300, Math.max(seit, (now - o.gesehenMs) / 1000.0) + 5));
                    double max = 0;
                    for (float v : sr[1]) if (!Float.isNaN(v)) max = Math.max(max, Math.abs(v));
                    double h = max;
                    tun("Welle", () -> { if (o.id.get() > 0) Datenseite.speicher().welle(o.id.get(), h); });
                }
            }
        }
        offen.removeIf(o -> { if (o.welleFertig) { fertig.add(o); return true; } return false; });
        while (fertig.size() > 200) fertig.remove(0);
    }

    private boolean frontNachKalbung;

    /** Die Zeile einer Kalbung (Riss aus den Wegen von fracture in 80 m um den Ort, entlang der Front geordnet). */
    private Daten.Kalbung zeile(Offen o) {
        Kalbung.Ereignis e = o.e;
        // Fallzeit: vom ersten freien Stück bis zum ersten Aufschlag im Wasser bis 200 m um den Ort
        if (o.fallzeit == null)
            for (double[] a : aufschlaege)
                if (a[2] >= o.welt - 0.5 && Math.hypot(a[0] - e.x(), a[1] - e.z()) < 200) { o.fallzeit = Math.round(Math.max(0, a[2] - o.welt) * 100) / 100.0; break; }
        List<double[]> pts = new ArrayList<>();
        for (Object[] rs : risse) {
            double[] p = (double[]) rs[1];
            for (int i = 0; i + 2 < p.length; i += 3) if (Math.hypot(p[i] - e.x(), p[i + 2] - e.z()) < 80) pts.add(new double[]{p[i], p[i + 2]});
        }
        Daten.Geom riss = null;
        Gletscher gl = r.gletscher();
        if (pts.size() >= 2 && gl != null) {
            double[] nf = gl.glacier.nearestFront(e.x(), e.z(), 300);
            double nl = nf == null ? 1 : Math.max(1e-9, Math.hypot(nf[2], nf[3]));
            double tx = nf == null ? 1 : -nf[3] / nl, tz = nf == null ? 0 : nf[2] / nl;
            // die Spur des Risses an der Wand: längs der Front in Fächer zu 4 m, je Fach der mittlere Punkt
            double nx = tz, nz = -tx, s0 = Double.MAX_VALUE, s1 = -Double.MAX_VALUE;
            for (double[] q : pts) { double u = q[0] * tx + q[1] * tz; s0 = Math.min(s0, u); s1 = Math.max(s1, u); }
            int bins = (int) Math.max(1, Math.ceil((s1 - s0) / 4));
            double[] su = new double[bins], sv = new double[bins];
            int[] n = new int[bins];
            for (double[] q : pts) {
                double u = q[0] * tx + q[1] * tz, v = q[0] * nx + q[1] * nz;
                int i = (int) Math.min(bins - 1, (u - s0) / 4);
                su[i] += u; sv[i] += v; n[i]++;
            }
            List<double[]> d = new ArrayList<>();
            for (int i = 0; i < bins; i++) if (n[i] > 0) { double u = su[i] / n[i], v = sv[i] / n[i]; d.add(new double[]{u * tx + v * nx, u * tz + v * nz}); }
            if (d.size() >= 2) riss = Daten.Geom.linie(Startdaten.vereinfachen(d.toArray(new double[0][]), 0.5));
        }
        int doy = o.zeit.getDayOfYear();
        String art = switch (e.art()) { case TURM -> "TURM"; case SCHEIBE -> "SCHEIBE"; case FUSS -> "FUSS"; };
        return new Daten.Kalbung(0, o.zeit, doy, art, o.ausloeser, e.x(), e.height() + r.camera.waterLevel, e.z(), riss, e.volume(), e.volume() * 0.9, e.pieces(),
                e.height(), o.fallzeit, e.crackLead(), null, (long) e.solid(), o.regiebuch);
    }

    // ------------------------------------------------------------------ Eisberge

    private void eisberge() {
        long now = System.currentTimeMillis();
        LocalDateTime t = jetzt();
        double szMin = r.clock.epochMillis() / 60_000.0;
        Set<Integer> da = new HashSet<>();
        double level = r.camera.waterLevel;
        Ruptura ru = r.ruptura;
        for (com.dan.buoyancy.Berg b : r.bergs.bergs) {
            da.add(b.id);
            Spur s = spuren.get(b.id);
            double[] c = b.cgWorld();
            if (s == null) {
                if (b.initialVolume() < EISBERG_MIN || treibeis.contains(b.id)) continue;
                // Phase 16 (Befund Tageskarte 16.1.2026): nur Eisberge mit Herkunft (Kalbung oder Ruptura). Das Treibeis
                // beim Start ist Kulisse; es wartet 3 s auf ein „geboren“ und gilt dann als Treibeis.
                Integer src = herkunft.remove(b.id);
                if (src == null) {
                    Long f = erstGesehen.putIfAbsent(b.id, now);
                    if (f != null && now - f > 3000) { treibeis.add(b.id); erstGesehen.remove(b.id); }
                    continue;
                }
                erstGesehen.remove(b.id);
                s = new Spur();
                s.nr = b.id;
                s.geburt = t;
                s.gx = c[0];
                s.gz = c[2];
                s.v0 = b.initialVolume();
                s.letzteMinute = szMin;
                s.weg.add(new double[]{c[0], c[2]});
                s.seed = 7919L * b.id + Math.round(s.v0);
                {
                    for (Offen o : offen) if (o.e.solid() == src) s.kalbungIndex = o.index;
                    for (Offen o : fertig) if (o.e.solid() == src) s.kalbungIndex = o.index;
                    if (ru != null && ru.solid() != null && ru.solid().id == src && laufId.get() > 0) s.laufId = laufId.get();
                }
                spuren.put(b.id, s);
                eisberge++;
            }
            double[] last = s.weg.get(s.weg.size() - 1);
            if (szMin - s.letzteMinute >= 1 && Math.hypot(c[0] - last[0], c[2] - last[1]) >= 2) {
                s.weg.add(new double[]{c[0], c[2]});
                s.letzteMinute = szMin;
            }
            s.grounded = b.grounded();
            double[] df = b.draftAndFreeboard(level);
            double[] R = b.rotation();
            double gier = (Math.toDegrees(Math.atan2(R[0], -R[6])) + 360) % 360;
            s.zuletzt = eisberg(s, b.volume(), df[0], df[1], b.capsizes(), c[0], c[2], gier, null, Daten.OFFEN);
            if (now - s.geschriebenMs >= 120_000) {
                s.geschriebenMs = now;
                aufschreiben(s.zuletzt);
            }
        }
        treibeis.retainAll(da);
        erstGesehen.keySet().retainAll(da);
        herkunft.keySet().removeIf(id -> !da.contains(id) && !spuren.containsKey(id) && herkunft.size() > 200);
        // verschwunden: geschmolzen, gestrandet oder aus der Szene
        for (java.util.Iterator<Map.Entry<Integer, Spur>> it = spuren.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Spur> en = it.next();
            if (da.contains(en.getKey())) continue;
            Spur s = en.getValue();
            String ende = geschmolzen.remove(en.getKey()) ? Daten.GESCHMOLZEN : s.grounded ? Daten.GESTRANDET : Daten.AUS_DER_SZENE;
            Daten.Eisberg z = s.zuletzt;
            if (z != null) aufschreiben(eisberg(s, ende.equals(Daten.GESCHMOLZEN) ? 0 : z.volumenM3(), z.tiefgangM(), z.freibordM(), z.kenterungen(), z.x(), z.z(), z.gierGrad(), t, ende));
            it.remove();
        }
    }

    private Daten.Eisberg eisberg(Spur s, double v, double tief, double frei, int kent, double x, double z, double gier, LocalDateTime ende, String art) {
        Long kid = s.kalbungId;
        if (s.kalbungIndex != null) {
            for (Offen o : offen) if (o.index == s.kalbungIndex && o.id.get() > 0) kid = o.id.get();
            for (Offen o : fertig) if (o.index == s.kalbungIndex && o.id.get() > 0) kid = o.id.get();
        }
        List<double[]> w = new ArrayList<>(s.weg);
        double[] last = w.get(w.size() - 1);
        if (Math.hypot(x - last[0], z - last[1]) >= 2) w.add(new double[]{x, z});
        double[][] vw = Startdaten.vereinfachen(w.toArray(new double[0][]), 2);
        return new Daten.Eisberg(s.nr, kid, s.laufId, s.geburt, s.gx, s.gz, s.v0, v, tief, frei, kent, java.util.Arrays.asList(vw), x, z, gier, s.seed, ende, art);
    }

    private void aufschreiben(Daten.Eisberg e) {
        Integer ki = null;
        Long sid = null;
        for (Spur s : spuren.values()) if (s.nr == e.nr() && s.geburt.equals(e.geburt())) { ki = s.kalbungIndex; sid = s.sitzung; }
        final Integer kIndex = ki;
        final Long fort = sid;
        tun("Eisberg", () -> {
            Daten.Eisberg z = e;
            // die Nummer der Kalbung steht erst nach ihrem Schreiben fest (gleicher Faden, also jetzt)
            if (z.kalbungId() == null && kIndex != null) {
                Long kid = null;
                synchronized (Chronik.this) {
                    for (Offen o : offen) if (o.index == kIndex && o.id.get() > 0) kid = o.id.get();
                    for (Offen o : fertig) if (o.index == kIndex && o.id.get() > 0) kid = o.id.get();
                }
                if (kid != null) z = new Daten.Eisberg(z.nr(), kid, z.rupturaLaufId(), z.geburt(), z.geburtX(), z.geburtZ(), z.volumenStartM3(), z.volumenM3(), z.tiefgangM(),
                        z.freibordM(), z.kenterungen(), z.weg(), z.x(), z.z(), z.gierGrad(), z.formSeed(), z.ende(), z.endeArt());
            }
            Datenseite.speicher().eisberg(z, fort != null ? fort : sitzung());
        });
    }

    /** Die Spuren für die Tageskarte und die Eisbergkarte: Nummer → {Geburt (ms Szene), Volumen, Kalbung}. */
    public synchronized Daten.Eisberg spur(int bergId) {
        Spur s = spuren.get(bergId);
        return s == null ? null : s.zuletzt;
    }

    // ------------------------------------------------------------------ Front

    private void front() {
        Gletscher g = r.gletscher();
        if (g == null) return;
        LocalDateTime t = jetzt();
        double adv = g.glacier.advance();
        boolean tag = t.getHour() >= 12 && !t.toLocalDate().equals(frontTag);
        boolean kalb = frontNachKalbung && (Double.isNaN(frontVorstoss) || Math.abs(adv - frontVorstoss) >= 1);
        frontNachKalbung = false;
        if (!(tag || kalb || frontJetzt)) return;
        String art = frontJetzt ? frontArt : Daten.F_SITZUNG;
        frontJetzt = false;
        frontArt = Daten.F_SITZUNG;
        if (tag) frontTag = t.toLocalDate();
        frontVorstoss = adv;
        Daten.Geom linie = Startdaten.linie(g.glacier);
        double len = linie.laenge();
        Daten.Front f = new Daten.Front(0, t, null, art, art.equals(Daten.F_MERKEN) ? "Gemerkt " + Daten.text(t) : "Front " + Daten.text(t), linie, len,
                Math.round(adv * 100) / 100.0, (double) Math.round(adv * len), Math.round(g.glacier.frontTop() * 100) / 100.0, null,
                String.format(Locale.GERMAN, "Vorstoß %+.1f m (Jahresgang [G])%s", adv, tag ? "" : kalb ? ", nach einer großen Kalbung" : ""), Daten.EIGEN);
        tun("Front", () -> { Datenseite.speicher().front(f, sitzung()); fronten++; });
    }

    // ------------------------------------------------------------------ Ruptura

    private void ruptura() {
        Ruptura ru = r.ruptura;
        if (ru != rupturaVorher) { rupturaVorher = ru; phase = Ruptura.Phase.AUS; }
        if (ru == null) return;
        Ruptura.Phase p = ru.phase();
        long now = System.currentTimeMillis();
        if (lauf == null && p.running()) {
            LocalDateTime beginn = jetzt().minusSeconds((long) ru.sceneTime());
            double[][] box = {ru.dam.world(0, ru.dam.qMin), ru.dam.world(ru.dam.length, ru.dam.qMin), ru.dam.world(ru.dam.length, ru.dam.qMax), ru.dam.world(0, ru.dam.qMax)};
            double[][] rinne = new double[Ruptura.RINNE.length / 2][];
            for (int i = 0; i < rinne.length; i++) rinne[i] = new double[]{Ruptura.RINNE[2 * i], Ruptura.RINNE[2 * i + 1]};
            com.dan.glacier.scene.Regie.Book b = r.regie.book();
            lauf = new Daten.RupturaLauf(0, beginn.getYear(), beginn, null, null, null, null, null, null, p.name(), ru.stau(), ru.flow(), 0, 0, 0, 0,
                    Daten.Geom.flaeche(box), Daten.Geom.linie(rinne), b == null ? null : b.name());
            laufId.set(-1);
            laufSchreiben(lauf);
            laeufe++;
        }
        if (lauf == null) { phase = p; return; }
        LocalDateTime zu = lauf.beginn().plusSeconds((long) ru.sceneTime());
        Daten.RupturaLauf l = lauf;
        boolean neu = p != phase;
        double stauMax = Math.max(l.stauMaxM(), ru.stau()), flowMax = Math.max(l.abflussMaxM3s(), ru.flow());
        LocalDateTime db = l.durchbruch(), tu = l.tunnel(), ri = l.rinne(), br = l.bruecke(), ei = l.einsturz(), en = l.ende();
        if (neu) {
            switch (p) {
                case TUNNEL -> { if (db == null) db = zu; if (tu == null) tu = zu; }
                case RINNE -> { if (ri == null) ri = zu; }
                case BRUECKE -> { if (br == null) br = zu; }
                case ABFLUSS -> { if (ei == null) ei = zu; }
                case RUHE, AUS -> { if (en == null) en = zu; }
                default -> { }
            }
        }
        String ph = p == Ruptura.Phase.AUS ? l.phase() : p.name();
        lauf = new Daten.RupturaLauf(l.id(), l.jahr(), l.beginn(), db, tu, ri, br, ei, en, ph, stauMax, flowMax, Math.max(0, (stauMax - ru.stau())) * Ruptura.BRAZO_AREA,
                ru.bridgeVolume, ru.largest, ru.fallen, l.damm(), l.rinneLinie(), l.regiebuch());
        phase = p;
        boolean ende = p == Ruptura.Phase.RUHE || p == Ruptura.Phase.AUS;
        if (neu || ende || now - laufGeschriebenMs > 30_000) { laufGeschriebenMs = now; laufSchreiben(lauf); }
        if (ende) lauf = null;
    }

    private void laufSchreiben(Daten.RupturaLauf l) {
        tun("Lauf der Ruptura", () -> {
            long id = laufId.get();
            Daten.RupturaLauf x = new Daten.RupturaLauf(Math.max(0, id), l.jahr(), l.beginn(), l.durchbruch(), l.tunnel(), l.rinne(), l.bruecke(), l.einsturz(), l.ende(),
                    l.phase(), l.stauMaxM(), l.abflussMaxM3s(), l.volumenM3(), l.brueckeM3(), l.groesstesM3(), l.stuecke(), l.damm(), l.rinneLinie(), l.regiebuch());
            laufId.set(Datenseite.speicher().rupturaLauf(x, sitzung()));
        });
    }

    // ------------------------------------------------------------------ Regiebücher

    private void regie() {
        Object[] l = r.regieLauf;
        if (l == null) return;
        r.regieLauf = null;
        File f = (File) l[9];
        tun("Lauf eines Regiebuchs", () -> {
            byte[] png = null;
            if (f != null && Datenseite.speicher().datenbank()) {
                try { png = java.nio.file.Files.readAllBytes(f.toPath()); } catch (java.io.IOException e) { /* ohne Blatt */ }
            }
            Datenseite.speicher().regielauf(new Daten.Regielauf((String) l[0], (LocalDateTime) l[1], (LocalDateTime) l[2], (Boolean) l[3], (Double) l[4],
                    (Double) l[5], (Double) l[6], (Integer) l[7], (String) l[8], png, f == null ? null : f.getPath()), sitzung());
            regielaeufe++;
        });
    }

    /** „Chronik: 12 Kalbungen, 30 Eisberge, 3 Fronten, 1 Lauf der Ruptura · zuletzt 16.01.2026 14:32 Turm“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Chronik%s: %d Kalbungen, %d Eisberge, %d Fronten, %d Läufe der Ruptura, %d Läufe der Regiebücher%s%s", an ? "" : " (aus)",
                kalbungen, eisberge, fronten, laeufe, regielaeufe, letzte.isEmpty() ? "" : " · zuletzt " + letzte, fehler > 0 ? " · " + fehler + " nicht geschrieben" : "");
    }
}
