#version 150
// Photorealistic orbital beam — cylinder SDF with flicker + magma cracks
// Inspired by Destroying MC cracks.fsh (CENTER_POS/laserLength) + star_piercer bloom.
// NeoForge port: same Center/Radius contract as world_blast_volume.
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
uniform float ScgGlowBoost;
out vec4 fragColor;

float hash31(vec3 p){ p=fract(p*0.3183099+vec3(0.11,0.17,0.13)); p*=17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
float vnoise(vec3 x){ vec3 i=floor(x); vec3 f=fract(x); f=f*f*(3.0-2.0*f);
 float n000=hash31(i+vec3(0,0,0)),n100=hash31(i+vec3(1,0,0)),n010=hash31(i+vec3(0,1,0)),n110=hash31(i+vec3(1,1,0)),n001=hash31(i+vec3(0,0,1)),n101=hash31(i+vec3(1,0,1)),n011=hash31(i+vec3(0,1,1)),n111=hash31(i+vec3(1,1,1));
 return mix(mix(mix(n000,n100,f.x),mix(n010,n110,f.x),f.y),mix(mix(n001,n101,f.x),mix(n011,n111,f.x),f.y),f.z); }
float fbm(vec3 p){ float ampB=0.5,sB=0.0; for(int iiB=0;iiB<4;iiB++){ sB+=ampB*vnoise(p); p=p*2.03+vec3(1.7,9.2,3.3); ampB*=0.5; } return sB; }
float sdCylinder(vec3 p,float h,float r){ vec2 d=abs(vec2(length(p.xz),p.y))-vec2(r,h); return min(max(d.x,d.y),0.0)+length(max(d,0.0)); }

void main(){
 if(Solid>0.5){ fragColor=vec4(1,0,1,1); return; }
 float r=max(Radius,1.0);
 vec3 ro=CamPos, rd=normalize(vWorld-ro);
 float boxR=r*2.0;
 vec3 bmin=Center-vec3(boxR), bmax=Center+vec3(boxR);
 vec3 invRd=vec3((rd.x<0? -1:1)/max(abs(rd.x),1e-6),(rd.y<0? -1:1)/max(abs(rd.y),1e-6),(rd.z<0? -1:1)/max(abs(rd.z),1e-6));
 vec3 t1=(bmin-ro)*invRd, t2=(bmax-ro)*invRd;
 vec3 tmn=min(t1,t2), tmx=max(t1,t2);
 float tEnter=max(max(tmn.x,tmn.y),tmn.z);
 float tExit=min(min(tmx.x,tmx.y),tmx.z);
 tEnter=max(tEnter,0.0); if(tExit<=tEnter) discard;

 float prog=clamp(Progress,0,1);
 float beamW=mix(0.8, 3.5, prog)*(0.8+0.4*sin(Time*6.0+fract(Seed*91.0)*6.28));
 float beamH=r*1.8 + 140.0*prog; // tall shaft from orbit
 float jitter=hash31(vec3(gl_FragCoord.xy,Seed*17.0+1.1));
 int steps=int(clamp(Steps*1.5,16.0,96.0));
 float stepLen=(tExit-tEnter)/float(steps);
 vec3 pos=ro+rd*(tEnter+stepLen*jitter);
 vec3 acc=vec3(0); float trans=1.0;
 for(int miB=0;miB<96;miB++){
  if(miB>=steps) break;
  vec3 qb=pos-Center;
  // vertical cylinder at center
  float dCyl=sdCylinder(qb-vec3(0,beamH*0.35,0),beamH*0.5,beamW*0.5);
  float edgeWobble=(fbm(qb*0.4+vec3(Time*0.8))*2.0-1.0)*beamW*0.15;
  dCyl+=edgeWobble;
  float aoB=0.0;
  if(dCyl<0.0){
   // inside beam: hot core + current bands
   float core=1.0-smoothstep(0.0,beamW*0.35,length(qb.xz));
   float bands=0.82+0.18*sin(qb.y*0.18+Time*4.2);
   float flicker=0.9+0.1*sin(Time*12.0+hash31(qb*0.5)*10.0);
   aoB= (0.9*core + 0.4*(1-core))*bands*flicker;
  } else if(dCyl<beamW*0.6){
   float glow=1.0-smoothstep(0.0,beamW*0.6,dCyl);
   aoB=glow*0.35;
  }
  float edgeFadeB=1.0 - smoothstep(0.62, 0.96, length(pos - Center) / max(r*2.0, 1e-4));
  aoB*=edgeFadeB;
  if(aoB>0.01){
   vec3 col=mix(ColorB.rgb, ColorA.rgb, clamp(aoB*1.3,0.0,1.0));
   col*=1.0+ aoB*ScgGlowBoost*0.35;
   acc+=col*aoB*trans*0.9;
   trans*=1.0-clamp(aoB*0.85,0.0,1.0);
   if(trans<0.02) break;
  }
  pos+=rd*stepLen;
 }
 float opacity=1.0-trans;
 if(opacity<0.004 && dot(acc,acc)<0.00002) discard;
 fragColor=vec4(min(acc,vec3(opacity))*Strength, opacity*Strength);
}
