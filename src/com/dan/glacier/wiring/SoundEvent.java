package com.dan.glacier.wiring;

/**
 * Ein Ereignis für den Klang. Gibt: fracture (Knacken, Riss, Bruch, Aufschlag), wave (Auflaufen), flock, buoyancy
 * (Katamaran), lightning. Nimmt: {@code soundscape} (Phase 12, F18 A).
 */
@FunctionalInterface
public interface SoundEvent {
    enum Kind { KNACKEN, AECHZEN, RISS, KALBUNG, AUFSCHLAG, AUFLAUFEN, KONDOR, MOEWE, MOTOR, DONNER }

    void sound(Kind kind, double x, double y, double z, double strength);
}
