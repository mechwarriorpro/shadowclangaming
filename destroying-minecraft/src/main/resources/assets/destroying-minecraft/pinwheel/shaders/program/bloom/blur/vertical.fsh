// Original: SpacePotatoee/Destroying-Minecraft — All Rights Reserved, ported to NeoForge for comparison (see THIRD_PARTY.md)
uniform sampler2D DiffuseSampler;
uniform sampler2D HandDepth;
uniform sampler2D DepthComponent;

uniform vec2 ScreenSize;
uniform float xLimit;
uniform float blurStrength;

in vec2 texCoord;
out vec4 fragColor;

//https://lisyarus.github.io/blog/posts/blur-coefficients-generator.html#:~:text=OFFSETS%20are%20offsets%20in%20pixels,gives%20to%20the%20output%20value.
const float OFFSETS[11] = float[11](
    -9.318646266210974,
    -7.354343693774204,
    -5.391740969253486,
    -3.430453776060771,
    -1.4700359482354282,
        0.4900013331200345,
    2.450166002687522,
    4.410959565941335,
    6.372852233686804,
    8.336261302595647,
    10
);

const float WEIGHTS[11] = float[11](
    0.006504635171009163,
    0.024688296407069085,
    0.06844262489475733,
    0.1386228151585448,
    0.2051594440697814,
        0.2218914988346649,
    0.1753844147581679,
    0.10130155891340226,
    0.04275193447972078,
    0.013180099602575794,
    0.0020726777103066527
);

vec2 multiplier = vec2(0.0, 0.5 * blurStrength);

void main() {
    if(texCoord.x <= xLimit) {
        //half the offset for better results on lower mipmaps
        vec4 color = texture(DiffuseSampler, texCoord + vec2(OFFSETS[5]/ScreenSize) * multiplier) * WEIGHTS[5];
        float count = 1.0;
        for(int i = 0; i < 5; i++) {
            color += texture(DiffuseSampler, texCoord + vec2(OFFSETS[i+6]/ScreenSize) * multiplier) * WEIGHTS[i+6];
            color += texture(DiffuseSampler, texCoord + vec2(OFFSETS[i]/ScreenSize) * multiplier) * WEIGHTS[i];
            count += 1;
        }

        fragColor = color;
    }

}