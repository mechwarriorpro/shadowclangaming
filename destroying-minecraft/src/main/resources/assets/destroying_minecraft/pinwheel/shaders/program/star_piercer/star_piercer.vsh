// Original: SpacePotatoee/Destroying-Minecraft — All Rights Reserved, ported to NeoForge for comparison (see THIRD_PARTY.md)
#include veil:light
#include veil:fog
#veil:buffer veil:camera VeilCamera

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 Color;
layout(location = 2) in vec3 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;


//Color.rg = UV0
//UV0.xy = normal.rg


uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform int renderingShadow;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec3 normal;
flat out ivec2 lightUV;
out vec2 texCoord0;

vec3 distort(in vec3 shadowPosition) {
    const float bias0 = 0.95;
    const float bias1 = 1.0 - bias0;

    float factorDistance = length(shadowPosition.xy);

    float distortFactor = factorDistance * bias0 + bias1;

    return shadowPosition * vec3(vec2(1.0 / distortFactor), 0.2);
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    if (renderingShadow == 1) {
        gl_Position.xyz = distort(gl_Position.xyz);
    }

    vertexDistance = fog_distance(Position, FogShape);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, UV0, vec4(0));
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    normal = Normal;
    lightUV = UV2;
    texCoord0 = UV0.xy;
}