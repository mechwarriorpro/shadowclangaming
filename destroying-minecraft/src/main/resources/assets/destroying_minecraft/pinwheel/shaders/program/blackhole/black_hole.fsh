// Original: SpacePotatoee/Destroying-Minecraft — All Rights Reserved, ported to NeoForge for comparison (see THIRD_PARTY.md)
#veil:buffer veil:camera VeilCamera
#include destroying_minecraft:ray_march
#include destroying_minecraft:noise
#include veil:space_helper
#include veil:blend

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;
uniform sampler2D HandDepth;
uniform sampler2D DepthComponent;
uniform sampler2D RandNoise;
uniform sampler2D PrevSampler;
uniform sampler2D PrevDepth;
uniform sampler2D StarsTexture;

uniform vec4 ColorModulator;
uniform vec3 redMultiplier;
uniform float GameTime;

uniform mat4 prevProjMat;
uniform mat4 prevViewMat;
uniform vec3 prevCameraPos;

in vec2 texCoord;
out vec4 fragColor;

struct Ray {
    vec3 pos;
    vec3 dir;
    bool hit;
};

const float BH_SIZE = 0.15;
const float DISK_RADIUS = BH_SIZE + 3.5;
const int ITERATIONS = 70;
const int DISK_ITERATIONS = 5;
vec3 BH_POS = VeilCamera.CameraPosition + vec3(0.0, 1.0, -2.9);
//vec3 BH_POS = vec3(-1194, 74, 1235);

vec3 InDiskColor = vec3(1) * 0.5 * redMultiplier;
const vec3 OutDiskColor = vec3(0.1607843137254902, 0.11764705882352941, 0.09411764705882353) * 5;


float mapDisk(vec3 rayPos) {
    vec3 rotatedRayPos = rayPos - BH_POS;
    //BH Rotation
    rotatedRayPos.xy *= rot2D(-9);
    rotatedRayPos.yz *= rot2D(-21);

    float centerHole = sdCylinder(rotatedRayPos, 0.035, BH_SIZE + 0.45);
    float disk = sdCylinder(rotatedRayPos, 0.03, DISK_RADIUS);

    return opSubtraction(centerHole, disk);
}

void warpSpace(inout Ray ray, in float stepDist) {
    vec3 dirToCenter = normalize(BH_POS - ray.pos);
    float dstToCenter = distance(BH_POS, ray.pos);

    float force = 1 / (pow(dstToCenter, 2.0));
    ray.dir = normalize(mix(ray.dir, dirToCenter, force * BH_SIZE * 30 / float(ITERATIONS)));
}

//Attenuation formula https://gamedev.stackexchange.com/questions/56897/glsl-light-attenuation-color-and-intensity-formula
float attenuation(float value, float a, float b) {
    return 1 / (1 + a*abs(value) + b*abs(value)*abs(value));
}

void raymarchAccretionDisk(inout Ray ray, float diskDist, inout vec4 color) {

    vec3 step = ray.dir * 0.01;
    for (int i = 0; i < DISK_ITERATIONS; i++) {
        ray.pos += step;
        float radius = distance(ray.pos, BH_POS);
        vec3 diskPos = ray.pos - BH_POS;
        float angle = atan2(diskPos.x, diskPos.z) + GameTime * 500;
        float attenuate = attenuation(radius / 1.5, 0.1, 1) * (1.0 - diskDist);

        float noise = fbm(vec3(radius * 15,diskPos.y * attenuate * attenuate * 10, angle * 5), 6);
        float cloud = clamp(noise, 0.0, 1.0);
        color.rgb += vec3(cloud * attenuate) * mix(OutDiskColor, InDiskColor, attenuate * attenuate);
        color.rgb *= attenuate * attenuate;
        color.a += attenuate * 0.8;

        if (color.a >= 1.0) {
            break;
        }
    }


}

vec3 getSphereTexture(in vec3 rayPos, in vec3 normal, sampler2D textureSampler) {
    return (texture(textureSampler, rayPos.xz).rgb * normal.y) +
    (texture(textureSampler, rayPos.xy).rgb * normal.z) +
    (texture(textureSampler, rayPos.yz).rgb * normal.x);
}

void main() {
    vec3 cameraPos = VeilCamera.CameraPosition;

    float depth = texture(DiffuseDepthSampler, texCoord).r;
    float handDepth = texture(HandDepth, texCoord).r;

    if(depth >= 1.0 && handDepth >= 1.0) {
        vec3 worldSpacePos = screenToWorldSpace(texCoord, depth).xyz;


        vec3 playerSpacePos = worldSpacePos - prevCameraPos;
        vec3 prevViewPos = (prevViewMat * vec4(playerSpacePos, 1.0)).xyz;
        vec4 homogenousPos = prevProjMat * vec4(prevViewPos, 1.0);
        vec3 prevNDCPos = homogenousPos.xyz / homogenousPos.w;
        vec2 prevTexcoord = (prevNDCPos * 0.5 + 0.5).xy;
        float prevDepth = texture(PrevDepth, prevTexcoord).r;

        float farPlane = 7.0;
        vec3 ro = (VeilCamera.CameraPosition) + rand(texCoord + GameTime) * 0.02;
        vec3 rayDir = viewDirFromUv(texCoord);
        Ray ray = Ray(ro, rayDir, false);

        float stepDist = farPlane / float(ITERATIONS);


        vec4 BHcolor = vec4(0.0);
        vec4 color = vec4(0.0, 0.0, 0.0, 1.0);

        for(int i = 0; i <= ITERATIONS; i++) {
            ray.pos += ray.dir * stepDist;


            float diskDist = mapDisk(ray.pos);

            //Warp Space
            warpSpace(ray, stepDist);


            //Hit Accretion Disk
            if (diskDist <= 0.0001) {
                ray.hit = true;
                raymarchAccretionDisk(ray, clamp(diskDist * 1000.0, 0.0, 1.0), BHcolor);
                if (BHcolor.a >= 1.0) {
                    break;
                }
            }
            if (distance(ray.pos, BH_POS) < BH_SIZE) {
                ray.hit = true;
                break;
            }

        }

        if (ray.hit) {
            BHcolor.rgb *= 30;
            BHcolor.rgb = clamp(BHcolor.rgb, vec3(0.0), vec3(400.0));
            color = vec4(blend(color, BHcolor), 1.0);
        } else {
            vec3 normal = pow(abs(viewDirFromUv(texCoord).rgb), vec3(2));
            color.rgb = getSphereTexture(viewDirFromUv(texCoord), normal, StarsTexture)*2;

        }

        fragColor = color;



        if(prevTexcoord.x >= 0.0 && prevTexcoord.x <= 1.0 && prevTexcoord.y >= 0.0 && prevTexcoord.y <= 1.0) {
            if(prevDepth >= 1.0) {
                fragColor = mix(fragColor, texture(PrevSampler, prevTexcoord), 0.7);
            }
        }




    } else {
        fragColor = texture(DiffuseSampler, texCoord);
    }

    gl_FragDepth = depth;
}