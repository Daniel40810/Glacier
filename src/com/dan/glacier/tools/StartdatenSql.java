package com.dan.glacier.tools;

import com.dan.glacier.data.Startdaten;
import com.dan.glacier.scene.Terrain;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Phase 15: schreibt {@code db/sql/04_startdaten.sql} aus {@link Startdaten} und der Szene (Gelände aus
 * {@code daten/gelaende}, Gletscher mit der Front des Geländes und dem Jahresgang). Aufruf:
 * {@code StartdatenSql [daten/gelaende] [db/sql/04_startdaten.sql]}. Die Prüfung vergleicht die Datei mit dem, was
 * dieses Werkzeug jetzt schreiben würde.
 */
public final class StartdatenSql {
    public static void main(String[] a) throws Exception {
        File dir = new File(a.length > 0 ? a[0] : "daten/gelaende");
        File out = new File(a.length > 1 ? a[1] : "db/sql/04_startdaten.sql");
        String sql = erzeugen(dir);
        Files.writeString(out.toPath(), sql, StandardCharsets.UTF_8);
        System.out.println(out + ": " + sql.length() / 1024 + " kB, " + sql.lines().filter(l -> l.startsWith("-- ZAHLEN")).findFirst().orElse(""));
    }

    /** Das Skript aus dem Gelände in dir. */
    public static String erzeugen(File dir) {
        return com.dan.glacier.data.StartdatenSql.sql(szene(dir));
    }

    /** Die Szene der Startdaten: Gelände und Fronten. */
    public static Startdaten.Szene szene(File dir) {
        return Startdaten.szene(Terrain.load(dir), dir);
    }

    private StartdatenSql() { }
}
