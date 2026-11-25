#version 150
// Cinematic warhead geometry. Deliberately the simplest vertex shader that can exist: the shapes are
// built on the CPU in world space (see client/BlastGeometry), the colour arrives per vertex, and all this
// does is apply the two matrices client/WorldMatrices built. Nothing here can fail on an unusual driver.
in vec3 Position;
in vec4 Color;

// Our own matrices (see client/WorldMatrices): vertices arrive in world coordinates and the view matrix
// is vanilla's, so this places the geometry exactly once no matter what the pose stack is holding.
uniform mat4 ScgProj;
uniform mat4 ScgView;

out vec4 vertexColor;

void main() {
    gl_Position = ScgProj * ScgView * vec4(Position, 1.0);
    vertexColor = Color;
}
