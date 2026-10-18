package com.dan.glacier.scene;

import java.util.Locale;

/**
 * Phase 2: wo am Perito Moreno was wächst, als Karten zu 25 m über das ganze Gelände [G] (Werkbuch, Abschnitt 2):
 * <ul>
 * <li><b>Wald</b> aus Südbuchen vom Ufer bis rund 700 m über dem See (≈ 880 m ü. M.; die Baumgrenze liegt hier um
 * 1 000 m), nicht auf steilem Fels, in Flecken; dicht am Eis nur lichter Jungwuchs auf dem frischen Schutt.
 * <b>Lenga</b> ist der Waldbaum; <b>Guindo</b> (immergrün) wächst unten am feuchten, schattigen Hang (auf der
 * Südhalbkugel sind das die Südhänge) und nahe am Eis; <b>Ñire</b> an den lichten, trockeneren Rändern nach Osten.</li>
 * <li><b>Steppe</b> (Horstgras) auf den offenen, flachen Hängen, nach Osten hin mehr (Regenschatten der Anden).</li>
 * <li><b>Gras</b> und Seggen im Uferstreifen.</li>
 * <li><b>Moräne</b>: der frische Schutt in den ersten gut 100 m neben dem Eis, wo kein Wald steht; <b>Fels</b>: steile Flanken (über 40°).</li>
 * </ul>
 * Die Karten gehen an {@code com.dan.forest} (Dichte je Art), {@code com.dan.reed} (Dichte je Art),
 * {@code com.dan.ground} (Bedeckung) und als Textur ans Gelände (Waldboden, Kronendach in der Ferne, Steppe, Moräne).
 */
public final class Vegetation implements com.dan.forest.ForestField.Density, com.dan.reed.Reed.Density {
    /** Kartenzellen je Seite und Zellgröße (m), gleiche Ausdehnung wie das Gelände. */
    public static final int M = 512;
    public static final double CELL = Terrain.N * Terrain.CELL / M;
    /** Arten des Waldes in der Reihenfolge der Dichte: 0 Lenga, 1 Ñire, 2 Guindo. */
    public static final int LENGA = 0, NIRE = 1, GUINDO = 2;
    /** Obergrenze des Waldes über dem See (m) und Breite des Übergangs [G]. */
    public static final double TREELINE = 700, TREELINE_FADE = 150;

    public final byte[] lenga = new byte[M * M], nire = new byte[M * M], guindo = new byte[M * M], steppe = new byte[M * M],
            grass = new byte[M * M], moraine = new byte[M * M], rock = new byte[M * M];
    private final Terrain t;

    public Vegetation(Terrain t) {
        this.t = t;
        build();
    }

    public static java.util.List<com.dan.forest.Species> species() {
        return java.util.List.of(com.dan.forest.Species.lenga(), com.dan.forest.Species.nire(), com.dan.forest.Species.guindo());
    }

    private void build() {
        boolean[] ice = new boolean[M * M], water = new boolean[M * M];
        double[] y = new double[M * M];
        for (int j = 0; j < M; j++)
            for (int i = 0; i < M; i++) {
                double x = Terrain.X0 + (i + 0.5) * CELL, z = Terrain.Z0 + (j + 0.5) * CELL;
                int k = j * M + i;
                byte s = t.sourceAt(x, z);
                ice[k] = s == Terrain.EIS;
                water[k] = s == Terrain.SEE;
                y[k] = t.height(x, z);
            }
        double[] dIce = distance(ice), dWater = distance(water);
        views = viewLanes(ice, water);
        // Phase 13: wo in manchen Jahren der Eisdamm der Ruptura liegt, wächst nichts (das Eis schleift den Spülsaum)
        com.dan.ice.IceDam dam = t.measured() ? Ruptura.damm(t, t::ice, 13) : null;
        for (int j = 0; j < M; j++)
            for (int i = 0; i < M; i++) {
                int k = j * M + i;
                if (ice[k] || water[k]) continue;
                double x = Terrain.X0 + (i + 0.5) * CELL, z = Terrain.Z0 + (j + 0.5) * CELL;
                double[] n = t.normal(x, z);
                double slope = Math.sqrt(Math.max(0, 1 - n[1] * n[1])) / Math.max(0.05, n[1]);
                double h = y[k], di = dIce[k] * CELL, dw = dWater[k] * CELL;
                double patch = fbm(x / 420.0 + 3.1, z / 420.0 - 7.7), patch2 = fbm(x / 140.0 - 1.3, z / 140.0 + 2.9);
                double steep = smooth(0.65, 0.95, slope);
                double rockCov = Math.max(steep, smooth(1150, 1350, h) * 0.8);
                // Wald
                double f = smooth(TREELINE + TREELINE_FADE * 0.5, TREELINE - TREELINE_FADE * 0.5, h) * smooth(1.05, 0.7, slope) * smooth(1.5, 4, h)
                        * smooth(0.28, 0.5, patch * 0.7 + patch2 * 0.3);
                f *= 0.25 + 0.75 * smooth(20, 120, di);                       // lichter Jungwuchs auf frischem Schutt
                f *= 1 - stegClearing(x, z);                                    // Lichtung am Steg, Schneise zur Front
                f *= 1 - viewClearing(x, z);                                    // Phase 15: Lichtung an Südflanke und Brazo Rico
                boolean scoured = dam != null && dam.inBox(x, z) && dam.insideXZ(x, z);
                if (scoured) f = 0;
                double south = Math.max(0, n[2]);                               // Hang nach Süden: schattig und feucht
                double wet = smooth(450, 80, h) * (0.45 + 0.9 * south) + smooth(500, 100, di) * 0.35;
                double dry = smooth(800, 4500, x) * 0.6 + (1 - smooth(0.25, 0.6, f)) * 0.4;
                double g = Math.min(0.85, wet * 0.6), nn = Math.min(0.8, dry * 0.55), l = Math.max(0, 1 - g - nn);
                // Steppe und Gras im Offenen
                double open = (1 - smooth(0.15, 0.5, f)) * (1 - rockCov) * smooth(950, 650, h);
                double st = open * smooth(0.9, 0.35, slope) * (0.35 + 0.65 * smooth(-1500, 3500, x)) * smooth(0.25, 0.55, patch2 * 0.5 + 0.5 * fbm(x / 60.0, z / 60.0));
                double gr = smooth(70, 10, dw) * smooth(0.6, 0.2, slope) * 0.8 + open * 0.15;
                if (scoured) { st *= 0.15; gr *= 0.25; }
                double mo = smooth(140, 20, di) * (1 - steep * 0.5) * (1 - smooth(0.3, 0.7, f));
                lenga[k] = b(f * l);
                nire[k] = b(f * nn);
                guindo[k] = b(f * g);
                steppe[k] = b(st);
                grass[k] = b(gr);
                moraine[k] = b(mo);
                rock[k] = b(rockCov);
            }
    }

    /** Halbmesser der Lichtung um den Steg (m) und halber Winkel und Länge der Blickschneise zur Front [G]. */
    public static final double STEG_CLEARING = 60, STEG_VIEW_HALF_ANGLE = 45, STEG_VIEW_LENGTH = 650;

    /**
     * 0..1: wie stark am Steg gelichtet ist. Die Pasarelas stehen auf freien Plattformen am Hang; zwischen Steg und
     * Front wächst nur niedriges Gebüsch, sonst sähe man vom Steg nur Stämme (Befund Status_Phase2.png).
     */
    public double stegClearing(double x, double z) {
        if (!t.measured()) return 0;
        double dx = x - t.steg[0], dz = z - t.steg[1], d = Math.hypot(dx, dz);
        double ring = 1 - smooth(STEG_CLEARING * 0.6, STEG_CLEARING, d);
        double ang = Math.toDegrees(Math.atan2(dx, -dz));
        double diff = Math.abs(((ang - t.steg[3]) % 360 + 540) % 360 - 180);
        double lane = (1 - smooth(STEG_VIEW_HALF_ANGLE * 0.75, STEG_VIEW_HALF_ANGLE, diff)) * (1 - smooth(STEG_VIEW_LENGTH * 0.8, STEG_VIEW_LENGTH, d));
        return Math.max(ring, lane);
    }

    /** Ansichten am Hang, die eine Lichtung brauchen (Befund Regiebuch-Blatt Phase 14: Südflanke nur Stämme). */
    public static final int[] VIEW_KEYS = {2, 5};
    /** Halbmesser der Lichtung (m), halber Winkel der Schneise (°). */
    public static final double VIEW_CLEARING = 45, VIEW_HALF_ANGLE = 30;
    /** Je Ansicht {x, z, Blick, Länge der Schneise bis zum ersten Eis oder Wasser}. */
    private double[][] views = new double[0][];

    private double[][] viewLanes(boolean[] ice, boolean[] water) {
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (int key : VIEW_KEYS) {
            com.dan.glacier.camera.Ansichten.Ansicht a = com.dan.glacier.camera.Ansichten.byKey(key);
            if (a == null) continue;
            double h = Math.toRadians(a.heading()), dx = Math.sin(h), dz = -Math.cos(h), len = 0;
            for (double d = 0; d <= 1500; d += CELL * 0.5) {
                int i = (int) Math.floor((a.x() + dx * d - Terrain.X0) / CELL), j = (int) Math.floor((a.z() + dz * d - Terrain.Z0) / CELL);
                if (i < 0 || j < 0 || i >= M || j >= M) break;
                len = d;
                if (ice[j * M + i] || water[j * M + i]) break;
            }
            out.add(new double[]{a.x(), a.z(), a.heading(), len + 30});
        }
        return out.toArray(new double[0][]);
    }

    /** 0..1: Lichtung um die Ansichten am Hang und Schneise bis zum Eis oder Wasser in Blickrichtung. */
    public double viewClearing(double x, double z) {
        double best = 0;
        for (double[] v : views) {
            double dx = x - v[0], dz = z - v[1], d = Math.hypot(dx, dz);
            if (d > v[3] + 10 && d > VIEW_CLEARING) continue;
            double ring = 1 - smooth(VIEW_CLEARING * 0.6, VIEW_CLEARING, d);
            double ang = Math.toDegrees(Math.atan2(dx, -dz));
            double diff = Math.abs(((ang - v[2]) % 360 + 540) % 360 - 180);
            double lane = (1 - smooth(VIEW_HALF_ANGLE * 0.75, VIEW_HALF_ANGLE, diff)) * (1 - smooth(v[3] * 0.9, v[3], d));
            best = Math.max(best, Math.max(ring, lane));
        }
        return best;
    }

    // ------------------------------------------------------------------ Abfragen

    /** Wert einer Karte an (x, z), 0..1, bilinear. */
    public static double map(byte[] m, double x, double z) {
        double u = (x - Terrain.X0) / CELL - 0.5, v = (z - Terrain.Z0) / CELL - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fx = u - i, fz = v - j;
        return ((at(m, i, j) * (1 - fx) + at(m, i + 1, j) * fx) * (1 - fz) + (at(m, i, j + 1) * (1 - fx) + at(m, i + 1, j + 1) * fx) * fz) / 255.0;
    }

    private static int at(byte[] m, int i, int j) {
        if (i < 0 || j < 0 || i >= M || j >= M) return 0;
        return m[j * M + i] & 0xff;
    }

    /** Dichte des Waldes insgesamt (0..1); im Wasser und auf dem Eis keiner (die Karte zu 25 m franst sonst ins Ufer aus). */
    public double forest(double x, double z) {
        if (!dry(x, z)) return 0;
        return Math.min(1, map(lenga, x, z) + map(nire, x, z) + map(guindo, x, z));
    }

    /** Trockenes Land: über dem Spülsaum und nicht auf dem Eis. */
    public boolean dry(double x, double z) { return t.height(x, z) > 0.5 && t.sourceAt(x, z) != Terrain.EIS; }

    /** {@code com.dan.forest}: Dichte der Art s (0 Lenga, 1 Ñire, 2 Guindo). */
    @Override
    public double at(double x, double z, int s) {
        if (!dry(x, z)) return 0;
        return switch (s) { case LENGA -> map(lenga, x, z); case NIRE -> map(nire, x, z); case GUINDO -> map(guindo, x, z); default -> 0; };
    }

    /** {@code com.dan.reed}: Steppe und Ufergras; Schilf und Binsen gibt es am Gletschersee nicht. */
    @Override
    public double at(double x, double z, com.dan.reed.Reed.Kind kind) {
        if (t.sourceAt(x, z) == Terrain.EIS || t.height(x, z) < 0.1) return 0;
        return switch (kind) {
            case STEPPE -> map(steppe, x, z);
            case GRAS -> map(grass, x, z) * 0.7;
            default -> 0;
        };
    }

    /** Anteile über das ganze Gelände (Wald, Steppe, Moräne) für Statuszeile und Prüfung. */
    public double[] shares() {
        double f = 0, s = 0, m = 0;
        for (int k = 0; k < M * M; k++) {
            f += Math.min(255, (lenga[k] & 0xff) + (nire[k] & 0xff) + (guindo[k] & 0xff));
            s += steppe[k] & 0xff;
            m += moraine[k] & 0xff;
        }
        double n = 255.0 * M * M;
        return new double[]{f / n, s / n, m / n};
    }

    /** „Wald 22 %, Steppe 9 %, Moräne 3 %“ */
    public String describe() {
        double[] s = shares();
        return String.format(Locale.GERMAN, "Wald %.0f %% (Lenga, Ñire, Guindo), Steppe %.0f %%, Moräne %.0f %% [G]", s[0] * 100, s[1] * 100, s[2] * 100);
    }

    // ------------------------------------------------------------------ Helfer

    /** Abstand jeder Zelle zur nächsten Zelle mit m = wahr (in Zellen; Zwei-Durchgangs-Chamfer 3-4). */
    static double[] distance(boolean[] m) {
        double[] d = new double[M * M];
        double inf = 1e9;
        for (int k = 0; k < d.length; k++) d[k] = m[k] ? 0 : inf;
        for (int j = 0; j < M; j++)
            for (int i = 0; i < M; i++) {
                int k = j * M + i;
                if (i > 0) d[k] = Math.min(d[k], d[k - 1] + 1);
                if (j > 0) d[k] = Math.min(d[k], d[k - M] + 1);
                if (i > 0 && j > 0) d[k] = Math.min(d[k], d[k - M - 1] + 1.414);
                if (i < M - 1 && j > 0) d[k] = Math.min(d[k], d[k - M + 1] + 1.414);
            }
        for (int j = M - 1; j >= 0; j--)
            for (int i = M - 1; i >= 0; i--) {
                int k = j * M + i;
                if (i < M - 1) d[k] = Math.min(d[k], d[k + 1] + 1);
                if (j < M - 1) d[k] = Math.min(d[k], d[k + M] + 1);
                if (i < M - 1 && j < M - 1) d[k] = Math.min(d[k], d[k + M + 1] + 1.414);
                if (i > 0 && j < M - 1) d[k] = Math.min(d[k], d[k + M - 1] + 1.414);
            }
        return d;
    }

    static byte b(double v) { return (byte) (int) Math.round(Math.max(0, Math.min(1, v)) * 255); }

    public static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static double hash(int x, int y) {
        long h = x * 374761393L + y * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xffffff) / (double) 0xffffff;
    }

    static double vnoise(double x, double y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(ix, iy), bb = hash(ix + 1, iy), c = hash(ix, iy + 1), e = hash(ix + 1, iy + 1);
        return a + (bb - a) * fx + (c - a) * fy + (a - bb - c + e) * fx * fy;
    }

    public static double fbm(double x, double y) { return 0.57 * vnoise(x, y) + 0.29 * vnoise(x * 2.03 + 7.1, y * 2.03 - 3.3) + 0.14 * vnoise(x * 4.1 - 1.7, y * 4.1 + 5.2); }
}
