#version 150
// CBC Enhanced Shells — Vortex Shell shader (permission from arginou57)
// Original: storm teal helical bands, swirling vortex + chromatic aberration.
// Original GLSL, inspired by description. Credit: arginou57, T50 permission. See THIRD_PARTY.md.
uniform sampler2D DiffuseSampler;
uniform float Strength;
uniform float Time;
in vec2 texCoord;
out vec4 fragColor;
void main(){
    vec4 base = texture(DiffuseSampler, texCoord);
    vec2 uv = texCoord - vec2(0.5);
    float dist = length(uv);
    float angle = atan(uv.y, uv.x);
    float t = Time * 1.2;
    float swirl = angle + dist*8.0 - t*2.5;
    float bands = sin(swirl*3.0) * 0.5 + 0.5;
    float fall = 1.0 - smoothstep(0.15, 0.55, dist);
    bands *= fall * smoothstep(0.02, 0.08, dist);
    float chroma = Strength * 0.012 * (0.5 + bands*0.5);
    float r = texture(DiffuseSampler, texCoord + vec2(chroma, 0)).r;
    float g = base.g;
    float b = texture(DiffuseSampler, texCoord - vec2(chroma, 0)).b;
    vec3 aberr = vec3(r,g,b);
    vec3 vortexCol = vec3(0.15, 0.75, 0.68) * bands * Strength * 0.6;
    vec3 outCol = mix(base.rgb, aberr, Strength*0.35) + vortexCol;
    float eye = exp(-dist*12.0) * Strength * 0.4;
    outCol += vec3(0.9,1.0,0.95)*eye;
    fragColor = vec4(outCol, base.a);
}
