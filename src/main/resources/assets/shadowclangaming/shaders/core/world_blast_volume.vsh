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
// 1.0 while the skybox volume is being drawn, 0.0 for the blast itself. See the fragment shader.
uniform float SkyPass;

out vec3 vWorld;

void main() {
    vWorld = Position;
    gl_Position = ScgProj * ScgView * vec4(Position, 1.0);
    if (SkyPass > 0.5) {
        // Park the skybox on the far plane. With the depth test still on and no depth write, it then
        // draws only where the depth buffer is still clear - so terrain, buildings and hills occlude it
        // exactly as they should. Without this a box that size would be a wall of nebula in your face.
        gl_Position.z = gl_Position.w;
    }
}
