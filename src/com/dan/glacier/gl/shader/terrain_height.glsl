// terrain_height.glsl — Höhe des Geländes für Vertex- und Fragment-Shader (Phase 2, aus Haithabu).
// Höhentextur R16 (normiert, 2048² zu 6,25 m, mit Mip-Stufen), Höhe = Wert · uHMap.x + uHMap.y, bilinear wie
// Terrain.height in Java, damit Kamera und Bild dieselbe Fläche sehen. In der Ferne aus gröberen Mip-Stufen (stetig
// nach der Entfernung, damit Nachbarknoten dieselbe Fläche sehen). Außerhalb des Rasters wachsen vom Rand aus Berge
// als Rahmen [G]: der Rand, dazu mit der Entfernung steigend ein Kammrauschen bis gut 2 000 m über dem See.
uniform sampler2D uHeight;
uniform usampler2D uSource;    // Herkunft je Zelle: 0 Fels, 1 See, 2 Eis, 3 modelliert
uniform vec3 uGrid;            // X0, Z0 (Nordwestecke), Zellgröße
uniform float uGridN;          // Zellen je Seite
uniform vec2 uHMap;            // Spanne, kleinste Höhe
uniform vec3 uLodRange;        // Sichtweite der feinsten Stufe (m), Beginn des Gleitens (Anteil), Sichtweite in Knotengrößen

vec2 gridUV(vec2 w) { return (w - uGrid.xy) / (uGrid.z * uGridN); }

// Mip-Stufe nach der Entfernung: ein Texel etwa so groß wie der Punktabstand dort
float heightLevel(float dist) { return clamp(log2(max(dist, 1.0) / (64.0 * uLodRange.z * uGrid.z)), 0.0, 9.0); }

float th_hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float th_noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(th_hash(i), th_hash(i + vec2(1, 0)), f.x), mix(th_hash(i + vec2(0, 1)), th_hash(i + vec2(1, 1)), f.x), f.y);
}
// Kämme: 1 − |2n − 1| über drei Oktaven (scharfe Grate), in m
float th_ridges(vec2 w) {
    float s = 0.0, a = 1.0, f = 1.0 / 3200.0;
    for (int i = 0; i < 4; i++) { s += a * (1.0 - abs(2.0 * th_noise(w * f + float(i) * 17.3) - 1.0)); a *= 0.5; f *= 2.1; }
    return s / 1.875;
}

// Abstand außerhalb des Rasters (m, 0 innen)
float outsideDist(vec2 w) {
    vec2 lo = uGrid.xy, hi = uGrid.xy + uGrid.z * uGridN;
    vec2 d = max(lo - w, w - hi);
    return length(max(d, vec2(0.0)));
}

float heightAt(vec2 w, float lvl) {
    float h = textureLod(uHeight, gridUV(w), lvl).r * uHMap.x + uHMap.y;
    float o = outsideDist(w);
    if (o > 0.0) {
        float rise = smoothstep(0.0, 6000.0, o);
        h = mix(max(h, 0.0), 300.0 + 1900.0 * th_ridges(w), rise);
    }
    return h;
}

float terrainHeight(vec2 w, float dist) { return heightAt(w, heightLevel(dist)); }

uint sourceAt(vec2 w) {
    ivec2 c = clamp(ivec2(floor((w - uGrid.xy) / uGrid.z)), ivec2(0), ivec2(int(uGridN) - 1));
    return texelFetch(uSource, c, 0).r;
}
