// Original: SpacePotatoee/Destroying-Minecraft — All Rights Reserved, ported to NeoForge for comparison (see THIRD_PARTY.md)
uniform sampler2D DiffuseSampler;
uniform sampler2D BHBloomSampler;
uniform sampler2D StartSampler;

uniform vec2 ScreenSize;

in vec2 texCoord;
out vec4 fragColor;

const float padding = 0.025;

vec3 BloomLod(float scale, vec2 offset){
    vec3 color = vec3(0.0);
    vec2 uv = (texCoord + offset * scale) / scale;
    if(uv.x > 1.0 || uv.y > 1.0 || uv.x < 0.0 || uv.y < 0.0){
        color = vec3(0.0, 0.0, 0.0);
    } else {
        color = texture(DiffuseSampler, uv).rgb;
    }

    return color;
}

void main() {
    vec4 color = texture(StartSampler, texCoord);

    vec4 highlights = vec4(0.0);
    float scale = 2.0;
    float offset = 0;

    highlights.rgb += BloomLod(2.0, vec2(0.0           , 0.0          ));
    highlights.rgb += BloomLod(4.0, vec2(0.0           , 0.5 + padding));
    highlights.rgb += BloomLod(8.0, vec2(0.25 + padding, 0.5 + padding));
    highlights.rgb += BloomLod(16.0, vec2(0.4  + padding, 0.5 + padding));
    highlights.rgb += BloomLod(32.0, vec2(0.0           , 0.775 + padding));
    highlights.rgb += BloomLod(64.0, vec2(0.04          , 0.775 + padding));



    fragColor = color + (highlights / 6);
}