package com.dan.glacier.ui;

import com.dan.fbutton.FButton;
import com.dan.fcombobox.FComboBox;
import com.dan.glacier.data.Chronik;
import com.dan.glacier.data.Daten;
import com.dan.glacier.data.Datenseite;
import com.dan.glacier.gl.Renderer;
import com.dan.glacier.scene.Gletscher;
import com.dan.glacier.scene.Kalbung;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.Locale;

/**
 * Die Säulen- und die Eisbergkarte (F30 A, statt der Hauskarte von Haithabu): ein Klick in die Szene zeigt, was unter
 * dem Zeiger liegt. Die Karten zeigen und lösen aus; sie ändern nichts dauerhaft.
 * <ul>
 * <li><b>Säulenkarte</b> (Klick auf die Front oder das Eis bis 150 m dahinter): Höhe über dem See, Tiefe bis zum Grund,
 * Abstand zur Front, Vorstoß, der Abschnitt der Kalbung dort (Zustand, Kerbe, größte Auslastung der Flächen und die
 * Reserve bis zum Bruch) und die letzten Kalbungen im Umkreis von 100 m aus der Chronik. „Schwächen“ löst dort einen
 * Abbruch aus; er kommt mit dem Auslöser KARTE in die Chronik.</li>
 * <li><b>Eisbergkarte</b> (Klick auf einen Eisberg): Volumen, Anteil vom Anfang, Tiefgang, Freibord, Kenterungen, Alter
 * und Weg; sein Ursprung (die Kalbung) mit Zeit und Ort; „Zum Ursprung“ dreht die Kamera dorthin.</li>
 * </ul>
 */
final class Karte {
    private Karte() { }

    private static JDialog offen;

    /** Nach einem Klick: Karte zeigen, wenn etwas Passendes getroffen ist. Gibt eine Zeile für das HUD zurück. */
    static String klick(Component owner, Renderer r, Chronik chronik, int x, int y, int w, int h) {
        double[] p = r.pick(x, y, w, h);
        if (p == null) return null;
        int art = (int) p[0];
        if (art == 1) {
            for (com.dan.buoyancy.Berg b : r.bergs.bergs) if (b.id == (int) p[4]) { eisberg(owner, r, chronik, b); return "Eisbergkarte: Berg " + b.id; }
            return null;
        }
        Gletscher g = r.gletscher();
        if (g == null) return null;
        double d = g.glacier.distFront(p[1], p[3]);
        if (art == 2 && d < 150 || art == 3 && Math.abs(d) < 40) { saeule(owner, r, chronik, p[1], p[3]); return String.format(Locale.GERMAN, "Säulenkarte bei %.0f / %.0f", p[1], p[3]); }
        return null;
    }

    private static JDialog fenster(Component owner, String titel) {
        if (offen != null) offen.dispose();
        Window w = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        JDialog d = new JDialog(w, titel, java.awt.Dialog.ModalityType.MODELESS);
        AppIcon.install(d);
        offen = d;
        return d;
    }

    private static JPanel rahmen() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(Theme.bg());
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        return p;
    }

    private static String fmt(String f, Object... a) { return String.format(Locale.GERMAN, f, a); }

    static void saeule(Component owner, Renderer r, Chronik chronik, double x, double z) {
        Gletscher g = r.gletscher();
        double level = r.camera.waterLevel;
        double[] nf = g.glacier.nearestFront(x, z, 300);
        double fx = nf == null ? x : nf[0], fz = nf == null ? z : nf[1];
        double top = g.glacier.surface(fx - (nf == null ? 0 : nf[2]) * 3, fz - (nf == null ? 0 : nf[3]) * 3), bed = g.glacier.bed(fx, fz);
        StringBuilder b = new StringBuilder("<html><body style='width:380px'>");
        b.append(fmt("<b>Säule der Front</b> bei x %.0f, z %.0f (%.0f m hinter der Front)<br>", x, z, Math.max(0, g.glacier.distFront(x, z))));
        b.append(fmt("Oberkante %.0f m über dem See, Grund %.0f m unter dem See, Eis %.0f m<br>", top - level, level - bed, top - bed));
        b.append(fmt("Vorstoß %+.1f m (%s) · Fließen an der Front bis %.1f m/Tag<br>", g.glacier.advance(), Double.isNaN(r.vorstossHand) ? "Jahresgang" : "von Hand",
                g.glacier.frontSpeed()));
        Kalbung k = r.kalbung;
        Kalbung.Abschnitt best = null;
        double bd = 120;
        if (k != null) for (Kalbung.Abschnitt a : k.sections()) {
            double[] c = a.block.world(0, a.block.depth / 2);
            double dd = Math.hypot(c[0] - x, c[1] - z);
            if (dd < bd) { bd = dd; best = a; }
        }
        if (best != null) {
            double u = best.solid.maxUtilization();
            b.append(fmt("<br><b>Abschnitt %d (%s)</b>, %s: %d Zellen, Kerbe %.1f m (höchstens %.1f)<br>Größte Auslastung der Flächen %.0f %% · Reserve bis zum Bruch %.0f %%%s<br>",
                    best.nr, best.art.label, best.zustand.name().toLowerCase(Locale.ROOT), best.solid.cells(), best.notch, best.notchMax, u * 100, Math.max(0, 1 - u) * 100,
                    best.pieces > 0 ? fmt(" · %d Stücke, %,.0f m³ frei", best.pieces, best.volume) : ""));
        } else b.append("<br>Hier ist die Front noch nicht in Zellen zerlegt; „Schwächen“ legt einen Abschnitt an und lässt ihn brechen.<br>");
        b.append("<br><b>Kalbungen im Umkreis von 100 m</b> (Chronik, ").append(Datenseite.wo()).append(")<br>");
        JLabel liste = ControlPanel.small("lese …");
        JDialog d = fenster(owner, "Säulenkarte · Glacier");
        JPanel p = rahmen();
        JPanel mitte = new JPanel();
        mitte.setOpaque(false);
        mitte.setLayout(new BoxLayout(mitte, BoxLayout.Y_AXIS));
        JLabel kopf = ControlPanel.label(b.append("</body></html>").toString());
        kopf.setAlignmentX(Component.LEFT_ALIGNMENT);
        liste.setAlignmentX(Component.LEFT_ALIGNMENT);
        mitte.add(kopf);
        mitte.add(liste);
        p.add(mitte, BorderLayout.CENTER);
        JPanel unten = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        unten.setOpaque(false);
        FComboBox art = new FComboBox(Kalbung.Art.values());
        art.setSelectedItem(Kalbung.Art.TURM);
        FButton schw = new FButton("Schwächen"), zu = new FButton("Schließen");
        schw.setToolTipText("Hier einen Abbruch auslösen (schnelle Kerbe); die Chronik schreibt ihn mit dem Auslöser KARTE");
        schw.addActionListener(e -> {
            Kalbung kk = r.kalbung;
            if (kk == null) return;
            if (chronik != null) chronik.karte(fx, fz);
            kk.trigger((Kalbung.Art) art.getSelectedItem(), fx, fz);
            r.hudMeldung("Säulenkarte: " + ((Kalbung.Art) art.getSelectedItem()).label + " ausgelöst");
            d.dispose();
        });
        zu.addActionListener(e -> d.dispose());
        unten.add(art);
        unten.add(schw);
        unten.add(zu);
        p.add(unten, BorderLayout.SOUTH);
        d.setContentPane(p);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        hinten(() -> Datenseite.speicher().kalbungenUm(fx, fz, 100, 6), l -> {
            StringBuilder s = new StringBuilder("<html><body style='width:380px'>");
            if (l.isEmpty()) s.append("noch keine");
            for (Daten.KalbungKurz kk : l) s.append(Daten.text(kk.szenenzeit())).append(" · ").append(kk).append(" · ").append(kk.ausloeser().toLowerCase(Locale.ROOT)).append("<br>");
            liste.setText(s.append("</body></html>").toString());
            d.pack();
        }, liste);
    }

    static void eisberg(Component owner, Renderer r, Chronik chronik, com.dan.buoyancy.Berg berg) {
        double level = r.camera.waterLevel;
        double[] df = berg.draftAndFreeboard(level);
        Daten.Eisberg sp = chronik == null ? null : chronik.spur(berg.id);
        StringBuilder b = new StringBuilder("<html><body style='width:380px'>");
        b.append(fmt("<b>Eisberg %d</b> · %s<br>", berg.id, berg.origin == null ? "" : berg.origin));
        b.append(fmt("%,.0f m³ (%.0f %% vom Anfang, %,.0f m³) · Tiefgang %.1f m · Freibord %.1f m<br>", berg.volume(), 100 * berg.volume() / Math.max(1, berg.initialVolume()),
                berg.initialVolume(), df[0], df[1]));
        b.append(fmt("%d× gekentert%s · %.1f h im Wasser (Physik)<br>", berg.capsizes(), berg.grounded() ? " · auf Grund gelaufen" : "", berg.age() / 3600));
        double ox = Double.NaN, oz = Double.NaN;
        if (sp != null) {
            b.append(fmt("Geboren %s bei x %.0f, z %.0f · Weg %,.0f m in %d Punkten<br>", Daten.text(sp.geburt()), sp.geburtX(), sp.geburtZ(), sp.wegM(), sp.weg().size()));
            b.append(sp.kalbungId() != null ? "Ursprung: Kalbung " + sp.kalbungId() + " der Chronik" : sp.rupturaLaufId() != null ? "Ursprung: die Ruptura (Lauf " + sp.rupturaLaufId() + ")"
                    : "Ursprung: nicht aus einer Kalbung der Chronik").append("<br>");
            ox = sp.geburtX();
            oz = sp.geburtZ();
        } else b.append("Nicht in der Chronik (unter 200 m³ oder vor dem Start der Chronik).<br>");
        JDialog d = fenster(owner, "Eisbergkarte · Glacier");
        JPanel p = rahmen();
        p.add(ControlPanel.label(b.append("</body></html>").toString()), BorderLayout.CENTER);
        JPanel unten = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        unten.setOpaque(false);
        FButton hin = new FButton("Zum Ursprung"), hier = new FButton("Zum Berg"), zu = new FButton("Schließen");
        double fx = ox, fz = oz;
        hin.setEnabled(!Double.isNaN(ox));
        hin.addActionListener(e -> { r.camera.stopFahrt(); r.camera.lookAt(fx, level + 30, fz, r.camera.heading(), 18, 520); r.camera.mode = com.dan.glacier.camera.Camera.Mode.KREISEN; });
        hier.addActionListener(e -> { double[] cc = berg.cgWorld(); r.camera.stopFahrt(); r.camera.mode = com.dan.glacier.camera.Camera.Mode.KREISEN; r.camera.lookAt(cc[0], level + 10, cc[2], r.camera.heading(), 14, Math.max(80, berg.radius() * 6)); });
        zu.addActionListener(e -> d.dispose());
        unten.add(hier);
        unten.add(hin);
        unten.add(zu);
        p.add(unten, BorderLayout.SOUTH);
        d.setContentPane(p);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    interface Lesen<T> { T tun() throws Exception; }

    /** Im Hintergrund lesen, im Swing-Faden zeigen; Fehler in das Label. */
    static <T> void hinten(Lesen<T> l, java.util.function.Consumer<T> dann, JLabel fehler) {
        Thread t = new Thread(() -> {
            try {
                T v = l.tun();
                SwingUtilities.invokeLater(() -> dann.accept(v));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> fehler.setText("nicht lesbar: " + e.getMessage()));
            }
        }, "Glacier-Karte");
        t.setDaemon(true);
        t.start();
    }
}
