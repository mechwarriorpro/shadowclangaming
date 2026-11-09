#version 150

// stellar flash / white-out with corona
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
    float core = (1.0 - smoothstep(0.0, 0.55, r));
    float corona = (1.0 - smoothstep(0.25, 0.75, r));
    vec3 hot = mix(vec3(1.0, 1.0, 0.92), vec3(0.55, 0.75, 1.0), smoothstep(0.1, 0.6, r));
    vec3 col = base.rgb + hot * (core * 1.8 + corona * 0.45) * Strength;
    col = mix(col, vec3(1.0), clamp(Strength * core * 0.85, 0.0, 0.92));
    fragColor = vec4(col, base.a);

}
