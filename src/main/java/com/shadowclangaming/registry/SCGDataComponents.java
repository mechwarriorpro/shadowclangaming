package com.shadowclangaming.registry;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.chem.Element;
import com.shadowclangaming.gun.GunStats;
import com.shadowclangaming.spell.Spell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Custom data components - the 1.20.5+ replacement for "NBT on an ItemStack".
 *
 * Two rules that save a lot of pain:
 *   persistent(...)          how it is written to disk
 *   networkSynchronized(...) how it is sent to the client
 * Forget networkSynchronized and the tooltip will be blank on the client even though the server knows
 * the value.
 */
public final class SCGDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(ShadowClanGaming.MOD_ID);

    /** Which element is inside an Element Vial. Vials of different elements never stack. */
    public static final Supplier<DataComponentType<Element>> ELEMENT = DATA_COMPONENTS.registerComponentType(
            "element",
            builder -> builder.persistent(Element.CODEC).networkSynchronized(Element.STREAM_CODEC));

    /** Where a target designator is pointing (legacy/manual use). */
    public static final Supplier<DataComponentType<BlockPos>> TARGET_POS = DATA_COMPONENTS.registerComponentType(
            "target_pos",
            builder -> builder.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC));

    /** Which launch pad a designator is bound to. */
    public static final Supplier<DataComponentType<BlockPos>> BOUND_PAD = DATA_COMPONENTS.registerComponentType(
            "bound_pad",
            builder -> builder.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC));

    /** Dimension id of the bound pad, so a designator can't fire across dimensions. */
    public static final Supplier<DataComponentType<String>> BOUND_DIMENSION = DATA_COMPONENTS.registerComponentType(
            "bound_dimension",
            builder -> builder.persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));

    /** Energy stored in a target designator (FE). */
    public static final Supplier<DataComponentType<Integer>> ENERGY = DATA_COMPONENTS.registerComponentType(
            "energy",
            builder -> builder.persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /**
     * Currency loaded into a Numismatic warhead, in the smallest unit (1 = 1 spur).
     * LONG, not INT: there is no cap on how much a player may load, and 256 Suns is 1,048,576 units.
     */
    public static final Supplier<DataComponentType<Long>> FUNDING = DATA_COMPONENTS.registerComponentType(
            "funding",
            builder -> builder.persistent(com.mojang.serialization.Codec.LONG)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG));

    /** Bank Card: which withdrawal tier is selected. */
    public static final Supplier<DataComponentType<Integer>> CARD_TIER = DATA_COMPONENTS.registerComponentType(
            "card_tier",
            builder -> builder.persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /** Bank Card: last balance seen, cached for the tooltip (the client cannot query the server API). */
    public static final Supplier<DataComponentType<Long>> CARD_BALANCE = DATA_COMPONENTS.registerComponentType(
            "card_balance",
            builder -> builder.persistent(com.mojang.serialization.Codec.LONG)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG));

    /**
     * Bank Card: the UUID of the player whose EconomyCraft balance the card draws on.
     * Set the first time a player uses the card ("signing" it). The Auto-Trader refuses to work
     * with an unsigned card, because it has no other way to know whose account to credit.
     */
    public static final Supplier<DataComponentType<String>> CARD_OWNER = DATA_COMPONENTS.registerComponentType(
            "card_owner",
            builder -> builder.persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));

    /**
     * The spell inscribed into a rifle or loaded into a spell warhead.
     *
     * A whole spell in one component: shape plus an ordered component list. Because it is persistent
     * AND networkSynchronized, the tooltip can describe the spell on the client without a packet
     * round trip, and a rifle can be dropped, stored or traded with its magic intact.
     */
    /**
     * What was loaded into a missile by right-clicking it with items.
     *
     * A LIST OF STACKS, and a component rather than a block entity field, because the whole point is
     * that the missile is the container: you load it in your hand, carry it, and the launcher only has
     * to read it. Cargo, constructor blocks and AE2 cells all use this one field.
     */
    public static final Supplier<DataComponentType<java.util.List<ItemStack>>> PAYLOAD =
            DATA_COMPONENTS.registerComponentType(
                    "payload",
                    builder -> builder
                            .persistent(ItemStack.CODEC.listOf())
                            .networkSynchronized(ItemStack.STREAM_CODEC.apply(
                                    net.minecraft.network.codec.ByteBufCodecs.list(
                                            com.shadowclangaming.warhead.MissilePayload.MAX_STACKS))));

    /**
     * The pad's radius slider, in blocks.
     *
     * "Instead of max blast calculation use a slider instead so the player can set radius, also set
     * exact radius." -1 means "not set", so the warhead falls back to its own rule (funding for a
     * Numismatic warhead, payload count for a constructor).
     */
    public static final Supplier<DataComponentType<Integer>> RADIUS = DATA_COMPONENTS.registerComponentType(
            "radius",
            builder -> builder.persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /**
     * Which warhead is fitted to a missile module, as a {@link com.shadowclangaming.warhead.WarheadType}
     * id. Absent = an empty module, which still flies (see WarheadType#DUD).
     */
    public static final Supplier<DataComponentType<String>> MODULE_WARHEAD = DATA_COMPONENTS.registerComponentType(
            "module_warhead",
            builder -> builder.persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));

    /**
     * The altitude, in blocks, at which a cluster warhead breaks up.
     *
     * Absent = the frame's own proximity rule ({@code ModuleKind.CLUSTER_TRIGGER} blocks from the target).
     * Setting it lets a player open the pattern high (a wide, soft spread that covers a whole base) or low
     * (a tight pattern that drills one spot). It rides on the missile stack, so it survives being unloaded,
     * carried to another silo and fired from a designator.
     */
    public static final Supplier<DataComponentType<Integer>> SPLIT_ALTITUDE =
            DATA_COMPONENTS.registerComponentType(
                    "split_altitude",
                    builder -> builder.persistent(com.mojang.serialization.Codec.INT)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /**
     * Which warhead a suit of armour is loaded with, as a {@link com.shadowclangaming.warhead.WarheadType}
     * id. Absent = the suit falls back to its built-in charge. Used by the kamikaze vest, and by anything
     * else later that wants an "armed with X" property.
     */
    public static final Supplier<DataComponentType<String>> VEST_PAYLOAD = DATA_COMPONENTS.registerComponentType(
            "vest_payload",
            builder -> builder.persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));

    public static final Supplier<DataComponentType<Spell>> SPELL = DATA_COMPONENTS.registerComponentType(
            "spell",
            builder -> builder.persistent(Spell.CODEC).networkSynchronized(Spell.STREAM_CODEC));

    /** Rifle stat: highest component tier the frame can hold. Written by the blueprint table. */
    public static final Supplier<DataComponentType<Integer>> RIFLE_TIER = DATA_COMPONENTS.registerComponentType(
            "rifle_tier",
            builder -> builder.persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /** Rifle stat: percentage of a spell's mana cost the frame charges (60..95). */
    public static final Supplier<DataComponentType<Integer>> RIFLE_EFFICIENCY = DATA_COMPONENTS.registerComponentType(
            "rifle_efficiency",
            builder -> builder.persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /** The blocks a rifle was built from - what it renders as. See BuildShape. */
    public static final Supplier<DataComponentType<com.shadowclangaming.blueprint.BuildShape>> RIFLE_BUILD =
            DATA_COMPONENTS.registerComponentType(
                    "rifle_build",
                    builder -> builder.persistent(com.shadowclangaming.blueprint.BuildShape.CODEC)
                            .networkSynchronized(com.shadowclangaming.blueprint.BuildShape.STREAM_CODEC));

    /** How many Emitters the build has: each one is -5% cooldown and +2.5% mana. */
    public static final Supplier<DataComponentType<Integer>> RIFLE_EMITTERS =
            DATA_COMPONENTS.registerComponentType(
                    "rifle_emitters",
                    builder -> builder.persistent(com.mojang.serialization.Codec.INT)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /** Barrel STRENGTH set at the rifle bench: 1..128. Power, mana and cooldown all read it. */
    public static final Supplier<DataComponentType<Integer>> RIFLE_STRENGTH =
            DATA_COMPONENTS.registerComponentType(
                    "rifle_strength",
                    builder -> builder.persistent(com.mojang.serialization.Codec.INT)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /** Which emitter fires next. Cycles 0..emitters-1 on every shot - the multi-barrel rotation. */
    public static final Supplier<DataComponentType<Integer>> RIFLE_BARREL =
            DATA_COMPONENTS.registerComponentType(
                    "rifle_barrel",
                    builder -> builder.persistent(com.mojang.serialization.Codec.INT)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /**
     * The whole rolled gun: maker, rarity, element, six parts and every number that comes out of
     * them. One component, so a gun is a single immutable value.
     */
    public static final Supplier<DataComponentType<GunStats>> GUN = DATA_COMPONENTS.registerComponentType(
            "gun",
            builder -> builder.persistent(GunStats.CODEC).networkSynchronized(GunStats.STREAM_CODEC));

    /** Rounds left in the magazine. Separate from GUN so firing does not rewrite the whole stat block. */
    public static final Supplier<DataComponentType<Integer>> GUN_AMMO = DATA_COMPONENTS.registerComponentType(
            "gun_ammo",
            builder -> builder.persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /** Channel number stored in a Frequency Chip. 0..99. */
    public static final Supplier<DataComponentType<Integer>> FREQUENCY = DATA_COMPONENTS.registerComponentType(
            "frequency",
            builder -> builder.persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /** Upgrade Kit tier, 1..5. A kit only upgrades a block from tier n to n+1. */
    public static final Supplier<DataComponentType<Integer>> KIT_TIER = DATA_COMPONENTS.registerComponentType(
            "kit_tier",
            builder -> builder.persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /** UUID string of the entity a Tracker has locked. Empty string = no lock. */
    public static final Supplier<DataComponentType<String>> TRACKED_ENTITY = DATA_COMPONENTS.registerComponentType(
            "tracked_entity",
            builder -> builder.persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));

    /** Chemical formulas discovered and logged into the Chemist's Journal. */
    public static final Supplier<DataComponentType<java.util.List<String>>> JOURNAL_DISCOVERIES =
            DATA_COMPONENTS.registerComponentType(
                    "journal_discoveries",
                    builder -> builder.persistent(com.mojang.serialization.Codec.STRING.listOf())
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8
                                    .apply(net.minecraft.network.codec.ByteBufCodecs.list(500))));

    /** Chemical coating applied to a weapon: element and remaining hit charges. */
    public static final Supplier<DataComponentType<com.shadowclangaming.chem.ChemicalCoating>> CHEMICAL_COATING =
            DATA_COMPONENTS.registerComponentType(
                    "chemical_coating",
                    builder -> builder.persistent(com.shadowclangaming.chem.ChemicalCoating.CODEC)
                            .networkSynchronized(com.shadowclangaming.chem.ChemicalCoating.STREAM_CODEC));

    /** Chemical toxicant / element laced into a food item. */
    public static final Supplier<DataComponentType<Element>> POISONED_FOOD =
            DATA_COMPONENTS.registerComponentType(
                    "poisoned_food",
                    builder -> builder.persistent(Element.CODEC)
                            .networkSynchronized(Element.STREAM_CODEC));

    /** Chemical compound inside a compound test tube / vial. */
    public static final Supplier<DataComponentType<com.shadowclangaming.chem.Compound>> COMPOUND =
            DATA_COMPONENTS.registerComponentType(
                    "compound",
                    builder -> builder.persistent(com.shadowclangaming.chem.Compound.CODEC)
                            .networkSynchronized(com.shadowclangaming.chem.Compound.STREAM_CODEC));

    /** Chemical compound laced into a food item. */
    public static final Supplier<DataComponentType<com.shadowclangaming.chem.Compound>> POISONED_COMPOUND =
            DATA_COMPONENTS.registerComponentType(
                    "poisoned_compound",
                    builder -> builder.persistent(com.shadowclangaming.chem.Compound.CODEC)
                            .networkSynchronized(com.shadowclangaming.chem.Compound.STREAM_CODEC));

    /** Chemical element upgrades installed on the Polytool. */
    public static final Supplier<DataComponentType<java.util.List<com.shadowclangaming.item.PolytoolUpgrade>>> POLYTOOL_UPGRADES =
            DATA_COMPONENTS.registerComponentType(
                    "polytool_upgrades",
                    builder -> builder.persistent(com.shadowclangaming.item.PolytoolUpgrade.CODEC.listOf())
                            .networkSynchronized(com.shadowclangaming.item.PolytoolUpgrade.STREAM_CODEC.apply(ByteBufCodecs.list())));

    private SCGDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
