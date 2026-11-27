#version 330
#extension GL_ARB_separate_shader_objects : require

// Can't moj_import in things used during startup, when resource packs don't exist.
// This is a copy of dynamicimports.glsl and projection.glsl
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out vec4 vertexColor;

// CYBER//UI: the loading screen fades with the Mojang red (#EF323D). Swap
// exactly that colour for near-black; the fade alpha is preserved.
const vec3 MOJANG_RED = vec3(239.0, 50.0, 61.0) / 255.0;
const vec3 CYBER_BG = vec3(10.0, 10.0, 15.0) / 255.0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    if (all(lessThan(abs(Color.rgb - MOJANG_RED), vec3(0.5 / 255.0)))) {
        vertexColor.rgb = CYBER_BG;
    }
}
