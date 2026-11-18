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
        // Park the skybox just INSIDE the far plane. With the depth test still on and no depth write, it
        // then draws only where the depth buffer is still clear - so terrain, buildings and hills occlude
        // it exactly as they should. Without this a box that size would be a wall of nebula in your face.
        //
        // 0.9999 and not 1.0: sitting exactly ON the far plane only passes a GL_LEQUAL depth test, and
        // nothing here sets the depth function - it is whatever the level renderer last left behind. Under
        // GL_LESS every single sky fragment fails, which is a skybox that silently never draws at all
        // rather than one that draws badly. A hair inside the plane passes either test, and is still
        // behind every real block in the world by a margin of four orders of magnitude.
        gl_Position.z = gl_Position.w * 0.9999;
    }
}
