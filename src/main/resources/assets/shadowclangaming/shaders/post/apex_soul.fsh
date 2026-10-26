#version 150

// soul furnace: green spectral haze drawn toward the centre
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
    vec2 dir = normalize(d + 1e-6);
    float swirl = sin(atan(d.y, d.x) * 4.0 + Time * 2.5) * 0.5 + 0.5;
    vec2 offset = -dir * Strength * 0.02 * (1.0 - r) * swirl;
    vec4 pulled = texture(DiffuseSampler, uv + offset);
    vec3 col = mix(pulled.rgb, vec3(0.4, 1.0, 0.55) * dot(pulled.rgb, vec3(0.33)), Strength * 0.45);
    col += vec3(0.35, 1.0, 0.5) * (1.0 - smoothstep(0.0, 0.35, r)) * Strength * 0.5;
    fragColor = vec4(col, base.a);

}
