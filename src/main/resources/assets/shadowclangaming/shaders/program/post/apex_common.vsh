#version 150

// Shared vertex shader for every ShadowClanGaming post effect: it draws one full-screen triangle pair
// with the standard post texture coordinates and nothing else. All of the work is in the fragment stage.
in vec4 Position;
in vec2 UV0;

uniform mat4 ProjMat;

out vec2 texCoord;

void main() {
    gl_Position = ProjMat * Position;
    texCoord = UV0;
}
