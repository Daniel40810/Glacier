package com.dan.glacier.scene;

import com.dan.ice.Glacier;
import com.dan.river.FlowField;
import com.dan.river.RiverPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 5: der Perito Moreno als Körper aus {@code com.dan.ice}, gebaut aus dem Gelände (GLO-30 und dem modellierten
 * Grund): das Raster um das Eis mit 600 m Rand, Front auf 74 m (Site.FRONT_HEIGHT), Fließen an der Front 2 m/Tag [G].
 * Dazu die Schmelzbäche: Wasser, das auf der Zunge das Gefälle hinab läuft, sich eine Rinne schneidet und in einer
 * Spalte (Gletschermühle) oder am Rand verschwindet; ihre Strömung rechnet {@code com.dan.river}.
 */
public final class Gletscher {
    /** Geschwindigkeit in der Mitte an der Front (m/Tag) [G, Werkbuch Abschnitt 2]. */
    public static final double SPEED = 2.0;
    public final Glacier glacier;
    public final List<Bach> baeche = new ArrayList<>();
    public final double buildMs;

    /** Ein Schmelzbach: Linie, halbe Breite, Wasserspiegel je Punkt, Strömung (m/s) und Gefälle je Punkt. */
    public static final class Bach {
        public final double[] line;
        public final float[] level, speed, slope;
        public final double halfWidth;
        public final FlowField flow;
        Bach(double[] line, double hw, float[] level, float[] speed, float[] slope, FlowField flow) {
            this.line = line; this.halfWidth = hw; this.level = level; this.speed = speed; this.slope = slope; this.flow = flow;
        }
        public double length() { return (line.length / 2 - 1) * STEP; }
    }

    static final double STEP = 4;

    public Gletscher(Terrain t) {
        long t0 = System.nanoTime();
        double xa = -7000, xb = 900, za = -1900, zb = 5100;
        int i0 = (int) Math.floor((xa - Terrain.X0) / Terrain.CELL), i1 = (int) Math.ceil((xb - Terrain.X0) / Terrain.CELL);
        int j0 = (int) Math.floor((za - Terrain.Z0) / Terrain.CELL), j1 = (int) Math.ceil((zb - Terrain.Z0) / Terrain.CELL);
        i0 = Math.max(0, i0); j0 = Math.max(0, j0); i1 = Math.min(Terrain.N, i1); j1 = Math.min(Terrain.N, j1);
        int nx = i1 - i0, nz = j1 - j0;
        float[] h = new float[nx * nz], bed = new float[nx * nz];
        byte[] kind = new byte[nx * nz];
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int q = j * nx + i;
                h[q] = (float) t.at(i + i0, j + j0);
                bed[q] = (float) t.bedAt(i + i0, j + j0);
                byte s = t.src[(j + j0) * Terrain.N + i + i0];
                kind[q] = s == Terrain.EIS ? Glacier.ICE : s == Terrain.SEE ? Glacier.WATER : Glacier.ROCK;
            }
        glacier = new Glacier(new Glacier.Grid(nx, nz, Terrain.X0 + i0 * Terrain.CELL, Terrain.Z0 + j0 * Terrain.CELL, Terrain.CELL, h, bed, kind),
                0, Site.FRONT_HEIGHT, SPEED, 2026);
        baeche();
        buildMs = (System.nanoTime() - t0) / 1e6;
    }

    /**
     * Schmelzbäche: Quellen auf ruhigen Stellen der Zunge (wenig Spalten, mindestens 250 m vom Rand), je 4 m das
     * Gefälle hinab; was kürzer als 300 m bleibt, verschwindet gleich in einer Spalte und wird nicht gezeichnet [G].
     */
    private void baeche() {
        Glacier g = glacier;
        double[][] seeds = {{-5200, 2200}, {-4600, 1700}, {-4000, 1300}, {-3500, 900}, {-3000, 1500}, {-2600, 700}, {-5600, 3000}, {-2200, 1100}};
        for (double[] s : seeds) {
            if (!g.covers(s[0], s[1]) || g.distMargin(s[0], s[1]) < 250 || g.openness(s[0], s[1]) > 0.35) continue;
            double[] p = g.downhill(s[0], s[1], STEP, 2500);
            // in einer offenen Spalte endet der Bach (Gletschermühle)
            int end = p.length / 2;
            for (int k = 0; k < p.length / 2; k++)
                if (g.openness(p[k * 2], p[k * 2 + 1]) > 0.6) { end = k; break; }
            if (end * STEP < 300) continue;
            double[] line = java.util.Arrays.copyOf(p, end * 2);
            double hw = 1.2 + 0.4 * (baeche.size() % 3);
            g.addChannel(line, hw, 1.4);
            int n = line.length / 2;
            float[] lv = new float[n], sp = new float[n], sl = new float[n];
            double[][] nodes = new double[n][];
            for (int k = 0; k < n; k++) {
                lv[k] = (float) (g.surface(line[k * 2], line[k * 2 + 1]) + 0.35);
                nodes[k] = new double[]{line[k * 2], line[k * 2 + 1], lv[k], hw};
            }
            // Spiegel darf nicht steigen (das Wasser läuft bergab): nach vorn auf das Minimum klemmen
            for (int k = 1; k < n; k++) { lv[k] = Math.min(lv[k], lv[k - 1]); nodes[k][2] = lv[k]; }
            RiverPath rp = new RiverPath(nodes, 2.5, hw * 3);
            FlowField ff = new FlowField(rp);
            ff.depth = 0.35f;
            ff.manning = 0.012f;   // glattes Eis
            FlowField.Flow f = new FlowField.Flow();
            for (int k = 0; k < n; k++) {
                sp[k] = Math.max(0.3f, Math.min(3.5f, ff.sample(line[k * 2], line[k * 2 + 1], f) && f.wet ? f.speed : 0.5f));
                int a = Math.max(0, k - 2), b = Math.min(n - 1, k + 2);
                sl[k] = (float) Math.max(0, (lv[a] - lv[b]) / Math.max(1, (b - a) * STEP));
            }
            baeche.add(new Bach(line, hw, lv, sp, sl, ff));
        }
    }

    /** „8 Schmelzbäche, 9,4 km, 0,8 bis 2,6 m/s“ */
    public String describeBaeche() {
        double len = 0, lo = 1e9, hi = 0;
        for (Bach b : baeche) {
            len += b.length();
            for (float s : b.speed) { lo = Math.min(lo, s); hi = Math.max(hi, s); }
        }
        return baeche.isEmpty() ? "keine Schmelzbäche" : String.format(Locale.GERMAN, "%d Schmelzbäche, %.1f km, %.1f bis %.1f m/s", baeche.size(), len / 1000, lo, hi);
    }

    /**
     * Band der Bäche für die Grafikkarte: je Punkt zwei Ecken (links, rechts) mit {x, y, z, s, quer, Geschwindigkeit,
     * Gefälle, Richtung x, Richtung z} (9 Werte), dazu die Dreiecke.
     */
    public Object[] ribbon() {
        List<float[]> v = new ArrayList<>();
        List<int[]> tr = new ArrayList<>();
        for (Bach b : baeche) {
            int n = b.line.length / 2, base = v.size();
            double[] p = b.line;
            for (int k = 0; k < n; k++) {
                int a = Math.max(0, k - 1), c = Math.min(n - 1, k + 1);
                double tx = p[c * 2] - p[a * 2], tz = p[c * 2 + 1] - p[a * 2 + 1], l = Math.max(1e-6, Math.hypot(tx, tz));
                tx /= l;
                tz /= l;
                for (int sd = -1; sd <= 1; sd += 2) {
                    double x = p[k * 2] - tz * b.halfWidth * sd, z = p[k * 2 + 1] + tx * b.halfWidth * sd;
                    v.add(new float[]{(float) x, b.level[k], (float) z, (float) (k * STEP), sd, b.speed[k], b.slope[k], (float) tx, (float) tz});
                }
                if (k > 0) {
                    int i0 = base + (k - 1) * 2, i1 = base + k * 2;
                    tr.add(new int[]{i0, i0 + 1, i1});
                    tr.add(new int[]{i1, i0 + 1, i1 + 1});
                }
            }
        }
        float[] vb = new float[v.size() * 9];
        for (int i = 0; i < v.size(); i++) System.arraycopy(v.get(i), 0, vb, i * 9, 9);
        int[] ib = new int[tr.size() * 3];
        for (int i = 0; i < tr.size(); i++) System.arraycopy(tr.get(i), 0, ib, i * 3, 3);
        return new Object[]{vb, ib};
    }

    /** Die Fahrt „Über die Zunge“: von weit oben auf dem Eis längs der Mitte zur Front, 70 m über dem Eis, am Ende über der Kante. */
    public double[][] zungenWeg() {
        Glacier g = glacier;
        List<double[]> pts = new ArrayList<>();
        for (double x = -6600; x <= 200; x += 150) {
            // Mitte quer zum Tal: dort, wo der Rand am weitesten weg ist
            double best = -1, bz = 0;
            for (double z = -1800; z <= 5000; z += 25) {
                if (!g.covers(x, z)) continue;
                double d = g.distMargin(x, z);
                if (d > best) { best = d; bz = z; }
            }
            if (best < 0) continue;
            if (!pts.isEmpty() && Math.abs(bz - pts.get(pts.size() - 1)[2]) > 600) continue;
            pts.add(new double[]{x, 0, bz});
        }
        // glätten, Höhe über der glatten Oberfläche, bis zur Front
        int n = pts.size();
        double[][] r = new double[n][];
        for (int k = 0; k < n; k++) {
            double sx = 0, sz = 0;
            int c = 0;
            for (int q = Math.max(0, k - 3); q <= Math.min(n - 1, k + 3); q++) { sx += pts.get(q)[0]; sz += pts.get(q)[2]; c++; }
            double x = sx / c, z = sz / c;
            double base = g.covers(x, z) ? g.base(x, z) : 0;
            r[k] = new double[]{x, base + 70, z};
        }
        // über die Kante hinaus: 400 m weiter in Fahrtrichtung über dem Wasser, langsam sinkend
        if (n >= 2) {
            double dx = r[n - 1][0] - r[n - 2][0], dz = r[n - 1][2] - r[n - 2][2], l = Math.max(1e-6, Math.hypot(dx, dz));
            double[][] e = java.util.Arrays.copyOf(r, n + 2);
            e[n] = new double[]{r[n - 1][0] + dx / l * 200, r[n - 1][1] - 20, r[n - 1][2] + dz / l * 200};
            e[n + 1] = new double[]{r[n - 1][0] + dx / l * 400, Math.max(40, r[n - 1][1] - 50), r[n - 1][2] + dz / l * 400};
            return e;
        }
        return r;
    }

    /**
     * Phase 14: der Weg der Fahrt „Durch die Spalte“: aus 25 m über dem Eis hinab in eine tiefe Spalte nahe der Front,
     * in halber Tiefe ihr entlang (die Mitte wird alle 3 m neu gesucht: dort, wo sie am tiefsten ist), am Ende wieder
     * hinauf über die Türme. Gesucht wird die längste Spalte, die mindestens 14 m tief ist, auf einem Raster von 40 m
     * zwischen 150 und 1 500 m hinter der Front, mit gleichem Seed gleich. Punkte {x, Augenhöhe, z}; null, wenn keine
     * Spalte lang genug ist.
     */
    public synchronized double[][] spaltenWeg() {
        if (spaltenWeg == null) spaltenWeg = sucheSpalte();
        return spaltenWeg.length == 0 ? null : spaltenWeg;
    }

    private double[][] spaltenWeg;

    private double[][] sucheSpalte() {
        Glacier g = glacier;
        double[] bounds = g.bounds();
        List<double[]> best = null;
        search:
        for (double x = bounds[0]; x <= bounds[2]; x += 40)
            for (double z = bounds[1]; z <= bounds[3]; z += 40) {
                if (best != null && best.size() >= 80) break search;   // lang genug: nicht weiter suchen (die Suche kostet Sekunden)
                double df = g.distFront(x, z);
                if (df < 150 || df > 1500 || !g.covers(x, z) || g.openness(x, z) < 0.55) continue;
                // nächste tiefe Stelle quer zur Spalte suchen
                double dirDeg = g.strain(x, z)[2] + 90, ax = Math.cos(Math.toRadians(dirDeg)), az = Math.sin(Math.toRadians(dirDeg));
                double[] c = deepest(x, z, -az, ax, 14);
                if (c == null || depthAt(c[0], c[1]) < 14) continue;
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    List<double[]> tr = trace(c[0], c[1], ax * sgn, az * sgn);
                    if (tr.size() >= 40 && (best == null || tr.size() > best.size())) best = tr;
                }
            }
        if (best == null) return new double[0][];
        int n = Math.min(best.size(), 110);   // höchstens 330 m in der Spalte
        List<double[]> pts = new ArrayList<>();
        double[] a0 = best.get(0), a1 = best.get(Math.min(n - 1, 4));
        double dx = a1[0] - a0[0], dz = a1[1] - a0[1], l = Math.max(1e-6, Math.hypot(dx, dz));
        dx /= l;
        dz /= l;
        // Anflug: 60 m davor in 25 m über dem Eis, hinab bis an die Kante
        for (int k = 4; k >= 1; k--) {
            double px = a0[0] - dx * 15 * k, pz = a0[1] - dz * 15 * k;
            double top = g.covers(px, pz) ? g.base(px, pz) : a0[2];
            pts.add(new double[]{px, top + 6 + 19 * (k - 1) / 3.0, pz});
        }
        for (int k = 0; k < n; k++) {
            double[] p = best.get(k);
            // die ersten 30 m sinkt das Auge von der Kante in die Spalte (sonst stürzt es auf 3 m 20 m hinab)
            double u = Math.min(1, k / 10.0), y = (p[2] + 4) + (p[3] - (p[2] + 4)) * u * u * (3 - 2 * u);
            pts.add(new double[]{p[0], y, p[1]});
        }
        // hinauf: 60 m weiter bis 30 m über die Türme
        double[] e0 = best.get(n - 1), e1 = best.get(Math.max(0, n - 5));
        double ex = e0[0] - e1[0], ez = e0[1] - e1[1], el = Math.max(1e-6, Math.hypot(ex, ez));
        for (int k = 1; k <= 4; k++) {
            double px = e0[0] + ex / el * 15 * k, pz = e0[1] + ez / el * 15 * k;
            double top = g.covers(px, pz) ? g.base(px, pz) : e0[2];
            pts.add(new double[]{px, top + 7.5 * k, pz});
        }
        // die Augenhöhe glätten (die Mitte springt von Stütze zu Stütze)
        double[][] r = pts.toArray(new double[0][]);
        double[] y = new double[r.length];
        for (int k = 0; k < r.length; k++) {
            double s = 0;
            int cnt = 0;
            for (int q = Math.max(0, k - 2); q <= Math.min(r.length - 1, k + 2); q++) { s += r[q][1]; cnt++; }
            y[k] = s / cnt;
        }
        for (int k = 0; k < r.length; k++) r[k][1] = y[k];
        return r;
    }

    /** Tiefe der Spalte an (x, z): Oberfläche ohne Spalten minus mit (m). */
    double depthAt(double x, double z) { return glacier.base(x, z) - glacier.surface(x, z); }

    /** Die tiefste Stelle auf der Linie quer (nx, nz) um (x, z), höchstens r m weit: {x, z}; null ohne Eis. */
    private double[] deepest(double x, double z, double nx, double nz, double r) {
        double bx = Double.NaN, bz = 0, bd = -1e9;
        for (double d = -r; d <= r; d += 0.5) {
            double px = x + nx * d, pz = z + nz * d;
            if (!glacier.covers(px, pz)) continue;
            double dd = depthAt(px, pz);
            if (dd > bd) { bd = dd; bx = px; bz = pz; }
        }
        return Double.isNaN(bx) ? null : new double[]{bx, bz};
    }

    /**
     * Die Spalte entlang in Schritten von 3 m: je Schritt quer die tiefste Stelle (±3 m), die Richtung folgt der Mitte
     * träge; Ende, wo sie flacher als 12 m wird, enger als 2 m, oder mehr als 30° abbiegt. Je Punkt {x, z, Oberkante, Augenhöhe}: die Augen in 45 % der Tiefe über dem Grund.
     */
    private List<double[]> trace(double x, double z, double dx, double dz) {
        List<double[]> out = new ArrayList<>();
        double sx = dx, sz = dz;
        for (int k = 0; k < 400; k++) {
            double[] c = deepest(x, z, -dz, dx, 3);
            // nur eine Spalte, die fast gerade läuft: mehr als 30° vom Anfang weg oder 2,5 m Sprung quer beendet sie
            if (c == null || Math.hypot(c[0] - x, c[1] - z) > 2.5 || dx * sx + dz * sz < Math.cos(Math.toRadians(30))) break;
            if (c == null) break;
            double dep = depthAt(c[0], c[1]);
            if (dep < 12) break;
            double bottom = glacier.surface(c[0], c[1]), rim = glacier.base(c[0], c[1]);
            // Wände: quer in Augenhöhe mindestens 1,5 m frei
            double eye = bottom + 0.45 * (rim - bottom);
            if (clearance(c[0], c[1], -dz, dx, eye) < 2) break;
            out.add(new double[]{c[0], c[1], rim, eye});
            if (k > 0) {
                double[] prev = out.get(out.size() - 2);
                double mx = c[0] - prev[0], mz = c[1] - prev[1], ml = Math.hypot(mx, mz);
                if (ml > 1e-6) { dx = 0.85 * dx + 0.15 * mx / ml; dz = 0.85 * dz + 0.15 * mz / ml; double n = Math.hypot(dx, dz); dx /= n; dz /= n; }
            }
            x = c[0] + dx * 3;
            z = c[1] + dz * 3;
        }
        return out;
    }

    /** Freier Raum quer zur Spalte in Höhe y (m, kleinere Seite): bis wohin die Oberfläche unter y bleibt. */
    double clearance(double x, double z, double nx, double nz, double y) {
        double min = 1e9;
        for (int sgn = -1; sgn <= 1; sgn += 2)
            for (double d = 0.25; d <= 8; d += 0.25)
                if (glacier.surface(x + nx * d * sgn, z + nz * d * sgn) > y) { min = Math.min(min, d); break; }
        return min;
    }
}
