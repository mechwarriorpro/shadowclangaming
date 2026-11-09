// Original: SpacePotatoee/Destroying-Minecraft — All Rights Reserved, ported to NeoForge for comparison (see THIRD_PARTY.md)
#include veil:space_helper
#include veil:fog
#include veil:blend
#include destroying-minecraft:ray_march
#include destroying-minecraft:noise
#veil:buffer veil:camera VeilCamera

#define SHADOW_SAMPLES 1

#define SHADOW_STRENGTH 0.4

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;
uniform sampler2D OpaqueDepth;
uniform sampler2D AlbedoSampler;
uniform sampler2D ShadowMap;
uniform sampler2D NormalSampler;
uniform sampler2D LightUVSampler;
uniform sampler2D VanillaLightMapTexture;
uniform sampler2D BloomSampler;
uniform sampler2D BloomStartSampler;
uniform isampler2D MaterialSampler;

uniform sampler2D TranslucentDepth;
uniform sampler2D TranslucentAlbedoSampler;
uniform sampler2D TranslucentNormalSampler;
uniform sampler2D TranslucentUVSampler;
uniform isampler2D TranslucentMaterialSampler;

uniform sampler2D HandDepth;
uniform sampler2D HandAlbedoSampler;
uniform sampler2D HandLightColor;

uniform sampler2D NoiseTex;

uniform vec2 ScreenSize;
uniform mat4 shadowViewMatrix;
uniform mat4 IShadowViewMatrix;
uniform mat4 shadowProjMat;
uniform float GameTime;
uniform float supernovaTimer;
uniform float flashTimer;
uniform float explosionTimer;
uniform float laserLength;
uniform int flashFrame;

uniform vec3 laserPos;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec2 texCoord;
out vec4 fragColor;

const vec3 SkyColor = vec3(0.6,0.9,1.0);
vec3 sunDir = normalize(mat3(IShadowViewMatrix) * vec3(0.0,0.0,1.0));

vec3 projectAndDivide(mat4 projMat, vec3 pos){
    vec4 homogeneousPos = projMat * vec4(pos, 1.0);
    return homogeneousPos.xyz / homogeneousPos.w;
}

vec3 distort(in vec3 shadowPosition) {
    const float bias0 = 0.95;
    const float bias1 = 1.0 - bias0;

    float factorDistance = length(shadowPosition.xy);

    float distortFactor = factorDistance * bias0 + bias1;

    return shadowPosition * vec3(vec2(1.0 / distortFactor), 0.2);
}

vec3 viewToWorldSpaceDir(vec3 direction) {
    return (VeilCamera.IViewMat * vec4(direction, 0.0)).xyz;
}

mat2 randRotMat(vec2 coord){
    float randomAngle = texture(NoiseTex, coord * 20.0).r * 180.0;
    float cosTheta = cos(randomAngle);
    float sinTheta = sin(randomAngle);
    return mat2(cosTheta, -sinTheta, sinTheta, cosTheta) / 2048.0;
}

vec3 overlay(vec3 color1, vec3 color2) {
    if (color2.r + color2.g + color2.b > 0.0) {
        color1 = color2;
    }

    return color1;
}

vec3 getShadow(vec3 albedoColor, float depth, vec3 worldNormal, bool translucent) {
    vec3 flash = flashTimer > 0 ? vec3(0.6) * (1.0 - min(flashTimer, 1.0)) : vec3(0.0);

    vec3 viewPos = screenToViewSpace(texCoord, depth).rgb;


    vec3 localSpace = screenToLocalSpace(texCoord, depth).xyz;
    vec3 adjustedLocalSpace = localSpace + 0.02 * worldNormal;
    vec3 shadowViewSpace = (shadowViewMatrix * vec4(adjustedLocalSpace, 1.0)).xyz;

    vec3 shadowNDCPos = distort(projectAndDivide(shadowProjMat, shadowViewSpace));

    vec3 shadowScreenPos = shadowNDCPos * 0.5 + 0.5;
    float shadowDepth = shadowScreenPos.z;

    vec2 lightUV = texture(LightUVSampler, texCoord).rg;
    if (translucent) {
        vec2 translucentLightUV = texture(TranslucentUVSampler, texCoord).rg;
        lightUV = overlay(vec3(lightUV, 0.0), vec3(translucentLightUV, 0.0)).rg;
    }

    vec3 blockLight = texture(VanillaLightMapTexture, vec2(lightUV.x, 1.0/32.0)).rgb;
    vec3 skyLight = texture(VanillaLightMapTexture, vec2(1.0/32.0, lightUV.y)).rgb;

    float lightDir = dot(worldNormal, sunDir);

    float shadowSum = SHADOW_STRENGTH;
    if (shadowScreenPos.x >= 0.0 && shadowScreenPos.x <= 1.0 && shadowScreenPos.y >= 0.0 && shadowScreenPos.y <= 1.0 ) {

        if (lightDir > -0.07) {
            mat2 randRotation = randRotMat(texCoord);
            for (int x = -SHADOW_SAMPLES; x <= SHADOW_SAMPLES; x++){
                for (int y = -SHADOW_SAMPLES; y <= SHADOW_SAMPLES; y++){
                    vec2 offset = randRotation * vec2(x, y) * 1;
                    float shadowSampler = texture(ShadowMap, shadowScreenPos.xy + offset).r;

                    if (shadowDepth < shadowSampler) {
                        shadowSum += 1.0;
                    }
                }
            }

            shadowSum /= pow(2.0 * SHADOW_SAMPLES + 1.0, 2.0);
        }

    } else {
        shadowSum = 1.0;
    }

    vec3 outputColor = albedoColor.rgb;
    if (flashTimer > 0.0) {
        outputColor = outputColor.rgb * (blockLight + skyLight * max(shadowSum, SHADOW_STRENGTH)) + flash*lightUV.y;
    }
    else {
        outputColor = outputColor.rgb * (blockLight + skyLight * max(shadowSum, SHADOW_STRENGTH)*(1.0 - supernovaTimer));
    }

    return outputColor;
}

float mapLaser(vec3 p) {
    vec3 rayPos = p;
    float length = laserLength * 20000;

    if (length <= 0.0) return 5000.0;
    rayPos -= laserPos;
    float angle = atan2(sunDir.x, sunDir.y);
    rayPos.xy *= rot2D(angle * 180/3.141592);
    rayPos.y -= length;
    float d = sdCylinder(rayPos, length, 0.5);
    d -= (sin(p.y*1 + rand(vec2(GameTime*1000, 745))*100)*0.5 + 0.5)*0.1;
    return d;
}

void rayMarchLaser(inout vec3 color, vec3 playerPos, out vec3 rayPos, out bool hit) {
    vec3 rayOrigin = VeilCamera.CameraPosition + VeilCamera.CameraBobOffset;
    vec3 rayDir = viewDirFromUv(texCoord);
    float dist = 0.0;

    for(int i = 0; i < 200; i++) {
        rayPos = rayOrigin + rayDir * dist;
        float d = mapLaser(rayPos);
        dist += d;

        if(d < 0.01) {
            color = vec3(10.0, 1.0, 1.0);
            hit = true;
            break;
        } else if (dist > length(playerPos) || dist > 700.0) {
            break;
        }

    }
}

float getBrightness(vec3 color) {
    return (color.r + color.g + color.b) / 3;
}

void main() {
    vec4 sky = texture(DiffuseSampler, texCoord);
    vec3 albedoColor = texture(AlbedoSampler, texCoord).rgb;
    vec4 translucentAlbedoColor = texture(TranslucentAlbedoSampler, texCoord);

    float depth = texture(OpaqueDepth, texCoord).r;
    float translucentDepth = texture(TranslucentDepth, texCoord).r;
    float handDepth = texture(HandDepth, texCoord).r;
    uint material = texture(MaterialSampler, texCoord).r;
    uint translucentMaterial = texture(TranslucentMaterialSampler, texCoord).r;

    vec3 outputColor = albedoColor.rgb;

    vec3 opaqueNormal = viewToWorldSpaceDir(texture(NormalSampler, texCoord).rgb);



    ///EARLY EXITS///
    //Hand
    if (handDepth < 1.0) {
        outputColor = texture(HandAlbedoSampler, texCoord).rgb * texture(HandLightColor, texCoord).rgb;
        gl_FragDepth = handDepth;
    }
    //BlackHole Terrain
    else if (material == 9) {
        outputColor = albedoColor * (dot(sunDir, opaqueNormal) * 0.5 + 0.7);
        gl_FragDepth = translucentDepth;
    }
    //Only sky
    else if (translucentDepth >= 1.0) {
        outputColor = sky.rgb;
        gl_FragDepth = translucentDepth;
    }
    else {
        float height = viewDirFromUv(texCoord).y;

        ///OPAQUE///
        vec3 opaqueColor = outputColor;
        opaqueColor = getShadow(opaqueColor, depth, opaqueNormal, false);
        //Opaque fog
        opaqueColor = linear_fog(vec4(opaqueColor, 1.0), length(screenToViewSpace(texCoord, depth).rgb), FogEnd - 30, FogEnd, vec4(vec3(SkyColor - height * 0.7), 1.0)).rgb;
        opaqueColor += texture(BloomSampler, texCoord).rgb;

        //Sky thats behind translucent objects
        if (depth >= 1.0) {
            opaqueColor = sky.rgb;
        }


        ///TRANSLUCENT///
        vec3 translucentColor = translucentAlbedoColor.rgb;

        //Not Clouds
        if (translucentMaterial != 12) {
            translucentColor = getShadow(translucentAlbedoColor.rgb, translucentDepth, viewToWorldSpaceDir(texture(TranslucentNormalSampler, texCoord).rgb), true);
        }
        outputColor = mix(opaqueColor, translucentColor, translucentAlbedoColor.a);
        //Translucent fog
        outputColor = linear_fog(vec4(outputColor, 1.0), length(screenToViewSpace(texCoord, translucentDepth).rgb), FogEnd - 30, FogEnd, vec4(vec3(SkyColor - height * 0.7), 1.0)).rgb;
    }

    //Supernova Laser
    if (laserLength > 0.0) {
        vec3 rayPos = vec3(0.0);
        bool hit = false;
        vec3 playerPos = screenToLocalSpace(texCoord, depth).rgb;
        rayMarchLaser(outputColor, playerPos, rayPos, hit);
        if (hit) {
            translucentDepth = worldToScreenSpace(vec4(rayPos + VeilCamera.CameraPosition, 1.0)).b;
            depth = translucentDepth;
        }
    }


    if (flashFrame == 1) {
        if (getBrightness(outputColor.rgb) > 0.3) {
            outputColor = vec3(0.0);
        } else {
            outputColor = vec3(1.0);
        }
    }

    fragColor = vec4(outputColor, 1.0);
    gl_FragDepth = translucentDepth;
}