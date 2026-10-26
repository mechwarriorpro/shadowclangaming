#version 150
// Cinematic warhead geometry. Deliberately the simplest vertex shader that can exist: the shapes are
// built on the CPU in world space (see client/BlastGeometry), the colour arrives per vertex, and all this
// does is apply the two matrices Minecraft already set. Nothing here can fail on an unusual driver.
in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
}
