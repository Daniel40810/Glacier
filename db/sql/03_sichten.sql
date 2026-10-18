-- =====================================================================================================
-- Glacier · Datenbank · 03_sichten.sql
-- Neun Sichten: die Fronten mit Länge und Vorstoß gegen den Vortag, Kalbungen und Eisberge mit Länge und
-- Breite als Zahl, die Statistik je Tag und Monat, die Tageskarte als eine Ebene für QGIS (Abnahme Phase 15),
-- die Rupturas belegt und gespielt nebeneinander, die Befunde mit ihren Quellen und die letzte Sitzung für
-- „Weiter wie zuletzt“. Längen und Flächen auf dem Ellipsoid, Toleranz 5 cm. CREATE OR REPLACE: beliebig oft
-- wiederholbar.
-- =====================================================================================================

-- Die Fronten mit Länge auf dem Ellipsoid und dem Vorstoß gegen die vorige Front derselben Art.
CREATE OR REPLACE VIEW GLC_FRONT_V AS
SELECT f.ID,
       f.ART,
       f.NAME,
       f.SZENENZEIT,
       TRUNC(f.SZENENZEIT) AS TAG,
       f.GUELTIG_BIS,
       ROUND(MDSYS.SDO_GEOM.SDO_LENGTH(f.LINIE, 0.05, 'unit=M'), 1) AS LAENGE_M,
       f.VORSTOSS_M,
       ROUND(f.VORSTOSS_M - LAG(f.VORSTOSS_M) OVER (PARTITION BY f.ART, f.SITZUNG_ID ORDER BY f.SZENENZEIT, f.ID), 2) AS GEGEN_VORIGE_M,
       f.FLAECHE_DIFF_M2,
       f.HOEHE_MITTEL_M,
       f.SITZUNG_ID,
       q.KURZ AS QUELLE,
       f.HERKUNFT
FROM GLC_FRONT f
LEFT JOIN GLC_QUELLE q ON q.ID = f.QUELLE_ID;

-- Kalbungen ohne Spatial-Typ: so lesen SQL Developer und Tabellenprogramme die Lage als Zahlen.
CREATE OR REPLACE VIEW GLC_KALBUNG_V AS
SELECT k.ID,
       k.SZENENZEIT,
       TRUNC(k.SZENENZEIT) AS TAG,
       EXTRACT(MONTH FROM k.SZENENZEIT) AS MONAT,
       k.JAHRESTAG,
       k.ART,
       k.AUSLOESER,
       k.LAGE.SDO_POINT.X AS LAENGE,
       k.LAGE.SDO_POINT.Y AS BREITE,
       k.X_M,
       k.Y_M,
       k.Z_M,
       k.VOLUMEN_M3,
       k.MASSE_T,
       k.STUECKE,
       k.HOEHE_M,
       k.FALLZEIT_S,
       k.VORWARNUNG_S,
       k.WELLE_STEG_M,
       CASE WHEN k.RISS IS NOT NULL THEN ROUND(MDSYS.SDO_GEOM.SDO_LENGTH(k.RISS, 0.05, 'unit=M'), 1) END AS RISS_M,
       (SELECT COUNT(*) FROM GLC_EISBERG e WHERE e.KALBUNG_ID = k.ID) AS EISBERGE,
       r.NAME AS REGIEBUCH,
       k.SITZUNG_ID
FROM GLC_KALBUNG k
LEFT JOIN GLC_REGIEBUCH r ON r.ID = k.REGIEBUCH_ID;

-- Statistik je Szenentag und Art.
CREATE OR REPLACE VIEW GLC_KALBUNG_TAG_V AS
SELECT TRUNC(k.SZENENZEIT) AS TAG,
       k.ART,
       COUNT(*) AS ANZAHL,
       ROUND(SUM(k.VOLUMEN_M3)) AS VOLUMEN_M3,
       ROUND(MAX(k.VOLUMEN_M3)) AS GROESSTE_M3,
       MAX(k.WELLE_STEG_M) AS WELLE_MAX_M
FROM GLC_KALBUNG k
GROUP BY TRUNC(k.SZENENZEIT), k.ART;

-- Statistik je Monat und Art, mit der Zahl der Tage, an denen die Szene lief.
CREATE OR REPLACE VIEW GLC_KALBUNG_MONAT_V AS
SELECT EXTRACT(YEAR FROM k.SZENENZEIT) AS JAHR,
       EXTRACT(MONTH FROM k.SZENENZEIT) AS MONAT,
       k.ART,
       COUNT(*) AS ANZAHL,
       COUNT(DISTINCT TRUNC(k.SZENENZEIT)) AS TAGE,
       ROUND(COUNT(*) / COUNT(DISTINCT TRUNC(k.SZENENZEIT)), 2) AS JE_TAG,
       ROUND(SUM(k.VOLUMEN_M3)) AS VOLUMEN_M3,
       ROUND(MAX(k.VOLUMEN_M3)) AS GROESSTE_M3
FROM GLC_KALBUNG k
GROUP BY EXTRACT(YEAR FROM k.SZENENZEIT), EXTRACT(MONTH FROM k.SZENENZEIT), k.ART;

-- Eisberge mit Geburtsort als Zahlen, Alter in Szenenstunden und Weg in km.
CREATE OR REPLACE VIEW GLC_EISBERG_V AS
SELECT e.ID,
       e.NR,
       e.KALBUNG_ID,
       k.ART AS KALBUNG_ART,
       e.RUPTURA_LAUF_ID,
       e.GEBURT,
       e.ENDE,
       e.ENDE_ART,
       ROUND((CAST(NVL(e.ENDE, e.GEAENDERT) AS DATE) - CAST(e.GEBURT AS DATE)) * 24, 2) AS ALTER_H,
       e.VOLUMEN_START_M3,
       e.VOLUMEN_M3,
       ROUND(100 * e.VOLUMEN_M3 / NULLIF(e.VOLUMEN_START_M3, 0), 1) AS REST_PROZ,
       e.TIEFGANG_M,
       e.FREIBORD_M,
       e.KENTERUNGEN,
       e.LAGE.SDO_POINT.X AS LAENGE,
       e.LAGE.SDO_POINT.Y AS BREITE,
       CASE WHEN e.WEG IS NOT NULL THEN ROUND(MDSYS.SDO_GEOM.SDO_LENGTH(e.WEG, 0.05, 'unit=M') / 1000, 3) END AS WEG_KM,
       e.PUNKTE,
       e.SITZUNG_ID
FROM GLC_EISBERG e
LEFT JOIN GLC_KALBUNG k ON k.ID = e.KALBUNG_ID;

-- Die Tageskarte: Fronten, Kalbungen, Risse, Wege der Eisberge, Damm und Rinne der Ruptura als eine Ebene
-- (QGIS: Ebene GLC_TAGESKARTE_V, Schlüssel OBJEKT_ID, Filter auf TAG, Stil nach EBENE und ART).
-- OBJEKT_ID ist eindeutig: Ebene × 10^9 + ID.
CREATE OR REPLACE VIEW GLC_TAGESKARTE_V AS
SELECT 1000000000 + f.ID AS OBJEKT_ID, 'FRONT' AS EBENE, TRUNC(f.SZENENZEIT) AS TAG, f.ART, f.VORSTOSS_M AS GROESSE,
       NVL(f.NAME, TO_CHAR(f.SZENENZEIT, 'DD.MM.YYYY HH24:MI')) AS TEXT, f.LINIE AS GEOM
FROM GLC_FRONT f
UNION ALL
SELECT 2000000000 + k.ID, 'KALBUNG', TRUNC(k.SZENENZEIT), k.ART, k.VOLUMEN_M3,
       TO_CHAR(k.SZENENZEIT, 'HH24:MI') || ' ' || k.AUSLOESER, k.LAGE
FROM GLC_KALBUNG k
UNION ALL
SELECT 3000000000 + k.ID, 'RISS', TRUNC(k.SZENENZEIT), k.ART, k.VOLUMEN_M3,
       TO_CHAR(k.SZENENZEIT, 'HH24:MI'), k.RISS
FROM GLC_KALBUNG k WHERE k.RISS IS NOT NULL
UNION ALL
SELECT 4000000000 + e.ID, 'EISBERG', TRUNC(e.GEBURT), e.ENDE_ART, e.VOLUMEN_START_M3,
       'Eisberg ' || e.NR, NVL(e.WEG, e.LAGE)
FROM GLC_EISBERG e
UNION ALL
SELECT 5000000000 + l.ID, 'DAMM', TRUNC(l.BEGINN), l.PHASE, l.STAU_MAX_M,
       'Ruptura ' || TO_CHAR(l.BEGINN, 'DD.MM.YYYY'), l.DAMM
FROM GLC_RUPTURA_LAUF l WHERE l.DAMM IS NOT NULL
UNION ALL
SELECT 6000000000 + l.ID, 'RINNE', TRUNC(l.BEGINN), l.PHASE, l.ABFLUSS_MAX_M3S,
       'Rinne ' || TO_CHAR(l.BEGINN, 'DD.MM.YYYY'), l.RINNE_LINIE
FROM GLC_RUPTURA_LAUF l WHERE l.RINNE_LINIE IS NOT NULL;

-- Die Rupturas: belegt (GLC_RUPTURA) und in der App gespielt (GLC_RUPTURA_LAUF) nebeneinander.
CREATE OR REPLACE VIEW GLC_RUPTURA_V AS
SELECT 'BELEGT' AS ART,
       r.JAHR,
       r.NR,
       r.DATUM,
       r.GENAUIGKEIT,
       r.STAU_M,
       r.ABFLUSS_M3S,
       r.DAUER_H,
       r.TEXT,
       q.KURZ AS QUELLE,
       CAST(NULL AS NUMBER) AS LAUF_ID
FROM GLC_RUPTURA r
LEFT JOIN GLC_QUELLE q ON q.ID = r.QUELLE_ID
UNION ALL
SELECT 'GESPIELT',
       NVL(l.JAHR, EXTRACT(YEAR FROM l.BEGINN)),
       CAST(NULL AS NUMBER),
       CAST(NVL(l.EINSTURZ, l.BEGINN) AS DATE),
       'TAG',
       l.STAU_MAX_M,
       l.ABFLUSS_MAX_M3S,
       CASE WHEN l.DURCHBRUCH IS NOT NULL THEN ROUND((CAST(NVL(l.ENDE, l.GEAENDERT) AS DATE) - CAST(l.DURCHBRUCH AS DATE)) * 24, 1) END,
       l.PHASE,
       CAST(NULL AS VARCHAR2(30 CHAR)),
       l.ID
FROM GLC_RUPTURA_LAUF l;

-- Befunde mit ihren Quellen (Kurzzeichen) in einer Zeile.
CREATE OR REPLACE VIEW GLC_BEFUND_V AS
SELECT b.ID,
       b.THEMA,
       b.GEGENSTAND,
       b.BEFUND,
       b.TEXT,
       (SELECT LISTAGG(q.KURZ, ', ') WITHIN GROUP (ORDER BY q.KURZ)
          FROM GLC_BEFUND_QUELLE bq JOIN GLC_QUELLE q ON q.ID = bq.QUELLE_ID
         WHERE bq.BEFUND_ID = b.ID) AS QUELLEN
FROM GLC_BEFUND b;

-- Die jüngste Sitzung mit gesichertem Zustand, ob sauber beendet oder abgebrochen.
CREATE OR REPLACE VIEW GLC_LETZTE_SITZUNG_V AS
SELECT *
FROM (SELECT s.ID, s.BEGINN, s.ENDE, s.SAUBER_BEENDET, s.APP_VERSION, s.REGIEBUCH_ID, s.REGIE_SEKUNDE, s.SZENENZEIT,
             s.ZEITRAFFER, s.VORSTOSS_M, s.RUPTURA_PHASE, s.STAU_M, s.EISBERGE, s.ZUSTAND_ZEIT
      FROM GLC_SITZUNG s
      WHERE s.ZUSTAND IS NOT NULL
      ORDER BY s.ZUSTAND_ZEIT DESC NULLS LAST, s.ID DESC)
WHERE ROWNUM = 1;

-- Ende 03_sichten.sql
