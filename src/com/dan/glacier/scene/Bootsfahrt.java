package com.dan.glacier.scene;

import com.dan.buoyancy.Catamaran;
import com.dan.ice.Glacier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 10: die Route des Katamarans. Vom Anleger hinaus, dann vor der Front entlang in rund 300 m Abstand (näher fahren
 * die Ausflugsboote nicht heran [G]), langsam, damit die Leute schauen können; am Ende weiter draußen zurück und wieder
 * an den Anleger. Jeder Punkt liegt über mindestens 12 m Wasser und nicht im Eis.
 */
public final class Bootsfahrt {
    private Bootsfahrt() { }

    /** Fahrt (m/s): hinaus und zurück, vor der Front, am Anleger. */
    public static final double CRUISE = 6.0, VIEW = 3.2, HARBOUR = 1.8;
    /** Abstand zur Front (m) und Länge der Strecke davor (m). */
    public static final double OFF = 300, RUN = 1200, MIN_DEPTH = 12;

    /** Wasser genug und kein Eis? */
    static boolean free(Terrain t, Glacier g, double level, double x, double z, double depth) {
        return !g.covers(x, z) && !t.ice(x, z) && t.height(x, z) < level - depth;
    }

    /**
     * Die Route: {x, z, Fahrt} je Wegpunkt. {@code front} ist die Front im Blick {x, z, nx, nz} (aus {@link Welle#front}),
     * der Liegeplatz {x, z, Kurs} und das Ende des Anlegers {x, z} aus {@link Stege}.
     */
    public static List<double[]> route(Terrain t, Glacier g, double level, double[] front, double[] berth, double[] pierEnd) {
        List<double[]> r = new ArrayList<>();
        r.add(new double[]{berth[0], berth[1], HARBOUR});
        // vom Liegeplatz rückwärts weg geht nicht: erst ein Stück längs des Anlegers hinaus, dann drehen
        double ox = berth[0] - pierEnd[0], oz = berth[1] - pierEnd[1], ol = Math.max(1e-6, Math.hypot(ox, oz));
        double hx = Math.sin(Math.toRadians(berth[2] + 180)), hz = -Math.cos(Math.toRadians(berth[2] + 180));
        r.add(new double[]{berth[0] + hx * 35 + ox / ol * 15, berth[1] + hz * 35 + oz / ol * 15, HARBOUR});
        // die Strecke vor der Front: in beide Richtungen probieren, die längere nehmen
        List<double[]> best = null;
        for (int dir = -1; dir <= 1; dir += 2) {
            List<double[]> run = new ArrayList<>();
            double tx = -front[3] * dir, tz = front[2] * dir;
            for (double s = -150; s <= RUN; s += 150) {
                double[] q = g.nearestFront(front[0] + tx * s, front[1] + tz * s, 700);
                if (q == null) break;
                double nl = Math.hypot(q[2], q[3]), nx = q[2] / nl, nz = q[3] / nl;
                double[] pt = null;
                for (double off : new double[]{OFF, 260, 340, 220, 380, 180, 420}) {
                    double x = q[0] + nx * off, z = q[1] + nz * off;
                    if (free(t, g, level, x, z, MIN_DEPTH) && free(t, g, level, x - nx * 60, z - nz * 60, MIN_DEPTH)) { pt = new double[]{x, z, VIEW}; break; }
                }
                if (pt == null) { if (!run.isEmpty()) break; else continue; }
                run.add(pt);
            }
            if (best == null || run.size() > best.size()) best = run;
        }
        if (best != null && !best.isEmpty()) {
            best.get(0)[2] = CRUISE;   // hin zum Anfang der Strecke schnell
            r.addAll(best);
            // zurück weiter draußen: dieselbe Strecke rückwärts, 150 m weiter von der Front (wo das Wasser reicht)
            for (int i = best.size() - 1; i >= 0; i -= 2) {
                double[] p = best.get(i);
                double[] q = g.nearestFront(p[0], p[1], 900);
                if (q == null) continue;
                double nl = Math.hypot(q[2], q[3]), nx = q[2] / nl, nz = q[3] / nl;
                for (double extra : new double[]{150, 100, 50}) {
                    double x = p[0] + nx * extra, z = p[1] + nz * extra;
                    if (free(t, g, level, x, z, MIN_DEPTH)) { r.add(new double[]{x, z, CRUISE}); break; }
                }
            }
        }
        r.add(new double[]{pierEnd[0] + hx * 45 + ox / ol * 15, pierEnd[1] + hz * 45 + oz / ol * 15, CRUISE});
        r.add(new double[]{berth[0] + hx * 12, berth[1] + hz * 12, HARBOUR});
        r.add(new double[]{berth[0], berth[1], HARBOUR * 0.6});
        return r;
    }

    /** Länge der Route (m) und Dauer bei den Fahrten der Punkte (s). */
    public static double[] lengthAndTime(List<double[]> r) {
        double l = 0, s = 0;
        for (int i = 1; i < r.size(); i++) {
            double d = Math.hypot(r.get(i)[0] - r.get(i - 1)[0], r.get(i)[1] - r.get(i - 1)[1]);
            l += d;
            s += d / Math.max(0.3, r.get(i)[2]);
        }
        return new double[]{l, s};
    }

    /** Ein Katamaran auf dieser Route. */
    public static Catamaran boat(List<double[]> r) {
        Catamaran c = new Catamaran("Katamaran");
        for (double[] p : r) c.via(p[0], p[1], p[2]);
        return c;
    }

    /** Ob jeder Abschnitt der Route (alle 10 m) über Wasser von mindestens d m liegt. */
    public static double shallowest(Terrain t, Glacier g, double level, List<double[]> r) {
        double m = 1e9;
        for (int i = 1; i < r.size(); i++) {
            double[] a = r.get(i - 1), b = r.get(i);
            double l = Math.hypot(b[0] - a[0], b[1] - a[1]);
            for (double s = 0; s <= l; s += 10) {
                double x = a[0] + (b[0] - a[0]) * s / l, z = a[1] + (b[1] - a[1]) * s / l;
                m = Math.min(m, g.covers(x, z) ? -100 : level - t.height(x, z));
            }
        }
        return m;
    }

    public static String describe(List<double[]> r) {
        double[] lt = lengthAndTime(r);
        return String.format(Locale.GERMAN, "Bootsfahrt: %d Wegpunkte, %,.0f m, %.0f min", r.size(), lt[0], lt[1] / 60);
    }
}
