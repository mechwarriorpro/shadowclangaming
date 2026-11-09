#version 150
// Vertex colour straight out, additive (the blend function is set by the renderer). Kept free of noise,
// samplers and branches on purpose: a fragment shader that will not compile is a warhead with no effect,
// and the previous build's shader-heavy approach is exactly what this replaces.
in vec4 vertexColor;


out vec4 fragColor;

void main() {
    // See world_blast_volume.fsh: apply() overwrites ColorModulator with RenderSystem's shader colour,
    // so a mod shader must not fold it into its own output.
    vec4 color = vertexColor;
    if (color.a < 0.004) {
        discard;
    }
    fragColor = color;
}
