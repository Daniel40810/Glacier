// underwater.frag — Phase 9, Phase 10 überarbeitet: unter dem Spiegel. Das Gletschermehl trübt das Wasser: was weiter
// weg ist, geht im türkisen Streulicht unter (exp(−d/Sicht)), was tief liegt, bekommt weniger Licht von oben
// (exp(−Tiefe/uAtten)). Die Oberfläche von unten: im Snellschen Fenster (48,6° um die Senkrechte) sieht man durch, außerhalb
// spiegelt sie das Wasser darunter (Totalreflexion). Mischung über den HDR-Puffer: Ziel · Alpha + Quelle
// (glBlendFunc(ONE, SRC_ALPHA)).
#include "common.glsl"
in vec2 vUV;
out vec4 frag;
uniform sampler2D uDepth;     // Kopie der Tiefe der Szene
uniform vec3 uWaterLight;     // Streulicht im Wasser (vorbelichtet)
uniform float uVis;           // Sichtweite (m)
uniform float uAtten;         // Tiefe, auf der das Licht von oben auf 1/e fällt (m)

void main() {
    float d = texture(uDepth, vUV).r;
    vec2 ndc = vUV * 2.0 - 1.0;
    vec3 p;
    float dist;
    bool sky = uReversed == 1 ? d <= 1.0e-7 : d >= 1.0 - 1.0e-7;
    if (sky) {
        vec4 q = uInvVP * vec4(ndc, uReversed == 1 ? 0.5 : 0.0, 1.0);
        p = uCam + normalize(q.xyz / q.w - uCam) * 1.0e5;
        dist = 1.0e5;
    } else {
        float z = uReversed == 1 ? d : d * 2.0 - 1.0;
        vec4 q = uInvVP * vec4(ndc, z, 1.0);
        p = q.xyz / q.w;
        dist = length(p - uCam);
    }
    vec3 dir = (p - uCam) / max(dist, 1.0e-3);
    float below = max(0.0, uLevel - uCam.y);
    // Streulicht: heller zur Oberfläche und nach oben, dunkler in der Tiefe
    vec3 fog = uWaterLight * exp(-below / 40.0) * mix(0.45, 1.5, smoothstep(-0.4, 0.9, dir.y));
    float f = 1.0 - exp(-dist / uVis);
    // Licht, das von oben bis zum Punkt kommt
    float keep = exp(-max(0.0, uLevel - p.y) / uAtten);
    // die Oberfläche von unten (oder der Himmel durch sie)
    if (sky || abs(p.y - uLevel) < 1.5 && dir.y > 0.0) {
        float cosT = clamp(dir.y, 0.0, 1.0);
        float window = smoothstep(0.62, 0.70, cosT);    // cos 48,6° = 0,66
        // außerhalb: Totalreflexion (das trübe Wasser darunter), im Fenster: der Himmel, gedämpft durch den Weg
        vec3 mirror = uWaterLight * exp(-below / 40.0) * 0.55;
        float through = window * exp(-below / uVis) * 0.9;
        frag = vec4(mirror * (1.0 - window) + fog * window * (1.0 - exp(-below / uVis)), through);
        return;
    }
    frag = vec4(fog * f, (1.0 - f) * keep);
}
