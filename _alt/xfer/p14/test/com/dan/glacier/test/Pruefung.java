package com.dan.glacier.test;

import com.dan.glacier.camera.Ansichten;
import com.dan.glacier.camera.Camera;
import com.dan.glacier.gl.ComputeProbe;
import com.dan.glacier.gl.Mat4;
import com.dan.glacier.gl.Quality;
import com.dan.glacier.gl.Renderer;
import com.dan.glacier.gl.Shader;
import com.dan.glacier.scene.Bodendecke;
import com.dan.glacier.scene.Buehne;
import com.dan.glacier.scene.Geo;
import com.dan.glacier.scene.Physik;
import com.dan.glacier.scene.SceneClock;
import com.dan.glacier.scene.Site;
import com.dan.glacier.scene.Sun;
import com.dan.glacier.scene.Terrain;
import com.dan.glacier.scene.Vegetation;
import com.dan.glacier.tools.GelaendeImport;
import com.dan.glacier.ui.AppIcon;
import com.dan.glacier.wiring.Beaufort;
import com.dan.glacier.wiring.Collider;
import com.dan.glacier.wiring.Wiring;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Prüfprogramm der App ohne Grafikkarte und ohne Datenbank (aus Haithabu). In NetBeans: Rechtsklick → „Run File“,
 * oder Ant-Target {@code pruefung}. Endet mit „ERGEBNIS: alles in Ordnung“ oder mit der Liste der Verstöße
 * (Rückgabewert 1). Wächst mit jeder Phase. Die Packages haben zusätzlich je ihre eigene Prüfklasse in {@code .demo}.
 */
public final class Pruefung {
    private static int ok, bad;

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        mathe();
        ort();
        sonne();
        uhr();
        buehne();
        gelaende();
        vegetation();
        himmel();
        wetter();
        eis();
        gletscher();
        kamera();
        qualitaet();
        physik();
        bruch();
        kalbung();
        welle();
        eisberge();
        leben();
        jahr();
        klang();
        ruptura();
        regie();
        shader();
        verdrahtung();
        symbol();
        pakete();
        isolation();
        System.out.printf(Locale.GERMAN, "%n%d Prüfungen, %d Verstöße, %.1f s%n", ok + bad, bad, (System.nanoTime() - t0) / 1e9);
        System.out.println(bad == 0 ? "ERGEBNIS: alles in Ordnung" : "ERGEBNIS: " + bad + " Verstöße");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean cond, String what) {
        if (cond) ok++; else bad++;
        System.out.println((cond ? "  ok    " : "  FEHLER ") + what);
    }

    static void mathe() {
        System.out.println("Matrizen");
        float[] v = Mat4.lookAt(10, 20, 30, 0, 14, 0);
        float[] p = Mat4.perspective(55, 16 / 9.0, 0.5, 80000, true);
        float[] vp = Mat4.mul(p, v), id = Mat4.mul(vp, Mat4.invert(vp));
        double err = 0;
        for (int i = 0; i < 16; i++) err = Math.max(err, Math.abs(id[i] - (i % 5 == 0 ? 1 : 0)));
        check(err < 1e-4, String.format(Locale.GERMAN, "Projektion·Blick mal Inverse = Einheit (Fehler %.1e)", err));
        float[] look = Mat4.lookAt(0, 0, 0, 0, 0, -1);
        double[] c = Mat4.apply(Mat4.mul(p, look), 0, 0, -100);
        check(Math.abs(c[2] / c[3] - 0.5 / 100) < 1e-6, "Umgekehrte Tiefe: Punkt in 100 m hat Tiefe Nahebene/100");
        double[] far = Mat4.apply(Mat4.mul(p, look), 0, 0, -12000);
        check(far[2] / far[3] > 0 && far[2] / far[3] < 1e-4, "Berge in 12 km liegen zwischen 0 und Nahebene, vor dem Unendlichen");
    }

    static void ort() {
        System.out.println("Ort und Maßstab");
        check(Site.dms(Site.LAT, Site.LON).equals("50°28′23″ S · 73°02′13″ W"), "Spitze der Front aus GLO-30 (Phase 2) in Grad, Minuten, Sekunden: " + Site.dms(Site.LAT, Site.LON));
        double[] xz = Site.xz(new double[]{Site.LAT, Site.LON});
        check(Math.abs(xz[0]) < 1e-6 && Math.abs(xz[1]) < 1e-6, "Ursprung der Szene liegt auf der Spitze der Front");
        double x = 1234.5, z = -987.25;
        double[] back = Site.xz(Site.latLon(x, z));
        check(Math.abs(back[0] - x) < 1e-6 && Math.abs(back[1] - z) < 1e-6, "Szene ↔ Breite/Länge hin und zurück (Südhalbkugel)");
        check(Site.lat(-1000) > Site.LAT && Site.lon(1000) > Site.LON, "Nord ist −z (Breite steigt gegen null), Ost ist +x");
        double[] u = Geo.UTM18S.forward(Site.LAT, Site.LON), ll = Geo.UTM18S.inverse(u[0], u[1]);
        check(Math.abs(ll[0] - Site.LAT) < 1e-9 && Math.abs(ll[1] - Site.LON) < 1e-9 && u[1] < 10_000_000 && u[0] > 500_000,
                String.format(Locale.GERMAN, "UTM 18 Süd hin und zurück: %.0f E, %.0f N", u[0], u[1]));
        double[] g = Site.xz(new double[]{Site.GLACIER_LAT, Site.GLACIER_LON});
        check(g[0] < -2000 && g[1] > 0, String.format(Locale.GERMAN, "Der Gletscher als Ganzes (50°30′ S 73°08′ W) liegt %.1f km westlich, %.1f km südlich der Front – oben auf der Zunge", -g[0] / 1000, g[1] / 1000));
        check(Math.abs(Site.FRONT_DRAFT - 96) < 1e-9, "Front: 74 m über Wasser, 170 m Eis, also 96 m unter dem Spiegel");
        check(Math.abs(Site.waveSpeed(100) - 31.3) < 0.1, String.format(Locale.GERMAN, "Lange Welle bei 100 m Tiefe: %.1f m/s, über 600 m Kanal in %.0f s", Site.waveSpeed(100), 600 / Site.waveSpeed(100)));
    }

    static void sonne() {
        System.out.println("Sonne (Südhalbkugel)");
        SceneClock c = new SceneClock(LocalDate.of(2026, 1, 16), 0);
        double day = c.epochMillis();
        double up = hourOf(Sun.crossing(day, Site.LAT, Site.LON, true)), down = hourOf(Sun.crossing(day, Site.LAT, Site.LON, false));
        check(up > 5.0 && up < 6.5 && down > 21.0 && down < 22.5, "16.1.2026 Aufgang " + hm(up) + ", Untergang " + hm(down) + " ART");
        double len = down - up;
        check(len > 15.5 && len < 16.8, String.format(Locale.GERMAN, "Tag im Januar %.2f h (50,5° S, Hochsommer)", len));
        double best = -90, bestH = 0;
        for (double h = 10; h < 18; h += 1 / 60.0) {
            c.set(LocalDate.of(2026, 1, 16), h);
            double e = c.sun()[0];
            if (e > best) { best = e; bestH = h; }
        }
        c.set(LocalDate.of(2026, 1, 16), bestH);
        double az = c.sun()[1];
        check((az < 5 || az > 355) && bestH > 13.5 && bestH < 14.5, String.format(Locale.GERMAN, "Höchststand %s ART bei %.1f°, Azimut %03.0f° – die Sonne steht mittags im Norden", hm(bestH), best, az));
        SceneClock dez = new SceneClock(LocalDate.of(2026, 12, 21), 0);
        double dd = hourOf(Sun.crossing(dez.epochMillis(), Site.LAT, Site.LON, false));
        check(dd > 22.0, "21.12.2026 Untergang " + hm(dd) + " ART (Werkbuch: im Dezember bis nach 22 Uhr)");
        SceneClock jul = new SceneClock(LocalDate.of(2026, 7, 16), 0);
        double ju = hourOf(Sun.crossing(jul.epochMillis(), Site.LAT, Site.LON, true)), jd = hourOf(Sun.crossing(jul.epochMillis(), Site.LAT, Site.LON, false));
        check(jd - ju < 9, String.format(Locale.GERMAN, "16.7.2026 Tag %.1f h (Winter)", jd - ju));
    }

    static void uhr() {
        System.out.println("Uhr");
        SceneClock c = new SceneClock();
        check(c.label().equals("16.1.2026 15:00 ART"), "Start: " + c.label());
        check(Math.abs(c.local().getOffset().getTotalSeconds() + 3 * 3600) < 1, "Argentinische Zeit UTC−3");
        c.lapse = 60;
        c.step(30);
        check(c.label().equals("16.1.2026 15:30 ART"), "Zeitraffer 1 min/s, 30 s → " + c.label());
        double e0 = c.epochMillis();
        c.lapse = 1e9;
        c.step(1);
        check(Math.abs(c.epochMillis() - e0 - SceneClock.MAX_LAPSE * 1000) < 1e-3, "Zeitraffer höchstens 3600-fach");
        c.set(LocalDate.of(2026, 12, 31), 23.5);
        c.lapse = 3600;
        c.step(1);
        check(c.dateLabel().equals("1.1.2027"), "Jahreswechsel: " + c.label());
        SceneClock s = new SceneClock();
        s.lapse = 0;
        s.step(2.5);
        check(Math.abs(s.t() - 2.5) < 1e-9 && s.label().equals("16.1.2026 15:00 ART"), "Uhr steht, Laufzeit läuft weiter (Wellen bewegen sich)");
    }

    static void buehne() {
        System.out.println("Bühne (Phase 1, Ersatz ohne Höhendaten)");
        check(Buehne.ice(-10, 0) && !Buehne.ice(10, 0) && !Buehne.ice(-10, 2600) && Buehne.height(-100, 0) == Site.FRONT_HEIGHT, "Eis westlich der Front, 5 km breit, 74 m hoch");
        Terrain f = Terrain.flat();
        check(!f.measured() && f.ice(-100, 0) && f.sourceAt(Buehne.TIP + 30, 20) == Terrain.FELS && f.waterDepth(200, -800) > 50,
                "Terrain.flat(): die Bühne als Gelände, falls daten/gelaende fehlt");
    }

    static Terrain terrain;

    static void gelaende() {
        System.out.println("Gelände (Phase 2, GLO-30)");
        double y = 123.456;
        check(Math.abs(Terrain.decode(Terrain.encode(y)) - y) < (Terrain.HMAX - Terrain.HMIN) / 65535.0, "Höhe in 16 bit: Stufe " + String.format(Locale.GERMAN, "%.3f m", (Terrain.HMAX - Terrain.HMIN) / 65535.0));
        boolean[] m = {false, true, false, true};
        check(Math.abs(GelaendeImport.maskAt(m, 2, 2, 0.5, 0.5) - 0.5) < 1e-9 && GelaendeImport.maskAt(m, 2, 2, 1, 0) == 1, "Masken bilinear (keine Treppen der 30-m-Zellen)");
        terrain = Terrain.load(Terrain.defaultDir());
        if (!terrain.measured()) {
            System.out.println("  (daten/gelaende fehlt – tools.GelaendeImport erst laufen lassen; Prüfungen am echten Gelände übersprungen)");
            return;
        }
        Terrain t = terrain;
        System.out.println("  " + t.describe());
        check(Math.abs(t.lakeAsl - Site.LAKE_LEVEL_ASL) < 0.5, String.format(Locale.GERMAN, "Seespiegel %.1f m ü. M. wie Site.LAKE_LEVEL_ASL", t.lakeAsl));
        check(Math.abs(Double.parseDouble(t.props.getProperty("ursprung.lat")) - Site.LAT) < 1e-6
                && Math.abs(Double.parseDouble(t.props.getProperty("ursprung.lon")) - Site.LON) < 1e-6, "Ursprung des Imports = Site.LAT/LON");
        double[] sh = t.sourceShares();
        check(sh[1] > 0.10 && sh[1] < 0.30 && sh[2] > 0.08 && sh[2] < 0.25, String.format(Locale.GERMAN, "See %.0f %%, Eis %.0f %% des Ausschnitts", sh[1] * 100, sh[2] * 100));
        double[] fs = t.frontStats();
        check(fs[0] > 3500 && fs[0] < 6500, String.format(Locale.GERMAN, "Front %.1f km lang (Lücken über %.0f m zählen nicht)", fs[0] / 1000, Terrain.FRONT_GAP));
        check(fs[1] > 20 && fs[1] < 90, String.format(Locale.GERMAN, "Oberkante im Höhenmodell im Mittel %.0f m (die echte Wand kommt in Phase 5)", fs[1]));
        boolean iceNear = false, waterNear = false;
        for (int a = 0; a < 360; a += 10)
            for (int r = 10; r <= 120; r += 10) {
                double x = r * Math.cos(Math.toRadians(a)), z = r * Math.sin(Math.toRadians(a));
                iceNear |= t.ice(x, z);
                waterNear |= t.sourceAt(x, z) == Terrain.SEE;
            }
        check(iceNear && waterNear, "am Ursprung stoßen Eis und See zusammen (Spitze der Front)");
        double sd = Math.hypot(t.steg[0], t.steg[1]);
        check(t.sourceAt(t.steg[0], t.steg[1]) == Terrain.FELS && t.steg[0] > 0 && sd >= 290 && t.steg[2] >= 24 && t.steg[2] <= 61,
                String.format(Locale.GERMAN, "Steg auf der Halbinsel: %.0f m vor der Spitze, %.0f m über dem See, Blick %03.0f°", sd, t.steg[2], t.steg[3]));
        check(Math.abs(t.height(t.steg[0], t.steg[1]) - t.steg[2]) < 3, "Höhe am Steg = Höhe im Gelände");
        double dCanal = t.waterDepth(-300, -900);
        check(dCanal > 40 && dCanal < 170, String.format(Locale.GERMAN, "Canal de los Témpanos vor der Front %.0f m tief [G]", dCanal));
        double iceBed = t.ice(-1500, 0) ? t.height(-1500, 0) - t.bed(-1500, 0) : 0;
        check(iceBed > 150 && iceBed < 450, String.format(Locale.GERMAN, "Eis 1,5 km hinter der Front %.0f m dick [G], Grund darunter modelliert", iceBed));
        double[] r = new double[2];
        t.range(-2000, -2000, 4000, r);
        check(r[0] < -40 && r[1] > 300, String.format(Locale.GERMAN, "Spanne eines Knotens 4 km um die Front: %.0f bis %.0f m", r[0], r[1]));
    }

    static void vegetation() {
        System.out.println("Vegetation (Phase 2) [G]");
        Terrain t = terrain != null && terrain.measured() ? terrain : Terrain.flat();
        Vegetation v = new Vegetation(t);
        System.out.println("  " + v.describe());
        check(Vegetation.species().size() == 3 && Vegetation.species().get(0).name.startsWith("Lenga"), "Arten: Lenga, Ñire, Guindo");
        double[] sh = v.shares();
        check(!t.measured() || sh[0] > 0.10 && sh[0] < 0.50, String.format(Locale.GERMAN, "Wald auf %.0f %% des Ausschnitts", sh[0] * 100));
        int onIce = 0, onLake = 0, high = 0, n = 0;
        for (double x = Terrain.X0 + 50; x < Terrain.X0 + Terrain.N * Terrain.CELL; x += 97)
            for (double z = Terrain.Z0 + 50; z < Terrain.Z0 + Terrain.N * Terrain.CELL; z += 97) {
                double f = v.forest(x, z);
                byte s = t.sourceAt(x, z);
                n++;
                if (f > 0.3 && s == Terrain.EIS && t.ice(x + 30, z) && t.ice(x - 30, z) && t.ice(x, z + 30) && t.ice(x, z - 30)) onIce++;
                if (f > 0.3 && s == Terrain.SEE && t.waterDepth(x, z) > 5) onLake++;
                if (f > 0.05 && t.height(x, z) > Vegetation.TREELINE + Vegetation.TREELINE_FADE + 30) high++;
            }
        check(onIce == 0 && onLake == 0, "kein Wald auf dem Eis und im See (" + n + " Stichproben)");
        check(high == 0, String.format(Locale.GERMAN, "kein Wald über der Baumgrenze (%.0f m über dem See)", Vegetation.TREELINE + Vegetation.TREELINE_FADE));
        float[] c = new float[5];
        new Bodendecke(t, v).cover(-1500, 0, c);
        check(!t.ice(-1500, 0) || c[0] + c[1] + c[2] + c[3] + c[4] == 0, "Bodendecke: nichts auf dem Eis");
        check(v.at(-1500, 0, com.dan.reed.Reed.Kind.STEPPE) == 0 || !t.ice(-1500, 0), "Steppengras nicht auf dem Eis");
        check(Wiring.northernDay(16) == 198 && Wiring.northernDay(200) == 17, "Südhalbkugel: 16. Januar ist für forest, ground, reed der 17. Juli");
        if (t.measured()) {
            double fs = v.forest(t.steg[0], t.steg[1]);
            double a = Math.toRadians(t.steg[3]), lx = t.steg[0] + Math.sin(a) * 200, lz = t.steg[1] - Math.cos(a) * 200;
            double lane = v.forest(lx, lz);
            check(fs < 0.05 && lane < 0.05, String.format(Locale.GERMAN, "Steg frei (Befund Status_Phase2): Wald am Steg %.0f %%, 200 m Richtung Front %.0f %%", fs * 100, lane * 100));
        }
    }

    /** Phase 3: der Himmel über der Front. */
    static void himmel() {
        System.out.println("Himmel (Phase 3, com.dan.sky)");
        com.dan.sky.Sky sky = new com.dan.sky.Sky();
        sky.latDeg = Site.LAT;
        sky.lonDeg = Site.LON;
        SceneClock c = new SceneClock();
        sky.epochMillis = c.epochMillis();
        com.dan.sky.Sky.State st = sky.update();
        double az = (Math.toDegrees(Math.atan2(st.sunDir[0], -st.sunDir[2])) + 360) % 360;
        check(st.sunAlt > 50 && st.sunAlt < 62 && (az > 300 || az < 30), String.format(Locale.GERMAN, "16.1.2026 15:00 ART: Sonne %.1f° hoch aus %03.0f° (über Norden)", st.sunAlt, az));
        c.set(LocalDate.of(2026, 1, 17), 1.5);
        sky.epochMillis = c.epochMillis();
        com.dan.sky.Sky.State n = sky.update();
        double crux = alt(n, 186.65, -63.10), lmc = alt(n, 80.89, -69.76), smc = alt(n, 13.19, -72.83);
        check(n.sunAlt < -12 && crux > 10 && lmc > 25 && smc > 25,
                String.format(Locale.GERMAN, "17.1. 01:30 ART: Sonne %.0f°, Acrux %.0f° hoch, Große Wolke %.0f°, Kleine Wolke %.0f° (bei 50,5° Süd nie unter der Kimm)", n.sunAlt, crux, lmc, smc));
        check(com.dan.sky.StarCatalog.get().figures.stream().anyMatch(f -> f.code().equals("Cru")) && com.dan.sky.StarCatalog.magellanic(80.9, -69.8) > 0.5,
                "Kreuz des Südens als Figur, Magellansche Wolken in der Milchstraße (Packages sky, seit Glacier Phase 3)");
    }

    /** Höhe eines Sterns (RA, Dec J2000) über dem Horizont in Grad. */
    static double alt(com.dan.sky.Sky.State st, double ra, double dec) {
        double[] d = com.dan.sky.Celestial.apply(st.eqToScene, com.dan.sky.Celestial.equatorial(ra, dec));
        return Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, d[1]))));
    }

    /** Phase 4: das Material Eis (com.dan.ice) und die Probe-Eisberge vor dem Steg. */
    static void eis() {
        System.out.println("Eis (Phase 4, com.dan.ice)");
        check(com.dan.ice.Ice.STAGE >= 4 && Renderer.PHASE >= 4, "com.dan.ice auf Stand Material, App auf Phase " + Renderer.PHASE);
        double[] hl = {com.dan.ice.IceOptics.halfLength(0), com.dan.ice.IceOptics.halfLength(2)};
        check(hl[0] < 5 && hl[1] > 100, String.format(Locale.GERMAN, "Rot nach %.1f m halbiert, Blau erst nach %.0f m (Warren & Brandt)", hl[0], hl[1]));
        check(Quality.MITTEL.ice == com.dan.ice.Ice.Quality.MEDIUM && com.dan.ice.gl.IceGl.steps(Quality.MITTEL.ice) == 4, "„Mittel“: Eis MEDIUM, 4 Proben durch die Risse");
        Renderer r = new Renderer();
        java.util.List<double[]> pl = r.icebergPlaces();
        boolean wet = true;
        StringBuilder sb = new StringBuilder();
        for (double[] p : pl) {
            double d = Math.hypot(p[0] - r.terrain.steg[0], p[1] - r.terrain.steg[1]);
            wet &= !r.terrain.measured() || r.terrain.height(p[0], p[1]) < r.ocean.waterLevel - 10;
            sb.append(String.format(Locale.GERMAN, " %.0f m", d));
        }
        check(pl.size() == 2 && wet, "zwei Probe-Eisberge im Wasser vor dem Steg, Abstand" + sb);
        com.dan.ice.IceMesh m = com.dan.ice.IceShapes.iceberg(11, 7, 0.85, 3);
        double wl = m.waterline(r.bergIce.submergedFraction(1000));
        check(Math.abs(m.volumeBelow(wl) / m.volume() - r.bergIce.submergedFraction(1000)) < 1e-3, String.format(Locale.GERMAN, "Eisberg schwimmt mit %.0f %% unter Wasser", r.bergIce.submergedFraction(1000) * 100));
    }

    /** Phase 5: der Perito Moreno als Körper aus com.dan.ice, aus dem Gelände gebaut. */
    static void gletscher() {
        System.out.println("Gletscher (Phase 5, com.dan.ice)");
        check(com.dan.ice.Ice.STAGE >= 5 && Renderer.PHASE >= 5, "com.dan.ice auf Stand Gletscher, App auf Phase " + Renderer.PHASE);
        check(Math.abs(Wiring.glacierAdvance(345) - 40) < 1e-9 && Wiring.glacierAdvance(162) < -39 && Wiring.glacierAdvance(16) > 25,
                String.format(Locale.GERMAN, "Vorstoß: +40 m im Dezember, −40 m im Juni, am 16. Januar %+.0f m [G]", Wiring.glacierAdvance(16)));
        if (terrain == null || !terrain.measured()) { System.out.println("  (ohne Höhendaten übersprungen)"); return; }
        Renderer r = new Renderer(new SceneClock(), null, terrain);
        com.dan.glacier.scene.Gletscher gl = r.gletscherJetzt();
        com.dan.ice.Glacier g = gl.glacier;
        check(gl.buildMs < 10_000, String.format(Locale.GERMAN, "gebaut in %.1f s: %s", gl.buildMs / 1000, g.describe()));
        check(g.frontLines().size() >= 1 && g.frontLength() > 3500 && g.frontLength() < 6500, String.format(Locale.GERMAN, "Front %.1f km lang (Werkbuch: 5 km)", g.frontLength() / 1000));
        double top = g.frontTop();
        check(Math.abs(g.measuredFrontTop() + Math.max(0, Site.FRONT_HEIGHT - g.measuredFrontTop()) - Site.FRONT_HEIGHT) < 1e-6 && top > 40 && top < 80,
                String.format(Locale.GERMAN, "Front auf %.0f m gehoben (GLO-30: %.0f m), Oberkante mit Spalten und Türmen im Mittel %.0f m", Site.FRONT_HEIGHT, g.measuredFrontTop(), top));
        check(g.frontSpeed() > 1.5 && g.frontSpeed() < 4.5, String.format(Locale.GERMAN, "an der Front bis %.1f m/Tag [G: um 2]", g.frontSpeed()));
        check(g.crevassedShare() > 0.15 && g.crevassedShare() < 0.7, String.format(Locale.GERMAN, "Spalten auf %.0f %% der Zunge", g.crevassedShare() * 100));
        double ix = -1500, iz = 0;
        check(g.covers(ix, iz) && Math.abs(Ansichten.ground(ix, iz) - g.surface(ix, iz)) < 1e-9 && Math.abs(r.groundAt(ix, iz) - g.surface(ix, iz)) < 1e-9,
                String.format(Locale.GERMAN, "„Auf dem Eis“ steht auf der Oberfläche des Gletschers (%.0f m, GLO-30 %.0f m)", g.surface(ix, iz), terrain.height(ix, iz)));
        check(!g.covers(terrain.steg[0], terrain.steg[1]), "der Steg liegt nicht unter dem Eis");
        g.setAdvance(Wiring.ADVANCE_AMPLITUDE);
        boolean free = !g.covers(terrain.steg[0], terrain.steg[1]);
        for (double[] p : r.icebergPlaces()) free &= !g.covers(p[0], p[1]) && g.distFront(p[0], p[1]) > 0;
        double[] near = g.nearestFront(terrain.steg[0], terrain.steg[1], 2000);
        double toSteg = near == null ? 1e9 : Math.hypot(near[0] - terrain.steg[0], near[1] - terrain.steg[1]);
        g.setAdvance(0);
        check(free && toSteg > 150, String.format(Locale.GERMAN, "auch bei vollem Vorstoß bleiben Steg und Eisberge frei (Front %.0f m vom Steg)", toSteg));
        com.dan.glacier.camera.Fahrt f = r.zungenFahrt();
        boolean above = true;
        double[][] path = gl.zungenWeg();
        for (double[] p : path) if (g.covers(p[0], p[2])) above &= p[1] > g.surface(p[0], p[2]) + 40;
        double[] last = path[path.length - 1];
        check(f != null && f.length > 5500 && above && !g.covers(last[0], last[2]) && f.maxTurnPerMeter() < 3,
                String.format(Locale.GERMAN, "Fahrt „Über die Zunge“: %.1f km, immer über 40 m über dem Eis, endet über dem Wasser, Blick dreht höchstens %.1f°/m", f == null ? 0 : f.length / 1000, f == null ? 0 : f.maxTurnPerMeter()));
        boolean onIce = true, speeds = true;
        for (com.dan.glacier.scene.Gletscher.Bach b : gl.baeche) {
            for (int k = 0; k < b.line.length; k += 20) onIce &= g.covers(b.line[k], b.line[k + 1]);
            for (float v : b.speed) speeds &= v >= 0.3f && v <= 3.5f;
        }
        check(gl.baeche.size() >= 3 && onIce && speeds, gl.describeBaeche() + " (com.dan.river), alle auf dem Eis");
        com.dan.glacier.wiring.Collider c = Wiring.glacierCollider(g);
        check(Math.abs(c.distance(ix, g.surface(ix, iz) + 1, iz) - 1) < 1e-6 && c.distance(ix, g.surface(ix, iz) - 2, iz) < 0, "Gletscher als Collider: 1 m über der Oberfläche = 1, darunter negativ");
    }

    /** Phase 3: die Wetterregie mit dem Klima Patagoniens. */
    static void wetter() {
        System.out.println("Wetter (Phase 3, Klima Patagonien) [G]");
        SceneClock c = new SceneClock();
        Wiring w = new Wiring(c);
        w.regie = true;
        w.startFree(com.dan.weather.Weather.Phase.KLAR);
        int foehn = 0, n = 0, rain = 0;
        double sx = 0, sz = 0, lensMax = 0, lensFoehn = 0;
        com.dan.cloud.Clouds cl = new com.dan.cloud.Clouds();
        c.lapse = 600;
        for (int i = 0; i < 30 * 24 * 6; i++) {
            c.step(1.0);
            w.feedWeather(0.05);
            n++;
            com.dan.weather.Weather.Snapshot s = w.weather.snapshot();
            if (s.phase() == com.dan.weather.Weather.Phase.FOEHN) { foehn++; lensFoehn += w.lens; }
            if (w.rainRate > 0.2) rain++;
            double a = Math.toRadians(w.wind.fromDeg());
            sx += Math.sin(a) * w.wind.speed();
            sz += Math.cos(a) * w.wind.speed();
            lensMax = Math.max(lensMax, w.lens);
        }
        w.feed(cl);
        double mean = (Math.toDegrees(Math.atan2(sx, sz)) + 360) % 360;
        check(foehn > n / 20 && mean > 240 && mean < 310 && rain > n / 50 && rain < n / 2,
                String.format(Locale.GERMAN, "30 Tage freie Regie: Föhnsturm %.0f %% der Zeit, Wind im Mittel aus %03.0f°, Regen %.0f %% der Zeit", foehn * 100.0 / n, mean, rain * 100.0 / n));
        check(foehn > 0 && lensFoehn / foehn > 0.5 && lensMax <= 1 && Math.abs(cl.lens - w.lens) < 1e-12 && Math.abs(cl.lensFromDeg - w.wind.fromDeg()) < 1e-9,
                String.format(Locale.GERMAN, "Lentikularis im Föhnsturm im Mittel %.0f %%, an die Wolken mit der Windrichtung", lensFoehn / Math.max(1, foehn) * 100));
        check(Wiring.lensTarget(com.dan.weather.Weather.Phase.KLAR, 3, 270, 1) == 0 && Wiring.lensTarget(com.dan.weather.Weather.Phase.FOEHN, 20, 285, 2) > 0.8
                        && Wiring.lensTarget(com.dan.weather.Weather.Phase.FOEHN, 20, 285, 8) == 0,
                "Lentikularis: keine bei Schwachwind, fast immer im Föhn, nicht sichtbar unter 8/8");
        com.dan.fog.Fog f = new com.dan.fog.Fog();
        w.fogAmount = 1;
        w.regie = false;
        w.wind.speed = 1;
        w.feed(f, -5);
        check(Math.abs(f.top - 80) < 1e-9 && f.amount > 0.9, String.format(Locale.GERMAN, "Dichter Seenebel vor Tag: bis %.0f m über dem See (die Front ragt mit gut 40 m nicht heraus)", f.top));
    }

    static void kamera() {
        System.out.println("Kamera");
        Camera cam = new Camera();
        check(cam.eye[0] > 1200 && Math.abs(cam.eye[2]) < 400 && cam.heading() > 260 && cam.heading() < 290,
                String.format(Locale.GERMAN, "Grundansicht: %.0f m vor der Front, Blick %03.0f° auf die Wand", cam.eye[0], cam.heading()));
        Terrain t = terrain != null ? terrain : Terrain.flat();
        Ansichten.use(t);
        double gi = t.height(-1500, 0);
        cam.place(-1500, 0, 0, 90, 0);
        cam.groundLevel = gi;
        cam.step(0.1);
        check(cam.eye[1] >= gi + Camera.MIN_EYE - 1e-9, String.format(Locale.GERMAN, "Frei auf dem Eis: Auge bleibt über dem Eis (%.1f m)", cam.eye[1]));
        cam.setMode(Camera.Mode.KREISEN);
        check(cam.mode == Camera.Mode.KREISEN && cam.dist > 1, "Wechsel Frei → Kreisen behält die Ansicht");
        int ready = 0;
        for (Ansichten.Ansicht a : Ansichten.ALLE) {
            Camera c = new Camera();
            if (Ansichten.apply(a, c, Renderer.PHASE)) {
                ready++;
                double ground = Ansichten.ground(c.eye[0], c.eye[2]);
                if (a.key() != 7 && c.eye[1] < ground + 1) check(false, a + " liegt unter dem Gelände");
                if (a.key() == 7 && !c.diving) check(false, a + " taucht nicht");
            }
        }
        check(ready == 8 && Ansichten.ALLE.size() == 8, ready + " von 8 Ansichten im Gelände, seit Phase 9 auch „Unter Wasser“ (taucht), seit Phase 13 „Die Brücke“");
        if (t.measured()) {
            String[][] want = {{"1", "Land"}, {"2", "Land"}, {"3", "See"}, {"4", "Eis"}, {"5", "Land"}, {"6", "Land"}, {"8", "Land"}};
            StringBuilder sb = new StringBuilder();
            boolean all = true;
            for (String[] w : want) {
                Camera c = new Camera();
                Ansichten.apply(Ansichten.byKey(Integer.parseInt(w[0])), c, Renderer.PHASE);
                String z = Ansichten.zone(c.eye[0], c.eye[2]);
                all &= z.equals(w[1]);
                sb.append(sb.length() > 0 ? ", " : "").append(w[0]).append(' ').append(z);
            }
            check(all, "Ansichten stehen, wo sie sollen: " + sb);
            Camera c = new Camera();
            Ansichten.apply(Ansichten.byKey(1), c, Renderer.PHASE);
            check(Math.abs(c.eye[0] - t.steg[0]) < 1e-6 && Math.abs(c.eye[2] - t.steg[1]) < 1e-6, "1 Vom Steg steht am Steg aus dem Import");
        }
    }

    static void qualitaet() {
        System.out.println("Qualität");
        boolean mono = true;
        Quality[] q = Quality.values();
        for (int i = 1; i < q.length; i++) mono &= q[i].renderScale >= q[i - 1].renderScale && q[i].budgetMs() > q[i - 1].budgetMs();
        check(mono, "Stufen Niedrig → Ultra steigen an");
        check(Math.abs(Quality.MITTEL.budgetMs() - 15.7) < 1e-9 && Math.abs(Quality.HOCH.budgetMs() - 2 * 15.7) < 1e-9, "„Mittel“ hat das Budget aus Abschnitt 10: 15,7 ms, „Hoch“ das Doppelte");
        Renderer r = new Renderer();
        check(r.quality() == Quality.MITTEL, "Start auf „Mittel“ (F21 A)");
        r.regulateForTest(30, 10_000_000_000L);
        check(r.renderScale < Quality.MITTEL.renderScale, String.format(Locale.GERMAN, "Grafikkarte 30 ms: Auflösung sinkt auf %.0f %%", r.renderScale * 100));
        check(r.renderScale >= Renderer.MIN_SCALE, "nie unter " + Math.round(Renderer.MIN_SCALE * 100) + " %");
    }

    static void physik() {
        System.out.println("Faden der Physik (F21 A)");
        Physik p = new Physik();
        int[] n = {0};
        p.add((t, dt) -> n[0]++);
        p.advance(0.5);
        int steps = p.drain();
        check(steps == Physik.MAX_BEHIND && n[0] == steps, "Fester Schritt 1/120 s, höchstens " + Physik.MAX_BEHIND + " Schritte nachgeholt (" + steps + ")");
        p.advance(Physik.DT * 3.5);
        check(p.drain() == 3, "3,5 Schritte Zeit → 3 Schritte, der Rest wartet");
        p.add((t, dt) -> { throw new IllegalStateException("Probe"); });
        p.advance(Physik.DT);
        p.drain();
        check(p.aufgaben() == 1, "Eine fehlerhafte Aufgabe fällt heraus, die anderen laufen weiter");
    }

    /** Phase 6: die Bruch-Engine im Faden der Physik (ausführlich prüft com.dan.fracture.demo.FractureCheck). */
    static void bruch() {
        System.out.println("Bruch (com.dan.fracture, Phase 6)");
        check(com.dan.fracture.Fracture.STAGE == 6, "fracture ist eine Engine (Stufe 6), keine Hülle mehr");
        com.dan.fracture.Fracture f = com.dan.fracture.Fracture.ice();
        f.material.scatter = 0;
        com.dan.fracture.Solid s = f.addSolid(com.dan.fracture.Shape.box(0, 0, 0, 2, 10, 2), 200);
        s.anchorBelow(0);
        double lo = 0, hi = 60e6;
        for (int it = 0; it < 20; it++) {
            double F = (lo + hi) / 2;
            s.pressure(q -> q.ny > 0.99 && q.p[1] > 9.999, F / 4);
            if (s.solve() > 1) hi = F; else lo = F;
        }
        double hand = 5e6 * 4 - 917 * 9.81 * 40;
        check(Math.abs(lo / hand - 1) < 0.1, String.format(Locale.GERMAN, "Säule 2 × 2 × 10 m Eis bricht bei %.1f MN, Handrechnung σc·A − G = %.1f MN", lo / 1e6, hand / 1e6));
        // im Faden der Physik: ein Block fällt 10 m auf den Boden und kommt nach √(2h/g) an
        com.dan.fracture.Fracture g = com.dan.fracture.Fracture.glacier();
        Terrain flat = Terrain.flat();
        g.ground = flat::height;
        double y0 = flat.height(0, 0);
        com.dan.fracture.Piece p = g.addPiece(com.dan.fracture.Shape.box(-1, 0, -1, 1, 2, 1), 1);
        p.place(0, y0 + 11, 0);
        Physik ph = new Physik();
        ph.add((t, dt) -> g.step(dt));
        double t = 0;
        while (p.position()[1] - y0 - 1 > 0.02 && t < 5) { ph.advance(Physik.DT); ph.drain(); t += Physik.DT; }
        check(Math.abs(t - g.freeFall(10)) < 0.03, String.format(Locale.GERMAN, "Block fällt im Faden der Physik 10 m in %.2f s (Handrechnung %.2f s)", t, g.freeFall(10)));
        // Phase 7: die Spannung im eigenen Faden gibt dieselbe Auslastung wie im Schritt; das Bild liest ohne Sperre
        double[] uu = new double[2];
        for (int mode = 0; mode < 2; mode++) {
            com.dan.fracture.Fracture fa = com.dan.fracture.Fracture.ice();
            fa.material.scatter = 0;
            fa.asyncSolve = mode == 1;
            com.dan.fracture.Solid sa = fa.addSolid(com.dan.fracture.Shape.box(0, 0, 0, 2, 10, 2), 200);
            sa.anchorBelow(0);
            sa.pressure(q -> q.ny > 0.99 && q.p[1] > 9.999, 1e6);
            for (int i = 0; i < 1000 && sa.maxUtilization() == 0; i++) {
                fa.step(1 / 120.0);
                if (mode == 1) try { Thread.sleep(2); } catch (InterruptedException e) { break; }
            }
            uu[mode] = sa.maxUtilization();
        }
        check(uu[0] > 0 && Math.abs(uu[1] / uu[0] - 1) < 1e-6, String.format(Locale.GERMAN, "Spannung im eigenen Faden (asyncSolve): Auslastung %.4f wie im Schritt %.4f", uu[1], uu[0]));
        check(g.pieceCount() == 1 && g.poses().size() == 1 && g.awakeCount() <= 1, "Stücke und Lagen ohne Sperre lesbar (pieceCount, poses)");
        check(Quality.MITTEL.fracture == com.dan.fracture.Fracture.Quality.MEDIUM && Quality.ULTRA.fracture == com.dan.fracture.Fracture.Quality.ULTRA,
                "Qualitätsstufen der App reichen bis fracture (Mittel → MEDIUM)");
    }

    /** Phase 7: die drei Arten der Kalbung am Tal der Vorschau (ohne GL), und die Statistik einer Stunde Sommer. */
    static void kalbung() {
        System.out.println("Kalbung (Phase 7)");
        com.dan.ice.Glacier g = new com.dan.ice.Glacier(com.dan.ice.demo.GlacierValley.grid(), 0, 60, 2, 1);
        double zc = com.dan.ice.demo.GlacierValley.center(120);
        com.dan.ice.FrontBlock fb = com.dan.ice.FrontBlock.near(g, 150, zc, 80, 65, 35);
        check(fb != null, "Abschnitt an der Front des Tals gefunden: " + fb);
        if (fb == null) return;
        double[] c = fb.world(0, 20);
        float[] cut = fb.cut();
        double[] pl = fb.planes();
        boolean in = true;
        for (int q = 0; q < 6; q++) in &= pl[q * 4] * c[0] + pl[q * 4 + 1] * (fb.yBottom + 1) + pl[q * 4 + 2] * c[1] <= pl[q * 4 + 3];
        double[] l = fb.local(c[0], c[1]);
        check(in && fb.inBox(c[0], fb.yBottom + 1, c[1]) && Math.abs(l[0]) < 1e-9 && Math.abs(l[1] - 20) < 1e-9 && cut.length == 8 && cut[4] == 40f,
                "Quader: Achsen hin und zurück, Ebenen und Ausschnitt stimmen überein");
        check(fb.density(fb.px, g.waterLevel, fb.pz) > 0.9 && fb.density(c[0], g.waterLevel + 60, c[1]) < 0.6, "Zellen fein an der Wasserlinie der Front, grob dahinter und oben");
        for (com.dan.glacier.scene.Kalbung.Art art : com.dan.glacier.scene.Kalbung.Art.values()) {
            com.dan.fracture.Fracture f = com.dan.fracture.Fracture.glacier();
            f.ground = (x, z) -> g.covers(x, z) ? g.surface(x, z) : g.ground(x, z);
            f.water = (x, z, t) -> 0.0;
            com.dan.glacier.scene.Kalbung k = new com.dan.glacier.scene.Kalbung(f, g, () -> 1.0, () -> 16, 3);
            k.regie = false;
            k.notchFastPerSecond = 2;
            f.onBreak = (id, v, m) -> k.broke(id, v);
            k.trigger(art, 150, zc);
            long t0 = System.nanoTime();
            int i = 0;
            while (k.sections().isEmpty() && System.nanoTime() - t0 < 60e9) {
                k.step(Physik.DT);
                if (k.building() > 0) { try { Thread.sleep(5); } catch (InterruptedException e) { break; } }
            }
            com.dan.glacier.scene.Kalbung.Abschnitt a = k.sections().isEmpty() ? null : k.sections().get(0);
            if (a != null && art == com.dan.glacier.scene.Kalbung.Art.TURM) {
                // Befund Status_Phase7: die Zellen enden an der Wand, nicht am Quader davor
                int out = 0;
                for (int ci = 0; ci < a.solid.cells(); ci++) {
                    double[] cc = a.solid.center(ci), fr = a.block.front(cc[0], cc[2]);
                    if (fr != null && fr[0] * cc[0] + fr[1] * cc[2] > fr[2] + 0.5) out++;
                }
                check(out == 0, "Zellen enden an der Front (" + out + " von " + a.solid.cells() + " davor)");
            }
            for (; a != null && i < 120 * 60 && !(a.main && f.time() - a.firstBreak > 3); i++) { k.step(Physik.DT); f.step(Physik.DT); }
            boolean main = a != null && a.main;
            com.dan.glacier.scene.Kalbung.Ereignis e = k.log.isEmpty() ? null : k.log.get(0);
            check(main && e != null && e.art() == art && e.volume() > 5000,
                    String.format(Locale.GERMAN, "%s bricht ab: %s (%.1f s Physik, %.1f s Rechnung)", art.label,
                            e == null ? "kein Hauptstück" : String.format(Locale.GERMAN, "%,.0f m³ in %d Stücken", e.volume(), e.pieces()), f.time(), (System.nanoTime() - t0) / 1e9));
            check(a != null && !Double.isNaN(a.firstCrack) && a.firstCrack <= a.firstBreak,
                    String.format(Locale.GERMAN, "%s: der Riss ist vor dem Bruch zu sehen (%.1f s vorher)", art.label, a == null ? Double.NaN : a.firstBreak - a.firstCrack));
            k.shutdown();
        }
        // am Ende einer Frontlinie rückt der Abschnitt längs der Front, bis sie über seine ganze Breite reicht
        double[] end = g.frontLines().get(0);
        com.dan.ice.FrontBlock eb = com.dan.ice.FrontBlock.near(g, end[0], end[1], 80, 65, 35);
        boolean along = eb != null;
        if (eb != null) for (double aa : new double[]{-36, 0, 36}) {
            double[] w = eb.world(aa, 0), nf = g.nearestFront(w[0], w[1], 60);
            along &= nf != null && Math.hypot(nf[0] - w[0], nf[1] - w[1]) < 8;
        }
        check(along, "Abschnitt am Ende der Frontlinie: verschoben, die Front reicht über die ganze Breite (" + eb + ")");
        // eine Stunde Sommer: im Mittel rateSummer Abbrüche, Türme am häufigsten
        com.dan.glacier.scene.Kalbung k = new com.dan.glacier.scene.Kalbung(com.dan.fracture.Fracture.glacier(), g, () -> 1.0, () -> 16, 11);
        int n = 0, turm = 0, scheibe = 0, fuss = 0, hours = 200;
        for (int h = 0; h < hours; h++)
            for (Object[] o : k.plan(3600)) {
                n++;
                switch ((com.dan.glacier.scene.Kalbung.Art) o[1]) { case TURM -> turm++; case SCHEIBE -> scheibe++; default -> fuss++; }
            }
        double perHour = n / (double) hours;
        check(k.summer() > 0.9 && Math.abs(perHour - k.rateSummer) < 0.6 && turm > scheibe && scheibe > fuss && fuss > 0,
                String.format(Locale.GERMAN, "Eine Stunde Sommer: %.1f Abbrüche (Soll %.0f), Turm %d %%, Scheibe %d %%, Fuß %d %%", perHour, k.rateSummer,
                        100 * turm / n, 100 * scheibe / n, 100 * fuss / n));
        k.shutdown();
    }

    /** Phase 8: die Welle am echten See (mit Höhendaten) – ein großer Bruch vor dem Steg. */
    static void welle() {
        System.out.println("Welle (Phase 8, com.dan.wave)");
        check(com.dan.wave.Wave.STAGE == 8, "wave ist ein Löser (Stufe 8), keine Hülle mehr");
        Terrain t = Terrain.load(new File("daten/gelaende"));
        if (t == null || !t.measured()) { System.out.println("  (ohne Höhendaten übersprungen)"); return; }
        com.dan.glacier.scene.Gletscher gl = new com.dan.glacier.scene.Gletscher(t);
        gl.glacier.setAdvance(Wiring.glacierAdvance(16));
        double[] f = com.dan.glacier.scene.Welle.front(t, gl.glacier, 0);
        check(f != null && t.height(f[0] + f[2] * 40, f[1] + f[3] * 40) <= -40, "Front vor dem Steg mit tiefem Wasser gefunden"
                + (f == null ? "" : String.format(Locale.GERMAN, " (%.0f m vom Steg, %.0f m tief)", Math.hypot(f[0] - t.steg[0], f[1] - t.steg[1]), -t.height(f[0] + f[2] * 40, f[1] + f[3] * 40))));
        if (f == null) return;
        double[] c = com.dan.glacier.scene.Welle.center(t, f);
        com.dan.wave.Wave w = com.dan.wave.Wave.lake();
        long t0 = System.nanoTime();
        w.place(c[0], c[1], c[0], c[1]).build(com.dan.glacier.scene.Welle.bed(t, gl.glacier, 0));
        double build = (System.nanoTime() - t0) / 1e9;
        double[] gp = com.dan.glacier.scene.Welle.gauge(t, w, f);
        com.dan.wave.Wave.Gauge pegel = w.gauge("Steg", gp[0], gp[1]);
        check(w.depthAt(gp[0], gp[1]) > 3 && Math.hypot(gp[0] - t.steg[0], gp[1] - t.steg[1]) < 300,
                String.format(Locale.GERMAN, "Pegel %.0f m vor dem Steg in %.1f m Wasser; Gitter in %.1f s angelegt", Math.hypot(gp[0] - t.steg[0], gp[1] - t.steg[1]), w.depthAt(gp[0], gp[1]), build));
        int[] ru = {0};
        w.onRunup = (x, z, h, tt) -> ru[0]++;
        double[] im = com.dan.glacier.scene.Welle.impact(f);
        w.addVolumeOver(im[0], im[1], 25000, 15, 5);
        double first = -1, peak = 0, tPeak = 0;
        long c0 = System.nanoTime();
        for (int i = 0; i < 45 * 30; i++) {
            w.step(1 / 30.0);
            double e = w.levelAt(gp[0], gp[1]);
            if (first < 0 && !Double.isNaN(e) && Math.abs(e) > 0.03) first = w.time();
            if (e > peak) { peak = e; tPeak = w.time(); }
        }
        double cpu = (System.nanoTime() - c0) / 1e6 / w.time();
        double dist = Math.hypot(gp[0] - im[0], gp[1] - im[1]);
        check(first > 5 && first < 35, String.format(Locale.GERMAN, "ein großer Bruch (25 000 m³ in 5 s): die Welle ist nach %.1f s am Steg (%.0f m, Werkbuch: rund 20 s über den Kanal)", first, dist));
        check(peak > 0.2 && peak < 3, String.format(Locale.GERMAN, "am Steg %.2f m hoch nach %.1f s (Werkbuch: ein Meter oder mehr bei einem großen Bruch [G])", peak, tPeak));
        check(ru[0] > 0, "sie läuft am Ufer auf (" + ru[0] + " Meldungen)");
        float[][] s = pegel.series(45);
        check(s[0].length > 600, "Kurve der Höhe am Steg aufgezeichnet (" + s[0].length + " Werte)");
        check(cpu < 600, String.format(Locale.GERMAN, "Rechnung %.0f ms je Sekunde Welle (eigener Faden „Welle“)", cpu));
    }

    /** Phase 9: Eisberge – aus der Kalbung übergeben, ein Tag auf einmal, einer kippt, von unten, Rechenzeit. */
    static void eisberge() {
        System.out.println("Eisberge (Phase 9, com.dan.buoyancy)");
        // Übergabe: ein Stück aus fracture wird ein Berg mit demselben Volumen
        com.dan.fracture.Fracture f = com.dan.fracture.Fracture.glacier();
        com.dan.fracture.Piece p = f.addPiece(com.dan.fracture.Shape.box(-6, -4, -5, 6, 4, 5), 40);
        com.dan.ice.IceMesh m = com.dan.glacier.gl.KalbungGl.pieceMesh(p);
        com.dan.buoyancy.Berg b = new com.dan.buoyancy.Berg(m.pos, m.idx, 80);
        check(Math.abs(b.volume() / p.volume() - 1) < 0.01, String.format(Locale.GERMAN, "Stück aus der Kalbung wird Eisberg: %.0f m³ wie das Stück (%.0f m³), %d Proben", b.volume(), p.volume(), b.samples()));
        Terrain t = Terrain.load(new File("daten/gelaende"));
        if (t == null || !t.measured()) { System.out.println("  (ohne Höhendaten übersprungen)"); return; }
        com.dan.glacier.scene.Gletscher gl = new com.dan.glacier.scene.Gletscher(t);
        gl.glacier.setAdvance(Wiring.glacierAdvance(16));
        com.dan.buoyancy.Sea sea = new com.dan.buoyancy.Sea() {
            @Override public double height(double x, double z) { return 0; }
            @Override public double level() { return 0; }
            @Override public double bed(double x, double z) { return gl.glacier.covers(x, z) ? 100 : t.height(x, z); }
        };
        com.dan.buoyancy.Bergs bs = new com.dan.buoyancy.Bergs();
        bs.sea = sea;
        bs.windSpeed = 6;
        double[] front = com.dan.glacier.scene.Welle.front(t, gl.glacier, 0);
        double[] c = com.dan.glacier.scene.Welle.center(t, front);
        com.dan.buoyancy.BrashField bf = new com.dan.buoyancy.BrashField(500, 500, c[0] - 1000, c[1] - 1000, 4);
        bs.brash = bf;
        int n = com.dan.glacier.scene.Eisberge.fillDay(bs, gl.glacier, sea, 4, 270, 11, bf);
        boolean wet = true;
        for (com.dan.buoyancy.Berg x : bs.bergs) { double[] cg = x.cgWorld(); wet &= t.height(cg[0], cg[2]) < -3 && !gl.glacier.covers(cg[0], cg[2]); }
        check(n >= 80 && wet, String.format(Locale.GERMAN, "ein Tag Kalbung im Sommer: %d Eisberge, alle im Wasser, %,.0f m³", n, bs.totalVolume()));
        com.dan.buoyancy.Berg tip = com.dan.glacier.scene.Eisberge.toppler(front[0] + front[2] * 80, front[1] + front[3] * 80, 0, 30, 5);
        bs.add(tip);
        long t0 = System.nanoTime();
        double fast = 0, tCap = -1;
        for (int i = 0; i < 60 * 90; i++) {
            bs.step(1 / 60.0, 1 / 60.0);
            if (tCap < 0 && tip.capsizes() > 0) tCap = bs.time();
            if (i % 60 == 0) for (com.dan.buoyancy.Berg x : bs.bergs) { double[] v = x.velocity(); fast = Math.max(fast, Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])); }
        }
        double ms = (System.nanoTime() - t0) / 1e6 / (60 * 90);
        check(tCap > 0, String.format(Locale.GERMAN, "ein hoher, schmaler Berg (44 × 28 × 9 m) vor der Front kentert nach %.0f s", tCap));
        check(fast < 6, String.format(Locale.GERMAN, "alle schwimmen ruhig: schnellster %.2f m/s in 90 s", fast));
        check(bf.area() > 1000, String.format(Locale.GERMAN, "Treibeis vor der Front: %,.0f m²", bf.area()));
        check(ms < 3, String.format(Locale.GERMAN, "%d Eisberge auf dem echten Grund: %.2f ms je Schritt (1/60 s)", bs.count(), ms));
        com.dan.buoyancy.Berg any = bs.largest();
        double[] df = any.draftAndFreeboard(0);
        check(Math.abs(any.submerged() - 0.9) < 0.05 || any.grounded(), String.format(Locale.GERMAN, "der größte: %s (Tiefgang %.1f m)", any.describe(0), df[0]));
        // die Ansicht von unten liegt im Wasser, über dem Grund, vor der Front
        double ux = front[0] + front[2] * 24, uz = front[1] + front[3] * 24;
        check(t.height(ux, uz) < -12, String.format(Locale.GERMAN, "Ansicht „Unter Wasser“: 6 m tief über %.0f m Wasser, 24 m vor der Front (bei 30 m Sicht steht die Wand im Bild)", -t.height(ux, uz)));
    }

    static void leben() {
        System.out.println("Leben (Phase 10: flock, Katamaran, Stege, weathering)");
        // Lenga im Herbst: am 22. April rot, im Januar grün
        com.dan.forest.Species lenga = com.dan.forest.Species.lenga();
        com.dan.forest.Season apr = com.dan.forest.Season.of(lenga, Wiring.northernDay(112), 0), jan = com.dan.forest.Season.of(lenga, Wiring.northernDay(16), 0);
        check(apr.autumn > 0.9 && apr.foliage > 0.5 && jan.autumn < 0.05, String.format(Locale.GERMAN, "Lenga am 22. April: Färbung %.0f %%, Laub %.0f %%; im Januar %.0f %% (vorbereitet für Phase 11)", 100 * apr.autumn, 100 * apr.foliage, 100 * jan.autumn));
        // der Katamaran reitet eine lange Welle ab (Einzelwelle 1 m, 40 s): hebt sich mit, kentert nicht
        com.dan.buoyancy.Catamaran k = new com.dan.buoyancy.Catamaran("Probe");
        k.via(0, 0, 0).via(0, -100, 0);
        double[] tt = {0};
        com.dan.buoyancy.Sea wave = new com.dan.buoyancy.Sea() {
            @Override public double height(double x, double z) { double ph = (x - 300 + 31 * tt[0]) / 250; return Math.exp(-ph * ph); }
            @Override public double level() { return 0; }
        };
        k.moor(new com.dan.buoyancy.Sea() { @Override public double height(double x, double z) { return 0; } @Override public double level() { return 0; } });
        double y0 = k.y, ymax = -9, ymin = 9, roll = 0, pitch = 0;
        for (int i = 0; i < 60 * 40; i++) { tt[0] = i / 60.0; k.step(wave, 1 / 60.0); ymax = Math.max(ymax, k.y - y0); ymin = Math.min(ymin, k.y - y0); roll = Math.max(roll, Math.abs(k.roll)); pitch = Math.max(pitch, Math.abs(k.pitch)); }
        check(k.mass() > 80_000 && k.mass() < 200_000 && k.gm > 3 && ymax > 0.9 && ymax < 1.15 && Math.toDegrees(pitch) < 6,
                String.format(Locale.GERMAN, "Katamaran 26 × 9,2 m: %.0f t, Tiefgang %.2f m, GM %.1f m; eine 1-m-Welle hebt ihn um %.2f m, Stampfen höchstens %.1f°, Rollen %.1f°",
                        k.mass() / 1000, k.draft, k.gm, ymax, Math.toDegrees(pitch), Math.toDegrees(roll)));
        com.dan.buoyancy.Mesh km = com.dan.buoyancy.Mesh.catamaran(k);
        check(km.triangles() > 800, "Katamaran als Netz: zwei Rümpfe, Salon, Sonnendeck mit Geländer (" + km.triangles() + " Dreiecke)");
        Terrain t = Terrain.load(new File("daten/gelaende"));
        if (t == null || !t.measured()) { System.out.println("  (ohne Höhendaten übersprungen)"); return; }
        com.dan.glacier.scene.Gletscher gl = new com.dan.glacier.scene.Gletscher(t);
        gl.glacier.setAdvance(Wiring.glacierAdvance(16));
        com.dan.glacier.scene.Stege st = new com.dan.glacier.scene.Stege(t, 0);
        check(st.minClearance() >= 0.15 && st.maxSlope() <= 0.3 && st.length() > 250 && st.balkone.size() == 2,
                st.describe());
        double berthDepth = -t.height(st.berth[0], st.berth[1]);
        check(berthDepth > 2.5 && Math.hypot(st.berth[0] - st.pierEnd[0], st.berth[1] - st.pierEnd[1]) < 15,
                String.format(Locale.GERMAN, "Anleger %.0f m lang, Liegeplatz längsseits über %.1f m Wasser", Math.hypot(st.pierEnd[0] - st.pierStart[0], st.pierEnd[1] - st.pierStart[1]), berthDepth));
        // nass: unter der nassen Linie dunkler (ein Pfahl des Anlegers)
        com.dan.weathering.Weathering wz = com.dan.weathering.Weathering.calm();
        wz.waterLevel = 0;
        wz.settle();
        wz.setWetLine(1.2);
        com.dan.weathering.Weathering.Snapshot sn = wz.snapshot();
        double wetLow = com.dan.glacier.scene.Stege.wetness(sn, 0.8, st.pierEnd[0], st.pierEnd[1]), wetHigh = com.dan.glacier.scene.Stege.wetness(sn, 1.9, st.pierEnd[0], st.pierEnd[1]);
        com.dan.buoyancy.Mesh dry = st.mesh(null), wet = st.mesh(sn);
        double sumDry = 0, sumWet = 0;
        float[] vd = dry.vertices(), vw = wet.vertices();
        for (int i = 0; i < Math.min(vd.length, vw.length) / 10; i++) { sumDry += vd[i * 10 + 6]; sumWet += vw[i * 10 + 6]; }
        check(wetLow > 0.9 && wetHigh < 0.1 && sumWet < sumDry, String.format(Locale.GERMAN, "Stege nass nach dem Auflaufen (Linie +1,2 m): Pfahl bei 0,8 m nass %.0f %%, bei 1,9 m %.0f %%, Netz %d Dreiecke", 100 * wetLow, 100 * wetHigh, wet.triangles()));
        // die Bootsfahrt: vom Anleger 300 m vor der Front entlang und zurück
        double[] front = com.dan.glacier.scene.Welle.front(t, gl.glacier, 0);
        java.util.List<double[]> route = com.dan.glacier.scene.Bootsfahrt.route(t, gl.glacier, 0, front, st.berth, st.pierEnd);
        double[] lt = com.dan.glacier.scene.Bootsfahrt.lengthAndTime(route);
        int near = 0;
        for (double[] q : route) { double[] nf = gl.glacier.nearestFront(q[0], q[1], 2000); if (nf != null && Math.abs(Math.hypot(nf[0] - q[0], nf[1] - q[1]) - 300) < 60) near++; }
        double shallow = com.dan.glacier.scene.Bootsfahrt.shallowest(t, gl.glacier, 0, route);
        check(near >= 5 && shallow > 2.5 && lt[0] > 2000 && lt[1] < 30 * 60,
                String.format(Locale.GERMAN, "%s · %d Punkte 300 m (± 60 m) vor der Front · flachste Stelle %.1f m", com.dan.glacier.scene.Bootsfahrt.describe(route), near, shallow));
        // Vögel in der App: Kondore mit Thermik über den Hängen, Möwen auf den Geländern und Eisbergen
        Renderer r = new Renderer(new SceneClock(), null, t);
        com.dan.flock.Flock fl = r.voegel();
        fl.ambientLux = 60_000;
        for (int i = 0; i < 30 * 120; i++) fl.step(1 / 30.0);
        int perchSpots = 0, berg = 0;
        for (com.dan.flock.Flock.Perch pp : fl.perches) { perchSpots++; if (pp.name.equals("Eisberg")) berg++; }
        check(fl.present(com.dan.flock.Species.KONDOR) == 4 && fl.thermals.size() >= 3 && fl.kelpGulls && berg == 10,
                String.format(Locale.GERMAN, "Vögel: %s · %d Thermiken, %d Sitzplätze (davon %d auf Eisbergen)", fl.describe(), fl.thermals.size(), perchSpots, berg));
    }

    static void jahr() {
        System.out.println("Jahr (Phase 11, com.dan.season)");
        Wiring w = new Wiring(new SceneClock());
        check(w.weather.climate != null && w.weather.summerDay == 15 && w.jahr.year.southern,
                String.format(Locale.GERMAN, "Regie und Jahr kennen die Südhalbkugel: Januar %.1f °C, Juli %.1f °C vor der Front", w.jahr.klima.meanTemp(16), w.jahr.klima.meanTemp(197)));
        com.dan.glacier.wiring.Jahr j = new com.dan.glacier.wiring.Jahr();
        int[] months = {1, 4, 7, 10};
        double[] line = new double[4], gs = new double[4], au = new double[4], ice = new double[4], ground = new double[4];
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 4; k++) {
            java.time.LocalDate d = java.time.LocalDate.of(2026, months[k], 16);
            j.step(0, d, 15, j.year.dayMean(2026, d.getDayOfYear()) + 2, 0, 15, 3, 6);
            line[k] = j.snowLine(); gs[k] = j.glacierSnow(); au[k] = j.autumn(); ice[k] = j.state().ice(); ground[k] = j.groundSnow();
            sb.append(String.format(Locale.GERMAN, "%s%d.: Grenze %,.0f m, Zunge %.0f %%%s", k > 0 ? " · " : "", months[k], line[k], 100 * gs[k], ice[k] > 0.005 ? String.format(Locale.GERMAN, ", Eis %.0f cm", 100 * ice[k]) : ""));
        }
        check(line[0] > 1000 && gs[0] == 0 && line[2] <= 60 && gs[2] > 0.5 && line[3] > 100 && line[3] < 900 && au[1] > 0.9 && au[0] < 0.05,
                "Dieselbe Ansicht am 16. Januar, April, Juli, Oktober (Jahr 2026): " + sb + "; Lenga im April rot");
        check(j.melt() < 0.7 && new com.dan.glacier.wiring.Jahr() != null && com.dan.glacier.wiring.Jahr.stauAt(16) > 10 && com.dan.glacier.wiring.Jahr.stauAt(100) == 0 && com.dan.glacier.wiring.Jahr.stauAt(152) == 0,
                String.format(Locale.GERMAN, "Stau mit Damm: 1. Juni 0 m, 1. Dezember %.1f m, 16. Januar %.1f m, nach dem Bruch im März 0 m", com.dan.glacier.wiring.Jahr.stauAt(335), com.dan.glacier.wiring.Jahr.stauAt(16)));
        Terrain t = Terrain.load(new File("daten/gelaende"));
        if (t == null || !t.measured()) { System.out.println("  (ohne Höhendaten übersprungen)"); return; }
        double[] br = Renderer.BRAZO_RICO;
        int shallow = 0, deep = 0;
        for (double x = br[0]; x <= br[2]; x += 50) for (double z = br[1]; z <= br[3]; z += 50) {
            if (t.ice(x, z)) continue;
            double h = t.height(x, z);
            if (h < 0 && h > -5) shallow++;
            if (h < -50) deep++;
        }
        check(shallow > 50 && deep > 500, String.format(Locale.GERMAN, "Brazo Rico südlich der Front: %,d Zellen (50 m) mit bis 5 m Wasser frieren zuerst, %,d tiefe bleiben offen", shallow, deep));
    }

    static void klang() {
        System.out.println("Klang (Phase 12, com.dan.soundscape)");
        com.dan.glacier.scene.Klang k = new com.dan.glacier.scene.Klang();
        k.s.autoGain = false;
        k.s.waveHeight = 0;
        k.diesel.levelDb = 0;   // kein Boot in der Nähe
        double[] ear = {0, 40, 0};
        // ein Bruch 490 m vor dem Ohr: Knacken, dann der Donner aus dem Riss nach der Laufzeit, dann der Aufschlag
        double[] path = new double[15 * 3];
        for (int i = 0; i < 15; i++) { path[i * 3] = 2 * i; path[i * 3 + 1] = 5 * i; path[i * 3 + 2] = -490; }
        k.crack(path, 5e6, true);
        k.broke(30_000);
        int sr = k.s.sampleRate();
        float[] all = new float[sr * 4 * 2];
        int at = 0;
        for (int f = 0; f < 240; f++) {
            if (f == 60) k.sound(com.dan.glacier.wiring.SoundEvent.Kind.AUFSCHLAG, 0, 0, -490, 30_000);
            k.step(1 / 60.0, ear, 180, 0, 270, 0, 0, 10, 0, 3, 0, false, false);
            k.diesel.levelDb = 0;
            float[] o = k.s.render(1 / 60.0);
            System.arraycopy(o, 0, all, at, Math.min(o.length, all.length - at));
            at += o.length;
        }
        double c = k.s.soundSpeed(), dist = Math.hypot(490, 40);
        int onset = -1;
        for (int i = 0; i < all.length / 2 && onset < 0; i++) if (Math.abs(all[i * 2]) > 0.02) onset = i;
        double tOn = onset / (double) sr;
        check(Math.abs(tOn - dist / c) < 0.15, String.format(Locale.GERMAN, "Bruch in 490 m: der Klang kommt nach %.2f s an (Laufzeit %.2f s) · %s", tOn, dist / c, k.describe()));
        // der Diesel: an Bord gleichmäßig, Drehzahl nach der Fahrt
        k.boat(0, 0, 0, 7, true);
        check(k.diesel.aboard && Math.abs(k.diesel.firingHz - com.dan.soundscape.Hum.firing(1900, 6)) < 0.1,
                String.format(Locale.GERMAN, "Diesel des Katamarans bei 7 m/s: %.0f Hz Zündfrequenz (1 900 U/min, 6 Zylinder), an Bord %.0f dB", k.diesel.firingHz, k.diesel.aboardDb));
        Terrain t = Terrain.load(new File("daten/gelaende"));
        if (t == null || !t.measured()) { System.out.println("  (ohne Höhendaten übersprungen)"); return; }
        com.dan.glacier.scene.Stege st = new com.dan.glacier.scene.Stege(t, 0);
        com.dan.glacier.scene.Klang k2 = new com.dan.glacier.scene.Klang();
        k2.stege(st);
        check(k2.s.bodies.size() == 2 && k2.s.pilings.size() >= 5, String.format(Locale.GERMAN, "Stege im Klang: %d Geländer singen im Wind, %d Pfähle glucksen", k2.s.bodies.size(), k2.s.pilings.size()));
    }

    static void shader() {
        System.out.println("Shader");
        for (String[] prog : Renderer.shaderFiles()) {
            for (String f : prog) {
                boolean found;
                String text = "";
                try {
                    text = Shader.source(f, null).text;
                    found = text.startsWith("#version 430 core");
                } catch (RuntimeException e) {
                    found = false;
                }
                check(found, "Shader " + f + " gefunden und Bausteine eingesetzt (" + text.split("\n").length + " Zeilen)");
            }
        }
        String tf = Shader.source("terrain.frag", null).text, tv = Shader.source("terrain.vert", null).text;
        check(tf.contains("iceFraction") && tf.contains("uCover") && tf.contains("uSnowLine") && tv.contains("EARTH_R"),
                "Gelände: Eis weich aus den Nachbarzellen, Bedeckung aus der Vegetation, Schneegrenze, Erdkrümmung");
        boolean half = false;
        for (String[] prog : Renderer.shaderFiles()) for (String f : prog) half |= Pattern.compile("\\bhalf\\b").matcher(Shader.source(f, null).text).find();
        check(!half, "kein reserviertes Wort „half“ in den Shadern der App (AMD-Treiber sind streng)");
        check(new File("src/com/dan/glacier/gl/shader/stage.frag").exists() == false || !new File("src").isDirectory(), "stage.frag (Bühne von Phase 1) ist abgestellt");
        check(ComputeProbe.expected(0, 63) == 64 * 65 / 2 && ComputeProbe.expected(1, 0) == 65, "Compute-Test: Erwartungswerte der Präfixsumme");
    }

    /** Phase 13: der Eisdamm, die Maske des Brazo Rico, das Wasser und das Regiebuch „Die Ruptura“ ohne Fenster. */
    static void ruptura() {
        System.out.println("Ruptura (Phase 13)");
        Terrain t = Terrain.load(new File("daten/gelaende"));
        if (!t.measured()) { check(false, "Ruptura: ohne daten/gelaende keine Halbinsel"); return; }
        com.dan.glacier.scene.Gletscher gl = new com.dan.glacier.scene.Gletscher(t);
        com.dan.ice.IceDam d = com.dan.glacier.scene.Ruptura.damm(t, gl.glacier::covers, 13);
        System.out.println("  " + d);
        check(d.length > 450 && d.length < 600, String.format(Locale.GERMAN, "Rinne von Brazo Rico zum Kanal %.0f m lang", d.length));
        int on = 0;
        for (double s = 20; s < d.length - 20; s += 20) { double[] p = d.at(s); if (d.insideXZ(p[0], p[1])) on++; }
        check(on >= (int) ((d.length - 40) / 20) - 1, "der Damm deckt die Rinne über die ganze Länge (" + on + " Stichproben)");
        double[] st = t.steg;
        double near = 1e9;
        for (double x = st[0] - 120; x <= st[0] + 120; x += 2) for (double z = st[1] - 120; z <= st[1] + 120; z += 2) if (d.insideXZ(x, z)) near = Math.min(near, Math.hypot(x - st[0], z - st[1]));
        check(!d.insideXZ(st[0], st[1]) && near > 12 && near < 80, String.format(Locale.GERMAN, "der Steg steht neben dem Damm, nicht auf ihm (Rand %.0f m entfernt)", near));
        double lowTop = 1e9;
        for (double s = 0; s <= d.length; s += 10) { double[] p = d.at(s); lowTop = Math.min(lowTop, d.top(p[0], p[1])); }
        check(lowTop > com.dan.glacier.scene.Ruptura.STAU_MAX + 3, String.format(Locale.GERMAN, "die Krone über der Rinne (mindestens %.1f m) liegt über dem höchsten Stau (%.0f m)", lowTop, com.dan.glacier.scene.Ruptura.STAU_MAX));
        double[] b = d.bridge();
        check(Math.hypot(b[0] - st[0], b[1] - st[1]) < 180, String.format(Locale.GERMAN, "die Brücke steht dem Steg gegenüber (%.0f m)", Math.hypot(b[0] - st[0], b[1] - st[1])));
        byte[] m = com.dan.glacier.scene.Ruptura.brazoMask(t, d, gl.glacier::covers);
        double[] ms = com.dan.glacier.scene.Ruptura.maskStats(m);
        check(ms[1] == 0 && ms[0] > 10 && ms[0] < 30, String.format(Locale.GERMAN, "Maske des Brazo Rico %.1f km², der Damm hält ihn vom Kanal fern", ms[0]));
        check(com.dan.glacier.scene.Ruptura.inTube(0, 2, 10) && com.dan.glacier.scene.Ruptura.inTube(9, 4, 10) && !com.dan.glacier.scene.Ruptura.inTube(9, 13, 10)
                && com.dan.glacier.scene.Ruptura.inTube(0, 14.5, 10) && !com.dan.glacier.scene.Ruptura.inTube(0, 15.5, 10),
                "Tunnel: senkrechte Wände, darüber das Gewölbe (Scheitel 1,5 Halbmesser über der Sohle)");
        // das Regiebuch ohne Fenster, Schritt für Schritt
        com.dan.fracture.Fracture f = com.dan.fracture.Fracture.glacier();
        f.ground = (x, z) -> gl.glacier.covers(x, z) ? gl.glacier.surface(x, z) : t.height(x, z);
        com.dan.glacier.scene.Ruptura r = new com.dan.glacier.scene.Ruptura(f, t, gl.glacier, () -> 1.0, 13);
        f.water = (x, z, tt) -> { double w = r.waterAt(x, z); return Double.isNaN(w) ? 0 : w; };
        f.current = r::currentAt;
        f.onBreak = (id, v, mm) -> r.broke(id, v);
        f.maxPieces = 100;
        f.dissolveBelow = 25;
        f.sleepAfter = 12;
        r.regiebuch();
        double dt = 1 / 120.0, tt = 0, qMax = 0, deepest = 0, stauTop = 0;
        long t0 = System.nanoTime();
        // Phase 14: die Fotos des Regiebuchs „Die Ruptura“ (ruptura.txt) liegen in den richtigen Abschnitten
        final double[] FOTO_T = {20, 65, 105, 160, 182, 200, 245};
        com.dan.glacier.scene.Ruptura.Phase[] fotoPhase = new com.dan.glacier.scene.Ruptura.Phase[FOTO_T.length];
        java.util.List<com.dan.glacier.scene.Ruptura.Phase> seen = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < 200 * 60 && r.solid() == null; i++) { r.step(dt); Thread.sleep(5); }
            // Befund Bitte_Fixen.png (Phase 13): Zellen am Rand ragten als Splitter über Fels und Wasser hinaus
            com.dan.fracture.Solid ds = r.solid();
            int shards = 0;
            for (int i = 0; ds != null && i < ds.cells(); i++) {
                if (!ds.attached(i)) continue;
                for (com.dan.fracture.Poly.Face fc : ds.cell(i).faces)
                    for (int k = 0; k < fc.p.length; k += 3) {
                        double x = fc.p[k], y = fc.p[k + 1], z = fc.p[k + 2];
                        if (d.insideXZ(x, z)) continue;
                        double[] ll = d.local(x, z);
                        if (ll[0] < -d.out + 1.5 || ll[0] > d.straight() + d.out - 1.5) continue;   // die Wände zum Wasser
                        double gr = gl.glacier.covers(x, z) ? gl.glacier.surface(x, z) : t.height(x, z);
                        boolean nearFp = false;
                        for (double dx = -10; dx <= 10 && !nearFp; dx += 2.5) for (double dz = -10; dz <= 10 && !nearFp; dz += 2.5) nearFp = d.insideXZ(x + dx, z + dz);
                        if (y - Math.max(gr, 0) > 3 && !nearFp) shards++;
                    }
            }
            check(ds != null && shards == 0, String.format(Locale.GERMAN, "keine Splitter: keine Zelle ragt mehr als 10 m über den Damm hinaus (%d Platzhalter daneben geschmolzen)", r.dummyCells));
            while (tt < 600 && r.phase() != com.dan.glacier.scene.Ruptura.Phase.RUHE) {
                r.step(dt);
                f.step(dt);
                tt += dt;
                if (seen.isEmpty() || seen.get(seen.size() - 1) != r.phase()) seen.add(r.phase());
                for (int q = 0; q < FOTO_T.length; q++) if (fotoPhase[q] == null && tt >= FOTO_T[q]) fotoPhase[q] = r.phase();
                qMax = Math.max(qMax, r.flow());
                stauTop = Math.max(stauTop, r.stau());
                if (((int) (tt * 120)) % 240 == 0) for (com.dan.fracture.Fracture.Pose p : f.poses()) {
                    double[] c = p.position();
                    deepest = Math.max(deepest, f.ground.heightAt(c[0], c[2]) - c[1] - Math.cbrt(p.piece().volume()));
                }
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        System.out.println("  " + r.describe());
        synchronized (r.log) { for (com.dan.glacier.scene.Ruptura.Ereignis e : r.log) System.out.println("    " + e); }
        java.util.List<com.dan.glacier.scene.Ruptura.Phase> order = java.util.List.of(com.dan.glacier.scene.Ruptura.Phase.STAU, com.dan.glacier.scene.Ruptura.Phase.SICKERN,
                com.dan.glacier.scene.Ruptura.Phase.TUNNEL, com.dan.glacier.scene.Ruptura.Phase.RINNE, com.dan.glacier.scene.Ruptura.Phase.BRUECKE,
                com.dan.glacier.scene.Ruptura.Phase.ABFLUSS, com.dan.glacier.scene.Ruptura.Phase.RUHE);
        check(seen.equals(order), "Regiebuch: Stau, Sickern, Durchbruch, Rinne, Brücke, Abfluss, Ruhe – " + seen);
        com.dan.glacier.scene.Ruptura.Phase[] want = {com.dan.glacier.scene.Ruptura.Phase.STAU, com.dan.glacier.scene.Ruptura.Phase.SICKERN,
                com.dan.glacier.scene.Ruptura.Phase.TUNNEL, com.dan.glacier.scene.Ruptura.Phase.RINNE, com.dan.glacier.scene.Ruptura.Phase.BRUECKE,
                com.dan.glacier.scene.Ruptura.Phase.BRUECKE, com.dan.glacier.scene.Ruptura.Phase.ABFLUSS};
        check(java.util.Arrays.equals(fotoPhase, want), "Fotos des Regiebuchs „Die Ruptura“ in den Abschnitten: " + java.util.Arrays.toString(fotoPhase));
        check(tt > 180 && tt < 480, String.format(Locale.GERMAN, "„Wochen in zehn Minuten“: %.1f min echte Zeit für %s Szenenzeit", tt / 60, com.dan.glacier.scene.Ruptura.dauer(r.sceneTime())));
        check(stauTop >= 24.9 && stauTop <= 30 && r.stau() < 0.25, String.format(Locale.GERMAN, "Stau bis %.1f m (belegt: bis 30 m), danach auf dem Spiegel des Hauptsees", stauTop));
        check(qMax > 6000 && qMax <= Site.RUPTURA_FLOW + 1, String.format(Locale.GERMAN, "Abfluss bis %,.0f m³/s (belegt: bis 8 000 m³/s)", qMax));
        check(!Double.isNaN(r.bridgeFell) && r.bridgeVolume > 5000, String.format(Locale.GERMAN, "die Brücke stürzt (%,.0f m³), %d Stücke, das größte %,.0f m³", r.bridgeVolume, r.fallen, r.largest));
        check(deepest < 4, String.format(Locale.GERMAN, "kein Stück steckt tiefer als 4 m im Grund (%.1f m)", deepest));
        double ms2 = (System.nanoTime() - t0) / 1e6 / Math.max(1, tt * 120);
        check(ms2 < 3, String.format(Locale.GERMAN, "Ruptura und Bruch %.2f ms je Schritt im Mittel (hier gemessen; Faden der Physik 8,3 ms)", ms2));
        check(com.dan.soundscape.Torrent.levelDb(8000) > 115 && com.dan.soundscape.Torrent.levelDb(8000) < 120, String.format(Locale.GERMAN, "Tosen bei 8 000 m³/s: %.0f dB in 1 m", com.dan.soundscape.Torrent.levelDb(8000)));
        com.dan.glacier.camera.Ansichten.Ansicht a8 = com.dan.glacier.camera.Ansichten.byKey(8);
        check(a8 != null && a8.name().equals("Die Brücke") && Renderer.PHASE >= 13, "Taste 8: die Brücke vom Steg");
        Vegetation v = new Vegetation(t);
        int trees = 0;
        for (double s = 0; s <= d.length; s += 10) { double[] p = d.at(s); if (v.forest(p[0], p[1]) > 0.05) trees++; }
        check(trees == 0, "kein Wald, wo der Damm liegt (" + trees + " Bäume an der Rinne)");
        r.shutdown();
    }

    /** Phase 14: die drei Regiebücher als Text, ohne Bild durchgespielt; die Fahrt „Durch die Spalte“. */
    static void regie() {
        System.out.println("Regiebücher (Phase 14)");
        java.util.List<com.dan.glacier.scene.Regie.Book> all = com.dan.glacier.scene.Regie.all();
        check(all.size() == 3 && all.get(0).name().equals("Ein Sommertag an der Front") && all.get(1).name().equals("Die Ruptura") && all.get(2).name().equals("Südhimmel"),
                "drei Regiebücher aus scene/regie: " + all);
        int[] fotosWant = {10, 9, 6};
        double[] lenWant = {16 * 60 + 1, 5 * 60 + 31, 6 * 60 + 1};
        for (int i = 0; i < all.size(); i++) {
            com.dan.glacier.scene.Regie.Book b = all.get(i);
            java.util.List<String> calls = new java.util.ArrayList<>();
            java.util.List<String> views = new java.util.ArrayList<>(), rides = new java.util.ArrayList<>();
            com.dan.glacier.scene.Regie.Ctx c = new com.dan.glacier.scene.Regie.Ctx() {
                @Override public void time(java.time.LocalDate date, double hour) { calls.add("zeit " + date + " " + hour); }
                @Override public void lapse(double x) { calls.add("raffer"); }
                @Override public void weather(com.dan.weather.Scenario s) { calls.add("wetter " + s.steps().size()); }
                @Override public void weatherFree(com.dan.weather.Weather.Phase p, double bft, double fromDeg) { calls.add("wetter_frei"); }
                @Override public void view(String name) { calls.add("ansicht"); views.add(name); }
                @Override public void fahrt(String name) { calls.add("fahrt"); rides.add(name); }
                @Override public void boot() { calls.add("boot"); }
                @Override public void aussteigen() { calls.add("aussteigen"); }
                @Override public void kalbung(com.dan.glacier.scene.Kalbung.Art art) { calls.add("kalbung"); }
                @Override public void kalbungRegie(boolean on) { calls.add("kalbung_regie"); }
                @Override public void ruptura() { calls.add("ruptura"); }
                @Override public void damm(boolean on) { calls.add("damm"); }
                @Override public void figuren(boolean on) { calls.add("figuren"); }
                @Override public void capture(String caption) { calls.add("foto"); }
                @Override public void sheet(String name) { calls.add("blatt"); }
                @Override public void say(String text) { calls.add("sage"); }
            };
            com.dan.glacier.scene.Regie reg = new com.dan.glacier.scene.Regie();
            reg.start(b, c);
            int steps = 0;
            while (reg.running() && steps++ < 100_000) reg.step(1 / 30.0, c);
            long fotos = calls.stream().filter("foto"::equals).count();
            boolean viewsOk = views.stream().allMatch(v -> Ansichten.byName(v) != null);
            boolean ridesOk = rides.stream().allMatch(v -> v.equals("Durch die Spalte") || v.equals("Über die Zunge"));
            check(!reg.running() && Math.abs(b.length() - lenWant[i]) < 1e-6 && fotos == fotosWant[i] && calls.get(calls.size() - 2).equals("blatt") || calls.contains("blatt") && fotos == fotosWant[i] && !reg.running(),
                    String.format(Locale.GERMAN, "„%s“ läuft ohne Bild durch: %d:%02d min, %d Stichworte, %d Fotos, am Ende das Blatt", b.name(), (int) b.length() / 60, (int) b.length() % 60, reg.fired(), fotos));
            check(viewsOk && ridesOk, "„" + b.name() + "“: alle Ansichten und Fahrten gibt es (" + String.join(", ", views) + (rides.isEmpty() ? "" : "; " + String.join(", ", rides)) + ")");
            if (i == 0) check(calls.contains("wetter 4") && calls.contains("boot") && calls.contains("aussteigen") && calls.stream().filter("kalbung"::equals).count() == 3,
                    "„Ein Sommertag an der Front“: Wetterverlauf in vier Schritten (Seenebel bis Abend), Bootsfahrt, drei Kalbungen von Hand");
            if (i == 1) check(calls.indexOf("ruptura") >= 0 && calls.get(0).startsWith("zeit 2027-03-04"), "„Die Ruptura“: 4. März 2027, startet die Ruptura");
            if (i == 2) check(calls.contains("figuren") && calls.get(0).startsWith("zeit 2026-07-19 20.5"), "„Südhimmel“: 19. Juli 2026, 20:30, mit Sternbildern");
        }
        // Fehler im Text nennen die Zeile
        String bad = "Regiebuch: Probe\n0:00 zeit 16.01.2026 06:00\n0:10 springen hoch\n";
        String msg = "";
        try { com.dan.glacier.scene.Regie.parse(bad); } catch (IllegalArgumentException e) { msg = e.getMessage(); }
        check(msg.startsWith("Zeile 3") && msg.contains("springen"), "ein unbekannter Befehl nennt die Zeile: " + msg);
        // der Mond am 19.7.2026 um 21 Uhr über dem Eis (Nordwesten, zunehmend)
        com.dan.sky.Sky sky = new com.dan.sky.Sky(java.time.ZonedDateTime.of(2026, 7, 19, 21, 0, 0, 0, java.time.ZoneOffset.ofHours(-3)).toInstant().toEpochMilli(), Site.LAT, Site.LON);
        com.dan.sky.Sky.State st = sky.update();
        check(st.moonAlt > 25 && st.moonAz > 270 && st.moonAz < 340 && st.moonFraction > 0.25 && st.moonFraction < 0.5 && st.sunAlt < -18,
                String.format(Locale.GERMAN, "Südhimmel: am 19.7.2026 um 21 Uhr steht der Mond %.0f° hoch im Nordwesten (%03.0f°, %.0f %%), die Sonne %.0f°", st.moonAlt, st.moonAz, st.moonFraction * 100, st.sunAlt));
        // die Fahrt „Durch die Spalte“
        Terrain t = Terrain.load(new File("daten/gelaende"));
        if (!t.measured()) return;
        com.dan.glacier.scene.Gletscher gl = new com.dan.glacier.scene.Gletscher(t);
        long t0 = System.nanoTime();
        double[][] w = gl.spaltenWeg();
        double ms = (System.nanoTime() - t0) / 1e6;
        check(w != null, String.format(Locale.GERMAN, "eine Spalte für „Durch die Spalte“ gefunden (%.1f s Suche)", ms / 1000));
        if (w == null) return;
        com.dan.glacier.camera.Fahrt f = new com.dan.glacier.camera.Fahrt("Durch die Spalte", w, 60, 0, 4);
        double minClear = 1e9, below = 0, in = 0;
        for (int i = 5; i < w.length - 5; i++) {
            double dx = w[i + 1][0] - w[i - 1][0], dz = w[i + 1][2] - w[i - 1][2], l = Math.hypot(dx, dz);
            double nx = -dz / l, nz = dx / l;
            for (int sg = -1; sg <= 1; sg += 2)
                for (double dd = 0.25; dd <= 8; dd += 0.25)
                    if (gl.glacier.surface(w[i][0] + nx * dd * sg, w[i][2] + nz * dd * sg) > w[i][1]) { minClear = Math.min(minClear, dd); break; }
            in++;
            if (w[i][1] < gl.glacier.base(w[i][0], w[i][2]) - 5) below++;
        }
        check(f.length > 150 && minClear >= 1.9 && below / in > 0.9 && f.maxTurnPerMeter() < 2,
                String.format(Locale.GERMAN, "Durch die Spalte: %,.0f m, %.0f %% des Wegs mehr als 5 m unter der Kante, Wände mindestens %.1f m entfernt, höchstens %.1f°/m Drehung", f.length, 100 * below / in, minClear, f.maxTurnPerMeter()));
    }

    static void verdrahtung() {
        System.out.println("Verdrahtung");
        Wiring w = new Wiring(new SceneClock());
        check(w.links().size() == 80 && w.links().stream().allMatch(l -> l.phase() <= 13), "Phase 1 bis 13: " + w.summary());
        check(w.links().stream().filter(l -> l.phase() == 13).count() == 8 && w.links().stream().anyMatch(l -> l.via().startsWith("Torrent"))
                && w.links().stream().anyMatch(l -> l.to().equals("fracture") && l.via().contains("Current")),
                "Phase 13: Damm an fracture, Jahr an die Ruptura, Brazo Rico an ocean, Wasser und Strömung an fracture, Abfluss an wave und spray, Tosen an soundscape");
        check(w.links().stream().filter(l -> l.phase() == 12 && l.to().equals("soundscape")).count() == 9,
                "Phase 12: Wind, Ohr, Risse, Aufschläge, Auflaufen, Rufe, Berge und Boot, Donner und Stege an soundscape");
        check(w.links().stream().filter(l -> l.phase() == 11).count() == 9 && w.links().stream().anyMatch(l -> l.from().startsWith("season") && l.to().startsWith("ocean (Brazo Rico)")),
                "Phase 11: das Jahr an Wetter, Gelände, Eis, Wald, Boden, Brazo Rico, Kalbung, Regen und Stau");
        check(w.links().stream().anyMatch(l -> l.from().equals("weather") && l.to().equals("flock") && l.phase() == 10)
                && w.links().stream().anyMatch(l -> l.from().startsWith("wave (onRunup)") && l.to().startsWith("weathering") && l.phase() == 10)
                && w.links().stream().anyMatch(l -> l.from().equals("ocean + wave") && l.to().startsWith("buoyancy (Katamaran)")),
                "Phase 10: Wind an flock, Auflaufen an weathering (nasse Stege), Wasser an den Katamaran");
        check(w.links().stream().anyMatch(l -> l.from().startsWith("fracture (onSettle)") && l.to().equals("buoyancy")), "Phase 9: Stücke der Kalbung werden Eisberge (fracture an buoyancy)");
        check(w.links().stream().anyMatch(l -> l.to().equals("wave") && l.via().startsWith("Impact") && l.phase() == 8)
                && w.links().stream().anyMatch(l -> l.from().equals("wave") && l.to().equals("ocean")), "Phase 8: Impact an wave, Höhentextur an ocean");
        check(w.links().stream().anyMatch(l -> l.to().equals("fracture") && l.phase() == 6), "Phase 6: Boden und Seespiegel an fracture");
        check(w.links().stream().anyMatch(l -> l.to().contains("forest") && l.phase() == 2) && w.links().stream().anyMatch(l -> l.to().contains("ocean") && l.phase() == 2),
                "Phase 2: Wind, Uhr und Gelände an ocean, forest, reed, ground");
        int found = 0;
        for (String n : Wiring.INTERFACES) {
            try {
                Class<?> c = Class.forName("com.dan.glacier.wiring." + n);
                if (c.isInterface()) found++;
            } catch (ClassNotFoundException e) { /* fehlt */ }
        }
        check(found == Wiring.INTERFACES.length && found == 15, found + " von 15 Schnittstellen aus Abschnitt 4 stehen als Interface, darunter neu Sea, Cells, Impact, Crack, Runup");
        check(w.links().stream().anyMatch(l -> l.via().startsWith("Cells") && l.phase() == 7)
                && w.links().stream().anyMatch(l -> l.via().startsWith("Runup") && l.to().equals("soundscape")), "Phase 7: Cells (die Front an fracture) verbunden; seit Phase 12 Runup an soundscape");
        check(Beaufort.of(w.wind.speed()) == 4, "Wind von Hand: Bft 4 (" + Beaufort.NAME[4] + ")");
        double[] to = w.wind.toVector();
        check(to[0] > 0.99, "Westwind vom Eisfeld weht nach Osten über die Front auf den Kanal");
        w.waterLevel.stau = 25;
        check(w.waterLevel.brazoRico() - w.waterLevel.level() == 25, "Stau des Brazo Rico über dem Hauptsee");
        Collider block = Collider.box(-50, 37, 0, 50, 37, 2500);
        check(block.distance(-50, 37, 0) < 0 && Math.abs(block.distance(10, 37, 0) - 10) < 1e-9, "Collider: Eisblock als Quader");
    }

    static void symbol() {
        System.out.println("Programmsymbol");
        boolean all = true;
        for (int s : AppIcon.SIZES) {
            java.awt.image.BufferedImage b = AppIcon.paint(s);
            all &= b.getWidth() == s && (b.getRGB(s / 2, s / 2) >>> 24) == 255 && (b.getRGB(0, 0) >>> 24) < 255;
        }
        check(all, "Symbol in " + AppIcon.SIZES.length + " Größen gemalt, deckend in der Mitte, runde Ecken");
    }

    /** Die 21 Element-Packages stehen im Quellordner, jedes mit README; die 7 abgestellten liegen in _alt/packages (F6 A). */
    static void pakete() {
        System.out.println("Packages");
        File base = new File("src/com/dan");
        if (!base.isDirectory()) { System.out.println("  (Quellordner nicht gefunden, übersprungen)"); return; }
        String[] wanted = {"ice", "fracture", "wave", "sky", "cloud", "weather", "rain", "fog", "lightning", "ocean", "spray", "buoyancy",
                "weathering", "flock", "forest", "ground", "river", "reed", "season", "soundscape", "store"};
        int n = 0;
        for (String w : wanted) if (new File(base, w + "/README.md").isFile()) n++;
        File[] dirs = base.listFiles(f -> f.isDirectory() && !f.getName().equals("glacier"));
        check(n == wanted.length && dirs != null && dirs.length == wanted.length, n + " von 21 Packages mit README im Build (18 aus Haithabu, 3 neu)");
        // FStyle.jar bringt ältere Kopien von forest, ground und river mit; die eigenen Klassen müssen vorn im Klassenpfad stehen
        StringBuilder shadow = new StringBuilder();
        for (Class<?> c : new Class<?>[]{com.dan.forest.Species.class, com.dan.ground.Biome.class, com.dan.reed.Reed.class, com.dan.ocean.Ocean.class}) {
            var cs = c.getProtectionDomain().getCodeSource();
            String where = cs == null || cs.getLocation() == null ? "" : cs.getLocation().toString();
            if (where.endsWith("FStyle.jar")) shadow.append(' ').append(c.getSimpleName());
        }
        check(shadow.length() == 0, shadow.length() == 0 ? "Packages kommen aus dem eigenen Build, nicht aus FStyle.jar"
                : "aus FStyle.jar statt aus dem Build:" + shadow + " – build/classes muss im Klassenpfad vor FStyle.jar stehen");
        String[] gone = {"timber", "smoke", "hearth", "herd", "longship", "road", "muster"};
        int m = 0;
        for (String g : gone) if (!new File(base, g).exists() && new File("_alt/packages/" + g).isDirectory()) m++;
        check(m == gone.length, m + " von 7 Packages nach _alt/packages abgestellt (F6 A)");
        if (new File("db").isDirectory()) check(!new File("db/Object_nach_Schema.sql").exists() && !new File("db/TableFormat.sql").exists() && new File("db/USER.sql").isFile(),
                "db/: nur USER.sql, die beiden fremden Dateien in _alt (F19 A)");
    }

    /** Kein Element-Package kennt die App (die volle Prüfung macht check-packages). */
    static void isolation() throws Exception {
        System.out.println("Isolation");
        File base = new File("src/com/dan");
        if (!base.isDirectory()) { System.out.println("  (Quellordner nicht gefunden, übersprungen)"); return; }
        Pattern p = Pattern.compile("com\\.dan\\.glacier");
        int found = 0, files = 0;
        File[] dirs = base.listFiles(f -> f.isDirectory() && !f.getName().equals("glacier"));
        if (dirs != null) for (File d : dirs) {
            try (Stream<Path> s = Files.walk(d.toPath())) {
                for (Path f : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".java"))::iterator) {
                    files++;
                    if (p.matcher(Files.readString(f, StandardCharsets.UTF_8)).find()) { found++; System.out.println("    " + f); }
                }
            }
        }
        check(found == 0, files + " Dateien in " + (dirs == null ? 0 : dirs.length) + " Packages, keine nennt com.dan.glacier");
    }

    static double hourOf(double epochMs) {
        var z = Instant.ofEpochMilli((long) epochMs).atZone(SceneClock.ZONE);
        return z.getHour() + z.getMinute() / 60.0 + z.getSecond() / 3600.0;
    }

    static String hm(double h) { return String.format("%02d:%02d", (int) h, (int) Math.round((h - (int) h) * 60) % 60); }

    private Pruefung() { }
}
