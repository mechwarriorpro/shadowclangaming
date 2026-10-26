package com.shadowclangaming.warhead;

/**
 * How a warhead looks when it goes off.
 *
 * One thing to have a huge boom; another to make it stylish. Every warhead maps to a STYLE, and a style owns
 * a post-processing chain, a flash colour, a screen-shake weight and how long the effect runs. The mapping
 * lives here - in pure Java, with no Minecraft imports - so the harness can prove that every warhead in the
 * roster has a style and that no style id is a typo.
 *
 * WHY STYLES AND NOT 38 SHADERS. Thirty-eight bespoke fragment shaders would be thirty-eight chances for one
 * of them to fail to compile on somebody's driver, and most of them would be the same shader with a
 * different tint. Eight styles cover the roster by *meaning* (fire, radiation, EMP, toxic, frost, void,
 * money, plain high explosive), and each style still gets its own colour and particle treatment, so a
 * fission warhead does not look like an HE charge.
 *
 * The shader names here must match the files in
 * {@code assets/shadowclangaming/post_effect/<name>.json} - {@code tools/gen_blast_shaders.py} generates all
 * three files per style from this list, so the two cannot drift.
 */
public enum BlastStyle {

    /** Plain high explosive: a white-hot flash and a hard shockwave ring. */
    FLASH("blast_flash", "flash", 0xFFF6E7C1, 0.85F, 0.55F, 26),
    /** Fire: orange core, embers, longer burn-in. */
    FIRE("blast_fire", "fire", 0xFFFF8A2B, 0.80F, 0.45F, 34),
    /** Radiation: sickly green-yellow wash that lingers. */
    RAD("blast_rad", "rad", 0xFFB6E36A, 0.70F, 0.40F, 46),
    /** EMP: blue, thin, with a hard chromatic split and no warmth at all. */
    EMP("blast_emp", "emp", 0xFF7FD4FF, 0.65F, 0.35F, 30),
    /** Contagion: a crawling green vignette that breathes. */
    TOXIC("blast_toxic", "toxic", 0xFF6FCF57, 0.62F, 0.30F, 52),
    /** Absolute zero: cyan frost that creeps in from the edges. */
    FROST("blast_frost", "frost", 0xFF9FE8FF, 0.66F, 0.28F, 44),
    /** Antimatter and the big imploders: the light goes the wrong way. */
    VOID("blast_void", "void", 0xFFB273FF, 0.95F, 0.75F, 44),
    /** Numismatic: gold, because the blast is literally made of money. */
    COIN("blast_coin", "coin", 0xFFFFD24A, 0.75F, 0.40F, 34),
    /**
     * The chain of last resort. Every other style can fall back to this one, and this one falls back to the
     * HUD overlay in {@code client/BlastOverlay} - so a machine with no working post-processing still sees
     * *something* happen on screen instead of a silent crater.
     */
    FALLBACK("blast_fallback", "fallback", 0xFFFFFFFF, 0.70F, 0.35F, 24);

    private final String shader;
    private final String id;
    private final int colour;
    private final float flash;
    private final float shake;
    private final int baseTicks;

    BlastStyle(String shader, String id, int colour, float flash, float shake, int baseTicks) {
        this.shader = shader;
        this.id = id;
        this.colour = colour;
        this.flash = flash;
        this.shake = shake;
        this.baseTicks = baseTicks;
    }

    /** Post-chain name: {@code assets/shadowclangaming/post_effect/<shader>.json}. */
    public String shader() {
        return shader;
    }

    /** Stable id used in packets and config, never the enum ordinal. */
    public String id() {
        return id;
    }

    /** ARGB, used for the HUD fallback and for particle tinting. */
    public int colour() {
        return colour;
    }

    /** 0..1: how bright the flash is at full strength. */
    public float flash() {
        return flash;
    }

    /** 0..1: how much camera shake this style asks for. */
    public float shake() {
        return shake;
    }

    /** Effect lifetime for a nominal blast, before the power scaling below. */
    public int baseTicks() {
        return baseTicks;
    }

    /**
     * How long this effect should run for a warhead of a given power.
     *
     * Bigger bangs get a longer tail, but the curve is logarithmic and hard-capped. The cap used to be 90
     * ticks; the effects are world geometry now rather than a screen grade (see {@link BlastMotif}), so a
     * long tail costs vertices for a few seconds instead of a filtered framebuffer, and the ceiling is
     * {@link BlastMotif#MAX_TICKS} - one minute.
     *
     * <p>This is the STYLE curve: nine numbers shared by thirty-eight weapons. The authoritative duration
     * of a detonation is {@link BlastMotif#durationTicks}, which is per-warhead and is what
     * {@link BlastFx} sends and {@link ExplosivePackaging} turns into a fuse.
     */
    public int ticksFor(float power) {
        double scaled = baseTicks + Math.log1p(Math.max(0.0D, power)) * 12.0D;
        return (int) Math.max(12.0D, Math.min(BlastMotif.MAX_TICKS, Math.round(scaled)));
    }

    /** Look up by packet id; unknown ids degrade to {@link #FALLBACK} rather than throwing. */
    public static BlastStyle byId(String id) {
        if (id != null) {
            for (BlastStyle style : values()) {
                if (style.id.equals(id) || style.shader.equals(id)) {
                    return style;
                }
            }
        }
        return FALLBACK;
    }

    /**
     * Which style a warhead detonates with.
     *
     * The apex warheads are not here: they own a timeline and hand off to {@code ApexWarheads}, which has its
     * own chains. Anything this method does not recognise gets {@link #FALLBACK}, so a warhead added in a
     * later round still flashes instead of doing nothing.
     */
    public static BlastStyle forWarhead(String warheadId) {
        if (warheadId == null) {
            return FALLBACK;
        }
        return switch (warheadId) {
            case "he", "dud", "decoy", "cluster" -> FLASH;
            case "incendiary", "world_rune", "soul_furnace", "ragnarok", "starforge", "beef",
                 "violator", "clucker" -> FIRE;
            case "fission", "fusion", "spell" -> RAD;
            case "contagion" -> TOXIC;
            case "emp", "tracking", "minefield" -> EMP;
            case "absolute_zero", "bulwark" -> FROST;
            case "antimatter", "omega", "absolute_annihilation", "universal_imploder", "world_eater",
                 "abyssal_covenant", "arcane_singularity", "reality_breaker", "reality_corruptor",
                 "spacetime_hammer", "stellar_requiem", "quasar_lance", "big_bang_seed" -> VOID;
            case "numismatic" -> COIN;
            case "cargo", "constructor", "spatial" -> FLASH;
            default -> FALLBACK;
        };
    }
}
