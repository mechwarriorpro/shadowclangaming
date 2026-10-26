#version 150
// Vertex colour straight out, additive (the blend function is set by the renderer). Kept free of noise,
// samplers and branches on purpose: a fragment shader that will not compile is a warhead with no effect,
// and the previous build's shader-heavy approach is exactly what this replaces.
in vec4 vertexColor;

uniform vec4 ColorModulator;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator;
    if (color.a < 0.004) {
        discard;
    }
    fragColor = color;
}
