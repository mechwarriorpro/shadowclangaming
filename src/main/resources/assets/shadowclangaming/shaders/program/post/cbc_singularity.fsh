#version 150
// CBC Enhanced Shells — Singularity Shell shader (permission from arginou57)
// Original: starry violet-black, black hole with accretion disk, gravitational lensing.
// Original GLSL, inspired. Credit: arginou57, T50 permission. See THIRD_PARTY.md.
uniform sampler2D DiffuseSampler;
uniform float Strength;
uniform float Time;
in vec2 texCoord;
out vec4 fragColor;
float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }
void main(){
    vec4 base = texture(DiffuseSampler, texCoord);
    vec2 uv = texCoord - vec2(0.5);
    float dist = length(uv);
    float angle = atan(uv.y, uv.x);
    float t = Time * 0.5;
    float bhRadius = 0.12 + Strength*0.04;
    float shadow = 1.0 - smoothstep(bhRadius-0.025, bhRadius+0.025, dist);
    float ring = exp(-pow((dist - bhRadius - 0.015)*32.0, 2.0)) * Strength;
    float diskR = dist * (1.0 + 0.35*sin(angle));
    float doppler = 0.7 + 0.3*cos(angle - 0.6);
    float inner = 1.0 - smoothstep(0.16, 0.18, abs(diskR - 0.22));
    float outer = 1.0 - smoothstep(0.2, 0.5, dist);
    float disk = inner * outer * doppler * Strength * 0.8;
    float diskAngle = angle + t*1.8;
    vec3 diskCol = mix(vec3(0.6,0.2,0.9), vec3(1.0,0.55,0.15), fract(diskAngle*2.0/3.1415)) * disk;
    if (diskAngle > 2.8 && diskAngle < 3.4) diskCol *= 0.3;
    float lens = shadow * Strength * 0.22 * (1.0 - smoothstep(0.0, 0.3, dist));
    vec2 lensedUv = (uv * (1.0 + lens*1.5)) + vec2(0.5);
    vec3 lensed = texture(DiffuseSampler, clamp(lensedUv, 0.0,1.0)).rgb;
    vec3 outCol = mix(base.rgb, lensed, shadow*0.6);
    vec3 stars = vec3(0.35, 0.18, 0.65) * exp(-dist*1.8) * Strength * 0.2;
    outCol = outCol * (1.0 - shadow*0.92) + diskCol + vec3(1.0,0.95,0.85)*ring*1.2 + stars;
    fragColor = vec4(outCol, base.a);
}
