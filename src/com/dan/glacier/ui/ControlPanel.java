package com.dan.glacier.ui;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcollapsiblepanel.FCollapsiblePanel;
import com.dan.fcombobox.FComboBox;
import com.dan.fslider.FSlider;
import com.dan.glacier.camera.Ansichten;
import com.dan.glacier.camera.Camera;
import com.dan.glacier.gl.ComputeProbe;
import com.dan.glacier.gl.Quality;
import com.dan.glacier.gl.Renderer;
import com.dan.glacier.gl.SceneView;
import com.dan.glacier.scene.SceneClock;
import com.dan.glacier.scene.Sun;
import com.dan.glacier.wiring.Beaufort;
import com.dan.glacier.wiring.Wiring;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * Das Bedienfeld rechts als Klappblöcke (aus Haithabu, entschlackt). Phase 1: ZEIT mit Datum, Uhrzeit, Zeitraffer und
 * Sonne; WETTER mit dem Wind von Hand (seit Phase 3 die Wetterregie mit Lagen, Handreglern, Lentikularis und Blitz); SEE mit Seespiegel und Stau des Brazo Rico; KAMERA mit Ansichten 1 bis 7; BILD
 * mit Qualität, Auflösung, HUD, Marken, Foto; TECHNIK mit Leistung, Physik, Packages und Verdrahtung. Seit Phase 15 alle
 * Blöcke aus Abschnitt 7, dazu EIS, CHRONIK (Zähler, letzte Kalbungen, Front merken, Tageskarte) und DATEN (Verbinden,
 * Einrichten, Weiter wie zuletzt, Übernahme, Presets, gemerkte Ansichten, Protokoll); ein Klick in die Szene zeigt die
 * Säulen- oder die Eisbergkarte. Offen bleibt, was zuletzt offen
 * war (je Rechner gemerkt). Jeder Regler schreibt direkt in die öffentlichen Felder von Uhr, Kamera, Verdrahtung und
 * Renderer; es gibt keinen zweiten Zustand.
 */
public final class ControlPanel extends JPanel implements javax.swing.Scrollable {
    private final SceneView view;
    private final Renderer r;
    private final SceneClock clock;
    private final Wiring wiring;
    private final JLabel dateLbl = label(""), timeLbl = label(""), sunLbl = small(""), windLbl = label(""), dirLbl = label(""),
            levelLbl = label(""), stauLbl = label(""), camLbl = small(""), perfLbl = small(""), computeLbl = small(""), glLbl = small(""),
            physLbl = small(""), kalbLbl = small(""), waveLbl = small(""), bergLbl = small(""), lebenLbl = small(""), jahrLbl = small(""), klangLbl = small(""), kalbLog = small(""), rupLbl = small(""), rupLog = small(""), buchLbl = small(""), buchInfo = small(""), linksLbl = small(""), hudLbl = small(""), scaleLbl = label(""), qualLbl = small(""), pkgLbl = small(""),
            regieLbl = small(""), skyLbl = small(""), coverLbl = label(""), rainLbl = label(""), fogLbl = label(""), visLbl = label(""), lensLbl = label("");
    private final FSlider time = new FSlider(0, 24 * 60 - 1, 15 * 60), wind = new FSlider(0, 12, 4), windDir = new FSlider(0, 359, 270),
            scaleSl = new FSlider(50, 100, 85), level = new FSlider(-200, 200, 0), stau = new FSlider(0, 30, 0),
            cover = new FSlider(0, 80, 20), rainSl = new FSlider(0, 200, 0), fogSl = new FSlider(0, 100, 0), visSl = new FSlider(1, 150, 70),
            lensSl = new FSlider(0, 100, 0);
    private final FCheckBox free = new FCheckBox("Frei fliegen (F)"), orbit = new FCheckBox("Von selbst kreisen (R)"),
            marks = new FCheckBox("Marken (Raster 1 km)"), autoBox = new FCheckBox("Auflösung selbst regeln");
    private FComboBox lapseBox, qualBox, hudBox, regieBox, thunderBox;
    /** Phase 3: die Wahl der Regie: von Hand, freie Regie ab einer Lage. */
    static final Object[] REGIE = {"von Hand", "frei ab „klar“", "frei ab „Föhnsturm“", "frei ab „aufziehend“", "frei ab „Front“", "frei ab „Sturm“",
            "frei ab „Seenebel“"};
    static final com.dan.weather.Weather.Phase[] REGIE_PHASE = {null, com.dan.weather.Weather.Phase.KLAR, com.dan.weather.Weather.Phase.FOEHN,
            com.dan.weather.Weather.Phase.AUFZIEHEND, com.dan.weather.Weather.Phase.FRONT, com.dan.weather.Weather.Phase.STURM, com.dan.weather.Weather.Phase.NEBEL};
    /** Phase 8: der Pegel vor dem Steg der letzten 90 s. */
    private final javax.swing.JComponent pegelKurve = new javax.swing.JComponent() {
        {
            setPreferredSize(new java.awt.Dimension(225, 80));
            setMaximumSize(new java.awt.Dimension(260, 80));
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override protected void paintComponent(java.awt.Graphics g0) {
            java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.setColor(new java.awt.Color(10, 18, 26));
            g.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
            com.dan.wave.Wave.Gauge p = r.pegel;
            g.setFont(getFont().deriveFont(10f));
            if (p == null) { g.setColor(new java.awt.Color(120, 140, 155)); g.drawString("Pegel: wartet auf die Welle", 8, h / 2); g.dispose(); return; }
            float[][] s = p.series(90);
            double amp = 0.1;
            for (float v : s[1]) if (!Float.isNaN(v)) amp = Math.max(amp, Math.abs(v));
            amp = Math.ceil(amp * 10) / 10;
            int mid = h / 2;
            g.setColor(new java.awt.Color(40, 60, 75));
            g.drawLine(4, mid, w - 4, mid);
            g.setColor(new java.awt.Color(90, 200, 220));
            int n = s[0].length;
            if (n > 1) {
                double t1 = s[0][n - 1], t0 = t1 - 90;
                java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
                boolean pen = false;
                for (int i = 0; i < n; i++) {
                    if (Float.isNaN(s[1][i])) { pen = false; continue; }
                    double x = 4 + (w - 8) * (s[0][i] - t0) / 90, y = mid - (h / 2.0 - 6) * s[1][i] / amp;
                    if (pen) path.lineTo(x, y); else path.moveTo(x, y);
                    pen = true;
                }
                g.draw(path);
            }
            g.setColor(new java.awt.Color(160, 180, 190));
            g.drawString(String.format(Locale.GERMAN, "±%.1f m · höchste %.2f m", amp, p.max()), 8, 12);
            g.drawString("−90 s", 6, h - 4);
            g.dispose();
        }
    };
    private boolean fromScene;
    private int ticks;
    private JPanel target = this;
    private static final Preferences PREFS = prefs();
    private final java.util.List<Object[]> bloecke = new java.util.ArrayList<>();
    private final java.util.List<FCollapsiblePanel> zu = new java.util.ArrayList<>();
    private static final double[] LAPSE = {0, 1, 10, 60, 240, 600, 3600};

    /** Die Element-Packages im Build (Werkbuch, Abschnitt 4) mit Stand. */
    public static final String[] PAKETE = {"ice (neu, Material, Gletscher, Eisdamm)", "fracture (neu, Engine, Strömung)", "wave (neu, Löser, Phase 8)", "sky (verdrahtet, Südhimmel)",
            "cloud (verdrahtet, Lentikularis)", "weather (verdrahtet, Föhnsturm)", "rain (verdrahtet)", "fog (verdrahtet)", "lightning (verdrahtet)",
            "ocean (verdrahtet, Lago)", "spray (verdrahtet)", "buoyancy (verdrahtet, Eisberge, Katamaran)", "weathering (verdrahtet, Stege)", "flock (verdrahtet, Kondor)", "forest (verdrahtet)", "ground (verdrahtet)", "river", "reed (verdrahtet)",
            "season (verdrahtet, das Jahr)", "soundscape (verdrahtet, Eis, Kalbung, Wildwasser)", "store"};

    private static Preferences prefs() {
        try { return Preferences.userRoot().node("com/dan/glacier/bedienfeld"); } catch (RuntimeException | Error e) { return null; }
    }

    public ControlPanel(SceneView view, Wiring wiring) {
        this.view = view;
        this.r = view.renderer();
        this.clock = r.clock;
        this.wiring = wiring;
        setBackground(Theme.bg());
        setOpaque(true);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 8, 16, 10));

        // ============================================================== ZEIT
        block("ZEIT", true);
        put(dateLbl);
        JPanel days = grid(3);
        FButton minus = new FButton("− Tag"), heute = new FButton("16.1.2026"), plus = new FButton("+ Tag");
        minus.addActionListener(e -> { clock.shiftDays(-1); r.readapt(); syncFromScene(); view.requestFocusInWindow(); });
        plus.addActionListener(e -> { clock.shiftDays(1); r.readapt(); syncFromScene(); view.requestFocusInWindow(); });
        heute.setToolTipText("Zurück zum Start der Szene: 16. Januar 2026, 15:00 Uhr");
        heute.addActionListener(e -> { clock.set(SceneClock.START_DATE, SceneClock.START_HOUR); r.readapt(); syncFromScene(); view.requestFocusInWindow(); });
        days.add(minus); days.add(heute); days.add(plus);
        put(days);
        FComboBox monthBox = new FComboBox(new Object[]{"Monat springen …", "16. Januar", "16. Februar", "16. März", "16. April", "16. Mai", "16. Juni",
                "16. Juli", "16. August", "16. September", "16. Oktober", "16. November", "16. Dezember"});
        monthBox.setToolTipText("Zum 16. eines Monats im laufenden Jahr, gleiche Uhrzeit; das Jahr (Schnee, Eis, Lenga-Rot) rechnet bis dorthin vor");
        monthBox.addActionListener(e -> {
            int i = monthBox.getSelectedIndex();
            if (i <= 0) return;
            clock.set(LocalDate.of(clock.date().getYear(), i, 16), clock.hour());
            r.readapt();
            monthBox.setSelectedIndex(0);
            syncFromScene();
            view.requestFocusInWindow();
        });
        put(monthBox);
        put(timeLbl);
        put(time);
        time.addChangeListener(e -> { if (!fromScene) { clock.setHour(time.getValue() / 60.0); r.readapt(); } labels(); });
        put(label("Zeitraffer"));
        lapseBox = new FComboBox(new Object[]{"Uhr steht", "Echtzeit", "10-fach", "1 min/s", "4 min/s", "10 min/s", "1 h/s"});
        lapseBox.setSelectedIndex(1);
        lapseBox.addActionListener(e -> { clock.lapse = LAPSE[Math.max(0, lapseBox.getSelectedIndex())]; view.requestFocusInWindow(); });
        put(lapseBox);
        put(sunLbl);
        // Phase 11: das Jahr
        put(jahrLbl);
        JPanel jahrRow = grid(4);
        String[][] q = {{"Januar", "1"}, {"April", "4"}, {"Juli", "7"}, {"Oktober", "10"}};
        for (String[] m : q) {
            FButton b = new FButton(m[0]);
            b.setToolTipText("Zum 16. " + m[0] + ", gleiche Uhrzeit (die vier Tage der Abnahme)");
            b.addActionListener(e -> { clock.set(LocalDate.of(clock.date().getYear(), Integer.parseInt(m[1]), 16), clock.hour()); r.readapt(); syncFromScene(); view.requestFocusInWindow(); });
            jahrRow.add(b);
        }
        put(jahrRow);
        FCheckBox damm = new FCheckBox("Jahr mit Eisdamm (Brazo Rico staut sich)");
        damm.addActionListener(e -> { if (r.wiring != null) r.wiring.jahr.dammJahr = damm.isSelected(); view.requestFocusInWindow(); });
        put(damm);
        put(small("<html><body style='width:215px'>Das Jahr (com.dan.season) mit dem Klima vor der Front: Januar 12 °C, Juli knapp unter 0 °C, "
                + "rund 800 mm Niederschlag. Die Schneegrenze steht im Sommer bei 1 200 m und kommt im Winter bis an den See; Schnee liegt "
                + "dann auf der Zunge und in den Kronen, in den flachen Buchten des Brazo Rico friert es. Im April ist die Lenga rot. "
                + "Mit Eisdamm steigt der Brazo Rico ab Juni; Anfang März bricht der Damm (Ruptura, Block RUPTURA).</body></html>"));
        put(small("<html><body style='width:215px'>Argentinische Zeit (UTC−3, ohne Sommerzeit). Auf 50,5° Süd steht die Sonne mittags im Norden, "
                + "gegen 14 Uhr; im Dezember geht sie nach 22 Uhr unter. Himmel aus com.dan.sky: Sonne, Mond, Planeten und die Sterne des Südhimmels "
                + "mit Kreuz des Südens und Magellanschen Wolken (die zwei Wolken stehen hier die ganze Nacht über dem Horizont).</body></html>"));
        FCheckBox figBox = new FCheckBox("Sternbilder einzeichnen");
        figBox.setSelected(r.showFigures);
        figBox.addActionListener(e -> { r.showFigures = figBox.isSelected(); view.requestFocusInWindow(); });
        put(figBox);

        // ============================================================== WETTER
        block("WETTER", true);
        put(label("Regie"));
        regieBox = new FComboBox(REGIE);
        regieBox.setSelectedIndex(wiring.regie ? 1 : 0);
        regieBox.setToolTipText("Freie Regie mit dem Klima Patagoniens: Westwind vom Eisfeld, Fronten aus West, Föhnsturm im Lee der Anden, selten Gewitter");
        regieBox.addActionListener(e -> { if (!fromScene) regie(regieBox.getSelectedIndex()); view.requestFocusInWindow(); });
        put(regieBox);
        put(regieLbl);
        FButton weiter = new FButton("Regie weiter");
        weiter.setToolTipText("Alle Handregler lösen; die Größen gleiten mit ihrer Trägheit zurück zur Regie");
        weiter.addActionListener(e -> { wiring.resumeRegie(); view.requestFocusInWindow(); });
        put(weiter);
        put(windLbl);
        put(wind);
        wind.addChangeListener(e -> {
            if (fromScene) return;
            if (wiring.regie) wiring.hand(com.dan.weather.Weather.Var.WIND, Beaufort.mid(wind.getValue()));
            else wiring.wind.speed = Beaufort.mid(wind.getValue());
            labels();
        });
        put(dirLbl);
        put(windDir);
        windDir.addChangeListener(e -> {
            if (fromScene) return;
            if (wiring.regie) wiring.hand(com.dan.weather.Weather.Var.DIRECTION, windDir.getValue());
            else wiring.wind.fromDeg = windDir.getValue();
            labels();
        });
        sub("Himmel");
        put(skyLbl);
        put(coverLbl);
        put(cover);
        cover.addChangeListener(e -> { if (!fromScene) hand(com.dan.weather.Weather.Var.COVER, cover.getValue() / 10.0); labels(); });
        put(lensLbl);
        put(lensSl);
        lensSl.addChangeListener(e -> { if (!fromScene) wiring.lensHand = lensSl.getValue() / 100.0; labels(); });
        put(rainLbl);
        put(rainSl);
        rainSl.addChangeListener(e -> { if (!fromScene) hand(com.dan.weather.Weather.Var.RAIN, rainMm(rainSl.getValue())); labels(); });
        put(fogLbl);
        put(fogSl);
        fogSl.addChangeListener(e -> { if (!fromScene) hand(com.dan.weather.Weather.Var.FOG, fogSl.getValue() / 100.0); labels(); });
        put(visLbl);
        put(visSl);
        visSl.addChangeListener(e -> { if (!fromScene) hand(com.dan.weather.Weather.Var.VISIBILITY, visSl.getValue()); labels(); });
        put(label("Gewitter"));
        thunderBox = new FComboBox(new Object[]{"keines", "fern", "nah", "heftig", "über uns"});
        thunderBox.addActionListener(e -> { if (!fromScene) hand(com.dan.weather.Weather.Var.THUNDER, thunderBox.getSelectedIndex()); view.requestFocusInWindow(); });
        put(thunderBox);
        JPanel sh1 = grid(2), sh2 = grid(2);
        layer(sh1, "Wolken", r.showClouds, v -> r.showClouds = v);
        layer(sh1, "Regen", r.showRain, v -> r.showRain = v);
        layer(sh2, "Nebel", r.showFog, v -> r.showFog = v);
        layer(sh2, "Blitze", r.showLightning, v -> r.showLightning = v);
        put(sh1);
        put(sh2);
        FButton bolt = new FButton("Blitz in Blickrichtung");
        bolt.setToolTipText("Ein Erdblitz in 1,5 bis 4 km vor der Kamera (auch ohne Gewitter)");
        bolt.addActionListener(e -> { r.triggerLightning(com.dan.lightning.Bolt.Kind.CLOUD_GROUND); view.requestFocusInWindow(); });
        put(bolt);
        put(small("<html><body style='width:215px'>Handregler halten eine Größe fest, bis „Regie weiter“. Lentikularis: von Hand, sonst aus Lage "
                + "und Westwind (im Föhnsturm fast immer, vor einer Front oft). Die Linsen stehen über den Anden still, die Luft strömt hindurch.</body></html>"));

        // ============================================================== SEE
        block("SEE", false);
        put(levelLbl);
        put(level);
        level.addChangeListener(e -> { if (!fromScene) wiring.waterLevel.level = level.getValue() / 100.0; labels(); });
        put(stauLbl);
        put(stau);
        stau.addChangeListener(e -> { if (!fromScene) wiring.waterLevel.stau = stau.getValue(); labels(); });
        put(small("<html><body style='width:215px'>Der Stau des Brazo Rico (bis 30 m über dem Hauptsee): im Jahr mit Eisdamm aus dem Jahresgang, "
                + "während der Ruptura aus ihrem Abfluss. Der Regler zeigt ihn an.</body></html>"));

        // ============================================================== REGIE (Phase 14)
        block("REGIEBÜCHER", true);
        put(buchLbl);
        java.util.List<com.dan.glacier.scene.Regie.Book> books;
        try { books = com.dan.glacier.scene.Regie.all(); }
        catch (RuntimeException ex) { books = java.util.List.of(); buchInfo.setText("Regiebücher nicht lesbar: " + ex.getMessage()); }
        final java.util.List<com.dan.glacier.scene.Regie.Book> bookList = books;
        FComboBox bookBox = new FComboBox(bookList.toArray());
        put(bookBox);
        Runnable showAbout = () -> {
            Object sel = bookBox.getSelectedItem();
            if (sel instanceof com.dan.glacier.scene.Regie.Book b)
                buchInfo.setText("<html><body style='width:215px'>" + esc(b.about()) + String.format(Locale.GERMAN, " · %d:%02d min, %d Stichworte", (int) b.length() / 60, (int) b.length() % 60, b.cues().size()) + "</body></html>");
        };
        bookBox.addActionListener(e -> { showAbout.run(); view.requestFocusInWindow(); });
        showAbout.run();
        JPanel bookRow = grid(2);
        FButton bookGo = new FButton("Von vorn"), bookStop = new FButton("Anhalten");
        bookGo.setToolTipText("Das Regiebuch von vorn: Datum, Uhrzeit, Wetter, Ansichten, Fahrten, Kalbungen; am Ende ein Blatt mit den Fotos in docs/status");
        FCheckBox imKino = new FCheckBox("Im Kino zeigen");
        imKino.setSelected(true);
        imKino.setToolTipText("Phase 16: ganzer Bildschirm ohne Bedienfeld, Breitwand mit Balken, Vor- und Abspann, Untertitel aus den Stichworten, "
                + "Überblendung bei jedem Schnitt. Esc beendet das Kino (das Regiebuch läuft weiter), Leertaste hält an, F12 fotografiert.");
        bookGo.addActionListener(e -> {
            if (bookBox.getSelectedItem() instanceof com.dan.glacier.scene.Regie.Book b) {
                if (imKino.isSelected()) r.kinoStart(b); else r.regieStart(b);
            }
            view.requestFocusInWindow();
        });
        bookStop.addActionListener(e -> { r.regieStop(); view.requestFocusInWindow(); });
        bookRow.add(bookGo);
        bookRow.add(bookStop);
        put(bookRow);
        put(imKino);
        put(buchInfo);
        put(small("<html><body style='width:215px'>Drei Regiebücher als Text (scene/regie/*.txt): ein Sommertag an der Front, die Ruptura, "
                + "der Südhimmel. Jedes setzt Datum, Uhr, Zeitraffer und Wetter, wechselt Ansichten und Fahrten, löst Kalbungen aus und "
                + "fotografiert; am Ende liegt ein Blatt mit allen Fotos in docs/status (Regiebuch_….png). Im Kino: Esc heraus, "
                + "Leertaste hält an.</body></html>"));

        // ============================================================== EIS (Phase 15)
        eisBlock();

        // ============================================================== KALBUNG (Phase 7)
        block("KALBUNG", true);
        put(kalbLbl);
        FCheckBox regieK = new FCheckBox("Regie würfelt Abbrüche");
        regieK.setSelected(true);
        regieK.addActionListener(e -> {
            com.dan.glacier.scene.Kalbung k = r.kalbung;
            if (k != null) k.regie = regieK.isSelected();
            view.requestFocusInWindow();
        });
        put(regieK);
        sub("Auslösen an der Front im Blick");
        JPanel arten = grid(3);
        for (com.dan.glacier.scene.Kalbung.Art art : com.dan.glacier.scene.Kalbung.Art.values()) {
            FButton b = new FButton(art.label);
            b.addActionListener(e -> {
                com.dan.glacier.scene.Kalbung k = r.kalbung;
                double[] fb = r.frontImBlick();   // Phase 10: aus dem Blick (auch bei der Bootsfahrt), nicht aus dem alten Drehpunkt
                if (k != null) k.trigger(art, fb[0], fb[1]);
                view.requestFocusInWindow();
            });
            arten.add(b);
        }
        put(arten);
        FCheckBox showK = new FCheckBox("Abschnitte und Stücke zeichnen");
        showK.setSelected(true);
        showK.addActionListener(e -> { r.showKalbung = showK.isSelected(); view.requestFocusInWindow(); });
        put(showK);
        put(kalbLog);
        put(small("<html><body style='width:215px'>Ein Abschnitt der Front wird in Zellen zerlegt; die Schmelzkerbe frisst sich an der "
                + "Wasserlinie hinein, Spalten schwächen das Eis. Wann es bricht, entscheidet die Spannung (com.dan.fracture). "
                + "Von Hand: an der Stelle der Front, die dem Blickpunkt am nächsten ist, mit schneller Kerbe.</body></html>"));

        // ============================================================== RUPTURA (Phase 13)
        block("RUPTURA", true);
        put(rupLbl);
        JPanel rbook = grid(1);
        FButton book = new FButton("Regiebuch „Die Ruptura“");
        book.setToolTipText("Jahr mit Eisdamm, 4. März 2027 15 Uhr, die Uhr steht; der Brazo Rico steigt von 21 auf 25 m, dann Tunnel, Rinne, Brücke, Einsturz, Abfluss (rund 5 Minuten)");
        book.addActionListener(e -> {
            r.rupturaRegiebuch();
            ansicht(Ansichten.byKey(8));
            view.requestFocusInWindow();
        });
        rbook.add(book);
        put(rbook);
        JPanel rhand = grid(3);
        FButton durch = new FButton("Durchbruch"), sturz = new FButton("Einsturz"), neu = new FButton("Neuer Damm");
        durch.setToolTipText("Der Tunnel ist sofort durch (auch im Jahr mit Damm ohne Regiebuch)");
        sturz.setToolTipText("Die Brücke reißt an ihren Enden (sobald der Tunnel offen ist)");
        neu.setToolTipText("Alles zurück: ein frischer Damm, kein Tunnel");
        durch.addActionListener(e -> { com.dan.glacier.scene.Ruptura ru = r.ruptura; if (ru != null) ru.durchbruchJetzt(); view.requestFocusInWindow(); });
        sturz.addActionListener(e -> { com.dan.glacier.scene.Ruptura ru = r.ruptura; if (ru != null) ru.einsturzJetzt(); view.requestFocusInWindow(); });
        neu.addActionListener(e -> { com.dan.glacier.scene.Ruptura ru = r.ruptura; if (ru != null) ru.reset(); view.requestFocusInWindow(); });
        rhand.add(durch);
        rhand.add(sturz);
        rhand.add(neu);
        put(rhand);
        FCheckBox showR = new FCheckBox("Stau und Rinne zeichnen");
        showR.setSelected(true);
        showR.addActionListener(e -> { r.showRuptura = showR.isSelected(); view.requestFocusInWindow(); });
        put(showR);
        put(rupLog);
        put(small("<html><body style='width:215px'>Liegt die Front an der Halbinsel (Jahr mit Eisdamm, Block ZEIT), staut sich der Brazo Rico hinter "
                + "dem Damm. Das Wasser frisst einen Tunnel an der Sohle, weitet ihn, die Decke fällt in die Rinne (com.dan.fracture), "
                + "über ihr bleibt die Brücke stehen, bis sie stürzt; bis 8 000 m³/s schießen in den Canal de los Témpanos. "
                + "Ohne Regiebuch kommt die Ruptura Anfang März von selbst, im Zeitraffer der Uhr. Taste 8: die Brücke vom Steg.</body></html>"));

        // ============================================================== WELLE (Phase 8)
        block("WELLE", true);
        put(waveLbl);
        put(pegelKurve);
        sub("Probe: ein Einschlag an der Front im Blick");
        JPanel probes = grid(3);
        for (int v : new int[]{5_000, 20_000, 50_000}) {
            FButton b = new FButton(String.format(Locale.GERMAN, "%,d m³", v));
            b.addActionListener(e -> { double[] fb = r.frontImBlick(); r.probeWave(fb[0], fb[1], v); view.requestFocusInWindow(); });
            probes.add(b);
        }
        put(probes);
        JPanel waveRow = grid(2);
        FCheckBox showW = new FCheckBox("Welle zeichnen");
        showW.setSelected(true);
        showW.addActionListener(e -> { r.showWave = showW.isSelected(); view.requestFocusInWindow(); });
        waveRow.add(showW);
        FButton calm = new FButton("See beruhigen");
        calm.addActionListener(e -> { if (r.waveReady) r.wave.calm(); view.requestFocusInWindow(); });
        waveRow.add(calm);
        put(waveRow);
        put(small("<html><body style='width:215px'>Flachwasser auf zwei Gittern (com.dan.wave): 2 m zwischen Steg und Front, 8 m über "
                + "beide Seearme. Quelle ist das Volumen, das ein Stück beim Eintauchen verdrängt; die Front wirft die Welle zurück, "
                + "am Ufer läuft sie auf und hinterlässt einen nassen Saum. Die Kurve zeigt den Pegel vor dem Steg (90 s).</body></html>"));

        // ============================================================== EISBERGE (Phase 9)
        block("EISBERGE", true);
        put(bergLbl);
        JPanel bergRow = grid(2);
        FButton day = new FButton("Ein Tag Kalbung");
        day.addActionListener(e -> { r.fillDay(); view.requestFocusInWindow(); });
        bergRow.add(day);
        FButton tip = new FButton("Ein Berg kippt");
        tip.addActionListener(e -> { double[] fb = r.frontImBlick(); r.toppler(fb[0], fb[1]); view.requestFocusInWindow(); });
        bergRow.add(tip);
        put(bergRow);
        JPanel bergRow2 = grid(2);
        FCheckBox showB = new FCheckBox("Eisberge zeichnen");
        showB.setSelected(true);
        showB.addActionListener(e -> {
            r.showBergs = showB.isSelected();
            view.requestFocusInWindow();
        });
        bergRow2.add(showB);
        FButton clearB = new FButton("Alle weg");
        clearB.addActionListener(e -> { r.bergs.clear(); view.requestFocusInWindow(); });
        bergRow2.add(clearB);
        put(bergRow2);
        put(small("<html><body style='width:215px'>Stücke der Kalbung werden Eisberge, sobald sie auf dem Wasser zur Ruhe kommen "
                + "(com.dan.buoyancy): sie schwimmen mit 90 % unter Wasser, treiben mit dem Wind, stoßen sich, laufen auf Grund und "
                + "schmelzen unter Wasser schneller als an der Luft – so wandert der Schwerpunkt, und manch einer kippt. "
                + "„Ein Tag Kalbung“ legt die Eisberge eines Tages auf einmal in den See, „Ein Berg kippt“ einen hohen, schmalen "
                + "vor die Front im Blick. Von unten: Ansicht 7.</body></html>"));

        // ============================================================== LEBEN (Phase 10)
        block("LEBEN", true);
        put(lebenLbl);
        JPanel lebenRow = grid(2);
        FButton ride = new FButton("Bootsfahrt");
        ride.addActionListener(e -> { if (!r.startBootsfahrt()) lebenLbl.setText("Die Route steht erst, wenn die Welle steht."); view.requestFocusInWindow(); });
        lebenRow.add(ride);
        FButton out = new FButton("Aussteigen");
        out.addActionListener(e -> { r.stopBootsfahrt(); view.requestFocusInWindow(); });
        lebenRow.add(out);
        put(lebenRow);
        JPanel lebenRow2 = grid(2);
        FButton fall = new FButton("Lenga im Herbst");
        fall.addActionListener(e -> { clock.set(LocalDate.of(clock.date().getYear(), 4, 22), clock.hour()); r.readapt(); syncFromScene(); view.requestFocusInWindow(); });
        lebenRow2.add(fall);
        FButton wetS = new FButton("Stege nass");
        wetS.addActionListener(e -> { r.stegeNass().setWetLine(r.camera.waterLevel + 1.4); view.requestFocusInWindow(); });
        lebenRow2.add(wetS);
        put(lebenRow2);
        JPanel lebenRow3 = grid(3);
        boolean[] ls = r.lebenShown();
        FCheckBox lb = new FCheckBox("Vögel"), lk = new FCheckBox("Boot"), lst = new FCheckBox("Stege");
        lb.setSelected(ls[0]); lk.setSelected(ls[1]); lst.setSelected(ls[2]);
        java.awt.event.ActionListener al = e -> { r.showLeben(lb.isSelected(), lk.isSelected(), lst.isSelected()); view.requestFocusInWindow(); };
        lb.addActionListener(al); lk.addActionListener(al); lst.addActionListener(al);
        lebenRow3.add(lb); lebenRow3.add(lk); lebenRow3.add(lst);
        put(lebenRow3);
        put(small("<html><body style='width:215px'>Dominikanermöwen fliegen zwischen Front, Steg und Anleger und sitzen auf "
                + "Geländern, Pfählen und Eisbergen; Andenkondore kreisen in der Thermik über den Hängen (com.dan.flock). "
                + "Der Katamaran (com.dan.buoyancy) fährt die Bootsfahrt 300 m vor der Front entlang und schwimmt auf der Welle "
                + "eines Abbruchs; die Kamera fährt mit (Ziehen schaut umher). Die Stege werden im Regen und im Auflaufen der "
                + "Welle nass (com.dan.weathering). „Lenga im Herbst“ springt auf den 22. April: die Wälder sind rot.</body></html>"));

        // ============================================================== KLANG (Phase 12)
        block("KLANG", true);
        put(klangLbl);
        FCheckBox ton = new FCheckBox("Ton (Lautsprecher)");
        ton.setSelected(r.tonAn());
        ton.addActionListener(e -> { r.setTon(ton.isSelected()); view.requestFocusInWindow(); });
        put(ton);
        FComboBox vol = new FComboBox(new Object[]{"Lautstärke 100 %", "Lautstärke 50 %", "Lautstärke 25 %", "Lautstärke 10 %"});
        vol.addActionListener(e -> { r.klang.s.volume = new double[]{1, 0.5, 0.25, 0.1}[Math.max(0, vol.getSelectedIndex())]; view.requestFocusInWindow(); });
        put(vol);
        put(small("<html><body style='width:215px'>Erzeugter Ton (com.dan.soundscape), kein Klang aus Dateien: das Eis knackt wie Schüsse und ächzt, "
                + "der Donner einer Kalbung rollt aus dem Weg des Risses heran, nach der Laufzeit des Schalls; dann der dumpfe Aufschlag und, "
                + "wenn die Welle das Ufer erreicht, ihr Rauschen. Dazu Wind (über dem Eis anders als im Wald), See, Regen, Möwen, das "
                + "Rauschen der Kondorschwingen, der Diesel des Katamarans und das Singen der Geländer im Sturm. Hörproben ohne App: "
                + "tools/Hoerproben.java schreibt sie nach docs/hoerproben.</body></html>"));

        // ============================================================== KAMERA
        block("KAMERA", true);
        put(camLbl);
        free.addActionListener(e -> { view.setMode(free.isSelected() ? Camera.Mode.FREI : Camera.Mode.KREISEN); view.requestFocusInWindow(); });
        orbit.addActionListener(e -> { view.setAutoOrbit(orbit.isSelected()); view.requestFocusInWindow(); });
        put(free);
        put(orbit);
        view.setModeListener(m -> javax.swing.SwingUtilities.invokeLater(() -> free.setSelected(m == Camera.Mode.FREI)));
        view.setOrbitListener(b -> javax.swing.SwingUtilities.invokeLater(() -> orbit.setSelected(b)));
        JPanel home = grid(1);
        FButton hb = new FButton("Pos1 Grundansicht");
        hb.addActionListener(e -> { view.goHome(); view.requestFocusInWindow(); });
        home.add(hb);
        put(home);
        sub("Ansichten (Tasten 1 bis 8)");
        JPanel views = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        views.setOpaque(false);
        for (Ansichten.Ansicht a : Ansichten.ALLE) {
            FButton b = new FButton(a.key() + " " + a.name());
            boolean ok = Ansichten.ready(a, Renderer.PHASE);
            b.setEnabled(ok);
            b.setToolTipText(Ansichten.describe(a, Renderer.PHASE));
            b.addActionListener(e -> ansicht(a));
            views.add(b);
            view.setKeyAction(java.awt.event.KeyEvent.VK_0 + a.key(), () -> javax.swing.SwingUtilities.invokeLater(() -> ansicht(a)));
        }
        put(views);
        sub("Fahrten");
        JPanel rides = grid(2);
        FButton zunge = new FButton("Über die Zunge"), halt = new FButton("Fahrt anhalten");
        zunge.addActionListener(e -> {
            com.dan.glacier.camera.Fahrt f = r.zungenFahrt();
            if (f != null) r.camera.startFahrt(f);
            view.requestFocusInWindow();
        });
        halt.addActionListener(e -> { r.camera.stopFahrt(); view.requestFocusInWindow(); });
        rides.add(zunge);
        rides.add(halt);
        put(rides);
        JPanel rides2 = grid(1);
        FButton spalte = new FButton("Durch die Spalte");
        spalte.setToolTipText("Hinab in eine tiefe Spalte nahe der Front, ihr entlang in halber Tiefe, wieder hinauf (5 m/s)");
        spalte.addActionListener(e -> {
            com.dan.glacier.camera.Fahrt f = r.spaltenFahrt();
            if (f != null) r.camera.startFahrt(f);
            else camLbl.setText("Die Spalte wird noch gesucht (einige Sekunden nach dem Start).");
            view.requestFocusInWindow();
        });
        rides2.add(spalte);
        put(rides2);
        put(small("<html><body style='width:215px'>Im Gelände aus GLO-30 [G]: der Steg 300 m vor der Spitze, die Südflanke vom Ufer der Brazo Rico, das Boot im Canal de los Témpanos, der Berg im Nordosten, auf dem Eis mitten auf der Zunge. „Über die Zunge“: 2½ Minuten in 70 m Höhe vom Eisfeld bis über die Front. „Durch die Spalte“: in einer 15 bis 25 m tiefen Spalte der Zunge entlang. Taste 7 taucht vor der Front, Taste 8 blickt vom Steg auf die Brücke der Ruptura.</body></html>"));

        // ============================================================== BILD
        block("BILD", false);
        put(label("Qualität"));
        qualBox = new FComboBox(Quality.values());
        qualBox.setSelectedItem(r.quality());
        qualBox.addActionListener(e -> { r.setQuality((Quality) qualBox.getSelectedItem()); view.requestFocusInWindow(); });
        put(qualBox);
        put(qualLbl);
        autoBox.setSelected(r.autoScale);
        autoBox.addActionListener(e -> { r.autoScale = autoBox.isSelected(); if (!r.autoScale) r.renderScale = scaleSl.getValue() / 100.0; view.requestFocusInWindow(); });
        put(autoBox);
        put(scaleLbl);
        put(scaleSl);
        scaleSl.addChangeListener(e -> {
            if (fromScene) return;
            r.autoScale = false;
            autoBox.setSelected(false);
            r.renderScale = scaleSl.getValue() / 100.0;
        });
        put(label("HUD"));
        hudBox = new FComboBox(new Object[]{"kurz", "voll", "aus"});
        hudBox.addActionListener(e -> {
            int i = hudBox.getSelectedIndex();
            r.showHud = i != 2;
            r.hudFull = i == 1;
            view.requestFocusInWindow();
        });
        put(hudBox);
        marks.setSelected(r.showMarks);
        marks.addActionListener(e -> { r.showMarks = marks.isSelected(); view.requestFocusInWindow(); });
        put(marks);
        put(label("Ebenen (Phase 2, 4 und 5)"));
        JPanel lay1 = grid(2), lay2 = grid(2), lay3 = grid(2);
        layer(lay1, "See", r.showWater, v -> r.showWater = v);
        layer(lay1, "Wald", r.showForest, v -> r.showForest = v);
        layer(lay2, "Halme", r.showReed, v -> r.showReed = v);
        layer(lay2, "Bodendecke", r.showGround, v -> r.showGround = v);
        layer(lay3, "Baumschatten", r.showSunShadow, v -> r.showSunShadow = v);
        layer(lay3, "Eisberge", r.showIce, v -> r.showIce = v);
        JPanel lay4 = grid(2);
        layer(lay4, "Gletscher", r.showGlacier, v -> r.showGlacier = v);
        layer(lay4, "Schmelzbäche", r.showMelt, v -> r.showMelt = v);
        put(lay1);
        put(lay2);
        put(lay3);
        put(lay4);
        JPanel pic = grid(2);
        FButton photo = new FButton("Foto (F12)"), shaders = new FButton("Shader neu (F5)");
        photo.addActionListener(e -> { r.photoRequest = true; view.requestFocusInWindow(); });
        shaders.addActionListener(e -> { r.requestShaderReload(); view.requestFocusInWindow(); });
        pic.add(photo); pic.add(shaders);
        put(pic);

        // ============================================================== TECHNIK
        // ============================================================== CHRONIK und DATEN (Phase 15)
        chronikBlock();
        datenBlock();

        block("TECHNIK", false);
        put(perfLbl);
        // Phase 16: der Dauerlauf (Abnahme „fertig“)
        JPanel dlRow = grid(2);
        FButton dlGo = new FButton("Dauerlauf 30 min"), dlStop = new FButton("Abbrechen");
        dlGo.setToolTipText("Die drei Regiebücher im Kino, dann drei Minuten frei im Zeitraffer, im Wechsel 30 Minuten lang; "
                + "am Ende Bericht und Kurven in docs/status (Dauerlauf_….png und .txt)");
        dlGo.addActionListener(e -> {
            boolean ok = com.dan.glacier.app.Dauerlauf.starten(r, 30, f -> r.hudMeldung("Dauerlauf fertig: " + (f != null ? f.getName() : "ohne Bericht")));
            r.hudMeldung(ok ? "Dauerlauf beginnt (30 min)" : "Ein Dauerlauf läuft schon");
            view.requestFocusInWindow();
        });
        dlStop.addActionListener(e -> com.dan.glacier.app.Dauerlauf.anhalten());
        dlRow.add(dlGo);
        dlRow.add(dlStop);
        put(dlRow);
        put(computeLbl);
        put(glLbl);
        put(physLbl);
        sub("Packages");
        put(pkgLbl);
        sub("Verdrahtung");
        put(linksLbl);
        sub("Alle Zeilen des HUD");
        put(hudLbl);

        // Phase 15: ein Klick in die Szene zeigt die Säulen- oder die Eisbergkarte (F30 A)
        view.setClickListener((x, y, w, h) -> javax.swing.SwingUtilities.invokeLater(() -> {
            String m = Karte.klick(this, r, chronik, x, y, w, h);
            if (m != null) r.hudMeldung(m);
        }));
        for (Object[] b : bloecke) ((FCollapsiblePanel) b[0]).setBodyComponent((JComponent) b[1]);
        for (FCollapsiblePanel p : zu) p.setExpanded(false, false);
        add(Box.createVerticalGlue());
        view.setKeyAction(java.awt.event.KeyEvent.VK_F1, () -> javax.swing.SwingUtilities.invokeLater(this::tasten));
        view.setKeyAction(java.awt.event.KeyEvent.VK_SPACE, () -> javax.swing.SwingUtilities.invokeLater(() ->
                lapseBox.setSelectedIndex(clock.lapse == 0 ? 1 : 0)));
        syncFromScene();
        new Timer(500, e -> {
            // Befund Phase 3: solange eine Auswahlliste offen ist, nichts auffrischen; neue Texte ändern die Höhe der
            // Zeilen, der Block legt sich neu aus, und Swing schließt die Liste, sobald sich ihr Feld bewegt
            if (popupOpen(this) || menuOpen()) return;
            syncFromScene();
            if (ticks++ % 2 == 0) technik();
            nachmessen();
        }).start();
    }

    /** Phase 3: Regie wählen: von Hand oder freie Regie ab einer Lage. */
    private void regie(int i) {
        if (i <= 0) { wiring.regie = false; return; }
        wiring.regie = true;
        wiring.startFree(REGIE_PHASE[Math.min(REGIE_PHASE.length - 1, i)]);
        r.readapt();
        syncFromScene();
    }

    /** Ein Handregler: mit Regie überschreibt er die Größe, ohne setzt er das Feld der Verdrahtung. */
    private void hand(com.dan.weather.Weather.Var v, double value) {
        if (wiring.regie) { wiring.hand(v, value); return; }
        switch (v) {
            case COVER -> wiring.oktas = value;
            case RAIN -> wiring.rainRate = value;
            case FOG -> wiring.fogAmount = value;
            case VISIBILITY -> wiring.visibilityKm = value;
            case THUNDER -> wiring.thunderstorm = com.dan.lightning.Lightning.Level.values()[(int) Math.max(0, Math.min(4, value))];
            default -> { }
        }
    }

    /** Reglerstellung 0..200 → mm/h (0 bis 40, unten fein). */
    static double rainMm(int v) { return v <= 0 ? 0 : 40 * Math.pow(v / 200.0, 2.2); }
    static int rainPos(double mm) { return mm <= 0 ? 0 : (int) Math.round(200 * Math.pow(Math.min(40, mm) / 40, 1 / 2.2)); }

    private void ansicht(Ansichten.Ansicht a) {
        if (Ansichten.apply(a, r.camera, Renderer.PHASE)) {
            free.setSelected(r.camera.mode == Camera.Mode.FREI);
            orbit.setSelected(false);
        }
        view.requestFocusInWindow();
    }

    private void tasten() {
        StringBuilder an = new StringBuilder();
        for (Ansichten.Ansicht a : Ansichten.ALLE) an.append(an.length() > 0 ? ", " : "").append(a.key()).append(' ').append(a.name());
        com.dan.foptionpane.FOptionPane.showMessageDialog(this, "<html><body style='width:420px'>"
                + "<b>Szene</b><br>Links ziehen: drehen · rechts ziehen: verschieben · Rad, Bild↑/↓, +/−: zoomen<br>"
                + "Pfeile: drehen · W A S D: gleiten · Q/E: ab und auf (frei) · Umschalt: schneller<br>"
                + "F: frei fliegen · R: von selbst kreisen · Pos1: Grundansicht · H: HUD kurz, voll, aus<br>"
                + "Leertaste: Uhr anhalten und weiter · F12: Foto nach docs/status · F5: Shader neu laden<br>"
                + "Ansichten: " + an + "<br><br>"
                + "<b>Fenster</b><br>F1: diese Übersicht · F11: nur die Szene (Bedienfeld und Statuszeile weg und wieder da)</body></html>",
                "Glacier · Tasten", com.dan.foptionpane.FOptionPane.INFORMATION_MESSAGE);
    }

    /** Regler nach der Szene stellen (alle 0,5 s). */
    private void syncFromScene() {
        fromScene = true;
        int m = (int) Math.round(clock.hour() * 60) % (24 * 60);
        if (!time.getValueIsAdjusting()) time.setValue(m);
        if (!wind.getValueIsAdjusting()) wind.setValue(Beaufort.of(wiring.wind.speed()));
        if (!windDir.getValueIsAdjusting()) windDir.setValue((int) Math.round(wiring.wind.fromDeg()) % 360);
        if (!level.getValueIsAdjusting()) level.setValue((int) Math.round(wiring.waterLevel.level * 100));
        if (!stau.getValueIsAdjusting()) stau.setValue((int) Math.round(wiring.waterLevel.stau));
        if (!scaleSl.getValueIsAdjusting()) scaleSl.setValue((int) Math.round(r.renderScale * 100));
        if (!cover.getValueIsAdjusting()) cover.setValue((int) Math.round(wiring.oktas * 10));
        if (!rainSl.getValueIsAdjusting()) rainSl.setValue(rainPos(wiring.rainRate));
        if (!fogSl.getValueIsAdjusting()) fogSl.setValue((int) Math.round(wiring.fogAmount * 100));
        if (!visSl.getValueIsAdjusting()) visSl.setValue((int) Math.round(Math.min(150, wiring.visibilityKm)));
        if (!lensSl.getValueIsAdjusting()) lensSl.setValue((int) Math.round((wiring.lensHand >= 0 ? wiring.lensHand : wiring.lens) * 100));
        if (thunderBox != null && !thunderBox.isPopupVisible()) thunderBox.setSelectedIndex(wiring.thunderstorm.ordinal());
        if (regieBox != null && !regieBox.isPopupVisible() && !wiring.regie) regieBox.setSelectedIndex(0);
        fromScene = false;
        labels();
    }

    private void labels() {
        txt(dateLbl, clock.local().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d. MMMM u", Locale.GERMANY)));
        txt(timeLbl, "Uhrzeit " + clock.timeLabel());
        double[] s = clock.sun();
        double day = clock.local().toLocalDate().atStartOfDay(SceneClock.ZONE).toInstant().toEpochMilli();
        double up = Sun.crossing(day, clock.lat, clock.lon, true), down = Sun.crossing(day, clock.lat, clock.lon, false);
        txt(sunLbl, String.format(Locale.GERMAN, "<html><body style='width:215px'>Sonne %.1f° hoch, aus %03.0f° (%s) · Aufgang %s, Untergang %s</body></html>",
                s[0], s[1], compass(s[1]), hm(up), hm(down)));
        txt(windLbl, String.format(Locale.GERMAN, "Wind Bft %d · %s · %.1f m/s%s", wiring.wind.beaufort(), Beaufort.NAME[wiring.wind.beaufort()], wiring.wind.speed(),
                wiring.regie ? String.format(Locale.GERMAN, " · Böe %.1f", wiring.gustNow) : ""));
        com.dan.weather.Weather.Snapshot ws = wiring.weather.snapshot();
        txt(regieLbl, "<html><body style='width:215px'>" + (!wiring.regie ? "Alles von Hand; die Regie steht." : ws == null ? "Regie startet …"
                : "Freie Regie: " + ws.phase().label + String.format(Locale.GERMAN, ", als Nächstes %s in %.1f h", ws.next().label, ws.nextInH())
                + String.format(Locale.GERMAN, "<br>%.1f °C, %.0f %% Feuchte, %.0f hPa %s", ws.temperature(), ws.humidity(), ws.pressure(), ws.tendencyText())
                + (wiring.weather.overrides() > 0 ? "<br>" + wiring.weather.overrides() + " Größen von Hand" : "")) + "</body></html>");
        txt(skyLbl, "<html><body style='width:215px'>" + esc(r.skyLine().replace("!", "")) + "</body></html>");
        txt(coverLbl, String.format(Locale.GERMAN, "Bedeckung %.1f/8 · %s", wiring.oktas, wiring.cloudType.label));
        txt(lensLbl, String.format(Locale.GERMAN, "Lentikularis %.0f %%%s", (wiring.lensHand >= 0 ? wiring.lensHand : wiring.lens) * 100, wiring.lensHand >= 0 ? " (von Hand)" : ""));
        txt(rainLbl, wiring.rainRate <= 0.01 ? "Regen: trocken" : String.format(Locale.GERMAN, "Regen %.1f mm/h", wiring.rainRate));
        txt(fogLbl, String.format(Locale.GERMAN, "Seenebel %.0f %%%s", wiring.fogAmount * 100,
                r.fog.snapshot().any() ? String.format(Locale.GERMAN, " · %.0f bis %.0f m", wiring.fogBase, wiring.fogTop) : ""));
        txt(visLbl, String.format(Locale.GERMAN, "Sicht %.0f km", wiring.visibilityKm));
        txt(dirLbl, String.format(Locale.GERMAN, "aus %03.0f° (%s)", wiring.wind.fromDeg(), compass(wiring.wind.fromDeg())));
        txt(levelLbl, String.format(Locale.GERMAN, "Seespiegel %+.2f m (Lago Argentino, %.0f m ü. M. [G])", wiring.waterLevel.level(), com.dan.glacier.scene.Site.LAKE_LEVEL_ASL));
        txt(stauLbl, String.format(Locale.GERMAN, "Stau des Brazo Rico %+.0f m", wiring.waterLevel.stau));
        txt(camLbl, String.format(Locale.GERMAN, "<html><body style='width:215px'>%s · Auge %.0f m · Blick %03.0f° · %.0f m von der Front · %s</body></html>",
                r.camera.modeName(), r.camera.eye[1], r.camera.heading(), r.camera.originDistance(),
                com.dan.glacier.camera.Ansichten.zone(r.camera.eye[0], r.camera.eye[2])));
        txt(scaleLbl, String.format(Locale.GERMAN, "Auflösung %.0f %% (%s)", r.renderScale * 100, r.autoScale ? "geregelt, " + r.scaleSteps() + "× geändert" : "fest"));
        txt(qualLbl, "<html><body style='width:215px'>" + r.quality().summary() + "</body></html>");
    }

    private void technik() {
        txt(perfLbl, String.format(Locale.GERMAN, "<html><body style='width:215px'>%.0f Bilder/s · Grafikkarte %.2f ms · Prozessor %.2f ms<br>%s</body></html>",
                r.fps(), r.gpuMs(), r.cpuMs(), r.passTimes()));
        ComputeProbe.Result c = r.compute();
        txt(computeLbl, "<html><body style='width:215px'>" + (c == null ? "Compute-Test läuft …" : c.line()) + "</body></html>");
        txt(glLbl, "<html><body style='width:215px'>" + r.glInfo() + (r.reversedDepth() ? " · umgekehrte Tiefe" : "") + "</body></html>");
        txt(physLbl, "<html><body style='width:215px'>" + r.physik.describe() + "</body></html>");
        txt(waveLbl, "<html><body style='width:215px'>" + esc(r.waveLine()) + "</body></html>");
        txt(bergLbl, "<html><body style='width:215px'>" + esc(r.bergs.describe()) + "</body></html>");
        txt(lebenLbl, "<html><body style='width:215px'>" + esc(r.lebenLine()) + "</body></html>");
        txt(jahrLbl, "<html><body style='width:215px'>" + esc(r.jahrLine()) + "</body></html>");
        txt(klangLbl, "<html><body style='width:215px'>" + esc(r.klangLine()) + "</body></html>");
        pegelKurve.repaint();
        com.dan.glacier.scene.Kalbung k = r.kalbung;
        txt(kalbLbl, "<html><body style='width:215px'>" + (k == null ? "Kalbung: wartet auf den Gletscher" : esc(k.describe())) + "</body></html>");
        if (k != null) {
            StringBuilder kb = new StringBuilder("<html><body style='width:215px'>");
            java.util.List<com.dan.glacier.scene.Kalbung.Ereignis> list;
            synchronized (k.log) { list = new java.util.ArrayList<>(k.log); }
            for (int i = list.size() - 1, n = 0; i >= 0 && n < 5; i--, n++) {
                com.dan.glacier.scene.Kalbung.Ereignis e = list.get(i);
                kb.append(String.format(Locale.GERMAN, "vor %.0f min: %s, %,.0f m³ in %d Stücken, Riss %.1f s vorher<br>",
                        (k.sceneTime() - e.time()) / 60, e.art().label, e.volume(), e.pieces(), e.crackLead()));
            }
            for (com.dan.glacier.scene.Kalbung.Abschnitt a : k.sections())
                if (a.zustand != com.dan.glacier.scene.Kalbung.Zustand.RUHT) kb.append("· ").append(esc(a.describe())).append("<br>");
            txt(kalbLog, kb.append("</body></html>").toString());
        }
        txt(buchLbl, "<html><body style='width:215px'>" + esc(r.regieLine()) + "</body></html>");
        com.dan.glacier.scene.Ruptura ru = r.ruptura;
        txt(rupLbl, "<html><body style='width:215px'>" + esc(r.rupturaLine()) + "</body></html>");
        if (ru != null) {
            StringBuilder rb = new StringBuilder("<html><body style='width:215px'>");
            java.util.List<com.dan.glacier.scene.Ruptura.Ereignis> list;
            synchronized (ru.log) { list = new java.util.ArrayList<>(ru.log); }
            for (int i = Math.max(0, list.size() - 7); i < list.size(); i++) rb.append(esc(list.get(i).toString())).append("<br>");
            txt(rupLog, rb.append("</body></html>").toString());
        }
        txt(pkgLbl, "<html><body style='width:215px'>" + PAKETE.length + " Packages: " + String.join(", ", PAKETE)
                + ". Abgestellt nach _alt/packages: timber, smoke, hearth, herd, longship, road, muster (F6 A). "
                + "Die Isolationsprüfung <i>check-packages</i> läuft nach jedem Übersetzen.</body></html>");
        StringBuilder sb = new StringBuilder("<html><body style='width:215px'>").append(wiring.summary());
        for (Wiring.Link l : wiring.links()) sb.append("<br>✓ ").append(esc(l.toString()));
        for (Wiring.Link l : Wiring.planned()) sb.append("<br>· ").append(esc(l.toString()));
        txt(linksLbl, sb.append("</body></html>").toString().replace("→", "&rarr;"));
        StringBuilder h = new StringBuilder("<html><body style='width:215px'>");
        for (String l : r.hudLines()) h.append(esc(l.startsWith("!") ? l.substring(1) : l)).append("<br>");
        txt(hudLbl, h.append("</body></html>").toString());
        datenZeilen();
    }

    // ============================================================== Phase 15: EIS, CHRONIK, DATEN

    private com.dan.glacier.data.Chronik chronik;
    private final JLabel eisLbl = small(""), chronikLbl = small(""), heuteLbl = small(""), datenLbl = small(""), presetLbl = small(""), protLbl = small(""),
            gemerktLbl = small("");
    private FComboBox kalbBox, paketBox, presetBox, sitzungBox, gemerktBox;
    private java.util.List<com.dan.glacier.data.Daten.PaketPreset> presetListe = java.util.List.of();
    private java.util.List<com.dan.glacier.data.Daten.Ansicht> gemerktListe = java.util.List.of();
    private volatile String datenNote = "";
    private long naechsteListe;

    /** Phase 15: die Chronik der laufenden Szene (aus der App, sobald sie steht). */
    public void chronik(com.dan.glacier.data.Chronik c) {
        chronik = c;
        neuLesen();
    }

    private static com.dan.glacier.data.Speicher sp() { return com.dan.glacier.data.Datenseite.speicher(); }

    /** Eine Combo neu füllen, ohne dass ihr Hörer feuert; die Auswahl bleibt, wenn es den Eintrag noch gibt. */
    private void fuellen(FComboBox box, java.util.List<?> l, String wahl) {
        fromScene = true;
        try {
            box.removeAllItems();
            int k = -1;
            for (int i = 0; i < l.size(); i++) { box.addItem(l.get(i)); if (l.get(i).toString().equals(wahl)) k = i; }
            if (k >= 0) box.setSelectedIndex(k); else if (!l.isEmpty()) box.setSelectedIndex(0);
        } finally { fromScene = false; }
    }

    private static String gewaehlt(FComboBox box) { Object o = box.getSelectedItem(); return o == null ? null : o.toString(); }

    /** Im Faden der Daten arbeiten, danach auf dem Swing-Faden weiter; Fehler landen im Protokoll und in einer Meldung. */
    private <T> void hinten(String was, java.util.concurrent.Callable<T> arbeit, java.util.function.Consumer<T> danach) {
        com.dan.store.Dienst.spaeter(() -> {
            try {
                T t = arbeit.call();
                javax.swing.SwingUtilities.invokeLater(() -> danach.accept(t));
            } catch (Exception ex) {
                com.dan.store.Protokoll.fehler("Bedienfeld", was + ": " + ex.getMessage(), ex instanceof RuntimeException ? ex : null);
                javax.swing.SwingUtilities.invokeLater(() -> com.dan.foptionpane.FOptionPane.showMessageDialog(this, was + " ging nicht:\n" + ex.getMessage(),
                        "Glacier · " + was, com.dan.foptionpane.FOptionPane.ERROR_MESSAGE));
            }
        });
    }

    /** Block EIS (Abschnitt 7): Vorstoß von Hand, Schmelzkerbe, Festigkeit. */
    private void eisBlock() {
        block("EIS", false);
        put(eisLbl);
        FCheckBox hand = new FCheckBox("Vorstoß von Hand (sonst der Jahresgang)");
        FSlider vs = new FSlider(-60, 60, 0);
        hand.addActionListener(e -> { r.vorstossHand = hand.isSelected() ? vs.getValue() : Double.NaN; view.requestFocusInWindow(); });
        vs.addChangeListener(e -> { if (hand.isSelected()) r.vorstossHand = vs.getValue(); });
        put(hand);
        put(vs);
        put(label("Schmelzkerbe an der Wasserlinie (m je Szenenminute × 10)"));
        FSlider kerbe = new FSlider(0, 30, 6);
        kerbe.addChangeListener(e -> { com.dan.glacier.scene.Kalbung k = r.kalbung; if (k != null) k.notchPerMinute = kerbe.getValue() / 10.0; });
        put(kerbe);
        put(label("Zugfestigkeit des Eises (kPa; neue Abschnitte)"));
        FSlider fest = new FSlider(100, 3000, (int) Math.round(r.bruch.material.tensile / 1000));
        fest.addChangeListener(e -> r.bruch.material.tensile = fest.getValue() * 1000.0);
        put(fest);
        put(small("<html><body style='width:215px'>Der Vorstoß folgt sonst dem Jahresgang (±40 m [G]). Die Kerbe frisst sich an der Wasserlinie ins Eis; "
                + "je tiefer, desto eher bricht die Säule darüber. Die Festigkeit gilt für Abschnitte, die danach in Zellen zerlegt werden; gleicher Seed, "
                + "gleiche Zellen, gleicher Riss. Ein Klick auf die Front zeigt die Säulenkarte, ein Klick auf einen Eisberg seine Karte.</body></html>"));
    }

    /** Block CHRONIK (F36 A): Zähler, letzte Kalbungen, Front merken, Tageskarte, Chronik leeren. */
    private void chronikBlock() {
        block("CHRONIK", true);
        put(chronikLbl);
        put(heuteLbl);
        sub("Letzte Kalbungen");
        kalbBox = new FComboBox();
        put(kalbBox);
        JPanel g1 = grid(2);
        FButton hin = new FButton("Hinfliegen"), merken = new FButton("Front merken");
        hin.setToolTipText("Die Kamera kreist über dem Ort der gewählten Kalbung");
        hin.addActionListener(e -> {
            Object o = kalbBox.getSelectedItem();
            if (o instanceof com.dan.glacier.data.Daten.KalbungKurz k) {
                r.camera.stopFahrt();
                r.camera.mode = Camera.Mode.KREISEN;
                r.camera.lookAt(k.x(), r.camera.waterLevel + 30, k.z(), r.camera.heading(), 16, 480);
                free.setSelected(false);
            }
            view.requestFocusInWindow();
        });
        merken.setToolTipText("Die Front jetzt als Linie in die Chronik (Art MERKEN)");
        merken.addActionListener(e -> { if (chronik != null) chronik.frontMerken(); view.requestFocusInWindow(); });
        g1.add(hin);
        g1.add(merken);
        put(g1);
        JPanel g2 = grid(2);
        FButton karte = new FButton("Tageskarte"), schreib = new FButton("Chronik aus");
        karte.setToolTipText("Draufsicht des Szenentags als PNG nach docs/status: Fronten, Kalbungen, Risse, Wege der Eisberge (wie GLC_TAGESKARTE_V in QGIS)");
        karte.addActionListener(e -> {
            java.time.LocalDate tag = clock.date();
            if (chronik != null) chronik.abwarten();
            hinten("Tageskarte", () -> com.dan.glacier.data.Tageskarte.zeichnen(sp(), r.terrain, tag, r.photoDir != null ? r.photoDir : new java.io.File("docs/status")), k -> {
                r.hudMeldung("Tageskarte: " + k.datei().getPath());
                heuteLbl.setText(String.format(Locale.GERMAN, "<html><body style='width:215px'>Tageskarte %s: %d Kalbungen, %,.0f m³, %d Eisberge, %d Fronten · %s</body></html>",
                        tag, k.kalbungen(), k.volumenM3(), k.eisberge(), k.fronten(), k.datei().getName()));
            });
        });
        schreib.setToolTipText("Die Chronik anhalten oder wieder schreiben lassen (die Szene läuft weiter)");
        schreib.addActionListener(e -> {
            if (chronik == null) return;
            chronik.schreiben(!chronik.schreibt());
            schreib.setText(chronik.schreibt() ? "Chronik aus" : "Chronik an");
        });
        g2.add(karte);
        g2.add(schreib);
        put(g2);
        sub("Chronik leeren (eigene Zeilen einer Sitzung)");
        sitzungBox = new FComboBox();
        put(sitzungBox);
        JPanel g3 = grid(1);
        FButton leeren = new FButton("Leeren …");
        leeren.addActionListener(e -> {
            Object o = sitzungBox.getSelectedItem();
            if (!(o instanceof Sitzungszeile s)) return;
            if (com.dan.foptionpane.FOptionPane.showConfirmDialog(this, "Alle Kalbungen, Eisberge, Fronten und Läufe der Sitzung " + s.id + " löschen?\n" + s,
                    "Glacier · Chronik leeren", com.dan.foptionpane.FOptionPane.YES_NO_OPTION) != com.dan.foptionpane.FOptionPane.YES_OPTION) return;
            hinten("Chronik leeren", () -> sp().chronikLeeren(s.id), n -> { r.hudMeldung("Chronik der Sitzung " + s.id + ": " + n + " Zeilen gelöscht"); neuLesen(); });
        });
        g3.add(leeren);
        put(g3);
        put(small("<html><body style='width:215px'>Die Chronik schreibt jede Kalbung (Ort, Riss, Volumen, Fallzeit, Welle am Steg nach 60 s), jeden Eisberg ab "
                + "200 m³ mit seinem Weg, die Front einmal je Szenentag um 12 Uhr und nach großen Abbrüchen, jeden Lauf der Ruptura und der Regiebücher. "
                + "In QGIS: Ebene GLC_TAGESKARTE_V (Geometrie GEOM, Schlüssel OBJEKT_ID), Filter auf TAG; ohne Datenbank daten/chronik/*.csv mit Spalte WKT.</body></html>"));
    }

    /** Eine Sitzung mit Chronik in der Auswahl. */
    private record Sitzungszeile(long id, long beginn, long kalbungen, long eisberge) {
        @Override public String toString() {
            String w = beginn <= 0 ? "" : java.time.format.DateTimeFormatter.ofPattern("dd.MM. HH:mm").format(java.time.Instant.ofEpochMilli(beginn).atZone(java.time.ZoneId.systemDefault()));
            return "Sitzung " + id + " · " + w + " · " + kalbungen + " Kalbungen, " + eisberge + " Eisberge" + (id == com.dan.store.Dienst.sitzung() ? " (diese)" : "");
        }
    }

    /** Block DATEN: Speicherort, Verbinden, Einrichten, Prüfen, Weiter wie zuletzt, Übernahme, Presets, gemerkte Ansichten, Protokoll. */
    private void datenBlock() {
        block("DATEN", false);
        put(datenLbl);
        JPanel g = new JPanel(new java.awt.GridLayout(0, 2, 6, 4));
        g.setOpaque(false);
        FButton verb = new FButton("Verbinden …"), einr = new FButton("Einrichten"), pruef = new FButton("Prüfen"), ohne = new FButton("Ohne Datenbank"),
                weiter = new FButton("Weiter wie zuletzt"), ueber = new FButton("Übernehmen");
        verb.setToolTipText("Mit dem Schema aus db/db.properties verbinden (fragt nach dem Passwort, wenn keins hinterlegt ist)");
        verb.addActionListener(e -> verbinden());
        einr.setToolTipText("Die Skripte aus db/sql ausführen und alles prüfen; löscht nie etwas");
        einr.addActionListener(e -> einrichter(false));
        pruef.setToolTipText("Nur prüfen: Tabellen, Spalten, Sichten, Metadaten, räumliche Indizes, Zahl der Startdaten");
        pruef.addActionListener(e -> einrichter(true));
        ohne.setToolTipText("Mit Dateien in daten/ weiterarbeiten");
        ohne.addActionListener(e -> {
            com.dan.glacier.data.Datenseite.setzen(new com.dan.glacier.data.DateiSpeicher(com.dan.glacier.data.DateiSpeicher.ordner()));
            neuLesen();
        });
        weiter.setToolTipText("Die letzte gesicherte Sitzung herstellen: Uhr, Wetter, Kamera, Regiebuch, Damm und Ruptura, die offenen Eisberge");
        weiter.addActionListener(e -> weiterWieZuletzt());
        ueber.setToolTipText("Chronik, eigene Regiebücher, Ansichten und Presets aus daten/ in die Datenbank (die CSV heißen danach *.uebernommen)");
        ueber.addActionListener(e -> uebernehmen());
        g.add(verb); g.add(ohne); g.add(einr); g.add(pruef); g.add(weiter); g.add(ueber);
        put(g);
        sub("Presets der Packages");
        java.util.List<String> pk = new java.util.ArrayList<>();
        for (String k : com.dan.glacier.data.Startdaten.PAKETE) pk.add(k.substring(k.lastIndexOf('.') + 1) + " · " + k.substring(0, k.lastIndexOf('.')));
        paketBox = new FComboBox(pk.toArray());
        presetBox = new FComboBox();
        paketBox.addActionListener(e -> { if (!fromScene) listePresets(null); });
        put(paketBox);
        put(presetBox);
        FButton an = new FButton("Anwenden"), unter = new FButton("Speichern unter …");
        an.setToolTipText("Alle Felder des Presets in das laufende Objekt der Szene schreiben");
        an.addActionListener(e -> presetAnwenden());
        unter.setToolTipText("Die Felder, wie sie jetzt in der Szene stehen, als eigenes Preset");
        unter.addActionListener(e -> presetSpeichern());
        JPanel pg = grid(2);
        pg.add(an);
        pg.add(unter);
        put(pg);
        put(presetLbl);
        sub("Gemerkte Ansichten (Taste V merkt)");
        gemerktBox = new FComboBox();
        put(gemerktBox);
        FButton gh = new FButton("Hin"), gl = new FButton("Löschen");
        gh.addActionListener(e -> gemerkteAnsicht());
        gl.addActionListener(e -> {
            int i = gemerktBox.getSelectedIndex();
            if (i < 0 || i >= gemerktListe.size()) return;
            long id = gemerktListe.get(i).id();
            hinten("Ansicht löschen", () -> { sp().loeschenAnsicht(id); return null; }, x -> listeGemerkt());
        });
        JPanel gg = grid(2);
        gg.add(gh);
        gg.add(gl);
        put(gg);
        put(gemerktLbl);
        view.setKeyAction(java.awt.event.KeyEvent.VK_V, () -> javax.swing.SwingUtilities.invokeLater(this::ansichtMerken));
        sub("Protokoll");
        put(protLbl);
        put(small("<html><body style='width:215px'>Das Protokoll geht alle 5 s in GLC_LOG (90 Tage) oder daten/protokoll.txt, der Zustand alle 2 min und beim Ende in "
                + "GLC_SITZUNG oder daten/sitzungen. Schema GLACIER auf PDBORCL, SRID 8307.</body></html>"));
    }

    private String paket() { String s = gewaehlt(paketBox); return s == null ? null : s.substring(s.indexOf(" · ") + 3); }
    private String klasse() { String s = gewaehlt(paketBox); return s == null ? null : s.substring(0, s.indexOf(" · ")); }

    private void listePresets(String wahl) {
        String p = paket(), k = klasse();
        if (p == null) return;
        hinten("Presets lesen", () -> sp().presets(p, k), l -> {
            presetListe = l;
            fuellen(presetBox, l, wahl);
            Object o = com.dan.glacier.data.Zustand.objekt(r, k);
            presetLbl.setText("<html><body style='width:215px'>" + l.size() + " Presets · " + (o == null ? "nicht in der Szene" : "in der Szene: " + o.getClass().getSimpleName()) + "</body></html>");
        });
    }

    private void presetAnwenden() {
        int i = presetBox.getSelectedIndex();
        if (i < 0 || i >= presetListe.size()) return;
        var pr = presetListe.get(i);
        Object o = com.dan.glacier.data.Zustand.objekt(r, pr.klasse());
        if (o == null) { presetLbl.setText(pr.klasse() + " läuft nicht in der Szene."); return; }
        java.util.List<String> f = com.dan.store.Felder.schreiben(o, pr.felder());
        presetLbl.setText("<html><body style='width:215px'>„" + pr.name() + "“ angewandt: " + (pr.felder().size() - f.size()) + " Felder"
                + (f.isEmpty() ? "" : ", nicht: " + String.join(", ", f)) + "</body></html>");
        com.dan.store.Protokoll.info("Preset", pr.klasse() + " ← „" + pr.name() + "“");
        view.requestFocusInWindow();
    }

    private void presetSpeichern() {
        String p = paket(), k = klasse();
        Object o = com.dan.glacier.data.Zustand.objekt(r, k);
        if (o == null) { presetLbl.setText(k + " läuft nicht in der Szene."); return; }
        String n = com.dan.foptionpane.FOptionPane.showInputDialog(this, "Name des Presets für " + k, "Glacier · Preset speichern", com.dan.foptionpane.FOptionPane.QUESTION_MESSAGE);
        if (n == null || n.isBlank()) return;
        var pr = new com.dan.glacier.data.Daten.PaketPreset(p, k, n.strip(), "Aus der Szene am " + java.time.LocalDate.now(), com.dan.glacier.data.Daten.EIGEN, com.dan.store.Felder.lesen(o));
        hinten("Preset speichern", () -> { sp().speichern(pr); return null; }, x -> listePresets(pr.name()));
    }

    private void listeGemerkt() {
        hinten("Ansichten lesen", () -> sp().ansichten(), l -> {
            java.util.List<com.dan.glacier.data.Daten.Ansicht> g = new java.util.ArrayList<>();
            for (var a : l) if ("GEMERKT".equals(a.art())) g.add(a);
            gemerktListe = g;
            fuellen(gemerktBox, g, null);
            gemerktLbl.setText(g.size() + " gemerkte Ansichten in " + sp().wo());
        });
    }

    private void ansichtMerken() {
        double[] k = com.dan.glacier.data.Zustand.kamera(r);
        java.time.LocalDateTime t = com.dan.glacier.data.Daten.szenenzeit(clock.epochMillis());
        String name = com.dan.glacier.camera.Ansichten.zone(k[0], k[2]) + " · " + com.dan.glacier.data.Daten.text(t);
        String z = com.dan.store.Felder.alsText(com.dan.glacier.data.Zustand.erfassen(r, r.tonAn()), "Glacier · gemerkte Ansicht");
        boolean nass = r.camera.diving && k[1] < r.camera.waterLevel;
        r.grabRequest = img -> {
            byte[] png = null;
            try {
                java.awt.image.BufferedImage s = new java.awt.image.BufferedImage(320, (int) Math.round(320.0 * img.getHeight() / img.getWidth()), java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g2 = s.createGraphics();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(img, 0, 0, s.getWidth(), s.getHeight(), null);
                g2.dispose();
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(s, "png", bo);
                png = bo.toByteArray();
            } catch (Exception ex) { /* ohne Vorschau */ }
            byte[] v = png;
            var a = new com.dan.glacier.data.Daten.Ansicht(0, name, null, "GEMERKT", k, null, nass, t, z, v, com.dan.glacier.data.Daten.EIGEN);
            long s = com.dan.store.Dienst.sitzung();
            hinten("Ansicht merken", () -> sp().speichern(a, s >= 0 ? s : null), id -> { r.hudMeldung("Ansicht gemerkt: " + name); listeGemerkt(); });
        };
    }

    private void gemerkteAnsicht() {
        int i = gemerktBox.getSelectedIndex();
        if (i < 0 || i >= gemerktListe.size()) return;
        long id = gemerktListe.get(i).id();
        hinten("Ansicht lesen", () -> sp().ansicht(id), a -> {
            double[] k = a.kamera();
            r.camera.stopFahrt();
            r.camera.stopMitfahrt();
            r.camera.diving = a.unterWasser();
            r.camera.place(k[0], k[1], k[2], k[3], k[4]);
            r.camera.snap();
            if (a.szenenzeit() != null) r.clock.set(a.szenenzeit().toLocalDate(), a.szenenzeit().getHour() + a.szenenzeit().getMinute() / 60.0);
            free.setSelected(true);
            view.requestFocusInWindow();
        });
    }

    /** Nach einem Wechsel des Speichers alle Listen neu lesen. */
    private void neuLesen() {
        listePresets(null);
        listeGemerkt();
        naechsteListe = 0;
        technik();
    }

    private void verbinden() {
        datenNote = "verbinde …";
        hinten("Verbinden", com.dan.glacier.data.Verbindung::konfig, k -> {
            com.dan.store.Db.Konfig kk = k;
            char[] neu = null;
            if (!k.hatPasswort()) {
                com.dan.ftextfield.FPasswordfield pw = new com.dan.ftextfield.FPasswordfield();
                JPanel p = new JPanel(new BorderLayout(0, 6));
                p.add(new JLabel("Passwort für " + k.kurz() + ":"), BorderLayout.NORTH);
                p.add(pw, BorderLayout.CENTER);
                if (com.dan.foptionpane.FOptionPane.showConfirmDialog(this, p, "Glacier · Datenbank", com.dan.foptionpane.FOptionPane.OK_CANCEL_OPTION)
                        != com.dan.foptionpane.FOptionPane.OK_OPTION || pw.getPassword().length == 0) { datenNote = ""; return; }
                neu = pw.getPassword();
                kk = k.mitPasswort(neu);
            }
            char[] eingabe = neu;
            com.dan.store.Db.Konfig kf = kk;
            hinten("Verbinden", () -> {
                String[] grund = {""};
                com.dan.glacier.data.DbSpeicher d = com.dan.glacier.data.Datenseite.verbinden(kf, grund);
                if (d == null) throw new com.dan.store.Ablage.Fehler(grund[0]);
                if (eingabe != null) try { com.dan.store.Db.passwortSpeichern(kf, eingabe); } catch (Exception ex) { com.dan.store.Protokoll.warnung("Daten", "Passwort nicht eingetragen: " + ex.getMessage()); }
                int st = com.dan.glacier.data.Verbindung.einrichter().stufe(d.verbindung());
                return new Object[]{d, st};
            }, o -> {
                var d = (com.dan.glacier.data.DbSpeicher) o[0];
                int st = (Integer) o[1];
                if (st < com.dan.glacier.data.Verbindung.VERSION) {
                    if (com.dan.foptionpane.FOptionPane.showConfirmDialog(this, d.wo() + " ist noch nicht eingerichtet (Stufe " + st + ").\nJetzt einrichten? (löscht nie etwas)",
                            "Glacier · Datenbank", com.dan.foptionpane.FOptionPane.YES_NO_OPTION) != com.dan.foptionpane.FOptionPane.YES_OPTION) { d.schliessen(); datenNote = "nicht eingerichtet"; return; }
                    com.dan.glacier.data.Datenseite.setzen(d);
                    einrichter(false);
                    return;
                }
                com.dan.glacier.data.Datenseite.setzen(d);
                datenNote = "verbunden";
                neuLesen();
            });
        });
    }

    private void einrichter(boolean nurPruefen) {
        if (!(sp() instanceof com.dan.glacier.data.DbSpeicher d)) {
            com.dan.foptionpane.FOptionPane.showMessageDialog(this, "Erst mit der Datenbank verbinden („Verbinden …“).", "Glacier · Einrichter", com.dan.foptionpane.FOptionPane.ERROR_MESSAGE);
            return;
        }
        datenNote = nurPruefen ? "prüfe …" : "richte ein …";
        hinten(nurPruefen ? "Prüfen" : "Einrichten", () -> {
            var e = com.dan.glacier.data.Verbindung.einrichter();
            return nurPruefen ? e.pruefen(d.verbindung(), com.dan.glacier.data.Verbindung.skripte())
                    : e.einrichten(d.verbindung(), com.dan.glacier.data.Verbindung.skripte(), (t, a) -> datenNote = String.format(Locale.GERMAN, "%.0f %% · %s", a * 100, t));
        }, er -> {
            Startbild.merkeEinrichter(er.kurz());
            datenNote = "";
            if (er.ok()) com.dan.store.Dienst.spaeter(com.dan.store.Dienst::neuBeginnen);
            com.dan.store.Protokoll.info("Einrichter", (nurPruefen ? "Prüfen: " : "Einrichten: ") + er.kurz());
            for (String z : er.zeilen()) if (!z.startsWith("ok") && !z.contains("alles in Ordnung")) com.dan.store.Protokoll.warnung("Einrichter", z);
            JLabel t = small("<html><body style='width:560px'>" + String.join("<br>", er.zeilen()) + "</body></html>");
            javax.swing.JScrollPane spn = new javax.swing.JScrollPane(t);
            spn.setPreferredSize(new Dimension(640, 420));
            com.dan.foptionpane.FOptionPane.showMessageDialog(this, spn, "Glacier · " + (nurPruefen ? "Prüfen: " : "Einrichten: ") + er.kurz(),
                    er.ok() ? com.dan.foptionpane.FOptionPane.INFORMATION_MESSAGE : com.dan.foptionpane.FOptionPane.WARNING_MESSAGE);
            neuLesen();
        });
    }

    private void uebernehmen() {
        if (!sp().datenbank()) {
            com.dan.foptionpane.FOptionPane.showMessageDialog(this, "Erst mit der Datenbank verbinden („Verbinden …“).", "Glacier · Übernehmen", com.dan.foptionpane.FOptionPane.ERROR_MESSAGE);
            return;
        }
        var von = new com.dan.glacier.data.DateiSpeicher(com.dan.glacier.data.DateiSpeicher.ordner());
        datenNote = "übernehme …";
        hinten("Übernehmen", () -> com.dan.glacier.data.Uebernahme.uebernehmen(von, sp()), z -> {
            datenNote = "übernommen";
            com.dan.foptionpane.FOptionPane.showMessageDialog(this, "Aus " + von.wo() + " nach " + sp().wo() + ":\n" + z, "Glacier · Übernahme",
                    com.dan.foptionpane.FOptionPane.INFORMATION_MESSAGE);
            neuLesen();
        });
    }

    private void weiterWieZuletzt() {
        hinten("Weiter wie zuletzt", () -> {
            var s = sp().letzteSitzung(com.dan.store.Dienst.sitzung());
            if (s == null || s.zustand() == null) throw new com.dan.store.Ablage.Fehler("Keine gesicherte Sitzung in " + sp().wo());
            return s;
        }, s -> { Weiter.jetzt(r, s, chronik); view.setMode(r.camera.mode); syncFromScene(); });
    }

    /** Alle 1 s (technik): Zeilen von EIS, CHRONIK und DATEN; alle 10 s die Listen aus dem Speicher. */
    private void datenZeilen() {
        var g = r.gletscher();
        var k = r.kalbung;
        txt(eisLbl, "<html><body style='width:215px'>" + (g == null ? "Gletscher: wird gebaut" : String.format(Locale.GERMAN, "Vorstoß %+.1f m (%s) · Front %.1f km, Oberkante %.0f m · "
                + "Kerbe %.1f m je Szenenminute · Zugfestigkeit %.0f kPa", g.glacier.advance(), Double.isNaN(r.vorstossHand) ? "Jahresgang" : "von Hand", g.glacier.frontLength() / 1000,
                g.glacier.frontTop(), k == null ? 0 : k.notchPerMinute, r.bruch.material.tensile / 1000)) + "</body></html>");
        var c = chronik;
        txt(chronikLbl, "<html><body style='width:215px'>" + esc(c == null ? "Chronik: startet mit der Szene" : c.describe()) + " · " + esc(sp().wo()) + "</body></html>");
        long gs = com.dan.store.Dienst.gesichert();
        txt(datenLbl, "<html><body style='width:215px'>" + esc(sp().wo()) + (datenNote.isEmpty() ? "" : " · " + esc(datenNote))
                + "<br>Sitzung " + com.dan.store.Dienst.sitzung() + (gs > 0 ? ", gesichert " + hm(gs).replace(" ART", "") + " Uhr (Rechner)" : ", noch nicht gesichert")
                + (com.dan.glacier.data.Datenseite.einrichterStand.isEmpty() ? "" : "<br>Einrichter: " + esc(com.dan.glacier.data.Datenseite.einrichterStand))
                + (com.dan.store.Dienst.letzterFehler().isEmpty() ? "" : "<br>Zuletzt: " + esc(com.dan.store.Dienst.letzterFehler())) + "</body></html>");
        long now = System.currentTimeMillis();
        if (now < naechsteListe || kalbBox == null) return;
        naechsteListe = now + 10_000;
        java.time.LocalDate tag = clock.date();
        com.dan.store.Dienst.spaeter(() -> {
            try {
                var sp = sp();
                var heute = sp.kalbungen(tag.atStartOfDay(), tag.plusDays(1).atStartOfDay(), 500);
                var letzte = sp.kalbungen(null, null, 12);
                var sitz = sp.sitzungenMitChronik(12);
                var prot = sp.letzteZeilen(6);
                int[] n = new int[3];
                double vol = 0;
                for (var x : heute) { n[x.art().equals("TURM") ? 0 : x.art().equals("SCHEIBE") ? 1 : 2]++; vol += x.volumenM3(); }
                String h = String.format(Locale.GERMAN, "<html><body style='width:215px'>Heute (%s): %d Türme, %d Scheiben, %d Füße · %,.0f m³</body></html>",
                        tag, n[0], n[1], n[2], vol);
                java.util.List<Sitzungszeile> sl = new java.util.ArrayList<>();
                for (long[] s : sitz) sl.add(new Sitzungszeile(s[0], s[1], s[2], s[3]));
                StringBuilder pb = new StringBuilder("<html><body style='width:215px'>");
                for (String z : prot) pb.append(esc(z.length() > 140 ? z.substring(0, 140) + " …" : z)).append("<br>");
                String ps = pb.append("</body></html>").toString();
                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (popupOpen(this) || menuOpen()) return;
                    if (!heuteLbl.getText().startsWith("<html><body style='width:215px'>Tageskarte")) txt(heuteLbl, h);
                    String wk = gewaehlt(kalbBox), ws = gewaehlt(sitzungBox);
                    fuellen(kalbBox, letzte, wk);
                    fuellen(sitzungBox, sl, ws);
                    txt(protLbl, ps);
                });
            } catch (Exception ex) {
                javax.swing.SwingUtilities.invokeLater(() -> txt(heuteLbl, "Chronik nicht lesbar: " + ex.getMessage()));
            }
        });
    }

    private final java.util.Map<FCollapsiblePanel, Integer> hoehe = new java.util.HashMap<>();

    /**
     * Befund Phase 3 (wie Haithabu Phase 14): FCollapsiblePanel merkt sich die Höhe des Inhalts beim Aufklappen. Wachsen
     * danach Zeilen (Regie, Himmel), schneidet der Block unten ab und die Schalter darunter sind nicht zu erreichen. Darum
     * alle halbe Sekunde nachmessen und offene Blöcke auf die neue Höhe setzen.
     */
    private void nachmessen() {
        boolean changed = false;
        for (Object[] b : bloecke) {
            FCollapsiblePanel p = (FCollapsiblePanel) b[0];
            if (!p.isExpanded()) { hoehe.remove(p); continue; }
            JComponent body = (JComponent) b[1];
            body.invalidate();
            int h = body.getPreferredSize().height;
            Integer old = hoehe.put(p, h);
            if (old == null || old != h) { p.setExpanded(true, false); changed = true; }
        }
        if (changed) { revalidate(); repaint(); }
    }

    /** Ist irgendwo im Bedienfeld eine Auswahlliste aufgeklappt? */
    private static boolean popupOpen(java.awt.Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof javax.swing.JComboBox<?> b && b.isPopupVisible()) return true;
            if (k instanceof java.awt.Container cc && popupOpen(cc)) return true;
        }
        return false;
    }

    /** Ist irgendein Aufklappmenü offen (auch die Liste einer Auswahl)? Dann darf sich nichts neu auslegen. */
    public static boolean menuOpen() { return javax.swing.MenuSelectionManager.defaultManager().getSelectedPath().length > 0; }

    /** Text nur setzen, wenn er sich ändert (sonst legt Swing den Block bei jedem Takt neu aus). */
    private static void txt(JLabel l, String t) { if (!t.equals(l.getText())) l.setText(t); }

    private static String esc(String t) { return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    static String hm(double epochMs) {
        if (Double.isNaN(epochMs)) return "—";
        return String.format("%tR", Instant.ofEpochMilli((long) epochMs).atZone(SceneClock.ZONE));
    }

    static String compass(double deg) {
        String[] n = {"Nord", "Nordnordost", "Nordost", "Ostnordost", "Ost", "Ostsüdost", "Südost", "Südsüdost",
                "Süd", "Südsüdwest", "Südwest", "Westsüdwest", "West", "Westnordwest", "Nordwest", "Nordnordwest"};
        return n[(int) Math.round(((deg % 360) + 360) % 360 / 22.5) % 16];
    }

    // ------------------------------------------------------------ Aufbau

    /** Ein Klappblock (FCollapsiblePanel); offen bleibt, was zuletzt offen war. */
    private void block(String title, boolean offen) {
        FCollapsiblePanel p = new FCollapsiblePanel() {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setTitle(title);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel inhalt = new JPanel();
        inhalt.setOpaque(false);
        inhalt.setLayout(new BoxLayout(inhalt, BoxLayout.Y_AXIS));
        inhalt.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 8));
        bloecke.add(new Object[]{p, inhalt});
        String key = "offen." + title;
        boolean auf = PREFS == null ? offen : PREFS.getBoolean(key, offen);
        if (!auf) zu.add(p);
        p.addFCollapsiblePanelListener((panel, expanded) -> {
            if (PREFS != null) try { PREFS.putBoolean(key, expanded); } catch (RuntimeException ex) { /* nicht gemerkt */ }
        });
        if (getComponentCount() > 0) add(Box.createVerticalStrut(6));
        add(p);
        target = inhalt;
    }

    /** Eine Zwischenüberschrift im Block. */
    private void sub(String text) {
        JLabel h = new JLabel(text);
        h.setForeground(Theme.accent());
        h.setFont(new Font("SansSerif", Font.BOLD, 11));
        h.setBorder(BorderFactory.createEmptyBorder(10, 0, 3, 0));
        put(h);
    }

    private void put(Component c) { target.add(row(c)); }

    /** Ein Schalter für eine Ebene des Bildes. */
    private void layer(JPanel p, String name, boolean on, java.util.function.Consumer<Boolean> set) {
        FCheckBox b = new FCheckBox(name);
        b.setSelected(on);
        b.addActionListener(e -> { set.accept(b.isSelected()); view.requestFocusInWindow(); });
        p.add(b);
    }

    private static JPanel grid(int n) {
        JPanel p = new JPanel(new java.awt.GridLayout(1, n, 6, 0));
        p.setOpaque(false);
        return p;
    }

    /** Eine Zeile, die mit ihrem Inhalt wächst. */
    private static JComponent row(Component c) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        p.add(c, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    static JLabel label(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Theme.ink());
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        return l;
    }

    static JLabel small(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Theme.muted());
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        return l;
    }

    // ------------------------------------------------------------ Scrollable: nur senkrecht
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(java.awt.Rectangle rr, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(java.awt.Rectangle rr, int o, int d) { return 120; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
