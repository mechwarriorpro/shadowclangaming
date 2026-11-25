package com.shadowclangaming.spell;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * One entry in a spell. This is the whole component list - the "base effects" of the old Ars Magica 2
 * school, reimplemented from scratch.
 *
 * THE MANA RULE (old Ars Magica style, kept deliberately):
 *   a component's cost is driven by its TIER, not its effect:  25 x tier x tier
 *   tier 1 = 25, tier 2 = 100, tier 3 = 225, tier 4 = 400, tier 5 = 625
 * so a spell is not priced per point of damage, it is priced by how exotic the magic is. Ten tier-1
 * components (250 mana) are cheaper than two tier-5 ones (1250), which is exactly the trade the old
 * system forced on you.
 *
 * THE COUNT IS UNCAPPED. There is no 10-component or 20-component ceiling: a spell may hold as many
 * components as the player can pay for, and each one is applied in order. A thousand-component spell
 * is legal, will cost a fortune, and will be very slow to write down.
 */
public enum SpellComponent implements StringRepresentable {

    // ---- tier 1: the cheap physical school -------------------------------------------------
    PHYSICAL_DAMAGE("physical_damage", 1, Category.DAMAGE),
    FIRE_DAMAGE("fire_damage", 1, Category.DAMAGE),
    FROST_DAMAGE("frost_damage", 1, Category.DAMAGE),
    HEAL("heal", 1, Category.HEAL),
    HASTE("haste", 1, Category.BUFF),
    JUMP_BOOST("jump_boost", 1, Category.BUFF),
    SLOW_FALL("slow_fall", 1, Category.BUFF),
    NIGHT_VISION("night_vision", 1, Category.BUFF),
    WATER_BREATHING("water_breathing", 1, Category.BUFF),
    SLOWNESS("slowness", 1, Category.DEBUFF),
    KNOCKBACK("knockback", 1, Category.UTILITY),
    IGNITE("ignite", 1, Category.UTILITY),
    EXTINGUISH("extinguish", 1, Category.UTILITY),
    PLACE_LIGHT("place_light", 1, Category.UTILITY),
    LIFE_TAP("life_tap", 1, Category.UTILITY),

    // ---- tier 2: the common school ---------------------------------------------------------
    LIGHTNING_DAMAGE("lightning_damage", 2, Category.DAMAGE),
    REGENERATION("regeneration", 2, Category.HEAL),
    ABSORPTION("absorption", 2, Category.BUFF),
    RESISTANCE("resistance", 2, Category.BUFF),
    STRENGTH("strength", 2, Category.BUFF),
    SPEED("speed", 2, Category.BUFF),
    WEAKNESS("weakness", 2, Category.DEBUFF),
    POISON("poison", 2, Category.DEBUFF),
    BLINDNESS("blindness", 2, Category.DEBUFF),
    NAUSEA("nausea", 2, Category.DEBUFF),
    HUNGER("hunger", 2, Category.DEBUFF),
    PULL("pull", 2, Category.UTILITY),
    DIG("dig", 2, Category.UTILITY),

    // ---- tier 3: the expensive school ------------------------------------------------------
    MAGIC_DAMAGE("magic_damage", 3, Category.DAMAGE),
    LIFESTEAL("lifesteal", 3, Category.DAMAGE),
    LEVITATION("levitation", 3, Category.DEBUFF),
    INVISIBILITY("invisibility", 3, Category.BUFF),
    DARKNESS("darkness", 3, Category.DEBUFF),
    BLINK("blink", 3, Category.UTILITY),
    DISPEL("dispel", 3, Category.UTILITY),

    // ---- tier 4: the ruinous school --------------------------------------------------------
    WITHER("wither", 4, Category.DEBUFF),
    LIGHTNING_STRIKE("lightning_strike", 4, Category.DAMAGE),
    LINGERING_ZONE("lingering_zone", 4, Category.AREA),

    // ---- tier 5: the end of the list -------------------------------------------------------
    GRAVITY_WELL("gravity_well", 5, Category.AREA),
    WITHER_STORM("wither_storm", 5, Category.AREA),
    ANNIHILATE("annihilate", 5, Category.DAMAGE),

    /**
     * Throws the thing you hit somewhere else. Up to 16 blocks, in a random direction, and only ever
     * into a spot something can actually stand in - a blind teleport that puts the victim inside stone
     * is a bug, not a spell.
     */
    RANDOM_TELEPORT("random_teleport", 3, Category.UTILITY),
    /**
     * Only meaningful inside a PROJECTILE step: the bolt carries the rest of the spell, and where it
     * lands it splits into five more bolts that home onto whatever is nearby. Each one delivers a full
     * copy of the remaining sequence, so a cluster strike is five spells for the price of one cast.
     */
    CLUSTER_STRIKE("cluster_strike", 4, Category.AREA),
    /**
     * Freezes the target in time for 15 seconds: no movement, no attacks, no block breaking, no item
     * use, no dropping things. See {@link Chronosplit}.
     */
    CHRONOSPLIT("chronosplit", 5, Category.DEBUFF),
    /**
     * The ordinary firestorm: a column of fire for six seconds that burns what it touches and sets the
     * ground alight. It respects the damage cooldown, and it destroys items the way fire always does.
     */
    FIRE_STORM("fire_storm", 4, Category.AREA),
    /**
     * The upgrade. Wider, hotter, and it does not wait for the damage cooldown between pulses. It never
     * places fire and never ignites anything, so loot on the ground survives it.
     */
    HELLSTORM("hellstorm", 5, Category.AREA),

    // ============================================================================================
    // THE SECOND SCHOOL. The first thirty came from the base Ars Magica 2 list; these are the rest of
    // it (Starstrike, Entangle and friends) plus Blood Magic, Thaumcraft and plain old D&D, re-priced
    // against this mod's own casting maths: a component is 25 x tier^2 mana, a rifle charges its
    // efficiency fraction of that per shot, and a step is multiplied by its modifiers.
    // ============================================================================================

    // ---- tier 1: cantrips ----------------------------------------------------------------------
    /** A snap of static. Cheap chip damage that arcs visibly. */
    SPARK("spark", 1, Category.DAMAGE),
    /** Cold hands: a little frost damage and a short slow. */
    CHILL("chill", 1, Category.DEBUFF),
    /** Feather fall for half a minute - the one spell a builder actually wants. */
    FEATHER("feather", 1, Category.BUFF),
    /** Fills the target's hunger bar. Works on you, which makes it a poor man's regeneration. */
    SATIATE("satiate", 1, Category.HEAL),

    // ---- tier 2: the common school -------------------------------------------------------------
    /**
     * Roots: nothing moves. Slowness V plus zeroed momentum for six seconds - the victim can still
     * fight, but they cannot leave.
     */
    ENTANGLE("entangle", 2, Category.DEBUFF),
    /** Fire and light: damage plus blindness for four seconds. */
    SEARING_LIGHT("searing_light", 2, Category.DAMAGE),
    /** Absorption and resistance together: the humble defensive spell. */
    MAGIC_SHIELD("magic_shield", 2, Category.BUFF),
    /** Nausea and darkness: a cheap way to make somebody miss. */
    MIND_FOG("mind_fog", 2, Category.DEBUFF),
    /** Nothing to hear: silence on the victim (no item use) for five seconds. */
    SILENCE("silence", 2, Category.DEBUFF),

    // ---- tier 3: the expensive school ----------------------------------------------------------
    /** A ring of ice: frost damage and freezing to everything within four blocks. */
    FROST_NOVA("frost_nova", 3, Category.AREA),
    /** Damage plus wither, and the caster drinks the difference. */
    NECROTIC_DRAIN("necrotic_drain", 3, Category.DAMAGE),
    /** Knocks the weapon out of the victim's hand. */
    DISARM("disarm", 3, Category.UTILITY),
    /** Resistance, fire resistance and a clean sweep of negative effects. */
    ARCANE_WARD("arcane_ward", 3, Category.BUFF),
    /** A longer, aimed blink: the caster steps up to twelve blocks along their line of sight. */
    WARP_STEP("warp_step", 3, Category.UTILITY),
    /** Blood Magic, imported: pay two hearts, gain 400 mana. Self only. */
    BLOOD_SACRIFICE("blood_sacrifice", 3, Category.UTILITY),
    /** A small, clean explosion. No block damage, no fire, no crater. */
    DETONATE("detonate", 3, Category.UTILITY),

    // ---- tier 4: the ruinous school ------------------------------------------------------------
    /** A star falls on the target: heavy damage and fire in the crater. */
    STARSTRIKE("starstrike", 4, Category.DAMAGE),
    /** Extra damage to the undead, less to everything else. Paladins, this is yours. */
    HOLY_SMITE("holy_smite", 4, Category.DAMAGE),
    /** Damages the living by how badly hurt the caster already is, and costs the caster a heart. */
    BLOOD_LANCE("blood_lance", 4, Category.DAMAGE),
    /** Lightning that jumps: the target plus up to four more within eight blocks. */
    CHAIN_LIGHTNING("chain_lightning", 4, Category.DAMAGE),
    /** Everything within six blocks is thrown upward. */
    GRAVITY_FLIP("gravity_flip", 4, Category.UTILITY),
    /** The floor turns to treacle: slowness IV and mining fatigue III in a wide area. */
    TIME_SLOW("time_slow", 4, Category.DEBUFF),
    /** Levitation plus slow falling: flight, with a landing you survive. */
    FLIGHT("flight", 4, Category.BUFF),
    /** Heals, then strips every harmful effect in the area. The anti-necromancer button. */
    SANCTUARY("sanctuary", 4, Category.HEAL),
    /** Plague: poison, hunger and wither, and it spreads to everything nearby. */
    PLAGUE_BEARER("plague_bearer", 4, Category.DEBUFF),

    // ---- tier 5: the end of the list -----------------------------------------------------------
    /** Six lightning bolts land across a wide area at once. */
    STORM_CALL("storm_call", 5, Category.AREA),
    /** A rift that drags everything in and withers it. Gravity Well's crueller sibling. */
    THAUMIC_RIFT("thaumic_rift", 5, Category.AREA),
    /** Damage that pays the caster back in health, and keeps paying if it kills. */
    SOUL_HARVEST("soul_harvest", 5, Category.DAMAGE),
    /** Throws whatever it hits up to 64 blocks away. Hostiles only, by default. */
    BANISH("banish", 5, Category.UTILITY),

    // ============================================================================================
    // OVERLORD. Ainz's book, minus the instant-death-everything: death magic here is heavy damage
    // with an execution window, because a spell that simply kills players is a bug report.
    // ============================================================================================

    // ---- tier 1 ----
    /** A hurled shard of force. Cheap, reliable, no frills. */
    MAGIC_ARROW("magic_arrow", 1, Category.DAMAGE),
    /** A puff of light: four seconds of blindness, no damage. */
    FLASH("flash", 1, Category.DEBUFF),

    // ---- tier 4 ----
    /** Hell Flame: fire that keeps burning, and the ground with it. */
    HELL_FLAME("hell_flame", 4, Category.DAMAGE),
    /** Cry of the Banshee: a scream that curses everything in earshot with darkness and dread. */
    CRY_OF_THE_BANSHEE("cry_of_the_banshee", 4, Category.DEBUFF),

    // ---- tier 5 ----
    /**
     * Grasp Heart. Above 30 % health it is "only" heavy damage and weakness; at or below 30 % the
     * heart is crushed and the target dies outright. The execution window is what makes it a finisher
     * rather than a delete button.
     */
    GRASP_HEART("grasp_heart", 5, Category.DAMAGE),
    /** Fallen Down: a column of holy fire from above. The heaviest single-target spell in the mod. */
    FALLEN_DOWN("fallen_down", 5, Category.DAMAGE),
    /** Negative Burst: a sphere of death energy that withers and drains everything inside it. */
    NEGATIVE_BURST("negative_burst", 5, Category.AREA),
    /** Black Hole: a real one. Everything within eight blocks is dragged in and ground up. */
    BLACK_HOLE("black_hole", 5, Category.AREA),

    // ============================================================================================
    // KONOSUBA. Explosion, holy water, drain touch, and a party that keeps solving problems with
    // the wrong spell at the right time.
    // ============================================================================================

    // ---- tier 2 ----
    /** Create Water: puts out fire, soaks the ground, and hurts anything made of flame. */
    CREATE_WATER("create_water", 2, Category.UTILITY),
    /** Wind Blast: sharp air, and a shove to go with it. */
    WIND_BLAST("wind_blaster", 2, Category.UTILITY),
    /** Lurk: invisible and quick for eight seconds. Not a combat spell. */
    LURK("lurk", 2, Category.BUFF),

    // ---- tier 3 ----
    /**
     * Drain Touch: life AND mana move from the target to the caster. Against a player that is the
     * rudest spell in the mod, which is exactly why it is tier 3.
     */
    DRAIN_TOUCH("drain_touch", 3, Category.DAMAGE),

    // ---- tier 4 ----
    /** Cursed Crystal Prison: encased in ice. Frozen solid, and it hurts. */
    CURSED_CRYSTAL_PRISON("cursed_crystal_prison", 4, Category.DEBUFF),
    /** Turn Undead: ruinous against the undead, and a waste of mana against anything else. */
    TURN_UNDEAD("turn_undead", 4, Category.DAMAGE),
    /** Purification: cleanses the area of curses and burns the undead standing in it. */
    PURIFICATION("purification", 4, Category.HEAL),

    // ---- tier 5 ----
    /**
     * EXPLOSION. Megumin's, in full: everything within twelve blocks takes enormous damage, the
     * caster is left on their knees, and the crater is the point. It costs more than any other
     * component in the mod.
     */
    EXPLOSION("explosion", 5, Category.AREA),
    /** God Requiem: a hymn that heals the living and detonates the dead. */
    GOD_REQUIEM("god_requiem", 5, Category.HEAL),
    /**
     * BAD GAS. A stink so bad it launches people: the victim is thrown UP and along whichever way it
     * was facing, so whatever was charging at you sails over your shoulder. With Sable: Ragdolls
     * installed the body becomes a real physics ragdoll; without it, the mob loses its AI and tumbles
     * for three seconds instead. Small damage, nausea and hunger on contact.
     */
    BAD_GAS("bad_gas", 3, Category.DEBUFF);

    /** What the component mostly does. Used for colour, tooltips and the creative book. */
    public enum Category {
        DAMAGE, HEAL, BUFF, DEBUFF, UTILITY, AREA
    }

    public static final Codec<SpellComponent> CODEC = StringRepresentable.fromEnum(SpellComponent::values);

    /** Mana per tier step: tier x tier x this. */
    public static final long COST_PER_TIER_SQUARED = 25L;

    private final String id;
    private final int tier;
    private final Category category;

    SpellComponent(String id, int tier, Category category) {
        this.id = id;
        this.tier = tier;
        this.category = category;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public String id() {
        return id;
    }

    public int tier() {
        return tier;
    }

    public Category category() {
        return category;
    }

    /** Mana this single component adds. Tier squared - see the class comment. */
    public long manaCost() {
        return COST_PER_TIER_SQUARED * (long) tier * (long) tier;
    }

    public String translationKey() {
        return "component.shadowclangaming." + id;
    }

    public static SpellComponent byId(String id) {
        for (SpellComponent component : values()) {
            if (component.id.equalsIgnoreCase(id)) {
                return component;
            }
        }
        return PHYSICAL_DAMAGE;
    }
}
