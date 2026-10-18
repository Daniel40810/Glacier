package com.dan.glacier.test;

import com.dan.glacier.data.DateiSpeicher;
import com.dan.glacier.data.Daten;
import com.dan.glacier.data.DbSpeicher;
import com.dan.glacier.data.Speicher;
import com.dan.glacier.data.Startdaten;
import com.dan.glacier.data.Tageskarte;
import com.dan.glacier.data.Uebernahme;
import com.dan.glacier.data.Verbindung;
import com.dan.glacier.scene.Terrain;
import com.dan.store.Ablage;
import com.dan.store.Db;
import com.dan.store.Einrichter;
import com.dan.store.Felder;
import com.dan.store.Protokoll;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 15: die Prüfung gegen die echte Datenbank (PDBORCL, Schema GLACIER aus {@code db/db.properties}). In NetBeans:
 * Rechtsklick, „Run File“. Sie richtet ein (wiederholbar), läuft den Einrichter ein zweites Mal (muss ohne Fehler
 * durchgehen), prüft mit ihm, rechnet Fronten und Ausschnitt auf dem Ellipsoid nach, prüft alle Geometrien auf
 * Gültigkeit, schreibt eine Chronik für den 31.12.2099 (Front, Kalbungen mit Riss, Welle, Eisberge mit Weg, Lauf der
 * Ruptura, Lauf eines Regiebuchs mit Blatt) und liest sie über die Sichten und als Tageskarte zurück, speichert
 * Regiebuch, Ansicht und Preset unter „~Prüfung …“, sichert eine Sitzung, übernimmt Dateien und räumt hinterher auf.
 * Ende: „Alles in Ordnung.“
 * <p>
 * Die Ausgabe bitte als Text oder Bildschirmfoto schicken; jede Zeile mit FEHLER sagt, was nicht ging.
 */
public final class DbPruefung {
    private static int ok, bad;
    private static final String P = "~Prüfung ";
    /** Der Tag der Prüfchronik: kommt in keiner Szene vor. */
    static final LocalDate TAG = LocalDate.of(2099, 12, 31);

    static void check(boolean c, String was) {
        if (c) ok++; else bad++;
        System.out.println((c ? "  ok     " : "  FEHLER ") + was);
    }

    static double zahl(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery(sql)) { return r.next() ? r.getDouble(1) : Double.NaN; }
    }

    static String text(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery(sql)) { return r.next() ? r.getString(1) : null; }
    }

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        System.out.println("Glacier · Prüfung der Datenbank (Phase 15)");
        Db.Konfig k = Verbindung.konfig();
        System.out.println("Verbindung " + k.kurz());
        if (!k.hatPasswort()) {
            System.out.println("  FEHLER Kein Passwort in db/db.properties und keine Umgebungsvariable " + Verbindung.ENV
                    + ". Einmal mit dem Startbild verbinden (es trägt das Passwort ein) oder es von Hand eintragen.");
            System.exit(1);
        }
        DbSpeicher db = new DbSpeicher(k);
        System.out.println(db.wo());
        Connection c = db.verbindung();
        Einrichter ein = Verbindung.einrichter();

        System.out.println("Einrichter");
        Einrichter.Ergebnis e1 = ein.einrichten(c, Verbindung.skripte(), (t, a) -> { });
        for (String z : e1.zeilen()) if (!z.startsWith("ok")) System.out.println("    " + z);
        check(e1.ok(), "Erster Lauf: " + e1.kurz() + " (" + e1.ausgefuehrt() + " ausgeführt, " + e1.vorhanden() + " schon da)");
        Einrichter.Ergebnis e2 = ein.einrichten(c, Verbindung.skripte(), (t, a) -> { });
        check(e2.ok() && e2.fehler() == 0, "Zweiter Lauf geht ohne Fehler durch (wiederholbar): " + e2.kurz());
        Einrichter.Ergebnis pr = ein.pruefen(c, Verbindung.skripte());
        for (String z : pr.zeilen()) System.out.println("    " + z);
        check(pr.ok(), "Einrichter prüft: " + pr.kurz());
        check(ein.stufe(c) == Verbindung.VERSION, "Ausbaustufe " + ein.stufe(c));

        System.out.println("Trigger und Spatial");
        try (Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO GLC_WETTERLAGE (NAME, HERKUNFT) VALUES ('" + P + "Trigger', 'EIGEN')");
            try (ResultSet r = st.executeQuery("SELECT ID, ANGELEGT, GEAENDERT FROM GLC_WETTERLAGE WHERE NAME = '" + P + "Trigger'")) {
                check(r.next() && r.getLong(1) > 0 && r.getTimestamp(2) != null && r.getTimestamp(3) != null, "Trigger setzt ID, ANGELEGT und GEAENDERT");
            }
            st.executeUpdate("DELETE FROM GLC_WETTERLAGE WHERE NAME = '" + P + "Trigger'");
        }
        Terrain t = Terrain.load(new File("daten/gelaende"));
        Startdaten.Szene sz = com.dan.glacier.tools.StartdatenSql.szene(new File("daten/gelaende"));
        Daten.Front fg = sz.fronten().get(0);
        double lenEll = zahl(c, "SELECT LAENGE_M FROM GLC_FRONT_V WHERE ART = 'GELAENDE'"), flaeche = zahl(c, "SELECT MDSYS.SDO_GEOM.SDO_AREA(AUSSCHNITT, 0.05, 'unit=SQ_KM') FROM GLC_GELAENDE");
        check(Math.abs(lenEll / fg.laengeM() - 1) < 0.01 && Math.abs(flaeche - 163.84) < 2, String.format(Locale.GERMAN,
                "Auf dem Ellipsoid (SDO_LENGTH, SDO_AREA): Front des Geländes %.2f km (Szene %.2f km), Ausschnitt %.1f km² (Szene 163,8 km²)", lenEll / 1000, fg.laengeM() / 1000, flaeche));
        double modell = zahl(c, "SELECT COUNT(*) FROM GLC_FRONT WHERE ART = 'MODELL'"), spanne = zahl(c, "SELECT MAX(VORSTOSS_M) - MIN(VORSTOSS_M) FROM GLC_FRONT WHERE ART = 'MODELL'");
        check(modell == 12 && spanne > 60 && spanne <= 80, String.format(Locale.GERMAN, "Zwölf Monatsfronten des Jahresgangs, Vorstoß %.1f m von Juni bis Dezember", spanne));
        double ungueltig = 0;
        for (String m : Verbindung.METADATEN) {
            String[] tc = m.split("\\.");
            ungueltig += zahl(c, "SELECT COUNT(*) FROM " + tc[0] + " WHERE " + tc[1] + " IS NOT NULL AND MDSYS.SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(" + tc[1] + ", 0.05) <> 'TRUE'");
        }
        check(ungueltig == 0, "Alle Geometrien gültig (VALIDATE_GEOMETRY_WITH_CONTEXT) in " + Verbindung.METADATEN.length + " Spalten");
        double rupt = zahl(c, "SELECT COUNT(*) FROM GLC_RUPTURA"), stau16 = zahl(c, "SELECT STAU_M FROM GLC_RUPTURA WHERE DATUM = DATE '2016-03-10'");
        String q16 = text(c, "SELECT QUELLE FROM GLC_RUPTURA_V WHERE ART = 'BELEGT' AND DATUM = DATE '2016-03-10'");
        check(rupt == Startdaten.rupturen().size() && stau16 == 10.5 && "LN18A".equals(q16),
                String.format(Locale.GERMAN, "%.0f belegte Rupturas; 10.03.2016: Stau %.1f m, Quelle %s", rupt, stau16, q16));
        String befund = text(c, "SELECT QUELLEN FROM GLC_BEFUND_V WHERE GEGENSTAND = 'Stau und Abfluss'");
        check(befund != null && befund.contains("STU07"), "GLC_BEFUND_V mit Quellen: „Stau und Abfluss“ ← " + befund);

        System.out.println("Sitzung und Chronik (31.12.2099)");
        long sid = db.sitzungBeginnen(P.strip(), "Prüfung", "MITTEL");
        Long s = sid;
        double[][] fl = sz.fronten().get(0).linie().teile().get(0);
        double[] nf = fl[fl.length / 2];
        LocalDateTime mittag = TAG.atTime(12, 0);
        Daten.Front fr = new Daten.Front(0, mittag, null, Daten.F_SITZUNG, "Front " + Daten.text(mittag), sz.fronten().get(12).linie(), sz.fronten().get(12).laengeM(), 37.5,
                37.5 * 4600, 61.0, null, "Prüfung", Daten.EIGEN);
        db.front(fr, s);
        db.front(new Daten.Front(0, mittag.plusHours(3), null, Daten.F_MERKEN, "gemerkt", sz.fronten().get(12).linie(), sz.fronten().get(12).laengeM(), 38.0, null, 61.0, null, "Prüfung", Daten.EIGEN), s);
        Daten.Geom riss = Daten.Geom.linie(new double[][]{{nf[0] - 20, nf[1]}, {nf[0], nf[1] + 3}, {nf[0] + 20, nf[1]}});
        long k1 = db.kalbung(new Daten.Kalbung(0, mittag.plusMinutes(5), TAG.getDayOfYear(), "TURM", Daten.KARTE, nf[0], 230, nf[1], riss, 18_500, 16_650, 7, 52.0, 6.4, 3.2, null, 4711L, null), s);
        long k2 = db.kalbung(new Daten.Kalbung(0, mittag.plusMinutes(40), TAG.getDayOfYear(), "SCHEIBE", Daten.REGIE, nf[0] + 60, 210, nf[1] - 10, null, 2_300, 2_070, 3, 30.0, 4.1, 1.0, null, null, null), s);
        db.welle(k1, 0.734);
        double welle = zahl(c, "SELECT WELLE_STEG_M FROM GLC_KALBUNG WHERE ID = " + k1);
        double ll = zahl(c, "SELECT LAENGE FROM GLC_KALBUNG_V WHERE ID = " + k1), rissM = zahl(c, "SELECT RISS_M FROM GLC_KALBUNG_V WHERE ID = " + k1);
        double[] lonlat = Daten.lonLat(nf[0], nf[1]);
        check(k1 > 0 && k2 > k1 && welle == 0.734 && Math.abs(ll - lonlat[0]) < 1e-7 && Math.abs(rissM - riss.laenge()) < 0.5,
                String.format(Locale.GERMAN, "Kalbungen %d und %d: Lage als Punkt (Länge %.6f), Riss %.1f m auf dem Ellipsoid (Szene %.1f m), Welle am Steg nachgetragen %.3f m",
                        k1, k2, ll, rissM, riss.laenge(), welle));
        List<double[]> weg = new ArrayList<>();
        weg.add(new double[]{nf[0] + 30, nf[1] + 30});
        db.eisberg(new Daten.Eisberg(901, k1, null, mittag.plusMinutes(6), nf[0] + 30, nf[1] + 30, 9000, 9000, 25, 3, 0, weg, nf[0] + 30, nf[1] + 30, 10, 99, null, Daten.OFFEN), s);
        for (int i = 1; i <= 20; i++) weg.add(new double[]{nf[0] + 30 + i * 12, nf[1] + 30 + i * 9});
        db.eisberg(new Daten.Eisberg(901, null, null, mittag.plusMinutes(6), nf[0] + 30, nf[1] + 30, 9000, 8200, 24, 3, 1, weg, weg.get(20)[0], weg.get(20)[1], 30, 99, null, Daten.OFFEN), s);
        db.eisberg(new Daten.Eisberg(902, k2, null, mittag.plusMinutes(41), nf[0] + 70, nf[1] + 10, 900, 0, 8, 1, 0, List.of(new double[]{nf[0] + 70, nf[1] + 10}), nf[0] + 70, nf[1] + 10, 0, 7,
                mittag.plusHours(5), Daten.GESCHMOLZEN), s);
        List<Daten.Eisberg> offen = db.offeneEisberge(sid);
        double wegKm = zahl(c, "SELECT WEG_KM FROM GLC_EISBERG_V WHERE SITZUNG_ID = " + sid + " AND NR = 901"), eb = zahl(c, "SELECT EISBERGE FROM GLC_KALBUNG_V WHERE ID = " + k1);
        check(offen.size() == 1 && offen.get(0).kalbungId() == k1 && offen.get(0).weg().size() == 21 && offen.get(0).volumenM3() == 8200 && Math.abs(wegKm * 1000 - offen.get(0).wegM()) < 2
                        && eb == 1, String.format(Locale.GERMAN, "Eisberg fortgeschrieben (UPDATE): Weg mit %d Punkten, %.3f km auf dem Ellipsoid; einer geschmolzen; Kalbung %d hat %.0f Eisberg",
                offen.isEmpty() ? 0 : offen.get(0).weg().size(), wegKm, k1, eb));
        Daten.Geom damm = Daten.Geom.flaeche(new double[][]{{0, 0}, {300, 0}, {300, 200}, {0, 200}});
        Daten.Geom rinne = Daten.Geom.linie(new double[][]{{20, 55}, {55, -10}, {90, -110}});
        long lid = db.rupturaLauf(new Daten.RupturaLauf(0, 2099, mittag, null, null, null, null, null, null, "STAU", 21, 0, 0, 0, 0, 0, damm, rinne, null), s);
        db.rupturaLauf(new Daten.RupturaLauf(lid, 2099, mittag, mittag.plusDays(20), mittag.plusDays(20), mittag.plusDays(22), mittag.plusDays(23), mittag.plusDays(24), mittag.plusDays(26),
                "RUHE", 25, 7300, 25 * 70e6, 3.1e5, 4.4e4, 412, damm, rinne, null), s);
        double abfl = zahl(c, "SELECT ABFLUSS_MAX_M3S FROM GLC_RUPTURA_V WHERE LAUF_ID = " + lid), dauer = zahl(c, "SELECT DAUER_H FROM GLC_RUPTURA_V WHERE LAUF_ID = " + lid);
        check(abfl == 7300 && Math.abs(dauer - 144) < 0.1, String.format(Locale.GERMAN, "Lauf der Ruptura %d angelegt und fortgeschrieben: %.0f m³/s, %.0f h vom Durchbruch bis zum Ende (GLC_RUPTURA_V)",
                lid, abfl, dauer));
        byte[] png = new byte[7000];
        for (int i = 0; i < png.length; i++) png[i] = (byte) (i * 13);
        db.regielauf(new Daten.Regielauf("Ein Sommertag an der Front", LocalDateTime.now().minusMinutes(16), LocalDateTime.now(), true, 53.2, 31.0, 9.4, 10, "MITTEL", png, "docs/status/x.png"), s);
        double blob = zahl(c, "SELECT DBMS_LOB.GETLENGTH(BLATT) FROM GLC_REGIELAUF WHERE SITZUNG_ID = " + sid), rbid = zahl(c, "SELECT COUNT(*) FROM GLC_REGIELAUF WHERE SITZUNG_ID = " + sid + " AND REGIEBUCH_ID IS NOT NULL");
        check(blob == png.length && rbid == 1, "Lauf eines Regiebuchs mit Blatt (BLOB " + (int) blob + " Bytes), dem mitgelieferten Regiebuch zugeordnet");
        double karte = zahl(c, "SELECT COUNT(*) FROM GLC_TAGESKARTE_V WHERE TAG = DATE '2099-12-31'");
        String ebenen = text(c, "SELECT LISTAGG(EBENE, ',') WITHIN GROUP (ORDER BY EBENE) FROM (SELECT DISTINCT EBENE FROM GLC_TAGESKARTE_V WHERE TAG = DATE '2099-12-31')");
        double tagV = zahl(c, "SELECT SUM(ANZAHL) FROM GLC_KALBUNG_TAG_V WHERE TAG = DATE '2099-12-31'"), vor = zahl(c, "SELECT GEGEN_VORIGE_M FROM GLC_FRONT_V WHERE ART = 'MERKEN' AND TAG = DATE '2099-12-31'");
        check(karte == 9 && "DAMM,EISBERG,FRONT,KALBUNG,RINNE,RISS".equals(ebenen) && tagV == 2,
                String.format(Locale.GERMAN, "GLC_TAGESKARTE_V am 31.12.2099: %.0f Objekte in den Ebenen %s; GLC_KALBUNG_TAG_V %.0f Kalbungen", karte, ebenen, tagV));
        List<Daten.KalbungKurz> um = db.kalbungenUm(nf[0], nf[1], 100, 5);
        List<Daten.Kalbung> am = db.kalbungenAm(TAG);
        check(um.size() == 2 && am.size() == 2 && am.get(0).riss() != null && am.get(0).riss().xz().length == 3 && Math.abs(am.get(0).riss().xz()[1][1] - (nf[1] + 3)) < 0.01,
                "Säulenkarte: zwei Kalbungen im Umkreis von 100 m; Tageskarte liest Kalbungen mit Riss zurück (WKT → Szenenmeter, Abweichung < 1 cm)");
        File tmp = Files.createTempDirectory("glacier-dbpruefung").toFile();
        Tageskarte.Ergebnis tk = Tageskarte.zeichnen(db, t, TAG, tmp);
        check(tk.datei().isFile() && tk.kalbungen() == 2 && tk.eisberge() >= 1 && tk.fronten() == 2, "Tageskarte aus der Datenbank: " + tk.datei().getName() + ", "
                + tk.kalbungen() + " Kalbungen, " + tk.eisberge() + " Eisberge, " + tk.fronten() + " Fronten");
        System.out.println("    (Gemerkte Front gegen die vorige: " + vor + " m)");

        System.out.println("Regiebuch, Ansicht, Preset, Sitzung");
        String rt = "Regiebuch: " + P + "Abend\nLänge: 0:30\n0:00 zeit 16.01.2026 19:00 | Abend\n0:05 ansicht Vom Steg\n0:20 foto Abend\n0:29 blatt\n";
        db.speichern(new Daten.Regiebuch(P + "Abend", rt, "", null, Daten.EIGEN));
        long aid = db.speichern(new Daten.Ansicht(0, P + "Ansicht", null, "GEMERKT", new double[]{209, 40, -216, 224, -2, 55}, null, false, mittag, "a=1\nb=2\n", png, Daten.EIGEN), s);
        var oc = com.dan.ocean.Ocean.lago(4);
        db.speichern(new Daten.PaketPreset("com.dan.ocean", "Ocean", P + "Lago", "Prüfung", Daten.EIGEN, Felder.lesen(oc)));
        Daten.Regiebuch rb = db.regiebuecher().stream().filter(x -> x.name().equals(P + "Abend")).findFirst().orElseThrow();
        Daten.Ansicht an = db.ansicht(aid);
        double sw = zahl(c, "SELECT COUNT(*) FROM GLC_STICHWORT x JOIN GLC_REGIEBUCH r ON r.ID = x.REGIEBUCH_ID WHERE r.NAME = '" + P + "Abend'");
        double lageA = zahl(c, "SELECT a.LAGE.SDO_POINT.Y FROM GLC_ANSICHT a WHERE ID = " + aid);
        var oc2 = com.dan.ocean.Ocean.calm();
        Felder.schreiben(oc2, db.presets("com.dan.ocean", "Ocean").stream().filter(x -> x.name().equals(P + "Lago")).findFirst().orElseThrow().felder());
        check(rb.text().equals(rt) && sw == 4 && java.util.Arrays.equals(an.vorschau(), png) && mittag.equals(an.szenenzeit()) && Math.abs(lageA - Daten.lonLat(209, -216)[1]) < 1e-7
                        && Felder.lesen(oc2).equals(Felder.lesen(oc)),
                "Regiebuch (Text als CLOB, 4 Stichworte), Ansicht mit Vorschau (BLOB), Szenenzeit und Lage als Punkt, Preset gespeichert und gelesen");
        boolean abgelehnt = false;
        try { db.speichern(new Daten.Regiebuch("Die Ruptura", rt.replace(P + "Abend", "Die Ruptura"), "", null, Daten.EIGEN)); } catch (Ablage.Fehler e) { abgelehnt = true; }
        check(abgelehnt, "Ein mitgeliefertes Regiebuch („Die Ruptura“) wird nicht überschrieben");
        Map<String, String> sp = new LinkedHashMap<>();
        sp.put(Speicher.SZ, mittag.toString());
        sp.put(Speicher.REGIEBUCH, P + "Abend");
        sp.put(Speicher.REGIE_SEKUNDE, "12.5");
        sp.put(Speicher.ZEITRAFFER, "60");
        sp.put(Speicher.VORSTOSS, "37.5");
        sp.put(Speicher.RUPTURA, "STAU");
        sp.put(Speicher.STAU, "22.5");
        sp.put(Speicher.EISBERGE, "1");
        db.sitzungSichern(sid, new Ablage.Sicherung("uhr.datum=2099-12-31\nuhr.stunde=12\n", sp));
        Daten.Sitzung ls = db.letzteSitzung(-1);
        check(ls != null && ls.id() == sid && !ls.sauber() && mittag.equals(ls.szenenzeit()) && (P + "Abend").equals(ls.regiebuch()) && ls.eisberge() == 1 && "STAU".equals(ls.rupturaPhase()),
                "Sitzung gesichert: Szenenzeit als TIMESTAMP (" + (ls == null ? "—" : Daten.text(ls.szenenzeit())) + "), Regiebuch mit Stelle, Ruptura, Eisberge; noch nicht beendet");
        Protokoll.info("DbPrüfung", "eine Zeile aus der Prüfung");
        db.protokoll(Protokoll.abholen(), sid);
        db.sitzungBeenden(sid);
        double logz = zahl(c, "SELECT COUNT(*) FROM GLC_LOG WHERE SITZUNG_ID = " + sid);
        check(logz >= 1 && zahl(c, "SELECT SAUBER_BEENDET FROM GLC_SITZUNG WHERE ID = " + sid) == 1, "Protokoll in GLC_LOG (" + (int) logz + " Zeilen), Sitzung sauber beendet");

        System.out.println("Übernahme aus Dateien");
        File dir = Files.createTempDirectory("glacier-dbpruefung-dateien").toFile();
        DateiSpeicher d = new DateiSpeicher(dir);
        long dk = d.kalbung(new Daten.Kalbung(0, mittag.plusHours(2), TAG.getDayOfYear(), "FUSS", Daten.HAND, nf[0] - 40, 150, nf[1], null, 4100, 3690, 2, -20.0, null, 0, 0.12, null, null), 7L);
        d.eisberg(new Daten.Eisberg(5, dk, null, mittag.plusHours(2), nf[0] - 40, nf[1] + 20, 3000, 2900, 14, 2, 0, List.of(new double[]{nf[0] - 40, nf[1] + 20}, new double[]{nf[0] - 10, nf[1] + 60}),
                nf[0] - 10, nf[1] + 60, 0, 5, null, Daten.OFFEN), 7L);
        d.front(new Daten.Front(0, mittag.plusHours(4), null, Daten.F_SITZUNG, "aus Datei", sz.fronten().get(12).linie(), sz.fronten().get(12).laengeM(), 38.2, null, 61.0, null, "Prüfung", Daten.EIGEN), 7L);
        String ue = Uebernahme.uebernehmen(d, db);
        double kD = zahl(c, "SELECT COUNT(*) FROM GLC_KALBUNG WHERE SZENENZEIT = TIMESTAMP '2099-12-31 14:00:00' AND ART = 'FUSS'"),
                eD = zahl(c, "SELECT COUNT(*) FROM GLC_EISBERG e JOIN GLC_KALBUNG k ON k.ID = e.KALBUNG_ID WHERE k.SZENENZEIT = TIMESTAMP '2099-12-31 14:00:00'");
        check(kD == 1 && eD == 1 && new File(dir, "chronik/kalbungen.csv.uebernommen").isFile(), "Übernommen: " + ue + "; der Eisberg hängt an seiner Kalbung, die CSV heißen *.uebernommen");
        Daten.Stand st = db.stand();
        check(st.rupturen() == Startdaten.rupturen().size() && st.presets() >= Startdaten.presets().size() && st.befunde() >= Startdaten.befunde().size(),
                "Stand für das Startbild: " + st.fronten() + " Fronten, " + st.kalbungen() + " Kalbungen, " + st.eisberge() + " Eisberge, " + st.rupturen() + " Rupturas, "
                        + st.regiebuecher() + " Regiebücher, " + st.befunde() + " Befunde");

        System.out.println("Aufräumen");
        int weg1 = db.chronikLeeren(sid);
        db.loeschenRegiebuch(P + "Abend");
        db.loeschenAnsicht(aid);
        db.loeschenPreset("com.dan.ocean", "Ocean", P + "Lago");
        try (Statement x = c.createStatement()) {
            x.executeUpdate("DELETE FROM GLC_EISBERG WHERE KALBUNG_ID IN (SELECT ID FROM GLC_KALBUNG WHERE SZENENZEIT >= TIMESTAMP '2099-12-31 00:00:00')");
            x.executeUpdate("DELETE FROM GLC_KALBUNG WHERE SZENENZEIT >= TIMESTAMP '2099-12-31 00:00:00'");
            x.executeUpdate("DELETE FROM GLC_FRONT WHERE SZENENZEIT >= TIMESTAMP '2099-12-31 00:00:00' AND HERKUNFT = 'EIGEN'");
            x.executeUpdate("DELETE FROM GLC_LOG WHERE SITZUNG_ID = " + sid);
            x.executeUpdate("DELETE FROM GLC_SITZUNG WHERE ID = " + sid);
        }
        double rest = zahl(c, "SELECT (SELECT COUNT(*) FROM GLC_REGIEBUCH WHERE NAME LIKE '~%') + (SELECT COUNT(*) FROM GLC_ANSICHT WHERE NAME LIKE '~%') "
                + "+ (SELECT COUNT(*) FROM GLC_PAKET_PRESET WHERE NAME LIKE '~%') + (SELECT COUNT(*) FROM GLC_TAGESKARTE_V WHERE TAG = DATE '2099-12-31') FROM DUAL");
        check(rest == 0 && weg1 >= 8, "Aufgeräumt: " + weg1 + " Zeilen der Prüfsitzung (Chronik leeren), nichts mit „~“ und nichts am 31.12.2099 bleibt");
        db.schliessen();

        System.out.printf(Locale.GERMAN, "%d geprüft, %d Fehler · %.1f s%n", ok + bad, bad, (System.nanoTime() - t0) / 1e9);
        System.out.println(bad == 0 ? "Alles in Ordnung." : "Nicht alles in Ordnung.");
        System.exit(bad == 0 ? 0 : 1);
    }
}
