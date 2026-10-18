package com.dan.glacier.data;

import com.dan.glacier.scene.Terrain;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die Tageskarte (F36 A, Abnahme Phase 15): eine Draufsicht als PNG mit der Front des Tages, den Fronten der Tage
 * davor und der Front des Geländes, den Kalbungen als Punkte nach Art und Größe, ihren Rissen und den Wegen der
 * Eisberge. Dieselbe Karte zeichnet QGIS aus {@code GLC_TAGESKARTE_V}. Gelesen wird über den {@link Speicher}; im
 * Hintergrund liegen See, Eis und Land aus dem Gelände.
 */
public final class Tageskarte {
    private Tageskarte() { }

    public static final int W = 1600, H = 1200;
    static final Color HINTER = new Color(11, 18, 25), SEE = new Color(26, 74, 82), EIS = new Color(196, 222, 232), LAND = new Color(58, 56, 50),
            TEXT = new Color(230, 236, 240), GRAU = new Color(143, 162, 174);
    static final Color TURM = new Color(210, 96, 47), SCHEIBE = new Color(224, 163, 63), FUSS = new Color(61, 177, 165), FRONT = new Color(58, 143, 199),
            FRONT_ALT = new Color(120, 140, 160), BERG = new Color(240, 246, 249);

    /** Was gezeichnet wurde, für die Meldung. */
    public record Ergebnis(File datei, int fronten, int kalbungen, int eisberge, double volumenM3) { }

    /** Zeichnet die Karte des Tages und legt sie nach dir (docs/status) als Tageskarte_yyyy-mm-dd.png. */
    public static Ergebnis zeichnen(Speicher sp, Terrain t, LocalDate tag, File dir) throws Exception {
        List<Daten.Front> alle = sp.fronten(null, tag.plusDays(1).atStartOfDay(), false);
        List<Daten.Front> heute = new ArrayList<>(), vorher = new ArrayList<>();
        Daten.Front gelaende = null;
        for (Daten.Front f : alle) {
            if (Daten.F_GELAENDE.equals(f.art())) gelaende = f;
            else if (Daten.F_MODELL.equals(f.art())) continue;
            else if (f.szenenzeit().toLocalDate().equals(tag)) heute.add(f);
            else if (f.szenenzeit().toLocalDate().isAfter(tag.minusDays(31))) vorher.add(f);
        }
        List<Daten.Kalbung> ks = sp.kalbungenAm(tag);
        List<Daten.Eisberg> bs = sp.eisbergeAm(tag);
        // Ausschnitt: um die Front, mindestens 4 × 3 km
        double x0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        List<Daten.Front> rahmen = new ArrayList<>(heute);
        if (gelaende != null) rahmen.add(gelaende);
        for (Daten.Front f : rahmen) for (double[][] p : f.linie().teile()) for (double[] q : p) { x0 = Math.min(x0, q[0]); x1 = Math.max(x1, q[0]); z0 = Math.min(z0, q[1]); z1 = Math.max(z1, q[1]); }
        for (Daten.Kalbung k : ks) { x0 = Math.min(x0, k.x()); x1 = Math.max(x1, k.x()); z0 = Math.min(z0, k.z()); z1 = Math.max(z1, k.z()); }
        if (x0 > x1) { x0 = -2000; x1 = 2000; z0 = -1500; z1 = 1500; }
        double cx = (x0 + x1) / 2, cz = (z0 + z1) / 2, half = Math.max(Math.max((x1 - x0) / 2 + 500, 2000), ((z1 - z0) / 2 + 500) * W / (double) H);
        double s = W / (2 * half);
        double ox = cx - half, oz = cz - H / (2 * s);

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(HINTER);
        g.fillRect(0, 0, W, H);
        // Gelände: See, Eis, Land, mit Schummerung
        if (t != null) {
            for (int py = 0; py < H; py += 2)
                for (int px = 0; px < W; px += 2) {
                    double x = ox + px / s, z = oz + py / s;
                    byte src = t.sourceAt(x, z);
                    Color c = src == Terrain.SEE ? SEE : src == Terrain.EIS ? EIS : LAND;
                    double sh = 1;
                    if (src != Terrain.SEE) {
                        double[] n = t.normal(x, z);
                        sh = Math.max(0.45, Math.min(1.2, 0.75 + 0.6 * (-n[0] * 0.6 - n[2] * 0.6 + n[1] * 0.5)));
                    }
                    g.setColor(new Color(c8(c.getRed() * sh), c8(c.getGreen() * sh), c8(c.getBlue() * sh)));
                    g.fillRect(px, py, 2, 2);
                }
        }
        // Fronten
        if (gelaende != null) linie(g, gelaende.linie(), ox, oz, s, new Color(255, 255, 255, 120), 1.5f, true);
        for (Daten.Front f : vorher) linie(g, f.linie(), ox, oz, s, new Color(FRONT_ALT.getRed(), FRONT_ALT.getGreen(), FRONT_ALT.getBlue(), 150), 1.5f, false);
        for (Daten.Front f : heute) linie(g, f.linie(), ox, oz, s, FRONT, 3f, false);
        // Wege der Eisberge
        double vol = 0;
        for (Daten.Eisberg b : bs) {
            if (b.weg().size() >= 2) linie(g, Daten.Geom.linie(b.weg().toArray(new double[0][])), ox, oz, s, new Color(240, 246, 249, 140), 1.2f, false);
            double[] e = b.weg().get(b.weg().size() - 1);
            int r = (int) Math.max(2, Math.min(9, Math.cbrt(b.volumenM3()) / 3));
            g.setColor(BERG);
            g.fillOval(px(e[0], ox, s) - r, py(e[1], oz, s) - r, 2 * r, 2 * r);
        }
        // Kalbungen und Risse
        for (Daten.Kalbung k : ks) {
            Color c = switch (k.art()) { case "TURM" -> TURM; case "SCHEIBE" -> SCHEIBE; default -> FUSS; };
            if (k.riss() != null) linie(g, k.riss(), ox, oz, s, c, 2f, false);
            int r = (int) Math.max(4, Math.min(22, Math.cbrt(k.volumenM3()) / 1.6));
            int x = px(k.x(), ox, s), y = py(k.z(), oz, s);
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 170));
            g.fillOval(x - r, y - r, 2 * r, 2 * r);
            g.setColor(TEXT);
            g.setStroke(new BasicStroke(1.2f));
            g.drawOval(x - r, y - r, 2 * r, 2 * r);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.drawString(DateTimeFormatter.ofPattern("HH:mm").format(k.szenenzeit()), x + r + 3, y + 4);
            vol += k.volumenM3();
        }
        // der Steg zur Orientierung
        if (t != null && t.measured()) {
            int sx = px(t.steg[0], ox, s), sy = py(t.steg[1], oz, s);
            g.setColor(new Color(240, 210, 120));
            g.fillRect(sx - 4, sy - 4, 8, 8);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            g.drawString("Steg", sx + 7, sy + 4);
        }
        // Kopf, Legende, Maßstab
        g.setColor(new Color(11, 18, 25, 220));
        g.fillRect(0, 0, W, 64);
        g.fillRect(0, H - 48, W, 48);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.drawString("Glacier · Tageskarte " + DateTimeFormatter.ofPattern("dd.MM.yyyy").format(tag) + " · " + sp.wo(), 16, 30);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.setColor(GRAU);
        g.drawString(String.format(Locale.GERMAN, "%d Kalbungen (%,.0f m³) · %d Eisberge · %d Fronten des Tages, %d der 30 Tage davor · Front des Geländes (GLO-30) gestrichelt",
                ks.size(), vol, bs.size(), heute.size(), vorher.size()), 16, 52);
        int ly = H - 20, lx = 16;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        for (Object[] e : new Object[][]{{TURM, "Turm"}, {SCHEIBE, "Scheibe"}, {FUSS, "Fuß"}, {BERG, "Eisberg (Weg)"}, {FRONT, "Front des Tages"}, {FRONT_ALT, "Fronten davor"}}) {
            g.setColor((Color) e[0]);
            g.fillOval(lx, ly - 11, 12, 12);
            g.setColor(TEXT);
            g.drawString((String) e[1], lx + 18, ly);
            lx += 34 + g.getFontMetrics().stringWidth((String) e[1]);
        }
        double bar = Math.pow(10, Math.floor(Math.log10(half / 2)));
        int bw = (int) (bar * s);
        g.setColor(TEXT);
        g.fillRect(W - 40 - bw, H - 16, bw, 4);
        g.drawString(String.format(Locale.GERMAN, "%,.0f m · Norden oben", bar), W - 40 - bw, H - 24);
        g.dispose();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Ordner " + dir + " lässt sich nicht anlegen");
        File f = new File(dir, "Tageskarte_" + tag + ".png");
        ImageIO.write(img, "png", f);
        return new Ergebnis(f, heute.size(), ks.size(), bs.size(), vol);
    }

    private static int c8(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }
    private static int px(double x, double ox, double s) { return (int) Math.round((x - ox) * s); }
    private static int py(double z, double oz, double s) { return (int) Math.round((z - oz) * s); }

    private static void linie(Graphics2D g, Daten.Geom geom, double ox, double oz, double s, Color c, float w, boolean gestrichelt) {
        if (geom == null) return;
        g.setColor(c);
        g.setStroke(gestrichelt ? new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[]{8, 6}, 0) : new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (double[][] p : geom.teile()) {
            if (p.length < 2) continue;
            Path2D.Double path = new Path2D.Double();
            path.moveTo((p[0][0] - ox) * s, (p[0][1] - oz) * s);
            for (int i = 1; i < p.length; i++) path.lineTo((p[i][0] - ox) * s, (p[i][1] - oz) * s);
            g.draw(path);
        }
    }
}
