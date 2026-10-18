package com.dan.glacier.ui;

import com.dan.glacier.data.Chronik;
import com.dan.glacier.data.Daten;
import com.dan.glacier.data.Datenseite;
import com.dan.glacier.data.Zustand;
import com.dan.glacier.gl.Quality;
import com.dan.glacier.gl.Renderer;
import com.dan.store.Felder;
import com.dan.store.Protokoll;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.List;
import java.util.Map;

/**
 * „Weiter wie zuletzt“ (F31 A): einen gesicherten Zustand herstellen, sobald Gletscher, Kalbung und Welle stehen; dann
 * die offenen Eisberge der Sitzung aus der Chronik (Form neu aus dem Seed, Lage und Volumen wie zuletzt). Was gerade
 * fiel, kommt nicht zurück.
 */
public final class Weiter {
    private Weiter() { }

    /** Wartet höchstens 60 s auf die Szene, dann stellt es her; danach läuft {@code danach} (darf null sein). */
    public static void anwenden(Renderer r, Daten.Sitzung s, Chronik chronik, Runnable danach) {
        if (s == null || s.zustand() == null) return;
        long bis = System.currentTimeMillis() + 60_000;
        Timer t = new Timer(250, null);
        t.addActionListener(e -> {
            boolean bereit = r.gletscher() != null && r.kalbung != null && r.waveReady;
            if (!bereit && System.currentTimeMillis() < bis) return;
            t.stop();
            jetzt(r, s, chronik);
            if (danach != null) danach.run();
        });
        t.start();
    }

    /** Phase 16: ein Regiebuch im Kino, sobald die Szene steht (höchstens 60 s warten). */
    public static void kino(Renderer r, com.dan.glacier.scene.Regie.Book b) {
        long bis = System.currentTimeMillis() + 60_000;
        Timer t = new Timer(250, null);
        t.addActionListener(e -> {
            boolean bereit = r.gletscher() != null && r.kalbung != null && r.waveReady;
            if (!bereit && System.currentTimeMillis() < bis) return;
            t.stop();
            r.kinoStart(b);
        });
        t.start();
    }

    /** Sofort herstellen (die Szene steht schon). */
    public static void jetzt(Renderer r, Daten.Sitzung s, Chronik chronik) {
        Map<String, String> z = Felder.ausText(s.zustand());
        Quality q = Zustand.qualitaet(z);
        if (q != null && q != r.quality()) r.setQuality(q);
        if (z.containsKey("app.ton")) r.setTon(Boolean.parseBoolean(z.get("app.ton")));
        List<String> f = Zustand.anwenden(r, z);
        Protokoll.info("Sitzung", "Weiter wie zuletzt (Sitzung " + s.id() + "): " + z.size() + " Werte" + (f.isEmpty() ? "" : ", nicht hergestellt: " + String.join(", ", f)));
        r.hudMeldung("Weiter wie zuletzt · " + z.getOrDefault("uhr.datum", "") + (z.containsKey("regiebuch.name") ? " · " + z.get("regiebuch.name") : ""));
        // die offenen Eisberge aus der Chronik, im Hintergrund gelesen
        Thread th = new Thread(() -> {
            try {
                List<Daten.Eisberg> l = Datenseite.speicher().offeneEisberge(s.id());
                SwingUtilities.invokeLater(() -> {
                    double level = r.camera.waterLevel;
                    int n = 0;
                    r.bergs.clear();
                    for (Daten.Eisberg e : l) {
                        com.dan.buoyancy.Berg b = com.dan.glacier.scene.Eisberge.wieder(e.volumenM3(), e.x(), e.z(), level, e.gierGrad(), e.formSeed(),
                                "aus der Sitzung " + s.id() + (e.kalbungId() != null ? ", Kalbung " + e.kalbungId() : ""));
                        if (chronik != null) chronik.fortsetzen(b, e, s.id());
                        if (r.bergs.add(b) != null) n++;
                    }
                    Protokoll.info("Sitzung", "Weiter wie zuletzt: " + n + " von " + l.size() + " offenen Eisbergen wieder im See");
                    r.hudMeldung("Weiter wie zuletzt · " + z.getOrDefault("uhr.datum", "") + " · " + n + " Eisberge wieder im See");
                });
            } catch (Exception e) {
                Protokoll.warnung("Sitzung", "Eisberge der Sitzung " + s.id() + " nicht gelesen: " + e.getMessage());
            }
        }, "Glacier-Weiter");
        th.setDaemon(true);
        th.start();
    }
}
