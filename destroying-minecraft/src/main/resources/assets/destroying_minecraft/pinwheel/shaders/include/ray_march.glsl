// Original: SpacePotatoee/Destroying-Minecraft — All Rights Reserved, ported to NeoForge for comparison (see THIRD_PARTY.md)
//https://iquilezles.org/articles/distfunctions/

const float pi = 3.14159265;
float dot2( in vec2 v ) { return dot(v,v); }
float dot2( in vec3 v ) { return dot(v,v); }

float opSubtraction( float d1, float d2 ) {
    return max(-d1,d2);
}

float opIntersection( float d1, float d2 ) {
    return max(d1,d2);
}

float opSmoothIntersection( float d1, float d2, float k ) {
    float h = clamp( 0.5 - 0.5*(d2-d1)/k, 0.0, 1.0 );
    return mix( d2, d1, h ) + k*h*(1.0-h);
}

float opSmoothUnion( float d1, float d2, float k ) {
    float h = clamp( 0.5 + 0.5*(d2-d1)/k, 0.0, 1.0 );
    return mix( d2, d1, h ) - k*h*(1.0-h);
}

float sdPlane( vec3 p, vec3 n, float h )
{
    // n must be normalized
    return dot(p,n) + h;
}

float sdSphere(vec3 p, float s){
    return length(p)-s;
}

float sdBox( vec3 p, vec3 b ) {
    vec3 q = abs(p) - b;
    return length(max(q,0.0)) + min(max(q.x,max(q.y,q.z)),0.0);
}

float sdCappedCone( vec3 p, float h, float r1, float r2 ) {
    vec2 q = vec2( length(p.xz), p.y );
    vec2 k1 = vec2(r2,h);
    vec2 k2 = vec2(r2-r1,2.0*h);
    vec2 ca = vec2(q.x-min(q.x,(q.y<0.0)?r1:r2), abs(q.y)-h);
    vec2 cb = q - k1 + k2*clamp( dot(k1-q,k2)/dot2(k2), 0.0, 1.0 );
    float s = (cb.x<0.0 && ca.y<0.0) ? -1.0 : 1.0;
    return s*sqrt( min(dot2(ca),dot2(cb)) );
}

float sdCone( vec3 p, vec2 c, float h ){
    float q = length(p.xz);
    return max(dot(c.xy,vec2(q,p.y)),-h-p.y);
}

float sdCylinder(vec3 p, float h, float r ) {
    vec2 d = abs(vec2(length(p.xz),p.y)) - vec2(r,h);
    return min(max(d.x,d.y),0.0) + length(max(d,0.0));
}

float sdInfCylinder( vec3 p, vec3 c ) {
    return length(p.xz-c.xy)-c.z;
}

float sdRoundedCylinder( vec3 p, float ra, float rb, float h ){
    vec2 d = vec2( length(p.xz)-2.0*ra+rb, abs(p.y) - h );
    return min(max(d.x,d.y),0.0) + length(max(d,0.0)) - rb;
}

float sdTorus( vec3 p, vec2 t ) {
    vec2 q = vec2(length(p.xz)-t.x,p.y);
    return length(q)-t.y;
}

float sdEllipsoid( vec3 p, vec3 r ) {
    float k0 = length(p/r);
    float k1 = length(p/(r*r));
    return k0*(k0-1.0)/k1;
}

mat2 rot2D(float angle) {
    float rad = (angle * 3.151592)/180.0;
    float s = sin(rad);
    float c = cos(rad);
    return mat2(c, -s, s, c);
}

// Thank you Sonic Ether: https://www.shadertoy.com/view/lstSRS
float atan2(float y, float x) {
    if (x > 0.0) {
        return atan(y / x);
    }
    else if (x == 0.0) {
        if (y > 0.0) {
            return pi / 2.0;
        }
        else if (y < 0.0) {
            return -(pi / 2.0);
        }
        else {
            return 0.0;
        }
    }
    else { //(x < 0.0)
           if (y >= 0.0) {
               return atan(y / x) + pi;
           }
           else {
               return atan(y / x) - pi;
           }
    }
}