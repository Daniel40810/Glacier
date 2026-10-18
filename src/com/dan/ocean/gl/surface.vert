// surface.vert — das Ringgitter um die Kamera: jede Ecke liest die Auslenkung aller Kaskaden an ihrem Ruhepunkt
// (gefiltert nach Eckenabstand, damit kleine Wellen nicht zwischen den Ecken flimmern), dazu die Dünung, und sinkt mit
// der Entfernung um d²/2R unter die Waagerechte (Erdkrümmung, die Kimm liegt richtig).
#include "ocean_common.glsl"
layout(location = 0) in vec2 aPos;      // Lage relativ zum Mittelpunkt in m
layout(location = 1) in float aSpacing; // Eckenabstand an dieser Stelle in m
out vec2 vRest;
out vec3 vWorld;
out float vHeight;
out float vDist;
out float vDepth;

void main() {
    vec2 rest = uCenter + aPos;
    vec3 d = vec3(0.0);
    for (int c = 0; c < 4; c++) {
        if (c >= uCascades) break;
        float texel = uLength[c] / float(textureSize(uDisp, 0).x);
        float lod = max(0.0, log2(aSpacing / texel) + 0.5);
        d += textureLod(uDisp, vec3(rest / uLength[c], float(c)), lod).xyz;
    }
    vec3 sd, ss;
    swell(rest, sd, ss);
    d += sd;
    // seit Haithabu: zum Ufer hin flacher (unter 1,5 m Tiefe), auf dem Trockenen ganz still
    float depth = bedDepth(rest);
    d *= smoothstep(0.0, 1.5, depth);
    vec2 flatPos = rest + d.xz;
    float dist = length(flatPos - uCam.xz);
    vec3 world = vec3(flatPos.x, uLevel + d.y - dist * dist / (2.0 * EARTH_R), flatPos.y);
    // seit Glacier Phase 8: die lange Welle hebt den Spiegel; wo sie den Grund freilegt, taucht die Fläche unter ihn
    vec3 lw = longWave(rest);
    if (lw.z > 0.0) {
        if (lw.y > 0.0) world.y += lw.x;
        else if (depth >= 0.0 && depth < 6.0) world.y -= depth + 0.3;
    }
    vRest = rest;
    vWorld = world;
    vHeight = d.y;
    vDist = dist;
    vDepth = depth;
    gl_Position = uVP * vec4(world, 1.0);
}
