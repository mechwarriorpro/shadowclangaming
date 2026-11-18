#version 150
// CBC Enhanced Shells — Gravitational Shell shader (permission from arginou57, CurseForge 1489732)
// Original: midnight blue spacetime grid sinking into bright core.
// This is original GLSL inspired by that description. Credit: arginou57, T50 extension with permission. See THIRD_PARTY.md.
uniform sampler2D DiffuseSampler;
uniform float Strength;
uniform float Time;
in vec2 texCoord;
out vec4 fragColor;
void main(){
    vec4 base = texture(DiffuseSampler, texCoord);
    vec2 uv = texCoord - vec2(0.5);
    float dist = length(uv);
    float t = Time * 0.6;
    float scale = 20.0;
    vec2 gUv = uv * scale;
    float lens = Strength * 0.18 * exp(-dist*3.0);
    vec2 lensed = uv * (1.0 - lens * (1.0 + sin(t*2.0)*0.15));
    vec2 lg = lensed * scale;
    float gx = smoothstep(0.0, 0.04, abs(fract(lg.x)-0.5));
    float gy = smoothstep(0.0, 0.04, abs(fract(lg.y)-0.5));
    float fade = 1.0 - smoothstep(0.1, 0.6, dist);
    float grid = (gx*gy) * fade * (0.5 + 0.5*sin(t*3.0 + dist*12.0));
    float horizon = (1.0 - smoothstep(0.0, 0.5, dist)) * exp(-pow(dist*4.0,2.0));
    vec3 gridCol = vec3(0.25, 0.45, 0.95) * grid * Strength;
    vec3 coreCol = vec3(0.85, 0.92, 1.0) * (exp(-dist*8.0)*Strength*2.0 + horizon*0.6*Strength) ;
    vec3 outCol = base.rgb * (1.0 - lens*0.3) + gridCol + coreCol;
    float desat = (1.0 - smoothstep(0.05, 0.35, dist)) * Strength * 0.25;
    float luma = dot(outCol, vec3(0.299,0.587,0.114));
    outCol = mix(outCol, vec3(luma), desat);
    fragColor = vec4(outCol, base.a);
}
