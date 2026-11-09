// Original: SpacePotatoee/Destroying-Minecraft — All Rights Reserved, ported to NeoForge for comparison (see THIRD_PARTY.md)
#include veil:space_helper
#include veil:blend
#include destroying-minecraft:ray_march
#include destroying-minecraft:noise
#veil:buffer veil:camera VeilCamera

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;
uniform isampler2D MaterialSampler;
uniform sampler2D HandDepthSampler;

uniform sampler2D TranslucentAlbedoSampler;

uniform sampler2D DirtTexture;

uniform vec2 CENTER_POS;
uniform float GameTime;
uniform float laserLength;
uniform float cracksTime;
uniform float flashTimer;

in vec2 texCoord;
out vec4 fragColor;

const float TEXTURE_SIZE = 0.5;

float getNoise(vec3 pos, float HOLE_SIZE) {
    float noise = (fbm(pos * TEXTURE_SIZE, 7)) * 2.0 - 1.0;
    float noise2 = -noise;

    return min(max(noise, noise2) + fbm(pos*0.3, 1), 1);
}

float map(vec3 p) {
    vec3 rayPos = p;
    float length = laserLength * 520;

    if (length <= 0.0) return 5000.0;
    float d = sdCylinder(rayPos - vec3(CENTER_POS.x, 500, CENTER_POS.y), length, 0.5 * (1.0 + flashTimer*10.0));
    d -= (sin(p.y*1 + rand(vec2(GameTime*1000, 745))*100)*0.5 + 0.5)*0.1;
    return d;
}

void rayMarchLaser(inout vec3 color, vec3 playerPos, bool cracks, float crackDepth) {
    vec3 rayOrigin = VeilCamera.CameraPosition + VeilCamera.CameraBobOffset;
    vec3 rayDir = viewDirFromUv(texCoord);
    float dist = 0.0;

    for(int i = 0; i < 200; i++) {
        vec3 rayPos = rayOrigin + rayDir * dist;
        float d = map(rayPos);
        dist += d;

        if(d < 0.01) {
            float whiteValue = 2.0 + (1.0 + flashTimer*2.0);
            color = vec3(10.0, whiteValue, whiteValue);
            break;
        } else if((dist > length(playerPos) && dist > length(playerPos) + crackDepth) || dist > 500.0) {
            break;
        }

    }

}

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    vec4 translucentColor = texture(TranslucentAlbedoSampler, texCoord);
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    float handDepth = texture(HandDepthSampler, texCoord).r;
    uint material = texture(MaterialSampler, texCoord).r;
    vec3 worldPos = screenToWorldSpace(texCoord, depth).xyz;
    vec3 viewPos = screenToViewSpace(texCoord, 1.0 - depth).xyz;
    float crackDepth = 0.0;
    bool cracks = false;


    if (depth < 1.0 && material == 2 && handDepth >= 1.0) {

        worldPos.y = 4;
        float time = 0.0;
        if (cracksTime > 0.0) {
            time = (cracksTime*50) + 5.0;
        }
        float holeSize = pow(1 - distance(CENTER_POS, worldPos.xz)/time, 1);
        float noise = getNoise(worldPos, holeSize);




        if (noise < holeSize) {
            cracks = true;
            vec3 rayPos = worldPos + rand(texCoord + GameTime) * 0.01;
            vec3 rayDir = viewDirFromUv(texCoord);
            vec3 step = rayDir * length(viewPos)*2;


            vec3 magmaColor = vec3(0.0);
            for(int i = 0; i < 50; i++) {
                rayPos += step;
                holeSize = pow(1 - distance(CENTER_POS, rayPos.xz)/time, 1);
                noise = getNoise(vec3(rayPos.x, worldPos.y, rayPos.z), holeSize);


                magmaColor += vec3(1, 0.4, 0.08)*0.05;

                if(noise > holeSize) {
                    break;
                }

            }

            crackDepth += distance(rayPos, worldPos);

            color = magmaColor*2;
        }
    }

    vec3 playerPos = screenToLocalSpace(texCoord, depth).xyz;

    rayMarchLaser(color, playerPos, cracks, crackDepth);


    fragColor = vec4(color + flashTimer, 1.0);
}