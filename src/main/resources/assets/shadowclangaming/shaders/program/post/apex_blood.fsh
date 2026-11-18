#version 150

// blood sky: red grade, dark horizon, rune shimmer
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
    vec3 graded = mix(base.rgb, vec3(luma * 1.35, luma * 0.28, luma * 0.22), Strength * 0.65);
    float horizon = smoothstep(0.05, 0.55, uv.y);
    graded *= mix(1.0, 0.55, horizon * Strength);
    vec2 d = uv - vec2(0.5);
    float rune = (1.0 - smoothstep(0.0, 0.02, abs(sin((d.x * 40.0) + sin(d.y * 12.0 + Time)) - 0.0) - 0.97));
    graded += vec3(1.0, 0.25, 0.15) * rune * Strength * 0.6;
    fragColor = vec4(graded, base.a);

}
