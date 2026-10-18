package com.dan.glacier.scene;

import com.dan.buoyancy.Berg;
import com.dan.buoyancy.Bergs;
import com.dan.buoyancy.BrashField;
import com.dan.buoyancy.Sea;
import com.dan.fracture.Poly;
import com.dan.ice.Glacier;
import com.dan.ice.IceMesh;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Phase 9: Eisberge für den See. Aus der Kalbung kommen sie als Stücke von {@code fracture}, sobald diese auf dem
 * Wasser zur Ruhe gekommen sind (die App übergibt sie an {@code buoyancy}). Hier entstehen sie aus dem Nichts: ein Tag
 * Kalbung auf einmal ({@link #fillDay}) und ein hoher Turm, der kentert ({@link #toppler}). Die Formen sind konvexe
 * Körper: ein Quader, dem zufällige Ebenen die Ecken abschlagen.
 */
public final class Eisberge {
    private Eisberge() { }

    /** Eine Form: Netz zum Zeichnen und dasselbe als Dreiecke für die Proben. */
    public record Form(IceMesh mesh, float[] pos, int[] idx) { }

    /** Ein konvexer Brocken a × b × c (m), Ecken und Kanten abgeschlagen; Mitte im Ursprung. */
    public static Form convex(SplittableRandom r, double a, double b, double c, int cuts) {
        Poly p = Poly.box(-a / 2, -b / 2, -c / 2, a / 2, b / 2, c / 2, -1);
        for (int k = 0; k < cuts; k++) {
            double nx = r.nextDouble() * 2 - 1, ny = r.nextDouble() * 2 - 1, nz = r.nextDouble() * 2 - 1;
            double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 0.2) continue;
            nx /= l; ny /= l; nz /= l;
            double reach = Math.abs(nx) * a / 2 + Math.abs(ny) * b / 2 + Math.abs(nz) * c / 2;
            Poly q = p.copy();
            q.clip(nx, ny, nz, reach * (0.55 + 0.4 * r.nextDouble()), -1);
            if (!q.isEmpty()) p = q;
        }
        return form(p.faces);
    }

    /** Netz aus den Flächen eines konvexen Körpers (Umlauf nach der Normalen, nach außen). */
    public static Form form(List<Poly.Face> faces) {
        List<float[]> v = new ArrayList<>();
        for (Poly.Face f : faces) {
            double[] p = f.p;
            int n = p.length / 3;
            double sx = 0, sy = 0, sz = 0;
            for (int k = 1; k + 1 < n; k++) {
                double ax = p[k * 3] - p[0], ay = p[k * 3 + 1] - p[1], az = p[k * 3 + 2] - p[2];
                double bx = p[k * 3 + 3] - p[0], by = p[k * 3 + 4] - p[1], bz = p[k * 3 + 5] - p[2];
                sx += ay * bz - az * by; sy += az * bx - ax * bz; sz += ax * by - ay * bx;
            }
            boolean flip = sx * f.nx + sy * f.ny + sz * f.nz < 0;
            for (int k = 1; k + 1 < n; k++) {
                int[] t = flip ? new int[]{0, k + 1, k} : new int[]{0, k, k + 1};
                for (int q : t) v.add(new float[]{(float) p[q * 3], (float) p[q * 3 + 1], (float) p[q * 3 + 2], (float) f.nx, (float) f.ny, (float) f.nz});
            }
        }
        float[] pos = new float[v.size() * 3], nrm = new float[v.size() * 3], ao = new float[v.size()];
        int[] idx = new int[v.size()];
        for (int i = 0; i < v.size(); i++) {
            float[] e = v.get(i);
            System.arraycopy(e, 0, pos, i * 3, 3);
            System.arraycopy(e, 3, nrm, i * 3, 3);
            ao[i] = 1;
            idx[i] = i;
        }
        return new Form(new IceMesh(pos, nrm, idx, ao), pos, idx);
    }

    /** Ein Berg aus einer Form (Proben nach der Größe: 40 bis 120). */
    public static Berg berg(Form f) {
        double vol = Math.abs(Berg.meshVolume(f.pos(), f.idx()));
        Berg b = new Berg(f.pos(), f.idx(), (int) Math.max(40, Math.min(120, 30 + vol / 300)));
        b.tag = f.mesh();
        return b;
    }

    /** Größen der Abbrüche [G]: meist einige hundert bis einige tausend m³, selten über 20 000 (lognormal). */
    static double volume(SplittableRandom r, double median, double spread) {
        double g = 0;
        for (int i = 0; i < 6; i++) g += r.nextDouble();
        g = (g - 3) * Math.sqrt(2);   // ungefähr normal
        return median * Math.exp(spread * g);
    }

    /**
     * Ein Tag Kalbung auf einmal: so viele Abbrüche, wie die Rate für 24 h ergibt, je einer mit einem großen Berg und
     * einigen Brocken; jeder ist so lange getrieben (mit dem Wind und gestreut) und geschmolzen, wie er alt ist, bis er
     * auf Grund laufen würde. Liegende, stabile Formen (die kentern nicht gleich); dazu Treibeis an der Front.
     * Gibt die Zahl der neuen Berge zurück.
     */
    public static int fillDay(Bergs bergs, Glacier g, Sea sea, double ratePerHour, double windFromDeg, long seed, BrashField brash) {
        SplittableRandom r = new SplittableRandom(seed);
        int events = (int) Math.round(ratePerHour * 24);
        double level = sea.level(), to = Math.toRadians(windFromDeg + 180);
        List<double[]> fronts = new ArrayList<>(g.frontLines());
        double total = g.frontLength();
        int made = 0;
        for (int e = 0; e < events; e++) {
            double[] at = along(fronts, total, r.nextDouble());
            double[] nf = g.nearestFront(at[0], at[1], 20);
            if (nf == null) continue;
            double nl = Math.hypot(nf[2], nf[3]), nx = nf[2] / nl, nz = nf[3] / nl;
            double ageH = 24 * r.nextDouble();
            int pieces = 2 + r.nextInt(4);
            for (int k = 0; k < pieces; k++) {
                double v = k == 0 ? volume(r, 3000, 0.9) : volume(r, 300, 0.8);
                v *= Math.max(0.25, 1 - 0.03 * ageH);   // geschmolzen
                if (v < 20) continue;
                // liegend: Höhe kleiner als Breite und Länge
                double h = Math.cbrt(v) * (0.55 + 0.25 * r.nextDouble()), w = Math.sqrt(v / h) * (0.8 + 0.4 * r.nextDouble()), l = v / (h * w);
                Form f = convex(r, w, h, l, 6 + r.nextInt(6));
                Berg b = berg(f);
                // treiben: vom Ort vor der Front mit dem Wind (gestreut), höchstens bis es flach wird
                double dir = to + (r.nextDouble() - 0.5) * 1.6, dx = Math.sin(dir), dz = -Math.cos(dir);
                double x = at[0] + nx * (25 + 10 * k), z = at[1] + nz * (25 + 10 * k);
                double go = ageH * 3600 * 0.02 * (2 + 4 * r.nextDouble()) * (0.3 + 0.7 * r.nextDouble());
                double draft = 0.9 * h + 3;
                for (double s = 0; s < go; s += 10) {
                    double xn = x + dx * 10, zn = z + dz * 10;
                    if (level - sea.bed(xn, zn) < draft) break;
                    x = xn;
                    z = zn;
                }
                // überall unter dem Berg tief genug?
                double rr = 0.6 * Math.max(w, l);
                boolean deep = level - sea.bed(x, z) >= draft;
                for (int q = 0; q < 8 && deep; q++) {
                    double ang = q * Math.PI / 4;
                    deep = level - sea.bed(x + rr * Math.cos(ang), z + rr * Math.sin(ang)) >= draft;
                }
                if (!deep) continue;
                // nicht in einen anderen hinein
                boolean free = true;
                for (Berg o : bergs.bergs) {
                    double[] c = o.cgWorld();
                    if (Math.hypot(c[0] - x, c[2] - z) < (o.radius() + b.radius()) * 0.85) { free = false; break; }
                }
                if (!free) continue;
                double yaw = r.nextDouble() * Math.PI;
                b.place(x, level, z, new double[]{Math.cos(yaw / 2), 0, Math.sin(yaw / 2), 0}, null, null);
                b.settle(level);
                b.origin = String.format(Locale.GERMAN, "vor %.0f h abgebrochen", ageH);
                if (bergs.add(b) != null) made++;
                if (brash != null && ageH < 10) brash.add(at[0] + nx * 40, at[1] + nz * 40, v * 0.1, 40, sea);
            }
        }
        return made;
    }

    /**
     * Phase 15 („Weiter wie zuletzt“, F31 A): ein liegender Berg von etwa {@code volume} m³ an (x, z), Form aus dem
     * Seed neu gewürfelt, Blick {@code gierGrad}; schwimmt nach {@link Berg#settle}.
     */
    public static Berg wieder(double volume, double x, double z, double level, double gierGrad, long seed, String origin) {
        SplittableRandom r = new SplittableRandom(seed);
        double v = Math.max(30, volume);
        double h = Math.cbrt(v) * (0.55 + 0.25 * r.nextDouble()), w = Math.sqrt(v / h) * (0.8 + 0.4 * r.nextDouble()), l = v / (h * w);
        Berg b = berg(convex(r, w, h, l, 6 + r.nextInt(6)));
        double yaw = Math.toRadians(Double.isNaN(gierGrad) ? 0 : gierGrad);
        b.place(x, level, z, new double[]{Math.cos(yaw / 2), 0, Math.sin(yaw / 2), 0}, null, null);
        b.settle(level);
        b.origin = origin;
        return b;
    }

    /** Ein hoher, schmaler Berg (h × b × d), der aufrecht nicht schwimmen kann: kentert in Sekunden. */
    public static Berg toppler(double x, double z, double level, double headingDeg, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        Form f = convex(r, 28, 44, 9, 4);
        Berg b = berg(f);
        double yaw = Math.toRadians(headingDeg), tilt = 0.06;
        // erst um die Hochachse, dann ein wenig um die lange waagerechte Achse kippen
        double[] qy = {Math.cos(yaw / 2), 0, Math.sin(yaw / 2), 0}, qx = {Math.cos(tilt / 2), Math.sin(tilt / 2), 0, 0};
        double[] q = {qy[0] * qx[0] - qy[1] * qx[1] - qy[2] * qx[2] - qy[3] * qx[3], qy[0] * qx[1] + qy[1] * qx[0] + qy[2] * qx[3] - qy[3] * qx[2],
                qy[0] * qx[2] - qy[1] * qx[3] + qy[2] * qx[0] + qy[3] * qx[1], qy[0] * qx[3] + qy[1] * qx[2] - qy[2] * qx[1] + qy[3] * qx[0]};
        b.place(x, level, z, q, null, null);
        b.settle(level);
        b.origin = "Probe: kentert";
        return b;
    }

    /** Punkt auf den Frontlinien beim Anteil u (0..1) der ganzen Länge. */
    static double[] along(List<double[]> lines, double total, double u) {
        double s = u * total;
        for (double[] p : lines)
            for (int k = 2; k < p.length; k += 2) {
                double l = Math.hypot(p[k] - p[k - 2], p[k + 1] - p[k - 1]);
                if (s <= l) return new double[]{p[k - 2] + (p[k] - p[k - 2]) * s / l, p[k - 1] + (p[k + 1] - p[k - 1]) * s / l};
                s -= l;
            }
        double[] p = lines.get(0);
        return new double[]{p[0], p[1]};
    }
}
