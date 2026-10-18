package com.dan.glacier.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Das Programmsymbol (F22 A): die blaue Eisfront über türkisem Wasser, ein Riss, ein fallender Turm, Goldkante wie
 * heidelberg.ico. Phase 1 malt es; ab Phase 16 liegen die PNGs im Jar unter {@code com/dan/glacier/ui/icon/glacier_NN.png}
 * und als {@code icon/glacier.ico} für die Verknüpfung. Fehlt eine Größe, malt die App sie selbst.
 */
public final class AppIcon {
    public static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    private AppIcon() { }

    public static List<Image> images() {
        List<Image> l = new ArrayList<>();
        for (int s : SIZES) l.add(load(s));
        return l;
    }

    public static BufferedImage load(int size) {
        try (InputStream in = AppIcon.class.getResourceAsStream("icon/glacier_" + size + ".png")) {
            if (in != null) {
                BufferedImage b = javax.imageio.ImageIO.read(in);
                if (b != null) return b;
            }
        } catch (java.io.IOException e) { /* gemaltes Symbol */ }
        return paint(size);
    }

    /** Das Symbol, in jeder Größe neu gemalt. */
    public static BufferedImage paint(int s) {
        BufferedImage im = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double r = s * 0.18, inset = Math.max(1, s * 0.035);
        // Goldkante
        g.setPaint(new GradientPaint(0, 0, new Color(240, 210, 120), s, s, new Color(150, 105, 30)));
        g.fill(new RoundRectangle2D.Double(0, 0, s, s, r * 2, r * 2));
        RoundRectangle2D inner = new RoundRectangle2D.Double(inset, inset, s - 2 * inset, s - 2 * inset, r * 1.7, r * 1.7);
        // Himmel über Patagonien
        g.setPaint(new GradientPaint(0, 0, new Color(28, 62, 104), 0, (float) (s * 0.5), new Color(150, 196, 222)));
        g.fill(inner);
        g.setClip(inner);
        double water = s * 0.66;
        // Berge dahinter
        Path2D berg = new Path2D.Double();
        berg.moveTo(0, s * 0.40);
        berg.lineTo(s * 0.18, s * 0.24);
        berg.lineTo(s * 0.32, s * 0.33);
        berg.lineTo(s * 0.50, s * 0.18);
        berg.lineTo(s * 0.70, s * 0.32);
        berg.lineTo(s * 0.86, s * 0.22);
        berg.lineTo(s, s * 0.34);
        berg.lineTo(s, water);
        berg.lineTo(0, water);
        berg.closePath();
        g.setColor(new Color(70, 88, 104));
        g.fill(berg);
        // Die Front: eine Wand mit gezackter Oberkante, weiß oben, blau in der Tiefe
        Path2D front = new Path2D.Double();
        double top = s * 0.36;
        front.moveTo(0, water);
        front.lineTo(0, top + s * 0.03);
        double[] zack = {0.03, -0.02, 0.04, 0.0, 0.05, -0.01, 0.03, 0.06};
        for (int i = 0; i < zack.length; i++) front.lineTo(s * (0.08 + 0.08 * i), top + s * zack[i]);
        front.lineTo(s * 0.70, top + s * 0.02);
        front.lineTo(s * 0.70, water);
        front.closePath();
        g.setPaint(new GradientPaint(0, (float) top, new Color(236, 245, 250), 0, (float) water, new Color(58, 143, 199)));
        g.fill(front);
        // senkrechte Rinnen in Spaltenblau
        if (s >= 24) {
            g.setStroke(new BasicStroke((float) Math.max(1, s * 0.02)));
            g.setColor(new Color(30, 95, 147, 150));
            for (int i = 1; i < 6; i++) {
                double x = s * (0.11 * i);
                g.draw(new java.awt.geom.Line2D.Double(x, top + s * 0.08, x + s * 0.01, water - s * 0.02));
            }
        }
        // der Riss: ein Zickzack hinter dem fallenden Turm
        g.setStroke(new BasicStroke((float) Math.max(1, s * 0.03), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(16, 48, 84));
        Path2D riss = new Path2D.Double();
        riss.moveTo(s * 0.70, top + s * 0.02);
        riss.lineTo(s * 0.66, top + s * 0.10);
        riss.lineTo(s * 0.71, top + s * 0.17);
        riss.lineTo(s * 0.67, top + s * 0.25);
        g.draw(riss);
        // der fallende Turm, nach vorn gekippt
        java.awt.geom.AffineTransform at = g.getTransform();
        g.rotate(Math.toRadians(24), s * 0.74, water);
        g.setPaint(new GradientPaint(0, (float) (top - s * 0.02), new Color(230, 243, 250), 0, (float) water, new Color(80, 160, 210)));
        g.fill(new Rectangle2D.Double(s * 0.71, top - s * 0.01, s * 0.12, water - top));
        g.setTransform(at);
        // türkises Wasser mit Spritzern
        g.setPaint(new GradientPaint(0, (float) water, new Color(61, 177, 165), 0, s, new Color(18, 70, 78)));
        g.fill(new Rectangle2D.Double(0, water, s, s - water));
        if (s >= 32) {
            g.setColor(new Color(240, 250, 252, 210));
            for (int i = 0; i < 5; i++) {
                double x = s * (0.80 + 0.04 * (i - 2)), y = water - s * (0.03 + 0.03 * (i % 3));
                g.fill(new java.awt.geom.Ellipse2D.Double(x, y, s * 0.035, s * 0.035));
            }
            g.setColor(new Color(220, 245, 248, 120));
            g.setStroke(new BasicStroke((float) Math.max(1, s * 0.015)));
            g.draw(new java.awt.geom.Arc2D.Double(s * 0.58, water - s * 0.02, s * 0.44, s * 0.12, 180, 180, java.awt.geom.Arc2D.OPEN));
        }
        g.dispose();
        return im;
    }

    /** Setzt das Symbol am Fenster und, wo unterstützt, in der Taskleiste. */
    public static void install(java.awt.Window w) {
        List<Image> l = images();
        w.setIconImages(l);
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar tb = java.awt.Taskbar.getTaskbar();
                if (tb.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) tb.setIconImage(l.get(l.size() - 1));
            }
        } catch (Exception | Error ignored) {
            // Windows nimmt ohnehin die Fenstersymbole
        }
    }
}
