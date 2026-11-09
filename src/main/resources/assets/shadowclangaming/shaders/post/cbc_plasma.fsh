#version 150
// CBC Enhanced Shells — Plasma Shell shader (permission from arginou57, CurseForge 1489732)
// Original concept: volumetric plasma raymarching with white ceramic / sun emblem.
// This file is original GLSL inspired by that description, not a copy.
// Credit: CBC Enchanced Shells [Create Big Cannons] by arginou57 — extended to T50 with permission.
// See THIRD_PARTY.md. T1–T50 intensity is Strength uniform (0.02–1.0).
uniform sampler2D DiffuseSampler;
uniform float Strength;
uniform float Time;
in vec2 texCoord;
out vec4 fragColor;
float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }
float noise(vec2 p){ vec2 i=floor(p); vec2 f=fract(p); f=f*f*(3.0-2.0*f); float a=hash(i); float b=hash(i+vec2(1,0)); float c=hash(i+vec2(0,1)); float d=hash(i+vec2(1,1)); return mix(mix(a,b,f.x), mix(c,d,f.x), f.y); }
void main(){
    vec4 base = texture(DiffuseSampler, texCoord);
    vec2 uv = texCoord * 2.0 - 1.0;
    float dist = length(uv);
    float t = Time * 0.9;
    float n = noise(uv*3.0 + vec2(t*0.5, t*0.3));
    float n2 = noise(uv*6.0 - vec2(t*0.7, t*0.4));
    float fall = 1.0 - smoothstep(0.2, 1.4, dist);
    float plasma = (n * 0.6 + n2 * 0.4) * fall;
    float core = exp(-dist*2.8) * (0.7 + 0.3*sin(t*4.0));
    vec3 tint = mix(vec3(1.0, 0.65, 0.2), vec3(0.9, 0.95, 1.0), plasma);
    vec3 outCol = base.rgb + tint * plasma * Strength * 0.75 + vec3(1.0,0.82,0.45)*core*Strength*0.5;
    vec2 warp = vec2(noise(uv*4.0 + t), noise(uv*4.0 - t)) * 0.015 * Strength;
    outCol = mix(outCol, texture(DiffuseSampler, texCoord + warp).rgb, Strength * 0.15);
    fragColor = vec4(outCol, base.a);
}
