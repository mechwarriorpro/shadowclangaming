package com.shadowclangaming.registry;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.block.AutoTraderBlock;
import com.shadowclangaming.block.BloodCircleBlock;
import com.shadowclangaming.block.ChalkCircleBlock;
import com.shadowclangaming.block.ControlPanelBlock;
import com.shadowclangaming.block.MineBlock;
import com.shadowclangaming.block.CannonDirectorBlock;
import com.shadowclangaming.block.NetworkCableBlock;
import com.shadowclangaming.block.NetworkRackBlock;
import com.shadowclangaming.block.OrbitalNodeBlock;
import com.shadowclangaming.block.RadarBlock;
import com.shadowclangaming.block.StorageBridgeBlock;
import com.shadowclangaming.block.SupportRodBlock;
import com.shadowclangaming.block.BlueprintTableBlock;
import com.shadowclangaming.block.DecomposerBlock;
import com.shadowclangaming.block.EmitterBlock;
import com.shadowclangaming.block.FabricatorBlock;
import com.shadowclangaming.block.InscriptionTableBlock;
import com.shadowclangaming.block.LaunchPadBlock;
import com.shadowclangaming.block.PowerCableBlock;
import com.shadowclangaming.block.SynthesizerBlock;
import com.shadowclangaming.block.TerminalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SCGBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ShadowClanGaming.MOD_ID);

    /**
     * The launch pad. Holds one missile, takes a target, fires on redstone.
     */
    public static final DeferredBlock<LaunchPadBlock> LAUNCH_PAD = BLOCKS.registerBlock(
            "launch_pad",
            LaunchPadBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion());

    /**
     * Chemistry machine #1: item in, element vials out.
     */
    public static final DeferredBlock<DecomposerBlock> DECOMPOSER = BLOCKS.registerBlock(
            "decomposer",
            DecomposerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    /**
     * Chemistry machine #2: element vials in, useful items out.
     */
    public static final DeferredBlock<SynthesizerBlock> SYNTHESIZER = BLOCKS.registerBlock(
            "synthesizer",
            SynthesizerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    /**
     * Chemistry workstation #3: inspect items to reveal their elemental formula.
     */
    public static final DeferredBlock<com.shadowclangaming.block.MicroscopeBlock> MICROSCOPE = BLOCKS.registerBlock(
            "microscope",
            com.shadowclangaming.block.MicroscopeBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5F, 4.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /**
     * Chemistry machine #4: nuclear fission & fusion of elements.
     */
    public static final DeferredBlock<com.shadowclangaming.block.ChemicalReactorBlock> CHEMICAL_REACTOR = BLOCKS.registerBlock(
            "chemical_reactor",
            com.shadowclangaming.block.ChemicalReactorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 8.0F)
                    .sound(SoundType.HEAVY_METAL)
                    .requiresCorrectToolForDrops());

    /**
     * Chemistry machine #5: high-speed isotope separation.
     */
    public static final DeferredBlock<com.shadowclangaming.block.CentrifugeBlock> CENTRIFUGE = BLOCKS.registerBlock(
            "centrifuge",
            com.shadowclangaming.block.CentrifugeBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    /**
     * Auto-Trader: items in -> money, FE in -> money, redstone -> buys the template item.
     * Holds a Bank Card; that card's owner is the account.
     */
    public static final DeferredBlock<AutoTraderBlock> AUTO_TRADER = BLOCKS.registerBlock(
            "auto_trader",
            AutoTraderBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    /**
     * Hyperconductor Cable: long-based power trunk. FE, EMC and anything FE-shaped.
     */
    public static final DeferredBlock<PowerCableBlock> POWER_CABLE = BLOCKS.registerBlock(
            "hyperconductor_cable",
            PowerCableBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(0.8F, 3.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /** Chalk circle: stand in it, get mana. Four sizes. */
    public static final DeferredBlock<ChalkCircleBlock> CHALK_CIRCLE = BLOCKS.registerBlock(
            "chalk_circle",
            ChalkCircleBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .strength(0.1F)
                    .sound(SoundType.CALCITE)
                    .noOcclusion()
                    .noCollission());

    /** Blood circle: the ritual site for the sacrifice route. */
    public static final DeferredBlock<BloodCircleBlock> BLOOD_CIRCLE = BLOCKS.registerBlock(
            "blood_circle",
            BloodCircleBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.CRIMSON_HYPHAE)
                    .strength(0.4F)
                    .sound(SoundType.SOUL_SAND)
                    .noOcclusion()
                    .noCollission());

    /**
     * Where the nuclear chain begins. Drops Raw Uranium (see the loot table).
     * Generation is configured with JSON in data/shadowclangaming/worldgen/, no code needed.
     */
    public static final DeferredBlock<Block> URANIUM_ORE = BLOCKS.registerBlock(
            "uranium_ore",
            Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F, 3.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops());

    // ------------------------------------------------------------------ the magic block set

    /**
     * The muzzle. One of these must be inside a blueprint volume for a build to become a rifle.
     * On its own it is inert.
     */
    public static final DeferredBlock<EmitterBlock> EMITTER = BLOCKS.registerBlock(
            "emitter",
            EmitterBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 7)
                    .requiresCorrectToolForDrops());

    /** Marks out the volume a rifle is built in, then consumes it. */
    public static final DeferredBlock<BlueprintTableBlock> BLUEPRINT_TABLE = BLOCKS.registerBlock(
            "blueprint_table",
            BlueprintTableBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.WOOD)
                    .requiresCorrectToolForDrops());

    /** Where a spell is written: shape + components, paid for in mana and XP. */
    public static final DeferredBlock<InscriptionTableBlock> INSCRIPTION_TABLE = BLOCKS.registerBlock(
            "inscription_table",
            InscriptionTableBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.WOOD)
                    .requiresCorrectToolForDrops());

    /** Scrap in, a rolled gun out. Stateless, so no block entity. */
    public static final DeferredBlock<FabricatorBlock> FABRICATOR = BLOCKS.registerBlock(
            "fabricator",
            FabricatorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    /** The computer. No screen: it is driven by /term (see computer/TerminalShell). */
    public static final DeferredBlock<TerminalBlock> TERMINAL = BLOCKS.registerBlock(
            "terminal",
            TerminalBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 5)
                    .requiresCorrectToolForDrops());

    // ------------------------------------------------------------------ launcher structure

    /** The launcher's brain: tier, armed state, target, modules. A terminal can talk to it. */
    public static final DeferredBlock<ControlPanelBlock> CONTROL_PANEL = BLOCKS.registerBlock(
            "control_panel",
            ControlPanelBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(3.0F, 9.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    /** Support rods: two at the pad's tier are what make a launcher valid. */
    public static final DeferredBlock<SupportRodBlock> SUPPORT_ROD = BLOCKS.registerBlock(
            "support_rod",
            SupportRodBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 12.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion());

    /** A landmine. No block entity: it uses random ticks, so a field of 400 costs almost nothing. */
    public static final DeferredBlock<MineBlock> MINE = BLOCKS.registerBlock(
            "mine",
            MineBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(0.6F, 0.0F)
                    .sound(SoundType.METAL)
                    .randomTicks()
                    .noOcclusion());

    /** The computer network's wire. Carries a channel; connects terminals, panels, pads, bridges. */
    public static final DeferredBlock<NetworkCableBlock> NETWORK_CABLE = BLOCKS.registerBlock(
            "network_cable",
            NetworkCableBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(0.6F, 3.0F)
                    .sound(SoundType.WOOL)
                    // NOT noCollission(): a cable you can walk through and cannot click is a cable
                    // players report as "no hitbox". The 4x4px core plus its arms are the hitbox.
                    .noOcclusion());

    /** Reads an AE2 network (or plain containers) on behalf of a terminal. */
    public static final DeferredBlock<StorageBridgeBlock> STORAGE_BRIDGE = BLOCKS.registerBlock(
            "storage_bridge",
            StorageBridgeBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 3)
                    .requiresCorrectToolForDrops());

    // ------------------------------------------------------------------ the rest of the machine

    /** Network rack: four sockets of CPU and GPU. Feeds a terminal's compute budget. */
    public static final DeferredBlock<NetworkRackBlock> NETWORK_RACK = BLOCKS.registerBlock(
            "network_rack",
            NetworkRackBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    /** Orbital processing node: runs queued subcommands, one at a time, if it can see the sky. */
    public static final DeferredBlock<OrbitalNodeBlock> ORBITAL_NODE = BLOCKS.registerBlock(
            "orbital_node",
            OrbitalNodeBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.0F, 9.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 7)
                    .requiresCorrectToolForDrops());

    /** Our radar dish: 256 blocks base, up to 4096 with an array, and a real track list. */
    public static final DeferredBlock<RadarBlock> RADAR = BLOCKS.registerBlock(
            "radar",
            RadarBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 4)
                    .requiresCorrectToolForDrops()
                    .noOcclusion());

    /** Cannon director: lays and fires a Create Big Cannons mount. Inert without CBC. */
    public static final DeferredBlock<CannonDirectorBlock> CANNON_DIRECTOR = BLOCKS.registerBlock(
            "cannon_director",
            CannonDirectorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.5F, 9.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    // ------------------------------------------------------------------ one block per warhead
    //
    // 38 placeable warheads, registered in a loop from WarheadType.values() rather than 38 fields: the
    // list is generated from the warhead roster, so a new warhead gets its block for free and can never be
    // forgotten. DUD is skipped (a craftable dud block is a decoration, not an explosive).

    /** {@code explosive_<warhead-id>} for every packable warhead, in roster order. */
    public static final java.util.Map<String, DeferredBlock<com.shadowclangaming.block.ExplosiveBlock>>
            EXPLOSIVE_BLOCKS = registerExplosiveBlocks();

    private static java.util.Map<String, DeferredBlock<com.shadowclangaming.block.ExplosiveBlock>>
            registerExplosiveBlocks() {
        java.util.Map<String, DeferredBlock<com.shadowclangaming.block.ExplosiveBlock>> map =
                new java.util.LinkedHashMap<>();
        for (com.shadowclangaming.warhead.WarheadType warhead
                : com.shadowclangaming.warhead.WarheadType.values()) {
            if (!com.shadowclangaming.warhead.ExplosivePackaging.packable(warhead.id())) {
                continue;
            }
            String id = com.shadowclangaming.warhead.ExplosivePackaging.id(
                    com.shadowclangaming.warhead.ExplosivePackaging.Form.BLOCK, warhead.id());
            map.put(warhead.id(), BLOCKS.registerBlock(id,
                    props -> new com.shadowclangaming.block.ExplosiveBlock(warhead, props),
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.FIRE)
                            .strength(0.4F)
                            .sound(SoundType.GRASS)));
        }
        return java.util.Map.copyOf(map);
    }

    private SCGBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
