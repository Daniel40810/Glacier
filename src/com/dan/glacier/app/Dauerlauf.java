package com.dan.glacier.app;

import com.dan.glacier.gl.Renderer;
import com.dan.glacier.scene.Regie;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 16: der Dauerlauf (Abnahme „fertig“). Die App läuft mit Fenster und Grafikkarte so lange wie verlangt (Vorgabe
 * 30 min) und spielt dabei im Wechsel:
 * <ol>
 * <li>die drei Regiebücher im Kino, eins nach dem anderen,</li>
 * <li>dann drei Minuten frei im Zeitraffer 120 mit der Regie der Kalbung (Abbrüche, Eisberge, Welle, Chronik).</li>
 * </ol>
 * Alle 5 s misst er Bilder/s, Grafikkarte, Prozessor, belegten Speicher, Fäden, Eisberge, Stücke der Bruch-Engine,
 * Schritte der Physik, die nicht nachkamen, und Fehler. Am Ende schreibt er {@code docs/status/Dauerlauf_….png} (drei
 * Kurven) und {@code …txt} mit dem Urteil:
 * <ul>
 * <li>keine Fehler (Fäden, GL, Chronik);</li>
 * <li>im Mittel mindestens 30 Bilder/s;</li>
 * <li>kein Leck: der lebende Speicher (was nach der letzten Bereinigung blieb) wächst im letzten Drittel gegenüber dem
 * mittleren um höchstens 15 % (im ersten Drittel füllt sich der See erst mit Eisbergen);</li>
 * <li>die Grenzen der Szene halten: höchstens {@code bergs.max} Eisberge und {@code bruch.budget} Stücke (dazu bis 20 frisch
 * gelöste, die jünger als 2 s sind);</li>
 * <li>die Physik rechnete mindestens 99 % der Szenenzeit (gezählt wird die verfallene Zeit, nicht die Hänger des Bilds).</li>
 * </ul>
 * Start: Block TECHNIK → „Dauerlauf“, oder {@code -Dglacier.dauerlauf=Minuten} beim Start. Esc beendet nur das Kino,
 * nicht den Dauerlauf; der endet von selbst.
 */
public final class Dauerlauf {
    /** Ein Messpunkt. */
    record Punkt(double minute, double fps, double gpu, double cpu, double heapMb, double lebendMb, int faeden, int eisberge, int stuecke, long nichtNach, int fehler) { }

    private static volatile Dauerlauf laufend;
    /** Fehler aus Fäden (GlacierApp zählt sie). */
    static final java.util.concurrent.atomic.AtomicInteger FEHLER = new java.util.concurrent.atomic.AtomicInteger();

    private final Renderer r;
    private final double minuten;
    private final List<Punkt> punkte = new ArrayList<>();
    private final long beginn = System.nanoTime();
    private volatile boolean aus;
    private volatile String stand = "";

    private Dauerlauf(Renderer r, double minuten) {
        this.r = r;
        this.minuten = minuten;
    }

    public static boolean laeuft() { return laufend != null; }

    /** „Dauerlauf 12 von 30 min · Kino: Südhimmel“ oder leer. */
    public static String zeile() { Dauerlauf d = laufend; return d == null ? "" : d.stand; }

    /** Startet den Dauerlauf (einer zur Zeit); danach läuft {@code fertig} mit der Datei des Berichts (im Faden des Laufs). */
    public static synchronized boolean starten(Renderer r, double minuten, java.util.function.Consumer<File> fertig) {
        if (laufend != null) return false;
        Dauerlauf d = new Dauerlauf(r, Math.max(1, minuten));
        laufend = d;
        Thread t = new Thread(() -> {
            File f = null;
            try { f = d.lauf(); }
            catch (Exception e) { System.err.println("Dauerlauf: " + e); FEHLER.incrementAndGet(); }
            finally { laufend = null; }
            if (fertig != null) fertig.accept(f);
        }, "Glacier-Dauerlauf");
        t.setDaemon(true);
        t.start();
        return true;
    }

    public static void anhalten() { Dauerlauf d = laufend; if (d != null) d.aus = true; }

    private double minute() { return (System.nanoTime() - beginn) / 6e10; }

    private File lauf() throws Exception {
        int fehler0 = FEHLER.get();
        long nichtNach0 = r.physik.dropped(), schritte0 = r.physik.steps();
        verloren0 = r.physik.lostSeconds();
        // warten, bis die Szene steht
        for (int i = 0; i < 240 && (r.gletscher() == null || r.kalbung == null || !r.waveReady); i++) Thread.sleep(250);
        List<Regie.Book> buecher = Regie.all();
        r.ohneBlatt = true;
        try {
            return runden(buecher, fehler0, nichtNach0, schritte0);
        } finally {
            r.ohneBlatt = false;
        }
    }

    private File runden(List<Regie.Book> buecher, int fehler0, long nichtNach0, long schritte0) throws Exception {
        int runde = 0;
        long naechste = 0;
        while (!aus && minute() < minuten) {
            // 1 die Regiebücher im Kino
            for (Regie.Book b : buecher) {
                if (aus || minute() >= minuten) break;
                stand = String.format(Locale.GERMAN, "Dauerlauf %.0f von %.0f min · Kino: %s", minute(), minuten, b.name());
                r.kinoStart(b);
                Thread.sleep(500);
                while (!aus && minute() < minuten && (r.kino.laeuft() || r.regie.running())) { naechste = messen(naechste, fehler0); Thread.sleep(250); }
                if (r.kino.laeuft()) r.kinoVerlassen();
            }
            // 2 frei im Zeitraffer mit der Regie der Kalbung
            if (r.regie.running()) r.regieStop();
            r.clock.lapse = 120;
            if (r.kalbung != null) r.kalbung.regie = true;
            stand = String.format(Locale.GERMAN, "Dauerlauf %.0f von %.0f min · frei im Zeitraffer 120 (Runde %d)", minute(), minuten, ++runde);
            double bis = minute() + 3;
            while (!aus && minute() < Math.min(bis, minuten)) { naechste = messen(naechste, fehler0); Thread.sleep(250); }
            r.clock.lapse = 1;
        }
        if (r.kino.laeuft()) r.kinoVerlassen();
        if (r.regie.running()) r.regieStop();
        long nichtNach = r.physik.dropped() - nichtNach0, schritte = Math.max(1, r.physik.steps() - schritte0);
        return bericht(FEHLER.get() - fehler0, nichtNach, schritte);
    }

    private double verloren0;

    private long messen(long naechste, int fehler0) {
        long jetzt = System.currentTimeMillis();
        if (jetzt < naechste) return naechste;
        Runtime rt = Runtime.getRuntime();
        double heap = (rt.totalMemory() - rt.freeMemory()) / 1048576.0;
        // lebend: was die Bereinigung zuletzt übrig ließ (ohne sie selbst anzustoßen)
        double lebend = 0;
        for (java.lang.management.MemoryPoolMXBean mp : ManagementFactory.getMemoryPoolMXBeans()) {
            if (mp.getType() != java.lang.management.MemoryType.HEAP) continue;
            java.lang.management.MemoryUsage u = mp.getCollectionUsage();
            if (u != null) lebend += u.getUsed() / 1048576.0;
        }
        synchronized (punkte) {
            punkte.add(new Punkt(minute(), r.fps(), r.gpuMs(), r.cpuMs(), heap, lebend, ManagementFactory.getThreadMXBean().getThreadCount(), r.bergs.bergs.size(),
                    r.bruch.pieceCount(), r.physik.dropped(), FEHLER.get() - fehler0));
        }
        return jetzt + 5000;
    }

    /** Speicher nach einer Bereinigung (MB), für den Vergleich erstes gegen letztes Drittel. */
    private static double heapNachGc() {
        System.gc();
        try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / 1048576.0;
    }

    private File bericht(int fehler, long nichtNach, long schritte) throws Exception {
        List<Punkt> p;
        synchronized (punkte) { p = new ArrayList<>(punkte); }
        double heapEnde = heapNachGc();
        int n = p.size();
        double fps = 0, fpsMin = Double.MAX_VALUE, gpu = 0;
        int bergMax = 0, stueckMax = 0, faedenMax = 0;
        for (Punkt q : p) {
            fps += q.fps(); gpu += q.gpu();
            if (q.minute() > 1) fpsMin = Math.min(fpsMin, q.fps());
            bergMax = Math.max(bergMax, q.eisberge());
            stueckMax = Math.max(stueckMax, q.stuecke());
            faedenMax = Math.max(faedenMax, q.faeden());
        }
        fps /= Math.max(1, n);
        gpu /= Math.max(1, n);
        // Speicher: lebend (nach der letzten Bereinigung), Mittel je Drittel
        double h1 = 0, h2 = 0, h3 = 0;
        int k1 = 0, k2 = 0, k3 = 0;
        for (int i = 0; i < n; i++) {
            double v = p.get(i).lebendMb();
            if (i < n / 3) { h1 += v; k1++; } else if (i < n - n / 3) { h2 += v; k2++; } else { h3 += v; k3++; }
        }
        h1 /= Math.max(1, k1);
        h2 /= Math.max(1, k2);
        h3 /= Math.max(1, k3);
        double wachstum = h2 > 0 ? (h3 - h2) / h2 : 0;
        // Phase 16: Anteil der Szenenzeit, den die Physik wirklich gerechnet hat (verfallene Zeit gegen gerechnete)
        double verloren = r.physik.lostSeconds() - verloren0, gerechnet = schritte * com.dan.glacier.scene.Physik.DT;
        double nachAnteil = gerechnet / (gerechnet + verloren);
        int haenger = 0;
        for (Punkt q : p) if (q.minute() > 1 && q.fps() < 20) haenger++;
        List<String[]> urteil = new ArrayList<>();
        urteil.add(new String[]{fehler == 0 ? "ok" : "FEHLER", fehler + " Fehler (Fäden, GL, Chronik)"});
        urteil.add(new String[]{fps >= 30 ? "ok" : "FEHLER", String.format(Locale.GERMAN, "Ø %.1f Bilder/s (geringste %.0f), Grafikkarte Ø %.2f ms", fps, fpsMin == Double.MAX_VALUE ? 0 : fpsMin, gpu)});
        urteil.add(new String[]{wachstum <= 0.15 ? "ok" : "FEHLER", String.format(Locale.GERMAN, "lebender Speicher je Drittel %.0f, %.0f, %.0f MB (letztes gegen mittleres %+.0f %%), am Ende nach Bereinigung %.0f MB",
                h1, h2, h3, wachstum * 100, heapEnde)});
        int bergGrenze = r.bergs.max, stueckGrenze = r.bruch.budget > 0 ? r.bruch.budget + 20 : Integer.MAX_VALUE;
        urteil.add(new String[]{bergMax <= bergGrenze && stueckMax <= stueckGrenze ? "ok" : "FEHLER", String.format(Locale.GERMAN,
                "höchstens %d Eisberge (Grenze %d), %d Stücke (Grenze %d, %d herausgenommen), %d Fäden", bergMax, bergGrenze, stueckMax, r.bruch.budget, r.bruch.budgetRemoved(), faedenMax)});
        urteil.add(new String[]{nachAnteil >= 0.99 ? "ok" : "FEHLER", String.format(Locale.GERMAN, "Physik rechnete %.2f %% der Zeit (%.1f s verfallen, %d×); Bild unter 20 Bilder/s in %d von %d Messungen",
                nachAnteil * 100, verloren, nichtNach, haenger, p.size())});
        boolean gut = urteil.stream().allMatch(u -> u[0].equals("ok"));
        String stempel = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        File dir = r.photoDir != null ? r.photoDir : new File("docs/status");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Ordner " + dir + " lässt sich nicht anlegen");
        StringBuilder t = new StringBuilder();
        t.append("Glacier · Dauerlauf ").append(stempel).append(" · ").append(Renderer.VERSION).append(" · ").append(String.format(Locale.GERMAN, "%.1f min", minute()))
                .append(" · ").append(r.quality().label).append(" · ").append(r.glInfo()).append('\n');
        for (String[] u : urteil) t.append(String.format("%-7s %s%n", u[0], u[1]));
        t.append(gut ? "ERGEBNIS: Dauerlauf bestanden\n" : "ERGEBNIS: Dauerlauf nicht bestanden\n");
        // Phase 16: zur Deutung des Speichers (kein Urteil)
        com.dan.glacier.scene.Ruptura ru = r.ruptura;
        t.append(String.format(Locale.GERMAN, "info    Bruch: höchstens %d Berührungen je Schritt (Grenze %d, %d Zellpaare weggefallen), Vorrat %d; %d Trümmer von einem neuen Damm überbaut%n",
                r.bruch.contactPeak(), r.bruch.maxContacts, r.bruch.contactsCapped(), r.bruch.contactPool(), ru == null ? 0 : ru.buried));
        t.append("\nMinute;Bilder/s;Grafikkarte ms;Prozessor ms;Speicher MB;lebend MB;Fäden;Eisberge;Stücke;nicht nachgekommen;Fehler\n");
        for (Punkt q : p)
            t.append(String.format(Locale.GERMAN, "%.2f;%.1f;%.2f;%.2f;%.0f;%.0f;%d;%d;%d;%d;%d%n", q.minute(), q.fps(), q.gpu(), q.cpu(), q.heapMb(), q.lebendMb(), q.faeden(), q.eisberge(), q.stuecke(), q.nichtNach(), q.fehler()));
        File txt = new File(dir, "Dauerlauf_" + stempel + ".txt");
        Files.writeString(txt.toPath(), t.toString(), StandardCharsets.UTF_8);
        File png = new File(dir, "Dauerlauf_" + stempel + ".png");
        ImageIO.write(bild(p, urteil, gut), "png", png);
        System.out.print(t.substring(0, t.indexOf("\nMinute")));
        return png;
    }


    private BufferedImage bild(List<Punkt> p, List<String[]> urteil, boolean gut) {
        int w = 1600, h = 900, l = 80, rt = 30, top = 90, bot = 230;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(11, 18, 25));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(230, 236, 240));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        g.drawString(String.format(Locale.GERMAN, "Glacier · Dauerlauf %.0f min · %s · %s", minute(), r.quality().label, gut ? "bestanden" : "nicht bestanden"), l, 40);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.setColor(new Color(143, 162, 174));
        g.drawString("Bilder/s (blau, 0–90) · lebender Speicher (gelb, 0–Höchstwert) · Eisberge (weiß, 0–Grenze) · Stücke (rot, 0–Grenze)", l, 66);
        int pw = w - l - rt, ph = h - top - bot;
        g.setColor(new Color(40, 54, 66));
        for (int i = 0; i <= 6; i++) g.drawLine(l, top + ph * i / 6, l + pw, top + ph * i / 6);
        double tMax = p.isEmpty() ? 1 : Math.max(1, p.get(p.size() - 1).minute());
        double hMax = 1;
        for (Punkt q : p) hMax = Math.max(hMax, q.lebendMb());
        kurve(g, p, q -> q.fps() / 90, tMax, l, top, pw, ph, new Color(58, 143, 199));
        final double hm = hMax;
        kurve(g, p, q -> q.lebendMb() / hm, tMax, l, top, pw, ph, new Color(224, 190, 120));
        kurve(g, p, q -> q.eisberge() / (double) Math.max(1, r.bergs.max), tMax, l, top, pw, ph, new Color(240, 246, 249));
        kurve(g, p, q -> q.stuecke() / (double) Math.max(1, r.bruch.budget > 0 ? r.bruch.budget : 100), tMax, l, top, pw, ph, new Color(210, 96, 47));
        g.setColor(new Color(143, 162, 174));
        for (int m = 0; m <= (int) tMax; m += Math.max(1, (int) tMax / 10)) {
            int x = l + (int) (pw * m / tMax);
            g.drawString(m + " min", x - 12, top + ph + 20);
        }
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        int y = h - bot + 60;
        for (String[] u : urteil) {
            g.setColor(u[0].equals("ok") ? new Color(61, 177, 165) : new Color(220, 90, 70));
            g.drawString(String.format("%-7s", u[0]), l, y);
            g.setColor(new Color(230, 236, 240));
            g.drawString(u[1], l + 90, y);
            y += 26;
        }
        g.dispose();
        return im;
    }

    private static void kurve(Graphics2D g, List<Punkt> p, java.util.function.ToDoubleFunction<Punkt> f, double tMax, int l, int top, int pw, int ph, Color c) {
        if (p.size() < 2) return;
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < p.size(); i++) {
            double x = l + pw * p.get(i).minute() / tMax, y = top + ph * (1 - Math.max(0, Math.min(1, f.applyAsDouble(p.get(i)))));
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        g.setColor(c);
        g.setStroke(new BasicStroke(2f));
        g.draw(path);
    }
}
