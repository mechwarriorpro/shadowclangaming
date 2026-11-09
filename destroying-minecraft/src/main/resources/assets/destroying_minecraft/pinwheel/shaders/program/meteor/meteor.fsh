// Original: SpacePotatoee/Destroying-Minecraft — All Rights Reserved, ported to NeoForge for comparison (see THIRD_PARTY.md)
#veil:buffer veil:camera VeilCamera
#include destroying_minecraft:ray_march
#include destroying_minecraft:noise
#include veil:space_helper

uniform sampler2D PebbleDepth;

uniform float GameTime;
uniform vec2 ScreenSize;
uniform float random;

in vec4 entityPos;
in vec3 worldPos;
in float opacity;

out vec4 fragColor;
layout (location = 1) out vec4 Albedo;
layout (location = 2) out vec4 OutNormal;
layout (location = 3) out vec2 LightUV;
layout (location = 4) out vec4 LightColor;
layout (location = 6) out ivec4 Material;

const int MAX_STEPS = 100;
const float MAX_DIST = 20.0;
const vec3 lightDir = vec3(0.0, -1.0, -1.0);
const float ZOOM = 0.1;

vec3 getPlanetNormal(vec3 rayPos) {
    return normalize(rayPos - entityPos.xyz);
}

vec3 getSphereTexture(in vec3 rayPos, in vec3 normal, sampler2D textureSampler) {
    return (texture(textureSampler, rayPos.xz).rgb * normal.y) +
    (texture(textureSampler, rayPos.xy).rgb * normal.z) +
    (texture(textureSampler, rayPos.yz).rgb * normal.x);
}

vec2 map(vec3 p) {
    vec3 pos =  p - entityPos.xyz;
    vec3 meteorPos = pos;
    meteorPos.xy *= rot2D(rand(entityPos.w*74)*360 + GameTime*100000);
    meteorPos.xz *= rot2D(rand(entityPos.w*12)*360 + GameTime*300000);
    vec3 sphereNormal = getPlanetNormal(meteorPos);
    float displacement1 = getSphereTexture(meteorPos*0.1, sphereNormal, PebbleDepth).r;
    float totalDisplacement = (displacement1 * 0.4);

    float meteor = sdEllipsoid(meteorPos, vec3(2.5 * rand(entityPos.w*4)+0.5, 2.0 * rand(entityPos.w*2)+0.5, 2.0 * rand(entityPos.w*6)+0.5)*opacity) - totalDisplacement;

    vec3 trailPos = pos - vec3(5.25, 5.25, 0);
    trailPos.xy *= rot2D(45.0);
    float trailMaxWidth = 0.6 * rand(entityPos.w*4)+0.2;
    float trailDisplacement = fbm((trailPos*3) - vec3(vec2(GameTime*19000), 0), 3) * 0.9;

    float trail = sdCappedCone(trailPos - vec3(0, 1, 0), 8 * opacity, trailMaxWidth, 0) - trailDisplacement * opacity;

    float shape = min(meteor, trail);
    float type = 0.0;
    if(shape == trail) {
        type = 1;
    }

    return vec2(shape, type);
}

vec3 getNormal(vec3 p) {
    vec2 offset = vec2(0.001, 0.0);
    float d = map(p).x;
    return normalize(d - vec3(
        map(p - offset.xyy).x,
        map(p - offset.yxy).x,
        map(p - offset.yyx).x
    ));
}

// Ray marching function
void rayMarch(inout vec3 color, out vec3 normal, vec2 screenUv, inout bool hit) {
    vec3 ro = worldPos;
    vec3 rayDir = viewDirFromUv(screenUv).xyz;
    float dist = 0.0;

    for (int i = 0; i < MAX_STEPS; i++) {
        vec3 rayPos = ro + rayDir * dist;

        vec2 d = map(rayPos);
        dist += d.x;

        if (d.x < 0.001) {
            hit = true;
            normal = getNormal(rayPos);
            color = d.y == 0.0 ? vec3(1, 0.6, 0.2) * max(dot(normal, normalize(lightDir)), 0.2) : vec3(0.05 * (3.5 - pow(distance(rayPos, entityPos.xyz), 0.45)));
            break;
        } else if (dist > MAX_DIST){
            break;
        }
    }
}

vec3 worldToViewSpaceDir(vec3 pos) {
    vec4 viewSpacePos = VeilCamera.ViewMat * vec4(pos, 0.0);
    return viewSpacePos.xyz;
}

void main() {
    vec2 screenUv = gl_FragCoord.xy / ScreenSize;


    vec3 color = vec3(0.0);
    vec3 normal = vec3(0.0);
    bool hit = false;

    rayMarch(color, normal, screenUv, hit);

    if (!hit) {
        discard;
    }

    fragColor = vec4(color, 1.0);
    Albedo = vec4(color, 1.0);
    OutNormal = vec4(normalize(worldToViewSpaceDir(normal)), 1.0);
    LightUV = vec2(0.0, 1.0);
    LightColor = vec4(0.0);
    Material = ivec4(5, 0, 0, 1);

//    gl_FragDepth = 0.0;
}