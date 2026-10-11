#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;

// Nebula UI: the loading screen fills the window with the Mojang red
// (#EF323D). Swap exactly that colour for a dark navy; the fade-in/out alpha
// is preserved. Applies whenever resources are reloaded with the pack active.
const vec3 MOJANG_RED = vec3(239.0, 50.0, 61.0) / 255.0;
const vec3 NEBULA_BG = vec3(12.0, 14.0, 27.0) / 255.0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vec4 color = Color;
    if (all(lessThan(abs(color.rgb - MOJANG_RED), vec3(0.5 / 255.0)))) {
        color.rgb = NEBULA_BG;
    }
    vertexColor = color;
}
