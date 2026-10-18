package com.dan.glacier.tools;

import com.dan.glacier.scene.Geo;
import com.dan.glacier.scene.Site;
import com.dan.glacier.scene.Terrain;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Import der Höhendaten (Phase 2, F2 A): liest den Zuschnitt aus Copernicus GLO-30 als GeoTIFF (WGS84, 1″, etwa
 * {@code templates/rasters_COP30/output_hh.tif}, gelesen von {@link GeoTiff}) und schreibt das Gelände der Szene nach
 * {@code daten/gelaende/} ({@link Terrain}). Ohne GDAL: die Umrechnung macht {@link Geo}.
 * <ol>
 * <li><b>Seespiegel:</b> der häufigste Wert im Raster (das Höhenmodell setzt Seen eben); der See sind die großen
 * zusammenhängenden Flächen auf diesem Wert – Canal de los Témpanos und Brazo Rico.</li>
 * <li><b>Gletscher:</b> die größte zusammenhängende glatte Fläche am See (Neigung unter 16 %, nicht Wasser, bis 4 Zellen vom Ufer), geschlossen
 * und ohne Löcher, dazu die steilen Zellen der Front zwischen Eis und Wasser.</li>
 * <li><b>Front und Ursprung:</b> Eiszellen am Wasser bilden die Front; ihre östlichste Stelle, wo die Zunge an die
 * Halbinsel Magallanes stößt, ist der Ursprung der Szene. Weicht sie mehr als 10 m von {@link Site#LAT}/{@link Site#LON}
 * ab, nennt das Werkzeug die neuen Werte.</li>
 * <li><b>Raster der Szene:</b> 2048 × 2048 Zellen zu 6,25 m (Nord = −z), jede Zelle nach Breite und Länge umgerechnet
 * und bikubisch aus dem 30-m-Raster gelesen; auf Fels ein leichtes Relief von 1,5 m dazu [G].</li>
 * <li><b>Seegrund</b> [G]: Tiefe = 160 m · (1 − e^(−s/350 m)) mit s = Abstand zum Ufer aus Fels; vor der Front fällt
 * er auf die 96 m, auf denen der Fuß der Front steht (Werkbuch, Abschnitt 2).</li>
 * <li><b>Grund unter dem Eis</b> [G]: Eisdicke 170 m an der Front, 8 km aufwärts 380 m, zu den Rändern hin wie ein
 * Trogtal dünner (√ des Randabstands bis 600 m).</li>
 * <li><b>Stege:</b> der nächste Punkt der Halbinsel zur Mitte der Front, der mindestens 300 m entfernt und 5 bis 60 m
 * über dem See liegt.</li>
 * </ol>
 * Aufruf: {@code GelaendeImport [templates/rasters_COP30/output_hh.tif] [daten/gelaende] [docs/status/Gelaende_Import.png]}
 */
public final class GelaendeImport {
    /** Seegrund: größte Tiefe, Abfall-Länge (m) [G]; Eis: Dicke an der Front und 8 km aufwärts (m) [G]. */
    static final double DMAX = 160, LBATH = 350, ICE_FRONT = Site.ICE_THICKNESS, ICE_UP = 380, ICE_MARGIN = 600;
    static final int N = Terrain.N;
    final List<String> notes = new ArrayList<>();
    double lat0, lon0;

    public static void main(String[] a) throws Exception {
        File in = new File(a.length > 0 ? a[0] : "templates/rasters_COP30/output_hh.tif");
        File out = a.length > 1 ? new File(a[1]) : Terrain.defaultDir();
        File png = new File(a.length > 2 ? a[2] : "docs/status/Gelaende_Import.png");
        long t0 = System.nanoTime();
        GelaendeImport g = new GelaendeImport();
        Terrain t = g.run(in);
        t.save(out);
        writePreview(t, png);
        for (String n : g.notes) System.out.println(n);
        System.out.println("Gelände: " + t.describe());
        System.out.printf(Locale.GERMAN, "geschrieben nach %s und %s in %.1f s%n", out, png, (System.nanoTime() - t0) / 1e9);
    }

    public Terrain run(File tif) throws IOException {
        GeoTiff g = GeoTiff.read(tif);
        if (!g.geographic()) throw new IOException("erwartet WGS84 in Grad (GLO-30), die Datei hat EPSG " + g.epsg);
        notes.add("Höhenmodell: " + g);
        // GLO-30 hat zwischen 50° und 60° Breite 1,5″ in der Länge; ein Zuschnitt, den GDAL mit „nearest“ auf 1″ bringt,
        // wiederholt jede dritte Spalte (Streifen im Gelände). Doppelte Spalten fallen weg, die übrigen liegen 1,5″ auseinander.
        Grid gr = dedupColumns(g);
        if (gr.dropped > 0) notes.add(String.format(Locale.GERMAN, "%d von %d Spalten waren doppelt (GLO-30 hat hier %.2f″ in der Länge) – entfernt",
                gr.dropped, g.width, gr.sizeX * 3600));
        int W = gr.w, H = g.height;
        float[] d = gr.d;
        double east0 = gr.east0, sizeX = gr.sizeX;
        // 1 Seespiegel: häufigster Wert auf 0,1 m
        java.util.Map<Integer, Integer> hist = new java.util.HashMap<>();
        for (float v : d) if (!Float.isNaN(v)) hist.merge(Math.round(v * 10), 1, Integer::sum);
        int mode = hist.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).orElseThrow().getKey();
        double lake = mode / 10.0;
        boolean[] flat = new boolean[W * H];
        for (int k = 0; k < W * H; k++) flat[k] = Math.abs(d[k] - lake) < 0.6;
        boolean[] water = largeComponents(flat, W, H, 1000);
        notes.add(String.format(Locale.GERMAN, "Seespiegel im Höhenmodell %.1f m über dem Meer (Werkbuch schätzte 185 m), See %d Zellen", lake, count(water)));
        // 2 Gletscher
        double dxm = Math.toRadians(sizeX) * 6_371_000 * Math.cos(Math.toRadians(g.north0 - H * g.sizeY / 2)), dym = Math.toRadians(g.sizeY) * 6_371_000;
        boolean[] smooth = new boolean[W * H];
        for (int r = 1; r < H - 1; r++)
            for (int c = 1; c < W - 1; c++) {
                int k = r * W + c;
                double gx = (d[k + 1] - d[k - 1]) / (2 * dxm), gy = (d[k + W] - d[k - W]) / (2 * dym);
                smooth[k] = !water[k] && Math.hypot(gx, gy) < 0.16 && d[k] < lake + 1000;
            }
        // die glatte Zunge berührt das Wasser nicht unmittelbar (dazwischen liegt die steile Front): bis 4 Zellen Abstand
        boolean[] ice = largestTouching(smooth, dilate(water, W, H, 4), W, H);
        ice = close(ice, W, H, 2);
        fillHoles(ice, W, H);
        // steile Zellen der Front zwischen Eis und Wasser gehören zum Eis (bis 4 Zellen breit, von innen nach außen)
        for (int pass = 0; pass < 4; pass++) {
            boolean[] add = new boolean[W * H];
            for (int r = 2; r < H - 2; r++)
                for (int c = 2; c < W - 2; c++) {
                    int k = r * W + c;
                    if (ice[k] || water[k] || d[k] < lake + 12) continue;
                    boolean ni = ice[k - 1] || ice[k + 1] || ice[k - W] || ice[k + W];
                    boolean nw = false;
                    for (int dr = -4; dr <= 4 && !nw; dr++)
                        for (int dc = -4; dc <= 4 && !nw; dc++) {
                            int rr = r + dr, cc = c + dc;
                            if (rr >= 0 && cc >= 0 && rr < H && cc < W && water[rr * W + cc]) nw = true;
                        }
                    if (ni && nw) add[k] = true;
                }
            for (int k = 0; k < W * H; k++) ice[k] |= add[k];
        }
        for (int k = 0; k < W * H; k++) if (ice[k]) water[k] = false;
        // der Fuß der Front (bis 12 m über dem See, zwischen Eis und Wasser) ist im Höhenmodell geglättetes Eis über dem
        // Wasser, kein Land: er wird See, damit die Stirn steil ins Wasser geht und kein Felssaum am Eis bleibt
        int toe = 0;
        for (int pass = 0; pass < 2; pass++) {
            boolean[] add = new boolean[W * H];
            for (int r = 1; r < H - 1; r++)
                for (int c = 1; c < W - 1; c++) {
                    int k = r * W + c;
                    if (ice[k] || water[k] || d[k] >= lake + 12) continue;
                    boolean ni = false, nw = false;
                    for (int dr = -1; dr <= 1; dr++)
                        for (int dc = -1; dc <= 1; dc++) { int o = k + dr * W + dc; ni |= ice[o]; nw |= water[o]; }
                    if (ni && nw) add[k] = true;
                }
            for (int k = 0; k < W * H; k++) if (add[k]) { water[k] = true; toe++; }
        }
        notes.add(String.format(Locale.GERMAN, "Fuß der Front: %d Zellen zwischen Eis und Wasser zum See", toe));
        notes.add(String.format(Locale.GERMAN, "Gletscher im Ausschnitt: %d Zellen, %.1f km²", count(ice), count(ice) * dxm * dym / 1e6));
        // 3 Front: Eis am Wasser; Ursprung an ihrer östlichsten Stelle
        int tip = -1;
        for (int r = 1; r < H - 1; r++)
            for (int c = 1; c < W - 1; c++) {
                int k = r * W + c;
                if (!ice[k] || !near(water, W, H, r, c, 2)) continue;
                if (tip < 0 || c > tip % W) tip = k;
            }
        if (tip < 0) throw new IOException("keine Front gefunden (Eis berührt kein Wasser)");
        lat0 = g.north0 - (tip / W + 0.5) * g.sizeY;
        lon0 = east0 + (tip % W + 0.5) * sizeX;
        Geo.Tm tm = Geo.local(lat0, lon0);
        double shift = Math.hypot(Geo.local(Site.LAT, Site.LON).forward(lat0, lon0)[0], Geo.local(Site.LAT, Site.LON).forward(lat0, lon0)[1]);
        notes.add(String.format(Locale.ROOT, "Ursprung (Spitze der Front): %.7f, %.7f = %s%s", lat0, lon0, Site.dms(lat0, lon0),
                shift > 10 ? String.format(Locale.GERMAN, " – %.0f m von Site.LAT/LON, bitte dort eintragen", shift) : " – wie Site.LAT/LON"));
        // 4 Raster der Szene
        short[] hs = new short[N * N], bs = new short[N * N];
        byte[] src = new byte[N * N];
        float[] y = new float[N * N];
        boolean[] sIce = new boolean[N * N], sWater = new boolean[N * N], outside = new boolean[N * N];
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                double x = Terrain.X0 + (i + 0.5) * Terrain.CELL, z = Terrain.Z0 + (j + 0.5) * Terrain.CELL;
                double[] ll = tm.inverse(x, -z);
                double fc = (ll[1] - east0) / sizeX - 0.5, fr = (g.north0 - ll[0]) / g.sizeY - 0.5;
                int k = j * N + i;
                outside[k] = fc < 0 || fr < 0 || fc > W - 1 || fr > H - 1;
                y[k] = (float) (bicubic(d, W, H, fc, fr) - lake);
                // Masken bilinear und an 0,5 geschnitten: die Ränder folgen schräg dem Gelände statt den Treppen der 30-m-Zellen
                double mi = maskAt(ice, W, H, fc, fr), mw = maskAt(water, W, H, fc, fr);
                sIce[k] = mi >= 0.5;
                sWater[k] = !sIce[k] && mw > 0 && mw >= 1 - mi - mw;      // kein Landstreifen zwischen Eis und Wasser
            }
        // 5 Seegrund
        double[] dLand = edt(notLand(sIce, sWater)), dIce = edt(not(sIce));
        int lakeCells = 0;
        for (int k = 0; k < N * N; k++) {
            if (!sWater[k]) continue;
            lakeCells++;
            double sL = dLand[k] * Terrain.CELL, sI = dIce[k] * Terrain.CELL;
            double depth = DMAX * (1 - Math.exp(-sL / LBATH));
            depth = Math.max(depth, Site.FRONT_DRAFT * smooth(0, 150, sL) * Math.exp(-sI / 600));
            y[k] = (float) -Math.max(1.5, depth);
        }
        // Relief auf Fels [G]
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                int k = j * N + i;
                if (sWater[k] || sIce[k]) continue;
                double x = Terrain.X0 + (i + 0.5) * Terrain.CELL, z = Terrain.Z0 + (j + 0.5) * Terrain.CELL;
                y[k] += (float) (1.5 * (fbm(x / 23.0, z / 23.0) - 0.5) * smooth(0, 4, y[k]));
            }
        // 6 Grund unter dem Eis
        double[] dMargin = edt(sIce), dFront = edt(frontCells(sIce, sWater));
        for (int k = 0; k < N * N; k++) {
            hs[k] = Terrain.encode(y[k]);
            if (!sIce[k]) { bs[k] = hs[k]; src[k] = outside[k] ? Terrain.MODELLIERT : sWater[k] ? Terrain.SEE : Terrain.FELS; continue; }
            src[k] = Terrain.EIS;
            double up = dFront[k] * Terrain.CELL, edge = dMargin[k] * Terrain.CELL;
            double thick = ICE_FRONT + (ICE_UP - ICE_FRONT) * smooth(0, 8000, up);
            thick *= Math.sqrt(Math.min(1, edge / ICE_MARGIN)) * 0.85 + 0.15;
            bs[k] = Terrain.encode(Math.min(y[k] - 5, y[k] - thick));
        }
        // Front als Linie: Eiszellen am Wasser; Höhe der Oberkante = höchste Eiszelle bis 25 m landeinwärts
        // (das Höhenmodell glättet die Kante ins Wasser hinein); als Kette von Nachbar zu Nachbar geordnet
        List<double[]> fr = new ArrayList<>();
        for (int j = 4; j < N - 4; j += 2)
            for (int i = 4; i < N - 4; i += 2) {
                int k = j * N + i;
                if (!sIce[k] || !near(sWater, N, N, j, i, 2)) continue;
                double top = y[k];
                for (int dj = -4; dj <= 4; dj++) for (int di = -4; di <= 4; di++) { int o = (j + dj) * N + i + di; if (sIce[o]) top = Math.max(top, y[o]); }
                double x = Terrain.X0 + (i + 0.5) * Terrain.CELL, z = Terrain.Z0 + (j + 0.5) * Terrain.CELL;
                fr.add(new double[]{x, z, top});
            }
        List<double[]> line = arc(fr);
        double sumH = 0;
        for (double[] p : line) sumH += p[2];
        double flen = 0;
        for (int k = 1; k < line.size(); k++) { double dd = Math.hypot(line.get(k)[0] - line.get(k - 1)[0], line.get(k)[1] - line.get(k - 1)[1]); if (dd < 200) flen += dd; }
        // 7 Stege: auf der Halbinsel (östlich der Spitze) 25 bis 60 m über dem See, mindestens 300 m vor der Spitze [G];
        // die Pasarelas liegen am Hang über dem Ufer und schauen über den Kanal auf die Front
        double best = Double.MAX_VALUE, sx = 400, sz = 0, sy = 2;
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                int k = j * N + i;
                if (sIce[k] || sWater[k] || outside[k] || y[k] < 25 || y[k] > 60) continue;
                double x = Terrain.X0 + (i + 0.5) * Terrain.CELL, z = Terrain.Z0 + (j + 0.5) * Terrain.CELL, r = Math.hypot(x, z);
                if (r < 300 || x < 0) continue;
                if (r < best) { best = r; sx = x; sz = z; sy = y[k]; }
            }
        double blick = (Math.toDegrees(Math.atan2(-sx, sz)) + 360) % 360;
        Properties p = new Properties();
        p.setProperty("quelle", "Copernicus GLO-30 (" + tif.getName() + ")");
        p.setProperty("import.datum", LocalDate.now().toString());
        p.setProperty("see.ueberNN", fmt(lake));
        p.setProperty("ursprung.lat", String.format(Locale.ROOT, "%.7f", lat0));
        p.setProperty("ursprung.lon", String.format(Locale.ROOT, "%.7f", lon0));
        p.setProperty("steg.x", fmt(sx));
        p.setProperty("steg.z", fmt(sz));
        p.setProperty("steg.y", fmt(sy));
        p.setProperty("steg.blick", fmt(blick));
        p.setProperty("see.zellen", String.valueOf(lakeCells));
        p.setProperty("modell.seegrund", "Tiefe = 160 m (1 - exp(-s/350 m)), vor der Front 96 m [G]");
        p.setProperty("modell.eis", "Dicke 170 m an der Front bis 380 m in 8 km, Trog zu den Rändern [G]");
        notes.add(String.format(Locale.GERMAN, "Front: %d Punkte, %.1f km, Oberkante im Mittel %.0f m über dem See (Werkbuch: 74 m)", line.size(), flen / 1000, sumH / Math.max(1, line.size())));
        notes.add(String.format(Locale.GERMAN, "Stege: %.0f m vor der Mitte der Front (x %.0f, z %.0f, %.0f m über dem See), Blick %03.0f°", best, sx, sz, sy, blick));
        return new Terrain(hs, bs, src, line.toArray(new double[0][]), p);
    }

    // ------------------------------------------------------------------ Masken

    static boolean[] largeComponents(boolean[] m, int W, int H, int min) {
        int[] lab = label(m, W, H);
        int n = 0;
        for (int v : lab) n = Math.max(n, v);
        int[] size = new int[n + 1];
        for (int v : lab) size[v]++;
        boolean[] out = new boolean[m.length];
        for (int k = 0; k < m.length; k++) out[k] = lab[k] > 0 && size[lab[k]] >= min;
        return out;
    }

    /** Die größte Komponente von m, die an t grenzt. */
    static boolean[] largestTouching(boolean[] m, boolean[] t, int W, int H) {
        int[] lab = label(m, W, H);
        int n = 0;
        for (int v : lab) n = Math.max(n, v);
        int[] size = new int[n + 1];
        boolean[] touch = new boolean[n + 1];
        for (int r = 1; r < H - 1; r++)
            for (int c = 1; c < W - 1; c++) {
                int k = r * W + c, l = lab[k];
                if (l == 0) continue;
                size[l]++;
                if (t[k - 1] || t[k + 1] || t[k - W] || t[k + W] || t[k - W - 1] || t[k - W + 1] || t[k + W - 1] || t[k + W + 1]) touch[l] = true;
            }
        int best = 0;
        for (int l = 1; l <= n; l++) if (touch[l] && (best == 0 || size[l] > size[best])) best = l;
        boolean[] out = new boolean[m.length];
        for (int k = 0; k < m.length; k++) out[k] = best > 0 && lab[k] == best;
        return out;
    }

    static int[] label(boolean[] m, int W, int H) {
        int[] lab = new int[m.length];
        int next = 0;
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int s = 0; s < m.length; s++) {
            if (!m[s] || lab[s] != 0) continue;
            lab[s] = ++next;
            q.add(s);
            while (!q.isEmpty()) {
                int k = q.poll(), r = k / W, c = k % W;
                int[] nb = {c > 0 ? k - 1 : -1, c < W - 1 ? k + 1 : -1, r > 0 ? k - W : -1, r < H - 1 ? k + W : -1};
                for (int o : nb) if (o >= 0 && m[o] && lab[o] == 0) { lab[o] = next; q.add(o); }
            }
        }
        return lab;
    }

    /** Morphologisches Schließen mit Radius r (Quadrat). */
    static boolean[] close(boolean[] m, int W, int H, int r) { return erode(dilate(m, W, H, r), W, H, r); }

    static boolean[] dilate(boolean[] m, int W, int H, int r) {
        boolean[] o = new boolean[m.length];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                boolean v = false;
                for (int dy = -r; dy <= r && !v; dy++)
                    for (int dx = -r; dx <= r && !v; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx >= 0 && yy >= 0 && xx < W && yy < H && m[yy * W + xx]) v = true;
                    }
                o[y * W + x] = v;
            }
        return o;
    }

    static boolean[] erode(boolean[] m, int W, int H, int r) {
        boolean[] inv = not(m);
        boolean[] d = dilate(inv, W, H, r);
        return not(d);
    }

    /** Löcher füllen: was vom Rand aus nicht erreichbar ist, gehört dazu. */
    static void fillHoles(boolean[] m, int W, int H) {
        boolean[] reach = new boolean[m.length];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int x = 0; x < W; x++) { seed(m, reach, q, x); seed(m, reach, q, (H - 1) * W + x); }
        for (int y = 0; y < H; y++) { seed(m, reach, q, y * W); seed(m, reach, q, y * W + W - 1); }
        while (!q.isEmpty()) {
            int k = q.poll(), r = k / W, c = k % W;
            if (c > 0) seed(m, reach, q, k - 1);
            if (c < W - 1) seed(m, reach, q, k + 1);
            if (r > 0) seed(m, reach, q, k - W);
            if (r < H - 1) seed(m, reach, q, k + W);
        }
        for (int k = 0; k < m.length; k++) if (!reach[k]) m[k] = true;
    }

    private static void seed(boolean[] m, boolean[] reach, ArrayDeque<Integer> q, int k) {
        if (m[k] || reach[k]) return;
        reach[k] = true;
        q.add(k);
    }

    static boolean[] not(boolean[] m) { boolean[] r = new boolean[m.length]; for (int k = 0; k < m.length; k++) r[k] = !m[k]; return r; }

    /** Alles außer Fels (Eis und Wasser zählen nicht als Ufer). */
    static boolean[] notLand(boolean[] ice, boolean[] water) { boolean[] r = new boolean[ice.length]; for (int k = 0; k < r.length; k++) r[k] = ice[k] || water[k]; return r; }

    static boolean[] frontCells(boolean[] ice, boolean[] water) {
        boolean[] f = new boolean[ice.length];
        for (int j = 1; j < N - 1; j++)
            for (int i = 1; i < N - 1; i++) {
                int k = j * N + i;
                f[k] = !(ice[k] && near(water, N, N, j, i, 2));
            }
        for (int k = 0; k < N; k++) { f[k] = true; f[(N - 1) * N + k] = true; f[k * N] = true; f[k * N + N - 1] = true; }
        return f;
    }

    /**
     * Ordnet die Punkte der Front nach dem Winkel um einen Punkt 2 km westlich der Spitze (im Eis), vom Canal de los
     * Témpanos im Norden über die Spitze zum Brazo Rico im Süden, und dünnt auf mindestens 25 m Abstand aus.
     */
    static List<double[]> arc(List<double[]> pts) {
        List<double[]> s = new ArrayList<>(pts);
        s.sort((p, q) -> Double.compare(Math.atan2(p[1], p[0] + 2000), Math.atan2(q[1], q[0] + 2000)));
        List<double[]> thin = new ArrayList<>();
        for (double[] p : s) if (thin.isEmpty() || Math.hypot(p[0] - thin.get(thin.size() - 1)[0], p[1] - thin.get(thin.size() - 1)[1]) >= 25) thin.add(p);
        return thin;
    }

    /**
     * Ordnet Punkte zu einer Kette: Beginn am nördlichsten Punkt, dann immer der nächste noch freie Nachbar bis
     * maxGap m; zuletzt wird jeder zweite Punkt unter 25 m Abstand ausgedünnt. Die längste so gefundene Kette gilt.
     */
    static List<double[]> chain(List<double[]> pts, double maxGap) {
        List<double[]> best = new ArrayList<>();
        boolean[] used = new boolean[pts.size()];
        while (true) {
            int start = -1;
            for (int k = 0; k < pts.size(); k++) if (!used[k] && (start < 0 || pts.get(k)[1] < pts.get(start)[1])) start = k;
            if (start < 0) break;
            List<double[]> c = new ArrayList<>();
            int cur = start;
            used[cur] = true;
            c.add(pts.get(cur));
            while (true) {
                int nx = -1;
                double bd = maxGap;
                double[] p = pts.get(cur);
                for (int k = 0; k < pts.size(); k++) {
                    if (used[k]) continue;
                    double dd = Math.hypot(pts.get(k)[0] - p[0], pts.get(k)[1] - p[1]);
                    if (dd < bd) { bd = dd; nx = k; }
                }
                if (nx < 0) break;
                used[nx] = true;
                c.add(pts.get(nx));
                cur = nx;
            }
            if (c.size() > best.size()) best = c;
        }
        List<double[]> thin = new ArrayList<>();
        for (double[] p : best) if (thin.isEmpty() || Math.hypot(p[0] - thin.get(thin.size() - 1)[0], p[1] - thin.get(thin.size() - 1)[1]) >= 25) thin.add(p);
        return thin;
    }

    /** Ob m im Quadrat mit Halbmesser rad um (r, c) irgendwo wahr ist. */
    static boolean near(boolean[] m, int W, int H, int r, int c, int rad) {
        for (int dr = -rad; dr <= rad; dr++)
            for (int dc = -rad; dc <= rad; dc++) {
                int rr = r + dr, cc = c + dc;
                if (rr >= 0 && cc >= 0 && rr < H && cc < W && m[rr * W + cc]) return true;
            }
        return false;
    }

    /** Raster ohne doppelte Spalten: Werte, Breite, Länge der ersten Spaltenkante, Spaltenbreite (Grad), Zahl der entfernten. */
    record Grid(float[] d, int w, double east0, double sizeX, int dropped) { }

    static Grid dedupColumns(GeoTiff g) {
        int W = g.width, H = g.height;
        List<Integer> keep = new ArrayList<>();
        for (int c = 0; c < W; c++) {
            boolean dup = c > 0;
            for (int r = 0; r < H && dup; r++) if (Math.abs(g.data[r * W + c] - g.data[r * W + c - 1]) > 1e-4f) dup = false;
            if (!dup) keep.add(c);
        }
        int dropped = W - keep.size();
        if (dropped < W / 10) return new Grid(g.data, W, g.east0, g.sizeX, 0);
        int nw = keep.size();
        double step = (double) W / nw;    // 1,5 bei GLO-30 auf 50° bis 60°
        // die wahren Mitten liegen gleichmäßig; Versatz nach kleinsten Quadraten aus den behaltenen Spalten
        double off = 0;
        for (int k = 0; k < nw; k++) off += (keep.get(k) + 0.5) - step * k;
        off /= nw;
        float[] d = new float[nw * H];
        for (int r = 0; r < H; r++) for (int k = 0; k < nw; k++) d[r * nw + k] = g.data[r * W + keep.get(k)];
        double sizeX = g.sizeX * step, east0 = g.east0 + (off - step * 0.5) * g.sizeX;
        return new Grid(d, nw, east0, sizeX, dropped);
    }

    /** Maske (0/1) bilinear an der Stelle (Spalte fc, Zeile fr). */
    public static double maskAt(boolean[] m, int W, int H, double fc, double fr) {
        fc = Math.max(0, Math.min(W - 1, fc));
        fr = Math.max(0, Math.min(H - 1, fr));
        int c = Math.min(W - 2, (int) Math.floor(fc)), r = Math.min(H - 2, (int) Math.floor(fr));
        double u = fc - c, v = fr - r;
        double a = m[r * W + c] ? 1 : 0, b = m[r * W + c + 1] ? 1 : 0, d = m[(r + 1) * W + c] ? 1 : 0, e = m[(r + 1) * W + c + 1] ? 1 : 0;
        return (a * (1 - u) + b * u) * (1 - v) + (d * (1 - u) + e * u) * v;
    }

    static int count(boolean[] m) { int c = 0; for (boolean b : m) if (b) c++; return c; }

    /**
     * Euklidischer Abstand (in Zellen) jeder Zelle zur nächsten Zelle, an der inside falsch ist (Felzenszwalb und
     * Huttenlocher); Zellen mit inside = false haben 0.
     */
    static double[] edt(boolean[] inside) {
        double INF = 1e20;
        double[] f = new double[N * N];
        for (int k = 0; k < f.length; k++) f[k] = inside[k] ? INF : 0;
        double[] col = new double[N], out = new double[N];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) col[j] = f[j * N + i];
            edt1(col, out);
            for (int j = 0; j < N; j++) f[j * N + i] = out[j];
        }
        for (int j = 0; j < N; j++) {
            System.arraycopy(f, j * N, col, 0, N);
            edt1(col, out);
            System.arraycopy(out, 0, f, j * N, N);
        }
        for (int k = 0; k < f.length; k++) f[k] = Math.sqrt(f[k]);
        return f;
    }

    static void edt1(double[] f, double[] d) {
        int n = f.length;
        int[] v = new int[n];
        double[] z = new double[n + 1];
        int k = 0;
        v[0] = 0;
        z[0] = Double.NEGATIVE_INFINITY;
        z[1] = Double.POSITIVE_INFINITY;
        for (int q = 1; q < n; q++) {
            double s;
            while (true) {
                s = ((f[q] + q * (double) q) - (f[v[k]] + v[k] * (double) v[k])) / (2.0 * q - 2.0 * v[k]);
                if (s <= z[k]) { k--; if (k < 0) { k = 0; break; } } else break;
            }
            k++;
            v[k] = q;
            z[k] = s;
            z[k + 1] = Double.POSITIVE_INFINITY;
        }
        k = 0;
        for (int q = 0; q < n; q++) {
            while (z[k + 1] < q) k++;
            double dq = q - v[k];
            d[q] = dq * dq + f[v[k]];
        }
    }

    // ------------------------------------------------------------------ Helfer

    /** Bikubisch (Catmull-Rom) im Raster an (fc, fr) in Pixeln, Rand geklemmt. */
    static double bicubic(float[] d, int W, int H, double fc, double fr) {
        int c = (int) Math.floor(fc), r = (int) Math.floor(fr);
        double tx = fc - c, ty = fr - r, sum = 0;
        for (int m = -1; m <= 2; m++) {
            double row = 0;
            for (int n = -1; n <= 2; n++) {
                int cc = Math.max(0, Math.min(W - 1, c + n)), rr = Math.max(0, Math.min(H - 1, r + m));
                row += d[rr * W + cc] * cr(n - tx);
            }
            sum += row * cr(m - ty);
        }
        return sum;
    }

    static double cr(double x) {
        x = Math.abs(x);
        if (x < 1) return 1.5 * x * x * x - 2.5 * x * x + 1;
        if (x < 2) return -0.5 * x * x * x + 2.5 * x * x - 4 * x + 2;
        return 0;
    }

    static double smooth(double a, double b, double x) {
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
        double a = hash(ix, iy), b = hash(ix + 1, iy), c = hash(ix, iy + 1), e = hash(ix + 1, iy + 1);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + e) * fx * fy;
    }

    static double fbm(double x, double y) { return 0.57 * vnoise(x, y) + 0.29 * vnoise(x * 2.03 + 7.1, y * 2.03 - 3.3) + 0.14 * vnoise(x * 4.1 - 1.7, y * 4.1 + 5.2); }

    static String fmt(double v) { return String.format(Locale.ROOT, "%.2f", v); }

    /** Vorschau von oben: Fels nach Höhe schattiert, See blau nach Tiefe, Eis weiß, Front rot, Stege gelb. */
    static void writePreview(Terrain t, File png) throws IOException {
        int s = 1024, step = N / s;
        BufferedImage im = new BufferedImage(s, s, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < s; j++)
            for (int i = 0; i < s; i++) {
                int ci = i * step, cj = j * step, k = cj * N + ci;
                double y = Terrain.decode(t.h[k]);
                double hx = t.at(ci + 1, cj) - t.at(ci - 1, cj), hz = t.at(ci, cj + 1) - t.at(ci, cj - 1);
                double shade = Math.max(0.25, Math.min(1.3, 0.85 - (hx * 0.6 + hz * 0.4) / (2 * Terrain.CELL)));
                int r, g, b;
                if (t.src[k] == Terrain.SEE) { double dd = Math.min(1, -y / 160); r = (int) (40 - 25 * dd); g = (int) (170 - 90 * dd); b = (int) (175 - 50 * dd); }
                else if (t.src[k] == Terrain.EIS) { r = (int) (225 * shade); g = (int) (235 * shade); b = (int) (245 * shade); }
                else { double e = Math.min(1, y / 1400); r = (int) ((95 + 90 * e) * shade); g = (int) ((100 + 70 * e) * shade); b = (int) ((80 + 80 * e) * shade); }
                im.setRGB(i, j, (Math.min(255, Math.max(0, r)) << 16) | (Math.min(255, Math.max(0, g)) << 8) | Math.min(255, Math.max(0, b)));
            }
        java.awt.Graphics2D g = im.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        double sc = s / (N * Terrain.CELL);
        g.setColor(new java.awt.Color(230, 60, 40));
        for (double[] p : t.front) g.fillOval((int) ((p[0] - Terrain.X0) * sc) - 1, (int) ((p[1] - Terrain.Z0) * sc) - 1, 3, 3);
        g.setColor(new java.awt.Color(255, 220, 60));
        g.fillOval((int) ((t.steg[0] - Terrain.X0) * sc) - 5, (int) ((t.steg[1] - Terrain.Z0) * sc) - 5, 10, 10);
        g.setColor(java.awt.Color.WHITE);
        int ox = (int) (-Terrain.X0 * sc), oz = (int) (-Terrain.Z0 * sc);
        g.drawLine(ox - 6, oz, ox + 6, oz);
        g.drawLine(ox, oz - 6, ox, oz + 6);
        g.drawString("N ↑ · 1 km = " + Math.round(1000 * sc) + " px · " + t.describe(), 8, s - 10);
        g.dispose();
        File dir = png.getAbsoluteFile().getParentFile();
        if (dir != null) dir.mkdirs();
        javax.imageio.ImageIO.write(im, "png", png);
    }

    private GelaendeImport() { }
}
