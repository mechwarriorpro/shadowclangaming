// Original: SpacePotatoee/Destroying-Minecraft — All Rights Reserved, ported to NeoForge for comparison (see THIRD_PARTY.md)

float rand(float value) {
    return fract(sin(dot(value, 78.223)) * 43758.5453);
}

float rand(vec2 coord) {
    return fract(sin(dot(coord, vec2(12.9898, 78.223))) * 43758.5453) * 2.0 - 1.0;
}

float rand(vec3 coord) {
    return fract(sin(dot(coord, vec3(12.9898, 78.223, 472.63984))) * 43758.5453);
}

vec3 randVec3(float value) {
    return vec3(rand(value - 289.3254),
                rand(value + 35663.25),
                rand(value + 8274.52345)
    );
}

float scanLines(vec2 st) {
    return sin(dot(st.xy,
    vec2(0.012,0.0))*
    437.5453123);
}

//Thank you https://gist.github.com/patriciogonzalezvivo/670c22f3966e662d2f83 for the noise functions
float mod289(float x){return x - floor(x * (1.0 / 289.0)) * 289.0;}
vec4 mod289(vec4 x){return x - floor(x * (1.0 / 289.0)) * 289.0;}
vec4 perm(vec4 x){return mod289(((x * 34.0) + 1.0) * x);}

float noise(vec3 p) {
    vec3 a = floor(p);
    vec3 d = p - a;
    d = d * d * (3.0 - 2.0 * d);

    vec4 b = a.xxyy + vec4(0.0, 1.0, 0.0, 1.0);
    vec4 k1 = perm(b.xyxy);
    vec4 k2 = perm(k1.xyxy + b.zzww);

    vec4 c = k2 + a.zzzz;
    vec4 k3 = perm(c);
    vec4 k4 = perm(c + 1.0);

    vec4 o1 = fract(k3 * (1.0 / 41.0));
    vec4 o2 = fract(k4 * (1.0 / 41.0));

    vec4 o3 = o2 * d.z + o1 * (1.0 - d.z);
    vec2 o4 = o3.yw * d.x + o3.xz * (1.0 - d.x);

    return o4.y * d.y + o4.x * (1.0 - d.y);
}

float fbm(vec3 x, int iterations) {
    float v = 0.0;
    float a = 0.5;
    vec3 shift = vec3(100);
    for (int i = 0; i < iterations; ++i) {
        v += a * noise(x);
        x = x * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

vec4 permute(vec4 x) {
    return mod289(((x*34.0)+1.0)*x);
}

float permute(float x) {
    return mod289(((x*34.0)+1.0)*x);
}

vec4 taylorInvSqrt(vec4 r)
{
    return 1.79284291400159 - 0.85373472095314 * r;
}

float taylorInvSqrt(float r)
{
    return 1.79284291400159 - 0.85373472095314 * r;
}

vec4 grad4(float j, vec4 ip)
{
    const vec4 ones = vec4(1.0, 1.0, 1.0, -1.0);
    vec4 p,s;

    p.xyz = floor( fract (vec3(j) * ip.xyz) * 7.0) * ip.z - 1.0;
    p.w = 1.5 - dot(abs(p.xyz), ones.xyz);
    s = vec4(lessThan(p, vec4(0.0)));
    p.xyz = p.xyz + (s.xyz*2.0 - 1.0) * s.www;

    return p;
}

// (sqrt(5) - 1)/4 = F4, used once below
#define F4 0.309016994374947451

float snoise(vec4 v) {
    const vec4  C = vec4( 0.138196601125011,  // (5 - sqrt(5))/20  G4
    0.276393202250021,  // 2 * G4
    0.414589803375032,  // 3 * G4
    -0.447213595499958); // -1 + 4 * G4

    // First corner
    vec4 i  = floor(v + dot(v, vec4(F4)) );
    vec4 x0 = v -   i + dot(i, C.xxxx);

    // Other corners

    // Rank sorting originally contributed by Bill Licea-Kane, AMD (formerly ATI)
    vec4 i0;
    vec3 isX = step( x0.yzw, x0.xxx );
    vec3 isYZ = step( x0.zww, x0.yyz );
    //  i0.x = dot( isX, vec3( 1.0 ) );
    i0.x = isX.x + isX.y + isX.z;
    i0.yzw = 1.0 - isX;
    //  i0.y += dot( isYZ.xy, vec2( 1.0 ) );
    i0.y += isYZ.x + isYZ.y;
    i0.zw += 1.0 - isYZ.xy;
    i0.z += isYZ.z;
    i0.w += 1.0 - isYZ.z;

    // i0 now contains the unique values 0,1,2,3 in each channel
    vec4 i3 = clamp( i0, 0.0, 1.0 );
    vec4 i2 = clamp( i0-1.0, 0.0, 1.0 );
    vec4 i1 = clamp( i0-2.0, 0.0, 1.0 );

    //  x0 = x0 - 0.0 + 0.0 * C.xxxx
    //  x1 = x0 - i1  + 1.0 * C.xxxx
    //  x2 = x0 - i2  + 2.0 * C.xxxx
    //  x3 = x0 - i3  + 3.0 * C.xxxx
    //  x4 = x0 - 1.0 + 4.0 * C.xxxx
    vec4 x1 = x0 - i1 + C.xxxx;
    vec4 x2 = x0 - i2 + C.yyyy;
    vec4 x3 = x0 - i3 + C.zzzz;
    vec4 x4 = x0 + C.wwww;

    // Permutations
    i = mod289(i);
    float j0 = permute( permute( permute( permute(i.w) + i.z) + i.y) + i.x);
    vec4 j1 = permute( permute( permute( permute (
                                             i.w + vec4(i1.w, i2.w, i3.w, 1.0 ))
                                         + i.z + vec4(i1.z, i2.z, i3.z, 1.0 ))
                                + i.y + vec4(i1.y, i2.y, i3.y, 1.0 ))
                       + i.x + vec4(i1.x, i2.x, i3.x, 1.0 ));

    // Gradients: 7x7x6 points over a cube, mapped onto a 4-cross polytope
    // 7*7*6 = 294, which is close to the ring size 17*17 = 289.
    vec4 ip = vec4(1.0/294.0, 1.0/49.0, 1.0/7.0, 0.0) ;

    vec4 p0 = grad4(j0,   ip);
    vec4 p1 = grad4(j1.x, ip);
    vec4 p2 = grad4(j1.y, ip);
    vec4 p3 = grad4(j1.z, ip);
    vec4 p4 = grad4(j1.w, ip);

    // Normalise gradients
    vec4 norm = taylorInvSqrt(vec4(dot(p0,p0), dot(p1,p1), dot(p2, p2), dot(p3,p3)));
    p0 *= norm.x;
    p1 *= norm.y;
    p2 *= norm.z;
    p3 *= norm.w;
    p4 *= taylorInvSqrt(dot(p4,p4));

    // Mix contributions from the five corners
    vec3 m0 = max(0.6 - vec3(dot(x0,x0), dot(x1,x1), dot(x2,x2)), 0.0);
    vec2 m1 = max(0.6 - vec2(dot(x3,x3), dot(x4,x4)            ), 0.0);
    m0 = m0 * m0;
    m1 = m1 * m1;
    return 1.0 - (49.0 * ( dot(m0*m0, vec3( dot( p0, x0 ), dot( p1, x1 ), dot( p2, x2 )))
    + dot(m1*m1, vec2( dot( p3, x3 ), dot( p4, x4 ) ) ) ) );

}

vec4 hash44(vec4 p4)
{
    p4 = fract(p4  * vec4(.1031, .1030, .0973, .1099));
    p4 += dot(p4, p4.wzxy+33.33);
    return fract((p4.xxyz+p4.yzzw)*p4.zywx);
}

vec4 random_gradient(vec4 vector)
{
    return hash44(vector) * 2.0 - 1.0;
}

float perlin_noise(vec4 point)
{
    vec4 gridCell = floor(point);
    vec4 local = point - gridCell;

    vec4 weight = local * local * (3.0 - 2.0 * local);

    return mix(//W
        mix(//w0 z
            mix(//w0 z0 y
                mix(//w0 z0 y0 x
                    dot(
                        local - vec4(0,0,0,0),
                        random_gradient(gridCell + vec4(0,0,0,0))),
                    dot(
                        local - vec4(1,0,0,0),
                        random_gradient(gridCell + vec4(1,0,0,0))),
                    weight.x),
                mix(//w0 z0 y1 x
                    dot(
                        local - vec4(0,1,0,0),
                        random_gradient(gridCell + vec4(0,1,0,0))),
                    dot(
                        local - vec4(1,1,0,0),
                        random_gradient(gridCell + vec4(1,1,0,0))),
                    weight.x),
                weight.y),
            mix(//w0 z1 y
                mix(//w0 z1 y0 x
                    dot(
                        local - vec4(0,0,1,0),
                        random_gradient(gridCell + vec4(0,0,1,0))),
                    dot(
                        local - vec4(1,0,1,0),
                        random_gradient(gridCell + vec4(1,0,1,0))),
                    weight.x),
                mix(//w0 z1 y1 x
                    dot(
                        local - vec4(0,1,1,0),
                        random_gradient(gridCell + vec4(0,1,1,0))),
                    dot(
                        local - vec4(1,1,1,0),
                        random_gradient(gridCell + vec4(1,1,1,0))),
                    weight.x),
                weight.y),
            weight.z),
        mix(//w1 z
            mix(//w1 z0 y
                mix(//w1 z0 y0 x
                    dot(
                        local - vec4(0,0,0,1),
                        random_gradient(gridCell + vec4(0,0,0,1))),
                    dot(
                        local - vec4(1,0,0,1),
                        random_gradient(gridCell + vec4(1,0,0,1))),
                    weight.x),
                mix(//w1 z0 y1 x
                    dot(
                        local - vec4(0,1,0,1),
                        random_gradient(gridCell + vec4(0,1,0,1))),
                    dot(
                        local - vec4(1,1,0,1),
                        random_gradient(gridCell + vec4(1,1,0,1))),
                    weight.x),
                weight.y),
            mix(//w1 z1 y
                mix(//w1 z1 y0 x
                    dot(
                        local - vec4(0,0,1,1),
                        random_gradient(gridCell + vec4(0,0,1,1))),
                    dot(
                        local - vec4(1,0,1,1),
                        random_gradient(gridCell + vec4(1,0,1,1))),
                    weight.x),
                mix(//w1 z1 y1 x
                    dot(
                        local - vec4(0,1,1,1),
                        random_gradient(gridCell + vec4(0,1,1,1))),
                    dot(
                        local - vec4(1,1,1,1),
                        random_gradient(gridCell + vec4(1,1,1,1))),
                    weight.x),
                weight.y),
            weight.z),
        weight.w);
}

float fbm4d(vec4 x, int iterations) {
    float v = 0.0;
    float a = 0.5;
    vec4 shift = vec4(100);
    for (int i = 0; i < iterations; ++i) {
        v += a * perlin_noise(x);
        x = x * 2.0 + shift;
        a *= 0.5;
    }
    return v+0.5;
}