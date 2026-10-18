package com.dan.glacier.gl;

import com.dan.cloud.Clouds;
import com.dan.fog.Fog;
import com.dan.forest.ForestField;
import com.dan.ice.Ice;
import com.dan.lightning.Lightning;
import com.dan.ocean.Ocean;
import com.dan.rain.Rain;
import com.dan.reed.Reed;

import java.util.Locale;

/**
 * Qualitätsstufen der App (aus Haithabu, entschlackt). Phase 1: Anteil der Bildgröße, in dem gerechnet wird, und das
 * Zeitbudget. Phase 2: Sichtweite des Geländes (Knotengrößen je Stufe), FFT des Sees ({@code com.dan.ocean}),
 * Reichweite und Dichte der Halme ({@code com.dan.reed}), Wald ({@code com.dan.forest}), Sonnenschatten der Bäume und
 * der Halbmesser der Bodendecke ({@code com.dan.ground}). Phase 3: Wolken, Regen, Nebel und Blitz (Stufen wie in
 * Haithabu). Phase 4: Eis. Phase 6: Bruch. Jede weitere Package-Phase hängt die Stufe ihres Packages an (Phase 4–9: ice, fracture, wave, buoyancy). Die Packages haben ihre eigene Kopie dieser
 * Aufzählung (Paketvertrag Punkt 7).
 * Befund Status_Phase3: der Regen kostete auf „Mittel“ 3,9 ms bei 1 mm/h; „Mittel“ regnet darum mit der Stufe LOW.
 * Ziel (F21 A): „Mittel“ hält 60 Bilder/s in Full HD auf der Radeon 680M, „Hoch“ mindestens 30.
 */
public enum Quality {
    NIEDRIG("Niedrig", 0.67, 1.8, Ocean.Quality.LOW, Reed.Quality.LOW, ForestField.Quality.LOW, 1024, 150, 16,
            Clouds.Quality.FLAT, Rain.Quality.LOW, Fog.Quality.LOW, Lightning.Quality.LOW, Ice.Quality.LOW, com.dan.fracture.Fracture.Quality.LOW),
    MITTEL("Mittel", 0.85, 2.3, Ocean.Quality.MEDIUM, Reed.Quality.MEDIUM, ForestField.Quality.MEDIUM, 2048, 220, 24,
            Clouds.Quality.MEDIUM, Rain.Quality.LOW, Fog.Quality.MEDIUM, Lightning.Quality.MEDIUM, Ice.Quality.MEDIUM, com.dan.fracture.Fracture.Quality.MEDIUM),
    HOCH("Hoch", 1.0, 2.8, Ocean.Quality.HIGH, Reed.Quality.HIGH, ForestField.Quality.HIGH, 2048, 300, 30,
            Clouds.Quality.HIGH, Rain.Quality.HIGH, Fog.Quality.HIGH, Lightning.Quality.HIGH, Ice.Quality.HIGH, com.dan.fracture.Fracture.Quality.HIGH),
    ULTRA("Ultra", 1.0, 3.4, Ocean.Quality.ULTRA, Reed.Quality.ULTRA, ForestField.Quality.ULTRA, 4096, 420, 36,
            Clouds.Quality.ULTRA, Rain.Quality.ULTRA, Fog.Quality.ULTRA, Lightning.Quality.ULTRA, Ice.Quality.ULTRA, com.dan.fracture.Fracture.Quality.ULTRA);

    public final String label;
    /** Anteil der Bildgröße, in dem gerechnet wird. */
    public final double renderScale;
    /** Phase 2: Sichtweite jeder Geländestufe in ihren Knotengrößen (CDLOD). */
    public final double terrainRange;
    /** Phase 2: Stufe des Sees, der Halme und des Waldes. */
    public final Ocean.Quality ocean;
    public final Reed.Quality reed;
    public final ForestField.Quality forest;
    /** Phase 2: Sonnenschattenkarte (Texel, halbe Kante in m) und Halbmesser der Bodendecke um das Auge (m). */
    public final int shadowSize;
    public final double shadowHalf, groundRadius;
    /** Phase 3: Wolken, Regen, Nebel, Blitz. */
    public final Clouds.Quality clouds;
    public final Rain.Quality rain;
    public final Fog.Quality fog;
    public final Lightning.Quality lightning;
    /** Phase 4: Eis (Proben durch die Risse: 2, 4, 6, 8). */
    public final Ice.Quality ice;
    /** Phase 6: Zellen je Körper und Durchgänge der Stöße in {@code com.dan.fracture}. */
    public final com.dan.fracture.Fracture.Quality fracture;

    Quality(String label, double renderScale, double terrainRange, Ocean.Quality ocean, Reed.Quality reed, ForestField.Quality forest,
            int shadowSize, double shadowHalf, double groundRadius, Clouds.Quality clouds, Rain.Quality rain, Fog.Quality fog,
            Lightning.Quality lightning, Ice.Quality ice, com.dan.fracture.Fracture.Quality fracture) {
        this.ice = ice;
        this.fracture = fracture;
        this.clouds = clouds;
        this.rain = rain;
        this.fog = fog;
        this.lightning = lightning;
        this.label = label;
        this.renderScale = renderScale;
        this.terrainRange = terrainRange;
        this.ocean = ocean;
        this.reed = reed;
        this.forest = forest;
        this.shadowSize = shadowSize;
        this.shadowHalf = shadowHalf;
        this.groundRadius = groundRadius;
    }

    /**
     * Bildzeit, die die Stufe höchstens verbrauchen darf (ms): „Mittel“ 15,7 ms nach dem Budget im Werkbuch
     * (Abschnitt 10, 1 ms Reserve auf 16,7 ms), „Hoch“ das Doppelte, „Niedrig“ die Hälfte.
     */
    public double budgetMs() {
        return switch (this) { case NIEDRIG -> 7.85; case MITTEL -> 15.7; case HOCH -> 31.4; case ULTRA -> 50.0; };
    }

    /** Was die Stufe einstellt, als eine Zeile (Bedienfeld, README). */
    public String summary() {
        return String.format(Locale.GERMAN, "%s: %d %% der Auflösung, Budget %.1f ms, Gelände %.1f Knoten, See %d × %d², Halme %.0f %% Reichweite, Wald bis %,.0f m, Sonnenschatten %d² über %.0f m, Bodendecke %.0f m, Wolken %s, Regen %s, Nebel %s, Blitz %s, Eis %s, Bruch %s",
                label, Math.round(renderScale * 100), budgetMs(), terrainRange, ocean.cascades, ocean.n, reed.range * 100, forest.radius, shadowSize, shadowHalf * 2, groundRadius,
                clouds, rain, fog, lightning, ice, fracture);
    }

    @Override public String toString() { return label; }
}
