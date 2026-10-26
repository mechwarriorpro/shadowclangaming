#version 150

// black-hole lensing + accretion glow
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
    float pull = Strength * 0.55 * (1.0 - smoothstep(0.0, 0.62, r));
    vec2 warped = uv + normalize(d + 1e-6) * pull * r * 0.35;
    vec4 base = texture(DiffuseSampler, warped);
    float ring = (1.0 - smoothstep(0.18, 0.30, abs(r - 0.22 - 0.02 * sin(Time * 3.0))));
    float lens = (1.0 - smoothstep(0.10, 0.34, r));
    vec3 hot = mix(vec3(1.0, 0.55, 0.15), vec3(0.75, 0.35, 1.0), clamp(ring, 0.0, 1.0));
    vec3 col = base.rgb * (1.0 - lens * 0.85 * Strength);
    col += hot * ring * 1.4 * Strength;
    col += hot * 0.20 * lens * Strength;
    fragColor = vec4(col, base.a);

}
