package com.shadowclangaming.warhead.apex;

/**
 * The seventeen apex warheads - the end of the ladder, above antimatter.
 *
 * WHAT MAKES THEM A FAMILY. Every one of them is a *staged* weapon: it does not go bang once and stop.
 * It has a timeline (charge, front, collapse, release) and the destruction happens along that timeline,
 * which is what makes a black hole feel like a black hole and a shockwave hammer feel like a hammer.
 * Everything shared lives here; everything specific lives in {@link ApexWarheads} and
 * {@link ApexTimeline}.
 *
 * WHY A SECOND ENUM. {@link WarheadType} is the *item* roster: one entry, one missile, one blast. These
 * seventeen share so much machinery (fronts, rings, tendrils, shaders, staged execution) that giving each
 * one an enum entry in WarheadType would have meant seventeen near-identical detonate branches. Instead
 * each apex is one row of data here, and WarheadType forwards to {@link ApexWarheads#fire}.
 *
 * NO MINECRAFT IMPORTS. Radius, timing, front shape and visual keys are pure data, so the harness can
 * assert the whole ladder without a game.
 */
public enum Apex {

    /**
     * A black hole that eats the battlefield. The front marches inward, blocks spiral in, and what is
     * left is a void.
     */
    WORLD_EATER("world_eater", 96, Front.INWARD, Style.VOID, "lensing", 6, 12, 0),
    /** An artificial star above the target that brightens, then detonates. */
    STELLAR_REQUIEM("stellar_requiem", 128, Front.OUTWARD, Style.STELLAR, "flash", 3, 20, 60),
    /** A tear in spacetime: cracks, then a vortex that eats the landscape. */
    REALITY_BREAKER("reality_breaker", 120, Front.OUTWARD, Style.RIFT, "rift", 4, 15, 20),
    /** Successive gravitational shockwaves, each ring wider than the last. */
    SPACETIME_HAMMER("spacetime_hammer", 110, Front.OUTWARD, Style.HAMMER, "shock", 5, 10, 0),
    /** A spherical collapse into a core, then one final outward pulse. */
    UNIVERSAL_IMPLODER("universal_imploder", 100, Front.INWARD, Style.IMPLODE, "lensing", 5, 12, 20),
    /** Meteors, spectral lightning and a sky the colour of blood. */
    RAGNAROK("ragnarok", 150, Front.OUTWARD, Style.APOCALYPSE, "blood", 6, 12, 40),
    /** A glowing rune is engraved into the land, and completes, and then detonates. */
    WORLD_RUNE("world_rune", 140, Front.STATIC, Style.RUNE, "runes", 4, 25, 80),
    /** Hundreds of portals into somewhere else; tendrils drag the world through them. */
    ABYSSAL_COVENANT("abyssal_covenant", 90, Front.STATIC, Style.ABYSS, "void", 4, 15, 20),
    /** A spectral vortex that eats the living and turns them into a bigger explosion. */
    SOUL_FURNACE("soul_furnace", 80, Front.INWARD, Style.SOUL, "soul", 5, 12, 30),
    /** A magical sphere ringed with spell circles that absorbs the ground, then erupts. */
    ARCANE_SINGULARITY("arcane_singularity", 105, Front.INWARD, Style.ARCANE, "rift", 4, 15, 30),
    /** A miniature quasar whose jet carves a canyon across the map. */
    QUASAR_LANCE("quasar_lance", 64, Front.STATIC, Style.QUASAR, "shock", 3, 12, 10),
    /** An artificial star whose expanding corona melts everything it touches. */
    STARFORGE("starforge", 130, Front.OUTWARD, Style.STELLAR, "flash", 4, 18, 50),
    /** A point of light that grows into a universe-sized sphere before fading. */
    BIG_BANG_SEED("big_bang_seed", 160, Front.OUTWARD, Style.BIGBANG, "flash", 6, 10, 20),
    /** Corrupted geometry: impossible colour, floating fragments, deleted land. */
    REALITY_CORRUPTOR("reality_corruptor", 95, Front.STATIC, Style.CORRUPT, "rift", 4, 15, 10),
    /** A motionless black core that absorbs everything, then lets go all at once. */
    ABSOLUTE_ZERO("absolute_zero", 85, Front.INWARD, Style.ZERO, "void", 3, 40, 100),
    /** An annihilation front, then a secondary implosion, under a black sky. */
    OMEGA("omega", 200, Front.OUTWARD, Style.OMEGA, "blood", 5, 15, 30),
    /** The last weapon: seven rings, dimensional fractures, and a lot of missing map. */
    ABSOLUTE_ANNIHILATION("absolute_annihilation", 256, Front.OUTWARD, Style.ANNIHILATION, "rift", 7, 14, 40);

    /** Which way the destruction front travels. */
    public enum Front {
        /** Grows outward from the impact point (a shockwave, an expanding star). */
        OUTWARD,
        /** Shrinks toward the impact point, then flashes out at the end (a black hole). */
        INWARD,
        /** Does not travel: the whole marked volume goes at once, on the schedule. */
        STATIC
    }

    /** The look of the thing. Drives the client particle field; see {@code client/ApexVisuals}. */
    public enum Style {
        VOID, STELLAR, RIFT, HAMMER, IMPLODE, APOCALYPSE, RUNE, ABYSS,
        SOUL, ARCANE, QUASAR, BIGBANG, CORRUPT, ZERO, OMEGA, ANNIHILATION
    }

    private final String id;
    private final int radius;
    private final Front front;
    private final Style style;
    private final String shader;
    private final int stages;
    private final int intervalTicks;
    private final int chargeTicks;

    Apex(String id, int radius, Front front, Style style, String shader,
         int stages, int intervalTicks, int chargeTicks) {
        this.id = id;
        this.radius = radius;
        this.front = front;
        this.style = style;
        this.shader = shader;
        this.stages = stages;
        this.intervalTicks = intervalTicks;
        this.chargeTicks = chargeTicks;
    }

    public String id() {
        return id;
    }

    /** Default radius in blocks. The pad's slider overrides it - nothing here is a cap. */
    public int radius() {
        return radius;
    }

    public Front front() {
        return front;
    }

    public Style style() {
        return style;
    }

    /** Name of the post-processing effect under {@code assets/shadowclangaming/post_effect/}. */
    public String shader() {
        return shader;
    }

    /** How many destruction stages this weapon runs. Always at least 1. */
    public int stages() {
        return stages;
    }

    /** Ticks between stages. */
    public int intervalTicks() {
        return intervalTicks;
    }

    /** Ticks of visible charge before the first destructive stage. */
    public int chargeTicks() {
        return chargeTicks;
    }

    /** Total lifetime of the weapon in ticks, charge included. Used by the client FX and the scheduler. */
    public int totalTicks() {
        return chargeTicks + intervalTicks * (stages - 1) + 1;
    }

    /** Blast power that matches this apex's radius, on the same curve the rest of the mod uses. */
    public float power() {
        return radius * 0.8F;
    }

    public String translationKey() {
        return "warhead.shadowclangaming." + id;
    }

    /**
     * The apex with this id, or null.
     *
     * Keyed by the id string rather than by a {@code WarheadType} on purpose: it keeps this class free of
     * Minecraft imports (the harness compiles it), and it lets the network layer ask the same question
     * with the string that arrived over the wire.
     */
    public static Apex byId(String id) {
        if (id == null) {
            return null;
        }
        for (Apex apex : values()) {
            if (apex.id.equals(id)) {
                return apex;
            }
        }
        return null;
    }
}
