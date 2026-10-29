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
     * Which half of the weapon's life a layer belongs to.
     *
     * <p>CHARGE is the anticipation that plays *before* the warhead goes off - the fuse is this effect,
     * not a silent wait - and IMPACT is the detonation itself, for the warheads that end in one.
     */
    public enum Phase {
        /** Playing out before the blast. Its length is the form's fuse. */
        CHARGE,
        /** The detonation. Warheads with {@code finaleScale == 0} never have one. */
        IMPACT
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
    public record Spec(Layer layer, double from, double to, double scale, int argb, int count, double spin,
                       Phase phase) {

        /** The impact-phase row, which is what all thirty-eight hand-written rows are. */
        public Spec(Layer layer, double from, double to, double scale, int argb, int count, double spin) {
            this(layer, from, to, scale, argb, count, spin, Phase.IMPACT);
        }

        /** Clamped, so a hand-written row can never make the renderer divide by zero or draw backwards. */
        public Spec {
            from = clamp01(from);
            to = clamp01(to);
            if (to <= from) {
                to = Math.min(1.0D, from + 0.05D);
            }
            scale = Math.max(0.05D, scale);
            count = Math.max(1, count);
            if (phase == null) {
                phase = Phase.IMPACT;
            }
        }

        /** The same layer re-staged as anticipation: converging instead of bursting, and half the size. */
        public Spec asCharge(double from, double to, double scaleFactor) {
            return new Spec(layer, from, to, scale * scaleFactor, argb, count, -spin * 1.6D, Phase.CHARGE);
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
        private final List<Spec> charge;
        private final Volume volume;

        Motif(String warheadId, Theme theme, int baseTicks, float finaleScale, List<Spec> specs) {
            this.warheadId = warheadId;
            this.theme = theme;
            this.baseTicks = Math.max(MIN_TICKS, Math.min(MAX_TICKS, baseTicks));
            this.finaleScale = Math.max(0.0F, Math.min(2.0F, finaleScale));
            this.specs = List.copyOf(specs);
            this.charge = List.copyOf(buildCharge(this.specs));
            this.volume = Volume.of(this.warheadId, this.theme, this.specs, this.finaleScale);
        }

        /**
         * The anticipation layers, derived from the impact layers rather than hand-written again.
         *
         * What you watch charging is a preview of what is coming: the same kinds of shape, the same
         * colours, the same counts, but staggered in, at just over half the size, spinning the other way
         * so the whole thing visibly winds up instead of winding down.
         */
        private static List<Spec> buildCharge(List<Spec> impact) {
            List<Spec> out = new java.util.ArrayList<>();
            int n = impact.size();
            for (int i = 0; i < n; i++) {
                double from = i / (double) Math.max(1, n) * 0.7D;
                out.add(impact.get(i).asCharge(from, Math.min(1.0D, from + 0.4D), 0.55D));
            }
            return out;
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

        /** The anticipation layers, for the CPU geometry and particle fallbacks. Never empty. */
        public List<Spec> chargeSpecs() {
            return charge;
        }

        /** The parameters this warhead feeds the volumetric GPU shader. Never null. */
        public Volume volume() {
            return volume;
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


    /**
     * What one warhead tells the volumetric GPU shader to do.
     *
     * <p>The shader ({@code shaders/core/world_blast_volume.fsh}) raymarches a procedural density field
     * through a proxy volume the size of the blast. Everything that makes one warhead read differently
     * from another arrives as one of these numbers - shape, motion, palette - so there is no branch per
     * warhead in GLSL and nothing to get out of sync: this record is the shader's entire input besides
     * position, size and time.
     *
     * <p>All of it is derived, not authored: the shape numbers come from the theme, the palette comes
     * from the motif's own layer colours, and {@code swirl} from how fast its layers turn. Thirty-eight
     * warheads therefore produce thirty-eight distinct parameter sets, which the logic harness asserts.
     */
    /**
     * The full look of a warhead's in-world effect: shape, motion, palette, and which of the shader's
     * structural features it asks for.
     *
     * <p>The first eleven floats come from the theme and the motif's own layers, as they always did. The
     * fifteen feature weights after them are AUTHORED, one row per warhead, from that warhead's own
     * description in {@link WarheadType} - a World Eater gets a black hole, a Ragnarok gets meteors and
     * lightning, a Quasar Lance gets a jet, a Numismatic blast gets coins. That is what makes thirty-eight
     * warheads look like thirty-eight different weapons instead of the same fireball in thirty-eight
     * colours, and {@code tools/sweep.py} plus the logic harness fail the build if a warhead's signature
     * feature comes back at zero.
     */
    public record Volume(float beam, float ring, float skyRing, float implode, float turb, float rise,
                         float streaks, float dark, float glow, float swirl, float seed,
                         float hole, float star, float rift, float waves, float meteors, float lightning,
                         float glyphs, float portals, float jet, float coins, float smoke,
                         float subCharges, float flash, float dome, float embers,
                         float cap, float debris, float dust, float vortex, float cracks,
                         float sparks, float halo,
                         float disc, float lens, float granule, float prominence, float shell,
                         float nebula, float cone, float circle, float sky, float shaft,
                         int colorA, int colorB, int colorC) {

        /** Every field clamped, so a bad row cannot make the shader divide by zero or march backwards. */
        public Volume {
            beam = (float) clamp01(beam);
            ring = (float) clamp01(ring);
            skyRing = (float) clamp01(skyRing);
            implode = Math.max(-1.0F, Math.min(1.0F, implode));
            turb = Math.max(0.05F, Math.min(2.0F, turb));
            rise = Math.max(-1.0F, Math.min(1.0F, rise));
            streaks = (float) clamp01(streaks);
            dark = (float) clamp01(dark);
            glow = Math.max(0.05F, Math.min(2.0F, glow));
            swirl = Math.max(0.0F, Math.min(2.0F, swirl));
            seed = Math.max(0.0F, Math.min(0.999F, seed));
            hole = (float) clamp01(hole);
            star = (float) clamp01(star);
            rift = (float) clamp01(rift);
            waves = (float) clamp01(waves);
            meteors = (float) clamp01(meteors);
            lightning = (float) clamp01(lightning);
            glyphs = (float) clamp01(glyphs);
            portals = (float) clamp01(portals);
            jet = (float) clamp01(jet);
            coins = (float) clamp01(coins);
            smoke = (float) clamp01(smoke);
            subCharges = (float) clamp01(subCharges);
            flash = (float) clamp01(flash);
            dome = (float) clamp01(dome);
            embers = (float) clamp01(embers);
            cap = (float) clamp01(cap);
            debris = (float) clamp01(debris);
            dust = (float) clamp01(dust);
            vortex = (float) clamp01(vortex);
            cracks = (float) clamp01(cracks);
            sparks = (float) clamp01(sparks);
            halo = (float) clamp01(halo);
            disc = (float) clamp01(disc);
            lens = (float) clamp01(lens);
            granule = (float) clamp01(granule);
            prominence = (float) clamp01(prominence);
            shell = (float) clamp01(shell);
            nebula = (float) clamp01(nebula);
            cone = (float) clamp01(cone);
            circle = (float) clamp01(circle);
            sky = (float) clamp01(sky);
        }

        /** Volume parameters for a motif: theme baseline, then this warhead's own authored features. */
        static Volume of(String warheadId, Theme theme, List<Spec> specs, float finaleScale) {
            Shape shape = Shape.baseline(theme == null ? Theme.ORDNANCE : theme, finaleScale);
            author(shape, warheadId);

            // The palette is the motif's thermal ramp: white-hot core, saturated dark edge.
            //
            // It used to be "the first layer's colour is the hot core, the last is the cool edge". Those
            // are GEOMETRY colours - whatever ring, ember column or sky circle happened to be authored
            // first and last in the list - and their position in the list has nothing to do with
            // temperature. Measured across the roster, seventeen of thirty-eight warheads came out with a
            // core and an edge within 0.12 of each other in BOTH luminance and hue. world_rune ran
            // #FFE090 to #FFE0A0: sixteen parts in 255 of blue. incendiary #FFB050 to #FFB060.
            // absolute_zero #C0F0FF to #D0F0FF.
            //
            // The shader drives its whole internal structure off `heat`, lerping ColorC -> ColorB ->
            // ColorA. Hand it a flat pair and there is nothing to lerp: the volume renders as a uniformly
            // coloured opaque ball, and every one of those seventeen reads as the same effect with a
            // different tint. Stellar survived only because its motifs happen to start white-hot and end
            // blue.
            //
            // So the ramp is authored per theme instead - how bright the hot end tops out, how dark and
            // how saturated the cool end goes, and how far the edge's hue rotates from the core's. The
            // warhead's OWN hue is still read off its layers, so identity is preserved and the thirty-eight
            // parameter sets stay distinct. Deliberately dark themes (VOID, SOUL, BEAST, SILENT) get a low
            // ceiling rather than a white one, so "dark" stays an authored property rather than being
            // washed out by the ramp.
            Theme rampTheme = theme == null ? Theme.ORDNANCE : theme;
            float[] ramp = ramp(rampTheme);
            float hue = paletteHue(specs, ramp[5]);
            // Which way the cool end rotates depends on which half of the colour wheel this warhead
            // sits in, so it cannot be a signed per-theme offset: STELLAR holds both starforge, whose
            // hue is 0.11 and cools toward deep red, and fusion, whose hue is 0.56 and cools toward
            // deeper blue. One signed offset got one of them right and pushed the other through
            // yellow-green, which is how a blue star ended up with khaki edges.
            // A negative rotation is a forced direction and overrides the half-wheel rule. ARCANE needs
            // it: the rule sends a violet hue upward toward magenta, and the Arcane Singularity is asked
            // for deep indigo at its cool end, which is downward toward blue. The theme knows which way
            // its own colour cools; the general rule is only the default.
            float coolHue = ramp[4] < 0.0F ? hue + ramp[4]
                    : hue < 0.42F ? hue - ramp[4] : hue + ramp[4];
            int a = fromHsb(hue, ramp[1], ramp[0]);
            int c = fromHsb(coolHue, ramp[3], ramp[2]);
            int b = mix(a, c, 0.5F);
            // How fast this motif's layers turn, folded into one number the shader uses to shear the
            // field. Two warheads in the same theme still move differently because of it.
            double spin = 0.0D;
            for (Spec spec : specs) {
                spin += Math.abs(spec.spin());
            }
            float swirl = (float) Math.min(2.0D,
                    Math.max(spin / Math.max(1, specs.size()) * 22.0D, shape.swirl));
            // Where in the noise field this warhead starts. Two warheads of the same theme would otherwise
            // share one turbulence pattern; this puts theirs in different places, and because it comes
            // from the warhead's own id it also keeps all thirty-eight parameter sets provably distinct.
            float seed = (warheadId == null ? 0 : warheadId.hashCode() & 0x7FFFFFFF)
                    / (float) Integer.MAX_VALUE;
            return new Volume(shape.beam, shape.ring, shape.skyRing, shape.implode, shape.turb, shape.rise,
                    shape.streaks, shape.dark, shape.glow, swirl, seed,
                    shape.hole, shape.star, shape.rift, shape.waves, shape.meteors, shape.lightning,
                    shape.glyphs, shape.portals, shape.jet, shape.coins, shape.smoke,
                    shape.subCharges, shape.flash, shape.dome, shape.embers,
                    shape.cap, shape.debris, shape.dust, shape.vortex, shape.cracks,
                    shape.sparks, shape.halo,
                    shape.disc, shape.lens, shape.granule, shape.prominence, shape.shell,
                    shape.nebula, shape.cone, shape.circle, shape.sky, shape.shaft,
                    a | 0xFF000000, b | 0xFF000000, c | 0xFF000000);
        }

        /**
         * What each warhead actually looks like, taken from its own description.
         *
         * <p>One case per warhead, deliberately spelled out rather than derived: the point of this switch
         * is that a reader can hold the description of a warhead next to the line that draws it. Anything
         * not listed keeps its theme baseline, so a new warhead still works before anybody authors it.
         * The logic harness asserts the signature feature of every row below is non-zero.
         */
        private static void author(Shape shape, String warheadId) {
            switch (warheadId == null ? "" : warheadId) {
                // -- conventional
                case "dud" -> shape.smoke(0.90F).glow(0.22F).flash(0.0F).turb(0.65F).waves(0.0F).dust(0.5F);
                case "decoy" -> shape.smoke(0.75F).flash(0.35F).waves(0.5F).glow(0.45F).dust(0.6F).debris(0.3F);
                case "emp" -> shape.lightning(0.85F).waves(0.8F).dark(0.55F).glow(0.7F)
                        .turb(0.35F).streaks(0.6F).sparks(0.7F).cracks(0.4F);
                case "he" -> shape.waves(0.85F).flash(0.55F).smoke(0.45F).debris(0.6F).dust(0.7F).cracks(0.35F).halo(0.45F).shell(0.35F);
                case "incendiary" -> shape.embers(0.95F).rise(1.0F).turb(1.2F).sparks(0.6F).dust(0.3F).nebula(0.25F);
                case "cluster" -> shape.subCharges(0.95F).waves(0.4F).debris(0.8F).dust(0.5F).shell(0.3F);
                case "fission" -> shape.waves(0.9F).flash(0.6F).embers(0.7F).streaks(0.55F).smoke(0.5F).cap(0.95F).dust(0.8F).debris(0.5F).halo(0.5F).shell(0.7F).nebula(0.3F).sky(0.4F);
                case "fusion" -> shape.star(0.8F).flash(1.0F).waves(0.9F).streaks(0.7F).cap(0.7F).halo(0.8F).sparks(0.6F).debris(0.5F).granule(0.7F).shell(0.7F).nebula(0.4F).sky(0.6F);
                case "antimatter" -> shape.hole(0.9F).flash(1.0F).waves(1.0F).implode(0.8F)
                        .dark(0.8F).streaks(0.8F).halo(0.7F).cracks(0.6F).debris(0.6F).vortex(0.4F).disc(0.8F).lens(0.9F).shell(0.6F).nebula(0.5F).sky(0.5F);
                case "numismatic" -> shape.coins(1.0F).ring(0.6F).rise(0.7F).sparks(0.7F).halo(0.5F).nebula(0.3F);
                // -- apex, in roster order
                case "world_eater" -> shape.hole(1.0F).swirlUp(1.0F).dark(1.0F).implode(1.0F)
                        .glow(1.9F).smoke(0.0F).waves(0.35F).rise(-0.4F).vortex(0.9F).cracks(0.7F).halo(0.6F).sparks(0.4F).disc(1.0F).lens(1.0F).nebula(0.5F).sky(0.7F);
                case "stellar_requiem" -> shape.star(1.0F).flash(0.9F).glow(1.85F)
                        .swirlUp(0.6F).waves(0.5F).halo(0.9F).sparks(0.6F).debris(0.4F).granule(1.0F).prominence(0.9F).nebula(0.6F).lens(0.4F).sky(1.0F).shaft(0.80F);
                case "reality_breaker" -> shape.rift(1.0F).streaks(0.7F).dark(0.6F).turb(0.4F).cracks(0.9F).vortex(0.5F).sparks(0.5F).nebula(0.6F).shell(0.4F).circle(0.3F).sky(0.6F);
                case "spacetime_hammer" -> shape.waves(1.0F).implode(0.7F).skyRing(0.6F).turb(0.8F).dust(0.9F).cracks(0.8F).debris(0.5F).halo(0.5F).shell(0.8F).lens(0.5F).nebula(0.4F).sky(0.6F).shaft(0.90F);
                case "universal_imploder" -> shape.hole(0.7F).implode(1.0F).waves(0.75F)
                        .dark(0.85F).glow(1.7F).vortex(0.7F).cracks(0.6F).halo(0.5F).disc(0.7F).lens(0.9F).shell(0.5F).sky(0.5F);
                case "ragnarok" -> shape.meteors(1.0F).lightning(0.9F).skyRing(0.9F).waves(0.7F)
                        .embers(0.6F).glow(1.75F).cap(0.5F).dust(0.9F).debris(0.7F).cracks(0.8F).halo(0.6F).sparks(0.5F).nebula(0.7F).shell(0.5F).sky(0.85F).shaft(0.70F);
                case "world_rune" -> shape.glyphs(1.0F).waves(0.55F).glow(1.6F).skyRing(0.5F).cracks(1.0F).halo(0.5F).sparks(0.5F).nebula(0.3F).circle(1.0F);
                case "abyssal_covenant" -> shape.portals(1.0F).rift(0.45F).dark(0.7F).glow(1.5F).vortex(0.7F).sparks(0.6F).cracks(0.5F).nebula(0.8F).circle(0.6F).sky(0.6F);
                case "soul_furnace" -> shape.lightning(0.95F).skyRing(1.0F).hole(0.6F).dark(0.85F)
                        .implode(0.8F).swirlUp(1.0F).glow(1.8F).vortex(1.0F).sparks(0.7F).cracks(0.6F).halo(0.5F).nebula(0.6F).disc(0.5F).circle(0.8F).sky(0.7F);
                case "arcane_singularity" -> shape.glyphs(0.95F).skyRing(1.0F).hole(0.55F)
                        .swirlUp(0.9F).glow(1.7F).vortex(0.8F).sparks(0.8F).halo(0.6F).lens(0.7F).disc(0.6F).nebula(0.5F).circle(1.0F).sky(0.5F);
                case "quasar_lance" -> shape.jet(1.0F).waves(0.7F).streaks(0.6F).glow(1.8F).halo(0.7F).sparks(0.6F).dust(0.6F).cracks(0.5F).cone(1.0F).disc(0.8F).lens(0.5F).nebula(0.6F).sky(0.9F).shaft(1.00F);
                case "starforge" -> shape.star(0.95F).embers(0.9F).flash(0.7F).glow(1.8F)
                        .ring(0.6F).rise(0.6F).halo(0.8F).dust(0.7F).cracks(0.6F).sparks(0.5F).granule(1.0F).prominence(1.0F).cone(0.3F).nebula(0.5F).sky(0.9F).shaft(0.70F);
                case "big_bang_seed" -> shape.star(1.0F).flash(1.0F).waves(0.95F).streaks(0.9F)
                        .glow(1.9F).swirlUp(0.8F).halo(1.0F).debris(0.8F).dust(0.8F).sparks(0.7F).shell(1.0F).nebula(0.9F).granule(0.6F).lens(0.5F).sky(1.0F).shaft(0.60F);
                case "reality_corruptor" -> shape.rift(0.9F).dark(0.75F).turb(1.5F).hole(0.5F)
                        .glyphs(0.35F).cracks(0.9F).vortex(0.6F).sparks(0.4F).nebula(0.7F).shell(0.5F).sky(0.6F);
                case "absolute_zero" -> shape.hole(0.85F).implode(1.0F).dark(0.9F).turb(0.25F)
                        .swirlUp(0.2F).waves(0.6F).glow(1.6F).cracks(0.7F).sparks(0.6F).halo(0.4F).lens(0.8F).disc(0.5F).nebula(0.4F).sky(0.4F);
                case "omega" -> shape.waves(1.0F).implode(0.9F).hole(0.7F).flash(0.85F)
                        .streaks(0.7F).glow(1.9F).dust(0.9F).debris(0.8F).cracks(0.7F).cap(0.6F).halo(0.7F).shell(1.0F).lens(0.7F).disc(0.5F).sky(0.7F).shaft(0.60F);
                case "absolute_annihilation" -> shape.hole(0.9F).star(0.85F).flash(1.0F).waves(1.0F)
                        .streaks(0.95F).rift(0.5F).glow(1.95F).cap(0.9F).dust(1.0F).debris(1.0F).cracks(0.9F).halo(1.0F).sparks(0.8F).shell(1.0F).disc(0.9F).lens(1.0F).nebula(0.9F).granule(0.6F).sky(0.9F).shaft(0.85F);
                // -- utility warheads: they are effects, not bombs
                case "bulwark" -> shape.dome(1.0F).glyphs(0.35F).glow(0.9F).turb(0.2F).halo(0.5F).cracks(0.4F).circle(0.3F);
                case "contagion" -> shape.smoke(1.0F).dark(0.4F).rise(0.9F).glow(0.6F)
                        .embers(0.25F).waves(0.0F).dust(0.8F).vortex(0.4F).sparks(0.3F);
                case "spell" -> shape.glyphs(0.9F).skyRing(0.85F).swirlUp(0.8F).glow(1.2F).vortex(0.6F).sparks(0.7F).cracks(0.5F).circle(0.9F);
                case "cargo" -> shape.jet(0.45F).ring(0.4F).glow(0.55F).waves(0.0F).smoke(0.2F).dust(0.5F).sparks(0.3F);
                case "minefield" -> shape.portals(0.7F).waves(0.35F).glow(0.7F).subCharges(0.5F).debris(0.6F).dust(0.6F).sparks(0.4F);
                case "tracking" -> shape.ring(1.0F).skyRing(0.6F).streaks(0.5F).glow(0.8F)
                        .turb(0.3F).waves(0.5F).sparks(0.6F).cracks(0.4F);
                case "constructor" -> shape.dome(0.9F).jet(0.4F).glyphs(0.3F).glow(0.85F).turb(0.2F).cracks(0.6F).sparks(0.5F).circle(0.4F);
                case "spatial" -> shape.rift(0.75F).portals(0.5F).dark(0.5F).glow(1.0F).vortex(0.6F).sparks(0.5F).cracks(0.5F);
                case "clucker" -> shape.embers(0.8F).smoke(0.4F).rise(0.9F).glow(0.8F).sparks(0.5F).dust(0.5F);
                case "violator" -> shape.smoke(0.85F).dark(0.85F).glyphs(0.4F).lightning(0.35F)
                        .glow(1.1F).cracks(0.7F).vortex(0.5F).sparks(0.4F).circle(0.4F);
                case "beef" -> shape.smoke(0.7F).embers(0.7F).coins(0.3F).rise(0.6F).glow(0.75F).dust(0.6F).sparks(0.5F);
                default -> { }
            }
        }

        /** One string per warhead, so the harness can prove no two warheads share a look. */
        public String signature() {
            return String.format(java.util.Locale.ROOT,
                    "%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/%.8f/"
                            + "%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/"
                            + "%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/"
                            + "%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/%.2f/"
                            + "%.2f/%.2f/"
                            + "%08x/%08x/%08x",
                    beam, ring, skyRing, implode, turb, rise, streaks, dark, glow, swirl, seed,
                    hole, star, rift, waves, meteors, lightning, glyphs, portals, jet, coins, smoke,
                    subCharges, flash, dome, embers, cap, debris, dust, vortex, cracks, sparks, halo,
                    disc, lens, granule, prominence, shell, nebula, cone, circle, sky,
                    colorA, colorB, colorC);
        }

        /**
         * Mutable scratch for {@link #author}: the theme baseline plus this warhead's overrides.
         *
         * <p>Fluent setters that return {@code this}, so a row in {@code author} reads as a sentence about
         * that warhead. {@code swirlUp} is {@link Volume#swirl} under a different name, so a row can ask
         * for spin without colliding with the value the motif's own layers supply.
         */
        private static final class Shape {
            float beam;
            float ring;
            float skyRing;
            float implode;
            float turb;
            float rise;
            float streaks;
            float dark;
            float glow;
            /** Extra spin this warhead asks for on top of what its layers already supply. */
            float swirl;
            float hole;
            float star;
            float rift;
            float waves;
            float meteors;
            float lightning;
            float glyphs;
            float portals;
            float jet;
            float coins;
            float smoke;
            float subCharges;
            float flash;
            float dome;
            float embers;
            float cap;
            float debris;
            float dust;
            float vortex;
            float cracks;
            float sparks;
            float halo;
            float disc;
            float lens;
            float granule;
            float prominence;
            float shell;
            float nebula;
            float cone;
            float circle;
            float sky;
            /** Shaft arriving from orbit; distinct from {@code beam}, which is the jet column. */
            float shaft;

            /** The theme's shape and brightness, before any warhead overrides it. */
            static Shape baseline(Theme theme, float finaleScale) {
                Shape s = new Shape();
                switch (theme) {
                    case INFERNO -> { s.beam = 0.05F; s.ring = 0.55F; s.implode = -0.50F; s.turb = 1.15F; s.rise = 0.85F; s.streaks = 0.40F; s.embers = 0.4F;  s.dust = 0.30F; s.sparks = 0.40F; s.halo = 0.30F; }
                    case NUCLEAR -> { s.beam = 0.20F; s.ring = 0.95F; s.implode = -0.40F; s.turb = 0.90F; s.rise = 1.00F; s.streaks = 0.30F; s.flash = 0.5F; s.waves = 0.4F;  s.cap = 0.80F; s.dust = 0.60F; s.debris = 0.40F; s.halo = 0.50F; s.sparks = 0.30F; }
                    case ARCANE -> { s.beam = 0.35F; s.ring = 0.35F; s.skyRing = 1.00F; s.implode = 0.30F; s.turb = 0.45F; s.rise = 0.10F; s.streaks = 0.25F; s.dark = 0.15F; s.glyphs = 0.6F;  s.vortex = 0.35F; s.sparks = 0.50F; s.halo = 0.40F; s.cracks = 0.40F;  s.circle = 0.60F; }
                    case STELLAR -> { s.beam = 1.00F; s.ring = 0.40F; s.skyRing = 0.55F; s.implode = -0.30F; s.turb = 0.70F; s.rise = 0.25F; s.streaks = 0.85F; s.star = 0.6F; s.jet = 0.3F;  s.halo = 0.60F; s.sparks = 0.40F; s.debris = 0.30F;  s.sky = 0.60F; }
                    case CATACLYSM -> { s.beam = 0.45F; s.ring = 1.00F; s.skyRing = 0.70F; s.implode = -0.80F; s.turb = 1.35F; s.rise = 0.45F; s.streaks = 0.75F; s.dark = 0.10F; s.waves = 0.55F;  s.dust = 0.70F; s.debris = 0.60F; s.cracks = 0.50F; s.halo = 0.55F; s.cap = 0.40F; }
                    case VOID -> { s.beam = 0.25F; s.ring = 0.20F; s.skyRing = 0.30F; s.implode = 1.00F; s.turb = 0.55F; s.rise = -0.30F; s.streaks = 0.60F; s.dark = 1.00F; s.hole = 0.55F;  s.vortex = 0.50F; s.halo = 0.40F; s.cracks = 0.30F; s.sparks = 0.30F; }
                    case PLAGUE -> { s.turb = 1.25F; s.rise = 0.70F; s.streaks = 0.20F; s.dark = 0.30F; s.ring = 0.45F; s.skyRing = 0.15F; s.implode = -0.20F; s.smoke = 0.6F;  s.dust = 0.50F; s.vortex = 0.30F; s.sparks = 0.30F; }
                    case CRYO -> { s.beam = 0.10F; s.ring = 0.60F; s.skyRing = 0.20F; s.implode = -0.10F; s.turb = 0.85F; s.rise = 0.15F; s.streaks = 0.55F; s.dark = 0.10F;  s.sparks = 0.60F; s.halo = 0.35F; s.cracks = 0.35F; }
                    case MACHINE -> { s.beam = 0.15F; s.ring = 0.70F; s.skyRing = 0.10F; s.implode = -0.20F; s.turb = 0.35F; s.rise = 0.05F; s.streaks = 0.30F; s.dark = 0.05F; s.waves = 0.4F;  s.sparks = 0.40F; s.cracks = 0.30F; s.debris = 0.30F; }
                    case COIN -> { s.beam = 0.10F; s.ring = 0.50F; s.skyRing = 0.10F; s.implode = -0.30F; s.turb = 0.60F; s.rise = 0.55F; s.streaks = 0.45F; s.coins = 0.4F;  s.sparks = 0.50F; s.halo = 0.35F; }
                    case BARRIER -> { s.beam = 0.05F; s.ring = 0.30F; s.skyRing = 0.05F; s.implode = 0.20F; s.turb = 0.20F; s.streaks = 0.10F; s.dome = 0.7F;  s.halo = 0.30F; s.cracks = 0.25F; }
                    case LOGISTICS -> { s.beam = 0.30F; s.ring = 0.25F; s.skyRing = 0.05F; s.implode = 0.10F; s.turb = 0.30F; s.rise = 0.20F; s.streaks = 0.15F;  s.dust = 0.30F; s.sparks = 0.25F; }
                    case SWARM -> { s.ring = 0.35F; s.implode = -0.40F; s.turb = 1.10F; s.rise = 0.60F; s.streaks = 0.50F; s.subCharges = 0.5F;  s.debris = 0.60F; s.sparks = 0.50F; s.dust = 0.35F; }
                    case BEAST -> { s.beam = 0.05F; s.ring = 0.40F; s.implode = -0.50F; s.turb = 1.05F; s.rise = 0.35F; s.streaks = 0.30F; s.dark = 0.05F;  s.dust = 0.40F; s.debris = 0.40F; s.sparks = 0.30F; }
                    case SOUL -> { s.beam = 0.30F; s.ring = 0.30F; s.skyRing = 1.00F; s.implode = 0.60F; s.turb = 0.50F; s.rise = 0.30F; s.streaks = 0.40F; s.dark = 0.70F; s.lightning = 0.5F;  s.vortex = 0.80F; s.sparks = 0.50F; s.halo = 0.45F; s.cracks = 0.40F;  s.circle = 0.50F; s.sky = 0.40F; }
                    case SILENT -> { s.ring = 0.10F; s.turb = 0.25F; s.streaks = 0.05F; s.smoke = 0.6F;  s.dust = 0.30F; }
                    default -> { s.dust = 0.50F; s.debris = 0.45F; s.cracks = 0.20F; s.halo = 0.35F; s.ring = 0.75F; s.implode = -0.60F; s.turb = 1.00F; s.rise = 0.30F; s.streaks = 0.35F; s.flash = 0.3F; }
                }
                // Warheads that end in a bigger bang burn brighter; the ones that are not bombs stay dim.
                s.glow = 0.35F + finaleScale * 0.55F;
                return s;
            }

            Shape beam(float v) { beam = v; return this; }
            Shape ring(float v) { ring = v; return this; }
            Shape skyRing(float v) { skyRing = v; return this; }
            Shape implode(float v) { implode = v; return this; }
            Shape turb(float v) { turb = v; return this; }
            Shape rise(float v) { rise = v; return this; }
            Shape streaks(float v) { streaks = v; return this; }
            Shape dark(float v) { dark = v; return this; }
            Shape glow(float v) { glow = v; return this; }
            Shape swirlUp(float v) { swirl = v; return this; }
            Shape hole(float v) { hole = v; return this; }
            Shape star(float v) { star = v; return this; }
            Shape rift(float v) { rift = v; return this; }
            Shape waves(float v) { waves = v; return this; }
            Shape meteors(float v) { meteors = v; return this; }
            Shape lightning(float v) { lightning = v; return this; }
            Shape glyphs(float v) { glyphs = v; return this; }
            Shape portals(float v) { portals = v; return this; }
            Shape jet(float v) { jet = v; return this; }
            Shape coins(float v) { coins = v; return this; }
            Shape smoke(float v) { smoke = v; return this; }
            Shape subCharges(float v) { subCharges = v; return this; }
            Shape flash(float v) { flash = v; return this; }
            Shape dome(float v) { dome = v; return this; }
            Shape embers(float v) { embers = v; return this; }
            Shape cap(float v) { cap = v; return this; }
            Shape debris(float v) { debris = v; return this; }
            Shape dust(float v) { dust = v; return this; }
            Shape vortex(float v) { vortex = v; return this; }
            Shape cracks(float v) { cracks = v; return this; }
            Shape sparks(float v) { sparks = v; return this; }
            Shape halo(float v) { halo = v; return this; }
            Shape disc(float v) { disc = v; return this; }
            Shape lens(float v) { lens = v; return this; }
            Shape granule(float v) { granule = v; return this; }
            Shape prominence(float v) { prominence = v; return this; }
            Shape shell(float v) { shell = v; return this; }
            Shape nebula(float v) { nebula = v; return this; }
            Shape cone(float v) { cone = v; return this; }
            Shape circle(float v) { circle = v; return this; }
            Shape sky(float v) { sky = v; return this; }
            Shape shaft(float v) { shaft = v; return this; }
        }

        /**
         * A theme's thermal ramp, as {hot brightness, hot saturation, cool brightness, cool saturation,
         * cool hue rotation in turns, fallback hue}.
         *
         * <p>The rotation is normally a magnitude and the caller picks the direction from the warhead's
         * own hue - see paletteHue. A negative value means the theme forces the direction instead, which
         * ARCANE does: its violet cools downward into indigo, against the general rule.
         *
         * <p>The hot end is deliberately near-white for the themes that are supposed to be hot: a real
         * fireball's core is over-exposed and only its edges carry the colour. The dark themes are the
         * exception and are authored that way - a black hole whose core is white is not a black hole.
         */
        private static float[] ramp(Theme theme) {
            return switch (theme) {
                case ORDNANCE -> new float[] {0.98F, 0.18F, 0.22F, 0.95F, 0.030F, 0.08F};
                case INFERNO -> new float[] {1.00F, 0.12F, 0.20F, 1.00F, 0.050F, 0.03F};
                case NUCLEAR -> new float[] {1.00F, 0.16F, 0.22F, 0.95F, 0.020F, 0.22F};
                case ARCANE -> new float[] {0.95F, 0.25F, 0.28F, 0.90F, -0.070F, 0.78F};
                case STELLAR -> new float[] {1.00F, 0.06F, 0.82F, 0.30F, 0.060F, 0.58F};
                case CATACLYSM -> new float[] {1.00F, 0.14F, 0.24F, 1.00F, 0.040F, 0.98F};
                case VOID -> new float[] {0.72F, 0.45F, 0.14F, 1.00F, 0.030F, 0.78F};
                case PLAGUE -> new float[] {0.90F, 0.30F, 0.18F, 1.00F, 0.050F, 0.25F};
                case CRYO -> new float[] {1.00F, 0.10F, 0.55F, 0.45F, 0.030F, 0.55F};
                case MACHINE -> new float[] {1.00F, 0.12F, 0.45F, 0.80F, 0.040F, 0.55F};
                case COIN -> new float[] {1.00F, 0.20F, 0.35F, 0.95F, 0.030F, 0.13F};
                case BARRIER -> new float[] {0.98F, 0.14F, 0.50F, 0.70F, 0.030F, 0.55F};
                case LOGISTICS -> new float[] {0.85F, 0.18F, 0.32F, 0.55F, 0.020F, 0.10F};
                case SWARM -> new float[] {0.95F, 0.22F, 0.35F, 0.85F, 0.030F, 0.12F};
                case BEAST -> new float[] {0.70F, 0.35F, 0.18F, 0.75F, 0.020F, 0.85F};
                case SOUL -> new float[] {0.45F, 0.80F, 0.10F, 1.00F, 0.050F, 0.80F};
                case SILENT -> new float[] {0.55F, 0.10F, 0.28F, 0.15F, 0.000F, 0.10F};
            };
        }

        /**
         * The hue this motif's palette runs on.
         *
         * <p>The core sphere decides it, because that layer IS the fireball. Reading it off the most
         * saturated layer instead - the obvious move, and the first one tried here - hands the palette
         * to whatever accent a warhead happens to carry: fusion's embers are #FFE090 at saturation 0.44
         * while its actual body is a saturation-0.06 blue, so most-saturated turned a blue star gold
         * and then, cooled, khaki.
         *
         * <p>A core that is pure white or grey carries no usable hue; its number is rounding noise
         * rather than a colour. Those fall back to the theme's hue, not to the accents - a white-hot
         * detonation should cool the way its theme says, not the way its ember colour says.
         */
        private static float paletteHue(List<Spec> specs, float fallback) {
            for (Spec spec : specs) {
                if (spec.layer() == Layer.CORE_SPHERE) {
                    float[] hsb = toHsb(spec.argb());
                    return hsb[1] > 0.04F ? hsb[0] : fallback;
                }
            }
            float bestHue = fallback;
            float bestSat = 0.02F;
            for (Spec spec : specs) {
                float[] hsb = toHsb(spec.argb());
                if (hsb[1] > bestSat) {
                    bestSat = hsb[1];
                    bestHue = hsb[0];
                }
            }
            return bestHue;
        }

        /** Hue in turns, saturation and brightness both 0..1, to 0xAARRGGBB with an opaque alpha. */
        private static int fromHsb(float h, float s, float b) {
            h -= (float) Math.floor(h);
            s = Math.max(0.0F, Math.min(1.0F, s));
            b = Math.max(0.0F, Math.min(1.0F, b));
            float sextant = h * 6.0F;
            int i = (int) Math.floor(sextant) % 6;
            float f = sextant - (float) Math.floor(sextant);
            float p = b * (1.0F - s);
            float q = b * (1.0F - f * s);
            float t = b * (1.0F - (1.0F - f) * s);
            float r;
            float g;
            float bl;
            switch (i) {
                case 0 -> { r = b; g = t; bl = p; }
                case 1 -> { r = q; g = b; bl = p; }
                case 2 -> { r = p; g = b; bl = t; }
                case 3 -> { r = p; g = q; bl = b; }
                case 4 -> { r = t; g = p; bl = b; }
                default -> { r = b; g = p; bl = q; }
            }
            return 0xFF000000
                    | (Math.round(r * 255.0F) << 16)
                    | (Math.round(g * 255.0F) << 8)
                    | Math.round(bl * 255.0F);
        }

        /** 0xAARRGGBB to {hue in turns, saturation, brightness}. Hue of a grey is 0 and must not be used. */
        private static float[] toHsb(int argb) {
            float r = ((argb >>> 16) & 0xFF) / 255.0F;
            float g = ((argb >>> 8) & 0xFF) / 255.0F;
            float b = (argb & 0xFF) / 255.0F;
            float max = Math.max(r, Math.max(g, b));
            float min = Math.min(r, Math.min(g, b));
            float d = max - min;
            float h = 0.0F;
            if (d > 1e-6F) {
                if (max == r) {
                    h = (g - b) / d;
                    if (h < 0.0F) {
                        h += 6.0F;
                    }
                } else if (max == g) {
                    h = (b - r) / d + 2.0F;
                } else {
                    h = (r - g) / d + 4.0F;
                }
                h /= 6.0F;
            }
            return new float[] {h, max > 1e-6F ? d / max : 0.0F, max};
        }

        private static int mix(int argb0, int argb1, float t) {
            int out = 0;
            for (int shift = 0; shift < 24; shift += 8) {
                int v0 = (argb0 >>> shift) & 0xFF;
                int v1 = (argb1 >>> shift) & 0xFF;
                out |= Math.round(v0 + (v1 - v0) * t) << shift;
            }
            return out;
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
    /**
     * How bright a detonation is at a point in its own life: 0 at the flash, 1 when it is over.
     *
     * <p>HOLD, THEN LET GO. Full strength for the first three quarters and a smoothstep out of the last
     * one. It used to be {@code exp(-3t)}, which was written when an impact was a one-second flash - and
     * these are cinematic effects that now run for tens of seconds, so it left the blast at 22 % by the
     * halfway mark and 5 % at the end. A two-kilometre warhead popped for a second and then spent
     * twenty-four seconds invisible. The apex queue already used this shape for the same reason ("a
     * linear fade made a ninety-second Soul Furnace invisible for most of its own run"); the blast queue
     * never got it.
     *
     * <p>It lives here rather than in the client so the logic harness can assert the curve instead of
     * somebody re-deriving it by eye from a screen recording.
     *
     * @param t 0 at the detonation, 1 when the effect ends
     * @return 0..1
     */
    public static float impactEnvelope(float t) {
        float u = Math.max(0.0F, Math.min(1.0F, (t - 0.75F) / 0.25F));
        return 1.0F - u * u * (3.0F - 2.0F * u);
    }

    /**
     * How big the skybox volume is for a blast of this size, in blast radii - or 0 for "do not draw one".
     *
     * <p>One function, because the shader marches {@code radius * SkyBox} while the renderer draws a cube
     * {@code skyHalf} across, and if those two are derived separately they drift. Here {@code skyHalf} is
     * computed first, then divided back out, so {@code radius * skyBoxRadii(...)} is exactly the cube that
     * was drawn by construction - there is no second number to get wrong.
     *
     * <p>Returns 0 when the blast is already so wide that the block cap leaves no room outside its own
     * cube. A kilometre-wide fireball has no skybox around it, which is correct: it IS the sky.
     *
     * @param blastRadius   the blast radius in blocks
     * @param preferredRadii how many blast radii the sky should span when nothing caps it
     * @param maxBlocks     the cap on the sky cube's half-extent, in blocks
     * @param proxyRadii    the blast cube's own half-extent in radii; the sky must be bigger to be worth drawing
     */
    public static double skyBoxRadii(double blastRadius, double preferredRadii, double maxBlocks,
                                     double proxyRadii) {
        double radius = Math.max(1.0D, blastRadius);
        double skyHalf = Math.min(radius * Math.max(0.0D, preferredRadii), Math.max(0.0D, maxBlocks));
        double radii = skyHalf / radius;
        return radii > proxyRadii ? radii : 0.0D;
    }

    /**
     * How much of a blast's brightness survives the distance to the camera.
     *
     * <p>Deliberately NOT squared, and never below a floor. Squaring was wrong for warheads this big: a
     * 469-block blast has a 589-block reach, so standing at the edge of your own detonation gave
     * 0.20 squared = 0.04, which is under the renderer's own skip threshold - the closer you were to a
     * kilometre-wide explosion, the more likely it was to not be drawn at all. You are looking AT the
     * fireball; distance should thin it, not delete it.
     */
    public static float distanceFalloff(double distance, double reach) {
        if (reach <= 0.0D) {
            return 0.0F;
        }
        double proximity = 1.0D - Math.min(1.0D, Math.max(0.0D, distance) / reach);
        return (float) (0.30D + 0.70D * proximity);
    }

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
