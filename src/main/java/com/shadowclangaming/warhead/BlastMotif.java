package com.shadowclangaming.warhead;

import java.util.List;

/**
 * The world-space visual signature of every warhead: what it draws, in what order, for how long.
 *
 * <p>WHY THIS CLASS EXISTS. For most of the project's life a detonation's look was decided by
 * {@link BlastStyle} - nine palettes shared by thirty-eight weapons - and the result was the same shape
 * with a different tint every time. The user's verdict on that, in game, was blunt: a small sphere with
 * sparkles. So the look is now a per-warhead *motif*: a list of world-space geometry layers
 * ({@link Layer}) with their own timing, scale, colour and spin, driven from the warhead's own identity
 * rather than from a shared style. Two weapons that share a palette no longer share a shape.
 *
 * <p>WORLD SPACE, NOT SCREEN SPACE. Every layer here is geometry anchored to the detonation point:
 * shells, rings on the ground, circles hanging in the sky, beams, arcs. None of it is a HUD wash or a
 * full-screen post pass - those were tried and rejected. The client reads this table and builds vertices
 * around the impact point (see {@code client/CinematicFx}); the post chains remain an optional extra.
 *
 * <p>DURATIONS ARE LONG AND THEY VARY. A motif owns {@code baseTicks}, and {@link Motif#durationTicks}
 * stretches it with the warhead's power up to {@link #MAX_TICKS} - one minute for the end of the ladder.
 * This same number is what {@link ExplosivePackaging} turns into the fuse for the block, grenade and
 * minecart forms, so a charge always burns for exactly as long as its own effect runs.
 *
 * <p>FINALES ARE NOT UNIFORM. {@link Motif#finaleScale()} is 0 for the warheads that are not bombs -
 * Bulwark raises a dome, Cargo lands a crate, Constructor builds, Spatial steals ground, Violator and
 * Beef deliver a creature. Those get a spectacle and no crater, and this table is where that distinction
 * is written down so a later round cannot "fix" it by adding a blast to everything.
 *
 * <p>No Minecraft imports, so {@code tools/run_logic_harness.sh} can assert the whole roster.
 */
public final class BlastMotif {

    /** Shortest effect any warhead can have. Nothing is a single-frame flicker. */
    public static final int MIN_TICKS = 40;
    /** Longest effect any warhead can have: one minute of world geometry. */
    public static final int MAX_TICKS = 1200;

    /**
     * The kinds of world-space geometry the cinematic renderer knows how to build.
     *
     * Kept to a fixed list on purpose: the renderer implements each one once, the table composes them,
     * and a new warhead needs a new row rather than a new shader.
     */
    public enum Layer {
        /** A shell of light that swells from the centre and fades: the fireball proper. */
        CORE_SPHERE,
        /** A thin, fast ring that races outward along the ground plane: the pressure wave. */
        SHOCKWAVE,
        /** A slowly turning circle lying flat on the terrain, with radial strokes: a ground sigil. */
        GROUND_CIRCLE,
        /** A giant circle hanging above the impact, facing down: the sky sigil. */
        SKY_CIRCLE,
        /** A vertical pillar of light through the impact point. */
        BEAM,
        /** Jagged crackling arcs: world-space lightning, drawn as thick polylines. */
        ARC,
        /** A rising column of glowing motes: embers, ash, feathers, fallout. */
        EMBERS,
        /** Rotating polyhedral shards around the core: crystal, debris, coin. */
        SHARDS,
        /** Several concentric, tilted rings: a cage, a resonance ladder, a spell array. */
        RINGS_STACK,
        /** Spiralling tendrils that crawl outward from the centre. */
        TENDRILS,
        /** A wireframe sphere that sweeps outward: a sensor wash, an EMP front, a scanning field. */
        SCAN_SHELL,
        /** A ring of rune strokes drawn around the impact: an engraved glyph. */
        GLYPH_RING
    }

    /**
     * The theme a motif belongs to. Descriptive rather than functional - it is what the debug log, the
     * {@code /scg fx} read-out and the docs use to name a warhead's look in one word.
     */
    public enum Theme {
        /** Conventional explosives: flash, shockwave, smoke. */
        ORDNANCE,
        /** Fire that keeps burning after the bang. */
        INFERNO,
        /** Fission/fusion: the mushroom, the fallout, the light. */
        NUCLEAR,
        /** Runes, spell circles, violet geometry. */
        ARCANE,
        /** Stars: coronae, jets, blinding white. */
        STELLAR,
        /** End-of-the-world staging: many rings, red sky, long build. */
        CATACLYSM,
        /** The light goes the wrong way. */
        VOID,
        /** Sickness made visible: crawling green. */
        PLAGUE,
        /** Everything stops. Pale blue, facets, stillness. */
        CRYO,
        /** Electric, precise, geometric. */
        MACHINE,
        /** Gold, because the blast is made of money. */
        COIN,
        /** A dome or a cage: geometry that holds rather than destroys. */
        BARRIER,
        /** Delivery, not destruction: crates, builds, mines. */
        LOGISTICS,
        /** Many small things at once. */
        SWARM,
        /** A creature arrives. */
        BEAST,
        /** The Soul Furnace: black-violet lightning under red sky circles. */
        SOUL,
        /** Deliberately understated: it was never supposed to go off. */
        SILENT
    }

    /**
     * One geometry layer of a motif.
     *
     * @param layer  which shape to build
     * @param from   0..1 - the point in the effect's life at which this layer appears
     * @param to     0..1 - the point at which it has fully finished; must be greater than {@code from}
     * @param scale  size as a fraction of the effect's own radius (1.0 = the blast radius)
     * @param argb   colour, alpha included; the renderer uses alpha as the layer's peak opacity
     * @param count  how many of them (rings in a stack, arcs, shards, rune strokes)
     * @param spin   radians per tick of rotation; negative spins the other way
     */
    public record Spec(Layer layer, double from, double to, double scale, int argb, int count, double spin) {

        /** Clamped, so a hand-written row can never make the renderer divide by zero or draw backwards. */
        Spec {
            from = clamp01(from);
            to = clamp01(to);
            if (to <= from) {
                to = Math.min(1.0D, from + 0.05D);
            }
            scale = Math.max(0.05D, scale);
            count = Math.max(1, count);
        }

        /** How far through this layer's own window {@code progress} is: 0 at {@code from}, 1 at {@code to}. */
        public float local(float progress) {
            if (progress <= from) {
                return 0.0F;
            }
            if (progress >= to) {
                return 1.0F;
            }
            return (float) ((progress - from) / (to - from));
        }

        /** Is this layer on screen at all at {@code progress}? */
        public boolean active(float progress) {
            return progress >= from && progress <= to;
        }
    }

    /** One warhead's complete signature. */
    public static final class Motif {

        private final String warheadId;
        private final Theme theme;
        private final List<Spec> specs;
        private final int baseTicks;
        private final float finaleScale;

        Motif(String warheadId, Theme theme, int baseTicks, float finaleScale, List<Spec> specs) {
            this.warheadId = warheadId;
            this.theme = theme;
            this.baseTicks = Math.max(MIN_TICKS, Math.min(MAX_TICKS, baseTicks));
            this.finaleScale = Math.max(0.0F, Math.min(2.0F, finaleScale));
            this.specs = List.copyOf(specs);
        }

        public String warheadId() {
            return warheadId;
        }

        public Theme theme() {
            return theme;
        }

        /** The geometry, in draw order (back to front). Never empty. */
        public List<Spec> specs() {
            return specs;
        }

        /** Effect lifetime before power scaling. */
        public int baseTicks() {
            return baseTicks;
        }

        /**
         * How much blast this warhead actually ends with: 0 for the ones that are not bombs, 1 for a
         * normal finale, above 1 for the ones that are explicitly worse than their own radius suggests.
         */
        public float finaleScale() {
            return finaleScale;
        }

        /** True when this warhead's own description ends in an explosion rather than a delivery. */
        public boolean hasBlastFinale() {
            return finaleScale > 0.0F;
        }

        /**
         * Effect lifetime for a warhead of a given power, in ticks.
         *
         * Logarithmic and hard-capped, the same shape {@link BlastStyle#ticksFor} has always used: bigger
         * bangs get a longer tail, but an Omega cannot grade the world for ten minutes and a grenade
         * cannot be a single frame. This is the number the renderer, the fuse and the FX packet all use.
         */
        public int durationTicks(float power) {
            double scaled = baseTicks + Math.log1p(Math.max(0.0D, power)) * 12.0D;
            return (int) Math.max(MIN_TICKS, Math.min(MAX_TICKS, Math.round(scaled)));
        }

        @Override
        public String toString() {
            return "motif[" + warheadId + " " + theme + " " + baseTicks + "t x" + specs.size() + "]";
        }
    }

    private BlastMotif() {
    }

    // ------------------------------------------------------------------ the roster

    /**
     * The motif for a warhead id.
     *
     * Every one of the thirty-eight ids has its own row. An unknown id degrades to the HE motif rather
     * than throwing, because a packet from a newer build must not be able to kill a render frame.
     */
    public static Motif forWarhead(String warheadId) {
        String id = warheadId == null ? "" : warheadId.toLowerCase(java.util.Locale.ROOT);
        return switch (id) {
            // --- conventional ------------------------------------------------------------
            case "dud" -> new Motif("dud", Theme.SILENT, 50, 0.0F, List.of(
                    core(0.00, 0.45, 0.10, 0x60807A70),
                    circle(0.05, 1.00, 0.30, 0x40706A62, 8, 0.0020)));
            case "decoy" -> new Motif("decoy", Theme.ORDNANCE, 150, 0.0F, List.of(
                    core(0.00, 0.40, 0.55, 0xC0FFF0D0),
                    wave(0.00, 0.55, 1.10, 0xA0FFD9A0),
                    embers(0.05, 1.00, 0.45, 0x90C9B89A, 48, 0.0),
                    circle(0.10, 1.00, 0.70, 0x40A09080, 12, 0.0010)));
            case "he" -> new Motif("he", Theme.ORDNANCE, 130, 1.0F, List.of(
                    core(0.00, 0.45, 0.60, 0xD0FFE9C0),
                    wave(0.00, 0.50, 1.00, 0xB0FFCE90),
                    embers(0.05, 0.95, 0.50, 0x90FF9A40, 56, 0.0),
                    circle(0.15, 1.00, 0.60, 0x35886A50, 10, 0.0012)));
            case "incendiary" -> new Motif("incendiary", Theme.INFERNO, 300, 1.0F, List.of(
                    core(0.00, 0.40, 0.50, 0xD0FFB050),
                    embers(0.02, 1.00, 0.75, 0xC0FF7A20, 120, 0.0030),
                    beam(0.05, 0.70, 0.35, 0x70FF9A30, 0.9),
                    circle(0.10, 1.00, 0.85, 0x60FF6A10, 16, 0.0018),
                    wave(0.00, 0.45, 0.90, 0x70FFB060)));
            case "cluster" -> new Motif("cluster", Theme.ORDNANCE, 200, 1.0F, List.of(
                    core(0.00, 0.35, 0.40, 0xB0FFE6C0),
                    wave(0.00, 0.40, 0.60, 0x90FFD090),
                    wave(0.18, 0.62, 0.95, 0x80FFC070),
                    wave(0.34, 0.80, 1.25, 0x70FFB050),
                    rings(0.10, 1.00, 0.80, 0x50FFAE60, 3, 0.0025)));
            case "tracking" -> new Motif("tracking", Theme.MACHINE, 210, 1.0F, List.of(
                    core(0.00, 0.40, 0.45, 0xB0C8ECFF),
                    scan(0.00, 0.70, 1.00, 0x8060C8FF, 3),
                    beam(0.00, 1.00, 0.18, 0x5080D8FF, 1.6),
                    circle(0.10, 1.00, 0.70, 0x4060A8FF, 12, -0.0030)));
            case "emp" -> new Motif("emp", Theme.MACHINE, 330, 0.0F, List.of(
                    scan(0.00, 0.75, 1.10, 0xA070D8FF, 4),
                    beam(0.00, 0.80, 0.22, 0x60A0E8FF, 1.4),
                    circle(0.00, 1.00, 0.95, 0x7040A0FF, 24, -0.0040),
                    arc(0.05, 0.85, 0.80, 0x90A0F0FF, 14),
                    sky(0.20, 1.00, 1.20, 0x4060B0FF, 18, 0.0025)));

            // --- nuclear -------------------------------------------------------------------
            case "fission" -> new Motif("fission", Theme.NUCLEAR, 460, 1.0F, List.of(
                    core(0.00, 0.35, 0.70, 0xD0EAFFB0),
                    beam(0.00, 0.85, 0.45, 0x90C8F070, 1.5),
                    scan(0.10, 0.80, 1.15, 0x70A8E060, 3),
                    embers(0.10, 1.00, 0.90, 0xA0B8E860, 140, 0.0015),
                    sky(0.25, 1.00, 1.30, 0x5090D050, 16, 0.0020),
                    circle(0.15, 1.00, 0.80, 0x4080B040, 12, -0.0015)));
            case "fusion" -> new Motif("fusion", Theme.STELLAR, 400, 1.2F, List.of(
                    core(0.00, 0.45, 0.95, 0xE0F0FAFF),
                    beam(0.00, 0.70, 0.50, 0xB0D8F0FF, 1.4),
                    wave(0.00, 0.60, 1.35, 0xA0E0F4FF),
                    embers(0.10, 1.00, 0.80, 0xB0FFE090, 120, 0.0020),
                    sky(0.20, 1.00, 1.10, 0x60B0D8FF, 14, 0.0030)));

            case "antimatter" -> new Motif("antimatter", Theme.VOID, 520, 1.3F, List.of(
                    core(0.00, 0.45, 0.90, 0xD8C090FF),
                    wave(0.00, 0.55, 1.30, 0xB0A070E0),
                    tendrils(0.05, 1.00, 1.15, 0xB07030B0, 20, 0.0050),
                    arc(0.10, 0.95, 1.00, 0xC0D0A0FF, 14),
                    embers(0.10, 1.00, 0.85, 0xA0E0C0FF, 130, 0.0020),
                    sky(0.25, 1.00, 1.20, 0x506020A0, 14, -0.0026)));

            // --- money ---------------------------------------------------------------------
            case "numismatic" -> new Motif("numismatic", Theme.COIN, 320, 1.0F, List.of(
                    core(0.00, 0.35, 0.45, 0xC0FFE890),
                    shards(0.05, 1.00, 0.70, 0xD0FFD24A, 26, 0.0060),
                    embers(0.05, 1.00, 0.60, 0xB0FFE070, 90, 0.0),
                    circle(0.10, 1.00, 0.90, 0x70E0A820, 20, 0.0025),
                    rings(0.15, 1.00, 0.75, 0x60FFCE40, 4, -0.0035)));

            // --- non-blast warheads --------------------------------------------------------
            case "bulwark" -> new Motif("bulwark", Theme.BARRIER, 620, 0.0F, List.of(
                    rings(0.00, 1.00, 1.00, 0x809FE8FF, 6, 0.0022),
                    scan(0.00, 0.60, 1.00, 0x50B0E8FF, 3),
                    circle(0.00, 1.00, 1.00, 0x7080C8FF, 24, -0.0018),
                    beam(0.00, 0.40, 0.15, 0x40C0F0FF, 1.0),
                    sky(0.30, 1.00, 0.90, 0x4090D0FF, 12, 0.0020)));
            case "contagion" -> new Motif("contagion", Theme.PLAGUE, 640, 1.0F, List.of(
                    core(0.00, 0.40, 0.55, 0xA0A8E060),
                    tendrils(0.00, 1.00, 1.10, 0xB06FCF57, 18, 0.0040),
                    embers(0.05, 1.00, 0.80, 0x9080D040, 110, 0.0010),
                    circle(0.10, 1.00, 0.95, 0x6060A030, 18, 0.0015),
                    shards(0.20, 1.00, 0.60, 0x60A0E070, 14, -0.0025)));
            case "spell" -> new Motif("spell", Theme.ARCANE, 380, 0.0F, List.of(
                    glyphs(0.00, 0.80, 0.85, 0xC0C070FF, 16),
                    sky(0.05, 1.00, 1.05, 0x80B060FF, 20, 0.0030),
                    rings(0.00, 1.00, 0.70, 0x709050E0, 5, -0.0035),
                    embers(0.05, 1.00, 0.60, 0xA0D0A0FF, 90, 0.0),
                    core(0.10, 0.60, 0.35, 0x90B080FF)));
            case "cargo" -> new Motif("cargo", Theme.LOGISTICS, 250, 0.0F, List.of(
                    beam(0.00, 0.70, 0.30, 0x70D0C090, 0.8),
                    circle(0.00, 1.00, 0.75, 0x60C0A870, 12, 0.0020),
                    rings(0.15, 1.00, 0.55, 0x50E0D0A0, 3, -0.0025),
                    embers(0.20, 1.00, 0.40, 0x60F0E0B0, 30, 0.0)));
            case "minefield" -> new Motif("minefield", Theme.LOGISTICS, 300, 0.0F, List.of(
                    circle(0.00, 1.00, 1.00, 0x7080A0C0, 24, 0.0015),
                    glyphs(0.05, 0.90, 0.90, 0x6060A0D0, 20),
                    scan(0.00, 0.60, 1.05, 0x406090C0, 2),
                    shards(0.20, 1.00, 0.85, 0x50A0C0E0, 24, 0.0)));
            case "constructor" -> new Motif("constructor", Theme.LOGISTICS, 380, 0.0F, List.of(
                    scan(0.00, 0.85, 1.00, 0x80A0E0FF, 3),
                    rings(0.00, 1.00, 0.90, 0x7080D0E0, 5, 0.0020),
                    glyphs(0.10, 0.90, 0.80, 0x6090E0F0, 14),
                    embers(0.10, 1.00, 0.70, 0x80C0F0FF, 80, 0.0)));
            case "spatial" -> new Motif("spatial", Theme.VOID, 300, 0.0F, List.of(
                    scan(0.00, 0.70, 1.00, 0x80B073FF, 3),
                    shards(0.05, 1.00, 0.90, 0x90C090FF, 30, 0.0050),
                    beam(0.00, 0.90, 0.25, 0x60A070FF, 1.2),
                    circle(0.10, 1.00, 0.80, 0x509060E0, 16, -0.0030)));
            case "clucker" -> new Motif("clucker", Theme.SWARM, 260, 0.35F, List.of(
                    core(0.00, 0.30, 0.35, 0xA0FFF0C0),
                    embers(0.00, 1.00, 0.70, 0xB0FFF8E0, 100, 0.0040),
                    rings(0.05, 1.00, 0.65, 0x60FFE0A0, 4, 0.0050),
                    wave(0.00, 0.45, 0.80, 0x60FFE8B0)));
            case "violator" -> new Motif("violator", Theme.BEAST, 320, 0.0F, List.of(
                    core(0.00, 0.45, 0.60, 0xB0402040),
                    arc(0.05, 1.00, 0.90, 0xC08040C0, 12),
                    sky(0.10, 1.00, 1.15, 0x70A02030, 14, -0.0025),
                    embers(0.10, 1.00, 0.70, 0x90603060, 80, 0.0),
                    tendrils(0.20, 1.00, 0.80, 0x70502050, 10, 0.0030)));
            case "beef" -> new Motif("beef", Theme.BEAST, 220, 0.0F, List.of(
                    core(0.00, 0.35, 0.50, 0xA0E0A0A0),
                    wave(0.00, 0.50, 1.00, 0x80D08080),
                    embers(0.05, 1.00, 0.60, 0x90C08060, 70, 0.0),
                    circle(0.10, 1.00, 0.70, 0x40906050, 10, 0.0015)));

            // --- the seventeen apex weapons ------------------------------------------------
            case "world_eater" -> new Motif("world_eater", Theme.VOID, 440, 1.0F, List.of(
                    core(0.00, 0.70, 0.85, 0xC02A0A3A),
                    tendrils(0.00, 1.00, 1.20, 0xB06A20A0, 24, 0.0055),
                    rings(0.10, 1.00, 1.00, 0x80A060FF, 5, -0.0040),
                    arc(0.20, 1.00, 1.00, 0xA0C090FF, 10),
                    sky(0.30, 1.00, 1.20, 0x506020A0, 12, 0.0020)));
            case "stellar_requiem" -> new Motif("stellar_requiem", Theme.STELLAR, 640, 1.3F, List.of(
                    core(0.00, 0.50, 1.00, 0xE8FFFFFF),
                    beam(0.00, 0.80, 0.55, 0xC0D8ECFF, 1.6),
                    wave(0.00, 0.65, 1.40, 0xB0E8F6FF),
                    embers(0.10, 1.00, 0.90, 0xC0FFE8A0, 140, 0.0025),
                    sky(0.15, 1.00, 1.30, 0x70B0D0FF, 16, 0.0030)));
            case "reality_breaker" -> new Motif("reality_breaker", Theme.VOID, 500, 1.1F, List.of(
                    arc(0.00, 0.90, 1.10, 0xD0B060FF, 22),
                    tendrils(0.05, 1.00, 1.05, 0xA07030C0, 16, 0.0045),
                    core(0.10, 0.60, 0.70, 0xB0401060),
                    scan(0.15, 0.85, 1.10, 0x609050E0, 3),
                    sky(0.25, 1.00, 1.15, 0x506020A0, 14, -0.0030)));
            case "spacetime_hammer" -> new Motif("spacetime_hammer", Theme.CATACLYSM, 440, 1.2F, List.of(
                    rings(0.00, 1.00, 1.10, 0xC090C0FF, 5, 0.0035),
                    wave(0.00, 0.45, 0.70, 0xA0C0D8FF),
                    wave(0.25, 0.70, 1.05, 0x90A0C0FF),
                    wave(0.50, 0.95, 1.40, 0x8090B0FF),
                    beam(0.00, 0.90, 0.30, 0x70B0D0FF, 1.3),
                    sky(0.20, 1.00, 1.20, 0x507090E0, 12, 0.0025)));
            case "universal_imploder" -> new Motif("universal_imploder", Theme.VOID, 440, 1.1F, List.of(
                    core(0.00, 0.65, 0.90, 0xC0301050),
                    shards(0.00, 1.00, 1.10, 0xA09070D0, 32, -0.0060),
                    wave(0.30, 0.90, 1.20, 0x90A080E0),
                    beam(0.10, 1.00, 0.28, 0x808060C0, 1.2)));
            case "ragnarok" -> new Motif("ragnarok", Theme.CATACLYSM, 660, 1.2F, List.of(
                    sky(0.00, 1.00, 1.40, 0x90B01820, 20, 0.0022),
                    embers(0.00, 1.00, 1.20, 0xD0FF6020, 160, 0.0035),
                    core(0.05, 0.55, 0.80, 0xC0FF9040),
                    arc(0.10, 1.00, 1.10, 0xB0FFB0A0, 16),
                    beam(0.15, 0.85, 0.40, 0x80C03020, 1.4),
                    glyphs(0.30, 1.00, 1.00, 0x70E04030, 18)));
            case "world_rune" -> new Motif("world_rune", Theme.ARCANE, 560, 1.2F, List.of(
                    glyphs(0.00, 0.75, 1.00, 0xE0FFE090, 24),
                    circle(0.00, 1.00, 1.05, 0xA0FFC860, 28, 0.0018),
                    sky(0.15, 1.00, 1.20, 0x80FFB040, 18, -0.0022),
                    core(0.20, 0.70, 0.60, 0xB0FFD070),
                    embers(0.10, 1.00, 0.85, 0xA0FFE0A0, 110, 0.0)));
            case "abyssal_covenant" -> new Motif("abyssal_covenant", Theme.VOID, 500, 1.0F, List.of(
                    tendrils(0.00, 1.00, 1.25, 0xC0301030, 26, 0.0050),
                    shards(0.05, 1.00, 1.00, 0x90502060, 28, 0.0040),
                    core(0.05, 0.60, 0.75, 0xB0200830),
                    sky(0.20, 1.00, 1.15, 0x60401060, 16, 0.0026),
                    rings(0.25, 1.00, 0.90, 0x706030A0, 4, -0.0030)));
            case "soul_furnace" -> new Motif("soul_furnace", Theme.SOUL, 900, 1.0F, List.of(
                    core(0.00, 1.00, 1.00, 0xE0180028),
                    arc(0.00, 1.00, 1.05, 0xE06020B0, 20),
                    sky(0.00, 1.00, 1.60, 0xC0C01018, 24, 0.0030),
                    circle(0.05, 1.00, 1.10, 0x90A01020, 20, -0.0024),
                    tendrils(0.10, 1.00, 1.00, 0x90401060, 16, 0.0040)));
            case "arcane_singularity" -> new Motif("arcane_singularity", Theme.ARCANE, 500, 1.1F, List.of(
                    rings(0.00, 1.00, 1.05, 0xB0B070FF, 6, 0.0045),
                    sky(0.05, 1.00, 1.25, 0x909040E0, 20, -0.0030),
                    core(0.00, 0.65, 0.80, 0xC05020A0),
                    glyphs(0.15, 1.00, 0.90, 0xA0C090FF, 16),
                    embers(0.10, 1.00, 0.80, 0x90D0A0FF, 100, 0.0)));
            case "quasar_lance" -> new Motif("quasar_lance", Theme.STELLAR, 380, 1.0F, List.of(
                    beam(0.00, 0.85, 0.30, 0xE0E0F4FF, 3.0),
                    core(0.00, 0.45, 0.60, 0xD0F0FAFF),
                    wave(0.05, 0.60, 1.20, 0xA0C0E8FF),
                    embers(0.10, 1.00, 1.10, 0xB0B0E0FF, 120, 0.0030),
                    rings(0.15, 1.00, 0.80, 0x70A0D0FF, 4, 0.0040)));
            case "starforge" -> new Motif("starforge", Theme.STELLAR, 560, 1.3F, List.of(
                    core(0.00, 0.55, 1.05, 0xE8FFF0D0),
                    wave(0.00, 0.70, 1.45, 0xB0FFD890),
                    embers(0.05, 1.00, 1.00, 0xD0FFA040, 150, 0.0030),
                    circle(0.10, 1.00, 1.00, 0x80FF7020, 18, 0.0020),
                    beam(0.00, 0.60, 0.45, 0x90FFE0A0, 1.2)));
            case "big_bang_seed" -> new Motif("big_bang_seed", Theme.CATACLYSM, 760, 1.4F, List.of(
                    core(0.00, 0.60, 1.20, 0xF0FFFFFF),
                    beam(0.00, 0.90, 0.60, 0xD0E8F0FF, 1.8),
                    wave(0.00, 0.75, 1.60, 0xC0F0F8FF),
                    rings(0.10, 1.00, 1.30, 0x90C0D8FF, 6, 0.0030),
                    sky(0.20, 1.00, 1.50, 0x70B0C8FF, 18, -0.0025),
                    embers(0.15, 1.00, 1.10, 0xC0FFE8C0, 160, 0.0020)));
            case "reality_corruptor" -> new Motif("reality_corruptor", Theme.VOID, 500, 1.0F, List.of(
                    shards(0.00, 1.00, 1.15, 0xC0FF40C0, 34, 0.0055),
                    scan(0.00, 0.80, 1.05, 0x80C060E0, 3),
                    arc(0.10, 1.00, 0.95, 0xB0E070FF, 14),
                    tendrils(0.15, 1.00, 0.90, 0x90802080, 12, -0.0035),
                    core(0.05, 0.55, 0.60, 0xA0600060)));
            case "absolute_zero" -> new Motif("absolute_zero", Theme.CRYO, 620, 1.3F, List.of(
                    shards(0.00, 0.95, 1.10, 0xC0C0F0FF, 30, 0.0018),
                    scan(0.00, 0.90, 1.05, 0x70A0E0FF, 3),
                    core(0.10, 0.70, 0.70, 0xB0E8FAFF),
                    circle(0.05, 1.00, 1.00, 0x80A0D8FF, 20, -0.0012),
                    embers(0.20, 1.00, 0.80, 0x80D0F0FF, 90, 0.0)));
            case "omega" -> new Motif("omega", Theme.CATACLYSM, 720, 1.4F, List.of(
                    sky(0.00, 1.00, 1.50, 0xA0C01018, 22, 0.0024),
                    core(0.00, 0.55, 1.00, 0xD0200010),
                    beam(0.00, 0.90, 0.50, 0xA0E04030, 1.6),
                    wave(0.00, 0.70, 1.50, 0xB0FF6040),
                    arc(0.10, 1.00, 1.10, 0xC0FF8060, 16),
                    rings(0.20, 1.00, 1.20, 0x80A02020, 4, -0.0030)));
            case "absolute_annihilation" -> new Motif("absolute_annihilation", Theme.CATACLYSM, 900, 1.5F, List.of(
                    rings(0.00, 1.00, 1.30, 0xD0C0A0FF, 7, 0.0040),
                    arc(0.00, 1.00, 1.20, 0xC0B070FF, 24),
                    sky(0.05, 1.00, 1.60, 0x90A040E0, 24, -0.0028),
                    core(0.05, 0.60, 1.00, 0xD0300050),
                    tendrils(0.15, 1.00, 1.15, 0xA06020A0, 20, 0.0045),
                    wave(0.10, 0.80, 1.70, 0xA0D0B0FF)));

            // --- anything else: the honest, boring version of "something went off" ---------
            default -> new Motif(id.isEmpty() ? "he" : id, Theme.ORDNANCE, 130, 1.0F, List.of(
                    core(0.00, 0.45, 0.60, 0xD0FFE9C0),
                    wave(0.00, 0.50, 1.00, 0xB0FFCE90),
                    embers(0.05, 0.95, 0.50, 0x90FF9A40, 56, 0.0)));
        };
    }

    /** Effect lifetime for a warhead id at a given power. The one number the FX, the fuse and the log use. */
    public static int durationTicks(String warheadId, float power) {
        return forWarhead(warheadId).durationTicks(power);
    }

    /** The theme of a warhead id, for logs and read-outs. */
    public static Theme themeOf(String warheadId) {
        return forWarhead(warheadId).theme();
    }

    /** True when this id has its own hand-written row rather than the degraded default. */
    public static boolean known(String warheadId) {
        if (warheadId == null || warheadId.isBlank()) {
            return false;
        }
        String id = warheadId.toLowerCase(java.util.Locale.ROOT);
        return !forWarhead(id).specs().isEmpty() && ROSTER.contains(id);
    }

    /** Every id with its own motif row. Kept in one place so the harness can walk the whole roster. */
    public static final List<String> ROSTER = List.of(
            "dud", "decoy", "emp", "he", "incendiary", "cluster", "fission", "fusion",
            "antimatter", "numismatic", "world_eater", "stellar_requiem", "reality_breaker",
            "spacetime_hammer", "universal_imploder", "ragnarok", "world_rune", "abyssal_covenant",
            "soul_furnace", "arcane_singularity", "quasar_lance", "starforge", "big_bang_seed",
            "reality_corruptor", "absolute_zero", "omega", "absolute_annihilation", "bulwark",
            "contagion", "spell", "cargo", "minefield", "tracking", "constructor", "spatial",
            "clucker", "violator", "beef");

    // ------------------------------------------------------------------ spec builders

    private static Spec core(double from, double to, double scale, int argb) {
        return new Spec(Layer.CORE_SPHERE, from, to, scale, argb, 1, 0.0D);
    }

    private static Spec wave(double from, double to, double scale, int argb) {
        return new Spec(Layer.SHOCKWAVE, from, to, scale, argb, 1, 0.0D);
    }

    private static Spec circle(double from, double to, double scale, int argb, int strokes, double spin) {
        return new Spec(Layer.GROUND_CIRCLE, from, to, scale, argb, strokes, spin);
    }

    private static Spec sky(double from, double to, double scale, int argb, int strokes, double spin) {
        return new Spec(Layer.SKY_CIRCLE, from, to, scale, argb, strokes, spin);
    }

    private static Spec beam(double from, double to, double scale, int argb, double heightScale) {
        // `count` carries the pillar's height in radii, so a quasar jet can be three times taller than a
        // candle without the renderer needing a second parameter.
        return new Spec(Layer.BEAM, from, to, scale, argb, (int) Math.round(heightScale * 100.0D), 0.0D);
    }

    private static Spec arc(double from, double to, double scale, int argb, int count) {
        return new Spec(Layer.ARC, from, to, scale, argb, count, 0.0D);
    }

    private static Spec embers(double from, double to, double scale, int argb, int count, double spin) {
        return new Spec(Layer.EMBERS, from, to, scale, argb, count, spin);
    }

    private static Spec shards(double from, double to, double scale, int argb, int count, double spin) {
        return new Spec(Layer.SHARDS, from, to, scale, argb, count, spin);
    }

    private static Spec rings(double from, double to, double scale, int argb, int count, double spin) {
        return new Spec(Layer.RINGS_STACK, from, to, scale, argb, count, spin);
    }

    private static Spec tendrils(double from, double to, double scale, int argb, int count, double spin) {
        return new Spec(Layer.TENDRILS, from, to, scale, argb, count, spin);
    }

    private static Spec scan(double from, double to, double scale, int argb, int count) {
        return new Spec(Layer.SCAN_SHELL, from, to, scale, argb, count, 0.0D);
    }

    private static Spec glyphs(double from, double to, double scale, int argb, int count) {
        return new Spec(Layer.GLYPH_RING, from, to, scale, argb, count, 0.0D);
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
