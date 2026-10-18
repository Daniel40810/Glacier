// terrain.frag — das Gelände am Perito Moreno (Phase 2). Eingaben: terrain.vert (vWorld, vNormal, vDist), die Herkunft
// je Zelle (terrain_height.glsl: 0 Fels, 1 See, 2 Eis, 3 modelliert) und die Karte der Bedeckung uCover (Vegetation.java,
// 25 m): R Wald, G Steppe, B Moräne, A steiler Fels. Was wo liegt [G]:
//   Eis: die Zunge als weiße Fläche (Phase 2), leicht blau in den Mulden, Firnglanz, die Stirn bläulich; weich aus
//   den Nachbarzellen gemischt (keine Treppen); das Material kommt in Phase 4.
//   Seegrund: grauer Schluff aus Gletschermehl (der See darüber zeichnet com.dan.ocean).
//   Fels: graubraun, nach oben heller; Schnee ab gut 1 200 m über dem See auf flachen Stellen, steile Flanken frei.
//   Moräne: frischer, heller Schutt in den ersten gut 100 m am Eis, wo kein Wald steht.
//   Steppe: strohgelb; Wald: Waldboden nah (die Bäume zeichnet com.dan.forest), in der Ferne das Kronendach.
// Licht: Sonne (mit der Schattenkarte der Bäume und den Wolkenschatten), Mond, Himmel von oben, Blitz, Dunst
// (common.glsl). Seit Phase 3 mit com.dan.sky alles vorbelichtet (uSkyMode 1), sonst der Verlauf aus Phase 2.
#include "common.glsl"
#include "terrain_height.glsl"
in vec3 vWorld;
in vec3 vNormal;
in float vDist;
out vec4 frag;
uniform sampler2D uCover;      // R Wald, G Steppe, B Moräne, A Fels (je 0..1), 25 m
uniform vec4 uCoverRect;       // x, z der Nordwestecke, Kantenlänge (m), an (1) / aus (0)
uniform vec3 uWood;            // ab x (m) Kronendach statt Waldboden, Reichweite der Bäume y, an z
uniform vec3 uSunAmb;          // Himmelslicht von oben (linear), Horizontfarbe getrennt in uSkyHor
uniform vec3 uSkyHor;
uniform vec3 uSunRgb;          // Sonnenlicht (linear)
uniform float uSnowLine;       // Schneegrenze über dem See (m), Sommer ≈ 1 250
uniform int uMarks;
uniform int uGlacier;          // Phase 5: 1 = der Gletscher aus com.dan.ice liegt darüber, hier ist der Grund (Fels, Schutt)
uniform sampler2DShadow uSunShadow;
uniform mat4 uSunShadowM;
uniform int uSunShadowOn;
// Phase 3: Wolkenschatten (Karte um das Auge aus com.dan.cloud), das stärkste Blitzlicht, Nässe nach Regen
uniform sampler2D uCloudShadow;
uniform vec4 uCloudRect;       // x, z der Ecke, Kantenlänge, an
uniform vec3 uFlashDir, uFlashRgb;
uniform float uWet;
// Phase 8: wo die Welle aus com.dan.wave an Land gelaufen ist (Zeit des letzten Nasswerdens je Zelle), fein und grob
uniform sampler2D uWaveWet0, uWaveWet1;
uniform vec4 uWaveR0, uWaveR1;   // x0, z0, Zelle (m), an
uniform float uWaveTime;

float wetOf(sampler2D t, vec4 r, vec2 p, out float inside) {
    ivec2 n = textureSize(t, 0);
    vec2 u = (p - r.xy) / r.z - 0.5;
    inside = 0.0;
    if (u.x < -0.5 || u.y < -0.5 || u.x > float(n.x) - 0.5 || u.y > float(n.y) - 0.5) return 0.0;
    inside = 1.0;
    vec2 i = floor(u), f = u - i;
    float s = 0.0;
    for (int b = 0; b <= 1; b++)
        for (int a = 0; a <= 1; a++) {
            float at = texelFetch(t, clamp(ivec2(i) + ivec2(a, b), ivec2(0), n - 1), 0).r;
            // nass, solange es noch nicht abgetrocknet ist: eine Viertelstunde
            float w = at < -1.0e8 ? 0.0 : 1.0 - smoothstep(0.0, 900.0, uWaveTime - at);
            s += w * (a == 0 ? 1.0 - f.x : f.x) * (b == 0 ? 1.0 - f.y : f.y);
        }
    return s;
}

float waveWet(vec2 p) {
    float in0 = 0.0, in1 = 0.0, w = 0.0;
    if (uWaveR0.w > 0.0) { w = wetOf(uWaveWet0, uWaveR0, p, in0); if (in0 > 0.0) return w; }
    if (uWaveR1.w > 0.0) w = wetOf(uWaveWet1, uWaveR1, p, in1);
    return w;
}

float hash21(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i), b = hash21(i + vec2(1.0, 0.0)), c = hash21(i + vec2(0.0, 1.0)), d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
float fbm(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) { s += a * vnoise(p); p = p * 2.03 + 11.7; a *= 0.5; }
    return s;
}

float sunShadowAt(vec3 p, vec3 n) {
    if (uSunShadowOn == 0) return 1.0;
    vec4 q = uSunShadowM * vec4(p + n * 0.3, 1.0);
    vec3 s = q.xyz / q.w * 0.5 + 0.5;
    if (any(lessThan(s.xy, vec2(0.002))) || any(greaterThan(s.xy, vec2(0.998))) || s.z > 1.0) return 1.0;
    vec2 tx = 1.0 / vec2(textureSize(uSunShadow, 0));
    float sum = 0.0;
    for (int j = -1; j <= 1; j++)
        for (int i = -1; i <= 1; i++) sum += texture(uSunShadow, vec3(s.xy + vec2(i, j) * tx * 1.5, s.z - 0.0008));
    float edge = smoothstep(0.0, 0.08, min(min(s.x, s.y), min(1.0 - s.x, 1.0 - s.y)));
    return mix(1.0, sum / 9.0, edge);
}

// Anteil der Eiszellen an (x, z), bilinear aus den vier nächsten Zellen
float iceFraction(vec2 w) {
    vec2 c = (w - uGrid.xy) / uGrid.z - 0.5;
    ivec2 i = ivec2(floor(c));
    vec2 f = c - vec2(i);
    int n = int(uGridN) - 1;
    float a = texelFetch(uSource, clamp(i, ivec2(0), ivec2(n)), 0).r == 2u ? 1.0 : 0.0;
    float b = texelFetch(uSource, clamp(i + ivec2(1, 0), ivec2(0), ivec2(n)), 0).r == 2u ? 1.0 : 0.0;
    float d = texelFetch(uSource, clamp(i + ivec2(0, 1), ivec2(0), ivec2(n)), 0).r == 2u ? 1.0 : 0.0;
    float e = texelFetch(uSource, clamp(i + ivec2(1, 1), ivec2(0), ivec2(n)), 0).r == 2u ? 1.0 : 0.0;
    return mix(mix(a, b, f.x), mix(d, e, f.x), f.y);
}

void main() {
    vec3 p = vWorld;
    vec3 n = normalize(vNormal);
    float y = p.y;
    uint src = outsideDist(p.xz) > 0.0 ? 3u : sourceAt(p.xz);
    vec4 cov = uCoverRect.w > 0.0 ? textureLod(uCover, (p.xz - uCoverRect.xy) / uCoverRect.z, 0.0) : vec4(0.0);
    float slope = 1.0 - n.y;                 // 0 eben, 0,3 ≈ 45°
    float fine = vnoise(p.xz * 0.35), mid = fbm(p.xz * 0.02), big = fbm(p.xz * 0.0015);
    float pxw = vDist * 0.0015;              // Größe eines Bildpunkts (m), grob

    // Eisanteil weich aus den vier Nachbarzellen, damit die Front keine Treppen aus Rasterzellen zeigt
    float ice = iceFraction(p.xz);
    // über dem Wasser zählt jede Zelle mit Eis daneben zum Eis: die Rampe vom Eis zum Seegrund ist im Raster die Stirn
    ice = src == 3u ? 0.0 : max(smoothstep(0.35, 0.65, ice), step(0.01, ice) * smoothstep(0.3, 1.5, y)) * smoothstep(-1.0, 1.0, y);
    if (uGlacier == 1) ice = 0.0;

    // Eis als weiße Fläche: Firn, in Mulden leicht blau, Querspalten als blaue Schatten nur in der Nähe;
    // die steile Stirn (im GLO-30 eine Rampe) bläulich wie eine Eiswand
    vec3 firn = vec3(0.78, 0.82, 0.86), blue = vec3(0.38, 0.62, 0.82);
    float dip = smoothstep(0.02, 0.12, slope);
    float cr = (1.0 - smoothstep(0.0, 0.35, abs(fract(p.x / 34.0 + 0.4 * fbm(p.xz * 0.004)) - 0.5) * 34.0 - 0.6))
             * smoothstep(0.45, 0.65, fbm(p.xz * 0.005 + 4.0)) * (1.0 - smoothstep(300.0, 1500.0, vDist));
    float dirt = smoothstep(0.66, 0.82, fbm(vec2(p.z * 0.003, p.x * 0.0004))) * 0.5;
    vec3 iceC = mix(firn, blue, dip * 0.5 + cr * 0.6);
    iceC = mix(iceC, vec3(0.30, 0.29, 0.27), dirt);
    iceC = mix(iceC, vec3(0.55, 0.70, 0.82), smoothstep(0.25, 0.5, slope) * 0.7);
    iceC *= 0.92 + 0.08 * mid;

    vec3 alb;
    float gloss = 0.0;
    if (src == 1u && y < 0.5) {
        // Seegrund: Schluff aus Gletschermehl, unten dunkler
        alb = mix(vec3(0.16, 0.16, 0.15), vec3(0.09, 0.10, 0.10), smoothstep(-5.0, -60.0, y)) * (0.85 + 0.3 * mid);
    } else {
        // Fels: graubraun, nach oben heller und kälter; Bänder und Flecken
        vec3 rockA = vec3(0.15, 0.135, 0.12), rockB = vec3(0.24, 0.24, 0.245);
        vec3 rock = mix(rockA, rockB, smoothstep(200.0, 1200.0, y + 120.0 * (big - 0.5)));
        rock *= 0.75 + 0.45 * mid + 0.1 * fine;
        // Wiese, Steppe, Moräne nach der Karte
        vec3 grass = vec3(0.075, 0.095, 0.04) * (0.85 + 0.3 * mid);
        vec3 steppe = vec3(0.30, 0.25, 0.13) * (0.85 + 0.3 * fine);
        vec3 moraine = vec3(0.27, 0.26, 0.245) * (0.8 + 0.35 * fbm(p.xz * 0.15));
        float soil = smoothstep(0.85, 0.55, cov.a) * smoothstep(1100.0, 800.0, y);
        alb = mix(rock, grass, soil * 0.6);
        alb = mix(alb, steppe, cov.g * 0.85);
        alb = mix(alb, moraine, cov.b * 0.8);
        // Wald: nah der Waldboden (dunkel, Laubstreu), fern das Kronendach in Lenga-Grün
        if (uWood.z > 0.0 || cov.r > 0.0) {
            float far = uWood.z > 0.0 ? smoothstep(uWood.x, mix(uWood.x, uWood.y, 0.7), vDist) : 1.0;
            vec3 floorC = vec3(0.07, 0.06, 0.045) * (0.8 + 0.4 * fine);
            vec3 crown = vec3(0.035, 0.085, 0.025) * (0.75 + 0.5 * fbm(p.xz * 0.08));
            float dens = smoothstep(0.15, 0.6, cov.r);
            alb = mix(alb, floorC, dens * (1.0 - far) * 0.8);
            alb = mix(alb, crown, dens * far);
        }
        // Ufer: nasser, dunkler Kies im Spülsaum
        alb = mix(alb, vec3(0.12, 0.115, 0.105), (1.0 - smoothstep(0.3, 2.5, y)) * 0.7);
        // Schnee: oberhalb der Schneegrenze auf flachen Stellen, Rinnen länger
        float snowH = uSnowLine + 180.0 * (big - 0.5) + 400.0 * smoothstep(0.15, 0.45, slope);
        float snow = smoothstep(snowH - 60.0, snowH + 120.0, y) * (1.0 - smoothstep(0.35, 0.55, slope));
        alb = mix(alb, vec3(0.80, 0.83, 0.87), snow);
        gloss = snow * 0.15;
        if (src == 3u) {
            // Rahmen außerhalb der Daten: Fels und Schnee, gleich gemischt, damit kein Saum entsteht
            alb = mix(rock, vec3(0.80, 0.83, 0.87), snow);
        }
    }
    alb = mix(alb, iceC, ice);
    gloss = mix(gloss, 0.25, ice);

    // Licht
    float sh = sunShadowAt(p, n);
    float cloud = uCloudRect.w > 0.0 ? textureLod(uCloudShadow, (p.xz - uCloudRect.xy) / uCloudRect.z, 0.0).r : 1.0;
    // nass nach Regen: dunkler, außer Eis und Schnee
    alb *= 1.0 - 0.3 * uWet * (1.0 - ice) * smoothstep(0.0, 3.0, y);
    // Phase 8: der nasse Saum, wo die Welle aufgelaufen ist – dunkler und glänzend
    if (uWaveR0.w > 0.0 || uWaveR1.w > 0.0) {
        float ww = waveWet(p.xz) * (1.0 - ice);
        alb *= 1.0 - 0.45 * ww;
        gloss = max(gloss, 0.3 * ww);
    }
    vec3 col;
    vec3 v = normalize(uCam - p);
    vec3 hv = normalize(v + uSun);
    float spec = pow(max(dot(n, hv), 0.0), 60.0) * 0.4;
    if (uSkyMode == 1) {
        // Himmelslicht als Bestrahlung/π (com.dan.sky), steile Flanken sehen weniger Himmel
        float ambW = 0.55 + 0.45 * n.y;
        vec3 sunC = sunLight() * cloud;
        col = alb * (sunC * max(dot(n, uSun), 0.0) * sh + moonLight() * max(dot(n, uMoonDir), 0.0) * cloud
                     + uSkyAmb * ambW + uFlashRgb * max(dot(n, uFlashDir), 0.0));
        col += sunC * gloss * spec * sh;
    } else {
        // Himmelslicht wie in Haithabu: Zenitfarbe des Platzhalters gewichtet (0,30 + 0,25 n.y), steile Flanken sehen mehr Horizont
        float diff = max(dot(n, uSun), 0.0) * sh;
        vec3 sky = mix(uSkyHor * 0.6, uSunAmb, 0.5 + 0.5 * n.y) * (0.30 + 0.25 * n.y);
        col = alb * (uSunRgb * diff + sky);
        col += uSunRgb * gloss * spec * sh;
    }

    if (uMarks == 1 && src != 3u) {
        // Raster 1 km als Orientierung
        vec2 g1k = abs(fract(p.xz / 1000.0 + 0.5) - 0.5) * 1000.0;
        float line = 1.0 - smoothstep(1.5, 1.5 + pxw * 2.0, min(g1k.x, g1k.y));
        float lum = dot(col, vec3(0.2126, 0.7152, 0.0722));
        col = mix(col, col * 0.5 + vec3(0.6, 1.0, 1.2) * lum * 0.6, line * 0.45 * (1.0 - smoothstep(4000.0, 12000.0, vDist)));
    }
    col = aerial(col, normalize(p - uCam), vDist);
    frag = vec4(col, 1.0);
}
