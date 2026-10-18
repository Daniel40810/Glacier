package com.dan.glacier.data;

import com.dan.glacier.scene.Regie;
import com.dan.store.DateiAblage;
import com.dan.store.Felder;
import com.dan.store.Zahl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Speichern ohne Datenbank (F33 A): Textdateien im Ordner {@code daten/} des Projekts. Die mitgelieferten Einträge
 * kommen aus {@link Startdaten} und stehen nicht in Dateien; eigene liegen je in einer Datei. Die Chronik liegt als CSV
 * mit Semikolon und einer Spalte {@code WKT} in Länge und Breite (QGIS: „Getrennte Texte“, Geometrie WKT, EPSG:4326):
 * <pre>
 * daten/chronik/fronten.csv  kalbungen.csv  eisberge.csv  ruptura_laeufe.csv  regielaeufe.csv (+ Blätter in docs/status)
 * daten/regiebuecher/*.txt   daten/ansichten/N.txt + N.zustand + N.png   daten/presets/paket/Klasse/*.txt
 * daten/sitzungen/N.txt + N.zustand   daten/protokoll.txt
 * </pre>
 * Beim nächsten Verbinden übernimmt {@link Uebernahme} die Chronik in die Datenbank und benennt die Dateien in
 * {@code *.uebernommen} um.
 */
public final class DateiSpeicher extends DateiAblage implements Speicher {

    /** Die mitgelieferten Fronten (aus dem laufenden Gletscher, sobald er steht); ohne Szene leer. */
    public volatile Supplier<List<Daten.Front>> mitgelieferteFronten = List::of;

    public DateiSpeicher(File dir) { super(dir); }

    /** Der Ordner daten neben db (oder -Dglacier.daten). */
    public static File ordner() {
        String p = System.getProperty("glacier.daten");
        if (p != null && !p.isEmpty()) return new File(p);
        File db = Verbindung.ordner().getAbsoluteFile();
        File base = db.getParentFile();
        return new File(base != null ? base : new File("."), "daten");
    }

    private static void eigen(boolean mitgeliefert, String was) throws Fehler {
        if (mitgeliefert) throw new Fehler("„" + was + "“ ist mitgeliefert und wird nicht überschrieben; bitte unter einem eigenen Namen speichern");
    }

    // ------------------------------------------------------------------ CSV der Chronik

    /** Eine CSV-Datei der Chronik: Semikolon, Text in Anführungszeichen, erste Zeile die Spalten. */
    static final class Csv {
        final File f;
        final String[] spalten;
        List<LinkedHashMap<String, String>> zeilen;

        Csv(File f, String... spalten) { this.f = f; this.spalten = spalten; }

        synchronized List<LinkedHashMap<String, String>> lesen() throws Fehler {
            if (zeilen != null) return zeilen;
            zeilen = new ArrayList<>();
            if (!f.isFile()) return zeilen;
            try {
                List<String> l = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                if (l.isEmpty()) return zeilen;
                String[] kopf = teile(l.get(0));
                for (int i = 1; i < l.size(); i++) {
                    if (l.get(i).isBlank()) continue;
                    String[] v = teile(l.get(i));
                    LinkedHashMap<String, String> m = new LinkedHashMap<>();
                    for (int k = 0; k < kopf.length; k++) m.put(kopf[k], k < v.length ? v[k] : "");
                    zeilen.add(m);
                }
                return zeilen;
            } catch (IOException e) {
                throw new Fehler(f + " nicht lesbar: " + e.getMessage(), e);
            }
        }

        synchronized void schreiben() throws Fehler {
            StringBuilder b = new StringBuilder(String.join(";", spalten)).append('\n');
            for (Map<String, String> m : zeilen) {
                for (int k = 0; k < spalten.length; k++) b.append(k > 0 ? ";" : "").append(feld(m.getOrDefault(spalten[k], "")));
                b.append('\n');
            }
            schreibe(f, b.toString());
        }

        synchronized long naechste() throws Fehler {
            long n = 0;
            for (Map<String, String> m : lesen()) n = Math.max(n, (long) Zahl.lies(m.getOrDefault("ID", "0")));
            return n + 1;
        }

        static String feld(String v) {
            if (v == null) return "";
            if (v.indexOf(';') < 0 && v.indexOf('"') < 0 && v.indexOf('\n') < 0 && v.indexOf(',') < 0) return v;
            return "\"" + v.replace("\"", "\"\"").replace("\n", " ") + "\"";
        }

        static String[] teile(String z) {
            List<String> l = new ArrayList<>();
            StringBuilder b = new StringBuilder();
            boolean q = false;
            for (int i = 0; i < z.length(); i++) {
                char c = z.charAt(i);
                if (q) {
                    if (c == '"' && i + 1 < z.length() && z.charAt(i + 1) == '"') { b.append('"'); i++; }
                    else if (c == '"') q = false;
                    else b.append(c);
                } else if (c == '"') q = true;
                else if (c == ';') { l.add(b.toString()); b.setLength(0); }
                else b.append(c);
            }
            l.add(b.toString());
            return l.toArray(new String[0]);
        }
    }

    private Csv fronten, kalbungen, eisberge, laeufe, regie;

    private synchronized Csv fr() { if (fronten == null) fronten = new Csv(sub("chronik", "fronten.csv"), "ID", "SZENENZEIT", "ART", "NAME", "LAENGE_M", "VORSTOSS_M", "FLAECHE_DIFF_M2", "HOEHE_MITTEL_M", "SITZUNG", "BEMERKUNG", "XZ", "WKT"); return fronten; }
    private synchronized Csv ka() { if (kalbungen == null) kalbungen = new Csv(sub("chronik", "kalbungen.csv"), "ID", "SZENENZEIT", "JAHRESTAG", "ART", "AUSLOESER", "X_M", "Y_M", "Z_M", "VOLUMEN_M3", "MASSE_T", "STUECKE", "HOEHE_M", "FALLZEIT_S", "VORWARNUNG_S", "WELLE_STEG_M", "SEED", "SITZUNG", "REGIEBUCH", "RISS_XZ", "RISS_WKT", "WKT"); return kalbungen; }
    private synchronized Csv ei() { if (eisberge == null) eisberge = new Csv(sub("chronik", "eisberge.csv"), "SITZUNG", "NR", "KALBUNG_ID", "RUPTURA_LAUF_ID", "GEBURT", "GEBURT_X", "GEBURT_Z", "VOLUMEN_START_M3", "VOLUMEN_M3", "TIEFGANG_M", "FREIBORD_M", "KENTERUNGEN", "X_M", "Z_M", "GIER_GRAD", "FORM_SEED", "ENDE", "ENDE_ART", "WEG_M", "WEG_XZ", "WKT"); return eisberge; }
    private synchronized Csv lf() { if (laeufe == null) laeufe = new Csv(sub("chronik", "ruptura_laeufe.csv"), "ID", "JAHR", "BEGINN", "DURCHBRUCH", "TUNNEL", "RINNE", "BRUECKE", "EINSTURZ", "ENDE", "PHASE", "STAU_MAX_M", "ABFLUSS_MAX_M3S", "VOLUMEN_M3", "BRUECKE_M3", "GROESSTES_M3", "STUECKE", "SITZUNG", "REGIEBUCH", "DAMM_XZ", "RINNE_XZ", "WKT"); return laeufe; }
    private synchronized Csv rl() { if (regie == null) regie = new Csv(sub("chronik", "regielaeufe.csv"), "ID", "NAME", "BEGINN", "ENDE", "VOLLSTAENDIG", "BILDER_S_MITTEL", "BILDER_S_MIN", "GPU_MS_MITTEL", "FOTOS", "QUALITAET", "BLATT_DATEI", "SITZUNG"); return regie; }

    /** Die Dateien der Chronik (für die Übernahme). */
    public List<File> chronikDateien() {
        return List.of(fr().f, ka().f, ei().f, lf().f, rl().f);
    }

    private static String t(LocalDateTime t) { return t == null ? "" : t.toString(); }
    private static LocalDateTime t(String s) { return s == null || s.isEmpty() ? null : LocalDateTime.parse(s); }
    private static String n(double v) { return Double.isNaN(v) ? "" : Zahl.text(Math.round(v * 1000) / 1000.0); }
    private static String n(Double v) { return v == null ? "" : n((double) v); }
    private static double d(String s) { return s == null || s.isEmpty() ? Double.NaN : Zahl.lies(s); }
    private static Double dn(String s) { return s == null || s.isEmpty() ? null : Zahl.lies(s); }
    private static Long ln(String s) { return s == null || s.isEmpty() ? null : (long) Zahl.lies(s); }
    private static String s(Long v) { return v == null ? "" : v.toString(); }
    private static String g(Daten.Geom g) { return g == null ? "" : g.text(); }
    private static String w(Daten.Geom g) { return g == null ? "" : g.wkt(); }

    // ------------------------------------------------------------------ Stand

    @Override public Daten.Stand stand() throws Fehler {
        return new Daten.Stand(fr().lesen().size() + mitgelieferteFronten.get().size(), ka().lesen().size(), ei().lesen().size(), rupturen().size(), lf().lesen().size(),
                regiebuecher().size(), rl().lesen().size(), wetterlagen().size(), fahrten().size(), ansichten().size(), presets(null, null).size(), befunde().size(),
                quellen().size(), letzteSitzung(-1));
    }

    @Override public List<Daten.Quelle> quellen() { return Startdaten.quellen(); }
    @Override public List<Daten.Befund> befunde() { return Startdaten.befunde(); }
    @Override public List<Daten.Ruptura> rupturen() { return Startdaten.rupturen(); }

    // ------------------------------------------------------------------ Fronten

    @Override public long front(Daten.Front f, Long sitzung) throws Fehler {
        Csv c = fr();
        synchronized (c) {
            long id = c.naechste();
            LinkedHashMap<String, String> m = new LinkedHashMap<>();
            m.put("ID", Long.toString(id));
            m.put("SZENENZEIT", t(f.szenenzeit()));
            m.put("ART", f.art());
            m.put("NAME", f.name() == null ? "" : f.name());
            m.put("LAENGE_M", n(Math.round(f.laengeM() * 10) / 10.0));
            m.put("VORSTOSS_M", n(f.vorstossM()));
            m.put("FLAECHE_DIFF_M2", n(f.flaecheDiffM2()));
            m.put("HOEHE_MITTEL_M", n(f.hoeheMittelM()));
            m.put("SITZUNG", s(sitzung));
            m.put("BEMERKUNG", f.bemerkung() == null ? "" : f.bemerkung());
            m.put("XZ", g(f.linie()));
            m.put("WKT", w(f.linie()));
            c.lesen().add(m);
            c.schreiben();
            return id;
        }
    }

    @Override public List<Daten.Front> fronten(LocalDateTime von, LocalDateTime bis, boolean ohneLinie) throws Fehler {
        List<Daten.Front> l = new ArrayList<>();
        for (Daten.Front f : mitgelieferteFronten.get())
            if ((von == null || !f.szenenzeit().isBefore(von)) && (bis == null || f.szenenzeit().isBefore(bis))) l.add(f);
        Csv c = fr();
        synchronized (c) {
            for (Map<String, String> m : c.lesen()) {
                LocalDateTime z = t(m.get("SZENENZEIT"));
                if (z == null || (von != null && z.isBefore(von)) || (bis != null && !z.isBefore(bis))) continue;
                l.add(new Daten.Front((long) d(m.get("ID")), z, null, m.get("ART"), m.get("NAME"), ohneLinie ? null : Daten.Geom.ausText(m.get("XZ")), d(m.get("LAENGE_M")),
                        d(m.get("VORSTOSS_M")), dn(m.get("FLAECHE_DIFF_M2")), dn(m.get("HOEHE_MITTEL_M")), null, m.get("BEMERKUNG"), Daten.EIGEN));
            }
        }
        l.sort((a, b) -> a.szenenzeit().compareTo(b.szenenzeit()));
        return l;
    }

    // ------------------------------------------------------------------ Kalbungen

    @Override public long kalbung(Daten.Kalbung k, Long sitzung) throws Fehler {
        Csv c = ka();
        synchronized (c) {
            long id = c.naechste();
            LinkedHashMap<String, String> m = new LinkedHashMap<>();
            m.put("ID", Long.toString(id));
            m.put("SZENENZEIT", t(k.szenenzeit()));
            m.put("JAHRESTAG", Integer.toString(k.jahrestag()));
            m.put("ART", k.art());
            m.put("AUSLOESER", k.ausloeser());
            m.put("X_M", n(k.x()));
            m.put("Y_M", n(k.y()));
            m.put("Z_M", n(k.z()));
            m.put("VOLUMEN_M3", n(Math.round(k.volumenM3() * 10) / 10.0));
            m.put("MASSE_T", n(Math.round(k.masseT() * 10) / 10.0));
            m.put("STUECKE", Integer.toString(k.stuecke()));
            m.put("HOEHE_M", n(k.hoeheM()));
            m.put("FALLZEIT_S", n(k.fallzeitS()));
            m.put("VORWARNUNG_S", n(k.vorwarnungS()));
            m.put("WELLE_STEG_M", n(k.welleStegM()));
            m.put("SEED", s(k.seed()));
            m.put("SITZUNG", s(sitzung));
            m.put("REGIEBUCH", k.regiebuch() == null ? "" : k.regiebuch());
            m.put("RISS_XZ", g(k.riss()));
            m.put("RISS_WKT", w(k.riss()));
            m.put("WKT", Daten.Geom.punkt(k.x(), k.z()).wkt());
            c.lesen().add(m);
            c.schreiben();
            return id;
        }
    }

    @Override public void welle(long kalbung, double hoeheM) throws Fehler {
        Csv c = ka();
        synchronized (c) {
            for (Map<String, String> m : c.lesen()) if ((long) d(m.get("ID")) == kalbung) m.put("WELLE_STEG_M", n(hoeheM));
            c.schreiben();
        }
    }

    private Daten.Kalbung kalbung(Map<String, String> m) {
        return new Daten.Kalbung((long) d(m.get("ID")), t(m.get("SZENENZEIT")), (int) d(m.get("JAHRESTAG")), m.get("ART"), m.get("AUSLOESER"), d(m.get("X_M")), d(m.get("Y_M")),
                d(m.get("Z_M")), Daten.Geom.ausText(m.get("RISS_XZ")), d(m.get("VOLUMEN_M3")), d(m.get("MASSE_T")), (int) d(m.get("STUECKE")), d(m.get("HOEHE_M")),
                dn(m.get("FALLZEIT_S")), d(m.get("VORWARNUNG_S")), dn(m.get("WELLE_STEG_M")), ln(m.get("SEED")), m.get("REGIEBUCH").isEmpty() ? null : m.get("REGIEBUCH"));
    }

    private static Daten.KalbungKurz kurz(Daten.Kalbung k) {
        return new Daten.KalbungKurz(k.id(), k.szenenzeit(), k.art(), k.ausloeser(), k.x(), k.z(), k.volumenM3(), k.welleStegM());
    }

    private List<Daten.Kalbung> alleKalbungen() throws Fehler {
        Csv c = ka();
        synchronized (c) {
            List<Daten.Kalbung> l = new ArrayList<>();
            for (Map<String, String> m : c.lesen()) l.add(kalbung(m));
            return l;
        }
    }

    @Override public List<Daten.KalbungKurz> kalbungen(LocalDateTime von, LocalDateTime bis, int n) throws Fehler {
        List<Daten.KalbungKurz> l = new ArrayList<>();
        List<Daten.Kalbung> a = alleKalbungen();
        Collections.reverse(a);
        for (Daten.Kalbung k : a) {
            if ((von != null && k.szenenzeit().isBefore(von)) || (bis != null && !k.szenenzeit().isBefore(bis))) continue;
            l.add(kurz(k));
        }
        l.sort((x, y) -> y.szenenzeit().compareTo(x.szenenzeit()));
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }

    @Override public List<Daten.KalbungKurz> kalbungenUm(double x, double z, double radiusM, int n) throws Fehler {
        List<Daten.KalbungKurz> l = new ArrayList<>();
        for (Daten.Kalbung k : alleKalbungen()) if (Math.hypot(k.x() - x, k.z() - z) <= radiusM) l.add(kurz(k));
        l.sort((a, b) -> b.szenenzeit().compareTo(a.szenenzeit()));
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }

    @Override public List<Daten.Kalbung> kalbungenAm(LocalDate tag) throws Fehler {
        List<Daten.Kalbung> l = new ArrayList<>();
        for (Daten.Kalbung k : alleKalbungen()) if (k.szenenzeit().toLocalDate().equals(tag)) l.add(k);
        return l;
    }

    // ------------------------------------------------------------------ Eisberge

    @Override public void eisberg(Daten.Eisberg e, Long sitzung) throws Fehler {
        Csv c = ei();
        synchronized (c) {
            LinkedHashMap<String, String> m = null;
            for (LinkedHashMap<String, String> x : c.lesen())
                if (x.get("SITZUNG").equals(s(sitzung)) && (int) d(x.get("NR")) == e.nr()) m = x;
            if (m == null) {
                m = new LinkedHashMap<>();
                c.lesen().add(m);
            }
            m.put("SITZUNG", s(sitzung));
            m.put("NR", Integer.toString(e.nr()));
            m.put("KALBUNG_ID", e.kalbungId() != null ? e.kalbungId().toString() : m.getOrDefault("KALBUNG_ID", ""));
            m.put("RUPTURA_LAUF_ID", s(e.rupturaLaufId()));
            m.put("GEBURT", t(e.geburt()));
            m.put("GEBURT_X", n(e.geburtX()));
            m.put("GEBURT_Z", n(e.geburtZ()));
            m.put("VOLUMEN_START_M3", n(Math.round(e.volumenStartM3() * 10) / 10.0));
            m.put("VOLUMEN_M3", n(Math.round(e.volumenM3() * 10) / 10.0));
            m.put("TIEFGANG_M", n(e.tiefgangM()));
            m.put("FREIBORD_M", n(e.freibordM()));
            m.put("KENTERUNGEN", Integer.toString(e.kenterungen()));
            m.put("X_M", n(e.x()));
            m.put("Z_M", n(e.z()));
            m.put("GIER_GRAD", n(e.gierGrad()));
            m.put("FORM_SEED", Long.toString(e.formSeed()));
            m.put("ENDE", t(e.ende()));
            m.put("ENDE_ART", e.endeArt());
            m.put("WEG_M", n(Math.round(e.wegM() * 10) / 10.0));
            Daten.Geom weg = e.weg().size() >= 2 ? Daten.Geom.linie(e.weg().toArray(new double[0][])) : Daten.Geom.punkt(e.geburtX(), e.geburtZ());
            m.put("WEG_XZ", weg.text());
            m.put("WKT", weg.wkt());
            c.schreiben();
        }
    }

    private static Daten.Eisberg eisberg(Map<String, String> m) {
        Daten.Geom w = Daten.Geom.ausText(m.get("WEG_XZ"));
        List<double[]> weg = w == null ? List.of(new double[]{d(m.get("GEBURT_X")), d(m.get("GEBURT_Z"))}) : java.util.Arrays.asList(w.xz());
        return new Daten.Eisberg((int) d(m.get("NR")), ln(m.get("KALBUNG_ID")), ln(m.get("RUPTURA_LAUF_ID")), t(m.get("GEBURT")), d(m.get("GEBURT_X")), d(m.get("GEBURT_Z")),
                d(m.get("VOLUMEN_START_M3")), d(m.get("VOLUMEN_M3")), d(m.get("TIEFGANG_M")), d(m.get("FREIBORD_M")), (int) d(m.get("KENTERUNGEN")), weg, d(m.get("X_M")),
                d(m.get("Z_M")), d(m.get("GIER_GRAD")), (long) d(m.get("FORM_SEED")), t(m.get("ENDE")), m.get("ENDE_ART"));
    }

    @Override public List<Daten.Eisberg> offeneEisberge(long sitzung) throws Fehler {
        Csv c = ei();
        synchronized (c) {
            List<Daten.Eisberg> l = new ArrayList<>();
            for (Map<String, String> m : c.lesen()) if (m.get("SITZUNG").equals(Long.toString(sitzung)) && Daten.OFFEN.equals(m.get("ENDE_ART"))) l.add(eisberg(m));
            return l;
        }
    }

    @Override public List<Daten.Eisberg> eisbergeAm(LocalDate tag) throws Fehler {
        Csv c = ei();
        synchronized (c) {
            List<Daten.Eisberg> l = new ArrayList<>();
            LocalDateTime a = tag.atStartOfDay(), b = tag.plusDays(1).atStartOfDay();
            for (Map<String, String> m : c.lesen()) {
                LocalDateTime g = t(m.get("GEBURT")), e = t(m.get("ENDE"));
                if (g != null && g.isBefore(b) && (e == null || !e.isBefore(a))) l.add(eisberg(m));
            }
            return l;
        }
    }

    // ------------------------------------------------------------------ Läufe

    @Override public long rupturaLauf(Daten.RupturaLauf l, Long sitzung) throws Fehler {
        Csv c = lf();
        synchronized (c) {
            long id = l.id() > 0 ? l.id() : c.naechste();
            LinkedHashMap<String, String> m = null;
            for (LinkedHashMap<String, String> x : c.lesen()) if ((long) d(x.get("ID")) == id) m = x;
            if (m == null) { m = new LinkedHashMap<>(); c.lesen().add(m); }
            m.put("ID", Long.toString(id));
            m.put("JAHR", l.jahr() == null ? "" : l.jahr().toString());
            m.put("BEGINN", t(l.beginn()));
            m.put("DURCHBRUCH", t(l.durchbruch()));
            m.put("TUNNEL", t(l.tunnel()));
            m.put("RINNE", t(l.rinne()));
            m.put("BRUECKE", t(l.bruecke()));
            m.put("EINSTURZ", t(l.einsturz()));
            m.put("ENDE", t(l.ende()));
            m.put("PHASE", l.phase());
            m.put("STAU_MAX_M", n(l.stauMaxM()));
            m.put("ABFLUSS_MAX_M3S", n((double) Math.round(l.abflussMaxM3s())));
            m.put("VOLUMEN_M3", n((double) Math.round(l.volumenM3())));
            m.put("BRUECKE_M3", n(l.brueckeM3()));
            m.put("GROESSTES_M3", n(l.groesstesM3()));
            m.put("STUECKE", Integer.toString(l.stuecke()));
            m.put("SITZUNG", s(sitzung));
            m.put("REGIEBUCH", l.regiebuch() == null ? "" : l.regiebuch());
            m.put("DAMM_XZ", g(l.damm()));
            m.put("RINNE_XZ", g(l.rinneLinie()));
            m.put("WKT", w(l.damm()));
            c.schreiben();
            return id;
        }
    }

    @Override public List<Daten.RupturaLauf> rupturaLaeufe() throws Fehler {
        Csv c = lf();
        synchronized (c) {
            List<Daten.RupturaLauf> l = new ArrayList<>();
            for (Map<String, String> m : c.lesen()) {
                double j = d(m.get("JAHR"));
                l.add(new Daten.RupturaLauf((long) d(m.get("ID")), Double.isNaN(j) ? null : (int) j, t(m.get("BEGINN")), t(m.get("DURCHBRUCH")), t(m.get("TUNNEL")),
                        t(m.get("RINNE")), t(m.get("BRUECKE")), t(m.get("EINSTURZ")), t(m.get("ENDE")), m.get("PHASE"), d(m.get("STAU_MAX_M")), d(m.get("ABFLUSS_MAX_M3S")),
                        d(m.get("VOLUMEN_M3")), d(m.get("BRUECKE_M3")), d(m.get("GROESSTES_M3")), (int) d(m.get("STUECKE")), Daten.Geom.ausText(m.get("DAMM_XZ")),
                        Daten.Geom.ausText(m.get("RINNE_XZ")), m.get("REGIEBUCH").isEmpty() ? null : m.get("REGIEBUCH")));
            }
            Collections.reverse(l);
            return l;
        }
    }

    @Override public void regielauf(Daten.Regielauf l, Long sitzung) throws Fehler {
        Csv c = rl();
        synchronized (c) {
            LinkedHashMap<String, String> m = new LinkedHashMap<>();
            m.put("ID", Long.toString(c.naechste()));
            m.put("NAME", l.name());
            m.put("BEGINN", t(l.beginn()));
            m.put("ENDE", t(l.ende()));
            m.put("VOLLSTAENDIG", l.vollstaendig() ? "1" : "0");
            m.put("BILDER_S_MITTEL", n(l.bilderMittel()));
            m.put("BILDER_S_MIN", n(l.bilderMin()));
            m.put("GPU_MS_MITTEL", n(l.gpuMs()));
            m.put("FOTOS", Integer.toString(l.fotos()));
            m.put("QUALITAET", l.qualitaet() == null ? "" : l.qualitaet());
            m.put("BLATT_DATEI", l.blattDatei() == null ? "" : l.blattDatei());
            m.put("SITZUNG", s(sitzung));
            c.lesen().add(m);
            c.schreiben();
        }
    }

    /** Die Läufe der Regiebücher (für die Übernahme); das Blatt bleibt in seiner Datei. */
    public List<Daten.Regielauf> regielaeufe() throws Fehler {
        Csv c = rl();
        synchronized (c) {
            List<Daten.Regielauf> l = new ArrayList<>();
            for (Map<String, String> m : c.lesen())
                l.add(new Daten.Regielauf(m.get("NAME"), t(m.get("BEGINN")), t(m.get("ENDE")), "1".equals(m.get("VOLLSTAENDIG")), d(m.get("BILDER_S_MITTEL")),
                        d(m.get("BILDER_S_MIN")), d(m.get("GPU_MS_MITTEL")), (int) d(m.get("FOTOS")), m.get("QUALITAET"), null, m.get("BLATT_DATEI")));
            return l;
        }
    }

    /** Alle Zeilen der Chronik, wie sie in den Dateien stehen (für die Übernahme). */
    public List<Daten.Front> alleFronten() throws Fehler { return fronten(null, null, false).stream().filter(f -> Daten.EIGEN.equals(f.herkunft())).toList(); }
    public List<Daten.Kalbung> kalbungenAlle() throws Fehler { return alleKalbungen(); }
    public List<Object[]> eisbergeAlle() throws Fehler {
        Csv c = ei();
        synchronized (c) {
            List<Object[]> l = new ArrayList<>();
            for (Map<String, String> m : c.lesen()) l.add(new Object[]{ln(m.get("SITZUNG")), eisberg(m)});
            return l;
        }
    }

    @Override public int chronikLeeren(long sitzung) throws Fehler {
        int n = 0;
        String sid = Long.toString(sitzung);
        for (Csv c : new Csv[]{fr(), ka(), ei(), lf(), rl()}) {
            synchronized (c) {
                int vor = c.lesen().size();
                c.lesen().removeIf(m -> sid.equals(m.get("SITZUNG")));
                n += vor - c.lesen().size();
                if (vor != c.lesen().size()) c.schreiben();
            }
        }
        return n;
    }

    @Override public List<long[]> sitzungenMitChronik(int n) throws Fehler {
        Map<Long, long[]> m = new java.util.TreeMap<>(Collections.reverseOrder());
        for (Map<String, String> z : ka().lesen()) { Long s = ln(z.get("SITZUNG")); if (s != null) m.computeIfAbsent(s, k -> new long[]{k, 0, 0, 0})[2]++; }
        for (Map<String, String> z : ei().lesen()) { Long s = ln(z.get("SITZUNG")); if (s != null) m.computeIfAbsent(s, k -> new long[]{k, 0, 0, 0})[3]++; }
        for (Map<String, String> z : fr().lesen()) { Long s = ln(z.get("SITZUNG")); if (s != null) m.computeIfAbsent(s, k -> new long[]{k, 0, 0, 0}); }
        List<long[]> l = new ArrayList<>();
        for (long[] v : m.values()) {
            try { v[1] = Long.parseLong(sitzung(v[0]).getOrDefault("beginn", "0")); } catch (Fehler e) { v[1] = 0; }
            l.add(v);
            if (l.size() >= n) break;
        }
        return l;
    }

    // ------------------------------------------------------------------ Wetterlagen, Regiebücher, Fahrten

    @Override public List<Daten.Wetterlage> wetterlagen() { return Startdaten.wetterlagen(); }

    @Override public List<Daten.Regiebuch> regiebuecher() throws Fehler {
        List<Daten.Regiebuch> l = new ArrayList<>(Startdaten.regiebuecher());
        for (File f : dateien(sub("regiebuecher"))) {
            try {
                String t = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                Regie.Book b = Regie.parse(t);
                l.add(new Daten.Regiebuch(b.name(), t, b.about(), null, Daten.EIGEN));
            } catch (IOException | RuntimeException e) {
                com.dan.store.Protokoll.warnung("Dateien", "Regiebuch " + f.getName() + " nicht lesbar: " + e.getMessage());
            }
        }
        return l;
    }

    private static boolean mitgeliefertesBuch(String name) {
        for (Daten.Regiebuch r : Startdaten.regiebuecher()) if (r.name().equals(name)) return true;
        return false;
    }

    @Override public void speichern(Daten.Regiebuch r) throws Fehler {
        eigen(mitgeliefertesBuch(r.name()), r.name());
        try { Regie.parse(r.text()); } catch (RuntimeException e) { throw new Fehler("Regiebuch nicht lesbar: " + e.getMessage(), e); }
        schreibe(sub("regiebuecher", dateiname(r.name())), r.text());
    }

    @Override public void loeschenRegiebuch(String name) throws Fehler { eigen(mitgeliefertesBuch(name), name); loeschen("regiebuecher", name); }

    @Override public List<Daten.Fahrt> fahrten() { return Startdaten.fahrten(); }

    // ------------------------------------------------------------------ Ansichten

    private static Daten.Ansicht ansichtAus(Map<String, String> m, String zustand, byte[] png) {
        double[] k = new double[6];
        String[] n = {"x", "y", "z", "gier", "nick", "fov"};
        for (int i = 0; i < 6; i++) k[i] = Zahl.lies(m.get("kamera." + n[i]));
        return new Daten.Ansicht(Long.parseLong(m.get("id")), m.get("name"), null, "GEMERKT", k, null, "true".equals(m.get("unterWasser")), t(m.get("szenenzeit")), zustand, png,
                Daten.EIGEN);
    }

    @Override public List<Daten.Ansicht> ansichten() throws Fehler {
        List<Daten.Ansicht> l = new ArrayList<>(Startdaten.ansichten());
        List<Daten.Ansicht> g = new ArrayList<>();
        for (File f : dateien(sub("ansichten"))) {
            LinkedHashMap<String, String> m = lies(f);
            if (m.get("id") != null) g.add(ansichtAus(m, null, null));
        }
        g.sort((a, b) -> Long.compare(b.id(), a.id()));
        l.addAll(g);
        return l;
    }

    @Override public Daten.Ansicht ansicht(long id) throws Fehler {
        File f = sub("ansichten", id + ".txt");
        if (!f.isFile()) throw new Fehler("Ansicht " + id + " gibt es nicht");
        LinkedHashMap<String, String> m = lies(f);
        File z = sub("ansichten", id + ".zustand"), p = sub("ansichten", id + ".png");
        try {
            return ansichtAus(m, z.isFile() ? Files.readString(z.toPath(), StandardCharsets.UTF_8) : null, p.isFile() ? Files.readAllBytes(p.toPath()) : null);
        } catch (IOException e) {
            throw new Fehler("Ansicht " + id + " nicht lesbar: " + e.getMessage(), e);
        }
    }

    @Override public long speichern(Daten.Ansicht a, Long sitzung) throws Fehler {
        long id = naechsteNummer(sub("ansichten"));
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("id", Long.toString(id));
        m.put("name", a.name());
        String[] n = {"x", "y", "z", "gier", "nick", "fov"};
        for (int i = 0; i < 6; i++) m.put("kamera." + n[i], Zahl.text(a.kamera()[i]));
        m.put("unterWasser", Boolean.toString(a.unterWasser()));
        if (a.szenenzeit() != null) m.put("szenenzeit", a.szenenzeit().toString());
        if (sitzung != null) m.put("sitzung", sitzung.toString());
        schreibe(sub("ansichten", id + ".txt"), Felder.alsText(m, "Ansicht " + a.name()));
        if (a.zustand() != null) schreibe(sub("ansichten", id + ".zustand"), a.zustand());
        if (a.vorschau() != null) schreibe(sub("ansichten", id + ".png"), a.vorschau());
        return id;
    }

    @Override public void loeschenAnsicht(long id) throws Fehler {
        loesche(sub("ansichten", id + ".txt"), "Ansicht " + id);
        loesche(sub("ansichten", id + ".zustand"), "Ansicht " + id);
        loesche(sub("ansichten", id + ".png"), "Ansicht " + id);
    }

    // ------------------------------------------------------------------ Presets

    @Override public List<Daten.PaketPreset> presets(String paket, String klasse) throws Fehler {
        List<Daten.PaketPreset> l = new ArrayList<>();
        for (Daten.PaketPreset p : Startdaten.presets())
            if ((paket == null || p.paket().equals(paket)) && (klasse == null || p.klasse().equals(klasse))) l.add(p);
        File base = sub("presets");
        File[] ps = base.listFiles(File::isDirectory);
        if (ps == null) return l;
        for (File pd : ps) {
            if (paket != null && !pd.getName().equals(paket)) continue;
            File[] ks = pd.listFiles(File::isDirectory);
            if (ks == null) continue;
            for (File kd : ks) {
                if (klasse != null && !kd.getName().equals(klasse)) continue;
                for (File f : dateien(kd)) {
                    LinkedHashMap<String, String> m = lies(f);
                    String name = m.remove("#name"), about = m.remove("#beschreibung");
                    l.add(new Daten.PaketPreset(pd.getName(), kd.getName(), name == null ? f.getName().replace(".txt", "") : name, about, Daten.EIGEN, m));
                }
            }
        }
        return l;
    }

    private static boolean presetMitgeliefert(String paket, String klasse, String name) {
        for (Daten.PaketPreset p : Startdaten.presets()) if (p.paket().equals(paket) && p.klasse().equals(klasse) && p.name().equals(name)) return true;
        return false;
    }

    @Override public void speichern(Daten.PaketPreset p) throws Fehler {
        eigen(presetMitgeliefert(p.paket(), p.klasse(), p.name()), p.name());
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("#name", p.name());
        m.put("#beschreibung", p.beschreibung() == null ? "" : p.beschreibung());
        m.putAll(p.felder());
        schreibe(sub("presets", p.paket(), p.klasse(), dateiname(p.name())), Felder.alsText(m, "Preset " + p.paket() + "." + p.klasse() + " · " + p.name()));
    }

    @Override public void loeschenPreset(String paket, String klasse, String name) throws Fehler {
        eigen(presetMitgeliefert(paket, klasse, name), name);
        loesche(sub("presets", paket, klasse, dateiname(name)), name);
    }

    // ------------------------------------------------------------------ Sitzung

    @Override public Daten.Sitzung letzteSitzung(long ausser) throws Fehler {
        LinkedHashMap<String, String> m = super.letzteSitzungText(ausser);
        if (m == null) return null;
        String ende = m.get("ende"), zz = m.get("zustandZeit");
        double rs = Zahl.lies(m.get(REGIE_SEKUNDE)), eb = Zahl.lies(m.get(EISBERGE));
        String sz = m.get(SZ);
        return new Daten.Sitzung(Long.parseLong(m.get("id")), Long.parseLong(m.get("beginn")), ende == null || ende.isEmpty() ? null : Long.parseLong(ende),
                "true".equals(m.get("sauber")), m.get("zustand"), zz == null ? null : Long.parseLong(zz), leer(m.get(REGIEBUCH)), Double.isNaN(rs) ? null : rs,
                sz == null || sz.isEmpty() ? null : LocalDateTime.parse(sz), Double.isNaN(eb) ? null : (int) eb, leer(m.get(RUPTURA)));
    }

    private static String leer(String s) { return s == null || s.isEmpty() ? null : s; }
}
