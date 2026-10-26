#version 150
// Volumetric warhead effect, vertex half.
//
// The proxy volume is a cube in WORLD coordinates and this shader owns both matrices (ScgProj/ScgView,
// filled by client/VolumetricFx from the camera), rather than borrowing Minecraft's ModelViewMat. That
// is deliberate: whatever the level renderer has already pushed onto the pose stack when a stage event
// fires, this places the volume correctly exactly once, and the fragment shader needs the world position
// anyway to know where inside the blast it is sampling.
in vec3 Position;

uniform mat4 ScgProj;
uniform mat4 ScgView;

out vec3 vWorld;

void main() {
    vWorld = Position;
    gl_Position = ScgProj * ScgView * vec4(Position, 1.0);
}
