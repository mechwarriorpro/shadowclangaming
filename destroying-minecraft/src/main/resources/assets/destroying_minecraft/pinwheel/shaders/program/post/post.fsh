// Original: SpacePotatoee/Destroying-Minecraft — All Rights Reserved, ported to NeoForge for comparison (see THIRD_PARTY.md)
#include veil:space_helper
#include veil:blend
#include destroying_minecraft:ray_march
#include destroying_minecraft:noise
#veil:buffer veil:camera VeilCamera

uniform sampler2D MainTexture;
uniform sampler2D MainDepth;
uniform sampler2D BlurredTexture;

uniform vec2 ScreenSize;
uniform float centerDepth;
uniform float GameTime;
uniform float glitchTime;
uniform int enabledDepthOfField;
uniform int enabledBlackScreen;

const int ITERATIONS = 5;

in vec2 texCoord;
out vec4 fragColor;

float octave(float x){
    return mod(sin(x * 2.0) * sin(x * 4.0) * sin(x * 32.0), 1.0);
}

void main() {
    vec3 color = texture(MainTexture, texCoord).rgb;
    vec3 blurredTexture = vec3(0.0);
    float blur = 0;

    if (enabledBlackScreen == 1) {
        fragColor = vec4(0.0);
        return;
    }

    if (enabledDepthOfField == 1) {
        blurredTexture = texture(BlurredTexture, texCoord).rgb;
        float depth = texture(MainDepth, texCoord).r;
        vec3 playerPos = screenToLocalSpace(texCoord, depth).xyz;
        depth = length(playerPos) / 100;

        float dist = max(depth - centerDepth, 0.0);

        blur = smoothstep(0.0, 0.2, dist);
    }


    if (glitchTime > 0.0) {
        vec2 uv2 = vec2(texCoord.x + octave(texCoord.y + GameTime * 2000.0) * 0.01, texCoord.y);
        uv2 += (rand(vec2(GameTime * 4562.0))) * 0.01;

        uv2 = mix(texCoord, uv2, glitchTime);

        float pixelCount = mix(1000, 700, glitchTime);
        float ratio = ScreenSize.y / ScreenSize.x;
        float scaledPixelCount = pixelCount * ratio;
        vec2 finalRatio = vec2(pixelCount, scaledPixelCount);

        vec2 uv = (uv2) * finalRatio;
        uv = floor(uv) / finalRatio;

        float chromAbb = 0.005 * glitchTime;

        color.r = texture(MainTexture, uv - chromAbb).r;
        color.g = texture(MainTexture, uv + chromAbb).g;
        color.b = texture(MainTexture, uv).b;

        blurredTexture.r = texture(BlurredTexture, uv - chromAbb).r;
        blurredTexture.g = texture(BlurredTexture, uv + chromAbb).g;
        blurredTexture.b = texture(BlurredTexture, uv).b;
    }

    fragColor = vec4(mix(color, blurredTexture, blur), 1.0);
}