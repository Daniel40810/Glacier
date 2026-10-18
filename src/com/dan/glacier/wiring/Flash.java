package com.dan.glacier.wiring;

/** Ein Blitz. Gibt: {@code lightning} (Phase 3). Nimmt: cloud, ocean, ice (Licht im Eis), weathering, sky, soundscape. */
@FunctionalInterface
public interface Flash {
    /** Ort des Kanals (Mitte), Stärke 0..1, Szenenzeit in s, Dauer in s. */
    void flash(double x, double y, double z, double strength, double time, double duration);
}
