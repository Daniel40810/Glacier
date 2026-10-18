package com.dan.glacier.scene;

import com.dan.ice.Glacier;
import com.dan.wave.Wave;

/**
 * Phase 8: wo die Welle aus {@code com.dan.wave} am Perito Moreno liegt. Das feine Gitter (2 m) gehört zwischen den
 * Steg und die Front mit dem tiefsten Wasser davor – die Stelle der Front, die dem Steg am nächsten ist, liegt in einem
 * flachen Winkel am Ufer. Das grobe Gitter (8 m, gut 8 km) liegt um dieselbe Mitte und reicht über beide Seearme.
 * Der Grund ist das Gelände unter dem See, die Gletscherfront ist eine Wand.
 */
public final class Welle {
    private Welle() { }

    /** Ruhetiefe für die Welle: Seespiegel minus Gelände; unter dem Gletscher eine Wand (NaN). */
    public static Wave.Bed bed(Terrain t, Glacier g, double level) {
        return (x, z) -> g.covers(x, z) ? Double.NaN : level - t.height(x, z);
    }

    /**
     * Die Front im Blick des Stegs mit tiefem Wasser davor: die nächste Stelle (höchstens 900 m vom Steg), vor der
     * es 40 m vor der Wand mindestens 40 m tief ist, sonst die tiefste. {x, z, nx, nz} (Normale zum Wasser, Länge 1);
     * null ohne Front.
     */
    public static double[] front(Terrain t, Glacier g, double level) {
        double[] st = t.steg, best = null, near = null;
        double deepest = -1, nearest = Double.MAX_VALUE;
        for (double[] line : g.frontLines())
            for (int k = 0; k + 1 < line.length; k += 2) {
                double x = line[k], z = line[k + 1];
                if (Math.hypot(x - st[0], z - st[1]) > 900) continue;
                double[] n = g.nearestFront(x, z, 5);
                if (n == null) continue;
                double l = Math.hypot(n[2], n[3]), dep = level - t.height(x + n[2] / l * 40, z + n[3] / l * 40);
                double d = Math.hypot(x - st[0], z - st[1]);
                if (dep > deepest) { deepest = dep; best = new double[]{x, z, n[2] / l, n[3] / l}; }
                if (dep >= 40 && d < nearest) { nearest = d; near = new double[]{x, z, n[2] / l, n[3] / l}; }
            }
        return near != null ? near : best;
    }

    /** Mitte der Gitter: zwischen Steg und Front, 100 m zum Wasser hin. */
    public static double[] center(Terrain t, double[] front) {
        double[] st = t.steg;
        if (front == null) return new double[]{st[0], st[1]};
        return new double[]{(st[0] + front[0]) / 2 + front[2] * 100, (st[1] + front[1]) / 2 + front[3] * 100};
    }

    /**
     * Der Pegel: die Stelle mit mindestens 3 m Wasser, die dem Steg am nächsten ist und mit dem See verbunden
     * (Befund Status_Phase8: die nächste Stelle lag in einem Tümpel hinter dem Ufer, die Welle kam dort nie an).
     * Gefüllt wird auf einem Raster von 4 m bis 700 m um den Steg, ausgehend vom tiefen Wasser vor der Front.
     */
    public static double[] gauge(Terrain t, Wave w, double[] front) {
        double[] st = t.steg;
        double step = 4, half = 700;
        int n = (int) (2 * half / step);
        double x0 = st[0] - half, z0 = st[1] - half;
        boolean[] seen = new boolean[n * n];
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        if (front != null) {
            int i = (int) ((front[0] + front[2] * 40 - x0) / step), j = (int) ((front[1] + front[3] * 40 - z0) / step);
            if (i >= 0 && j >= 0 && i < n && j < n) { seen[j * n + i] = true; q.add(new int[]{i, j}); }
        }
        double gx = st[0], gz = st[1], gd = Double.MAX_VALUE;
        while (!q.isEmpty()) {
            int[] c = q.poll();
            double x = x0 + (c[0] + 0.5) * step, z = z0 + (c[1] + 0.5) * step;
            double d = Math.hypot(x - st[0], z - st[1]);
            if (d < gd && w.depthAt(x, z) > 3) { gd = d; gx = x; gz = z; }
            int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] o : nb) {
                int i = c[0] + o[0], j = c[1] + o[1];
                if (i < 0 || j < 0 || i >= n || j >= n || seen[j * n + i]) continue;
                seen[j * n + i] = true;
                double dep = w.depthAt(x0 + (i + 0.5) * step, z0 + (j + 0.5) * step);
                if (dep > 0.5) q.add(new int[]{i, j});
            }
        }
        if (gd == Double.MAX_VALUE) return gaugeNearest(t, w);
        return new double[]{gx, gz};
    }

    /** Die Stelle mit 3 m Wasser, die dem Steg am nächsten ist, ohne Rücksicht darauf, ob sie mit dem See verbunden ist. */
    static double[] gaugeNearest(Terrain t, Wave w) {
        double[] st = t.steg;
        double gx = st[0], gz = st[1], gd = Double.MAX_VALUE;
        for (double x = st[0] - 400; x <= st[0] + 400; x += 4)
            for (double z = st[1] - 400; z <= st[1] + 400; z += 4) {
                double d = Math.hypot(x - st[0], z - st[1]);
                if (d < gd && w.depthAt(x, z) > 3) { gd = d; gx = x; gz = z; }
            }
        return new double[]{gx, gz};
    }

    /** Wo ein Einschlag an dieser Front das Wasser trifft: 15 m vor der Wand. */
    public static double[] impact(double[] front) { return new double[]{front[0] + front[2] * 15, front[1] + front[3] * 15}; }
}
