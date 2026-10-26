#version 150

// reality tear: chromatic split + jagged distortion
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
    float angle = atan(d.y, d.x);
    float jag = sin(angle * 9.0 + Time * 2.0) * 0.5 + sin(angle * 23.0 - Time) * 0.25;
    float amount = Strength * 0.02 * (1.0 + jag) * (1.0 - smoothstep(0.05, 0.7, r));
    vec2 offset = normalize(d + 1e-6) * amount;
    float split = Strength * 0.006 * (1.0 - smoothstep(0.0, 0.7, r));
    vec3 col;
    col.r = texture(DiffuseSampler, uv + offset + vec2(split, 0.0)).r;
    col.g = texture(DiffuseSampler, uv + offset).g;
    col.b = texture(DiffuseSampler, uv + offset - vec2(split, 0.0)).b;
    float vein = (1.0 - smoothstep(0.0, 0.03, abs(sin(angle * 5.0 + Time * 1.5)) - 0.85));
    col += vec3(0.55, 0.1, 0.85) * vein * Strength;
    float vignette = smoothstep(0.62, 0.95, r);
    col = mix(col, vec3(0.10, 0.04, 0.14), vignette * Strength * 0.28);
    fragColor = vec4(col, 1.0);

}
