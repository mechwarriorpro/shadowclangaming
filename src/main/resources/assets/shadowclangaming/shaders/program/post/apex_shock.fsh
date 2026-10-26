#version 150

// passing shockwave rings: refraction bands
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

    vec2 d = uv - vec2(0.5);
    float r = length(d);
    float phase = fract(r * 6.0 - Time * 1.6);
    float band = (1.0 - smoothstep(0.0, 0.12, abs(phase - 0.5) - 0.30));
    vec2 offset = normalize(d + 1e-6) * band * Strength * 0.03;
    vec3 base = texture(DiffuseSampler, uv + offset).rgb;
    vec3 col = base + vec3(0.35, 0.55, 0.85) * band * Strength * 0.35;
    fragColor = vec4(col, 1.0);

}
