#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;

// CYBER//UI: the loading screen fills the window with the Mojang red
// (#EF323D). Swap exactly that colour for near-black; the fade-in/out alpha
// is preserved. Applies whenever resources are reloaded with the pack active.
const vec3 MOJANG_RED = vec3(239.0, 50.0, 61.0) / 255.0;
const vec3 CYBER_BG = vec3(10.0, 10.0, 15.0) / 255.0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vec4 color = Color;
    if (all(lessThan(abs(color.rgb - MOJANG_RED), vec3(0.5 / 255.0)))) {
        color.rgb = CYBER_BG;
    }
    vertexColor = color;
}
