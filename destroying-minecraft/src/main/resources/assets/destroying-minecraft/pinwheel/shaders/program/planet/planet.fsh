// Original: SpacePotatoee/Destroying-Minecraft — All Rights Reserved, ported to NeoForge for comparison (see THIRD_PARTY.md)
#include veil:space_helper
#include veil:blend
#include destroying-minecraft:ray_march
#include destroying-minecraft:noise
#veil:buffer veil:camera VeilCamera

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;
uniform sampler2D OpaqueDepth;
uniform sampler2D HandDepthSampler;
uniform isampler2D MaterialSampler;
uniform sampler2D PlanetColor;
uniform sampler2D PebbleDepth;
uniform sampler2D NormalSampler;

uniform float GameTime;
uniform float planetFallTimer;
uniform float flashTimer;

in vec2 texCoord;
out vec4 fragColor;

const float TEXTURE_ZOOM = 0.001;
const float NOISE_ZOOM = 0.0015;
const vec3 LIGHT_DIR = normalize(vec3(-0.5,-0.2,1));
const vec3 CONE_OFFSET = vec3(0, -19000, 0);
const vec3 CRACK_DIR = normalize(vec3(-1, -1.1, -0.03));

//vec3 PLANET_POS = vec3(VeilCamera.CameraPosition.x+20000, 20000 - (sin(GameTime * 1000)*0.5+0.5)*1000, VeilCamera.CameraPosition.z);
vec3 PLANET_POS = vec3(VeilCamera.CameraPosition.x+20000, 20000 - 4000*planetFallTimer, VeilCamera.CameraPosition.z);

struct Ray {
    vec3 rayPos;
    vec3 origin;
    vec3 direction;
    vec3 normal;
    bool hit;
};

vec3 getSphereTexture(in vec3 rayPos, in vec3 normal, sampler2D textureSampler) {
    return (texture(textureSampler, rayPos.xz * TEXTURE_ZOOM).rgb * normal.y) +
    (texture(textureSampler, rayPos.xy * TEXTURE_ZOOM).rgb * normal.z) +
    (texture(textureSampler, rayPos.yz * TEXTURE_ZOOM).rgb * normal.x);
}

float mapAsteroidField(vec3 rayPos) {
    float noise = fbm(rayPos*0.005, 4)*200;
    float debrisFieldArea = sdSphere((rayPos - PLANET_POS) - CRACK_DIR*6000, 14000) - noise*10;
    float asteroids = 99999999;

    for (int i = 0; i < 3; i++) {
        float repetition = 3200 - i*1050;

        vec3 id = floor(rayPos / repetition);
        vec3 offsetPosition = hash44(vec4(id*1.523, 1.0)).rgb;

        rayPos += offsetPosition * 800;
        vec3 asteroidPos = mod(rayPos, repetition) - repetition / 2;



        asteroids = min(sdSphere(asteroidPos, offsetPosition.r * 1000/(3*i+1)) - noise, asteroids);
    }

    return opSmoothIntersection(debrisFieldArea, asteroids, 200);
}

vec2 map(vec3 rayPos) {
    vec3 p = rayPos - PLANET_POS;

    vec3 sphereNormal = normalize(vec3(rayPos - PLANET_POS));
    float craters = getSphereTexture(p*0.3, sphereNormal, PebbleDepth).r * 50;

    float planet = sdSphere(p, 19000) - craters;
    float asteroid = mapAsteroidField(rayPos);

    return vec2(min(planet, asteroid), planet < asteroid ? 1.0 : 0.0);
//    return vec2(planet, 1.0);
}

vec3 getRaymarchNormal(in vec3 point) {
    vec2 e = vec2(0.1, 0.0);
    float d = map(point).x;
    return normalize(vec3(map(point + e.xyy).x, map(point + e.yxy).x, map(point + e.yyx).x) - d);
}

float getNoise(vec3 pos) {
    float noise = (fbm(pos * NOISE_ZOOM, 5)) * 2.0 - 1.0;
    float noise2 = -noise;

    return min(max(noise, noise2), 1);
}

float lowQualityMap(vec3 rayPos) {
    vec3 p = rayPos - PLANET_POS;

    float planet = sdSphere(p, 19000);
    float asteroid = sdSphere((rayPos - PLANET_POS) - CRACK_DIR*6000, 15200);

    return min(planet, asteroid);
}

void displacePlanet(inout vec3 outColor, int iterations, inout Ray ray) {
    vec3 step = ray.direction * 20;
    float depth = 0.0;
    bool inHole = false;
    vec3 surfacePoint;

    for (int i = 0; i < iterations; i++) {
        ray.rayPos += step;

        vec3 dirToCenter = ray.rayPos - PLANET_POS;
        float distToCenter = length(dirToCenter);
        dirToCenter = normalize(dirToCenter);
        surfacePoint = (ray.rayPos + dirToCenter * (19000 - distToCenter)) - PLANET_POS;

        vec3 sphereNormal = normalize(surfacePoint - PLANET_POS);

        float pointOfCracking = smoothstep(0.99, 1.0, dot(sphereNormal, CRACK_DIR)*0.5+0.5);

        float noise = clamp((0.15 - getNoise(surfacePoint)) * pointOfCracking*20, 0.0, 1.0);

        depth = (19000 - distToCenter) / 200;

        if (noise < depth) {
            break;
        }

        //If it wasn't in the hole, it would break immediately
        inHole = true;
    }
    vec3 planetWallColor = getSphereTexture(surfacePoint*0.1, abs(ray.normal), PlanetColor);
    vec3 crackWallColor = (vec3(depth)) * dot(normalize(ray.rayPos - PLANET_POS), LIGHT_DIR);
    vec3 laveColor = vec3(1, 0.5, 0.2) * (5 + 7*flashTimer);
    outColor = inHole ? crackWallColor * laveColor : outColor;
}

void rayMarchShadows(inout vec3 outColor, int iterations, inout Ray ray) {
    ray.origin = ray.rayPos + ray.normal *20;
    ray.direction = LIGHT_DIR;
    float dist = 0;

    for (int i = 0; i < iterations; i++) {
        ray.rayPos = ray.origin + ray.direction * dist;

        float d = map(ray.rayPos).x;

        dist += d;

        //Hit Something. In shadow
        if (d < 10) {
            outColor *= 0.0;
            break;
        } else if (dist > 10000) {
            break;
        }
    }

}

void rayMarch(inout vec3 outColor, int iterations, inout Ray ray) {
    float dist = 0.0;

    for (int i = 0; i < iterations; i++) {
        ray.rayPos = ray.origin + ray.direction * dist;

        vec2 d = vec2(lowQualityMap(ray.rayPos), 0.0);
        if (d.x < 100) {
            d = map(ray.rayPos);
        }

        dist += d.x;

        //Hit Something
        if (d.x < 10) {
            ray.normal = getRaymarchNormal(ray.rayPos);
            ray.hit = true;
            float light = clamp(dot(ray.normal, LIGHT_DIR), 0.0, 1.0);

            //Hit planet
            if (d.y == 1.0) {
                outColor = getSphereTexture(ray.rayPos - PLANET_POS, abs(ray.normal), PlanetColor) * light*2;
                displacePlanet(outColor, 40, ray);
            }
            //Hit Asteroid
            else {
                outColor = vec3(0.4) * light*2;
                if (light > 0.01) {
                    rayMarchShadows(outColor, 20, ray);
                }
            }

            break;
        }
        //Miss
        else if (dist > 30000) {
            ray.hit = false;
            break;
        }
    }
}

vec3 viewToWorldSpaceDir(vec3 direction) {
    return (VeilCamera.IViewMat * vec4(direction, 0.0)).xyz;
}


void main() {
    vec4 mainTexture = texture(DiffuseSampler, texCoord);
    float depth = texture(OpaqueDepth, texCoord).r;
    uint material = texture(MaterialSampler, texCoord).r;

    vec3 playerSpace = screenToLocalSpace(texCoord, depth).xyz;
    float worldDepth = length(playerSpace);



    if (depth >= 1.0) {
        Ray ray;
        ray.origin = VeilCamera.CameraPosition + VeilCamera.CameraBobOffset;
        ray.direction = viewDirFromUv(texCoord);

        rayMarch(fragColor.rgb, 70, ray);

        fragColor.rgb = blendGlint(mainTexture, fragColor);
    } else {
        fragColor = mainTexture;
    }

    float handDepth = texture(HandDepthSampler, texCoord).r;

    if (handDepth >= 1.0) {
        if (material == 5) {
            fragColor *= 10;
        } else if (material == 6) {
            fragColor = vec4(1.0, 0.0, 0.0, 1.0);
        }
    }


}