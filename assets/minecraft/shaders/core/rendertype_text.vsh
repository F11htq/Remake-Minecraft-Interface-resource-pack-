#version 150

#moj_import <minecraft:fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

// CYBER//UI: container titles ("Inventory", "Chest", ...) are drawn in a
// hard-coded dark grey (#404040) that is unreadable on dark panels. Recolour
// exactly that colour, and only on screens (orthographic projection), so that
// text in the world (signs, name tags) is never affected.
const vec3 VANILLA_LABEL = vec3(64.0 / 255.0);
const vec3 CYBER_LABEL = vec3(252.0, 238.0, 10.0) / 255.0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vec4 color = Color;
    bool isGui = abs(ProjMat[3][3] - 1.0) < 0.001;
    if (isGui && all(lessThan(abs(color.rgb - VANILLA_LABEL), vec3(0.5 / 255.0)))) {
        color.rgb = CYBER_LABEL;
    }

    vertexDistance = fog_distance(Position, FogShape);
    vertexColor = color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}
