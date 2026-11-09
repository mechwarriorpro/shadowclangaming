#version 150

// Pass-through: the last pass of every apex chain writes its result back into minecraft:main.
uniform sampler2D DiffuseSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    fragColor = texture(DiffuseSampler, texCoord);
}
