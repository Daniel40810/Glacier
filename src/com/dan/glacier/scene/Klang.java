package com.dan.glacier.scene;

import com.dan.glacier.wiring.SoundEvent;
import com.dan.soundscape.Aeolian;
import com.dan.soundscape.Hum;
import com.dan.soundscape.Soundscape;

import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Phase 12: der Klang am Gletscher, wie die App {@code com.dan.soundscape} verdrahtet. Ereignisse kommen als
 * {@link SoundEvent} aus jedem Faden (fracture, wave, flock, buoyancy, lightning) in eine Schlange; {@link #step}
 * verteilt sie im Faden des Bilds, mit Ohr und Wetter des Augenblicks:
 * <ul>
 * <li><b>Knacken</b> aus jedem Riss ({@code fracture.onCrack}), der <b>Donner der Kalbung</b> aus dem Weg des Risses,
 * wenn sich ein großes Stück löst ({@code onBreak}), <b>Aufschlag</b> ins Wasser ({@code onImpact}, die größten je
 * Fünftelsekunde);</li>
 * <li>das <b>Ächzen</b> der Front und leises Knacken in der Ferne von selbst (im Sommer öfter);</li>
 * <li><b>Auflaufen</b> der Welle am Ufer nahe dem Ohr ({@code wave.onRunup});</li>
 * <li><b>Möwen</b> und <b>Kondore</b> ({@code flock.onCall}), ein <b>kenternder Berg</b> ({@code buoyancy.onCapsize});</li>
 * <li>der <b>Diesel</b> des Katamarans (an Bord gleichmäßig, Drehzahl nach der Fahrt), Wind am Geländer (Äolstöne),
 * Glucksen an den Pfählen, Regen, See, Wind über Eis (geringe Rauigkeit) oder über dem Wald.</li>
 * </ul>
 */
public final class Klang implements SoundEvent {
    public final Soundscape s;
    private final ConcurrentLinkedQueue<double[]> queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<double[][]> calvings = new ConcurrentLinkedQueue<>();
    private final SplittableRandom rnd = new SplittableRandom(12);
    /** Diesel des Katamarans (Ort setzt die App je Bild). */
    public final Hum diesel = new Hum("Diesel des Katamarans", 0, 0, 0);
    /** Phase 13: das Tosen der Ruptura an der Mündung der Rinne (Ort und Durchfluss setzt die App je Bild). */
    public final com.dan.soundscape.Torrent flut = new com.dan.soundscape.Torrent("Ruptura", 0, 0, 0);
    /** Punkte der Front {x, z} in der Nähe (für das Ächzen von selbst); die App setzt sie, wenn die Front steht. */
    public volatile double[][] front;
    /** Wie oft die Front von selbst ächzt und knackt: mittlere Abstände (s) im Sommer; im Winter doppelt so lang. */
    public volatile double groanEvery = 30, crackEvery = 5;
    /** Sommer 0..1 (aus dem Jahr). */
    public volatile double summer = 1;
    private double groanIn = 8, crackIn = 2, impactAcc, impactX, impactY, impactZ, impactVol, runupClock;
    private double[] lastCrack;
    private long events, cracks, calvingsDone, impacts, runups, groans;

    public Klang() {
        s = Soundscape.beaufort(4);
        s.quality = Soundscape.Quality.MEDIUM;
        s.seed = 2026;
        diesel.firingHz = Hum.firing(900, 6);
        diesel.levelDb = 104;
        diesel.aboardDb = 74;
        s.hums.add(diesel);
        s.torrents.add(flut);
    }

    /** Phase 13: Wildwasser der Ruptura an (x, y, z) mit dem Durchfluss (m³/s); 0 schweigt. */
    public void flood(double x, double y, double z, double flow) {
        flut.x = x;
        flut.y = y;
        flut.z = z;
        flut.flow = flow;
    }

    /** Geländer, die im Wind singen, und Pfähle, an denen es gluckst (aus den Stegen). */
    public void stege(Stege st) {
        for (Stege.Balkon b : st.balkone) s.bodies.add(new Aeolian(b.name() + " Handlauf", b.x(), b.y() + Stege.RAIL, b.z(), 0.05, b.width() + b.depth()));
        int k = 0;
        for (Stege.Pfosten p : st.pfosten()) if (p.y0() < 0 && k++ % 2 == 0) s.pilings.add(new double[]{p.x(), p.z()});
    }

    // ------------------------------------------------------------------ Ereignisse (aus jedem Faden)

    @Override public void sound(Kind kind, double x, double y, double z, double strength) {
        queue.add(new double[]{kind.ordinal(), x, y, z, strength});
        events++;
    }

    /** Ein Riss läuft ({@code fracture.onCrack}): Knacken; der Weg wird gemerkt für den Donner, wenn ein Stück frei wird. */
    public void crack(double[] path, double energyJ, boolean broken) {
        if (path == null || path.length < 3) return;
        int n = path.length / 3;
        double x = 0, y = 0, z = 0;
        for (int i = 0; i < n; i++) { x += path[i * 3]; y += path[i * 3 + 1]; z += path[i * 3 + 2]; }
        double st = Math.max(0.1, Math.min(1, (Math.log10(Math.max(1, energyJ)) - 3) / 4));   // 1 kJ … 10 MJ
        sound(Kind.KNACKEN, x / n, y / n, z / n, st);
        if (broken) lastCrack = path.clone();
    }

    /** Ein Stück ist frei ({@code fracture.onBreak}): ab 300 m³ der Donner aus dem letzten Riss. */
    public void broke(double volume) {
        double[] p = lastCrack;
        if (p == null || volume < 300) return;
        lastCrack = null;
        int n = p.length / 3;
        double[][] pts = new double[n][];
        for (int i = 0; i < n; i++) pts[i] = new double[]{p[i * 3], p[i * 3 + 1], p[i * 3 + 2]};
        calvings.add(new double[][]{{volume}, flatten(pts)});
    }

    private static double[] flatten(double[][] p) {
        double[] f = new double[p.length * 3];
        for (int i = 0; i < p.length; i++) System.arraycopy(p[i], 0, f, i * 3, 3);
        return f;
    }

    // ------------------------------------------------------------------ je Bild

    /**
     * Ohr (Auge der Kamera, Blick in Grad), Wetter, Wasser; dt echte Sekunden. Verteilt die Ereignisse an
     * {@code soundscape} und lässt die Front von selbst ächzen und knacken.
     */
    public void step(double dt, double[] eye, double yawDeg, double windSpeed, double windFromDeg, double gust, double rainMmH, double airC,
                     double waveHs, double wavePeriod, double level, boolean onIce, boolean inForest) {
        s.earX = eye[0];
        s.earY = eye[1];
        s.earZ = eye[2];
        s.earYawDeg = yawDeg;
        s.windSpeed = windSpeed;
        s.windFromDeg = windFromDeg;
        s.gustiness = gust;
        s.rainRate = rainMmH;
        s.temperatureC = airC;
        s.waterLevel = level;
        // die See hört man am Wasser; oben am Hang leiser
        double above = Math.max(0, eye[1] - level);
        s.waveHeight = waveHs / (1 + above / 15);
        s.wavePeriod = wavePeriod;
        s.roughness = onIce ? 0.002 : inForest ? 0.4 : 0.03;
        double[] e;
        while ((e = queue.poll()) != null) dispatch(Kind.values()[(int) e[0]], e[1], e[2], e[3], e[4]);
        double[][] c;
        while ((c = calvings.poll()) != null) {
            double[] f = c[1];
            double[][] pts = new double[f.length / 3][];
            for (int i = 0; i < pts.length; i++) pts[i] = new double[]{f[i * 3], f[i * 3 + 1], f[i * 3 + 2]};
            s.calving(pts, c[0][0]);
            calvingsDone++;
        }
        // Aufschläge: der größte je Fünftelsekunde (mit der Summe der Volumen)
        impactAcc += dt;
        if (impactAcc >= 0.2 && impactVol > 0) {
            s.event(Soundscape.Kind.AUFSCHLAG, impactX, impactY, impactZ, 1, impactVol);
            impacts++;
            impactVol = 0;
            impactAcc = 0;
        }
        runupClock -= dt;
        // die Front von selbst
        double[][] fr = front;
        if (fr != null && fr.length > 0) {
            double slow = 1 + (1 - summer);
            groanIn -= dt;
            crackIn -= dt;
            if (groanIn <= 0) {
                groanIn = -Math.log(1 - rnd.nextDouble()) * groanEvery * slow;
                double[] p = nearFront(fr, eye, 1500);
                if (p != null) { s.event(Soundscape.Kind.AECHZEN, p[0], level + 20 + 30 * rnd.nextDouble(), p[1], 0.3 + 0.7 * rnd.nextDouble(), 0); groans++; }
            }
            if (crackIn <= 0) {
                crackIn = -Math.log(1 - rnd.nextDouble()) * crackEvery * slow;
                double[] p = nearFront(fr, eye, 2500);
                if (p != null) { s.event(Soundscape.Kind.KNACKEN, p[0], level + 10 + 60 * rnd.nextDouble(), p[1], 0.1 + 0.5 * rnd.nextDouble() * rnd.nextDouble(), 0); cracks++; }
            }
        }
    }

    /** Ein Punkt der Front, gewichtet nach Nähe zum Ohr (höchstens r m), oder null. */
    private double[] nearFront(double[][] fr, double[] eye, double r) {
        for (int k = 0; k < 12; k++) {
            double[] p = fr[rnd.nextInt(fr.length)];
            double d = Math.hypot(p[0] - eye[0], p[1] - eye[2]);
            if (d < r && rnd.nextDouble() < 1.2 - d / r) return p;
        }
        return null;
    }

    private void dispatch(Kind k, double x, double y, double z, double st) {
        switch (k) {
            case KNACKEN -> { s.event(Soundscape.Kind.KNACKEN, x, y, z, st, 0); cracks++; }
            case AECHZEN -> { s.event(Soundscape.Kind.AECHZEN, x, y, z, st, 0); groans++; }
            case AUFSCHLAG -> {
                // st = Volumen (m³): sammeln, der größte zählt
                if (st > impactVol) { impactX = x; impactY = y; impactZ = z; }
                impactVol += st;
            }
            case AUFLAUFEN -> {
                double d = Math.hypot(x - s.earX, z - s.earZ);
                if (d > 700 || runupClock > 0) return;
                runupClock = 0.35;
                s.event(Soundscape.Kind.AUFLAUFEN, x, y, z, Math.min(1, st / 2), 0);
                runups++;
            }
            case MOEWE -> s.event(Soundscape.Kind.MOEWE, x, y, z, st);
            case KONDOR -> s.event(Soundscape.Kind.KONDOR, x, y, z, st, 0);
            case DONNER -> {
                double d = Math.max(50, Math.hypot(x - s.earX, z - s.earZ));
                s.thunder(x, y, z, 128 - 20 * Math.log10(d) * 0.9, null, null, null, false);
            }
            case KALBUNG -> {
                // ohne Weg (etwa eine Probe): ein senkrechter Riss von 60 m an der Stelle
                double[][] pts = new double[12][];
                for (int i = 0; i < 12; i++) pts[i] = new double[]{x, y + i * 5, z};
                s.calving(pts, st);
                calvingsDone++;
            }
            case MOTOR, RISS -> { }
        }
    }

    /** Der Katamaran: Ort, Fahrt (m/s), ob das Ohr an Bord ist. Drehzahl 700 (Leerlauf) bis 1 900 bei 7 m/s. */
    public void boat(double x, double y, double z, double speed, boolean aboard) {
        diesel.x = x;
        diesel.y = y;
        diesel.z = z;
        diesel.aboard = aboard;
        diesel.firingHz = Hum.firing(700 + 1200 * Math.min(1, Math.abs(speed) / 7), 6);
        diesel.levelDb = 100 + 6 * Math.min(1, Math.abs(speed) / 7);
    }

    public String describe() {
        return String.format(Locale.GERMAN, "Klang: %s · %d Ereignisse: %d Knacken, %d Ächzen, %d Kalbungsdonner, %d Aufschläge, %d Auflaufen · %s",
                s.quality, events, cracks, groans, calvingsDone, impacts, runups, s.lastVoice())
                + (flut.flow > 0.5 ? String.format(Locale.GERMAN, " · Ruptura tost: %,.0f m³/s, %.0f dB in 1 m", flut.flow, com.dan.soundscape.Torrent.levelDb(flut.flow)) : "");
    }

    /** Punkte der Front alle 50 m (für {@link #front}). */
    public static double[][] frontPoints(List<double[]> lines) {
        java.util.ArrayList<double[]> out = new java.util.ArrayList<>();
        for (double[] p : lines)
            for (int k = 2; k < p.length; k += 2) {
                double l = Math.hypot(p[k] - p[k - 2], p[k + 1] - p[k - 1]);
                for (double t = 0; t < l; t += 50) out.add(new double[]{p[k - 2] + (p[k] - p[k - 2]) * t / l, p[k - 1] + (p[k + 1] - p[k - 1]) * t / l});
            }
        return out.toArray(new double[0][]);
    }
}
