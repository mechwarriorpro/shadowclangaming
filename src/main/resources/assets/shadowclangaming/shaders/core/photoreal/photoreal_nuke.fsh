#version 150
// Photorealistic nuke fireball — inspired by Destroying MC's veil/pinwheel nuke.fsh
// Reference: SDF composition of cylinder + torus + capped cone with fbm distortion,
// volumetric raymarch through proxy cube. Original maths by Herr_Chaos/SpacePotato,
// reimplemented here for NeoForge with SCG's Center/Radius/Progress uniforms.
// Licensed inspiration only — no verbatim Veil buffer code.

in vec3 vWorld;
uniform vec3 Center;
uniform vec3 CamPos;
uniform vec4 ColorA;
uniform vec4 ColorB;
uniform vec4 ColorC;
uniform float Radius;
uniform float Progress;
uniform float Time;
uniform float Steps;
uniform float Seed;
uniform float Strength;
uniform float Solid;

// ── hash / noise (IQ value noise, same family as Destroying MC) ─────────────
float hash31(vec3 p){
    p=fract(p*0.3183099+vec3(0.11,0.17,0.13));
    p*=17.0;
    return fract(p.x*p.y*p.z*(p.x+p.y+p.z));
}
float vnoise(vec3 x){
    vec3 i=floor(x); vec3 f=fract(x); f=f*f*(3.0-2.0*f);
    float n000=hash31(i+vec3(0,0,0)),n100=hash31(i+vec3(1,0,0));
    float n010=hash31(i+vec3(0,1,0)),n110=hash31(i+vec3(1,1,0));
    float n001=hash31(i+vec3(0,0,1)),n101=hash31(i+vec3(1,0,1));
    float n011=hash31(i+vec3(0,1,1)),n111=hash31(i+vec3(1,1,1));
    return mix(mix(mix(n000,n100,f.x),mix(n010,n110,f.x),f.y),
               mix(mix(n001,n101,f.x),mix(n011,n111,f.x),f.y),f.z);
}
float fbm(vec3 p){ float amp=0.5,s=0.0; for(int ii=0;ii<4;ii++){ s+=amp*vnoise(p); p=p*2.03+vec3(1.7,9.2,3.3); amp*=0.5; } return s; }
float fbm4d(vec4 p,int it){ vec3 qq=p.xyz; float aa=0.5,ss=0.0; for(int jj=0;jj<5;jj++){ if(jj>=it)break; ss+=aa*vnoise(qq+vec3(p.w*0.01)); qq=qq*2.0+vec3(100.0); aa*=0.5; } return ss; }
vec3 capHue(vec3 c){ float m=max(c.r,max(c.g,c.b)); if(m<=1.0) return c; return c*(1.5/(m+0.5)); }

// ── SDFs (iquilezles, also used by Destroying MC's ray_march.glsl) ───────────
float sdCylinder(vec3 p,float h,float r){ vec2 d=abs(vec2(length(p.xz),p.y))-vec2(r,h); return min(max(d.x,d.y),0.0)+length(max(d,0.0)); }
float sdTorus(vec3 p,vec2 t){ vec2 qt=vec2(length(p.xz)-t.x,p.y); return length(qt)-t.y; }
float sdCappedCone(vec3 p,float h,float r1,float r2){
    vec2 qc=vec2(length(p.xz),p.y); vec2 k1=vec2(r2,h); vec2 k2=vec2(r2-r1,2.0*h);
    vec2 ca=vec2(qc.x-min(qc.x,(qc.y<0.0)?r1:r2),abs(qc.y)-h);
    vec2 cb=qc-k1+k2*clamp(dot(k1-qc,k2)/dot(k2,k2),0.0,1.0);
    float s=(cb.x<0.0&&ca.y<0.0)?-1.0:1.0; return s*sqrt(min(dot(ca,ca),dot(cb,cb)));
}
float opSmoothUnion(float d1,float d2,float k){
    if(k<=1e-5) return min(d1,d2);
    float h=clamp(0.5+0.5*(d2-d1)/k,0.0,1.0);
    return mix(d2,d1,h)-k*h*(1.0-h);
}

// ── field: cylinder + torus + cone like Destroying MC, scaled by Progress ────
float mapField(vec3 p, float prog){
    float t=clamp(prog,0.0,1.0);
    vec3 nuke=p-Center;
    float cyl=sdCylinder(nuke-vec3(0,40*t,0),40*t,11);
    float tor=sdTorus(nuke-vec3(0,70*t,0),vec2(30*t,20));
    float cone=sdCappedCone(nuke-vec3(0,3,0),80*t,3,1);
    float distortion=(fbm(nuke+vec3(Time*0.5))*0.5 -fbm(nuke*0.1+vec3(Time*0.05))*1.0)*3.0;
    return opSmoothUnion(tor,opSmoothUnion(cyl,cone,20.0),6.0)-distortion;
}
float mapLQ(vec3 p,float prog){
    float t=clamp(prog,0.0,1.0); vec3 n=p-Center;
    float cyl=sdCylinder(n-vec3(0,40*t,0),40*t,11);
    float tor=sdTorus(n-vec3(0,70*t,0),vec2(30*t,20));
    float cone=sdCappedCone(n-vec3(0,3,0),80*t,3,1);
    return opSmoothUnion(tor,opSmoothUnion(cyl,cone,20.0),6.0);
}

out vec4 fragColor;

void main(){
    if(Solid>0.5){ fragColor=vec4(1,0,1,1); return; }
    float r=max(Radius,1.0);
    vec3 ro=CamPos;
    vec3 rd=normalize(vWorld-ro);
    float rl=length(vWorld-ro); if(rl<0.001) discard;
    // intersection with proxy box radius*2
    float box=r*2.0;
    vec3 bmin=Center-vec3(box), bmax=Center+vec3(box);
    vec3 invRd=vec3((rd.x<0? -1:1)/max(abs(rd.x),1e-6),(rd.y<0? -1:1)/max(abs(rd.y),1e-6),(rd.z<0? -1:1)/max(abs(rd.z),1e-6));
    vec3 t1=(bmin-ro)*invRd, t2=(bmax-ro)*invRd;
    vec3 tmin=min(t1,t2), tmax=max(t1,t2);
    float tEnter=max(max(tmin.x,tmin.y),tmin.z), tExit=min(min(tmax.x,tmax.y),tmax.z);
    tEnter=max(tEnter,0.0); if(tExit<=tEnter) discard;
    // march — adaptive steps: more for large radii (tearing = stepLen too big for view angle)
    int steps=int(clamp(Steps,48.0,224.0));
    // Ensure large radius gets enough steps even if distance-based stepsFor is low
    steps=int(clamp(max(float(steps), r*1.6), 48.0, 224.0));
    float stepLen=(tExit-tEnter)/float(steps);
    // Two-axis jitter: hash xy + hash yx decorrelates vertical/horizontal tearing
    float jitter=hash31(vec3(gl_FragCoord.xy, Seed*91.0+7.3));
    jitter=mix(jitter, hash31(vec3(gl_FragCoord.yx*1.7, Seed*13.0)), 0.5);
    vec3 pos=ro+rd*(tEnter+stepLen*jitter);
    vec3 acc=vec3(0); float trans=1.0;
    // Palette tint: ColorA=cool ambient, ColorB=mid fire, ColorC=hot core
    // Lower initial glow (8 not 12) + early darkening so it doesn't start white
    float progC=clamp(Progress,0.0,1.0);
    float glow=4.0+8.0*(1.0-progC);
    float earlyDark=mix(0.55, 1.0, clamp(progC*4.0,0.0,1.0)); // first 25% darker
    for(int mi=0;mi<64;mi++){
        if(mi>=steps) break;
        float d=mapLQ(pos,Progress);
        if(d<1.2){
            d=mapField(pos,Progress);
            // volumetric cloud sample
            vec3 stepS=rd*0.45;
            vec3 cloudPos=pos;
            float accum=0.0;
            for(int j=0;j<8;j++){
                cloudPos+=stepS;
                float n=fbm4d(vec4((cloudPos+vec3(0,40,0)*sin(Time*0.001))*0.1,Time*0.05),5);
                accum+=n*0.12+max(-d*0.14,0.0);
                if(accum>=1.0) break;
            }
            float distL=length(pos-ro);
            float fireNoise=fbm4d(vec4((pos+vec3(0,40,0))*0.1,Time*0.05),4);
            fireNoise=clamp((fireNoise-0.5)*4.0+0.5,0.0,1.0);
            // Warhead palette: ColorC=hot core, ColorB=mid fire, ColorA=cool ambient — all must contribute so GLSL keeps the uniforms
            vec3 paletteHot=ColorC.rgb, paletteMid=ColorB.rgb, paletteCool=ColorA.rgb;
            vec3 fireCol=mix(paletteMid, paletteHot, smoothstep(0.35,0.85,fireNoise));
            fireCol=mix(fireCol, paletteCool, 0.12);
            fireCol=clamp(fireCol*1.06, vec3(0.0), vec3(1.6));
            vec3 fire=fireCol*max((1.0-exp(-distL*0.2))*fireNoise,0.0)*glow;
            vec3 scatterBase=vec3(1.0-exp(-distL*0.15));
            vec3 scatterTint=mix(scatterBase, paletteCool*0.55+scatterBase*0.45, 0.22);
            vec3 sampleCol=capHue(scatterTint+fire*0.65)*earlyDark;
            float edgeFade=1.0 - smoothstep(0.62, 0.96, length(pos - Center) / max(r*2.0, 1e-4));
            float ao=clamp(accum*1.15,0.0,1.0) * edgeFade;
            // Boost inside density so core is opaque not transparent
            ao=mix(ao, 1.0, clamp((1.2-d)*0.35,0.0,1.0)*0.5);
            acc+=capHue(sampleCol)*ao*trans*0.98;
            trans*=1.0-ao*0.85;
            if(trans<0.02) break;
        }
        pos+=rd*stepLen;
    }
    float opacity=1.0-trans;
    // Don't discard low opacity early — that made mushroom transparent at start. Keep at least faint core.
    if(opacity<0.004 && dot(acc,acc)<0.00002) discard;
    // Soft knee: keep hue, allow slight over-opacity to stay opaque (not transparent) but not white
    vec3 outCol=capHue(acc);
    // Warm bias stronger: 0.6 so prog=0 is clearly orange not white
    float warmBias=clamp((0.35-progC)*2.2,0.0,1.0);
    outCol=mix(outCol, outCol*vec3(1.25,0.85,0.55), warmBias*0.6);
    outCol=min(outCol, vec3(opacity*1.2));
    // Ensure alpha at least covers where acc is visible (prevents transparent mushroom)
    float alphaOut=max(opacity, length(outCol)*0.55);
    fragColor=vec4(outCol*Strength, alphaOut*Strength);
}
