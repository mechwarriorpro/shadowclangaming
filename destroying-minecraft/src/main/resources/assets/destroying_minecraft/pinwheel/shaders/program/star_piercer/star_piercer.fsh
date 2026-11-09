// Original: SpacePotatoee/Destroying-Minecraft — All Rights Reserved, ported to NeoForge for comparison (see THIRD_PARTY.md)
#include veil:fog

uniform sampler2D Sampler0;
uniform sampler2D Sampler3;

uniform vec4 ColorModulator;
uniform vec4 FogColor;
uniform float FogStart;
uniform float FogEnd;
uniform float bloomTime;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec3 normal;
flat in ivec2 lightUV;
in vec2 texCoord0;

out vec4 fragColor;
layout (location = 1) out vec4 Albedo;
layout (location = 2) out vec3 OutNormal;
layout (location = 3) out vec2 LightUV;
layout (location = 4) out vec3 LightColor;
layout (location = 7) out vec3 Bloom;

void main() {
	vec4 color = texture(Sampler0, texCoord0);

	fragColor = color * lightMapColor;
	Albedo = color;
	OutNormal = normal;
	LightUV = lightUV;
	LightColor = lightMapColor.rgb;
	Bloom = texture(Sampler3, texCoord0).rgb * bloomTime * 15;
}