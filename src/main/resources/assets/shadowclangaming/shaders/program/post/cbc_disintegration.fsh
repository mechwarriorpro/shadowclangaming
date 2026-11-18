#version 150
// CBC Enhanced Shells — Disintegration Shell shader (permission from arginou57)
// Original: matte black, cyan hex grid, glowing crack, pixel-dissolve.
// Original GLSL, inspired. Credit: arginou57, T50 permission. See THIRD_PARTY.md.
uniform sampler2D DiffuseSampler;
uniform float Strength;
uniform float Time;
in vec2 texCoord;
out vec4 fragColor;
float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }
float noise(vec2 p){ vec2 i=floor(p); vec2 f=fract(p); f=f*f*(3.0-2.0*f); float a=hash(i); float b=hash(i+vec2(1,0)); float c=hash(i+vec2(0,1)); float d=hash(i+vec2(1,1)); return mix(mix(a,b,f.x), mix(c,d,f.x), f.y); }
void main(){
    vec4 base = texture(DiffuseSampler, texCoord);
    vec2 uv = texCoord;
    float t = Time * 0.85;
    vec2 gUv = uv * 28.0;
    vec2 a = fract(gUv) - 0.5;
    float d = length(a);
    float hexEdge = 1.0 - smoothstep(0.40, 0.42, d);
    float hex = hexEdge * smoothstep(0.0, 0.4, fract(t*0.7 + hash(floor(gUv))*2.0));
    float dissolve = noise(uv*18.0 + t*0.6);
    float thresh = Strength * 0.85;
    float keep = step(dissolve - 0.15, thresh);
    float edge = smoothstep(thresh-0.08, thresh, dissolve) * keep;
    vec3 crackCol = vec3(0.1, 0.95, 1.0) * edge * Strength * 1.4;
    vec3 hexCol = vec3(0.0, 0.55, 0.65) * hex * Strength * 0.5 * keep;
    vec3 outCol = base.rgb * keep + hexCol + crackCol;
    outCol = mix(outCol, vec3(0.02,0.02,0.03), (1.0-keep)*0.85);
    fragColor = vec4(outCol, base.a);
}
