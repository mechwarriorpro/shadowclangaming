#version 150
// Photorealistic ground-break cracks — inspired by Destroying MC cracks.fsh
// Ground material check via height threshold + fbm hole, magma through cracks.
in vec3 vWorld;
uniform vec3 Center;
uniform vec3 CamPos;
uniform vec4 ColorA;
uniform vec4 ColorB;
uniform float Radius;
uniform float Progress;
uniform float Time;
uniform float Steps;
uniform float Seed;
uniform float Strength;
uniform float Solid;
uniform float GroundY;
out vec4 fragColor;
float hash31(vec3 p){ p=fract(p*0.3183099+vec3(0.11,0.17,0.13)); p*=17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
float vnoise(vec3 x){ vec3 i=floor(x); vec3 f=fract(x); f=f*f*(3.0-2.0*f);
 float n000=hash31(i+vec3(0,0,0)),n100=hash31(i+vec3(1,0,0)),n010=hash31(i+vec3(0,1,0)),n110=hash31(i+vec3(1,1,0)),n001=hash31(i+vec3(0,0,1)),n101=hash31(i+vec3(1,0,1)),n011=hash31(i+vec3(0,1,1)),n111=hash31(i+vec3(1,1,1));
 return mix(mix(mix(n000,n100,f.x),mix(n010,n110,f.x),f.y),mix(mix(n001,n101,f.x),mix(n011,n111,f.x),f.y),f.z); }
float fbm(vec3 p){ float ampC=0.5,sC=0.0; for(int iiC=0;iiC<5;iiC++){ sC+=ampC*vnoise(p); p=p*2.03+vec3(1.7,9.2,3.3); ampC*=0.5; } return sC; }
float getNoise(vec3 pos){ float n=fbm(pos*0.5)*2.0-1.0; return clamp(abs(n)+fbm(pos*0.3)*0.5,0.0,1.0); }

void main(){
 if(Solid>0.5){ fragColor=vec4(1,0,1,1); return; }
 float r=max(Radius,1.0);
 vec3 ro=CamPos, rd=normalize(vWorld-ro);
 float boxR=r*2.0;
 vec3 bmin=Center-vec3(boxR), bmax=Center+vec3(boxR);
 vec3 invRd=vec3((rd.x<0? -1:1)/max(abs(rd.x),1e-6),(rd.y<0? -1:1)/max(abs(rd.y),1e-6),(rd.z<0? -1:1)/max(abs(rd.z),1e-6));
 vec3 t1=(bmin-ro)*invRd, t2=(bmax-ro)*invRd;
 vec3 tmn=min(t1,t2), tmx=max(t1,t2);
 float tEnter=max(max(tmn.x,tmn.y),tmn.z), tExit=min(min(tmx.x,tmx.y),tmx.z);
 tEnter=max(tEnter,0.0); if(tExit<=tEnter) discard;
 float prog=clamp(Progress,0.0,1.0);
 float timeScale=max(prog*48.0+4.0,4.0);
 vec2 centerXZ=Center.xz;
 // march short ground slab
 int steps=int(clamp(Steps*0.9 + max(r*0.5, 0.0),24.0,64.0));
 float stepLen=(tExit-tEnter)/float(steps);
 vec3 pos=ro+rd*(tEnter+stepLen*hash31(vec3(gl_FragCoord.xy,Seed*37.0)));
 vec3 acc=vec3(0); float trans=1.0;
 for(int miC=0;miC<48;miC++){
  if(miC>=steps) break;
  float edgeFadeC=1.0 - smoothstep(0.62, 0.96, length(pos - Center) / max(r*2.0, 1e-4));
  // ground layer: y ~ GroundY (Destroying Minecraft's material==2 check → only on terrain)
  float dy=abs(pos.y-GroundY);
  if(dy<4.0){
   vec3 gPos=vec3(pos.x, 4.0, pos.z);
   float holeSize=pow(max(0.0,1.0-distance(centerXZ,gPos.xz)/timeScale),1.0);
   float n=getNoise(gPos);
   // slight Time-driven pulsation so Time uniform is retained (was optimized away)
   float timePulse=0.96+0.04*sin(Time*0.55+Seed*6.2831);
   float holeJitter=holeSize*(0.98+0.02*sin(Time*0.18+ gPos.x*0.11));
   if(n < holeJitter){
     // inside crack — magma
     float magma=0.0;
     vec3 marchP=gPos;
     for(int k=0;k<12;k++){
       marchP.y+=0.12;
       float holeK=pow(max(0.0,1.0-distance(centerXZ,marchP.xz)/timeScale),1.0);
       float nn=getNoise(vec3(marchP.x,4.0,marchP.z));
       magma+=0.08;
       if(nn>holeK) break;
     }
     float flicker=0.92+0.08*sin(Time*0.62+Seed*6.2831 + magma*2.1);
     vec3 magmaCol=vec3(1.0,0.4,0.07)*magma*2.2*flicker*timePulse;
     float aoC=clamp(magma*0.6,0.0,1.0)*edgeFadeC;
     acc+=magmaCol*aoC*trans;
     trans*=1.0-aoC*0.9;
   } else {
     // dark fissure rim emission — also Time-modulated
     float rim=exp(-pow((n-holeJitter)*12.0,2.0))*0.4*timePulse*edgeFadeC;
     if(rim>0.01){ acc+=ColorA.rgb*rim*trans*0.5; trans*=1.0-rim*0.3; }
   }
  }
  // also draw faint shock dust ring outward
  float rr=length(pos.xz-centerXZ);
  float ring=(1.0-smoothstep(0.0, r*0.12, abs(rr - r*prog*1.3)))*edgeFadeC;
  if(ring>0.01 && abs(pos.y-GroundY)<3.0){
   acc+=ColorB.rgb*ring*0.25*trans;
  }
  pos+=rd*stepLen;
  if(trans<0.02) break;
 }
 float opacity=1.0-trans;
 if(opacity<0.004 && dot(acc,acc)<0.00002) discard;
 fragColor=vec4(min(acc,vec3(opacity))*Strength, opacity*Strength);
}
