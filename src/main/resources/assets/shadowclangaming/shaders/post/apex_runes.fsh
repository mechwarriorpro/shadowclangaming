#version 150

// arcane rune glow: pulsing sigils over the world
//
// Driven by two uniforms set from Java (see client/ApexShaderFx):
//   Strength  0..1, how hard the effect is currently pushing
//   Time      seconds since the chain was created, for animation
uniform sampler2D DiffuseSampler;
uniform float Strength;
uniform float Time;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    vec4 color = texture(DiffuseSampler, uv);

    vec4 base = texture(DiffuseSampler, uv);
    vec2 d = uv - vec2(0.5);
    float r = length(d);
    float angle = atan(d.y, d.x);
    float ring = (1.0 - smoothstep(0.0, 0.012, abs(r - 0.30 - 0.02 * sin(Time * 2.0))));
    float glyph = (1.0 - smoothstep(0.0, 0.05, abs(sin(angle * 12.0 + Time * 0.7) * r - 0.12)));
    float glow = (ring + glyph * 0.6) * Strength;
    vec3 col = base.rgb + vec3(0.35, 1.0, 0.8) * glow * 1.1;
    col = mix(col, vec3(0.9, 1.0, 0.95), clamp(glow * 0.25, 0.0, 0.35));
    fragColor = vec4(col, 1.0);

}
