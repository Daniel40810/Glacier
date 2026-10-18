package com.dan.glacier.data;

import com.dan.glacier.scene.Regie;
import com.dan.store.Zahl;
import com.dan.weather.Weather;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Schreibt die mitgelieferten Daten als SQL: {@code db/sql/04_startdaten.sql} (erzeugt von {@code tools.StartdatenSql},
 * nicht von Hand ändern). MERGE je Eintrag mit Namen; Kinder (Stichworte, Werte, Belege) und die mitgelieferten Fronten
 * werden gelöscht und neu angelegt. Eigene Zeilen (HERKUNFT = 'EIGEN') bleiben unberührt. Beliebig oft wiederholbar.
 * <p>
 * Geometrien: Punkte als {@code SDO_POINT_TYPE}, Linien und Flächen als WKT über {@code SDO_GEOMETRY(wkt, 8307)}; lange
 * Texte (WKT, Regiebücher) in Stücken zu höchstens 1 000 Zeichen als {@code TO_CLOB(…) || TO_CLOB(…)}, je Stück eine
 * Zeile, damit SQL Developer, SQLcl und der Einrichter jede Zeile ohne Grenze lesen.
 */
public final class StartdatenSql {
    private StartdatenSql() { }

    static final String M = "'MITGELIEFERT'";

    static String s(String v) {
        if (v == null) return "NULL";
        return "'" + v.replace("'", "''").replace("\r", "").replace("\n", "' || CHR(10) || '") + "'";
    }

    /** Ein langer Text als CLOB in Stücken. */
    static String clob(String v) {
        if (v == null) return "NULL";
        String t = v.replace("\r", "");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length(); i += 1000) {
            int e = Math.min(t.length(), i + 1000);
            // kein Stück endet mitten in einem Ersatzpaar
            if (e < t.length() && Character.isHighSurrogate(t.charAt(e - 1))) e--;
            b.append(i > 0 ? "\n    || " : "").append("TO_CLOB(").append(s(t.substring(i, e))).append(")");
            i = e - 1000;
        }
        return b.toString();
    }

    static String n(Double v) { return v == null || Double.isNaN(v) ? "NULL" : Zahl.text(Math.round(v * 1e6) / 1e6); }

    static String n(double v) { return n((Double) v); }

    static String n8(double v) { return Zahl.text(Math.round(v * 1e8) / 1e8); }

    static String b(boolean v) { return v ? "1" : "0"; }

    static String d(LocalDate d) { return d == null ? "NULL" : "DATE '" + d + "'"; }

    static String ts(LocalDateTime t) {
        return t == null ? "NULL" : "TIMESTAMP '" + t.toLocalDate() + " " + String.format("%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond()) + "'";
    }

    /** Ein Punkt in Länge und Breite. */
    static String punkt(double x, double z) {
        double[] ll = Daten.lonLat(x, z);
        return "MDSYS.SDO_GEOMETRY(2001, 8307, MDSYS.SDO_POINT_TYPE(" + Zahl.text(Math.round(ll[0] * 1e8) / 1e8) + ", " + Zahl.text(Math.round(ll[1] * 1e8) / 1e8) + ", NULL), NULL, NULL)";
    }

    static String geom(Daten.Geom g) {
        if (g == null) return "NULL";
        if (g.art().equals("PUNKT")) return punkt(g.xz()[0][0], g.xz()[0][1]);
        String w = g.wkt();
        if (w.length() <= 1000) return "MDSYS.SDO_GEOMETRY(" + s(w) + ", 8307)";
        return "MDSYS.SDO_GEOMETRY(" + clob(w) + ", 8307)";
    }

    private static void kopf(StringBuilder b, String t) {
        b.append("\n-- ").append("-".repeat(Math.max(4, 100 - t.length()))).append(' ').append(t).append('\n');
    }

    /** Die Zahlen der mitgelieferten Zeilen je Art (Zeile „-- ZAHLEN:“, der Einrichter prüft sie). */
    public static Map<String, Integer> zahlen(Startdaten.Szene sz) {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("Quellen", Startdaten.quellen().size());
        m.put("Befunde", Startdaten.befunde().size());
        int belege = 0;
        for (Daten.Befund f : Startdaten.befunde()) belege += f.quellen().size();
        m.put("Belege", belege);
        m.put("Gelände", 1);
        m.put("Fronten", sz.fronten().size());
        m.put("Rupturas", Startdaten.rupturen().size());
        m.put("Wetterlagen", Startdaten.wetterlagen().size());
        m.put("Regiebücher", Startdaten.regiebuecher().size());
        int sw = 0;
        for (Daten.Regiebuch r : Startdaten.regiebuecher()) sw += Regie.parse(r.text()).zeilen().size();
        m.put("Stichworte", sw);
        m.put("Ansichten", Startdaten.ansichten().size());
        m.put("Fahrten", Startdaten.fahrten().size());
        m.put("Presets", Startdaten.presets().size());
        int pw = 0;
        for (Daten.PaketPreset p : Startdaten.presets()) pw += p.felder().size();
        m.put("Presetwerte", pw);
        return m;
    }

    /** Das ganze Skript. */
    public static String sql(Startdaten.Szene sz) {
        StringBuilder b = new StringBuilder();
        Map<String, Integer> z = zahlen(sz);
        b.append("""
                -- =====================================================================================================
                -- Glacier · Datenbank · 04_startdaten.sql
                -- ERZEUGT aus com.dan.glacier.data.Startdaten (tools.StartdatenSql) – nicht von Hand ändern.
                -- Mitgelieferte Zeilen (HERKUNFT = 'MITGELIEFERT') werden angelegt oder wiederhergestellt; eigene Zeilen
                -- bleiben unberührt. Beliebig oft wiederholbar. Gelände und Fronten sind aus der Szene gerechnet (Gelände
                -- aus daten/gelaende, Prüfsummen in GLC_GELAENDE); die Rupturas aus data/ruptura.txt (recherchiert am
                -- 18.10.2026, Quellen in GLC_QUELLE).
                -- =====================================================================================================
                """);
        StringBuilder zl = new StringBuilder("-- ZAHLEN:");
        for (Map.Entry<String, Integer> e : z.entrySet()) zl.append(' ').append(e.getKey()).append('=').append(e.getValue()).append(';');
        b.append(zl).append("\n\n-- Die Quellen enthalten „&“: in SQL Developer keine Ersetzungsvariablen\nSET DEFINE OFF\n");

        kopf(b, "Ausbaustufe");
        b.append("MERGE INTO GLC_SCHEMA t USING (SELECT 1 AS VERSION FROM DUAL) q ON (t.VERSION = q.VERSION)\n")
                .append("WHEN NOT MATCHED THEN INSERT (VERSION, APP_VERSION, TEXT) VALUES (1, 'Phase 15', ")
                .append(s("Oberfläche und Datenbank: 21 Tabellen, die Chronik von Front, Kalbung, Eisbergen und Ruptura, Regiebücher als Text, Befunde")).append(");\n");

        kopf(b, "Quellen");
        for (Daten.Quelle q : Startdaten.quellen()) {
            b.append("MERGE INTO GLC_QUELLE t USING (SELECT ").append(s(q.kurz())).append(" AS KURZ FROM DUAL) q ON (t.KURZ = q.KURZ)\n")
                    .append("WHEN MATCHED THEN UPDATE SET t.TITEL = ").append(s(q.titel())).append(", t.URL = ").append(s(q.url())).append(", t.ART = ").append(s(q.art()))
                    .append(", t.ABGERUFEN = ").append(d(q.abgerufen())).append(", t.BEMERKUNG = ").append(s(q.bemerkung())).append(" WHERE t.HERKUNFT = ").append(M).append('\n')
                    .append("WHEN NOT MATCHED THEN INSERT (KURZ, TITEL, URL, ART, ABGERUFEN, BEMERKUNG, HERKUNFT) VALUES (").append(s(q.kurz())).append(", ")
                    .append(s(q.titel())).append(", ").append(s(q.url())).append(", ").append(s(q.art())).append(", ").append(d(q.abgerufen())).append(", ").append(s(q.bemerkung()))
                    .append(", ").append(M).append(");\n");
        }

        kopf(b, "Befunde");
        for (Daten.Befund f : Startdaten.befunde()) {
            b.append("MERGE INTO GLC_BEFUND t USING (SELECT ").append(s(f.thema())).append(" AS THEMA, ").append(s(f.gegenstand()))
                    .append(" AS GEGENSTAND FROM DUAL) q ON (t.THEMA = q.THEMA AND t.GEGENSTAND = q.GEGENSTAND)\n")
                    .append("WHEN MATCHED THEN UPDATE SET t.BEFUND = ").append(s(f.befund())).append(", t.TEXT = ").append(s(f.text())).append(" WHERE t.HERKUNFT = ").append(M).append('\n')
                    .append("WHEN NOT MATCHED THEN INSERT (THEMA, GEGENSTAND, BEFUND, TEXT, HERKUNFT) VALUES (").append(s(f.thema())).append(", ").append(s(f.gegenstand()))
                    .append(", ").append(s(f.befund())).append(", ").append(s(f.text())).append(", ").append(M).append(");\n");
        }
        b.append("DELETE FROM GLC_BEFUND_QUELLE WHERE BEFUND_ID IN (SELECT ID FROM GLC_BEFUND WHERE HERKUNFT = ").append(M).append(");\n");
        for (Daten.Befund f : Startdaten.befunde())
            for (String q : f.quellen())
                b.append("INSERT INTO GLC_BEFUND_QUELLE (BEFUND_ID, QUELLE_ID) SELECT b.ID, q.ID FROM GLC_BEFUND b, GLC_QUELLE q WHERE b.THEMA = ").append(s(f.thema()))
                        .append(" AND b.GEGENSTAND = ").append(s(f.gegenstand())).append(" AND b.HERKUNFT = ").append(M).append(" AND q.KURZ = ").append(s(q)).append(";\n");

        kopf(b, "Gelände");
        Daten.Gelaende g = sz.gelaende();
        String[] h = g.dateien().get("HOEHE"), gr = g.dateien().get("GRUND"), hk = g.dateien().get("HERKUNFT"), fr = g.dateien().get("FRONT");
        String werte = "t.AUSSCHNITT = " + geom(g.ausschnitt()) + ", t.URSPRUNG_LAENGE = " + n8(g.ursprungLon()) + ", t.URSPRUNG_BREITE = " + n8(g.ursprungLat())
                + ", t.NORD_GRAD = " + n(g.nordGrad()) + ", t.ZELLE_M = " + n(g.zelleM()) + ", t.ZELLEN = " + g.zellen() + ", t.SEESPIEGEL_M = " + n(g.seespiegelM())
                + ", t.DGM_VON = " + d(g.dgmVon()) + ", t.DGM_BIS = " + d(g.dgmBis()) + ", t.DATEI_HOEHE = " + s(h[0]) + ", t.SHA_HOEHE = " + s(h[1])
                + ", t.DATEI_GRUND = " + s(gr[0]) + ", t.SHA_GRUND = " + s(gr[1]) + ", t.DATEI_HERKUNFT = " + s(hk[0]) + ", t.SHA_HERKUNFT = " + s(hk[1])
                + ", t.DATEI_FRONT = " + s(fr[0]) + ", t.SHA_FRONT = " + s(fr[1]) + ", t.QUELLE_ID = (SELECT MAX(ID) FROM GLC_QUELLE WHERE KURZ = " + s(g.quelle())
                + "), t.BEMERKUNG = " + s(g.bemerkung());
        b.append("MERGE INTO GLC_GELAENDE t USING (SELECT ").append(s(g.name())).append(" AS NAME FROM DUAL) q ON (t.NAME = q.NAME)\n")
                .append("WHEN MATCHED THEN UPDATE SET ").append(werte).append(" WHERE t.HERKUNFT = ").append(M).append('\n')
                .append("WHEN NOT MATCHED THEN INSERT (NAME, URSPRUNG_LAENGE, URSPRUNG_BREITE, ZELLE_M, ZELLEN, SEESPIEGEL_M, HERKUNFT) VALUES (").append(s(g.name())).append(", ")
                .append(n8(g.ursprungLon())).append(", ").append(n8(g.ursprungLat())).append(", ").append(n(g.zelleM())).append(", ").append(g.zellen()).append(", ")
                .append(n(g.seespiegelM())).append(", ").append(M).append(");\n");
        // die Geometrie und die Dateien beim ersten Mal (MERGE kann im INSERT-Zweig keine langen Ausdrücke wiederholen; so bleibt es eine Stelle)
        b.append("UPDATE GLC_GELAENDE t SET ").append(werte).append(" WHERE t.NAME = ").append(s(g.name())).append(" AND t.HERKUNFT = ").append(M).append(";\n");

        kopf(b, "Fronten (Gelände und Jahresgang)");
        b.append("DELETE FROM GLC_FRONT WHERE HERKUNFT = ").append(M).append(";\n");
        for (Daten.Front f : sz.fronten())
            b.append("INSERT INTO GLC_FRONT (GELAENDE_ID, SZENENZEIT, GUELTIG_BIS, ART, NAME, LINIE, LAENGE_M, VORSTOSS_M, FLAECHE_DIFF_M2, HOEHE_MITTEL_M, QUELLE_ID, BEMERKUNG, HERKUNFT)\n")
                    .append("  SELECT (SELECT MIN(ID) FROM GLC_GELAENDE), ").append(ts(f.szenenzeit())).append(", ").append(ts(f.gueltigBis())).append(", ").append(s(f.art()))
                    .append(", ").append(s(f.name())).append(",\n    ").append(geom(f.linie())).append(",\n    ").append(n(Math.round(f.laengeM() * 10) / 10.0)).append(", ")
                    .append(n(f.vorstossM())).append(", ").append(n(f.flaecheDiffM2())).append(", ").append(n(f.hoeheMittelM())).append(", ")
                    .append(f.quelle() == null ? "NULL" : "(SELECT MAX(ID) FROM GLC_QUELLE WHERE KURZ = " + s(f.quelle()) + ")").append(", ").append(s(f.bemerkung())).append(", ")
                    .append(M).append(" FROM DUAL;\n");

        kopf(b, "Rupturas (belegt)");
        for (Daten.Ruptura r : Startdaten.rupturen()) {
            String text = r.text() == null ? null : r.text();
            if (r.quellen().size() > 1) text = (text == null ? "" : text + " ") + "(Quellen: " + String.join(", ", r.quellen()) + ")";
            String qid = r.quellen().isEmpty() ? "NULL" : "(SELECT MAX(ID) FROM GLC_QUELLE WHERE KURZ = " + s(r.quellen().get(0)) + ")";
            b.append("MERGE INTO GLC_RUPTURA t USING (SELECT ").append(r.jahr()).append(" AS JAHR, ").append(r.nr()).append(" AS NR FROM DUAL) q ON (t.JAHR = q.JAHR AND t.NR = q.NR)\n")
                    .append("WHEN MATCHED THEN UPDATE SET t.DATUM = ").append(d(r.datum())).append(", t.GENAUIGKEIT = ").append(s(r.genauigkeit())).append(", t.STAU_M = ")
                    .append(n(r.stauM())).append(", t.ABFLUSS_M3S = ").append(n(r.abflussM3s())).append(", t.DAUER_H = ").append(n(r.dauerH())).append(", t.TEXT = ").append(s(text))
                    .append(", t.QUELLE_ID = ").append(qid).append(" WHERE t.HERKUNFT = ").append(M).append('\n')
                    .append("WHEN NOT MATCHED THEN INSERT (JAHR, NR, DATUM, GENAUIGKEIT, STAU_M, ABFLUSS_M3S, DAUER_H, TEXT, QUELLE_ID, HERKUNFT) VALUES (").append(r.jahr()).append(", ")
                    .append(r.nr()).append(", ").append(d(r.datum())).append(", ").append(s(r.genauigkeit())).append(", ").append(n(r.stauM())).append(", ").append(n(r.abflussM3s()))
                    .append(", ").append(n(r.dauerH())).append(", ").append(s(text)).append(", ").append(qid).append(", ").append(M).append(");\n");
        }

        kopf(b, "Wetterlagen");
        for (Daten.Wetterlage w : Startdaten.wetterlagen()) {
            StringBuilder cols = new StringBuilder("NAME, BEAUFORT, PHASE"), vals = new StringBuilder(s(w.name()) + ", " + n(w.beaufort()) + ", " + s(w.phase()));
            StringBuilder upd = new StringBuilder("t.BEAUFORT = " + n(w.beaufort()) + ", t.PHASE = " + s(w.phase()));
            for (Map.Entry<Weather.Var, String> e : Verbindung.WETTER.entrySet()) {
                String v = wert(e.getKey(), w.werte()[e.getKey().ordinal()]);
                cols.append(", ").append(e.getValue());
                vals.append(", ").append(v);
                upd.append(", t.").append(e.getValue()).append(" = ").append(v);
            }
            b.append("MERGE INTO GLC_WETTERLAGE t USING (SELECT ").append(s(w.name())).append(" AS NAME FROM DUAL) q ON (t.NAME = q.NAME)\n")
                    .append("WHEN MATCHED THEN UPDATE SET ").append(upd).append(", t.MONATE = ").append(s(w.monate())).append(", t.BESCHREIBUNG = ").append(s(w.beschreibung()))
                    .append(" WHERE t.HERKUNFT = ").append(M).append('\n')
                    .append("WHEN NOT MATCHED THEN INSERT (").append(cols).append(", MONATE, BESCHREIBUNG, HERKUNFT)\n  VALUES (").append(vals).append(", ").append(s(w.monate()))
                    .append(", ").append(s(w.beschreibung())).append(", ").append(M).append(");\n");
        }

        kopf(b, "Regiebücher");
        for (Daten.Regiebuch r : Startdaten.regiebuecher()) {
            Regie.Book bk = Regie.parse(r.text());
            Startdaten.Kopf k = Startdaten.Kopf.aus(bk);
            String wetter = Regie.wetterBlock(r.text());
            String upd = "t.BESCHREIBUNG = " + s(r.beschreibung()) + ", t.DATEI = " + s(r.datei()) + ", t.SZENENZEIT = " + ts(k.szenenzeit()) + ", t.ZEITRAFFER = " + n(k.raffer())
                    + ", t.LAENGE_S = " + n(bk.length()) + ", t.FOTOS = " + k.fotos() + ", t.BLATT = " + b(k.blatt()) + ", t.WETTER = " + s(wetter);
            b.append("MERGE INTO GLC_REGIEBUCH t USING (SELECT ").append(s(r.name())).append(" AS NAME FROM DUAL) q ON (t.NAME = q.NAME)\n")
                    .append("WHEN MATCHED THEN UPDATE SET ").append(upd).append(" WHERE t.HERKUNFT = ").append(M).append('\n')
                    .append("WHEN NOT MATCHED THEN INSERT (NAME, LAENGE_S, TEXT, HERKUNFT) VALUES (").append(s(r.name())).append(", ").append(n(bk.length()))
                    .append(", '-', ").append(M).append(");\n");
            b.append("UPDATE GLC_REGIEBUCH t SET ").append(upd).append(",\n  t.TEXT = ").append(clob(r.text())).append("\n  WHERE t.NAME = ").append(s(r.name()))
                    .append(" AND t.HERKUNFT = ").append(M).append(";\n");
            String eltern = "FROM GLC_REGIEBUCH WHERE NAME = " + s(r.name()) + " AND HERKUNFT = " + M;
            b.append("DELETE FROM GLC_STICHWORT WHERE REGIEBUCH_ID IN (SELECT ID ").append(eltern).append(");\n");
            int nr = 0;
            for (Regie.Zeile zz : bk.zeilen())
                b.append("INSERT INTO GLC_STICHWORT (REGIEBUCH_ID, NR, SEKUNDE, BEFEHL, ARGUMENTE, TEXT) SELECT ID, ").append(++nr).append(", ").append(n(zz.sekunde())).append(", ")
                        .append(s(zz.befehl().toUpperCase(java.util.Locale.ROOT))).append(", ").append(s(zz.argumente().isEmpty() ? null : zz.argumente())).append(", ")
                        .append(s(zz.text().isEmpty() ? null : zz.text())).append(' ').append(eltern).append(";\n");
        }

        kopf(b, "Ansichten (Tasten 1–8)");
        for (Daten.Ansicht a : Startdaten.ansichten()) {
            double[] c = a.kamera();
            String upd = "t.TASTE = " + a.taste() + ", t.KAM_X = " + n(c[0]) + ", t.KAM_Z = " + n(c[2]) + ", t.KAM_GIER_GRAD = " + n(c[3]) + ", t.KAM_NICK_GRAD = " + n(c[4])
                    + ", t.KAM_FOV_GRAD = " + n(c[5]) + ", t.AUGE_UEBER_GRUND_M = " + n(a.augeUeberGrund()) + ", t.UNTER_WASSER = " + b(a.unterWasser())
                    + ", t.LAGE = " + punkt(c[0], c[2]);
            b.append("MERGE INTO GLC_ANSICHT t USING (SELECT ").append(s(a.name())).append(" AS NAME FROM DUAL) q ON (t.NAME = q.NAME AND t.ART = 'FEST')\n")
                    .append("WHEN MATCHED THEN UPDATE SET ").append(upd).append(" WHERE t.HERKUNFT = ").append(M).append('\n')
                    .append("WHEN NOT MATCHED THEN INSERT (NAME, TASTE, ART, KAM_X, KAM_Z, KAM_GIER_GRAD, KAM_NICK_GRAD, KAM_FOV_GRAD, AUGE_UEBER_GRUND_M, UNTER_WASSER, LAGE, HERKUNFT)\n  VALUES (")
                    .append(s(a.name())).append(", ").append(a.taste()).append(", 'FEST', ").append(n(c[0])).append(", ").append(n(c[2])).append(", ").append(n(c[3])).append(", ")
                    .append(n(c[4])).append(", ").append(n(c[5])).append(", ").append(n(a.augeUeberGrund())).append(", ").append(b(a.unterWasser())).append(", ")
                    .append(punkt(c[0], c[2])).append(", ").append(M).append(");\n");
        }

        kopf(b, "Kamerafahrten");
        for (Daten.Fahrt f : Startdaten.fahrten()) {
            String taste = f.taste() == null ? "NULL" : f.taste().toString();
            b.append("MERGE INTO GLC_FAHRT t USING (SELECT ").append(s(f.name())).append(" AS NAME FROM DUAL) q ON (t.NAME = q.NAME)\n")
                    .append("WHEN MATCHED THEN UPDATE SET t.TASTE = ").append(taste).append(", t.ART = ").append(s(f.art())).append(", t.SCHLEIFE = ").append(b(f.schleife()))
                    .append(", t.DAUER_S = ").append(n(f.dauerS())).append(", t.BESCHREIBUNG = ").append(s(f.beschreibung())).append(" WHERE t.HERKUNFT = ").append(M).append('\n')
                    .append("WHEN NOT MATCHED THEN INSERT (NAME, TASTE, ART, SCHLEIFE, DAUER_S, BESCHREIBUNG, HERKUNFT) VALUES (").append(s(f.name())).append(", ").append(taste)
                    .append(", ").append(s(f.art())).append(", ").append(b(f.schleife())).append(", ").append(n(f.dauerS())).append(", ").append(s(f.beschreibung())).append(", ")
                    .append(M).append(");\n");
        }

        kopf(b, "Presets der Packages");
        for (Daten.PaketPreset p : Startdaten.presets()) {
            String key = "PAKET = " + s(p.paket()) + " AND KLASSE = " + s(p.klasse()) + " AND NAME = " + s(p.name());
            b.append("MERGE INTO GLC_PAKET_PRESET t USING (SELECT ").append(s(p.paket())).append(" AS PAKET, ").append(s(p.klasse())).append(" AS KLASSE, ").append(s(p.name()))
                    .append(" AS NAME FROM DUAL) q ON (t.PAKET = q.PAKET AND t.KLASSE = q.KLASSE AND t.NAME = q.NAME)\n")
                    .append("WHEN MATCHED THEN UPDATE SET t.BESCHREIBUNG = ").append(s(p.beschreibung())).append(" WHERE t.HERKUNFT = ").append(M).append('\n')
                    .append("WHEN NOT MATCHED THEN INSERT (PAKET, KLASSE, NAME, BESCHREIBUNG, HERKUNFT) VALUES (").append(s(p.paket())).append(", ").append(s(p.klasse()))
                    .append(", ").append(s(p.name())).append(", ").append(s(p.beschreibung())).append(", ").append(M).append(");\n");
            String eltern = "FROM GLC_PAKET_PRESET WHERE " + key + " AND HERKUNFT = " + M;
            b.append("DELETE FROM GLC_PAKET_WERT WHERE PAKET_PRESET_ID IN (SELECT ID ").append(eltern).append(");\n");
            Map<String, String> typ = Startdaten.typen(p);
            for (Map.Entry<String, String> e : p.felder().entrySet())
                b.append("INSERT INTO GLC_PAKET_WERT (PAKET_PRESET_ID, FELD, TYP, WERT) SELECT ID, ").append(s(e.getKey())).append(", ").append(s(typ.getOrDefault(e.getKey(), "STRING")))
                        .append(", ").append(s(e.getValue().isEmpty() ? null : e.getValue())).append(' ').append(eltern).append(";\n");
        }
        b.append("\nCOMMIT;\n\n-- Ende 04_startdaten.sql\n");
        return b.toString();
    }

    /** Die Zahl einer Wetterspalte als SQL (Wolkenart als Name). */
    static String wert(Weather.Var v, double x) {
        if (Double.isNaN(x)) return "NULL";
        if (v == Weather.Var.CLOUD) return s(Weather.CloudKind.values()[(int) Math.max(0, Math.min(Weather.CloudKind.values().length - 1, Math.round(x)))].name());
        return n(x);
    }
}
