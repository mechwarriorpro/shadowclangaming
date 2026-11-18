// Original: SpacePotatoee/Destroying-Minecraft — All Rights Reserved, ported to NeoForge for comparison (see THIRD_PARTY.md)
#veil:buffer veil:camera VeilCamera

layout (location = 0) in vec3 Position;
layout (location = 1) in vec4 Color;
layout (location = 2) in vec4 Entity_Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 entityPos;
out vec3 worldPos;
out float opacity;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    entityPos = Entity_Position;
    worldPos = Position + VeilCamera.CameraPosition;
    opacity = Color.a;
}