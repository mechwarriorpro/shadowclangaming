#version 150
in vec2 texCoord;
in vec4 vertexColor;
uniform float GameTime;
uniform float EffectType;
uniform float Progress;
uniform float Intensity;
out vec4 fragColor;

float scgWorldBlastHash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
float scgWorldBlastNoise2(vec2 p) {
    vec2 i=floor(p), f=fract(p);
    f=f*f*(3.0-2.0*f);
    return mix(mix(scgWorldBlastHash21(i),scgWorldBlastHash21(i+vec2(1,0)),f.x),
               mix(scgWorldBlastHash21(i+vec2(0,1)),scgWorldBlastHash21(i+vec2(1,1)),f.x),f.y);
}
void main() {
    vec2 p=texCoord*2.0-1.0;
    float r=length(p);
    if(r>1.0) discard;
    float t=clamp(Progress,0.0,1.0);
    float type=floor(EffectType+0.5);
    float n=scgWorldBlastNoise2(p*5.0+vec2(GameTime*0.18,-GameTime*0.11));
    float n2=scgWorldBlastNoise2(p*11.0-GameTime*0.13);
    float body=exp(-r*r*3.2)*(1.0-t*0.78);
    float shellRadius=0.12+t*0.82;
    float shock=exp(-abs(r-shellRadius)*34.0)*(1.0-smoothstep(0.84,1.0,t));
    float turbulence=smoothstep(0.22,0.82,n*0.68+n2*0.32);
    float plume=body*(0.35+0.65*turbulence);
    float sparks=pow(max(0.0,n2-0.68),2.0)*2.7*(1.0-t);
    vec3 tint=max(vertexColor.rgb,vec3(0.03));
    vec3 hot=vec3(1.0,0.16,0.012);
    vec3 gold=vec3(1.0,0.66,0.08);
    if(type>1.5&&type<2.5) { hot=vec3(0.08,0.20,1.0); gold=vec3(0.4,0.85,1.0); }
    else if(type>2.5&&type<3.5) { hot=vec3(0.55,0.08,1.0); gold=vec3(0.08,0.9,1.0); }
    else if(type>3.5&&type<4.5) { hot=vec3(0.1,1.0,0.36); gold=vec3(0.8,1.0,0.1); }
    else if(type>4.5) { hot=vec3(0.25,0.38,1.0); gold=vec3(0.72,0.85,1.0); }
    vec3 c=mix(hot,gold,clamp(0.35+n*0.6,0.0,1.0));
    c=mix(c,tint,0.34);
    float alpha=clamp((plume*0.72+shock*0.9+sparks)*Intensity*vertexColor.a,0.0,0.94);
    if(alpha<0.012) discard;
    // Additive emissive color with a soft, noisy boundary: the quad silhouette never reaches the screen.
    vec3 emissive=c*(plume*1.55+shock*1.9+sparks*2.4)*Intensity;
    fragColor=vec4(emissive,alpha);
}
