// Original: SpacePotatoee/Destroying-Minecraft — All Rights Reserved, ported to NeoForge for comparison (see THIRD_PARTY.md)
uniform sampler2D DiffuseSampler;

uniform vec2 ScreenSize;

in vec2 texCoord;
out vec4 fragColor;

const float padding = 0.025;

const int samples[10] = int[10](
    1,
    2,
    6,
    8,
    16,
    16,
    32,
    32,
    1,
    1
);

//I actually used a version of this in SPB revamped and I'm only now realizing that its VERY similar to how https://www.shadertoy.com/view/lstSRS did it
vec3 BloomLod(float scale, vec2 offset, int samples) {
    vec3 color = vec3(0.0);
    vec2 uv = ((texCoord - offset) * scale);
    if(uv.x > 1.0 || uv.y > 1.0 || uv.x < 0.0 || uv.y < 0.0){
        return vec3(0.0);
    }

    float divide = 0.0;
    for(int i = 0; i < samples; i++) {
        for(int j = 0; j < samples; j++) {
            vec2 offset2 = vec2(i, j) / ScreenSize;
            color += texture(DiffuseSampler, uv + offset2).rgb;
            color += texture(DiffuseSampler, uv - offset2).rgb;
            divide += 2;
        }
    }

    return color / divide;
}

void main() {
    vec4 color = vec4(0.0, 0.0, 0.0, 1.0);
    color.rgb += BloomLod(2,  vec2(0.0           , 0.0          )  , samples[0]);
    color.rgb += BloomLod(4,  vec2(0.0           , 0.5 + padding)  , samples[1]);
    color.rgb += BloomLod(8,  vec2(0.25 + padding, 0.5 + padding)  , samples[2]);
    color.rgb += BloomLod(16, vec2(0.4  + padding, 0.5 + padding)  , samples[3]);
    color.rgb += BloomLod(32, vec2(0.0           , 0.775 + padding), samples[4]);
    color.rgb += BloomLod(64, vec2(0.04          , 0.775 + padding), samples[5]);


    float Brightness = 0.3 * dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));


    fragColor = vec4(color.rgb * Brightness, 1.0);

//    fragColor = texture(DiffuseSampler, texCoord);


}