package com.dan.glacier.scene;

import com.dan.glacier.wiring.Clock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Die Uhr der Szene mit Datum (Werkbuch, Abschnitt 6): Sie startet am 16. Januar 2026 um 15:00 Uhr [G], Hochsommer
 * auf der Südhalbkugel, die Sonne im Nordwesten, und läuft mit Zeitraffer bis 3600-fach (ein Tag in 24 s, für das
 * Jahr in Phase 11).
 * <p>
 * Zeit: Argentinien zählt das ganze Jahr nach UTC−3 (ART, ohne Sommerzeit). Die wahre Ortszeit am Gletscher (73° W)
 * liegt fast zwei Stunden dahinter; darum steht die Sonne erst gegen 14 Uhr im Norden, und im Dezember geht sie
 * nach 22 Uhr unter.
 * <p>
 * Die Uhr ist zugleich die Schnittstelle {@link Clock}, über die alle Packages Zeit bekommen.
 */
public final class SceneClock implements Clock {
    public static final double MAX_LAPSE = 3600;
    /** Argentinische Zeit, UTC−3, ohne Sommerzeit. */
    public static final ZoneOffset ZONE = ZoneOffset.ofHours(-3);
    /** Start der Szene: 16. Januar 2026, 15:00 Uhr [G]. */
    public static final LocalDate START_DATE = LocalDate.of(2026, 1, 16);
    public static final double START_HOUR = 15.0;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.u", Locale.GERMANY),
            TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY);

    /** Szenenzeit in ms seit 1970 (UTC), als double, damit kleine Schritte nicht verloren gehen. */
    private volatile double epochMs;
    /** Zeitraffer: Szenensekunden je echter Sekunde (0 = Uhr steht). */
    public volatile double lapse = 1;
    /** Ort für Sonne, Mond und Sterne. */
    public volatile double lat = Site.LAT, lon = Site.LON;
    /** Laufzeit der Szene in s (für Wellen und Animation; läuft auch bei stehender Uhr) und letzter Schritt. */
    private double t, dt;

    public SceneClock() { this(START_DATE, START_HOUR); }

    public SceneClock(LocalDate date, double hour) { set(date, hour); }

    /** Datum und Uhrzeit (Stunden, ART) setzen. */
    public synchronized void set(LocalDate date, double hour) {
        hour = Math.max(0, Math.min(24 - 1e-9, hour));
        int sec = (int) Math.floor(hour * 3600);
        ZonedDateTime z = ZonedDateTime.of(date, LocalTime.ofSecondOfDay(sec), ZONE);
        epochMs = z.toInstant().toEpochMilli() + (hour * 3600 - sec) * 1000;
    }

    public synchronized void setHour(double hour) { set(date(), hour); }

    /** Läuft um dt echte Sekunden weiter. */
    public synchronized void step(double realDt) {
        dt = realDt;
        t += realDt;
        epochMs += realDt * Math.max(0, Math.min(MAX_LAPSE, lapse)) * 1000;
    }

    public synchronized void shiftDays(int days) { epochMs += days * 86_400_000.0; }

    public ZonedDateTime local() { return Instant.ofEpochMilli((long) Math.floor(epochMs)).atZone(ZONE); }
    public LocalDate date() { return local().toLocalDate(); }
    public double hour() {
        ZonedDateTime z = local();
        return z.getHour() + z.getMinute() / 60.0 + (z.getSecond() + (epochMs - Math.floor(epochMs / 1000) * 1000) / 1000) / 3600.0;
    }

    /** Sonnenhöhe und -azimut in Grad an der Front. */
    public double[] sun() { return Sun.position(epochMs, lat, lon); }

    /** Szenenzeit direkt setzen (ms seit 1970, UTC), etwa für „Weiter wie zuletzt“ in Phase 15. */
    public synchronized void setEpochMillis(double ms) { epochMs = ms; }

    /** „16.1.2026 15:00 ART“ */
    public String label() { return dateLabel() + " " + timeLabel(); }
    public String dateLabel() { return DATE.format(local()); }
    public String timeLabel() { return TIME.format(local()) + " ART"; }

    // ------------------------------------------------------------------ Clock
    @Override public double t() { return t; }
    @Override public double dt() { return dt; }
    @Override public double epochMillis() { return epochMs; }
    @Override public double latDeg() { return lat; }
    @Override public double lonDeg() { return lon; }
}
