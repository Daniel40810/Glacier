package com.dan.glacier.data;

import com.dan.store.Ablage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Wohin die App speichert: in das Schema GLACIER ({@link DbSpeicher}) oder, ohne Datenbank, in Dateien im Ordner
 * {@code daten/} ({@link DateiSpeicher}, F33 A). Beide können dasselbe; die Oberfläche kennt nur diese Schnittstelle.
 * Sitzung und Protokoll kommen aus {@link Ablage} (com.dan.store).
 * <p>
 * Die Chronik (Fronten, Kalbungen, Eisberge, Läufe der Ruptura und der Regiebücher) schreibt {@link Chronik} im Faden
 * der Daten. Namen von Regiebüchern, Wetterlagen, Fahrten und Presets sind eindeutig je Art; ein mitgelieferter Eintrag
 * wird nie überschrieben, sondern abgelehnt.
 */
public interface Speicher extends Ablage {

    /** Schlüssel der Werte, die eine {@link Ablage.Sicherung} über die Sitzung mitbringt (Spalten von GLC_SITZUNG). */
    String REGIEBUCH = "regiebuch", REGIE_SEKUNDE = "regieSekunde", SZ = "szenenzeit", ZEITRAFFER = "zeitraffer", VORSTOSS = "vorstoss", KERBE = "kerbe",
            RUPTURA = "ruptura", STAU = "stau", EISBERGE = "eisberge";

    Daten.Stand stand() throws Fehler;

    // ------------------------------------------------------------------ Quellen, Gelände, Ruptura (F29, F35)

    List<Daten.Quelle> quellen() throws Fehler;

    List<Daten.Befund> befunde() throws Fehler;

    List<Daten.Ruptura> rupturen() throws Fehler;

    // ------------------------------------------------------------------ Chronik (F26–F29)

    /** Eine Front schreiben; gibt ihre Nummer zurück. */
    long front(Daten.Front f, Long sitzung) throws Fehler;

    /** Die Fronten (ohne Linie, wenn ohneLinie) eines Zeitraums (von einschließlich, bis ausschließlich; null = offen). */
    List<Daten.Front> fronten(LocalDateTime von, LocalDateTime bis, boolean ohneLinie) throws Fehler;

    /** Eine Kalbung schreiben; gibt ihre Nummer zurück. */
    long kalbung(Daten.Kalbung k, Long sitzung) throws Fehler;

    /** Die Wellenhöhe am Steg einer Kalbung nachtragen (F27: nach 60 s). */
    void welle(long kalbung, double hoeheM) throws Fehler;

    /** Kalbungen eines Zeitraums, neueste zuerst, höchstens n. */
    List<Daten.KalbungKurz> kalbungen(LocalDateTime von, LocalDateTime bis, int n) throws Fehler;

    /** Kalbungen bis zu radiusM um (x, z) in Szenenmetern, neueste zuerst, höchstens n (Säulenkarte, F30). */
    List<Daten.KalbungKurz> kalbungenUm(double x, double z, double radiusM, int n) throws Fehler;

    /** Ganze Kalbungen eines Tages (Tageskarte). */
    List<Daten.Kalbung> kalbungenAm(LocalDate tag) throws Fehler;

    /** Einen Eisberg anlegen oder fortschreiben (Schlüssel: Sitzung und nr). */
    void eisberg(Daten.Eisberg e, Long sitzung) throws Fehler;

    /** Die offenen Eisberge einer Sitzung (für „Weiter wie zuletzt“, F31). */
    List<Daten.Eisberg> offeneEisberge(long sitzung) throws Fehler;

    /** Eisberge, die an einem Tag geboren wurden oder noch trieben (Tageskarte). */
    List<Daten.Eisberg> eisbergeAm(LocalDate tag) throws Fehler;

    /** Einen Lauf der Ruptura anlegen (id 0) oder fortschreiben; gibt seine Nummer zurück. */
    long rupturaLauf(Daten.RupturaLauf l, Long sitzung) throws Fehler;

    List<Daten.RupturaLauf> rupturaLaeufe() throws Fehler;

    /** Einen Lauf eines Regiebuchs schreiben. */
    void regielauf(Daten.Regielauf l, Long sitzung) throws Fehler;

    /** Die eigenen Zeilen der Chronik einer Sitzung löschen (F34); gibt zurück, wie viele. */
    int chronikLeeren(long sitzung) throws Fehler;

    /** Sitzungen mit Chronik: {id, Beginn ms, Kalbungen, Eisberge}, neueste zuerst. */
    List<long[]> sitzungenMitChronik(int n) throws Fehler;

    // ------------------------------------------------------------------ Regie und Kamera (F32)

    List<Daten.Wetterlage> wetterlagen() throws Fehler;

    List<Daten.Regiebuch> regiebuecher() throws Fehler;
    void speichern(Daten.Regiebuch r) throws Fehler;
    void loeschenRegiebuch(String name) throws Fehler;

    List<Daten.Fahrt> fahrten() throws Fehler;

    /** Ansichten ohne Zustand und Vorschau (für Listen): feste zuerst, dann gemerkte, neueste zuerst. */
    List<Daten.Ansicht> ansichten() throws Fehler;
    /** Eine gemerkte Ansicht ganz, mit Zustand und Vorschau. */
    Daten.Ansicht ansicht(long id) throws Fehler;
    /** Speichert eine neue gemerkte Ansicht und gibt ihre Nummer zurück. */
    long speichern(Daten.Ansicht a, Long sitzung) throws Fehler;
    void loeschenAnsicht(long id) throws Fehler;

    // ------------------------------------------------------------------ Packages

    /** Presets eines Package (paket = „com.dan.ocean“, klasse = „Ocean“); null für alle. */
    List<Daten.PaketPreset> presets(String paket, String klasse) throws Fehler;
    void speichern(Daten.PaketPreset p) throws Fehler;
    void loeschenPreset(String paket, String klasse, String name) throws Fehler;

    // ------------------------------------------------------------------ Betrieb

    /** Die letzte Sitzung mit Zustand vor der laufenden (oder null). */
    Daten.Sitzung letzteSitzung(long ausser) throws Fehler;

    /** Die letzten n Zeilen des Protokolls zum Anzeigen. */
    List<String> letzteZeilen(int n) throws Fehler;
}
