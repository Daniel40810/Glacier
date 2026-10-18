package com.dan.glacier.scene;

import com.dan.buoyancy.Mesh;
import com.dan.weathering.Weathering;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 10: die Stege (Pasarelas) auf der Halbinsel Magallanes. Vom Parkplatz oben führen Wege aus Gitterrosten mit
 * Geländer in Kehren den Hang hinunter zum großen Balkon am Steg (Ansicht 1), von dort weiter zu einem unteren Balkon und
 * zum Anleger am Ufer, an dem der Katamaran festmacht. Alles folgt dem Gelände: das Deck 0,5 bis 1 m darüber, Pfosten
 * alle 2 m bis auf den Grund, Handlauf auf 1,1 m, ein Zwischenholm auf halber Höhe [G: Maße wie üblich bei Stegen].
 * <p>
 * Die Lage ist in einem Achsenkreuz am Steg beschrieben: f in Blickrichtung (zur Front), b nach rechts. Gebaut wird als
 * {@link Mesh} in Weltkoordinaten; nass macht es {@code weathering}: unter der nassen Linie (Auflaufen am Anleger), im
 * Regen und im Spritzwasser werden die Teile dunkler ({@link #mesh(Weathering.Snapshot)}).
 */
public final class Stege {
    /** Ein Weg: Punkte {x, y (Deck), z} alle 2 m. */
    public record Weg(String name, double[][] p, double width) { }
    /** Ein Balkon: Mitte, Blick (Grad), Breite quer und Tiefe in Blickrichtung (m), Höhe des Decks. */
    public record Balkon(String name, double x, double z, double heading, double width, double depth, double y) { }
    /** Ein Pfosten oder Pfahl: Ort, unten, oben, Halbmesser (für Gischt und Stöße). */
    public record Pfosten(double x, double z, double y0, double y1, double r) { }

    public static final double RAIL = 1.1, MID = 0.55, POST_STEP = 2.0;
    static final double[] DECK = {0.075, 0.068, 0.06}, IRON = {0.085, 0.09, 0.085}, WOOD = {0.13, 0.095, 0.06}, SIGN = {0.35, 0.30, 0.18};

    public final List<Weg> wege = new ArrayList<>();
    public final List<Balkon> balkone = new ArrayList<>();
    public final List<Pfosten> pfosten = new ArrayList<>();
    /** Der Anleger: Anfang am Ufer, Ende im Wasser {x, z}, Deck (m) und Liegeplatz für den Katamaran {x, z, Kurs}. */
    public final double[] pierStart, pierEnd, berth;
    public final double pierDeck;
    private final Terrain t;
    private final double level, h, fx, fz, rx, rz, sx, sz;

    public Stege(Terrain t, double level) {
        this.t = t;
        this.level = level;
        double[] st = t.steg;
        sx = st[0];
        sz = st[1];
        h = Math.toRadians(st[3]);
        fx = Math.sin(h);
        fz = -Math.cos(h);
        rx = Math.cos(h);
        rz = Math.sin(h);
        // der große Balkon am Steg (Ansicht 1 steht an seiner Brüstung)
        double y0 = t.height(sx, sz) + 0.6;
        balkone.add(new Balkon("Balcón principal", sx + fx * 2.5, sz + fz * 2.5, st[3], 12, 7, y0));
        // von oben in Kehren herunter
        wegXZ("Pasarela superior", zigzag(at(-160, 30), at(-4, 0), 0.2), 1.8, 0.7);
        // der Anleger: das Ufer nahe am Steg, vor dem das Wasser bald tief wird (der Katamaran braucht 3 m)
        double[] pier = findPier(t, level, sx, sz);
        pierStart = new double[]{pier[0], pier[1]};
        pierEnd = new double[]{pier[2], pier[3]};
        pierDeck = level + 1.6;
        double pdx = pierEnd[0] - pierStart[0], pdz = pierEnd[1] - pierStart[1], pl = Math.hypot(pdx, pdz);
        double ux = pdx / pl, uz = pdz / pl;
        // Liegeplatz längsseits am Ende (rechts vom Anleger, vom Ufer aus gesehen), Bug zum Ufer
        berth = new double[]{pierEnd[0] - ux * 9 + -uz * 6.4, pierEnd[1] - uz * 9 + ux * 6.4, (Math.toDegrees(Math.atan2(-ux, uz)) + 360) % 360};
        // unterer Balkon auf halber Höhe zwischen Steg und Ufer, Blick wie am Steg
        double[] A = {sx - fx * 1, sz - fz * 1}, S = pierStart;
        double hA = t.height(sx, sz), hS = level;
        double[] L = null;
        for (double u = 0.2; u <= 0.8; u += 0.02) {
            double x = A[0] + (S[0] - A[0]) * u, z = A[1] + (S[1] - A[1]) * u;
            if (t.height(x, z) <= hA - 0.5 * (hA - hS)) { L = new double[]{x, z}; break; }
        }
        if (L == null) L = new double[]{(A[0] + S[0]) / 2, (A[1] + S[1]) / 2};
        balkone.add(new Balkon("Balcón inferior", L[0], L[1], st[3], 8, 5, t.height(L[0], L[1]) + 0.9));
        wegXZ("Pasarela inferior", zigzag(new double[]{sx + rx * 6.5, sz + rz * 6.5}, new double[]{L[0] - fx * 2.6, L[1] - fz * 2.6}, 0.22), 1.6, 0.6);
        wegXZ("Bajada al muelle", zigzag(new double[]{L[0] + rx * 4.2, L[1] + rz * 4.2}, S, 0.22), 1.6, 0.5);
        double[][] pp = new double[Math.max(2, (int) Math.round(pl / POST_STEP)) + 1][];
        for (int i = 0; i < pp.length; i++) {
            double u = i / (double) (pp.length - 1);
            pp[i] = new double[]{pierStart[0] + pdx * u, pierDeck, pierStart[1] + pdz * u};
        }
        wege.add(new Weg("Muelle", pp, 3.0));
    }

    /**
     * Wo der Anleger hin kommt: ein Punkt am Ufer (Gelände knapp über dem See) im Umkreis von 700 m um den Steg, von dem
     * aus in gerader Linie nach höchstens 45 m 4 m Wasser stehen, der nächste zum Steg. {Ufer x, z, Ende x, z}.
     */
    static double[] findPier(Terrain t, double level, double sx, double sz) {
        double best = Double.MAX_VALUE;
        double[] out = null;
        for (double x = sx - 700; x <= sx + 700; x += 4)
            for (double z = sz - 700; z <= sz + 700; z += 4) {
                double hh = t.height(x, z);
                if (hh < level + 0.3 || hh > level + 1.5 || t.ice(x, z)) continue;
                double d = Math.hypot(x - sx, z - sz);
                if (d >= best) continue;
                for (int k = 0; k < 24; k++) {
                    double a = k * Math.PI / 12, dx = Math.sin(a), dz = -Math.cos(a);
                    double end = Double.NaN;
                    for (double r = 6; r <= 45; r += 3) {
                        double hx = t.height(x + dx * r, z + dz * r);
                        if (t.ice(x + dx * r, z + dz * r) || hx > level + 0.5 && r > 10) break;
                        if (hx < level - 4) { end = r + 6; break; }
                    }
                    if (Double.isNaN(end)) continue;
                    // und weiter draußen tief (kein Tümpel)
                    if (t.height(x + dx * (end + 60), z + dz * (end + 60)) > level - 10) continue;
                    best = d;
                    out = new double[]{x, z, x + dx * end, z + dz * end};
                    break;
                }
            }
        if (out == null) out = new double[]{sx, sz, sx + 30, sz};
        return out;
    }

    /** Ein Weg von a nach b in Kehren, so dass er nirgends steiler ist als maxSlope (höchstens 8 Kehren, je 30 m seitlich). */
    double[][] zigzag(double[] a, double[] b, double maxSlope) {
        double dx = b[0] - a[0], dz = b[1] - a[1], d = Math.hypot(dx, dz), drop = Math.abs(t.height(a[0], a[1]) - Math.max(level, t.height(b[0], b[1])));
        double qx = -dz / d, qz = dx / d;
        int n = 1;
        double w = 0;
        for (; n <= 8; n++) {
            double leg = drop / n / maxSlope, along = d / n;
            w = leg > along ? Math.sqrt(leg * leg - along * along) / 2 : 0;
            if (w <= 30) break;
        }
        w = Math.min(w, 30);
        double[][] p = new double[n + 1][];
        for (int k = 0; k <= n; k++) {
            double u = k / (double) n, off = k == 0 || k == n ? 0 : (k % 2 == 1 ? w : -w);
            if (n == 1 && w > 0) off = 0;
            p[k] = new double[]{a[0] + dx * u + qx * off, a[1] + dz * u + qz * off};
        }
        if (n == 1 && w > 0) {   // eine Kehre: über einen Punkt seitlich
            p = new double[][]{a, {a[0] + dx / 2 + qx * w * 2, a[1] + dz / 2 + qz * w * 2}, b};
        }
        return p;
    }

    /** Ort im Achsenkreuz am Steg: f in Blickrichtung, b nach rechts. */
    double[] at(double f, double b) { return new double[]{sx + fx * f + rx * b, sz + fz * f + rz * b}; }

    /** Ein Weg durch die Punkte {f, b}, Deck über dem Gelände (m), alle 2 m ein Punkt, geglättet, nie unter dem Grund. */
    private void weg(String name, double[][] fb, double width, double above) {
        double[][] xz = new double[fb.length][];
        for (int k = 0; k < fb.length; k++) xz[k] = at(fb[k][0], fb[k][1]);
        wegXZ(name, xz, width, above);
    }

    /** Wie oben, Punkte {x, z} in der Welt. */
    private void wegXZ(String name, double[][] xz, double width, double above) {
        List<double[]> pts = new ArrayList<>();
        for (int k = 0; k + 1 < xz.length; k++) {
            double[] a = xz[k], b = xz[k + 1];
            double l = Math.hypot(b[0] - a[0], b[1] - a[1]);
            int n = Math.max(1, (int) Math.round(l / POST_STEP));
            for (int i = (k == 0 ? 0 : 1); i <= n; i++) {
                double u = i / (double) n, x = a[0] + (b[0] - a[0]) * u, z = a[1] + (b[1] - a[1]) * u;
                pts.add(new double[]{x, Math.max(t.height(x, z), level) + above, z});
            }
        }
        double[][] p = pts.toArray(new double[0][]);
        // glätten (der Steg folgt dem Hang, nicht jeder Unebenheit), dann wieder über den Grund
        for (int pass = 0; pass < 3; pass++) {
            double[] y = new double[p.length];
            for (int i = 0; i < p.length; i++) y[i] = (p[Math.max(0, i - 1)][1] + 2 * p[i][1] + p[Math.min(p.length - 1, i + 1)][1]) / 4;
            for (int i = 0; i < p.length; i++) p[i][1] = Math.max(y[i], Math.max(level, t.height(p[i][0], p[i][2])) + 0.3);
        }
        wege.add(new Weg(name, p, width));
    }

    /** Die Pfosten und Pfähle (einmal gezählt), für Gischt und Stöße. */
    public List<Pfosten> pfosten() {
        if (pfosten.isEmpty()) mesh(null);
        return pfosten;
    }

    /** Länge aller Wege (m). */
    public double length() {
        double l = 0;
        for (Weg w : wege) for (int i = 1; i < w.p.length; i++) l += Math.hypot(w.p[i][0] - w.p[i - 1][0], w.p[i][2] - w.p[i - 1][2]);
        return l;
    }

    /** Kleinster Abstand des Decks über dem Gelände (m) über alle Wege und Balkone. */
    public double minClearance() {
        double m = 1e9;
        for (Weg w : wege) for (double[] q : w.p) m = Math.min(m, q[1] - Math.max(t.height(q[0], q[2]), level));
        for (Balkon b : balkone) m = Math.min(m, b.y - t.height(b.x, b.z));
        return m;
    }

    /** Größte Steigung eines Wegs (Höhe je Länge). */
    public double maxSlope() {
        double m = 0;
        for (Weg w : wege) for (int i = 1; i < w.p.length; i++) m = Math.max(m, Math.abs(w.p[i][1] - w.p[i - 1][1]) / Math.max(1e-6, Math.hypot(w.p[i][0] - w.p[i - 1][0], w.p[i][2] - w.p[i - 1][2])));
        return m;
    }

    /**
     * Das Netz, nass nach dem Stand der Verwitterung (null: trocken). Jeder Balken bekommt die Nässe an seinem unteren Ende;
     * Pfosten und Pfähle sind an der nassen Linie geteilt (unten nass, oben trocken).
     */
    public Mesh mesh(Weathering.Snapshot s) {
        Mesh m = new Mesh();
        boolean count = pfosten.isEmpty();
        for (Weg w : wege) {
            double[][] p = w.p;
            double hw = w.width / 2;
            boolean pier = w.name.equals("Muelle");
            for (int i = 0; i < p.length; i++) {
                double[] a = p[i], b = p[Math.min(p.length - 1, i + 1)], c = p[Math.max(0, i - 1)];
                double dx = b[0] - c[0], dz = b[2] - c[2], l = Math.max(1e-6, Math.hypot(dx, dz)), qx = -dz / l * hw, qz = dx / l * hw;
                // Pfosten links und rechts bis auf den Grund (am Anleger Pfähle in den See)
                for (int sd = -1; sd <= 1; sd += 2) {
                    double px = a[0] + sd * qx, pz = a[2] + sd * qz, g = t.height(px, pz);
                    if (!pier || i % 2 == 0) post(m, s, px, pz, Math.min(g, a[1] - 0.2), a[1] + RAIL, pier ? 0.14 : 0.035, pier ? WOOD : IRON, count);
                }
                if (i + 1 >= p.length) continue;
                // Deck: ein Rost von Pfosten zu Pfosten, dazu Handlauf und Zwischenholm
                double[] mid = {(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2};
                // Phase 16 (Befund Status_Phase15: das Deck war eine graue Fläche): sechs Roststäbe mit Fugen
                double[] dc = wet(DECK, s, mid[1], mid[0], mid[2], 1);
                for (int k = 0; k < 6; k++) {
                    double u = (k + 0.5) / 6 * 2 - 1, ux = u * qx, uz = u * qz;
                    m.beam(new double[]{a[0] + ux, a[1] - 0.08, a[2] + uz}, new double[]{b[0] + ux, b[1] - 0.08, b[2] + uz}, w.width / 6 * 0.8, 0.08, ton(dc, k + i), Mesh.PAINT);
                }
                for (int sd = -1; sd <= 1; sd += 2) {
                    double ax = a[0] + sd * qx, az = a[2] + sd * qz, bx = b[0] + sd * qx, bz = b[2] + sd * qz;
                    m.beam(new double[]{ax, a[1] + RAIL - 0.05, az}, new double[]{bx, b[1] + RAIL - 0.05, bz}, 0.05, 0.05, wet(IRON, s, a[1] + RAIL, ax, az, 0), Mesh.PAINT);
                    m.beam(new double[]{ax, a[1] + MID, az}, new double[]{bx, b[1] + MID, bz}, 0.03, 0.03, wet(IRON, s, a[1] + MID, ax, az, 0), Mesh.PAINT);
                }
            }
        }
        for (Balkon bk : balkone) {
            double hr = Math.toRadians(bk.heading), ffx = Math.sin(hr), ffz = -Math.cos(hr), bbx = Math.cos(hr), bbz = Math.sin(hr);
            double hw = bk.width / 2, hd = bk.depth / 2;
            // Deck als Rost: Stäbe von 10 cm mit 3 cm Fuge in Blickrichtung, darunter zwei Querträger (Phase 16)
            int strips = (int) Math.round(bk.width / 0.13);
            double[] dc = wet(DECK, s, bk.y, bk.x, bk.z, 1);
            for (int k = 0; k < strips; k++) {
                double uc = -hw + bk.width * (k + 0.5) / strips;
                double[] a = {bk.x + bbx * uc - ffx * hd, bk.y - 0.08, bk.z + bbz * uc - ffz * hd}, b = {bk.x + bbx * uc + ffx * hd, bk.y - 0.08, bk.z + bbz * uc + ffz * hd};
                m.beam(a, b, bk.width / strips * 0.77, 0.06, ton(dc, k), Mesh.PAINT);
            }
            for (int j = -1; j <= 1; j += 2) {
                double vc = j * hd * 0.5;
                m.beam(new double[]{bk.x - bbx * hw + ffx * vc, bk.y - 0.2, bk.z - bbz * hw + ffz * vc}, new double[]{bk.x + bbx * hw + ffx * vc, bk.y - 0.2, bk.z + bbz * hw + ffz * vc}, 0.12, 0.12, IRON, Mesh.PAINT);
            }
            // Brüstung vorn und an den Seiten; hinten offen (dort kommt der Weg an)
            double[][] c = {{-hw, -hd}, {-hw, hd}, {hw, hd}, {hw, -hd}};
            for (int e = 0; e < 3; e++) {
                double[] a0 = c[e], a1 = c[e + 1];
                double ax = bk.x + bbx * a0[0] + ffx * a0[1], az = bk.z + bbz * a0[0] + ffz * a0[1], bx = bk.x + bbx * a1[0] + ffx * a1[1], bz = bk.z + bbz * a1[0] + ffz * a1[1];
                double l = Math.hypot(bx - ax, bz - az);
                int n = Math.max(1, (int) Math.round(l / POST_STEP));
                for (int i = 0; i <= n; i++) {
                    double u = i / (double) n, px = ax + (bx - ax) * u, pz = az + (bz - az) * u;
                    post(m, s, px, pz, Math.min(t.height(px, pz), bk.y - 0.2), bk.y + RAIL, 0.04, IRON, count);
                }
                m.beam(new double[]{ax, bk.y + RAIL - 0.06, az}, new double[]{bx, bk.y + RAIL - 0.06, bz}, 0.12, 0.06, wet(WOOD, s, bk.y + RAIL, ax, az, 1), Mesh.PAINT);
                m.beam(new double[]{ax, bk.y + MID, az}, new double[]{bx, bk.y + MID, bz}, 0.03, 0.03, wet(IRON, s, bk.y + MID, ax, az, 0), Mesh.PAINT);
            }
            // Stützen unter dem Deck in der Mitte
            for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j += 2) {
                double px = bk.x + bbx * i * hw * 0.6 + ffx * j * hd * 0.5, pz = bk.z + bbz * i * hw * 0.6 + ffz * j * hd * 0.5;
                post(m, s, px, pz, t.height(px, pz), bk.y - 0.15, 0.08, IRON, count);
            }
            // eine Tafel am Eingang
            double tx = bk.x - ffx * (hd + 0.6) + bbx * (hw - 0.6), tz = bk.z - ffz * (hd + 0.6) + bbz * (hw - 0.6);
            m.beam(new double[]{tx, bk.y, tz}, new double[]{tx, bk.y + 1.0, tz}, 0.06, 0.06, IRON, Mesh.PAINT);
            m.beam(new double[]{tx - bbx * 0.4, bk.y + 1.0, tz - bbz * 0.4}, new double[]{tx + bbx * 0.4, bk.y + 1.0, tz + bbz * 0.4}, 0.04, 0.5, SIGN, Mesh.PAINT);
        }
        return m;
    }

    /** Ein Roststab: um ±7 % in der Helligkeit verschieden, damit das Deck nicht eine Fläche ist. */
    private static double[] ton(double[] c, int k) {
        double f = 1 + 0.07 * Math.sin(k * 2.399 + 0.7) ;
        return new double[]{c[0] * f, c[1] * f, c[2] * f};
    }

    /** Ein Pfosten von y0 bis y1, an der nassen Linie geteilt. */
    private void post(Mesh m, Weathering.Snapshot s, double x, double z, double y0, double y1, double r, double[] col, boolean count) {
        if (y1 - y0 < 0.05) return;
        if (count) pfosten.add(new Pfosten(x, z, y0, y1, r));
        double split = s == null ? Double.NaN : Math.max(y0, Math.min(y1, s.wetLine()));
        if (s != null && split > y0 + 0.02 && split < y1 - 0.02) {
            m.beam(new double[]{x, y0, z}, new double[]{x, split, z}, 2 * r, 2 * r, wet(col, s, y0, x, z, 0), Mesh.PAINT);
            m.beam(new double[]{x, split, z}, new double[]{x, y1, z}, 2 * r, 2 * r, wet(col, s, y1, x, z, 0), Mesh.PAINT);
        } else m.beam(new double[]{x, y0, z}, new double[]{x, y1, z}, 2 * r, 2 * r, wet(col, s, (y0 + y1) / 2, x, z, 0), Mesh.PAINT);
    }

    /** Farbe nass: dunkler, bis auf 55 % (Holz und Rost mehr als Farbe) [G]. */
    static double[] wet(double[] c, Weathering.Snapshot s, double y, double x, double z, double up) {
        if (s == null) return c;
        double w = Weathering.wetness(s, y, 0, up, 0, x, z, 0);
        double k = 1 - 0.45 * w;
        return new double[]{c[0] * k, c[1] * k, c[2] * k};
    }

    /** Nässe eines Orts (0..1) nach dem Stand, wie das Netz sie zeigt. */
    public static double wetness(Weathering.Snapshot s, double y, double x, double z) { return Weathering.wetness(s, y, 0, 1, 0, x, z, 0); }

    /** „Stege: 3 Wege und der Anleger, 412 m, 2 Balkone, 486 Pfosten · Deck mindestens 0,3 m über Grund, steilste Stelle 31 %“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Stege: %d Wege und der Anleger, %,.0f m, %d Balkone, %d Pfosten · Deck mindestens %.1f m über Grund, steilste Stelle %.0f %%",
                wege.size() - 1, length(), balkone.size(), pfosten().size(), minClearance(), 100 * maxSlope());
    }
}
