// melt.vert — Schmelzbäche auf dem Gletscher (Phase 5): ein Band entlang des Laufs aus com.dan.river, zwei Ecken je
// Stützpunkt. aFlow: Bogenlänge (m), Lage quer (−1 links … 1 rechts), Geschwindigkeit (m/s), Gefälle.
#include "common.glsl"
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec4 aFlow;
layout(location = 2) in vec2 aDir;
out vec3 vWorld;
out vec4 vFlow;
out vec2 vDir;
void main() {
    vWorld = aPos;
    vFlow = aFlow;
    vDir = aDir;
    gl_Position = uVP * vec4(aPos, 1.0);
}
