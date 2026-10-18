package com.dan.glacier.data;

import com.dan.glacier.scene.Regie;
import com.dan.store.Db;
import com.dan.store.Protokoll;
import com.dan.store.Zahl;
import com.dan.weather.Weather;

import java.io.StringReader;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Speichern im Schema GLACIER (F20 A, Phase 15). Eine Verbindung, alle Zugriffe synchronisiert (der Faden der Daten,
 * die Chronik und die Oberfläche teilen sie). Punkte schreibt sie als {@code SDO_POINT_TYPE} (so lesen die Sichten
 * Länge und Breite als Zahl), Linien und Flächen als WKT über {@code SDO_GEOMETRY(clob, 8307)}; gelesen wird mit
 * {@code SDO_UTIL.TO_WKTGEOMETRY}, ohne {@code sdoapi}. Szenenzeit als TIMESTAMP in Ortszeit (F24). Mitgelieferte
 * Zeilen werden nie überschrieben.
 */
public final class DbSpeicher implements Speicher {
    private final Db.Konfig konfig;
    private Connection c;
    private final String wo;

    public DbSpeicher(Db.Konfig k) throws Fehler {
        this.konfig = k;
        try {
            c = Db.oeffnen(k);
            String w = Db.einWert(c, "select user || ' auf ' || sys_context('USERENV','CON_NAME') from dual");
            wo = "Datenbank " + w;
        } catch (SQLException e) {
            throw new Fehler("Keine Verbindung zu " + k.kurz() + ": " + Db.erste(e), e);
        }
    }

    /** Für Prüfungen und den Einrichter: die offene Verbindung. */
    public synchronized Connection verbindung() throws Fehler {
        try {
            if (c == null || c.isClosed() || !c.isValid(3)) c = Db.oeffnen(konfig);
            return c;
        } catch (SQLException e) {
            throw new Fehler("Verbindung verloren: " + Db.erste(e), e);
        }
    }

    @Override public String wo() { return wo; }
    @Override public boolean datenbank() { return true; }

    private interface Arbeit<T> { T tun(Connection c) throws SQLException; }

    private synchronized <T> T mit(String was, Arbeit<T> a) throws Fehler {
        Connection cc = verbindung();
        try {
            return a.tun(cc);
        } catch (SQLException e) {
            throw new Fehler(was + ": " + Db.erste(e), e);
        }
    }

    /** In einer Transaktion: alles oder nichts. */
    private synchronized <T> T zusammen(String was, Arbeit<T> a) throws Fehler {
        Connection cc = verbindung();
        try {
            cc.setAutoCommit(false);
            T t = a.tun(cc);
            cc.commit();
            return t;
        } catch (SQLException e) {
            try { cc.rollback(); } catch (SQLException e2) { /* weiter */ }
            throw new Fehler(was + ": " + Db.erste(e), e);
        } finally {
            try { cc.setAutoCommit(true); } catch (SQLException e) { /* weiter */ }
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private static int zahl(Connection c, String sql, Object... a) throws SQLException {
        String v = Db.einWert(c, sql, a);
        return v == null ? 0 : Integer.parseInt(v);
    }

    private static long neueId(Connection c, String seq) throws SQLException { return Long.parseLong(Db.einWert(c, "SELECT TO_CHAR(" + seq + ".CURRVAL) FROM DUAL")); }

    static void setD(PreparedStatement st, int i, Double v) throws SQLException {
        if (v == null || Double.isNaN(v) || Double.isInfinite(v)) st.setNull(i, Types.NUMERIC); else st.setDouble(i, v);
    }

    static void setS(PreparedStatement st, int i, String v) throws SQLException {
        if (v == null || v.isEmpty()) st.setNull(i, Types.VARCHAR);
        else if (v.length() > 3900) st.setCharacterStream(i, new StringReader(v), v.length());
        else st.setString(i, v);
    }

    private static void setClob(PreparedStatement st, int i, String v) throws SQLException {
        if (v == null) st.setNull(i, Types.CLOB); else st.setCharacterStream(i, new StringReader(v), v.length());
    }

    private static void setL(PreparedStatement st, int i, Long v) throws SQLException {
        if (v == null || v < 0) st.setNull(i, Types.NUMERIC); else st.setLong(i, v);
    }

    private static void setT(PreparedStatement st, int i, LocalDateTime t) throws SQLException {
        if (t == null) st.setNull(i, Types.TIMESTAMP); else st.setTimestamp(i, Timestamp.valueOf(t));
    }

    private static LocalDateTime t(ResultSet r, String col) throws SQLException {
        Timestamp ts = r.getTimestamp(col);
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static Double dbl(ResultSet r, String col) throws SQLException {
        double v = r.getDouble(col);
        return r.wasNull() ? null : v;
    }

    private static double d(ResultSet r, String col) throws SQLException {
        double v = r.getDouble(col);
        return r.wasNull() ? Double.NaN : v;
    }

    private static Integer integer(ResultSet r, String col) throws SQLException {
        int v = r.getInt(col);
        return r.wasNull() ? null : v;
    }

    private static String clob(ResultSet r, String col) throws SQLException {
        Clob cl = r.getClob(col);
        return cl == null ? null : cl.getSubString(1, (int) cl.length());
    }

    /** SQL für eine Geometrie: Punkt als SDO_POINT_TYPE mit zwei Platzhaltern, sonst WKT mit einem; null: NULL. */
    private static String geomSql(Daten.Geom g) {
        if (g == null) return "NULL";
        if (g.art().equals("PUNKT")) return "MDSYS.SDO_GEOMETRY(2001, 8307, MDSYS.SDO_POINT_TYPE(?, ?, NULL), NULL, NULL)";
        return "MDSYS.SDO_GEOMETRY(?, 8307)";
    }

    /** Die Platzhalter von {@link #geomSql} füllen; gibt den nächsten Index zurück. */
    private static int setGeom(PreparedStatement st, int i, Daten.Geom g) throws SQLException {
        if (g == null) return i;
        if (g.art().equals("PUNKT")) {
            double[] ll = Daten.lonLat(g.xz()[0][0], g.xz()[0][1]);
            st.setDouble(i, Math.round(ll[0] * 1e8) / 1e8);
            st.setDouble(i + 1, Math.round(ll[1] * 1e8) / 1e8);
            return i + 2;
        }
        setClob(st, i, g.wkt());
        return i + 1;
    }

    private static boolean mitgeliefert(Connection c, String tabelle, String name) throws SQLException {
        return zahl(c, "SELECT COUNT(*) FROM " + tabelle + " WHERE NAME = ? AND HERKUNFT = 'MITGELIEFERT'", name) > 0;
    }

    private void loeschen(String tabelle, String name) throws Fehler {
        zusammen("Löschen", c -> {
            if (mitgeliefert(c, tabelle, name)) throw new SQLException("„" + name + "“ ist mitgeliefert und wird nicht gelöscht");
            try (PreparedStatement st = c.prepareStatement("DELETE FROM " + tabelle + " WHERE NAME = ? AND HERKUNFT = 'EIGEN'")) { st.setString(1, name); st.executeUpdate(); }
            return null;
        });
    }

    private static final String REGIEBUCH_ID = "(SELECT MAX(ID) FROM GLC_REGIEBUCH WHERE NAME = ?)";

    // ------------------------------------------------------------------ Stand

    @Override public Daten.Stand stand() throws Fehler {
        return mit("Stand", c -> new Daten.Stand(zahl(c, "SELECT COUNT(*) FROM GLC_FRONT"), zahl(c, "SELECT COUNT(*) FROM GLC_KALBUNG"),
                zahl(c, "SELECT COUNT(*) FROM GLC_EISBERG"), zahl(c, "SELECT COUNT(*) FROM GLC_RUPTURA"), zahl(c, "SELECT COUNT(*) FROM GLC_RUPTURA_LAUF"),
                zahl(c, "SELECT COUNT(*) FROM GLC_REGIEBUCH"), zahl(c, "SELECT COUNT(*) FROM GLC_REGIELAUF"), zahl(c, "SELECT COUNT(*) FROM GLC_WETTERLAGE"),
                zahl(c, "SELECT COUNT(*) FROM GLC_FAHRT"), zahl(c, "SELECT COUNT(*) FROM GLC_ANSICHT"), zahl(c, "SELECT COUNT(*) FROM GLC_PAKET_PRESET"),
                zahl(c, "SELECT COUNT(*) FROM GLC_BEFUND"), zahl(c, "SELECT COUNT(*) FROM GLC_QUELLE"), letzte(c, -1)));
    }

    // ------------------------------------------------------------------ Quellen, Befunde, Rupturas

    @Override public List<Daten.Quelle> quellen() throws Fehler {
        return mit("Quellen", c -> {
            List<Daten.Quelle> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT KURZ, TITEL, URL, ART, ABGERUFEN, BEMERKUNG FROM GLC_QUELLE ORDER BY ID")) {
                while (r.next()) {
                    java.sql.Date ab = r.getDate(5);
                    l.add(new Daten.Quelle(r.getString(1), r.getString(2), r.getString(3), r.getString(4), ab == null ? null : ab.toLocalDate(), r.getString(6)));
                }
            }
            return l;
        });
    }

    @Override public List<Daten.Befund> befunde() throws Fehler {
        return mit("Befunde", c -> {
            Map<Long, List<String>> q = new LinkedHashMap<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT x.BEFUND_ID, q.KURZ FROM GLC_BEFUND_QUELLE x JOIN GLC_QUELLE q ON q.ID = x.QUELLE_ID ORDER BY x.ID")) {
                while (r.next()) q.computeIfAbsent(r.getLong(1), k -> new ArrayList<>()).add(r.getString(2));
            }
            List<Daten.Befund> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT ID, THEMA, GEGENSTAND, BEFUND, TEXT FROM GLC_BEFUND ORDER BY ID")) {
                while (r.next()) l.add(new Daten.Befund(r.getString(2), r.getString(3), r.getString(4), r.getString(5), q.getOrDefault(r.getLong(1), List.of())));
            }
            return l;
        });
    }

    @Override public List<Daten.Ruptura> rupturen() throws Fehler {
        return mit("Rupturas", c -> {
            List<Daten.Ruptura> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT r.JAHR, r.NR, r.DATUM, r.GENAUIGKEIT, r.STAU_M, r.ABFLUSS_M3S, r.DAUER_H, r.TEXT, "
                    + "q.KURZ FROM GLC_RUPTURA r LEFT JOIN GLC_QUELLE q ON q.ID = r.QUELLE_ID ORDER BY r.DATUM, r.NR")) {
                while (r.next())
                    l.add(new Daten.Ruptura(r.getInt(1), r.getInt(2), r.getDate(3).toLocalDate(), r.getString(4), dbl(r, "STAU_M"), dbl(r, "ABFLUSS_M3S"), dbl(r, "DAUER_H"),
                            r.getString(8), r.getString(9) == null ? List.of() : List.of(r.getString(9))));
            }
            return l;
        });
    }

    // ------------------------------------------------------------------ Fronten

    @Override public long front(Daten.Front f, Long sitzung) throws Fehler {
        return zusammen("Front schreiben", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GLC_FRONT (GELAENDE_ID, SZENENZEIT, GUELTIG_BIS, ART, NAME, LINIE, LAENGE_M, VORSTOSS_M, FLAECHE_DIFF_M2, "
                    + "HOEHE_MITTEL_M, SITZUNG_ID, BEMERKUNG, HERKUNFT) VALUES ((SELECT MIN(ID) FROM GLC_GELAENDE), ?, ?, ?, ?, " + geomSql(f.linie())
                    + ", ?, ?, ?, ?, ?, ?, 'EIGEN')")) {
                setT(st, 1, f.szenenzeit());
                setT(st, 2, f.gueltigBis());
                st.setString(3, f.art());
                setS(st, 4, f.name());
                int i = setGeom(st, 5, f.linie());
                setD(st, i, Math.round(f.laengeM() * 10) / 10.0);
                setD(st, i + 1, Math.round(f.vorstossM() * 100) / 100.0);
                setD(st, i + 2, f.flaecheDiffM2());
                setD(st, i + 3, f.hoeheMittelM());
                setL(st, i + 4, sitzung);
                setS(st, i + 5, f.bemerkung());
                st.executeUpdate();
            }
            return neueId(c, "GLC_FRONT_SEQ");
        });
    }

    @Override public List<Daten.Front> fronten(LocalDateTime von, LocalDateTime bis, boolean ohneLinie) throws Fehler {
        return mit("Fronten", c -> {
            List<Daten.Front> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT f.ID, f.SZENENZEIT, f.GUELTIG_BIS, f.ART, f.NAME, " + (ohneLinie ? "NULL" : "MDSYS.SDO_UTIL.TO_WKTGEOMETRY(f.LINIE)")
                    + " AS WKT, f.LAENGE_M, f.VORSTOSS_M, f.FLAECHE_DIFF_M2, f.HOEHE_MITTEL_M, q.KURZ, f.BEMERKUNG, f.HERKUNFT FROM GLC_FRONT f "
                    + "LEFT JOIN GLC_QUELLE q ON q.ID = f.QUELLE_ID WHERE (? IS NULL OR f.SZENENZEIT >= ?) AND (? IS NULL OR f.SZENENZEIT < ?) ORDER BY f.SZENENZEIT, f.ID")) {
                setT(st, 1, von); setT(st, 2, von); setT(st, 3, bis); setT(st, 4, bis);
                try (ResultSet r = st.executeQuery()) {
                    while (r.next())
                        l.add(new Daten.Front(r.getLong(1), t(r, "SZENENZEIT"), t(r, "GUELTIG_BIS"), r.getString(4), r.getString(5), ohneLinie ? null : Daten.Geom.ausWkt(clob(r, "WKT")),
                                d(r, "LAENGE_M"), d(r, "VORSTOSS_M"), dbl(r, "FLAECHE_DIFF_M2"), dbl(r, "HOEHE_MITTEL_M"), r.getString(11), r.getString(12), r.getString(13)));
                }
            }
            return l;
        });
    }

    // ------------------------------------------------------------------ Kalbungen

    @Override public long kalbung(Daten.Kalbung k, Long sitzung) throws Fehler {
        return zusammen("Kalbung schreiben", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GLC_KALBUNG (SZENENZEIT, JAHRESTAG, ART, AUSLOESER, LAGE, RISS, X_M, Y_M, Z_M, VOLUMEN_M3, MASSE_T, STUECKE, "
                    + "HOEHE_M, FALLZEIT_S, VORWARNUNG_S, WELLE_STEG_M, SEED, SITZUNG_ID, REGIEBUCH_ID) VALUES (?, ?, ?, ?, " + geomSql(Daten.Geom.punkt(k.x(), k.z())) + ", "
                    + geomSql(k.riss()) + ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " + REGIEBUCH_ID + ")")) {
                setT(st, 1, k.szenenzeit());
                st.setInt(2, k.jahrestag());
                st.setString(3, k.art());
                st.setString(4, k.ausloeser());
                int i = setGeom(st, 5, Daten.Geom.punkt(k.x(), k.z()));
                i = setGeom(st, i, k.riss());
                setD(st, i, Math.round(k.x() * 100) / 100.0);
                setD(st, i + 1, Math.round(k.y() * 100) / 100.0);
                setD(st, i + 2, Math.round(k.z() * 100) / 100.0);
                setD(st, i + 3, Math.round(k.volumenM3() * 10) / 10.0);
                setD(st, i + 4, Math.round(k.masseT() * 10) / 10.0);
                st.setInt(i + 5, k.stuecke());
                setD(st, i + 6, Math.round(k.hoeheM() * 100) / 100.0);
                setD(st, i + 7, k.fallzeitS());
                setD(st, i + 8, Math.round(k.vorwarnungS() * 10) / 10.0);
                setD(st, i + 9, k.welleStegM());
                setL(st, i + 10, k.seed());
                setL(st, i + 11, sitzung);
                setS(st, i + 12, k.regiebuch());
                st.executeUpdate();
            }
            return neueId(c, "GLC_KALBUNG_SEQ");
        });
    }

    @Override public void welle(long kalbung, double hoeheM) throws Fehler {
        mit("Welle nachtragen", c -> {
            try (PreparedStatement st = c.prepareStatement("UPDATE GLC_KALBUNG SET WELLE_STEG_M = ? WHERE ID = ?")) {
                st.setDouble(1, Math.round(hoeheM * 1000) / 1000.0);
                st.setLong(2, kalbung);
                st.executeUpdate();
            }
            return null;
        });
    }

    private static final String KURZ_SQL = "SELECT ID, SZENENZEIT, ART, AUSLOESER, X_M, Z_M, VOLUMEN_M3, WELLE_STEG_M FROM GLC_KALBUNG ";

    private static Daten.KalbungKurz kurz(ResultSet r) throws SQLException {
        return new Daten.KalbungKurz(r.getLong(1), t(r, "SZENENZEIT"), r.getString(3), r.getString(4), r.getDouble(5), r.getDouble(6), r.getDouble(7), dbl(r, "WELLE_STEG_M"));
    }

    @Override public List<Daten.KalbungKurz> kalbungen(LocalDateTime von, LocalDateTime bis, int n) throws Fehler {
        return mit("Kalbungen", c -> {
            List<Daten.KalbungKurz> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement(KURZ_SQL + "WHERE (? IS NULL OR SZENENZEIT >= ?) AND (? IS NULL OR SZENENZEIT < ?) ORDER BY SZENENZEIT DESC, ID DESC FETCH FIRST ? ROWS ONLY")) {
                setT(st, 1, von); setT(st, 2, von); setT(st, 3, bis); setT(st, 4, bis);
                st.setInt(5, n);
                try (ResultSet r = st.executeQuery()) { while (r.next()) l.add(kurz(r)); }
            }
            return l;
        });
    }

    @Override public List<Daten.KalbungKurz> kalbungenUm(double x, double z, double radiusM, int n) throws Fehler {
        return mit("Kalbungen im Umkreis", c -> {
            List<Daten.KalbungKurz> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement(KURZ_SQL + "WHERE X_M BETWEEN ? AND ? AND Z_M BETWEEN ? AND ? AND POWER(X_M - ?, 2) + POWER(Z_M - ?, 2) <= ? "
                    + "ORDER BY SZENENZEIT DESC, ID DESC FETCH FIRST ? ROWS ONLY")) {
                st.setDouble(1, x - radiusM); st.setDouble(2, x + radiusM); st.setDouble(3, z - radiusM); st.setDouble(4, z + radiusM);
                st.setDouble(5, x); st.setDouble(6, z); st.setDouble(7, radiusM * radiusM); st.setInt(8, n);
                try (ResultSet r = st.executeQuery()) { while (r.next()) l.add(kurz(r)); }
            }
            return l;
        });
    }

    @Override public List<Daten.Kalbung> kalbungenAm(LocalDate tag) throws Fehler {
        return mit("Kalbungen des Tages", c -> {
            List<Daten.Kalbung> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT k.ID, k.SZENENZEIT, k.JAHRESTAG, k.ART, k.AUSLOESER, k.X_M, k.Y_M, k.Z_M, MDSYS.SDO_UTIL.TO_WKTGEOMETRY(k.RISS) AS WKT, "
                    + "k.VOLUMEN_M3, k.MASSE_T, k.STUECKE, k.HOEHE_M, k.FALLZEIT_S, k.VORWARNUNG_S, k.WELLE_STEG_M, k.SEED, r.NAME FROM GLC_KALBUNG k "
                    + "LEFT JOIN GLC_REGIEBUCH r ON r.ID = k.REGIEBUCH_ID WHERE k.SZENENZEIT >= ? AND k.SZENENZEIT < ? ORDER BY k.SZENENZEIT")) {
                setT(st, 1, tag.atStartOfDay());
                setT(st, 2, tag.plusDays(1).atStartOfDay());
                try (ResultSet r = st.executeQuery()) {
                    while (r.next()) {
                        long seed = r.getLong("SEED");
                        Long s = r.wasNull() ? null : seed;
                        l.add(new Daten.Kalbung(r.getLong(1), t(r, "SZENENZEIT"), r.getInt(3), r.getString(4), r.getString(5), r.getDouble(6), d(r, "Y_M"), r.getDouble(8),
                                Daten.Geom.ausWkt(clob(r, "WKT")), r.getDouble(10), d(r, "MASSE_T"), r.getInt(12), d(r, "HOEHE_M"), dbl(r, "FALLZEIT_S"), d(r, "VORWARNUNG_S"),
                                dbl(r, "WELLE_STEG_M"), s, r.getString(18)));
                    }
                }
            }
            return l;
        });
    }

    // ------------------------------------------------------------------ Eisberge

    private static Daten.Geom weg(Daten.Eisberg e) {
        if (e.weg().size() < 2) return null;
        return Daten.Geom.linie(e.weg().toArray(new double[0][]));
    }

    @Override public void eisberg(Daten.Eisberg e, Long sitzung) throws Fehler {
        Daten.Geom w = weg(e), lage = Daten.Geom.punkt(e.geburtX(), e.geburtZ());
        zusammen("Eisberg schreiben", c -> {
            int da;
            try (PreparedStatement st = c.prepareStatement("UPDATE GLC_EISBERG SET VOLUMEN_M3 = ?, TIEFGANG_M = ?, FREIBORD_M = ?, KENTERUNGEN = ?, WEG = " + geomSql(w)
                    + ", PUNKTE = ?, WEG_M = ?, X_M = ?, Z_M = ?, GIER_GRAD = ?, ENDE = ?, ENDE_ART = ?, KALBUNG_ID = NVL(KALBUNG_ID, ?) "
                    + "WHERE NR = ? AND DECODE(SITZUNG_ID, ?, 1, 0) = 1")) {
                setD(st, 1, Math.round(e.volumenM3() * 10) / 10.0);
                setD(st, 2, Math.round(e.tiefgangM() * 100) / 100.0);
                setD(st, 3, Math.round(e.freibordM() * 100) / 100.0);
                st.setInt(4, e.kenterungen());
                int i = setGeom(st, 5, w);
                st.setInt(i, Math.max(1, e.weg().size()));
                setD(st, i + 1, Math.round(e.wegM() * 10) / 10.0);
                setD(st, i + 2, Math.round(e.x() * 100) / 100.0);
                setD(st, i + 3, Math.round(e.z() * 100) / 100.0);
                setD(st, i + 4, Math.round(e.gierGrad() * 100) / 100.0);
                setT(st, i + 5, e.ende());
                st.setString(i + 6, e.endeArt());
                setL(st, i + 7, e.kalbungId());
                st.setInt(i + 8, e.nr());
                setL(st, i + 9, sitzung);
                da = st.executeUpdate();
            }
            if (da > 0) return null;
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GLC_EISBERG (NR, KALBUNG_ID, RUPTURA_LAUF_ID, GEBURT, LAGE, VOLUMEN_START_M3, VOLUMEN_M3, TIEFGANG_M, FREIBORD_M, "
                    + "KENTERUNGEN, WEG, PUNKTE, WEG_M, X_M, Z_M, GIER_GRAD, FORM_SEED, ENDE, ENDE_ART, SITZUNG_ID) VALUES (?, ?, ?, ?, " + geomSql(lage) + ", ?, ?, ?, ?, ?, "
                    + geomSql(w) + ", ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                st.setInt(1, e.nr());
                setL(st, 2, e.kalbungId());
                setL(st, 3, e.rupturaLaufId());
                setT(st, 4, e.geburt());
                int i = setGeom(st, 5, lage);
                setD(st, i, Math.round(e.volumenStartM3() * 10) / 10.0);
                setD(st, i + 1, Math.round(e.volumenM3() * 10) / 10.0);
                setD(st, i + 2, Math.round(e.tiefgangM() * 100) / 100.0);
                setD(st, i + 3, Math.round(e.freibordM() * 100) / 100.0);
                st.setInt(i + 4, e.kenterungen());
                i = setGeom(st, i + 5, w);
                st.setInt(i, Math.max(1, e.weg().size()));
                setD(st, i + 1, Math.round(e.wegM() * 10) / 10.0);
                setD(st, i + 2, Math.round(e.x() * 100) / 100.0);
                setD(st, i + 3, Math.round(e.z() * 100) / 100.0);
                setD(st, i + 4, Math.round(e.gierGrad() * 100) / 100.0);
                st.setLong(i + 5, e.formSeed());
                setT(st, i + 6, e.ende());
                st.setString(i + 7, e.endeArt());
                setL(st, i + 8, sitzung);
                st.executeUpdate();
            }
            return null;
        });
    }

    private static final String EISBERG_SQL = "SELECT NR, KALBUNG_ID, RUPTURA_LAUF_ID, GEBURT, e.LAGE.SDO_POINT.X AS LON, e.LAGE.SDO_POINT.Y AS LAT, VOLUMEN_START_M3, VOLUMEN_M3, "
            + "TIEFGANG_M, FREIBORD_M, KENTERUNGEN, MDSYS.SDO_UTIL.TO_WKTGEOMETRY(WEG) AS WKT, X_M, Z_M, GIER_GRAD, FORM_SEED, ENDE, ENDE_ART FROM GLC_EISBERG e ";

    private static Daten.Eisberg eisberg(ResultSet r) throws SQLException {
        double[] g = Daten.xz(r.getDouble("LON"), r.getDouble("LAT"));
        Daten.Geom w = Daten.Geom.ausWkt(clob(r, "WKT"));
        long k = r.getLong("KALBUNG_ID");
        Long kid = r.wasNull() ? null : k;
        long lf = r.getLong("RUPTURA_LAUF_ID");
        Long lid = r.wasNull() ? null : lf;
        return new Daten.Eisberg(r.getInt("NR"), kid, lid, t(r, "GEBURT"), g[0], g[1], r.getDouble("VOLUMEN_START_M3"), r.getDouble("VOLUMEN_M3"), d(r, "TIEFGANG_M"),
                d(r, "FREIBORD_M"), r.getInt("KENTERUNGEN"), w == null ? List.of(new double[]{g[0], g[1]}) : java.util.Arrays.asList(w.xz()), r.getDouble("X_M"), r.getDouble("Z_M"),
                d(r, "GIER_GRAD"), r.getLong("FORM_SEED"), t(r, "ENDE"), r.getString("ENDE_ART"));
    }

    @Override public List<Daten.Eisberg> offeneEisberge(long sitzung) throws Fehler {
        return mit("Offene Eisberge", c -> {
            List<Daten.Eisberg> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement(EISBERG_SQL + "WHERE SITZUNG_ID = ? AND ENDE_ART = 'OFFEN' ORDER BY NR")) {
                st.setLong(1, sitzung);
                try (ResultSet r = st.executeQuery()) { while (r.next()) l.add(eisberg(r)); }
            }
            return l;
        });
    }

    @Override public List<Daten.Eisberg> eisbergeAm(LocalDate tag) throws Fehler {
        return mit("Eisberge des Tages", c -> {
            List<Daten.Eisberg> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement(EISBERG_SQL + "WHERE GEBURT < ? AND (ENDE IS NULL OR ENDE >= ?) ORDER BY GEBURT FETCH FIRST 2000 ROWS ONLY")) {
                setT(st, 1, tag.plusDays(1).atStartOfDay());
                setT(st, 2, tag.atStartOfDay());
                try (ResultSet r = st.executeQuery()) { while (r.next()) l.add(eisberg(r)); }
            }
            return l;
        });
    }

    // ------------------------------------------------------------------ Ruptura und Regie (Läufe)

    @Override public long rupturaLauf(Daten.RupturaLauf l, Long sitzung) throws Fehler {
        return zusammen("Lauf der Ruptura", c -> {
            String cols = "JAHR = ?, BEGINN = ?, DURCHBRUCH = ?, TUNNEL = ?, RINNE = ?, BRUECKE = ?, EINSTURZ = ?, ENDE = ?, PHASE = ?, STAU_MAX_M = ?, ABFLUSS_MAX_M3S = ?, "
                    + "VOLUMEN_M3 = ?, BRUECKE_M3 = ?, GROESSTES_M3 = ?, STUECKE = ?";
            long id = l.id();
            if (id <= 0) {
                try (PreparedStatement st = c.prepareStatement("INSERT INTO GLC_RUPTURA_LAUF (JAHR, BEGINN, PHASE, DAMM, RINNE_LINIE, RUPTURA_ID, SITZUNG_ID, REGIEBUCH_ID) "
                        + "VALUES (?, ?, ?, " + geomSql(l.damm()) + ", " + geomSql(l.rinneLinie()) + ", (SELECT MAX(ID) FROM GLC_RUPTURA WHERE JAHR = ?), ?, " + REGIEBUCH_ID + ")")) {
                    if (l.jahr() == null) st.setNull(1, Types.NUMERIC); else st.setInt(1, l.jahr());
                    setT(st, 2, l.beginn());
                    st.setString(3, l.phase());
                    int i = setGeom(st, 4, l.damm());
                    i = setGeom(st, i, l.rinneLinie());
                    if (l.jahr() == null) st.setNull(i, Types.NUMERIC); else st.setInt(i, l.jahr());
                    setL(st, i + 1, sitzung);
                    setS(st, i + 2, l.regiebuch());
                    st.executeUpdate();
                }
                id = neueId(c, "GLC_RUPTURA_LAUF_SEQ");
            }
            try (PreparedStatement st = c.prepareStatement("UPDATE GLC_RUPTURA_LAUF SET " + cols + " WHERE ID = ?")) {
                if (l.jahr() == null) st.setNull(1, Types.NUMERIC); else st.setInt(1, l.jahr());
                setT(st, 2, l.beginn()); setT(st, 3, l.durchbruch()); setT(st, 4, l.tunnel()); setT(st, 5, l.rinne()); setT(st, 6, l.bruecke());
                setT(st, 7, l.einsturz()); setT(st, 8, l.ende());
                st.setString(9, l.phase());
                setD(st, 10, Math.round(l.stauMaxM() * 100) / 100.0);
                setD(st, 11, (double) Math.round(l.abflussMaxM3s()));
                setD(st, 12, (double) Math.round(l.volumenM3()));
                setD(st, 13, Math.round(l.brueckeM3() * 10) / 10.0);
                setD(st, 14, Math.round(l.groesstesM3() * 10) / 10.0);
                st.setInt(15, l.stuecke());
                st.setLong(16, id);
                st.executeUpdate();
            }
            return id;
        });
    }

    @Override public List<Daten.RupturaLauf> rupturaLaeufe() throws Fehler {
        return mit("Läufe der Ruptura", c -> {
            List<Daten.RupturaLauf> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT l.ID, l.JAHR, l.BEGINN, l.DURCHBRUCH, l.TUNNEL, l.RINNE, l.BRUECKE, l.EINSTURZ, l.ENDE, "
                    + "l.PHASE, l.STAU_MAX_M, l.ABFLUSS_MAX_M3S, l.VOLUMEN_M3, l.BRUECKE_M3, l.GROESSTES_M3, l.STUECKE, MDSYS.SDO_UTIL.TO_WKTGEOMETRY(l.DAMM) AS DW, "
                    + "MDSYS.SDO_UTIL.TO_WKTGEOMETRY(l.RINNE_LINIE) AS RW, b.NAME FROM GLC_RUPTURA_LAUF l LEFT JOIN GLC_REGIEBUCH b ON b.ID = l.REGIEBUCH_ID ORDER BY l.BEGINN DESC")) {
                while (r.next())
                    l.add(new Daten.RupturaLauf(r.getLong(1), integer(r, "JAHR"), t(r, "BEGINN"), t(r, "DURCHBRUCH"), t(r, "TUNNEL"), t(r, "RINNE"), t(r, "BRUECKE"),
                            t(r, "EINSTURZ"), t(r, "ENDE"), r.getString("PHASE"), d(r, "STAU_MAX_M"), d(r, "ABFLUSS_MAX_M3S"), d(r, "VOLUMEN_M3"), d(r, "BRUECKE_M3"),
                            d(r, "GROESSTES_M3"), r.getInt("STUECKE"), Daten.Geom.ausWkt(clob(r, "DW")), Daten.Geom.ausWkt(clob(r, "RW")), r.getString("NAME")));
            }
            return l;
        });
    }

    @Override public void regielauf(Daten.Regielauf l, Long sitzung) throws Fehler {
        zusammen("Lauf eines Regiebuchs", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GLC_REGIELAUF (REGIEBUCH_ID, NAME, BEGINN, ENDE, VOLLSTAENDIG, BILDER_S_MITTEL, BILDER_S_MIN, GPU_MS_MITTEL, "
                    + "FOTOS, QUALITAET, BLATT, BLATT_DATEI, SITZUNG_ID) VALUES (" + REGIEBUCH_ID + ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                st.setString(1, l.name());
                st.setString(2, l.name());
                setT(st, 3, l.beginn());
                setT(st, 4, l.ende());
                st.setInt(5, l.vollstaendig() ? 1 : 0);
                setD(st, 6, Math.round(l.bilderMittel() * 100) / 100.0);
                setD(st, 7, Math.round(l.bilderMin() * 100) / 100.0);
                setD(st, 8, Math.round(l.gpuMs() * 1000) / 1000.0);
                st.setInt(9, l.fotos());
                setS(st, 10, l.qualitaet());
                if (l.blatt() == null) st.setNull(11, Types.BLOB); else st.setBytes(11, l.blatt());
                setS(st, 12, l.blattDatei());
                setL(st, 13, sitzung);
                st.executeUpdate();
            }
            return null;
        });
    }

    @Override public int chronikLeeren(long sitzung) throws Fehler {
        return zusammen("Chronik leeren", c -> {
            int n = 0;
            for (String t : new String[]{"GLC_EISBERG", "GLC_KALBUNG", "GLC_RUPTURA_LAUF", "GLC_REGIELAUF"})
                try (PreparedStatement st = c.prepareStatement("DELETE FROM " + t + " WHERE SITZUNG_ID = ?")) { st.setLong(1, sitzung); n += st.executeUpdate(); }
            try (PreparedStatement st = c.prepareStatement("DELETE FROM GLC_FRONT WHERE SITZUNG_ID = ? AND HERKUNFT = 'EIGEN'")) { st.setLong(1, sitzung); n += st.executeUpdate(); }
            return n;
        });
    }

    @Override public List<long[]> sitzungenMitChronik(int n) throws Fehler {
        return mit("Sitzungen mit Chronik", c -> {
            List<long[]> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT s.ID, s.BEGINN, (SELECT COUNT(*) FROM GLC_KALBUNG k WHERE k.SITZUNG_ID = s.ID) AS K, "
                    + "(SELECT COUNT(*) FROM GLC_EISBERG e WHERE e.SITZUNG_ID = s.ID) AS E FROM GLC_SITZUNG s WHERE EXISTS (SELECT 1 FROM GLC_KALBUNG k WHERE k.SITZUNG_ID = s.ID) "
                    + "OR EXISTS (SELECT 1 FROM GLC_EISBERG e WHERE e.SITZUNG_ID = s.ID) OR EXISTS (SELECT 1 FROM GLC_FRONT f WHERE f.SITZUNG_ID = s.ID) "
                    + "OR EXISTS (SELECT 1 FROM GLC_RUPTURA_LAUF r WHERE r.SITZUNG_ID = s.ID) ORDER BY s.ID DESC FETCH FIRST ? ROWS ONLY")) {
                st.setInt(1, n);
                try (ResultSet r = st.executeQuery()) { while (r.next()) l.add(new long[]{r.getLong(1), r.getTimestamp(2).getTime(), r.getLong(3), r.getLong(4)}); }
            }
            return l;
        });
    }

    // ------------------------------------------------------------------ Wetterlagen

    @Override public List<Daten.Wetterlage> wetterlagen() throws Fehler {
        return mit("Wetterlagen", c -> {
            List<Daten.Wetterlage> l = new ArrayList<>();
            StringBuilder cols = new StringBuilder();
            for (String s : Verbindung.WETTER.values()) cols.append(", ").append(s);
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT NAME, BEAUFORT, PHASE, MONATE, BESCHREIBUNG, HERKUNFT" + cols
                    + " FROM GLC_WETTERLAGE ORDER BY CASE HERKUNFT WHEN 'MITGELIEFERT' THEN 0 ELSE 1 END, ID")) {
                while (r.next()) {
                    double[] v = new double[Weather.Var.values().length];
                    java.util.Arrays.fill(v, Double.NaN);
                    for (Map.Entry<Weather.Var, String> e : Verbindung.WETTER.entrySet()) {
                        if (e.getKey() == Weather.Var.CLOUD) {
                            String k = r.getString(e.getValue());
                            if (k != null) try { v[e.getKey().ordinal()] = Weather.CloudKind.valueOf(k).ordinal(); } catch (IllegalArgumentException ex) { /* leer */ }
                        } else v[e.getKey().ordinal()] = d(r, e.getValue());
                    }
                    l.add(new Daten.Wetterlage(r.getString(1), dbl(r, "BEAUFORT"), r.getString(3), v, r.getString(4), r.getString(5), r.getString(6)));
                }
            }
            return l;
        });
    }

    // ------------------------------------------------------------------ Regiebücher

    @Override public List<Daten.Regiebuch> regiebuecher() throws Fehler {
        return mit("Regiebücher", c -> {
            List<Daten.Regiebuch> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT NAME, TEXT, BESCHREIBUNG, DATEI, HERKUNFT FROM GLC_REGIEBUCH "
                    + "ORDER BY CASE HERKUNFT WHEN 'MITGELIEFERT' THEN 0 ELSE 1 END, ID")) {
                while (r.next()) l.add(new Daten.Regiebuch(r.getString(1), clob(r, "TEXT"), r.getString(3), r.getString(4), r.getString(5)));
            }
            return l;
        });
    }

    @Override public void speichern(Daten.Regiebuch rb) throws Fehler {
        Regie.Book b;
        try { b = Regie.parse(rb.text()); } catch (RuntimeException e) { throw new Fehler("Regiebuch nicht lesbar: " + e.getMessage(), e); }
        if (!b.name().equals(rb.name())) throw new Fehler("Der Name im Text („" + b.name() + "“) passt nicht zu „" + rb.name() + "“");
        Startdaten.Kopf k = Startdaten.Kopf.aus(b);
        zusammen("Regiebuch speichern", c -> {
            if (mitgeliefert(c, "GLC_REGIEBUCH", b.name())) throw new SQLException("„" + b.name() + "“ ist mitgeliefert; bitte unter einem eigenen Namen speichern");
            try (PreparedStatement st = c.prepareStatement("DELETE FROM GLC_REGIEBUCH WHERE NAME = ? AND HERKUNFT = 'EIGEN'")) { st.setString(1, b.name()); st.executeUpdate(); }
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GLC_REGIEBUCH (NAME, BESCHREIBUNG, DATEI, SZENENZEIT, ZEITRAFFER, LAENGE_S, FOTOS, BLATT, WETTER, TEXT, HERKUNFT) "
                    + "VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, 'EIGEN')")) {
                st.setString(1, b.name());
                setS(st, 2, b.about());
                setT(st, 3, k.szenenzeit());
                setD(st, 4, k.raffer());
                st.setDouble(5, b.length());
                st.setInt(6, k.fotos());
                st.setInt(7, k.blatt() ? 1 : 0);
                setS(st, 8, Regie.wetterBlock(rb.text()));
                setClob(st, 9, rb.text());
                st.executeUpdate();
            }
            long id = Long.parseLong(Db.einWert(c, "SELECT TO_CHAR(ID) FROM GLC_REGIEBUCH WHERE NAME = ?", b.name()));
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GLC_STICHWORT (REGIEBUCH_ID, NR, SEKUNDE, BEFEHL, ARGUMENTE, TEXT) VALUES (?, ?, ?, ?, ?, ?)")) {
                int nr = 0;
                for (Regie.Zeile z : b.zeilen()) {
                    st.setLong(1, id);
                    st.setInt(2, ++nr);
                    st.setDouble(3, z.sekunde());
                    st.setString(4, z.befehl().toUpperCase(java.util.Locale.ROOT));
                    setS(st, 5, z.argumente());
                    setS(st, 6, z.text());
                    st.addBatch();
                }
                st.executeBatch();
            }
            return null;
        });
    }

    @Override public void loeschenRegiebuch(String name) throws Fehler { loeschen("GLC_REGIEBUCH", name); }

    // ------------------------------------------------------------------ Fahrten

    @Override public List<Daten.Fahrt> fahrten() throws Fehler {
        return mit("Fahrten", c -> {
            Map<Long, List<Daten.Stuetzpunkt>> p = new LinkedHashMap<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT FAHRT_ID, NR, ZEIT_S, KAM_X, KAM_Y, KAM_Z, KAM_GIER_GRAD, KAM_NICK_GRAD, KAM_FOV_GRAD "
                    + "FROM GLC_STUETZPUNKT ORDER BY FAHRT_ID, NR")) {
                while (r.next())
                    p.computeIfAbsent(r.getLong(1), k -> new ArrayList<>()).add(new Daten.Stuetzpunkt(r.getInt(2), r.getDouble(3), d(r, "KAM_X"), d(r, "KAM_Y"), d(r, "KAM_Z"),
                            d(r, "KAM_GIER_GRAD"), d(r, "KAM_NICK_GRAD"), d(r, "KAM_FOV_GRAD")));
            }
            List<Daten.Fahrt> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT ID, NAME, TASTE, ART, SCHLEIFE, DAUER_S, BESCHREIBUNG, HERKUNFT FROM GLC_FAHRT "
                    + "ORDER BY CASE HERKUNFT WHEN 'MITGELIEFERT' THEN 0 ELSE 1 END, ID")) {
                while (r.next())
                    l.add(new Daten.Fahrt(r.getString(2), integer(r, "TASTE"), r.getString(4), r.getInt(5) == 1, dbl(r, "DAUER_S"), r.getString(7), r.getString(8),
                            p.getOrDefault(r.getLong(1), List.of())));
            }
            return l;
        });
    }

    // ------------------------------------------------------------------ Ansichten

    private static final String ANSICHT_SQL = "SELECT ID, NAME, TASTE, ART, KAM_X, KAM_Y, KAM_Z, KAM_GIER_GRAD, KAM_NICK_GRAD, KAM_FOV_GRAD, AUGE_UEBER_GRUND_M, UNTER_WASSER, "
            + "SZENENZEIT, HERKUNFT";

    private static Daten.Ansicht ansicht(ResultSet r, String zustand, byte[] png) throws SQLException {
        double[] k = {d(r, "KAM_X"), d(r, "KAM_Y"), d(r, "KAM_Z"), d(r, "KAM_GIER_GRAD"), d(r, "KAM_NICK_GRAD"), d(r, "KAM_FOV_GRAD")};
        return new Daten.Ansicht(r.getLong("ID"), r.getString("NAME"), integer(r, "TASTE"), r.getString("ART"), k, dbl(r, "AUGE_UEBER_GRUND_M"), r.getInt("UNTER_WASSER") == 1,
                t(r, "SZENENZEIT"), zustand, png, r.getString("HERKUNFT"));
    }

    @Override public List<Daten.Ansicht> ansichten() throws Fehler {
        return mit("Ansichten", c -> {
            List<Daten.Ansicht> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery(ANSICHT_SQL + " FROM GLC_ANSICHT ORDER BY CASE ART WHEN 'FEST' THEN 0 ELSE 1 END, TASTE, ID DESC")) {
                while (r.next()) l.add(ansicht(r, null, null));
            }
            return l;
        });
    }

    @Override public Daten.Ansicht ansicht(long id) throws Fehler {
        return mit("Ansicht", c -> {
            try (PreparedStatement st = c.prepareStatement(ANSICHT_SQL + ", ZUSTAND, VORSCHAU FROM GLC_ANSICHT WHERE ID = ?")) {
                st.setLong(1, id);
                try (ResultSet r = st.executeQuery()) {
                    if (!r.next()) throw new SQLException("Ansicht " + id + " gibt es nicht");
                    return ansicht(r, clob(r, "ZUSTAND"), r.getBytes("VORSCHAU"));
                }
            }
        });
    }

    @Override public long speichern(Daten.Ansicht a, Long sitzung) throws Fehler {
        Daten.Geom lage = Daten.Geom.punkt(a.kamera()[0], a.kamera()[2]);
        return zusammen("Ansicht merken", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GLC_ANSICHT (NAME, ART, KAM_X, KAM_Y, KAM_Z, KAM_GIER_GRAD, KAM_NICK_GRAD, KAM_FOV_GRAD, UNTER_WASSER, LAGE, "
                    + "SZENENZEIT, ZUSTAND, VORSCHAU, SITZUNG_ID, HERKUNFT) VALUES (?, 'GEMERKT', ?, ?, ?, ?, ?, ?, ?, " + geomSql(lage) + ", ?, ?, ?, ?, 'EIGEN')")) {
                st.setString(1, a.name());
                for (int i = 0; i < 6; i++) setD(st, 2 + i, Math.round(a.kamera()[i] * 100) / 100.0);
                st.setInt(8, a.unterWasser() ? 1 : 0);
                int i = setGeom(st, 9, lage);
                setT(st, i, a.szenenzeit());
                setClob(st, i + 1, a.zustand());
                if (a.vorschau() == null) st.setNull(i + 2, Types.BLOB); else st.setBytes(i + 2, a.vorschau());
                setL(st, i + 3, sitzung);
                st.executeUpdate();
            }
            return neueId(c, "GLC_ANSICHT_SEQ");
        });
    }

    @Override public void loeschenAnsicht(long id) throws Fehler {
        zusammen("Ansicht löschen", c -> {
            try (PreparedStatement st = c.prepareStatement("DELETE FROM GLC_ANSICHT WHERE ID = ? AND HERKUNFT = 'EIGEN'")) { st.setLong(1, id); st.executeUpdate(); }
            return null;
        });
    }

    // ------------------------------------------------------------------ Presets

    @Override public List<Daten.PaketPreset> presets(String paket, String klasse) throws Fehler {
        return mit("Presets", c -> {
            Map<Long, Map<String, String>> w = new LinkedHashMap<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT PAKET_PRESET_ID, FELD, WERT FROM GLC_PAKET_WERT ORDER BY PAKET_PRESET_ID, ID")) {
                while (r.next()) w.computeIfAbsent(r.getLong(1), k -> new LinkedHashMap<>()).put(r.getString(2), r.getString(3) == null ? "" : r.getString(3));
            }
            List<Daten.PaketPreset> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT ID, PAKET, KLASSE, NAME, BESCHREIBUNG, HERKUNFT FROM GLC_PAKET_PRESET WHERE (? IS NULL OR PAKET = ?) "
                    + "AND (? IS NULL OR KLASSE = ?) ORDER BY PAKET, KLASSE, CASE HERKUNFT WHEN 'MITGELIEFERT' THEN 0 ELSE 1 END, NAME")) {
                st.setString(1, paket);
                st.setString(2, paket);
                st.setString(3, klasse);
                st.setString(4, klasse);
                try (ResultSet r = st.executeQuery()) {
                    while (r.next())
                        l.add(new Daten.PaketPreset(r.getString(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6), w.getOrDefault(r.getLong(1), Map.of())));
                }
            }
            return l;
        });
    }

    @Override public void speichern(Daten.PaketPreset p) throws Fehler {
        Map<String, String> typ = Startdaten.typen(p);
        zusammen("Preset speichern", c -> {
            if (zahl(c, "SELECT COUNT(*) FROM GLC_PAKET_PRESET WHERE PAKET = ? AND KLASSE = ? AND NAME = ? AND HERKUNFT = 'MITGELIEFERT'", p.paket(), p.klasse(), p.name()) > 0)
                throw new SQLException("„" + p.name() + "“ ist mitgeliefert; bitte unter einem eigenen Namen speichern");
            try (PreparedStatement st = c.prepareStatement("DELETE FROM GLC_PAKET_PRESET WHERE PAKET = ? AND KLASSE = ? AND NAME = ? AND HERKUNFT = 'EIGEN'")) {
                st.setString(1, p.paket()); st.setString(2, p.klasse()); st.setString(3, p.name()); st.executeUpdate();
            }
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GLC_PAKET_PRESET (PAKET, KLASSE, NAME, BESCHREIBUNG, HERKUNFT) VALUES (?, ?, ?, ?, 'EIGEN')")) {
                st.setString(1, p.paket()); st.setString(2, p.klasse()); st.setString(3, p.name()); setS(st, 4, p.beschreibung()); st.executeUpdate();
            }
            long id = Long.parseLong(Db.einWert(c, "SELECT TO_CHAR(ID) FROM GLC_PAKET_PRESET WHERE PAKET = ? AND KLASSE = ? AND NAME = ?", p.paket(), p.klasse(), p.name()));
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GLC_PAKET_WERT (PAKET_PRESET_ID, FELD, TYP, WERT) VALUES (?, ?, ?, ?)")) {
                for (Map.Entry<String, String> e : p.felder().entrySet()) {
                    st.setLong(1, id);
                    st.setString(2, e.getKey());
                    st.setString(3, typ.getOrDefault(e.getKey(), "STRING"));
                    setS(st, 4, e.getValue());
                    st.addBatch();
                }
                st.executeBatch();
            }
            return null;
        });
    }

    @Override public void loeschenPreset(String paket, String klasse, String name) throws Fehler {
        zusammen("Preset löschen", c -> {
            try (PreparedStatement st = c.prepareStatement("DELETE FROM GLC_PAKET_PRESET WHERE PAKET = ? AND KLASSE = ? AND NAME = ? AND HERKUNFT = 'EIGEN'")) {
                st.setString(1, paket); st.setString(2, klasse); st.setString(3, name);
                if (st.executeUpdate() == 0) throw new SQLException("„" + name + "“ ist mitgeliefert oder gibt es nicht");
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ Sitzungen

    @Override public long sitzungBeginnen(String version, String gpu, String qualitaet) throws Fehler {
        return zusammen("Sitzung beginnen", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GLC_SITZUNG (RECHNER, BENUTZER, APP_VERSION, GPU, QUALITAET) VALUES (?, ?, ?, ?, ?)")) {
                setS(st, 1, com.dan.store.DateiAblage.rechner());
                setS(st, 2, System.getProperty("user.name", ""));
                st.setString(3, version.length() > 20 ? version.substring(0, 20) : version);
                setS(st, 4, gpu == null ? null : gpu.length() > 120 ? gpu.substring(0, 120) : gpu);
                setS(st, 5, qualitaet);
                st.executeUpdate();
            }
            long id = neueId(c, "GLC_SITZUNG_SEQ");
            // F34 A: das Protokoll hält 90 Tage; die Wege der Eisberge älterer Sitzungen werden auf 20 m vereinfacht
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM GLC_LOG WHERE ZEIT < SYSTIMESTAMP - INTERVAL '90' DAY");
                // GEAENDERT setzt der Trigger: so trifft es jeden Weg höchstens alle 90 Tage einmal
                st.executeUpdate("UPDATE GLC_EISBERG e SET e.WEG = MDSYS.SDO_UTIL.SIMPLIFY(e.WEG, 20, 0.05), "
                        + "e.PUNKTE = MDSYS.SDO_UTIL.GETNUMVERTICES(MDSYS.SDO_UTIL.SIMPLIFY(e.WEG, 20, 0.05)) WHERE e.WEG IS NOT NULL AND e.PUNKTE > 2 "
                        + "AND e.GEAENDERT < SYSTIMESTAMP - INTERVAL '90' DAY");
            }
            return id;
        });
    }

    @Override public void sitzungSichern(long id, Sicherung s) throws Fehler {
        Map<String, String> sp = s.spalten();
        LocalDateTime z = sp.get(SZ) == null ? null : LocalDateTime.parse(sp.get(SZ));
        double rs = Zahl.lies(sp.get(REGIE_SEKUNDE)), zr = Zahl.lies(sp.get(ZEITRAFFER)), vs = Zahl.lies(sp.get(VORSTOSS)), kb = Zahl.lies(sp.get(KERBE)),
                stau = Zahl.lies(sp.get(STAU)), eb = Zahl.lies(sp.get(EISBERGE));
        zusammen("Sitzung sichern", c -> {
            try (PreparedStatement st = c.prepareStatement("UPDATE GLC_SITZUNG SET ZUSTAND = ?, ZUSTAND_ZEIT = SYSTIMESTAMP, REGIEBUCH_ID = " + REGIEBUCH_ID + ", REGIE_SEKUNDE = ?, "
                    + "SZENENZEIT = ?, ZEITRAFFER = ?, VORSTOSS_M = ?, KERBE_M = ?, RUPTURA_PHASE = ?, STAU_M = ?, EISBERGE = ? WHERE ID = ?")) {
                setClob(st, 1, s.zustand());
                setS(st, 2, sp.get(REGIEBUCH));
                setD(st, 3, rs);
                setT(st, 4, z);
                setD(st, 5, zr);
                setD(st, 6, Math.round(vs * 100) / 100.0);
                setD(st, 7, Math.round(kb * 100) / 100.0);
                setS(st, 8, sp.get(RUPTURA));
                setD(st, 9, Math.round(stau * 100) / 100.0);
                setD(st, 10, eb);
                st.setLong(11, id);
                st.executeUpdate();
            }
            return null;
        });
    }

    @Override public void sitzungBeenden(long id) throws Fehler {
        zusammen("Sitzung beenden", c -> {
            try (PreparedStatement st = c.prepareStatement("UPDATE GLC_SITZUNG SET ENDE = SYSTIMESTAMP, SAUBER_BEENDET = 1 WHERE ID = ?")) { st.setLong(1, id); st.executeUpdate(); }
            return null;
        });
    }

    private static Daten.Sitzung letzte(Connection c, long ausser) throws SQLException {
        try (PreparedStatement st = c.prepareStatement("SELECT s.ID, s.BEGINN, s.ENDE, s.SAUBER_BEENDET, s.ZUSTAND, s.ZUSTAND_ZEIT, r.NAME AS REGIEBUCH, s.REGIE_SEKUNDE, "
                + "s.SZENENZEIT, s.EISBERGE, s.RUPTURA_PHASE FROM GLC_SITZUNG s LEFT JOIN GLC_REGIEBUCH r ON r.ID = s.REGIEBUCH_ID WHERE s.ZUSTAND IS NOT NULL AND s.ID <> ? "
                + "ORDER BY s.ZUSTAND_ZEIT DESC NULLS LAST, s.ID DESC FETCH FIRST 1 ROWS ONLY")) {
            st.setLong(1, ausser);
            try (ResultSet r = st.executeQuery()) {
                if (!r.next()) return null;
                Timestamp b = r.getTimestamp(2), e = r.getTimestamp(3), zz = r.getTimestamp(6);
                return new Daten.Sitzung(r.getLong(1), b.getTime(), e == null ? null : e.getTime(), r.getInt(4) == 1, clob(r, "ZUSTAND"), zz == null ? null : zz.getTime(),
                        r.getString(7), dbl(r, "REGIE_SEKUNDE"), t(r, "SZENENZEIT"), integer(r, "EISBERGE"), r.getString("RUPTURA_PHASE"));
            }
        }
    }

    @Override public Daten.Sitzung letzteSitzung(long ausser) throws Fehler { return mit("Letzte Sitzung", c -> letzte(c, ausser)); }

    // ------------------------------------------------------------------ Protokoll

    @Override public void protokoll(List<Protokoll.Zeile> zeilen, Long sitzung) throws Fehler {
        if (zeilen.isEmpty()) return;
        zusammen("Protokoll", c -> {
            List<Long> ids = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT GLC_LOG_SEQ.NEXTVAL FROM DUAL CONNECT BY LEVEL <= ?")) {
                st.setInt(1, zeilen.size());
                try (ResultSet r = st.executeQuery()) { while (r.next()) ids.add(r.getLong(1)); }
            }
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GLC_LOG (ID, SITZUNG_ID, ZEIT, STUFE, QUELLE, TEXT, SZENENZEIT, ORT, DETAIL) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (int i = 0; i < zeilen.size(); i++) {
                    Protokoll.Zeile z = zeilen.get(i);
                    st.setLong(1, ids.get(i));
                    setL(st, 2, sitzung);
                    st.setTimestamp(3, new Timestamp(z.zeitMs()));
                    st.setString(4, z.stufe());
                    st.setString(5, z.quelle().length() > 60 ? z.quelle().substring(0, 60) : z.quelle());
                    st.setString(6, z.text().length() > 1000 ? z.text().substring(0, 997) + " …" : z.text());
                    setS(st, 7, z.szenenzeit() == null ? null : z.szenenzeit().length() > 20 ? z.szenenzeit().substring(0, 20) : z.szenenzeit());
                    setS(st, 8, z.ort() == null ? null : z.ort().length() > 40 ? z.ort().substring(0, 40) : z.ort());
                    setClob(st, 9, z.detail());
                    st.addBatch();
                }
                st.executeBatch();
            }
            return null;
        });
    }

    @Override public List<String> letzteZeilen(int n) throws Fehler {
        return mit("Protokoll lesen", c -> {
            List<String> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT ZEIT, STUFE, QUELLE, TEXT FROM GLC_LOG ORDER BY ID DESC FETCH FIRST ? ROWS ONLY")) {
                st.setInt(1, n);
                try (ResultSet r = st.executeQuery()) {
                    while (r.next())
                        l.add(0, Protokoll.zeile(new Protokoll.Zeile(r.getTimestamp(1).getTime(), r.getString(2), r.getString(3), r.getString(4), null, null, null)));
                }
            }
            return l;
        });
    }

    @Override public synchronized void schliessen() {
        try { if (c != null) c.close(); } catch (SQLException e) { /* zu */ }
        c = null;
    }
}
