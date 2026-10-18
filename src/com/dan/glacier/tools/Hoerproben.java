package com.dan.glacier.tools;

import com.dan.glacier.scene.Gletscher;
import com.dan.glacier.scene.Klang;
import com.dan.glacier.scene.Stege;
import com.dan.glacier.scene.Terrain;
import com.dan.glacier.scene.Welle;
import com.dan.glacier.wiring.SoundEvent;
import com.dan.glacier.wiring.Wiring;
import com.dan.ice.Glacier;
import com.dan.soundscape.Wav;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 12: die Hörproben der Abnahme als WAV, ohne Fenster und ohne Lautsprecher, aus derselben Verdrahtung wie die App
 * ({@link Klang}) am echten Gelände: Steg am Morgen mit einem großen Bruch, auf dem Eis im Wind, Bootsfahrt, die Nacht.
 * Dazu ein Bild mit den vier Spektrogrammen.
 * <pre>
 * Hoerproben [ordner]          (Vorgabe docs/hoerproben)
 * </pre>
 */
public final class Hoerproben {
    private Hoerproben() { }

    /** Ein Ereignis zu einer Zeit (s): eine Aktion auf dem Klang. */
    record Cue(double t, java.util.function.Consumer<Klang> act, String what) { }

    record Probe(String file, String title, double seconds, double[] ear, double yaw, double wind, double windFrom, double rain, double air,
                 double hs, double period, boolean onIce, boolean forest, boolean birds, double boatSpeed, boolean aboard, List<Cue> cues) { }

    public static void main(String[] a) throws Exception {
        File dir = new File(a.length > 0 ? a[0] : "docs/hoerproben");
        dir.mkdirs();
        Terrain t = Terrain.load(Terrain.defaultDir());
        Gletscher g = new Gletscher(t);
        Glacier gl = g.glacier;
        gl.setAdvance(Wiring.glacierAdvance(16));
        Stege st = new Stege(t, 0);
        double[] fr = Welle.front(t, gl, 0);
        double[][] frontPts = Klang.frontPoints(gl.frontLines());
        Stege.Balkon bal = st.balkone.get(0);
        double[] steg = {bal.x(), bal.y() + 1.7, bal.z()};
        // die Front, auf die man vom Steg blickt (Blick 224°)
        double[] blick = hit(gl, steg, t.steg[3]);
        // eine Stelle auf dem Eis: 900 m hinter der Front im Blick
        double ix = blick[0] + Math.sin(Math.toRadians(t.steg[3])) * 900, iz = blick[1] - Math.cos(Math.toRadians(t.steg[3])) * 900;
        double[] ice = {ix, gl.surface(ix, iz) + 1.7, iz};
        // das Boot 300 m vor der Front, die Front zur Linken
        double[] boat = {fr[0] + fr[2] * 300, 6.2, fr[1] + fr[3] * 300};
        double boatYaw = (Math.toDegrees(Math.atan2(-fr[3], fr[2])) + 360) % 360;   // längs der Front
        double[] shore = {st.pierStart[0], 0, st.pierStart[1]};

        List<Probe> probes = new ArrayList<>();
        probes.add(new Probe("Steg_Morgen.wav", "Steg am Morgen mit einem großen Bruch (490 m)", 55, steg, t.steg[3], 2.5, 270, 0, 4, 0.15, 2.5, false, true, true, 0, false,
                List.of(cue(6, k -> crackSeries(k, blick, 0, 6, 0.6), "Knacken an der Front"),
                        cue(9, k -> k.sound(SoundEvent.Kind.AECHZEN, blick[0], 50, blick[1], 1), "Ächzen"),
                        cue(12, k -> calve(k, blick, 30_000), "Bruch: Donner aus dem Riss, 30 000 m³"),
                        cue(15, k -> k.sound(SoundEvent.Kind.AUFSCHLAG, blick[0], 0, blick[1], 30_000), "Aufschlag"),
                        cue(16.5, k -> k.sound(SoundEvent.Kind.AUFSCHLAG, blick[0] + 20, 0, blick[1] + 10, 4_000), "Brocken"),
                        cue(40, k -> k.sound(SoundEvent.Kind.AUFLAUFEN, shore[0], 0, shore[2], 1.6), "die Welle läuft am Ufer auf"),
                        cue(44, k -> k.sound(SoundEvent.Kind.AUFLAUFEN, shore[0] + 30, 0, shore[2] - 20, 1.0), "zweite Welle"))));
        probes.add(new Probe("Eis_im_Wind.wav", "Auf dem Eis im Wind (Bft 7)", 40, ice, t.steg[3] + 180, 15.5, 270, 0, 2, 0, 3, true, false, false, 0, false,
                List.of(cue(5, k -> k.sound(SoundEvent.Kind.KNACKEN, ice[0] + 40, ice[1] - 30, ice[2] + 10, 0.9), "Knacken unter den Füßen"),
                        cue(14, k -> k.sound(SoundEvent.Kind.AECHZEN, ice[0] - 80, ice[1] - 60, ice[2], 1), "Ächzen in der Tiefe"),
                        cue(24, k -> crackSeries(k, new double[]{ice[0] + 150, ice[2] - 60}, ice[1] - 20, 4, 0.8), "Spalte reißt"))));
        probes.add(new Probe("Bootsfahrt.wav", "Bootsfahrt 300 m vor der Front, ein Turm bricht", 50, boat, boatYaw, 4.5, 270, 0, 9, 0.3, 2.8, false, false, true, 3.2, true,
                List.of(cue(4, k -> k.sound(SoundEvent.Kind.KONDOR, boat[0] + 25, boat[1] + 15, boat[2] - 10, 1), "ein Kondor zieht vorbei"),
                        cue(8, k -> crackSeries(k, new double[]{fr[0], fr[1]}, 0, 5, 0.8), "Knacken"),
                        cue(11, k -> calve(k, new double[]{fr[0], fr[1]}, 50_000), "Bruch, 50 000 m³"),
                        cue(14, k -> k.sound(SoundEvent.Kind.AUFSCHLAG, fr[0] + fr[2] * 15, 0, fr[1] + fr[3] * 15, 50_000), "Aufschlag"))));
        probes.add(new Probe("Nacht.wav", "Die Nacht am Steg", 50, steg, t.steg[3], 1.0, 270, 0, 1, 0.08, 2.0, false, true, false, 0, false,
                List.of(cue(7, k -> k.sound(SoundEvent.Kind.KNACKEN, blick[0] + 300, 40, blick[1] - 200, 0.5), "fernes Knacken"),
                        cue(20, k -> k.sound(SoundEvent.Kind.AECHZEN, blick[0], 40, blick[1], 0.8), "Ächzen"),
                        cue(32, k -> calve(k, new double[]{blick[0] - 900, blick[1] - 700}, 8_000), "ferner Bruch (1,5 km)"))));

        List<float[]> spectra = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        int sr = 0;
        for (Probe p : probes) {
            Klang k = new Klang();
            k.front = frontPts;
            k.stege(st);
            k.summer = 1;
            if (!p.birds) k.groanEvery = 45;
            sr = k.s.sampleRate();
            int block = sr / 60;
            float[] all = new float[(int) (p.seconds * 60) * block * 2];
            int ci = 0;
            List<Cue> cues = new ArrayList<>(p.cues);
            SplittableRandomish birds = new SplittableRandomish(p.file.hashCode());
            double[] levels = new double[(int) p.seconds + 1];
            for (int f = 0; f < (int) (p.seconds * 60); f++) {
                double tt = f / 60.0;
                while (ci < cues.size() && cues.get(ci).t <= tt) cues.get(ci++).act.accept(k);
                if (p.birds && birds.next() < 1 / 60.0 / 6) {   // eine Möwe alle 6 s in 30 bis 150 m
                    double an = birds.next() * 6.283, d = 30 + 120 * birds.next();
                    k.sound(SoundEvent.Kind.MOEWE, p.ear[0] + Math.sin(an) * d, p.ear[1] + 10, p.ear[2] - Math.cos(an) * d, 0.5 + 0.5 * birds.next());
                }
                k.boat(p.ear[0], p.ear[1] - 5, p.ear[2], p.boatSpeed, p.aboard);
                if (!p.aboard) k.diesel.levelDb = 0;
                k.step(1 / 60.0, p.ear, p.yaw, p.wind, p.windFrom, 0.3, p.rain, p.air, p.hs, p.period, 0, p.onIce, p.forest);
                float[] o = k.s.render(1 / 60.0);
                System.arraycopy(o, 0, all, f * block * 2, Math.min(o.length, block * 2));
                double sum = 0;
                for (float v : o) sum += v * v;
                int sec = (int) tt;
                levels[sec] = Math.max(levels[sec], 104 + 10 * Math.log10(Math.max(1e-12, sum / o.length)));
            }
            File out = new File(dir, p.file);
            Wav.write(out, all, sr);
            StringBuilder lv = new StringBuilder();
            for (int i = 0; i < levels.length; i += 5) lv.append(String.format(Locale.GERMAN, "%s%.0f", i > 0 ? " " : "", levels[i]));
            System.out.println(String.format(Locale.GERMAN, "%s: %.0f s, %,d Hz · %s%n  Pegel je 5 s (dB, Ausgang): %s", out.getPath(), p.seconds, sr, k.describe(), lv));
            for (Cue c : p.cues) System.out.println(String.format(Locale.GERMAN, "  %4.1f s  %s", c.t, c.what));
            spectra.add(all);
            titles.add(p.title);
        }
        File png = new File(dir, "Spektrogramme.png");
        spectrograms(spectra, titles, sr, png);
        System.out.println("Bild: " + png.getPath());
    }

    static Cue cue(double t, java.util.function.Consumer<Klang> a, String what) { return new Cue(t, a, what); }

    /** Knacken in Folge an der Stelle {x, z} in Höhe y. */
    static void crackSeries(Klang k, double[] p, double y, int n, double st) {
        for (int i = 0; i < n; i++) k.sound(SoundEvent.Kind.KNACKEN, p[0] + 10 * i, y + 20 + 8 * i, p[1] - 5 * i, st * (0.6 + 0.4 * i / Math.max(1, n - 1)));
    }

    /** Ein Bruch: Riss als Weg (senkrecht 70 m, leicht schräg) und Donner. */
    static void calve(Klang k, double[] p, double volume) {
        double[] path = new double[15 * 3];
        for (int i = 0; i < 15; i++) { path[i * 3] = p[0] + 2 * i; path[i * 3 + 1] = 5 * i; path[i * 3 + 2] = p[1] + 1.5 * i; }
        k.crack(path, 5e6, true);
        k.broke(volume);
    }

    /** Wo der Blick vom Ort in Richtung heading (Grad) die Front trifft {x, z}. */
    static double[] hit(Glacier g, double[] e, double heading) {
        double h = Math.toRadians(heading), dx = Math.sin(h), dz = -Math.cos(h);
        for (double d = 20; d < 4000; d += 5) if (g.covers(e[0] + dx * d, e[2] + dz * d)) return new double[]{e[0] + dx * (d - 5), e[2] + dz * (d - 5)};
        return new double[]{e[0] + dx * 400, e[2] + dz * 400};
    }

    /** Ein einfacher Zufall ohne java.util.Random (gleicher Start, gleiche Vögel). */
    static final class SplittableRandomish {
        private final java.util.SplittableRandom r;
        SplittableRandomish(long seed) { r = new java.util.SplittableRandom(seed); }
        double next() { return r.nextDouble(); }
    }

    /** Vier Spektrogramme (0 bis 8 kHz, logarithmisch, 60 dB) untereinander. */
    static void spectrograms(List<float[]> data, List<String> titles, int sr, File out) throws Exception {
        int W = 1600, rowH = 230, H = 40 + rowH * data.size();
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(14, 20, 28));
        g.fillRect(0, 0, W, H);
        g.setColor(new Color(225, 232, 238));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString("Glacier · Phase 12: Hörproben (Spektrogramm 20 Hz bis 8 kHz, logarithmisch)", 20, 26);
        int N = 2048;
        for (int r = 0; r < data.size(); r++) {
            float[] st = data.get(r);
            int frames = st.length / 2, cols = W - 80, y0 = 40 + r * rowH + 20, h = rowH - 40;
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            g.setColor(new Color(200, 212, 222));
            g.drawString(titles.get(r), 70, y0 - 4);
            double[] re = new double[N], im = new double[N];
            for (int c = 0; c < cols; c++) {
                int at = (int) ((long) c * (frames - N) / cols);
                for (int i = 0; i < N; i++) {
                    double w = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (N - 1));
                    re[i] = (st[(at + i) * 2] + st[(at + i) * 2 + 1]) * 0.5 * w;
                    im[i] = 0;
                }
                fft(re, im);
                for (int y = 0; y < h; y++) {
                    double f = 20 * Math.pow(8000 / 20.0, 1 - y / (double) h);
                    int b = (int) Math.round(f * N / sr);
                    double m = Math.hypot(re[b], im[b]) / N + 1e-12;
                    double db = 20 * Math.log10(m), u = Math.max(0, Math.min(1, (db + 90) / 60));
                    img.setRGB(70 + c, y0 + y, heat(u));
                }
            }
            g.setColor(new Color(150, 165, 178));
            for (int f : new int[]{50, 200, 1000, 4000}) {
                int y = (int) (h * (1 - Math.log(f / 20.0) / Math.log(8000 / 20.0)));
                g.drawString(f >= 1000 ? f / 1000 + " kHz" : f + " Hz", 8, y0 + y + 4);
            }
            double secs = frames / (double) sr;
            for (int s = 0; s <= secs; s += 10) g.drawString(s + " s", 70 + (int) (cols * s / secs) - 8, y0 + h + 14);
        }
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", out);
    }

    static int heat(double u) {
        double r = Math.min(1, Math.max(0, 1.6 * u - 0.3)), gr = Math.min(1, Math.max(0, 1.8 * u - 0.8)), b = Math.min(1, Math.max(0, 0.25 + 1.2 * u - Math.max(0, 2.2 * u - 1.2)));
        return new Color((float) r, (float) gr, (float) (b * 0.9)).getRGB();
    }

    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len, wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    double ur = re[i + k], ui = im[i + k], vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci, vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr;
                    re[i + k] = ur + vr; im[i + k] = ui + vi;
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi;
                    double nr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = nr;
                }
            }
        }
    }
}
