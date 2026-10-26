#version 150
// Volumetric warhead effect, fragment half.
//
// This is the whole show. A cube the size of the blast is drawn around the detonation and this shader
// raymarches a procedural density field through it - no textures, no samplers, nothing downloaded: the
// fireball is computed per pixel from value noise, domain-warped, lit by a three-stop palette that comes
// straight from the warhead's own motif table. Depth-tested against the terrain, so a hill really does
// occlude it and walking around one really does change what you see.
//
// One code path serves all thirty-eight warheads. What differs between them arrives as uniforms: shape
// (beam, ring, sky circle, streaks), motion (turbulence, rise, swirl, implosion) and colour. There is no
// per-warhead branch here to fall out of sync with the Java table.
//
// Cost is bounded on purpose: a fixed march with early exit on opacity, and the caller lowers the step
// count for distant effects. If this program fails to compile on a driver, client/VolumetricFx falls
// back to the CPU geometry and particles - the warhead still goes off looking like itself.

in vec3 vWorld;

uniform vec4 ColorModulator;
uniform vec3 Center;
uniform vec3 CamPos;
uniform vec4 ColorA;
uniform vec4 ColorB;
uniform vec4 ColorC;
uniform float Radius;
uniform float Progress;
uniform float Phase;
uniform float Time;
uniform float Steps;
uniform float Beam;
uniform float Ring;
uniform float SkyRing;
uniform float Implode;
uniform float Turb;
uniform float Rise;
uniform float Streaks;
uniform float Dark;
uniform float Glow;
uniform float Swirl;
uniform float Seed;

out vec4 fragColor;

// Half-extent of the proxy cube, in blast radii. client/VolumetricFx draws a cube this size, so the two
// must stay in step: the shader marches exactly the volume the geometry covers, no more and no less.
// Every feature below is written to live inside it.
const float PROXY_BOX = 3.0;

// ---------------------------------------------------------------- value noise

float hash31(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.11, 0.17, 0.13));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash31(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash31(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash31(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash31(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash31(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash31(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash31(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash31(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
               mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float a = 0.5;
    float s = 0.0;
    for (int i = 0; i < 4; i++) {
        s += a * vnoise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.3);
        a *= 0.5;
    }
    return s;
}

// ---------------------------------------------------------------- the field
//
// Everything is evaluated in unit space: q is the sample position relative to the blast centre divided
// by its current radius, so the same maths draws a grenade and a two-kilometre crater.

float sampleField(vec3 wp, out vec3 emission) {
    float radius = max(Radius, 0.5);
    float t = clamp(Progress, 0.0, 1.0);
    bool charging = Phase < 0.5;

    // How big it is this instant. A charge swells towards full size and holds; a detonation snaps out,
    // overshoots, then blows apart and fades.
    float grow = charging
            ? mix(0.16, 1.0, smoothstep(0.0, 0.9, t))
            : mix(0.28, 1.35, smoothstep(0.0, 0.5, t));
    float fade = charging ? mix(0.55, 1.0, t) : 1.0 - smoothstep(0.4, 1.0, t);

    vec3 q = (wp - Center) / (radius * max(grow, 0.05));
    float r = length(q);

    // Motion: shear around Y, scroll upward, and pull the field inward while charging so the whole
    // thing visibly winds up before it lets go.
    float ang = Time * (0.3 + Swirl * 0.9) - Implode * t * 1.6;
    float ca = cos(ang);
    float sa = sin(ang);
    vec3 p = vec3(q.x * ca - q.z * sa, q.y, q.x * sa + q.z * ca);
    p.y -= Time * Rise * 0.8;
    p *= 1.0 + Implode * 0.3 * (charging ? 1.0 - t : t);

    // Domain warp: two cheap warps fold the noise over itself, which is what turns a blob into fire.
    // Seed is this warhead's own offset into the noise field, so two weapons of the same theme do not
    // produce the same fireball with a different colour.
    vec3 seedOffset = vec3(Seed * 97.0, Seed * 41.0, Seed * 63.0);
    float wx = fbm(p * 1.7 + vec3(11.3, 5.1, 3.7) + seedOffset);
    float wz = fbm(p * 1.7 + vec3(31.7, 7.9, 13.1) + seedOffset);
    vec3 warped = p + vec3(wx - 0.5, 0.0, wz - 0.5) * (0.9 + Turb);
    float n = fbm((warped + seedOffset * 0.35) * (1.5 + Turb * 1.3)) * 2.0 - 1.0;

    // Body: a turbulent ball that hollows out as it ages, so an old fireball is a rolling shell.
    float shell = 1.0 - smoothstep(0.3, 1.0, r + n * 0.45 * Turb);
    float hollow = charging ? 0.25 : smoothstep(0.1, 0.8, t);
    float body = clamp(shell - smoothstep(0.1, 0.85, r) * hollow * 0.9, 0.0, 1.0);

    // A pillar of light: the quasar lance, the nuclear stem, the arcane column.
    float stem = Beam
            * (1.0 - smoothstep(0.0, 0.3, length(q.xz)))
            * (1.0 - smoothstep(0.0, 1.4, abs(q.y - 0.7)))
            * (0.5 + 0.5 * n);
    body = max(body, clamp(stem, 0.0, 1.0));

    // Ground shockwave, racing outward on detonation and crawling outward while it charges.
    float rr = length(q.xz);
    float ringR = charging ? mix(0.1, 0.7, t) : mix(0.2, 1.7, smoothstep(0.0, 0.65, t));
    float ringW = charging ? 0.22 : mix(0.24, 0.05, t);
    float ring = Ring
            * (1.0 - smoothstep(0.0, ringW, abs(rr - ringR)))
            * (1.0 - smoothstep(0.0, 0.18, abs(q.y + 0.92)))
            * (0.55 + 0.45 * n);
    body = max(body, clamp(ring, 0.0, 1.0));

    // The giant circle overhead, banded and turning: the magic circle the apex weapons are known for.
    float sky = SkyRing
            * (1.0 - smoothstep(0.0, 0.1, abs(q.y - 1.5)))
            * (1.0 - smoothstep(0.45, 1.1, rr))
            * (0.5 + 0.5 * sin(rr * 20.0 - Time * 2.2 + n * 2.0));
    body = max(body, clamp(sky, 0.0, 1.0));

    // Radial streaks: the debris and light thrown out along the blast's own spokes.
    float spoke = atan(q.z, q.x);
    float streak = Streaks
            * pow(max(0.0, sin(spoke * 9.0 + Time * 1.6 + n * 2.0)), 6.0)
            * (1.0 - smoothstep(0.05, 1.15, r))
            * (0.4 + 0.6 * max(0.0, n));
    body = max(body, clamp(streak, 0.0, 1.0));

    body *= fade;
    if (body <= 0.002) {
        emission = vec3(0.0);
        return 0.0;
    }

    // Palette: hot core to mid to cool edge, with the void/soul themes sucking the light out of the
    // middle so they read black-and-purple rather than merely dim.
    float heat = clamp(1.0 - r * 0.8 + n * 0.35, 0.0, 1.0);
    vec3 col = mix(ColorC.rgb, ColorB.rgb, smoothstep(0.0, 0.55, heat));
    col = mix(col, ColorA.rgb, smoothstep(0.45, 1.0, heat));
    col *= mix(1.0, 0.2, Dark * (1.0 - heat));
    // A charge is anticipation: it pulses, because a steady glow reads as a light bug.
    if (charging) {
        col *= 0.72 + 0.28 * sin(Time * 6.0 + r * 4.0);
    }
    emission = col * Glow;
    return body;
}

void main() {
    vec3 ro = CamPos;
    vec3 rd = vWorld - ro;
    float rayLen = length(rd);
    if (rayLen < 0.0001) {
        discard;
    }
    rd /= rayLen;

    // One shade per pixel, whether the camera is outside the proxy volume or standing inside it: from
    // outside the near faces are front-facing, from inside the visible faces are back-facing.
    float radius = max(Radius, 0.5);
    float boxRadius = radius * PROXY_BOX;
    bool inside = all(lessThan(abs(CamPos - Center), vec3(boxRadius)));
    bool shade = inside ? !gl_FrontFacing : gl_FrontFacing;
    if (!shade) {
        discard;
    }

    // Ray/box intersection against the proxy cube, so the march starts at the real entry point.
    vec3 bmin = Center - vec3(boxRadius);
    vec3 bmax = Center + vec3(boxRadius);
    vec3 safeRd = vec3(max(abs(rd.x), 1e-6), max(abs(rd.y), 1e-6), max(abs(rd.z), 1e-6)) * sign(rd);
    vec3 t1 = (bmin - ro) / safeRd;
    vec3 t2 = (bmax - ro) / safeRd;
    vec3 tmin = min(t1, t2);
    vec3 tmax = max(t1, t2);
    float tEnter = max(max(tmin.x, tmin.y), tmin.z);
    float tExit = min(min(tmax.x, tmax.y), tmax.z);
    tEnter = max(tEnter, 0.0);
    if (tExit <= tEnter) {
        discard;
    }

    int steps = int(clamp(Steps, 8.0, 96.0));
    float stepLen = (tExit - tEnter) / float(steps);
    vec3 pos = ro + rd * (tEnter + stepLen * 0.5);

    vec3 acc = vec3(0.0);
    float transmittance = 1.0;
    // Optical depth is measured in units of the blast radius, so a grenade and a kilometre-wide
    // cataclysm are equally solid instead of one being fog and the other a brick.
    float densityScale = 7.0 / max(radius, 1.0);

    for (int i = 0; i < steps; i++) {
        vec3 emission;
        float density = sampleField(pos, emission);
        if (density > 0.002) {
            float a = clamp(density * stepLen * densityScale, 0.0, 1.0);
            acc += emission * a * transmittance;
            transmittance *= 1.0 - a;
            if (transmittance < 0.02) {
                break;
            }
        }
        pos += rd * stepLen;
    }

    if (dot(acc, acc) < 0.00002) {
        discard;
    }
    // Additive: the blend function is set by the renderer, alpha stays 1 so the colour lands whole.
    fragColor = vec4(acc, 1.0) * ColorModulator;
}
