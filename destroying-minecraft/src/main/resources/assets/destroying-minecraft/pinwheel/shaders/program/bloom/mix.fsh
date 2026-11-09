// Original: SpacePotatoee/Destroying-Minecraft — All Rights Reserved, ported to NeoForge for comparison (see THIRD_PARTY.md)

uniform sampler2D DiffuseSampler;
uniform sampler2D HandDepth;
uniform sampler2D DepthComponent;
uniform sampler2D FinalSampler;
uniform sampler2D MipMapSampler;

in vec2 texCoord;
out vec4 fragColor;

float gamma = 0.5;

const float _Ldmax = 2.15f;
const float _Cmax = 0.01f;


float luminance(vec3 color) {
    return dot(color, vec3(0.299f, 0.587f, 0.114f));
}

float getLuminance(){
    float offset = (1.0 - (1.0/ 256));
    vec2 uv = (vec2(0.5 + offset * 512, 0.5)) / 512;
    return luminance(texture(MipMapSampler, uv).rgb);
}

vec3 simpleReinhardToneMapping(vec3 color) {
    float exposure = 1.5;
    color *= exposure/(1. + color / exposure);
    color = pow(color, vec3(1. / 1.2));
    return color;
}

vec4 TRTonemapping(vec3 col){

    float Lin = luminance(col);

    float Lavg = getLuminance();

    float logLrw = log(Lavg) + 0.84;
    float alphaRw = 0.4 * logLrw + 2.92;
    float betaRw = -0.4 * logLrw * logLrw - 2.584 * logLrw + 2.0208;
    float Lwd = _Ldmax / sqrt(_Cmax);
    float logLd = log(Lwd) + 0.84;
    float alphaD = 0.4 * logLd + 2.92;
    float betaD = -0.4 * logLd * logLd - 2.584 * logLd + 2.0208;
    float Lout = pow(Lin, alphaRw / alphaD) / _Ldmax * pow(10.0, (betaRw - betaD) / alphaD) - (1.0 / _Cmax);

    vec3 Cout = col / Lin * Lout;

    return vec4(clamp(Cout, 0.0, 1.0), 1.0f);
}

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    float depth = texture(DepthComponent, texCoord).r;
    float handDepth = texture(HandDepth, texCoord).r;
    vec4 blackHole = texture(FinalSampler, texCoord);

//    if(depth >= 1.0 && handDepth >= 1.0){
//        finalColor = blackHole;
//    } else {
//        finalColor = color;
//    }

//    finalColor.rgb = simpleReinhardToneMapping(blackHole.rgb);

    fragColor = blackHole;
//    fragColor = vec4(getLuminance());


}