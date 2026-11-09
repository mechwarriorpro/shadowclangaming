#version 150
// Photorealistic stellar: granule convection + prominences + nebula
// Inspired by Destroying MC supernova/planet: crater displacement, star piercer bloom,
// but rendered as volume granules rather than planet raymarch.
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
out vec4 fragColor;
float hash31(vec3 p){ p=fract(p*0.3183099+vec3(0.11,0.17,0.13)); p*=17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
float vnoise(vec3 x){ vec3 i=floor(x); vec3 f=fract(x); f=f*f*(3.0-2.0*f);
 float n000=hash31(i+vec3(0,0,0)),n100=hash31(i+vec3(1,0,0)),n010=hash31(i+vec3(0,1,0)),n110=hash31(i+vec3(1,1,0)),n001=hash31(i+vec3(0,0,1)),n101=hash31(i+vec3(1,0,1)),n011=hash31(i+vec3(0,1,1)),n111=hash31(i+vec3(1,1,1));
 return mix(mix(mix(n000,n100,f.x),mix(n010,n110,f.x),f.y),mix(mix(n001,n101,f.x),mix(n011,n111,f.x),f.y),f.z); }
float fbm(vec3 p){ float ampS=0.5,sS=0.0; for(int iiS=0;iiS<4;iiS++){ sS+=ampS*vnoise(p); p=p*2.03+vec3(1.7,9.2,3.3); ampS*=0.5; } return sS; }

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
 // stellar implodes then flashes: early 0-0.3 shrink, 0.3-0.5 flash, rest nebula expansion
 // Fusion flash was causing white start: reduce white boost, add early darkening
 float flash= prog<0.3 ? 1.0-prog*2.0 : prog<0.5 ? (prog-0.3)*8.0 : 1.0;
 float earlyDarkS=mix(0.65, 1.0, clamp(prog*3.0,0.0,1.0));
 float outerR=r*(0.4+prog*1.1);
 int steps=int(clamp(Steps,32.0,128.0));
 steps=int(clamp(max(float(steps), r*1.4), 32.0, 192.0));
 float stepLen=(tExit-tEnter)/float(steps);
 float jitter=hash31(vec3(gl_FragCoord.xy,Seed*91.0+2.0));
 vec3 pos=ro+rd*(tEnter+stepLen*jitter);
 vec3 acc=vec3(0); float trans=1.0;
 float seedOff=Seed*97.0;
 for(int miS=0;miS<64;miS++){
  if(miS>=steps) break;
  vec3 qs=(pos-Center)/max(outerR,1.0);
  float rr=length(qs);
  // granule on star surface: dir-based fbm
  vec3 dir=normalize(qs+vec3(1e-3));
  float gran=fbm(dir*9.0+vec3(Time*0.05,0.0,0.0)+vec3(seedOff))*0.8;
  float onStar=1.0-smoothstep(0.3,0.9,rr);
  float density=0.0;
  // dense core
  density+=onStar*(0.6+1.1*gran*gran);
  // prominences loops
  float ang=atan(qs.z,qs.x);
  float loops=pow(max(0.0, sin(ang*5.0+seedOff*20.0)),18.0);
  float arcH=0.35+0.5*abs(sin(ang*2.0+Time*0.3));
  float tube=1.0-smoothstep(0.0,0.09, abs(rr-0.45)+abs(abs(qs.y)-arcH)*0.8);
  density+=loops*tube*pow(max(0.0,1.0-smoothstep(0.25,0.6,abs(qs.y))),1.0)*0.9;
  // nebula wisps outside
  float wispA=fbm(qs*0.9+vec3(0.0,Time*0.04,0.0)+vec3(seedOff*0.3));
  float wispB=fbm(qs*1.6-vec3(Time*0.02,0.0,0.0));
  float wisp=wispA*wispB + seedOff*0.002;
  float neb=pow(max(0.0, wisp*2.2),1.15)*(1.0-smoothstep(0.4,2.2,rr));
  density+=neb*0.7;
  // supernova Rayleigh-Taylor shell at prog
  if(prog>0.35){
   float front=mix(0.4,1.6,smoothstep(0.35,0.9,prog));
   float fingers=1.0+0.5*fbm(dir*4.5+vec3(seedOff));
   float edge=1.0-smoothstep(0.0,0.13,abs(rr-front*fingers));
   density+=edge*1.4* smoothstep(0.05,0.4,rr);
  }
  if(density>0.01){
   float heat=mix(0.2,1.0, clamp(gran*1.2+ loops*0.5,0.0,1.0));
   vec3 col=mix(ColorC.rgb, ColorB.rgb, smoothstep(0.0,0.55,heat));
   col=mix(col, ColorA.rgb, smoothstep(0.45,1.0,heat));
   if(prog<0.35) col*=mix(1.0, flash*1.15, clamp(1.0-prog*2.0,0.0,1.0));
   col*=earlyDarkS;
   // warm bias prevents white start
   float warmS=clamp((0.35-prog)*2.5,0.0,1.0);
   col=mix(col, col*vec3(1.3,0.9,0.5), warmS*0.6);
   float edgeFadeS=1.0 - smoothstep(0.62, 0.96, length(pos - Center) / max(r*2.0, 1e-4));
   float aoS=clamp(density*stepLen*1.4/max(outerR,1.0),0.0,1.0) * edgeFadeS;
   // boost core opacity so fusion isn't transparent
   aoS=mix(aoS, 1.0, clamp((1.0-rr)*0.4,0.0,1.0)*0.35);
   acc+=col*aoS*trans*1.15;
   trans*=1.0-aoS*0.88;
   if(trans<0.02) break;
  }
  pos+=rd*stepLen;
 }
 float opacity=1.0-trans;
 if(opacity<0.004 && dot(acc,acc)<0.00002) discard;
 vec3 outS=min(acc, vec3(opacity*1.1));
 float alphaS=max(opacity, length(outS)*0.6);
 fragColor=vec4(outS*Strength, alphaS*Strength);
}
