package com.dan.glacier.scene;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Das Gelände der Szene (Phase 2, F2 A, F3 A): ein Höhenraster von 2048 × 2048 Zellen zu 6,25 m, also 12,8 km um die
 * Front, Höhen in m über dem Spiegel des Lago Argentino (y = 0). Zwei Flächen: die <b>Oberfläche</b> (Fels, Moräne,
 * Gletscher, Seegrund – was man sieht) und der <b>Grund</b> (unter dem Eis modelliert [G]; sonst gleich der
 * Oberfläche). Dazu je Zelle die Herkunft und die Linie der Front. Erzeugt von {@code tools.GelaendeImport} aus
 * Copernicus GLO-30, gelesen aus {@code daten/gelaende/}. Ohne diese Dateien entsteht die Bühne von Phase 1
 * ({@link Buehne}).
 * <p>
 * Höhen liegen als 16-Bit-Werte vor ({@link #HMIN} bis {@link #HMAX}, 3 cm Stufe); dieselben Werte gehen als
 * R16-Textur auf die Grafikkarte, darum sehen Kamera und Bild dieselbe Fläche (bilinear).
 */
public final class Terrain implements com.dan.glacier.wiring.Terrain {
    public static final int N = 2048;
    public static final double CELL = 6.25;
    /** Lage der Nordwestecke des Rasters in der Szene; Zelle (i, j) hat ihre Mitte bei X0 + (i + 0,5) · CELL. */
    public static final double X0 = -7000, Z0 = -6400;
    public static final double HMIN = -250, HMAX = 1750;
    /** Herkunft je Zelle. */
    public static final byte FELS = 0, SEE = 1, EIS = 2, MODELLIERT = 3;

    /** Oberfläche und Grund als vorzeichenlose 16-Bit-Werte, Zeile für Zeile von Nord nach Süd. */
    public final short[] h, bed;
    public final byte[] src;
    /** Die Front als Linie: je Punkt x, z, Höhe der Oberkante über dem See (m); vom Canal de los Témpanos zum Brazo Rico. */
    public final double[][] front;
    /** Die Stege auf der Halbinsel: x, z, Höhe des Bodens, Blickrichtung zur Mitte der Front (Grad). */
    public final double[] steg;
    /** Spiegel des Sees über dem Meer im Höhenmodell (m). */
    public final double lakeAsl;
    public final String source;
    public final Properties props;
    private final float[][] minPyr = new float[LEVELS][], maxPyr = new float[LEVELS][];
    private final int[] side = new int[LEVELS];
    private static final int LEVELS = 12;

    public Terrain(short[] h, short[] bed, byte[] src, double[][] front, Properties props) {
        this.h = h;
        this.bed = bed;
        this.src = src;
        this.front = front;
        this.props = props;
        this.lakeAsl = Double.parseDouble(props.getProperty("see.ueberNN", String.valueOf(Site.LAKE_LEVEL_ASL)));
        this.steg = new double[]{Double.parseDouble(props.getProperty("steg.x", String.valueOf(Buehne.TIP + 30))),
                Double.parseDouble(props.getProperty("steg.z", "20")), Double.parseDouble(props.getProperty("steg.y", String.valueOf(Buehne.LAND))),
                Double.parseDouble(props.getProperty("steg.blick", "272"))};
        this.source = props.getProperty("quelle", "Bühne von Phase 1 (keine Höhendaten)");
        pyramid();
    }

    /** Ob echte Höhendaten geladen sind (sonst die Bühne von Phase 1). */
    public boolean measured() { return props.containsKey("quelle"); }

    // ------------------------------------------------------------------ Abfragen

    public static short encode(double y) {
        double v = (y - HMIN) / (HMAX - HMIN) * 65535.0;
        return (short) (int) Math.max(0, Math.min(65535, Math.round(v)));
    }

    public static double decode(short s) { return HMIN + (s & 0xFFFF) / 65535.0 * (HMAX - HMIN); }

    public double at(int i, int j) { return decode(h[clampIndex(i, j)]); }
    public double bedAt(int i, int j) { return decode(bed[clampIndex(i, j)]); }

    static int clampIndex(int i, int j) {
        i = Math.max(0, Math.min(N - 1, i));
        j = Math.max(0, Math.min(N - 1, j));
        return j * N + i;
    }

    public byte sourceAt(double x, double z) {
        int i = (int) Math.floor((x - X0) / CELL), j = (int) Math.floor((z - Z0) / CELL);
        if (i < 0 || j < 0 || i >= N || j >= N) return MODELLIERT;
        return src[j * N + i];
    }

    public boolean ice(double x, double z) { return sourceAt(x, z) == EIS; }

    /** Oberfläche an (x, z), bilinear zwischen den Zellmitten wie die Textur; außerhalb des Rasters der Rand. */
    @Override
    public double height(double x, double z) { return bilinear(h, x, z); }

    /** Grund unter dem Eis (und überall sonst die Oberfläche). */
    public double bed(double x, double z) { return bilinear(bed, x, z); }

    private static double bilinear(short[] a, double x, double z) {
        double u = (x - X0) / CELL - 0.5, v = (z - Z0) / CELL - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fx = u - i, fz = v - j;
        double p = decode(a[clampIndex(i, j)]), q = decode(a[clampIndex(i + 1, j)]), r = decode(a[clampIndex(i, j + 1)]), s = decode(a[clampIndex(i + 1, j + 1)]);
        return (p * (1 - fx) + q * fx) * (1 - fz) + (r * (1 - fx) + s * fx) * fz;
    }

    /** Wassertiefe am Ort bis zum Seespiegel (m, 0 = trocken oder Eis). */
    @Override
    public double waterDepth(double x, double z) {
        if (ice(x, z)) return 0;
        return Math.max(0, -height(x, z));
    }

    /** Kleinste und größte Höhe im Quadrat (x0, z0, Kante) aus der Min-Max-Pyramide (für das Ausblenden von Knoten). */
    public void range(double x0, double z0, double size, double[] out) {
        int i0 = (int) Math.floor((x0 - X0) / CELL) - 1, j0 = (int) Math.floor((z0 - Z0) / CELL) - 1;
        int i1 = (int) Math.ceil((x0 + size - X0) / CELL) + 1, j1 = (int) Math.ceil((z0 + size - Z0) / CELL) + 1;
        i0 = Math.max(0, Math.min(N - 1, i0)); j0 = Math.max(0, Math.min(N - 1, j0));
        i1 = Math.max(0, Math.min(N - 1, i1)); j1 = Math.max(0, Math.min(N - 1, j1));
        int span = Math.max(i1 - i0, j1 - j0) + 1, k = 0;
        while (k < LEVELS - 1 && (1 << (k + 1)) * 2 <= span) k++;
        int s = side[k];
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int j = j0 >> k; j <= j1 >> k; j++)
            for (int i = i0 >> k; i <= i1 >> k; i++) {
                int ii = Math.min(s - 1, i), jj = Math.min(s - 1, j);
                lo = Math.min(lo, minPyr[k][jj * s + ii]);
                hi = Math.max(hi, maxPyr[k][jj * s + ii]);
            }
        out[0] = lo;
        out[1] = hi;
    }

    private void pyramid() {
        float[] base = new float[N * N];
        for (int i = 0; i < base.length; i++) base[i] = (float) decode(h[i]);
        minPyr[0] = base;
        maxPyr[0] = base;
        side[0] = N;
        int n = N;
        for (int k = 1; k < LEVELS; k++) {
            int m = (n + 1) / 2;
            float[] mn = new float[m * m], mx = new float[m * m], pmn = minPyr[k - 1], pmx = maxPyr[k - 1];
            for (int j = 0; j < m; j++)
                for (int i = 0; i < m; i++) {
                    float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
                    for (int b = 0; b < 2; b++)
                        for (int a = 0; a < 2; a++) {
                            int ii = Math.min(n - 1, 2 * i + a), jj = Math.min(n - 1, 2 * j + b);
                            lo = Math.min(lo, pmn[jj * n + ii]);
                            hi = Math.max(hi, pmx[jj * n + ii]);
                        }
                    mn[j * m + i] = lo;
                    mx[j * m + i] = hi;
                }
            minPyr[k] = mn;
            maxPyr[k] = mx;
            side[k] = m;
            n = m;
        }
    }

    /** Anteil der Zellen je Herkunft (Fels, See, Eis, modelliert). */
    public double[] sourceShares() {
        long[] c = new long[4];
        for (byte b : src) c[Math.max(0, Math.min(3, b))]++;
        double t = src.length;
        return new double[]{c[0] / t, c[1] / t, c[2] / t, c[3] / t};
    }

    /** Längste Lücke (m) zwischen zwei Frontpunkten, die noch als Front zählt; größere springen über Land. */
    public static final double FRONT_GAP = 200;

    /** Länge der Front (m, ohne Lücken über {@link #FRONT_GAP}) und mittlere Höhe ihrer Oberkante über dem See (m). */
    public double[] frontStats() {
        double len = 0, hs = 0;
        for (int k = 0; k < front.length; k++) {
            if (k > 0) {
                double d = Math.hypot(front[k][0] - front[k - 1][0], front[k][1] - front[k - 1][1]);
                if (d <= FRONT_GAP) len += d;
            }
            hs += front[k][2];
        }
        return new double[]{len, front.length == 0 ? 0 : hs / front.length};
    }

    /** „Copernicus GLO-30 · Fels 61 %, See 17 %, Eis 21 % · Front 4,6 km, Oberkante im Mittel 62 m“ */
    public String describe() {
        double[] s = sourceShares(), f = frontStats();
        return String.format(Locale.GERMAN, "%s · Fels %.0f %%, See %.0f %%, Eis %.0f %% · Front %.1f km, Oberkante im Mittel %.0f m", source,
                s[0] * 100, s[1] * 100, s[2] * 100, f[0] / 1000, f[1]);
    }

    // ------------------------------------------------------------------ Dateien

    public static final String HOEHE = "hoehe.r16", GRUND = "grund.r16", HERKUNFT = "herkunft.u8", FRONT_CSV = "front.csv", INFO = "gelaende.properties";

    /** Liest {@code daten/gelaende} (oder die Bühne, wenn es fehlt). */
    public static Terrain load(File dir) {
        File hf = new File(dir, HOEHE), gf = new File(dir, GRUND), sf = new File(dir, HERKUNFT), pf = new File(dir, INFO);
        if (!hf.isFile() || !gf.isFile() || !sf.isFile() || !pf.isFile()) return flat();
        try {
            Properties p = new Properties();
            try (InputStream in = new FileInputStream(pf)) { p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)); }
            short[] h = readShorts(hf), b = readShorts(gf);
            byte[] src = Files.readAllBytes(sf.toPath());
            if (src.length != N * N) throw new IOException(sf + ": falsche Größe");
            List<double[]> fr = new ArrayList<>();
            File ff = new File(dir, FRONT_CSV);
            if (ff.isFile()) for (String line : Files.readAllLines(ff.toPath())) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.split(";");
                fr.add(new double[]{Double.parseDouble(f[0].trim()), Double.parseDouble(f[1].trim()), Double.parseDouble(f[2].trim())});
            }
            return new Terrain(h, b, src, fr.toArray(new double[0][]), p);
        } catch (IOException | RuntimeException e) {
            System.err.println("Gelände: " + e.getMessage() + " – es bleibt die Bühne von Phase 1");
            return flat();
        }
    }

    private static short[] readShorts(File f) throws IOException {
        byte[] raw = Files.readAllBytes(f.toPath());
        if (raw.length != N * N * 2) throw new IOException(f + ": " + raw.length + " Bytes statt " + N * N * 2);
        short[] s = new short[N * N];
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s);
        return s;
    }

    /** Ordner {@code daten/gelaende} neben dem Programm ({@code -Dglacier.daten=…} setzt ihn anders). */
    public static File defaultDir() {
        String d = System.getProperty("glacier.daten");
        return new File(d != null ? d : "daten", "gelaende");
    }

    public void save(File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Ordner " + dir + " lässt sich nicht anlegen");
        writeShorts(new File(dir, HOEHE), h);
        writeShorts(new File(dir, GRUND), bed);
        Files.write(new File(dir, HERKUNFT).toPath(), src);
        StringBuilder sb = new StringBuilder("# Front des Perito Moreno: x; z; Oberkante über dem See (m, Szene)\n");
        for (double[] p : front) sb.append(String.format(Locale.ROOT, "%.1f;%.1f;%.1f%n", p[0], p[1], p[2]));
        Files.writeString(new File(dir, FRONT_CSV).toPath(), sb.toString());
        try (OutputStream out = new java.io.FileOutputStream(new File(dir, INFO))) {
            props.store(new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8), "Gelaende der Szene Glacier (tools.GelaendeImport)");
        }
    }

    private static void writeShorts(File f, short[] s) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(N * N * 2).order(ByteOrder.LITTLE_ENDIAN);
        bb.asShortBuffer().put(s);
        Files.write(f.toPath(), bb.array());
    }

    /** Die Bühne von Phase 1 als Gelände: See 100 m tief, Eisblock 74 m, Halbinsel 2 m ({@link Buehne}). */
    public static Terrain flat() {
        short[] h = new short[N * N], b = new short[N * N];
        byte[] s = new byte[N * N];
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                double x = X0 + (i + 0.5) * CELL, z = Z0 + (j + 0.5) * CELL;
                boolean ice = Buehne.ice(x, z), land = Buehne.land(x, z);
                double y = ice ? Site.FRONT_HEIGHT : land ? Buehne.LAND : -100;
                h[j * N + i] = encode(y);
                b[j * N + i] = encode(ice ? -Site.FRONT_DRAFT : y);
                s[j * N + i] = ice ? EIS : land ? FELS : SEE;
            }
        double[][] fr = {{0, -Buehne.HALF_FRONT, Site.FRONT_HEIGHT}, {0, Buehne.HALF_FRONT, Site.FRONT_HEIGHT}};
        return new Terrain(h, b, s, fr, new Properties());
    }
}
