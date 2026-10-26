package com.shadowclangaming.registry;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.block.AutoTraderBlockEntity;
import com.shadowclangaming.block.BlueprintTableBlockEntity;
import com.shadowclangaming.block.InscriptionTableBlockEntity;
import com.shadowclangaming.block.CircleBlockEntity;
import com.shadowclangaming.block.ControlPanelBlockEntity;
import com.shadowclangaming.block.SupportRodBlockEntity;
import com.shadowclangaming.block.LaunchPadBlockEntity;
import com.shadowclangaming.block.PowerCableBlockEntity;
import com.shadowclangaming.cannon.CannonDirectorBlockEntity;
import com.shadowclangaming.computer.NetworkCableBlockEntity;
import com.shadowclangaming.computer.NetworkRackBlockEntity;
import com.shadowclangaming.computer.OrbitalNodeBlockEntity;
import com.shadowclangaming.radar.RadarBlockEntity;
import com.shadowclangaming.computer.StorageBridgeBlockEntity;
import com.shadowclangaming.computer.TerminalBlockEntity;
import com.shadowclangaming.block.ProcessorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SCGBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ShadowClanGaming.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchPadBlockEntity>> LAUNCH_PAD =
            BLOCK_ENTITIES.register("launch_pad", () -> BlockEntityType.Builder
                    .of(LaunchPadBlockEntity::new, SCGBlocks.LAUNCH_PAD.get())
                    .build(null));

    /** Shared by the Decomposer and the Synthesizer - one entity class, two blocks. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessorBlockEntity>> PROCESSOR =
            BLOCK_ENTITIES.register("processor", () -> BlockEntityType.Builder
                    .of(ProcessorBlockEntity::new, SCGBlocks.DECOMPOSER.get(), SCGBlocks.SYNTHESIZER.get())
                    .build(null));

    /** The Auto-Trader: one per block, holds the Bank Card and the FE buffer. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutoTraderBlockEntity>> AUTO_TRADER =
            BLOCK_ENTITIES.register("auto_trader", () -> BlockEntityType.Builder
                    .of(AutoTraderBlockEntity::new, SCGBlocks.AUTO_TRADER.get())
                    .build(null));

    /** Hyperconductor Cable: holds a 4 GFE long buffer. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerCableBlockEntity>> POWER_CABLE =
            BLOCK_ENTITIES.register("hyperconductor_cable", () -> BlockEntityType.Builder
                    .of(PowerCableBlockEntity::new, SCGBlocks.POWER_CABLE.get())
                    .build(null));

    /** Shared by the chalk circle and the blood circle - one entity, two blocks. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CircleBlockEntity>> CIRCLE =
            BLOCK_ENTITIES.register("circle", () -> BlockEntityType.Builder
                    .of(CircleBlockEntity::new, SCGBlocks.CHALK_CIRCLE.get(), SCGBlocks.BLOOD_CIRCLE.get())
                    .build(null));

    /** Remembers two corners and turns the build into a rifle. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlueprintTableBlockEntity>> BLUEPRINT_TABLE =
            BLOCK_ENTITIES.register("blueprint_table", () -> BlockEntityType.Builder
                    .of(BlueprintTableBlockEntity::new, SCGBlocks.BLUEPRINT_TABLE.get())
                    .build(null));

    /** Holds the half-finished spell shown in the inscription screen. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InscriptionTableBlockEntity>> INSCRIPTION_TABLE =
            BLOCK_ENTITIES.register("inscription_table", () -> BlockEntityType.Builder
                    .of(InscriptionTableBlockEntity::new, SCGBlocks.INSCRIPTION_TABLE.get())
                    .build(null));

    /** The terminal's frequency, installed cards and log. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TerminalBlockEntity>> TERMINAL =
            BLOCK_ENTITIES.register("terminal", () -> BlockEntityType.Builder
                    .of(TerminalBlockEntity::new, SCGBlocks.TERMINAL.get())
                    .build(null));

    /** Launcher control panel: tier, target, modules. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ControlPanelBlockEntity>> CONTROL_PANEL =
            BLOCK_ENTITIES.register("control_panel", () -> BlockEntityType.Builder
                    .of(ControlPanelBlockEntity::new, SCGBlocks.CONTROL_PANEL.get())
                    .build(null));

    /** A landmine's disguise: which block it is pretending to be. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.shadowclangaming.block.MineBlockEntity>> MINE =
            BLOCK_ENTITIES.register("mine", () -> BlockEntityType.Builder
                    .of(com.shadowclangaming.block.MineBlockEntity::new, SCGBlocks.MINE.get())
                    .build(null));

    /** Support rod: a tier and nothing else. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SupportRodBlockEntity>> SUPPORT_ROD =
            BLOCK_ENTITIES.register("support_rod", () -> BlockEntityType.Builder
                    .of(SupportRodBlockEntity::new, SCGBlocks.SUPPORT_ROD.get())
                    .build(null));

    /** The wire. One number per block: which channel it carries. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetworkCableBlockEntity>> NETWORK_CABLE =
            BLOCK_ENTITIES.register("network_cable", () -> BlockEntityType.Builder
                    .of(NetworkCableBlockEntity::new, SCGBlocks.NETWORK_CABLE.get())
                    .build(null));

    /** Storage bridge: AE2 (or container) reader. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageBridgeBlockEntity>> STORAGE_BRIDGE =
            BLOCK_ENTITIES.register("storage_bridge", () -> BlockEntityType.Builder
                    .of(StorageBridgeBlockEntity::new, SCGBlocks.STORAGE_BRIDGE.get())
                    .build(null));

    /** Network rack: two CPU sockets and two GPU sockets. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetworkRackBlockEntity>> NETWORK_RACK =
            BLOCK_ENTITIES.register("network_rack", () -> BlockEntityType.Builder
                    .of(NetworkRackBlockEntity::new, SCGBlocks.NETWORK_RACK.get())
                    .build(null));

    /** Orbital processing node: a CPU, a queue and a downlink. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OrbitalNodeBlockEntity>>
            ORBITAL_NODE = BLOCK_ENTITIES.register("orbital_node", () -> BlockEntityType.Builder
                    .of(OrbitalNodeBlockEntity::new, SCGBlocks.ORBITAL_NODE.get())
                    .build(null));

    /** Radar: radius, array count, track list and the locked track. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadarBlockEntity>> RADAR =
            BLOCK_ENTITIES.register("radar", () -> BlockEntityType.Builder
                    .of(RadarBlockEntity::new, SCGBlocks.RADAR.get())
                    .build(null));

    /** Cannon director: mount link, lay, armed flag, velocity. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CannonDirectorBlockEntity>>
            CANNON_DIRECTOR = BLOCK_ENTITIES.register("cannon_director", () -> BlockEntityType.Builder
                    .of(CannonDirectorBlockEntity::new, SCGBlocks.CANNON_DIRECTOR.get())
                    .build(null));

    private SCGBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
