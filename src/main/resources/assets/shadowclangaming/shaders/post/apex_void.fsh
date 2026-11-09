#version 150

// void grade: desaturate, darken, heavy vignette
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
    float luma = dot(base.rgb, vec3(0.299, 0.587, 0.114));
    vec3 col = mix(base.rgb, vec3(luma * 0.55, luma * 0.45, luma * 0.75), Strength * 0.8);
    float r = length(uv - vec2(0.5));
    col *= mix(1.0, 1.0 - smoothstep(0.25, 0.72, r) * 0.55, Strength);
    fragColor = vec4(col, base.a);

}
