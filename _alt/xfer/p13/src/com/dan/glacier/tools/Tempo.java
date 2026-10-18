package com.dan.glacier.tools;

import com.dan.glacier.camera.Ansichten;
import com.dan.glacier.gl.Quality;
import com.dan.glacier.gl.Renderer;
import com.dan.glacier.scene.SceneClock;
import com.dan.glacier.wiring.Beaufort;
import com.dan.glacier.wiring.Wiring;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import java.io.File;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Tempo-Probe und Standbild ohne Fenster (aus Haithabu). Rechnet die Szene unsichtbar in fester Größe, misst die
 * Wanduhr je Bild bis glFinish und die Zeit jedes Durchgangs auf der Grafikkarte und schreibt auf Wunsch ein PNG. So
 * lässt sich jede Phase vorher und nachher vergleichen und im Leistungsbudget (Werkbuch, Abschnitt 10) eintragen.
 * <pre>
 * Tempo [bilder] [breite] [höhe] [png|-] [NIEDRIG|MITTEL|HOCH|ULTRA] [uhrzeit] [datum JJJJ-MM-TT] [beaufort]
 * Tempo 120 1920 1080 docs/status/Tempo_Phase1.png MITTEL 15 2026-01-16 4
 * Umgebung: OHNE_HUD, OHNE_MARKEN, ANSICHT=Name einer Ansicht (VomSteg, Südflanke, VomBoot, AufdemEis, BrazoRico, VomBerg)
 * oder „oben“, KAMERA=x,z,Richtung,Neigung,Höhe (frei stehend); seit Phase 3 WETTER=Lage der freien Regie (KLAR, FOEHN,
 * FRONT, STURM …), LENTIKULARIS=0..1, BEDECKUNG=Achtel, FIGUREN (Sternbilder einzeichnen); seit Phase 5 FAHRT=Zunge mit
 * BILDER=Zahl: Bildfolge der Fahrt „Über die Zunge“ als png_01.png, png_02.png …; seit Phase 7 KALBUNG=Turm|Scheibe|Fuss
 * mit BILDER=Zahl und ABSTAND=Sekunden: ein Abbruch an der Front, die dem Blickpunkt am nächsten ist, als Bildfolge
 * (die Physik rechnet dabei ohne eigenen Faden Schritt für Schritt mit, damit jedes Bild denselben Stand zeigt).
 * Seit Phase 8 auch KALBUNG=Probe (ein Einschlag von VOLUMEN m³, Vorgabe 25 000, ohne Bruch) und je Folge die Kurve
 * des Pegels vor dem Steg als png_pegel.png. Seit Phase 9 KALBUNG=Kippen (ein hoher Berg kentert) und EISBERGE=Tag
 * (vorher einen Tag Kalbung in den See legen), etwa mit ANSICHT=UnterWasser. Seit Phase 10 FAHRT=Boot: die Bootsfahrt
 * an Bord des Katamarans als Bildfolge (BILDER, ABSTAND Vorgabe 12 s) mit einem Abbruch (KALBUNG, Vorgabe Turm) vor dem
 * Boot und dem Hub des Boots als Kurve png_hub.png. Seit Phase 11 JAHR=1: dieselbe Ansicht am 16. Januar, April, Juli und
 * Oktober als png_jan.png, png_apr.png, png_jul.png, png_okt.png. Seit Phase 13 RUPTURA=1: das Regiebuch „Die Ruptura“
 * als Bildfolge (BILDER, Vorgabe 12; ABSTAND in Sekunden echter Zeit, Vorgabe 26), Vorgabe der Blick „Die Brücke“ vom
 * Steg (ANSICHT=BrazoRico für den Damm von Süden), dazu Stau und Abfluss als Kurve png_abfluss.png.
 * </pre>
 */
public final class Tempo {
    private Tempo() { }

    /** Phase 8: der Pegel vor dem Steg als Kurve (Höhe über die Zeit seit dem Auslösen), 1 400 × 420. */
    static void pegelBild(Renderer r, File out, double seconds) throws Exception {
        com.dan.wave.Wave.Gauge p = r.pegel;
        if (p == null) return;
        kurve(out, p.series(seconds + 1), seconds, String.format(Locale.GERMAN, "Pegel vor dem Steg (%.0f m vom Steg) · höchste %.2f m · tiefste %.2f m",
                Math.hypot(p.x - r.terrain.steg[0], p.z - r.terrain.steg[1]), p.max(), p.min()));
    }

    /** Eine Kurve {Zeiten, Werte} über {@code seconds} mit Überschrift, 1 400 × 420 (Phase 10: auch der Hub des Katamarans). */
    static void kurve(File out, float[][] s, double seconds, String title) throws Exception {
        int w = 1400, h = 420, l = 70, rt = 20, top = 40, bot = 50;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(12, 20, 28));
        g.fillRect(0, 0, w, h);
        double amp = 0.2;
        for (float v : s[1]) if (!Float.isNaN(v)) amp = Math.max(amp, Math.abs(v));
        amp = Math.ceil(amp * 5) / 5;
        double t0 = s[0].length > 0 ? s[0][0] : 0, t1 = t0 + seconds;
        int mid = top + (h - top - bot) / 2;
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 14));
        g.setColor(new java.awt.Color(45, 65, 80));
        for (int k = 0; k <= (int) seconds; k += 10) {
            int x = (int) (l + (w - l - rt) * k / seconds);
            g.drawLine(x, top, x, h - bot);
            g.drawString(k + " s", x - 10, h - bot + 20);
        }
        for (int k = -2; k <= 2; k++) {
            int y = (int) (mid - (h - top - bot) / 2.0 * k / 2);
            g.drawLine(l, y, w - rt, y);
            g.drawString(String.format(Locale.GERMAN, "%+.1f m", amp * k / 2), 8, y + 5);
        }
        g.setColor(new java.awt.Color(90, 205, 225));
        g.setStroke(new java.awt.BasicStroke(2f));
        java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
        boolean pen = false;
        for (int i = 0; i < s[0].length; i++) {
            if (Float.isNaN(s[1][i])) { pen = false; continue; }
            double x = l + (w - l - rt) * (s[0][i] - t0) / (t1 - t0), y = mid - (h - top - bot) / 2.0 * s[1][i] / amp;
            if (pen) path.lineTo(x, y); else path.moveTo(x, y);
            pen = true;
        }
        g.draw(path);
        g.setColor(new java.awt.Color(200, 215, 225));
        g.drawString(title, l, 26);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", out);
    }

    /**
     * Phase 7: ein Abbruch als Bildfolge. Die Physik läuft ohne Faden (advance + drain je Schritt), die Netze der Kalbung
     * werden vor jedem Bild fertig gebaut.
     */
    static void kalbung(Renderer r, GLOffscreenAutoDrawable d, String art, File png) throws Exception {
        r.physik.stop();
        r.welle.stop();
        r.bruch.asyncSolve = false;   // jedes Bild zeigt denselben Stand, wie schnell der Rechner auch ist
        for (int i = 0; i < 3000 && (r.kalbung == null || !r.waveReady); i++) { d.display(); Thread.sleep(20); }
        r.welle.stop();   // erst jetzt gestartet: wieder anhalten, die Welle rechnet Schritt für Schritt mit
        // „Unter Wasser“ kennt seinen Ort erst, wenn die Welle steht
        Ansichten.Ansicht av = System.getenv("ANSICHT") == null ? null : Ansichten.byName(System.getenv("ANSICHT"));
        if (av != null && av.key() == 7) Ansichten.apply(av, r.camera, Renderer.PHASE);
        if (r.kalbung == null) { System.err.println("FEHLER: keine Kalbung (Gletscher fehlt?)"); return; }
        com.dan.glacier.scene.Kalbung k = r.kalbung;
        k.regie = false;
        String a = art.toUpperCase(Locale.ROOT).replace("ß", "SS").replace("Ü", "UE");
        com.dan.glacier.scene.Kalbung.Art which = a.startsWith("S") ? com.dan.glacier.scene.Kalbung.Art.SCHEIBE
                : a.startsWith("F") ? com.dan.glacier.scene.Kalbung.Art.FUSS : com.dan.glacier.scene.Kalbung.Art.TURM;
        // Phase 9: EISBERGE=Tag legt vorher einen Tag Kalbung in den See; KALBUNG=Kippen setzt einen Berg, der kentert
        if ("TAG".equalsIgnoreCase(System.getenv("EISBERGE"))) System.out.println("Ein Tag Kalbung: " + r.fillDay() + " Eisberge");
        boolean probe = a.startsWith("P") || a.startsWith("K");
        if (a.startsWith("K")) {
            double[] wf = r.waveFront;
            if (!r.toppler(wf != null ? wf[0] : r.camera.tx, wf != null ? wf[1] : r.camera.tz)) System.err.println("FEHLER: keine Front für den Berg, der kippt");
        } else if (probe) {
            // WELLE ohne Bruch: ein Einschlag (VOLUMEN m³, Vorgabe 25 000) an der Front im Blick
            double v = Double.parseDouble(System.getenv().getOrDefault("VOLUMEN", "25000"));
            double[] wf = r.waveFront;
            double px = wf != null ? wf[0] : r.camera.tx, pz = wf != null ? wf[1] : r.camera.tz;
            if (!r.probeWave(px, pz, v)) System.err.println("FEHLER: keine Front für die Probe-Welle");
        } else k.trigger(which, r.camera.tx, r.camera.tz);
        double dt = 1 / 30.0;
        // bis der Abschnitt zerlegt und in der Welt ist
        for (int i = 0; i < 600 && k.sections().isEmpty() && !probe; i++) { r.step(dt); r.physik.drain(); r.welle.drain(); Thread.sleep(20); }
        int n = Integer.parseInt(System.getenv().getOrDefault("BILDER", "12"));
        double every = Double.parseDouble(System.getenv().getOrDefault("ABSTAND", "4").replace(',', '.'));
        double t = 0;
        for (int f = 0; f < n; f++) {
            while (t < f * every) { r.step(dt); r.physik.drain(); r.welle.drain(); t += dt; }
            for (int i = 0; i < 1500 && r.glacierBusy(); i++) { d.display(); Thread.sleep(20); }
            d.display();
            File out = new File(png.getPath().replace(".png", String.format("_%02d.png", f + 1)));
            File dir = out.getAbsoluteFile().getParentFile();
            if (dir != null) dir.mkdirs();
            final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
            d.invoke(true, x -> { img[0] = r.grab(x); return true; });
            javax.imageio.ImageIO.write(img[0], "png", out);
            System.out.println(String.format(Locale.GERMAN, "Bild %d/%d nach %.1f s: %s · %s", f + 1, n, t, out.getPath(), k.describe()));
        }
        for (com.dan.glacier.scene.Kalbung.Abschnitt s : k.sections()) System.out.println("  " + s.describe());
        // Phase 8: die Kurve der Höhe am Steg
        File curve = new File(png.getPath().replace(".png", "_pegel.png"));
        pegelBild(r, curve, t);
        System.out.println("  " + r.waveLine());
        System.out.println("  Pegel: " + curve.getPath());
        synchronized (k.log) { for (com.dan.glacier.scene.Kalbung.Ereignis e : k.log) System.out.println("  " + e); }
    }

    /**
     * Phase 10: die Bootsfahrt als Bildfolge. Das Auge fährt an Bord mit (Blick nach Backbord zur Front); ist das Boot
     * vor der Front angekommen, bricht dort ein Turm ab ({@code KALBUNG}, Vorgabe Turm), und das Boot reitet die Welle ab.
     * Bilder alle ABSTAND s (Vorgabe 12), dazu der Hub des Boots als Kurve (_hub.png).
     */
    static void bootsfahrt(Renderer r, GLOffscreenAutoDrawable d, File png) throws Exception {
        r.physik.stop();
        r.welle.stop();
        r.bruch.asyncSolve = false;
        for (int i = 0; i < 3000 && (r.kalbung == null || !r.waveReady || r.bootsRoute() == null); i++) { d.display(); Thread.sleep(20); }
        r.welle.stop();
        if (r.kalbung == null || r.bootsRoute() == null) { System.err.println("FEHLER: keine Route (Welle oder Gletscher fehlen?)"); return; }
        com.dan.glacier.scene.Kalbung k = r.kalbung;
        k.regie = false;
        r.startBootsfahrt();
        double dt = 1 / 30.0, t = 0;
        com.dan.buoyancy.Catamaran boat = r.katamaran();
        // bis vor die Front (der dritte Wegpunkt ist der Anfang der Strecke)
        for (int i = 0; i < 30 * 600 && boat.leg() < 4; i++) { r.step(dt); r.physik.drain(); r.welle.drain(); t += dt; }
        System.out.println(String.format(Locale.GERMAN, "Vor der Front nach %.0f s: %s", t, boat.describe()));
        int n = Integer.parseInt(System.getenv().getOrDefault("BILDER", "12"));
        double every = Double.parseDouble(System.getenv().getOrDefault("ABSTAND", "12").replace(',', '.'));
        String art = System.getenv().getOrDefault("KALBUNG", "Turm").toUpperCase(Locale.ROOT);
        com.dan.glacier.scene.Kalbung.Art which = art.startsWith("S") ? com.dan.glacier.scene.Kalbung.Art.SCHEIBE
                : art.startsWith("F") ? com.dan.glacier.scene.Kalbung.Art.FUSS : com.dan.glacier.scene.Kalbung.Art.TURM;
        // der Abbruch: an der Front dem Boot gegenüber, etwas voraus
        double[] fw = boat.forward();
        k.trigger(which, boat.x + fw[0] * 120, boat.z + fw[1] * 120);
        java.util.List<float[]> hub = new java.util.ArrayList<>();
        double y0 = boat.y, tStart = t;
        boat.resetMotion();
        for (int f = 0; f < n; f++) {
            while (t - tStart < f * every) {
                r.step(dt); r.physik.drain(); r.welle.drain(); t += dt;
                hub.add(new float[]{(float) (t - tStart), (float) (boat.y - y0)});
            }
            for (int i = 0; i < 1500 && r.glacierBusy(); i++) { d.display(); Thread.sleep(20); }
            d.display();
            File out = new File(png.getPath().replace(".png", String.format("_%02d.png", f + 1)));
            File dir = out.getAbsoluteFile().getParentFile();
            if (dir != null) dir.mkdirs();
            final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
            d.invoke(true, x -> { img[0] = r.grab(x); return true; });
            javax.imageio.ImageIO.write(img[0], "png", out);
            System.out.println(String.format(Locale.GERMAN, "Bild %d/%d nach %.0f s: %s · %s · Welle am Boot %+.2f m", f + 1, n, t - tStart, out.getPath(), boat.describe(), r.wave.heightAt(boat.x, boat.z)));
        }
        float[][] sr = new float[2][hub.size()];
        for (int i = 0; i < hub.size(); i++) { sr[0][i] = hub.get(i)[0]; sr[1][i] = hub.get(i)[1]; }
        double[] m = boat.motion();
        File curve = new File(png.getPath().replace(".png", "_hub.png"));
        kurve(curve, sr, (n - 1) * every, String.format(Locale.GERMAN, "Hub des Katamarans vor der Front (Welle + Windsee) · höchster %+.2f m · tiefster %+.2f m · Rollen bis %.1f° · Stampfen bis %.1f°", m[0], m[1], m[2], m[3]));
        System.out.println("  " + k.describe());
        System.out.println("  " + r.waveLine());
        System.out.println("  " + r.lebenLine());
        System.out.println("  Hub: " + curve.getPath());
    }

    /**
     * Phase 11: dieselbe Ansicht am 16. Januar, April, Juli und Oktober (die Abnahme der Phase), Bilder _jan, _apr, _jul,
     * _okt. Die Temperatur kommt aus dem gewürfelten Jahr (Tagesmittel), der Niederschlag ruht, damit die Bilder klar sind.
     */
    static void jahr(Renderer r, GLOffscreenAutoDrawable d, SceneClock clock, Wiring wiring, File png, double hour) throws Exception {
        int[] months = {1, 4, 7, 10};
        String[] tag = {"jan", "apr", "jul", "okt"};
        int year = clock.date().getYear();
        for (int k = 0; k < months.length; k++) {
            LocalDate dd = LocalDate.of(year, months[k], 16);
            clock.set(dd, hour);
            wiring.airTempC = wiring.jahr.year.dayMean(year, dd.getDayOfYear()) + 2;
            wiring.rainRate = 0;
            r.readapt();
            for (int i = 0; i < 40; i++) { r.step(1 / 60.0); d.display(); }
            for (int i = 0; i < 150; i++) { r.step(1 / 60.0); d.display(); Thread.sleep(20); }   // Wald und Bodendecke backen nach
            for (int i = 0; i < 1500 && r.glacierBusy(); i++) { d.display(); Thread.sleep(20); }
            d.display();
            File out = new File(png.getPath().replace(".png", "_" + tag[k] + ".png"));
            File dir = out.getAbsoluteFile().getParentFile();
            if (dir != null) dir.mkdirs();
            final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
            d.invoke(true, x -> { img[0] = r.grab(x); return true; });
            javax.imageio.ImageIO.write(img[0], "png", out);
            System.out.println(String.format(Locale.GERMAN, "Bild %s: %s · %s", dd, out.getPath(), r.jahrLine()));
        }
    }

    /** Phase 13: das Regiebuch „Die Ruptura“ als Bildfolge; die Physik rechnet ohne Faden Schritt für Schritt mit. */
    static void ruptura(Renderer r, GLOffscreenAutoDrawable d, File png) throws Exception {
        r.physik.stop();
        r.welle.stop();
        r.bruch.asyncSolve = false;
        for (int i = 0; i < 3000 && (r.ruptura == null || !r.waveReady); i++) { d.display(); Thread.sleep(20); }
        r.welle.stop();
        if (r.ruptura == null) { System.err.println("FEHLER: keine Ruptura (Gelände oder Gletscher fehlt?)"); return; }
        com.dan.glacier.scene.Ruptura ru = r.ruptura;
        r.rupturaRegiebuch();
        String an = System.getenv("ANSICHT");
        Ansichten.Ansicht av = an != null && Ansichten.byName(an) != null ? Ansichten.byName(an) : Ansichten.byKey(8);
        Ansichten.apply(av, r.camera, Renderer.PHASE);
        r.camera.snap();
        double dt = 1 / 30.0;
        for (int i = 0; i < 3000 && ru.solid() == null; i++) { r.step(dt); r.physik.drain(); r.welle.drain(); Thread.sleep(20); }
        int n = Integer.parseInt(System.getenv().getOrDefault("BILDER", "12"));
        double every = Double.parseDouble(System.getenv().getOrDefault("ABSTAND", "26").replace(',', '.'));
        double t = 0;
        java.util.List<float[]> curve = new java.util.ArrayList<>();
        for (int f = 0; f < n; f++) {
            while (t < f * every) {
                r.step(dt); r.physik.drain(); r.welle.drain(); t += dt;
                curve.add(new float[]{(float) t, (float) ru.stau(), (float) ru.flow()});
            }
            for (int i = 0; i < 1500 && r.glacierBusy(); i++) { d.display(); Thread.sleep(20); }
            d.display();
            File out = new File(png.getPath().replace(".png", String.format("_%02d.png", f + 1)));
            File dir = out.getAbsoluteFile().getParentFile();
            if (dir != null) dir.mkdirs();
            final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
            d.invoke(true, x -> { img[0] = r.grab(x); return true; });
            javax.imageio.ImageIO.write(img[0], "png", out);
            System.out.println(String.format(Locale.GERMAN, "Bild %d/%d nach %.0f s: %s · %s", f + 1, n, t, out.getPath(), ru.describe()));
        }
        float[][] s = new float[2][curve.size()];
        for (int i = 0; i < curve.size(); i++) { s[0][i] = curve.get(i)[0]; s[1][i] = curve.get(i)[2] / 1000f; }
        File c = new File(png.getPath().replace(".png", "_abfluss.png"));
        kurve(c, s, t, String.format(Locale.GERMAN, "Abfluss durch die Rinne (1 000 m³/s) über die echte Zeit des Regiebuchs · Stau am Ende %.1f m", ru.stau()));
        System.out.println("  Abfluss: " + c.getPath());
        synchronized (ru.log) { for (com.dan.glacier.scene.Ruptura.Ereignis e : ru.log) System.out.println("  " + e); }
    }

    public static void main(String[] a) throws Exception {
        int frames = a.length > 0 ? Integer.parseInt(a[0]) : 120;
        int w = a.length > 1 ? Integer.parseInt(a[1]) : 1920, h = a.length > 2 ? Integer.parseInt(a[2]) : 1080;
        File png = a.length > 3 && !a[3].equals("-") ? new File(a[3]) : null;
        Quality q = a.length > 4 ? Quality.valueOf(a[4]) : Quality.MITTEL;
        double hour = a.length > 5 ? Double.parseDouble(a[5].replace(',', '.')) : SceneClock.START_HOUR;
        LocalDate day = a.length > 6 && !a[6].equals("-") ? LocalDate.parse(a[6]) : SceneClock.START_DATE;
        int bft = a.length > 7 ? Integer.parseInt(a[7]) : 4;

        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
        d.setAutoSwapBufferMode(false);
        SceneClock clock = new SceneClock(day, hour);
        clock.lapse = 0;
        Wiring wiring = new Wiring(clock);
        wiring.wind.speed = Beaufort.mid(bft);
        String wetter = System.getenv("WETTER");   // Phase 3: freie Regie ab einer Lage (KLAR, FOEHN, FRONT …)
        if (wetter != null) {
            wiring.regie = true;
            wiring.startFree(com.dan.weather.Weather.Phase.valueOf(wetter.toUpperCase(Locale.ROOT)));
            if (a.length > 7) wiring.hand(com.dan.weather.Weather.Var.WIND, Beaufort.mid(bft));
        }
        String lens = System.getenv("LENTIKULARIS");
        if (lens != null) wiring.lensHand = Double.parseDouble(lens.replace(',', '.'));
        String okt = System.getenv("BEDECKUNG");
        if (okt != null) { if (wiring.regie) wiring.hand(com.dan.weather.Weather.Var.COVER, Double.parseDouble(okt)); else wiring.oktas = Double.parseDouble(okt); }
        Renderer r = new Renderer(clock, wiring);
        r.frozen = true;
        r.setQuality(q);
        r.autoScale = false;
        if (System.getenv("OHNE_HUD") != null) r.showHud = false;
        if (System.getenv("OHNE_MARKEN") != null) r.showMarks = false;
        if (System.getenv("FIGUREN") != null) r.showFigures = true;
        // Phase 5: den Gletscher gleich bauen (sonst fehlt er in den ersten Bildern)
        r.gletscherJetzt();
        String an = System.getenv("ANSICHT");
        if ("oben".equals(an)) { r.camera.lookAt(0, 0, 0, 0, 89, 9000); r.camera.snap(); }
        else if (an != null && Ansichten.byName(an) != null) Ansichten.apply(Ansichten.byName(an), r.camera, Renderer.PHASE);
        String kam = System.getenv("KAMERA");   // x,z,Richtung,Neigung,Höhe über Grund (frei)
        if (kam != null) {
            String[] k = kam.split(",");
            double kx = Double.parseDouble(k[0]), kz = Double.parseDouble(k[1]);
            r.camera.place(kx, com.dan.glacier.camera.Ansichten.ground(kx, kz) + Double.parseDouble(k[4]), kz, Double.parseDouble(k[2]), Double.parseDouble(k[3]));
        }
        d.addGLEventListener(r);
        d.display();
        if (r.error() != null) { System.err.println("FEHLER: " + r.error()); System.exit(2); }
        for (int i = 0; i < 10; i++) { r.step(1 / 60.0); d.display(); }
        // Phase 5: warten, bis die feinen Kacheln des Gletschers gebaut sind (höchstens 60 s)
        for (int i = 0; i < 3000 && r.glacierBusy(); i++) { d.display(); Thread.sleep(20); }
        if (System.getenv("JAHR") != null && png != null) {   // Phase 11: die vier Tage der Abnahme
            jahr(r, d, clock, wiring, png, hour);
            d.destroy();
            System.exit(0);
        }
        if (System.getenv("RUPTURA") != null && png != null) {   // Phase 13: das Regiebuch als Bildfolge
            ruptura(r, d, png);
            d.destroy();
            System.exit(0);
        }
        String kalb = System.getenv("KALBUNG");   // Phase 7: Bildfolge eines Abbruchs
        boolean boot = System.getenv("FAHRT") != null && System.getenv("FAHRT").toLowerCase(Locale.ROOT).startsWith("boot");
        if (kalb != null && png != null && !boot) {
            kalbung(r, d, kalb, png);
            d.destroy();
            System.exit(0);
        }
        String fahrt = System.getenv("FAHRT");   // Phase 5: Bildfolge einer Fahrt, FAHRT=Zunge, BILDER=Zahl
        if (fahrt != null && png != null && fahrt.toLowerCase(Locale.ROOT).startsWith("boot")) {   // Phase 10: FAHRT=Boot
            bootsfahrt(r, d, png);
            d.destroy();
            System.exit(0);
        }
        if (fahrt != null && png != null) {
            com.dan.glacier.camera.Fahrt f = r.zungenFahrt();
            int n = Integer.parseInt(System.getenv().getOrDefault("BILDER", "8"));
            for (int k = 0; k < n; k++) {
                double[] fq = f.pose(n == 1 ? 0 : k / (double) (n - 1));
                r.camera.place(fq[0], fq[1], fq[2], fq[3], fq[4]);
                r.camera.snap();
                for (int i = 0; i < 6; i++) { r.step(1 / 60.0); d.display(); }
                for (int i = 0; i < 1500 && r.glacierBusy(); i++) { d.display(); Thread.sleep(20); }
                for (int i = 0; i < 4; i++) { r.step(1 / 60.0); d.display(); }
                File out = new File(png.getPath().replace(".png", String.format("_%02d.png", k + 1)));
                File dir = out.getAbsoluteFile().getParentFile();
                if (dir != null) dir.mkdirs();
                final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
                d.invoke(true, x -> { img[0] = r.grab(x); return true; });
                javax.imageio.ImageIO.write(img[0], "png", out);
                System.out.println("Bild " + (k + 1) + "/" + n + ": " + out.getPath() + String.format(Locale.GERMAN, " · %.0f m, %.0f m über dem See", f.length * k / Math.max(1.0, n - 1), fq[1]));
            }
            d.destroy();
            System.exit(0);
        }
        double best = Double.MAX_VALUE, sum = 0;
        // (Phase 7: Bildfolge eines Abbruchs in kalbung())
        for (int i = 0; i < frames; i++) {
            long t0 = System.nanoTime();
            r.step(1 / 60.0);
            d.display();
            d.getContext().makeCurrent();
            d.getGL().glFinish();
            d.getContext().release();
            double ms = (System.nanoTime() - t0) / 1e6;
            best = Math.min(best, ms);
            sum += ms;
        }
        System.out.println(r.glInfo());
        System.out.println(r.compute() == null ? "Compute-Test: nicht gelaufen" : r.compute().line());
        System.out.printf(Locale.GERMAN, "%d×%d %s, %s: bestes Bild %.2f ms, Mittel %.2f ms (%.0f Bilder/s)%n", w, h, q.label, clock.label(), best, sum / frames, 1000 / (sum / frames));
        System.out.println("Durchgänge (ms): " + r.passTimes());
        for (String l : r.hudLines()) System.out.println("  " + l);
        if (png != null) {
            File dir = png.getAbsoluteFile().getParentFile();
            if (dir != null) dir.mkdirs();
            final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
            d.invoke(true, x -> { img[0] = r.grab(x); return true; });
            javax.imageio.ImageIO.write(img[0], "png", png);
            System.out.println("Bild: " + png.getPath());
        }
        d.destroy();
        System.exit(0);
    }
}
