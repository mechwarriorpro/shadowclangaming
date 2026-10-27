#version 150
// Volumetric warhead effect, fragment half.
//
// A cube the size of the blast is drawn around the detonation and this marches a procedural density field
// through it - no textures, no samplers, no billboards. The fireball is a function, evaluated per pixel.
//
// THE FIELD IS A COMPOSITION, NOT A PRESET. Twenty-two structural features - hole, star, rift,
// shockwave shells, meteors, lightning, runes, sky rings, portals, jet, coins, smoke, sub-charges,
// flash, dome, embers, mushroom cap, debris, dust, vortex, ground cracks, sparks and halo - are each
// weighted 0..1 and summed. Every warhead in BlastMotif.Volume authors its own weights from its own
// description, so a World Eater is a black hole with an accretion ring, a Quasar Lance is a kilometre of
// jet, a Ragnarok is meteors and lightning under a blood-red sky, and a Numismatic blast is made of
// tumbling gold. One code path, thirty-eight compositions, nothing in here branches per warhead.
//
// Depth-tested against the terrain with no depth write, so a hill occludes it and a doorway frames it.
// If this program will not compile on a driver, client/VolumetricFx falls back to CPU triangles and then
// to particles - the warhead still goes off looking like itself.

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
// The fifteen structural features. See BlastMotif.Volume for who asks for what.
uniform float Hole;
uniform float Star;
uniform float Rift;
uniform float Waves;
uniform float Meteors;
uniform float Lightning;
uniform float Glyphs;
uniform float Portals;
uniform float Jet;
uniform float Coins;
uniform float Smoke;
uniform float SubCharges;
uniform float Flash;
uniform float Dome;
uniform float Embers;
// Round 49: seven more, so the ground and the air around the blast are part of the effect too.
uniform float Cap;
uniform float Debris;
uniform float Dust;
uniform float Vortex;
uniform float Cracks;
uniform float Sparks;
uniform float Halo;
// The effect's own fade, 1.0 for most of its life and then down to nothing. It has to scale the
// DENSITY and not just the emission: with premultiplied alpha the opacity comes from how much was
// accumulated, so dimming the colour alone would leave a solid black blob hanging in the world
// after the blast had finished burning.
uniform float Strength;

out vec4 fragColor;

// Half-extent of the proxy cube, in blast radii. client/VolumetricFx draws a cube this size, so the two
// must stay in step: the shader marches exactly the volume the geometry covers, no more and no less.
const float PROXY_BOX = 3.4;

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

// Ridged noise: the sharp crease that reads as a crack, a bolt or a tear.
float ridge(vec3 p) {
    return 1.0 - abs(fbm(p) * 2.0 - 1.0);
}

// ---------------------------------------------------------------- the field

float sampleField(vec3 wp, out vec3 emission) {
    float radius = max(Radius, 0.5);
    float t = clamp(Progress, 0.0, 1.0);
    bool charging = Phase < 0.5;

    // How big it is this instant. A charge swells towards full size and holds; a detonation snaps out,
    // overshoots, then blows apart and fades.
    float grow = charging
            ? mix(0.16, 1.0, smoothstep(0.0, 0.9, t))
            : mix(0.3, 1.45, smoothstep(0.0, 0.45, t));
    float fade = charging ? mix(0.6, 1.0, t) : 1.0 - smoothstep(0.45, 1.0, t);

    vec3 q = (wp - Center) / (radius * max(grow, 0.05));
    float r = length(q);

    // Motion: shear around Y, scroll upward, and pull the field inward while charging so the whole thing
    // visibly winds up before it lets go.
    float ang = Time * (0.3 + Swirl * 0.9) - Implode * t * 1.6;
    float ca = cos(ang);
    float sa = sin(ang);
    vec3 p = vec3(q.x * ca - q.z * sa, q.y, q.x * sa + q.z * ca);
    p.y -= Time * Rise * 0.8;
    p *= 1.0 + Implode * 0.3 * (charging ? 1.0 - t : t);

    vec3 seedOffset = vec3(Seed * 97.0, Seed * 41.0, Seed * 63.0);
    float wx = fbm(p * 1.7 + vec3(11.3, 5.1, 3.7) + seedOffset);
    float wz = fbm(p * 1.7 + vec3(31.7, 7.9, 13.1) + seedOffset);
    vec3 warped = p + vec3(wx - 0.5, 0.0, wz - 0.5) * (0.9 + Turb);
    float n = fbm((warped + seedOffset * 0.35) * (1.5 + Turb * 1.3)) * 2.0 - 1.0;
    float rr = length(q.xz);
    float body = 0.0;
    float heat = clamp(1.0 - r * 0.8 + n * 0.35, 0.0, 1.0);
    float bright = 1.0;

    // --- fireball: the turbulent body. Dense all the way through, with only the SURFACE noisy, so the
    // blast occludes what is behind it. It used to carve the interior out as the effect aged, which left
    // a hollow bubble - and a hollow bubble under an additive blend is exactly "transparent".
    float surface = r + n * 0.42 * Turb;
    float inside = 1.0 - smoothstep(0.58, 1.0, surface);
    float skirt = (1.0 - smoothstep(0.72, 1.55, surface)) * 0.6;
    // Late in the detonation the ball breaks apart rather than hollowing out.
    float breakup = charging ? 0.0 : smoothstep(0.55, 1.0, t) * 0.55;
    body += (1.0 - Smoke) * (inside * 2.2 + skirt) * (1.0 - breakup);

    // --- smoke: heavy, dark, low. What a dud or a plague leaves instead of a fireball.
    if (Smoke > 0.01) {
        float low = 1.0 - smoothstep(-0.9, 1.4, q.y);
        float puff = fbm(p * (1.1 + Turb) + seedOffset * 0.5 + vec3(0.0, Time * 0.25, 0.0));
        body += Smoke * clamp((1.0 - smoothstep(0.4, 1.5, r)) * (0.5 + puff) * low, 0.0, 1.2);
        bright *= mix(1.0, 0.16, Smoke);
    }

    // --- shockwave shells: several, staggered, racing outward
    if (Waves > 0.01 || Ring > 0.01) {
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float speed = 0.34 + fi * 0.22;
            float waveR = charging
                    ? mix(0.1 + fi * 0.12, 0.85 + fi * 0.15, t)
                    : mix(0.15, 0.5 + speed * 3.4, smoothstep(0.0, 0.4 + fi * 0.2, t));
            float w = charging ? 0.2 : mix(0.22, 0.035, smoothstep(0.0, 0.7, t));
            float amount = (i == 0 ? max(Waves, Ring) : Waves * (0.8 - fi * 0.25));
            if (amount > 0.01) {
                float crest = 1.0 - smoothstep(0.0, w, abs(rr - waveR));
                float flat = 1.0 - smoothstep(0.0, 0.16 + fi * 0.06, abs(q.y + 0.9));
                body += amount * crest * flat * (0.55 + 0.45 * n) * (1.0 - fi * 0.22);
            }
        }
    }

    // --- black hole: a core that eats light, with an accretion ring and a hot rim
    if (Hole > 0.01) {
        float event = 1.0 - smoothstep(0.0, 0.34, r);
        float disc = (1.0 - smoothstep(0.0, 0.1, abs(q.y))) * (1.0 - smoothstep(0.36, 1.15, rr));
        float spinDisc = 0.6 + 0.4 * sin(atan(q.z, q.x) * 3.0 - Time * 3.0 + n * 3.0);
        float rim = (1.0 - smoothstep(0.0, 0.07, abs(r - 0.38)));
        body += Hole * (disc * spinDisc * 1.3 + rim * 0.9);
        // The hole itself removes what is behind it.
        body = max(body, 0.0) * (1.0 - Hole * event * 0.92);
        bright *= mix(1.0, 2.4, Hole * rim);
        bright *= mix(1.0, 0.1, Hole * event);
    }

    // --- star: a blinding core with a spiky corona
    if (Star > 0.01) {
        float core = 1.0 - smoothstep(0.0, 0.3, r);
        float spikes = pow(max(0.0, fbm(normalize(q + vec3(0.001)) * 5.0 + seedOffset
                             + vec3(0.0, Time * 0.4, 0.0))), 3.0);
        float corona = (1.0 - smoothstep(0.2, 1.3, r)) * (0.35 + 0.9 * spikes);
        body += Star * (core * 1.6 + corona);
        bright *= mix(1.0, 3.2, Star * core);
        bright *= mix(1.0, 1.8, Star * corona);
    }

    // --- flash: one hard white frame at the start of the detonation
    if (Flash > 0.01 && !charging) {
        float kick = 1.0 - smoothstep(0.0, 0.16, t);
        body += Flash * kick * (1.0 - smoothstep(0.0, 1.7, r)) * 2.2;
        bright *= mix(1.0, 4.0, Flash * kick);
        heat = mix(heat, 1.0, Flash * kick);
    }

    // --- rift: a jagged tear in the world, bright at the edges
    if (Rift > 0.01) {
        float tear = abs(q.x) + ridge(vec3(q.y * 2.2, q.z * 2.2, Time * 0.6 + Seed * 30.0)) * 0.42 - 0.34;
        float open = 1.0 - smoothstep(0.0, 0.16, abs(tear));
        float edge = (1.0 - smoothstep(0.0, 0.09, abs(tear))) * (1.0 - smoothstep(0.05, 0.16, abs(tear)));
        float tall = 1.0 - smoothstep(0.9, 2.1, abs(q.y));
        body += Rift * (open * 0.75 + edge * 1.5) * tall;
        bright *= mix(1.0, 2.6, Rift * edge);
    }

    // --- meteors: streaks falling in from above
    if (Meteors > 0.01) {
        for (int i = 0; i < 5; i++) {
            float fi = float(i);
            float hx = hash31(vec3(fi * 3.1 + Seed * 7.0, 1.7, 5.3)) * 2.0 - 1.0;
            float hz = hash31(vec3(fi * 5.7 + Seed * 3.0, 9.1, 2.2)) * 2.0 - 1.0;
            float fall = fract(Time * 0.35 + fi * 0.19 + Seed);
            float my = mix(2.6, -0.7, fall);
            vec2 d = q.xz - vec2(hx, hz) * 1.5;
            float lateral = 1.0 - smoothstep(0.0, 0.07, length(d));
            float trail = (1.0 - smoothstep(0.0, 0.9, max(0.0, q.y - my))) * step(my, q.y);
            float head = 1.0 - smoothstep(0.0, 0.16, length(q - vec3(hx * 1.5, my, hz * 1.5)));
            body += Meteors * (lateral * trail * 0.9 + head * 1.4);
            bright *= mix(1.0, 2.2, Meteors * head);
        }
    }

    // --- lightning: crackling bolts radiating from the centre
    if (Lightning > 0.01) {
        float crack = ridge(p * (2.4 + Turb) + vec3(0.0, Time * 2.2, 0.0) + seedOffset);
        float bolt = pow(max(0.0, crack), 14.0);
        float reach = 1.0 - smoothstep(0.25, 1.7, r);
        float flicker = 0.55 + 0.45 * sin(Time * 23.0 + Seed * 40.0);
        body += Lightning * bolt * reach * flicker * 3.0;
        bright *= mix(1.0, 3.0, Lightning * bolt * reach);
    }

    // --- runes: strokes cut into the ground and circles turning overhead
    if (Glyphs > 0.01) {
        float spokes = pow(max(0.0, sin(atan(q.z, q.x) * 12.0 + Time * 0.5)), 22.0);
        float ground = (1.0 - smoothstep(0.0, 0.1, abs(q.y + 0.92)))
                     * (1.0 - smoothstep(0.35, 1.25, rr));
        float bands = (1.0 - smoothstep(0.0, 0.05, abs(fract(rr * 4.0 - Time * 0.3) - 0.5)));
        float sky = (1.0 - smoothstep(0.0, 0.08, abs(q.y - 1.55)))
                  * (1.0 - smoothstep(0.5, 1.35, rr));
        body += Glyphs * (ground * (spokes * 1.2 + bands * 0.8) * 1.5 + sky * 1.1);
        bright *= mix(1.0, 2.0, Glyphs * sky);
    }

    // --- sky circles: rings turning overhead. Runes ask for them, and so does a Soul Furnace, which is
    // meant to read as giant red magic circles hanging over the field.
    if (SkyRing > 0.01) {
        float height = 1.5 + SkyRing * 0.3;
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float ringR = 0.6 + fi * 0.35;
            float plane = 1.0 - smoothstep(0.0, 0.05 + fi * 0.02, abs(q.y - (height + fi * 0.18)));
            float band = (1.0 - smoothstep(0.0, 0.045, abs(rr - ringR)))
                       + (1.0 - smoothstep(0.0, 0.03, abs(fract(rr * 6.0 - Time * 0.5 - fi * 0.33) - 0.5)));
            float turn = 0.7 + 0.3 * sin(atan(q.z, q.x) * (8.0 + fi * 4.0) - Time * (0.8 + fi * 0.4));
            body += SkyRing * plane * band * turn * (1.1 - fi * 0.25);
        }
        bright *= mix(1.0, 1.9, SkyRing * 0.8);
    }

    // --- streaks: radial filaments spidering out from the core
    if (Streaks > 0.01) {
        float spokes = pow(max(0.0, sin(atan(q.z, q.x) * 30.0 + Seed * 60.0 + q.y * 3.0)), 12.0);
        float reach = (1.0 - smoothstep(0.05, 1.4, r)) * smoothstep(0.0, 0.25, r);
        body += Streaks * spokes * reach * 1.8;
        bright *= mix(1.0, 2.2, Streaks * spokes * reach);
    }

    // --- portals: scattered dark discs with bright rims
    if (Portals > 0.01) {
        for (int i = 0; i < 6; i++) {
            float fi = float(i);
            vec3 at = vec3(hash31(vec3(fi, Seed * 11.0, 1.0)), hash31(vec3(fi, Seed * 13.0, 2.0)),
                           hash31(vec3(fi, Seed * 17.0, 3.0))) * 2.4 - 1.2;
            float d = length(q - at);
            float disc = 1.0 - smoothstep(0.0, 0.17, d);
            float rim = (1.0 - smoothstep(0.0, 0.05, abs(d - 0.19)));
            float pulse = 0.6 + 0.4 * sin(Time * 2.4 + fi * 1.7);
            body += Portals * (rim * 1.3 * pulse - disc * 0.7);
            bright *= mix(1.0, 2.0, Portals * rim);
        }
    }

    // --- jet: a very tall, very narrow pillar with a bow shock
    if (Jet > 0.01 || Beam > 0.01) {
        float amount = max(Jet, Beam * 0.8);
        float width = mix(0.26, 0.07, Jet);
        float height = mix(1.6, 2.7, Jet);
        float pillar = (1.0 - smoothstep(0.0, width, rr)) * (1.0 - smoothstep(0.0, height, abs(q.y - 0.6)));
        float bow = (1.0 - smoothstep(0.0, 0.1, abs(rr - 0.45))) * (1.0 - smoothstep(0.0, 0.2, abs(q.y - 1.9)));
        float flicker = 0.75 + 0.25 * sin(q.y * 9.0 - Time * 7.0);
        body += amount * (pillar * flicker * 1.5 + bow * 0.8);
        bright *= mix(1.0, 2.6, amount * pillar);
    }

    // --- coins: flat gold discs tumbling through the blast
    if (Coins > 0.01) {
        for (int i = 0; i < 7; i++) {
            float fi = float(i);
            float a = fi * 2.399 + Time * (0.6 + 0.2 * fi);
            float rad = 0.25 + 0.9 * hash31(vec3(fi, Seed * 5.0, 7.0));
            vec3 at = vec3(cos(a) * rad, (hash31(vec3(fi, 3.0, Seed)) - 0.3) * 1.6 + Time * 0.25,
                           sin(a) * rad);
            float d = length((q - at) * vec3(1.0, 3.2, 1.0));
            float coin = 1.0 - smoothstep(0.0, 0.13, d);
            body += Coins * coin * 1.5;
            bright *= mix(1.0, 2.4, Coins * coin);
        }
    }

    // --- sub-charges: the cluster's spread, each with its own clock
    if (SubCharges > 0.01) {
        for (int i = 0; i < 6; i++) {
            float fi = float(i);
            float a = fi * 1.047 + Seed * 6.0;
            float rad = 0.75 + 0.5 * hash31(vec3(fi, Seed * 3.0, 11.0));
            vec3 at = vec3(cos(a) * rad, (hash31(vec3(fi, 7.0, Seed)) - 0.5) * 0.7, sin(a) * rad);
            float own = fract(t * (1.0 + fi * 0.18) + fi * 0.11);
            float puff = 1.0 - smoothstep(0.0, 0.42 + 0.3 * own, length(q - at));
            body += SubCharges * puff * (1.2 - own) * 1.4;
            bright *= mix(1.0, 1.9, SubCharges * puff);
        }
    }

    // --- dome: a hollow shell. The Bulwark's barrier and the Constructor's cage.
    if (Dome > 0.01) {
        float skin = 1.0 - smoothstep(0.0, 0.06, abs(r - 1.05));
        float hexes = 0.55 + 0.45 * sin(rr * 16.0) * sin(q.y * 16.0 + Time * 0.8);
        float cap = step(-0.95, q.y);
        body += Dome * skin * hexes * cap * 1.3;
        bright *= mix(1.0, 1.6, Dome * skin);
    }

    // --- mushroom cloud: a stem with a churning cap that flattens as it climbs
    if (Cap > 0.01) {
        float climb = charging ? 0.1 : smoothstep(0.0, 0.5, t) * 1.5;
        float stem = (1.0 - smoothstep(0.0, 0.22, rr)) * (1.0 - smoothstep(0.0, climb, max(0.0, q.y)));
        float capR = 0.35 + 0.5 * smoothstep(0.1, 0.6, t);
        float cap = (1.0 - smoothstep(0.0, 0.24, abs(q.y - (climb + 0.15))))
                  * (1.0 - smoothstep(capR * 0.6, capR * 1.4, rr));
        float churn = 0.55 + 0.45 * fbm(p * 2.0 + vec3(0.0, Time * 0.4, 0.0) + seedOffset);
        body += Cap * (stem * 1.5 + cap * 1.8) * churn;
        bright *= mix(1.0, 1.4, Cap * cap);
    }

    // --- debris: shrapnel thrown outward, each piece with its own speed and a streak behind it
    if (Debris > 0.01 && !charging) {
        for (int i = 0; i < 8; i++) {
            float fi = float(i);
            vec3 dir = normalize(vec3(hash31(vec3(fi, 1.0, Seed)) - 0.5,
                                      hash31(vec3(fi, 2.0, Seed)) * 0.7,
                                      hash31(vec3(fi, 3.0, Seed)) - 0.5) + vec3(0.001));
            float speed = 0.8 + 0.9 * hash31(vec3(fi, 4.0, Seed));
            vec3 at = dir * (t * 1.9 * speed);
            float streak = (1.0 - smoothstep(0.0, 0.07, length(cross(q - at, dir))))
                         * (1.0 - smoothstep(0.0, 0.24, dot(at - q, dir)));
            float piece = 1.0 - smoothstep(0.0, 0.08, length(q - at));
            body += Debris * (streak * 1.3 + piece * 1.5);
            bright *= mix(1.0, 1.9, Debris * piece);
        }
    }

    // --- dust: a ground-hugging ring rolling outward, and dim - it is dirt, not fire
    if (Dust > 0.01) {
        float front = charging ? 0.2 : mix(0.2, 2.6, smoothstep(0.0, 0.75, t));
        float band = 1.0 - smoothstep(0.0, 0.35, abs(rr - front));
        float ground = 1.0 - smoothstep(0.0, 0.32, max(0.0, q.y + 0.95));
        float roll = 0.5 + 0.5 * fbm(p * 1.6 + vec3(Time * 0.5, 0.0, 0.0) + seedOffset);
        body += Dust * band * ground * roll * 1.5;
        bright *= mix(1.0, 0.45, Dust * band * ground);
    }

    // --- vortex: a twisting funnel, three arms winding as they rise
    if (Vortex > 0.01) {
        float wide = 0.12 + 0.5 * max(0.0, q.y + 0.9);
        float arms = pow(max(0.0, sin(atan(q.z, q.x) + q.y * 2.4 - Time * 2.2) * 3.0), 6.0);
        float funnel = (1.0 - smoothstep(0.0, wide, rr))
                     * (1.0 - smoothstep(0.0, 2.3, max(0.0, q.y + 0.9)));
        body += Vortex * funnel * (0.45 + arms) * 1.4;
        bright *= mix(1.0, 1.8, Vortex * arms * funnel);
    }

    // --- cracks: glowing veins shot through the ground, pulsing outward
    if (Cracks > 0.01) {
        float veins = pow(max(0.0, sin(atan(q.z, q.x) * 18.0 + Seed * 40.0)), 26.0);
        float reach = (1.0 - smoothstep(0.1, 1.9, rr)) * smoothstep(0.0, 0.18, rr);
        float ground = 1.0 - smoothstep(0.0, 0.09, abs(q.y + 0.95));
        float pulse = 0.6 + 0.4 * sin(rr * 6.0 - Time * 3.0);
        body += Cracks * veins * reach * ground * pulse * 2.2;
        bright *= mix(1.0, 2.8, Cracks * veins * reach * ground);
    }

    // --- sparks: scattered points of light filling the volume, twinkling out of step
    if (Sparks > 0.01) {
        vec3 cell = floor(q * 5.0);
        float h = hash31(cell + seedOffset);
        if (h > 0.80) {
            vec3 jitter = vec3(hash31(cell + 11.0), hash31(cell + 23.0), hash31(cell + 37.0)) - 0.5;
            float d = length(fract(q * 5.0) - 0.5 - jitter * 0.6);
            float twinkle = 0.5 + 0.5 * sin(Time * 9.0 + h * 60.0);
            body += Sparks * (1.0 - smoothstep(0.0, 0.09, d)) * twinkle * 2.0;
            bright *= mix(1.0, 2.4, Sparks * twinkle);
        }
    }

    // --- halo: a wide soft bloom outside the fireball. Cheap, and it is what makes a blast read as
    // enormous from a distance rather than as a ball of a known size.
    if (Halo > 0.01) {
        float bloom = 1.0 - smoothstep(0.5, 2.6, r);
        body += Halo * bloom * 0.4;
        bright *= mix(1.0, 1.5, Halo * bloom);
    }

    // --- embers: a rising column of fire
    if (Embers > 0.01 || Rise > 0.4) {
        float amount = max(Embers, (Rise - 0.4) * 1.2);
        float column = (1.0 - smoothstep(0.1, 0.85, rr)) * (1.0 - smoothstep(0.0, 2.2, max(0.0, q.y)));
        float flick = fbm(vec3(q.xz * 3.0, q.y * 1.5 - Time * 1.6) + seedOffset);
        body += amount * column * (0.35 + 0.85 * flick);
    }

    body *= fade * Strength;
    if (body <= 0.002) {
        emission = vec3(0.0);
        return 0.0;
    }

    vec3 col = mix(ColorC.rgb, ColorB.rgb, smoothstep(0.0, 0.55, heat));
    col = mix(col, ColorA.rgb, smoothstep(0.45, 1.0, heat));
    col *= mix(1.0, 0.2, Dark * (1.0 - heat));
    if (charging) {
        col *= 0.7 + 0.3 * sin(Time * 6.0 + r * 4.0);
    }
    emission = col * Glow * bright;
    return clamp(body, 0.0, 3.0);
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

    vec3 bmin = Center - vec3(boxRadius);
    vec3 bmax = Center + vec3(boxRadius);
    // Reciprocal, and never zero: sign() of an exactly axis-aligned ray returns 0, which would put an
    // infinity into the slab test and a NaN on screen for a camera looking straight down an axis.
    vec3 invRd = vec3((rd.x < 0.0 ? -1.0 : 1.0) / max(abs(rd.x), 1e-6),
                      (rd.y < 0.0 ? -1.0 : 1.0) / max(abs(rd.y), 1e-6),
                      (rd.z < 0.0 ? -1.0 : 1.0) / max(abs(rd.z), 1e-6));
    vec3 t1 = (bmin - ro) * invRd;
    vec3 t2 = (bmax - ro) * invRd;
    vec3 tmin = min(t1, t2);
    vec3 tmax = max(t1, t2);
    float tEnter = max(max(tmin.x, tmin.y), tmin.z);
    float tExit = min(min(tmax.x, tmax.y), tmax.z);
    tEnter = max(tEnter, 0.0);
    if (tExit <= tEnter) {
        discard;
    }

    int steps = int(clamp(Steps, 8.0, 128.0));
    float stepLen = (tExit - tEnter) / float(steps);
    vec3 pos = ro + rd * (tEnter + stepLen * 0.5);

    vec3 acc = vec3(0.0);
    float transmittance = 1.0;
    // Optical depth in units of the blast radius, so a grenade and a kilometre-wide cataclysm are equally
    // solid instead of one being fog and the other a brick.
    float densityScale = 2.2 / max(radius, 1.0);

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

    float opacity = 1.0 - transmittance;
    // Both tests, not just brightness: a dark warhead (a World Eater's core is deliberately black) can
    // have almost no emission and still be completely opaque, and discarding it would punch a hole in
    // the middle of the one effect that is supposed to hide the world behind it.
    if (opacity < 0.004 && dot(acc, acc) < 0.00002) {
        discard;
    }
    // Premultiplied alpha, and the opacity is the march's own transmittance rather than a constant 1.
    // It used to be vec4(acc, 1.0) under an additive blend, which is why the blast read as coloured
    // glass: SRC_ALPHA/ONE adds the effect ON TOP of whatever was already in the framebuffer and never
    // attenuates it, so the terrain showed through at full brightness no matter how dense the volume got.
    // With ONE/ONE_MINUS_SRC_ALPHA the core - where transmittance has fallen to nothing - genuinely
    // occludes the world behind it, while the thin outer skirt still glows additively over it.
    fragColor = vec4(acc, opacity) * ColorModulator;
}
