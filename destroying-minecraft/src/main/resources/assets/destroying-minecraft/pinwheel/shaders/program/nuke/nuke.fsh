// Original: SpacePotatoee/Destroying-Minecraft — All Rights Reserved, ported to NeoForge for comparison (see THIRD_PARTY.md)
#include veil:space_helper
#include veil:blend
#include destroying-minecraft:ray_march
#include destroying-minecraft:noise
#veil:buffer veil:camera VeilCamera

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;
uniform sampler2D HandDepthSampler;

uniform vec3 NUKE_POS;
uniform float GameTime;
uniform float flashTimer;
uniform float smokeRiseTimer;

in vec2 texCoord;
out vec4 fragColor;

struct Ray {
    vec3 rayPos;
    vec3 rayOrigin;
    vec3 rayDir;
    bool hit;
};

//const vec3 NUKE_POS = vec3(-1654.06, 66.51, 1561.33);
const float ZOOM = 0.1;
const int ITERATIONS = 5;
const float ABSORPTION = 0.5;
const float SEETH = 500;

const vec3 fireColor = vec3(0.807843137254902, 0.44313725490196076, 0.09019607843137255);
float glow = (4 + 20*(1.0 - smokeRiseTimer));

float contrast(float color, float cont){
    return cont * (color - 0.5) + 0.5;
}

float map(vec3 p){
    vec3 nukePos = p - NUKE_POS;

    float cylinder = sdCylinder(nukePos - vec3(0, 40*smokeRiseTimer, 0), 40*smokeRiseTimer, 11);
    float torus = sdTorus(nukePos - vec3(0, 70*smokeRiseTimer,0), vec2(30*smokeRiseTimer, 20));
    float cone = sdCappedCone(nukePos - vec3(0, 3, 0), 3, 80*smokeRiseTimer, 1);

    float distortion = (fbm(nukePos + (GameTime*1000), 7) - fbm(nukePos*0.1 + (GameTime*100), 2)*3)*3;



    return opSmoothUnion(torus, opSmoothUnion(cylinder, cone, 20), 0) - distortion;
}

float lqMap(vec3 p) {
    vec3 nukePos = p - NUKE_POS;
    float cylinder = sdCylinder(nukePos - vec3(0, 40*smokeRiseTimer, 0), 40*smokeRiseTimer, 11);
    float torus = sdTorus(nukePos - vec3(0, 70*smokeRiseTimer,0), vec2(30*smokeRiseTimer, 20));
    float cone = sdCappedCone(nukePos - vec3(0, 3, 0), 3, 80*smokeRiseTimer, 1);

    return opSmoothUnion(torus, opSmoothUnion(cylinder, cone, 20), 0);
}

vec3 rayMarchCloud(inout vec3 color, in vec3 viewPos, in Ray ray) {
    vec3 step = ray.rayDir * 0.05;

    ray.rayOrigin = ray.rayPos;
    float accumulation = 0.0;
    for (int i = 0; i < 20; i++) {
        ray.rayPos += step;

        float noise = fbm4d(vec4((ray.rayPos + vec3(0,40,0)*sin(GameTime*0))*ZOOM, GameTime * SEETH), ITERATIONS);

        float d = map(ray.rayPos);
        accumulation += noise * 0.05 + max(-d*0.1, 0.0);

        if(d > 1.2 || accumulation >= 1.0 || length(ray.rayOrigin - ray.rayPos) > 10) {
            break;
        }
    }
    float dist = length(ray.rayOrigin - ray.rayPos);

    float fireNoise = fbm4d(vec4((ray.rayPos + vec3(0,40,0)*1)*0.1, GameTime * 100), ITERATIONS);
    fireNoise = contrast(fireNoise, -15);
    vec3 fire = fireColor * max((1.0 - exp(-dist * 0.5)) * fireNoise, 0.0) * glow;
    return vec3(1.0 - exp(-dist * 0.2)) + fire*1.2;
}

void rayMarchNuke(inout vec3 color, in vec3 viewPos, inout vec3 normal, inout Ray ray) {
    ray.rayOrigin = VeilCamera.CameraPosition + VeilCamera.CameraBobOffset;
    ray.rayDir = viewDirFromUv(texCoord);

    float dist = 0.0;
    for(int i = 0; i < 50; i++) {
        ray.rayPos = ray.rayOrigin + ray.rayDir * dist;

        float d = lqMap(ray.rayPos);
        if (d < 1) {
            d = map(ray.rayPos);
        }
        dist += d;

        if(d <= 0.1) {
            ray.hit = true;
            break;
        }

        else if(dist > length(viewPos) || dist > 300){
            break;
        }
    }

    if(ray.hit) {
        color = rayMarchCloud(color, viewPos, ray);
    }

}

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    float handDepth = texture(HandDepthSampler, texCoord).r;

    if (handDepth < 1.0) {
        fragColor = vec4(color, 1.0);
        return;
    }

    vec3 viewPos = screenToViewSpace(texCoord, depth).xyz;
    vec3 localPos = screenToLocalSpace(texCoord, depth).xyz;
    vec3 normal = vec3(0.0);

    Ray ray;
    ray.hit = false;
    if (smokeRiseTimer > 0.0) {
        rayMarchNuke(color, viewPos, normal, ray);
    }

    if(ray.hit) {
        fragColor = vec4(color, 1.0);
    } else {
        fragColor = texture(DiffuseSampler, texCoord);
    }
    float flash = flashTimer > 0 ? 0.6 * (1.0-min(flashTimer, 1.0)) : 0.0;
    fragColor += flash;
}