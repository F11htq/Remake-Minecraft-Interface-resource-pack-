#version 330
#extension GL_ARB_separate_shader_objects : require

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#include <minecraft:fog.glsl>
#include <minecraft:sample_lightmap.glsl>
#endif

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
layout(location = 3) in ivec2 UV2;
#endif

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
uniform sampler2D Sampler2;
layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
#endif

layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;

#ifdef IS_GUI
// CYBER//UI: container titles ("Inventory", "Chest", ...) are drawn in a
// hard-coded dark grey (#404040) that is unreadable on dark panels. Recolour
// exactly that colour; IS_GUI is only defined for on-screen text, so text in
// the world (signs, name tags) is never affected.
const vec3 VANILLA_LABEL = vec3(64.0 / 255.0);
const vec3 CYBER_LABEL = vec3(252.0, 238.0, 10.0) / 255.0;
#endif

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
#else
    vertexColor = Color;
#ifdef IS_GUI
    if (all(lessThan(abs(Color.rgb - VANILLA_LABEL), vec3(0.5 / 255.0)))) {
        vertexColor.rgb = CYBER_LABEL;
    }
#endif
#endif
    texCoord0 = UV0;
}
