package com.shadowclangaming.client;

import com.shadowclangaming.warhead.BlastMotif;

/**
 * The geometry of a warhead's effect, built as plain numbers.
 *
 * <p>This class knows how to turn one {@link BlastMotif.Spec} at one point in its life into triangles in
 * world space, and it knows nothing else: no Minecraft, no OpenGL, no camera. Vertices go into a
 * {@link Sink}, which the client renderer backs with a real vertex buffer. That split is deliberate - it
 * means the shape of every layer in the mod can be asserted by {@code tools/run_logic_harness.sh}
 * (radius, count, bounds, that nothing is drawn at zero alpha) without a game, which matters because the
 * last visual build was only ever checked by eye and the eye found it inadequate.
 *
 * <p>WHY GEOMETRY AND NOT A BILLBOARD. The rejected build drew one camera-facing quad per detonation,
 * which is why every warhead read as the same small glowing disc. Everything here is anchored to the
 * detonation point instead: shells that enclose the crater, rings lying on the ground, circles hanging in
 * the sky, pillars, arcs. Walk around one and it changes shape the way a real object does.
 *
 * <p>All coordinates are world coordinates. The caller is responsible for the camera transform.
 */
public final class BlastGeometry {

    /** Where a vertex goes. The client implements this with a {@code BufferBuilder}. */
    public interface Sink {
        void vertex(float x, float y, float z, int r, int g, int b, int a);
    }

    /** Latitude/longitude resolution of a {@code CORE_SPHERE}: 16 x 10 quads, 640 vertices. */
    public static final int SPHERE_SEGMENTS_U = 16;
    public static final int SPHERE_SEGMENTS_V = 10;
    /** Segments in a ring. Enough to read as a circle, cheap enough to draw several per effect. */
    public static final int RING_SEGMENTS = 48;
    /** Segments in a wireframe sphere's circles. */
    public static final int WIRE_SEGMENTS = 32;

    private BlastGeometry() {
    }

    // ------------------------------------------------------------------ envelopes

    /**
     * How visible a layer is at a point in its own window: a quick fade in, a long fade out.
     *
     * A layer that appeared at full strength would strobe; one that vanished instantly would look like a
     * bug. This is the same curve every layer uses, so an effect reads as one thing rather than a stack of
     * unrelated shapes blinking.
     */
    public static float envelope(float local) {
        float t = clamp01(local);
        float in = smooth(t / 0.12F);
        float out = 1.0F - smooth((t - 0.55F) / 0.45F);
        return clamp01(in * out);
    }

    /** Current size of a layer, in blocks, at a point in its own window. */
    public static double sizeAt(BlastMotif.Spec spec, float progress, double radius) {
        float local = spec.local(progress);
        // The sphere and the shockwave travel; the circles, beams and rings hold their size and only fade.
        boolean grows = spec.layer() == BlastMotif.Layer.CORE_SPHERE
                || spec.layer() == BlastMotif.Layer.SHOCKWAVE
                || spec.layer() == BlastMotif.Layer.SCAN_SHELL;
        double fraction = grows ? 0.12D + 0.88D * local : 1.0D;
        return Math.max(0.05D, radius * spec.scale() * fraction);
    }

    // ------------------------------------------------------------------ the layer dispatch

    /**
     * Build one layer of one effect.
     *
     * @param progress 0..1 through the whole effect's life
     * @param radius   the effect's own radius in blocks (the blast radius, roughly)
     * @param argb     the spec's colour, already combined with the effect's strength in its alpha
     * @param seed     stable per effect, so arcs and motes do not re-roll every frame
     * @return how many vertices were emitted; 0 when the layer is not on screen
     */
    public static int layer(Sink sink, BlastMotif.Spec spec, float progress,
                            double cx, double cy, double cz, double radius, int argb, int seed) {
        if (!spec.active(progress)) {
            return 0;
        }
        float local = spec.local(progress);
        float alpha = envelope(local) * ((argb >>> 24) & 0xFF) / 255.0F;
        if (alpha <= 0.01F) {
            return 0;
        }
        int colour = (argb & 0xFFFFFF) | ((int) (alpha * 255.0F) << 24);
        double size = sizeAt(spec, progress, radius);

        return switch (spec.layer()) {
            case CORE_SPHERE -> sphere(sink, cx, cy, cz, size, colour, SPHERE_SEGMENTS_U,
                    SPHERE_SEGMENTS_V, local * 0.6F);
            case SHOCKWAVE -> ring(sink, cx, cy + 0.2D, cz, size, 0.0D, 1.0D, 0.0D,
                    Math.max(0.35D, size * 0.06D), colour, 0.0D, RING_SEGMENTS);
            case GROUND_CIRCLE -> sigil(sink, cx, cy + 0.15D, cz, size, colour, spec.count(),
                    spec.spin() * ageOf(progress), 0.0D, 1.0D, 0.0D);
            case SKY_CIRCLE -> sigil(sink, cx, cy + Math.max(24.0D, radius * 0.55D), cz, size, colour,
                    spec.count(), spec.spin() * ageOf(progress), 0.0D, -1.0D, 0.0D);
            case BEAM -> cylinder(sink, cx, cy, cz, Math.max(0.4D, size * 0.30D),
                    Math.max(6.0D, radius * spec.count() / 100.0D), colour, 20, local);
            case ARC -> arcs(sink, cx, cy, cz, size, colour, spec.count(), seed);
            case EMBERS -> motes(sink, cx, cy, cz, size, colour, spec.count(), local, seed);
            case SHARDS -> motes(sink, cx, cy, cz, size, colour, spec.count(), 1.0F - local, seed ^ 0x5EED);
            case RINGS_STACK -> ringsStack(sink, cx, cy, cz, size, colour, spec.count(),
                    spec.spin() * ageOf(progress));
            case TENDRILS -> tendrils(sink, cx, cy, cz, size, colour, spec.count(),
                    spec.spin() * ageOf(progress), seed);
            case SCAN_SHELL -> scanShell(sink, cx, cy, cz, size, colour, spec.count());
            case GLYPH_RING -> glyphs(sink, cx, cy + 0.18D, cz, size, colour, spec.count());
        };
    }

    /**
     * A stand-in for "how many ticks has this effect been running", derived from progress so the builders
     * stay pure: rotation is a function of the effect's own life, not of the wall clock.
     */
    private static double ageOf(float progress) {
        return clamp01(progress) * 600.0D;
    }

    // ------------------------------------------------------------------ primitives

    /** A single quad. Returns 4. */
    public static int quad(Sink sink,
                           float ax, float ay, float az,
                           float bx, float by, float bz,
                           float cx, float cy, float cz,
                           float dx, float dy, float dz, int argb) {
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int a = (argb >>> 24) & 0xFF;
        sink.vertex(ax, ay, az, r, g, b, a);
        sink.vertex(bx, by, bz, r, g, b, a);
        sink.vertex(cx, cy, cz, r, g, b, a);
        sink.vertex(dx, dy, dz, r, g, b, a);
        return 4;
    }

    /**
     * A flat circle of quads - the primitive behind shockwaves, ground sigils and sky circles.
     *
     * @param nx,ny,nz the plane's normal; the circle lies in the plane perpendicular to it
     */
    public static int ring(Sink sink, double cx, double cy, double cz, double radius,
                           double nx, double ny, double nz, double thickness, int argb,
                           double phase, int segments) {
        double[] basis = basis(nx, ny, nz);
        double ux = basis[0], uy = basis[1], uz = basis[2];
        double vx = basis[3], vy = basis[4], vz = basis[5];
        int segs = Math.max(6, segments);
        double inner = Math.max(0.05D, radius - thickness * 0.5D);
        double outer = radius + thickness * 0.5D;
        int emitted = 0;
        for (int i = 0; i < segs; i++) {
            double a0 = phase + Math.PI * 2.0D * i / segs;
            double a1 = phase + Math.PI * 2.0D * (i + 1) / segs;
            double c0 = Math.cos(a0), s0 = Math.sin(a0);
            double c1 = Math.cos(a1), s1 = Math.sin(a1);
            emitted += quad(sink,
                    (float) (cx + ux * c0 * inner + vx * s0 * inner),
                    (float) (cy + uy * c0 * inner + vy * s0 * inner),
                    (float) (cz + uz * c0 * inner + vz * s0 * inner),
                    (float) (cx + ux * c1 * inner + vx * s1 * inner),
                    (float) (cy + uy * c1 * inner + vy * s1 * inner),
                    (float) (cz + uz * c1 * inner + vz * s1 * inner),
                    (float) (cx + ux * c1 * outer + vx * s1 * outer),
                    (float) (cy + uy * c1 * outer + vy * s1 * outer),
                    (float) (cz + uz * c1 * outer + vz * s1 * outer),
                    (float) (cx + ux * c0 * outer + vx * s0 * outer),
                    (float) (cy + uy * c0 * outer + vy * s0 * outer),
                    (float) (cz + uz * c0 * outer + vz * s0 * outer),
                    argb);
        }
        return emitted;
    }

    /** A UV sphere shell: the fireball proper. Returns the vertex count. */
    public static int sphere(Sink sink, double cx, double cy, double cz, double radius, int argb,
                             int segmentsU, int segmentsV, double phase) {
        int u = Math.max(6, segmentsU);
        int v = Math.max(3, segmentsV);
        int emitted = 0;
        for (int j = 0; j < v; j++) {
            double phi0 = Math.PI * j / v;
            double phi1 = Math.PI * (j + 1) / v;
            for (int i = 0; i < u; i++) {
                double th0 = phase + Math.PI * 2.0D * i / u;
                double th1 = phase + Math.PI * 2.0D * (i + 1) / u;
                double[] a = onSphere(cx, cy, cz, radius, th0, phi0);
                double[] b = onSphere(cx, cy, cz, radius, th1, phi0);
                double[] c = onSphere(cx, cy, cz, radius, th1, phi1);
                double[] d = onSphere(cx, cy, cz, radius, th0, phi1);
                emitted += quad(sink,
                        (float) a[0], (float) a[1], (float) a[2],
                        (float) b[0], (float) b[1], (float) b[2],
                        (float) c[0], (float) c[1], (float) c[2],
                        (float) d[0], (float) d[1], (float) d[2], argb);
            }
        }
        return emitted;
    }

    /**
     * A wireframe sphere: latitude circles plus meridians. The EMP front and the constructor's cage.
     */
    public static int scanShell(Sink sink, double cx, double cy, double cz, double radius, int argb,
                                int meridians) {
        int emitted = 0;
        int latitudes = 4;
        for (int j = 1; j <= latitudes; j++) {
            double phi = Math.PI * j / (latitudes + 1);
            double ringRadius = radius * Math.sin(phi);
            double y = cy + radius * Math.cos(phi);
            emitted += ring(sink, cx, y, cz, ringRadius, 0.0D, 1.0D, 0.0D,
                    Math.max(0.2D, radius * 0.02D), argb, 0.0D, WIRE_SEGMENTS);
        }
        int count = Math.max(3, meridians);
        for (int m = 0; m < count; m++) {
            double theta = Math.PI * m / count;
            double nx = Math.cos(theta), nz = Math.sin(theta);
            emitted += ring(sink, cx, cy, cz, radius, nx, 0.0D, nz,
                    Math.max(0.2D, radius * 0.02D), argb, 0.0D, WIRE_SEGMENTS);
        }
        return emitted;
    }

    /** A vertical pillar of light, open at both ends. {@code height} is how tall, in blocks. */
    public static int cylinder(Sink sink, double cx, double cy, double cz, double radius, double height,
                               int argb, int segments, float local) {
        int segs = Math.max(6, segments);
        double bottom = cy - height * 0.25D;
        double top = cy + height * (0.35D + 0.40D * clamp01(local));
        int emitted = 0;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2.0D * i / segs;
            double a1 = Math.PI * 2.0D * (i + 1) / segs;
            double x0 = cx + Math.cos(a0) * radius, z0 = cz + Math.sin(a0) * radius;
            double x1 = cx + Math.cos(a1) * radius, z1 = cz + Math.sin(a1) * radius;
            emitted += quad(sink,
                    (float) x0, (float) bottom, (float) z0,
                    (float) x1, (float) bottom, (float) z1,
                    (float) x1, (float) top, (float) z1,
                    (float) x0, (float) top, (float) z0, argb);
        }
        return emitted;
    }

    /**
     * A magic circle: an outer ring, an inner ring, and radial strokes between them.
     *
     * This is the shape that makes the Soul Furnace's sky read as sorcery rather than as a disc, and it is
     * cheap: three rings and {@code strokes} quads.
     */
    public static int sigil(Sink sink, double cx, double cy, double cz, double radius, int argb,
                            int strokes, double phase, double nx, double ny, double nz) {
        double thickness = Math.max(0.25D, radius * 0.035D);
        int emitted = ring(sink, cx, cy, cz, radius, nx, ny, nz, thickness, argb, phase, RING_SEGMENTS);
        emitted += ring(sink, cx, cy, cz, radius * 0.72D, nx, ny, nz, thickness * 0.7D, argb,
                -phase * 1.5D, RING_SEGMENTS);
        double[] basis = basis(nx, ny, nz);
        double ux = basis[0], uy = basis[1], uz = basis[2];
        double vx = basis[3], vy = basis[4], vz = basis[5];
        int count = Math.max(3, strokes);
        for (int i = 0; i < count; i++) {
            double angle = phase + Math.PI * 2.0D * i / count;
            double c = Math.cos(angle), s = Math.sin(angle);
            double r0 = radius * 0.72D;
            double r1 = radius * (i % 3 == 0 ? 1.0D : 0.90D);
            double w = thickness * 0.6D;
            // A stroke is a quad from the inner ring to the outer, widened perpendicular to its length.
            double px = -s * w, pz = c * w;
            emitted += quad(sink,
                    (float) (cx + ux * c * r0 + vx * s * r0 - px),
                    (float) (cy + uy * c * r0 + vy * s * r0),
                    (float) (cz + uz * c * r0 + vz * s * r0 - pz),
                    (float) (cx + ux * c * r0 + vx * s * r0 + px),
                    (float) (cy + uy * c * r0 + vy * s * r0),
                    (float) (cz + uz * c * r0 + vz * s * r0 + pz),
                    (float) (cx + ux * c * r1 + vx * s * r1 + px),
                    (float) (cy + uy * c * r1 + vy * s * r1),
                    (float) (cz + uz * c * r1 + vz * s * r1 + pz),
                    (float) (cx + ux * c * r1 + vx * s * r1 - px),
                    (float) (cy + uy * c * r1 + vy * s * r1),
                    (float) (cz + uz * c * r1 + vz * s * r1 - pz),
                    argb);
        }
        return emitted;
    }

    /** Rune strokes only: no rings. Used where the glyph itself is the point. */
    public static int glyphs(Sink sink, double cx, double cy, double cz, double radius, int argb,
                             int strokes) {
        int emitted = ring(sink, cx, cy, cz, radius, 0.0D, 1.0D, 0.0D,
                Math.max(0.2D, radius * 0.025D), argb, 0.0D, RING_SEGMENTS);
        int count = Math.max(4, strokes);
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0D * i / count;
            double c = Math.cos(angle), s = Math.sin(angle);
            double r0 = radius * (0.35D + (i % 4) * 0.12D);
            double r1 = radius;
            double w = Math.max(0.2D, radius * 0.03D);
            emitted += quad(sink,
                    (float) (cx + c * r0 - s * w), (float) cy, (float) (cz + s * r0 + c * w),
                    (float) (cx + c * r0 + s * w), (float) cy, (float) (cz + s * r0 - c * w),
                    (float) (cx + c * r1 + s * w), (float) cy, (float) (cz + s * r1 - c * w),
                    (float) (cx + c * r1 - s * w), (float) cy, (float) (cz + s * r1 + c * w),
                    argb);
        }
        return emitted;
    }

    /**
     * A ladder of concentric, tilted rings: the Spacetime Hammer's five shockwaves, the Bulwark's cage,
     * Absolute Annihilation's seven.
     */
    public static int ringsStack(Sink sink, double cx, double cy, double cz, double radius, int argb,
                                 int count, double phase) {
        int total = Math.max(1, count);
        int emitted = 0;
        for (int i = 0; i < total; i++) {
            double fraction = (i + 1) / (double) total;
            double tilt = Math.PI * 0.5D - (i % 3 - 1) * 0.22D;
            double nx = Math.sin(tilt) * Math.cos(phase + i);
            double ny = Math.cos(tilt);
            double nz = Math.sin(tilt) * Math.sin(phase + i);
            emitted += ring(sink, cx, cy + radius * 0.15D * (i - total * 0.5D) / total, cz,
                    radius * fraction, nx, ny, nz, Math.max(0.25D, radius * 0.03D), argb,
                    phase * (i % 2 == 0 ? 1.0D : -1.0D), RING_SEGMENTS);
        }
        return emitted;
    }

    /**
     * Crackling arcs: jagged polylines that start at the core and jump outward and upward.
     *
     * Deterministic per {@code seed} and per arc index, with the seed mixed with the effect's age bracket
     * by the caller, so an arc crackles between frames instead of holding one shape or strobing.
     */
    public static int arcs(Sink sink, double cx, double cy, double cz, double radius, int argb,
                           int count, int seed) {
        int total = Math.max(1, count);
        int emitted = 0;
        for (int i = 0; i < total; i++) {
            double angle = Math.PI * 2.0D * i / total + hash01(seed + i) * 0.4D;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            int steps = 6;
            double px = cx, py = cy, pz = cz;
            double thickness = Math.max(0.25D, radius * 0.045D);
            for (int s = 1; s <= steps; s++) {
                double fraction = s / (double) steps;
                double jitter = (hash01(seed * 31 + i * 17 + s) - 0.5D) * radius * 0.28D;
                double nx = cx + dx * radius * fraction + jitter * 0.4D;
                double ny = cy + radius * fraction * 0.85D
                        + (hash01(seed * 7 + i + s * 3) - 0.5D) * radius * 0.30D;
                double nz = cz + dz * radius * fraction - jitter * 0.4D;
                emitted += segment(sink, px, py, pz, nx, ny, nz, thickness, argb);
                px = nx;
                py = ny;
                pz = nz;
            }
        }
        return emitted;
    }

    /**
     * Spiralling tendrils that crawl outward along the ground: the Abyssal Covenant, the World Eater's
     * accretion, the plague's spread.
     */
    public static int tendrils(Sink sink, double cx, double cy, double cz, double radius, int argb,
                               int count, double phase, int seed) {
        int total = Math.max(1, count);
        int emitted = 0;
        double thickness = Math.max(0.3D, radius * 0.05D);
        for (int i = 0; i < total; i++) {
            double base = Math.PI * 2.0D * i / total + phase;
            int steps = 7;
            double px = cx, py = cy + 0.25D, pz = cz;
            for (int s = 1; s <= steps; s++) {
                double fraction = s / (double) steps;
                double angle = base + fraction * 2.2D + hash01(seed + i) * 0.6D;
                double distance = radius * fraction;
                double nx = cx + Math.cos(angle) * distance;
                double nz = cz + Math.sin(angle) * distance;
                double ny = cy + 0.25D + Math.sin(fraction * Math.PI) * radius * 0.18D;
                emitted += segment(sink, px, py, pz, nx, ny, nz, thickness * (1.0D - fraction * 0.5D),
                        argb);
                px = nx;
                py = ny;
                pz = nz;
            }
        }
        return emitted;
    }

    /**
     * Glowing motes: embers rising, shards orbiting, feathers, fallout.
     *
     * Each mote is a cross of two quads rather than a billboard, because a billboard needs the camera and
     * this class deliberately does not have one - and a cross is visible from every angle anyway.
     *
     * @param rise 0 at the bottom of its travel, 1 at the top
     */
    public static int motes(Sink sink, double cx, double cy, double cz, double radius, int argb,
                            int count, float rise, int seed) {
        int total = Math.max(1, count);
        int emitted = 0;
        float up = clamp01(rise);
        for (int i = 0; i < total; i++) {
            double angle = Math.PI * 2.0D * i / total + hash01(seed + i * 5) * 1.2D;
            double distance = radius * (0.15D + hash01(seed + i * 11) * 0.95D);
            double x = cx + Math.cos(angle) * distance;
            double z = cz + Math.sin(angle) * distance;
            double y = cy + 0.5D + up * radius * (0.6D + hash01(seed + i * 3) * 1.4D);
            double size = Math.max(0.2D, radius * 0.035D);
            emitted += crossSprite(sink, (float) x, (float) y, (float) z, (float) size, argb);
        }
        return emitted;
    }

    /** Two perpendicular quads: a dot that is visible from any direction. */
    public static int crossSprite(Sink sink, float x, float y, float z, float size, int argb) {
        int emitted = quad(sink,
                x - size, y - size, z,
                x + size, y - size, z,
                x + size, y + size, z,
                x - size, y + size, z, argb);
        emitted += quad(sink,
                x, y - size, z - size,
                x, y - size, z + size,
                x, y + size, z + size,
                x, y + size, z - size, argb);
        return emitted;
    }

    /** One thick line segment, widened perpendicular to its length in the plane containing world up. */
    public static int segment(Sink sink, double x0, double y0, double z0,
                              double x1, double y1, double z1, double thickness, int argb) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4D) {
            return 0;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        // side = cross(dir, up) = (dz, 0, -dx): a horizontal widening, which is what a bolt or a tendril
        // needs to stay readable from any camera angle. A perfectly vertical segment degenerates, so fall
        // back to world east.
        double sx = dz;
        double sz = -dx;
        double sideLength = Math.sqrt(sx * sx + sz * sz);
        if (sideLength < 1.0E-4D) {
            sx = 1.0D;
            sz = 0.0D;
            sideLength = 1.0D;
        }
        sx /= sideLength;
        sz /= sideLength;
        double h = thickness * 0.5D;
        return quad(sink,
                (float) (x0 - sx * h), (float) (y0 - h * 0.5D), (float) (z0 - sz * h),
                (float) (x1 - sx * h), (float) (y1 - h * 0.5D), (float) (z1 - sz * h),
                (float) (x1 + sx * h), (float) (y1 + h * 0.5D), (float) (z1 + sz * h),
                (float) (x0 + sx * h), (float) (y0 + h * 0.5D), (float) (z0 + sz * h),
                argb);
    }


    // ------------------------------------------------------------------ maths helpers

    /** Point on a sphere, spherical coordinates. */
    private static double[] onSphere(double cx, double cy, double cz, double radius,
                                     double theta, double phi) {
        double sinPhi = Math.sin(phi);
        return new double[]{
                cx + radius * sinPhi * Math.cos(theta),
                cy + radius * Math.cos(phi),
                cz + radius * sinPhi * Math.sin(theta)};
    }

    /**
     * Two unit vectors spanning the plane perpendicular to {@code (nx, ny, nz)}, as a six-element array.
     */
    public static double[] basis(double nx, double ny, double nz) {
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double ax = nx, ay = ny, az = nz;
        if (length < 1.0E-6D) {
            ax = 0.0D;
            ay = 1.0D;
            az = 0.0D;
            length = 1.0D;
        }
        ax /= length;
        ay /= length;
        az /= length;
        // Pick whichever world axis is least aligned with the normal to build the first tangent.
        double ox, oy, oz;
        if (Math.abs(ay) < 0.9D) {
            ox = 0.0D;
            oy = 1.0D;
            oz = 0.0D;
        } else {
            ox = 1.0D;
            oy = 0.0D;
            oz = 0.0D;
        }
        // u = normalize(cross(n, o))
        double ux = ay * oz - az * oy;
        double uy = az * ox - ax * oz;
        double uz = ax * oy - ay * ox;
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1.0E-6D) {
            ux = 1.0D;
            uy = 0.0D;
            uz = 0.0D;
            ul = 1.0D;
        }
        ux /= ul;
        uy /= ul;
        uz /= ul;
        // v = cross(n, u)
        double vx = ay * uz - az * uy;
        double vy = az * ux - ax * uz;
        double vz = ax * uy - ay * ux;
        return new double[]{ux, uy, uz, vx, vy, vz};
    }

    /** Deterministic 0..1 noise from an integer, so a shape is stable across frames. */
    public static double hash01(int seed) {
        int h = seed * 0x9E3779B1;
        h ^= (h >>> 15);
        h *= 0x85EBCA77;
        h ^= (h >>> 13);
        return (h & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }

    /** Clamped smoothstep, written out so the curve is obvious. */
    public static float smooth(float t) {
        float c = clamp01(t);
        return c * c * (3.0F - 2.0F * c);
    }

    public static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
