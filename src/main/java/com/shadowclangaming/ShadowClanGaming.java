package com.shadowclangaming;

import com.shadowclangaming.registry.SCGBlockEntities;
import com.shadowclangaming.registry.SCGArmorMaterials;
import com.shadowclangaming.registry.SCGBlocks;
import com.shadowclangaming.registry.SCGCreativeTabs;
import com.shadowclangaming.registry.SCGDataComponents;
import com.shadowclangaming.registry.SCGMenus;
import com.shadowclangaming.registry.SCGMobEffects;
import com.shadowclangaming.registry.SCGEntities;
import com.shadowclangaming.registry.SCGItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ShadowClanGaming - entry point.
 *
 * NeoForge calls this constructor once, very early. It does NOT need to be public static void main;
 * the @Mod annotation plus the mods.toml file are what make Minecraft find this class.
 *
 * Everything you register (items, blocks, entities...) is hooked to the mod event bus here.
 * If you add a new registry class, add its register() call below or nothing will show up in game.
 */
@Mod(ShadowClanGaming.MOD_ID)
public class ShadowClanGaming {
    public static final String MOD_ID = "shadowclangaming";
    public static final Logger LOGGER = LoggerFactory.getLogger("ShadowClanGaming");

    public ShadowClanGaming(IEventBus modEventBus, ModContainer modContainer) {
        com.shadowclangaming.debug.ShadowDebug.start();
        com.shadowclangaming.debug.ShadowDebug.log("MOD", "Mod constructor entered; NeoForge mod id={}", MOD_ID);
        // --- config ---
        // Two files: client (what one player sees) and common (what the world does). See config/SCGConfig.
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,
                com.shadowclangaming.config.SCGConfig.CLIENT_SPEC);
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON,
                com.shadowclangaming.config.SCGConfig.COMMON_SPEC);

        // --- content registration ---
        SCGDataComponents.register(modEventBus);
        SCGMobEffects.register(modEventBus);
        SCGArmorMaterials.register(modEventBus);
        SCGBlocks.register(modEventBus);
        SCGItems.register(modEventBus);
        SCGBlockEntities.register(modEventBus);
        SCGEntities.register(modEventBus);
        SCGCreativeTabs.register(modEventBus);
        SCGMenus.register(modEventBus);

        // --- capabilities (energy on the target designator, etc.) ---
        modEventBus.addListener(com.shadowclangaming.item.TargetDesignatorItem::registerCapabilities);
        // Item handler + FE buffer on the Auto-Trader (pipes and cables on every side).
        modEventBus.addListener(com.shadowclangaming.block.AutoTraderBlock::registerCapabilities);
        // FE input on the launch pad: charged FE becomes warhead funding at launch.
        modEventBus.addListener(com.shadowclangaming.block.LaunchPadBlock::registerCapabilities);
        // FE face of the Hyperconductor Cable.
        modEventBus.addListener(com.shadowclangaming.block.PowerCableBlock::registerCapabilities);

        // --- lifecycle hooks ---
        modEventBus.addListener(this::commonSetup);

        // Violator skulls: vanilla decides the radius before the event, so we cancel its version and
        // run our own at 5x. See ViolatorWither.scaleBlast.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) ->
                        com.shadowclangaming.warhead.ViolatorWither.scaleBlast(event));

        // A Create: Numismatics Bank Card right-clicked on one of our launch pads buys warhead funding.
        com.shadowclangaming.compat.numismatics.NumismaticsCards.register();

        // --- game bus: server profile detection + per-tick explosion work ---
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStartedEvent event) ->
                        com.shadowclangaming.perf.ServerProfile.detect(event.getServer()));

        // This listener is what spreads a huge blast over several ticks instead of freezing one.
        // ClientSync.flush is the second half of the same story: a strategic blast edits chunk palettes
        // directly (that is the only way it fits in a tick), so the modified chunks have to be resent to
        // the client or the world turns into ghost blocks.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> {
                    com.shadowclangaming.perf.BlastScheduler.INSTANCE.tick(event.getServer());
                    // Scar carving is budgeted per tick so a continent-scale blast never freezes one.
                    com.shadowclangaming.perf.StrategicBlasts.tick(event.getServer());
                    com.shadowclangaming.perf.ClientSync.flush(event.getServer());
                });

        // Persistent weapons with a lifetime: forcefield domes and the plague. Both live in saved
        // data and need one tick each, so they share a single listener.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> {
                    com.shadowclangaming.warhead.ForceFields.tick(event.getServer());
                    com.shadowclangaming.warhead.Outbreak.tick(event.getServer());
                    // Spell zones and runes: a zone re-runs the rest of its spell every 5 s for 30 s,
                    // a rune fires once when something walks into it.
                    com.shadowclangaming.spell.SpellZones.tick(event.getServer());
                    // Firestorm and Hellstorm: burning areas that pulse every half second.
                    com.shadowclangaming.spell.SpellStorms.tick(event.getServer());
                    // Delayed casting: DELAY and CHANT hand their step to this queue instead of
                    // sleeping on the server thread.
                    com.shadowclangaming.spell.SpellDelays.tick(event.getServer());
                    // Bad Gas: mobs thrown by a ragdoll-less server get their AI back from here.
                    com.shadowclangaming.spell.FlungBodies.tick(event.getServer());
                    // The constructor warhead fills its sphere over many ticks, a slice at a time.
                    com.shadowclangaming.warhead.ConstructorPlacer.tick(event.getServer());
                    // The Violator's 30s/5s skull burst schedule.
                    com.shadowclangaming.warhead.ViolatorWither.tick(event.getServer());
                    // Apex warheads: staged timelines (charge, front, release) and the budgeted
                    // non-spherical carver that digs their trenches, runes, tendrils and lances.
                    com.shadowclangaming.warhead.apex.ApexStages.tick(event.getServer());
                    com.shadowclangaming.warhead.apex.ApexCarver.tick(event.getServer());
                    // The Soul Furnace is not a fixed stage schedule: it consumes, re-checks for mobs
                    // every five seconds, and releases only when a check finds nothing left to eat.
                    com.shadowclangaming.warhead.apex.SoulFurnaceStages.tick(event.getServer());
                    // A warhead that arrived already armed plays its effect first and detonates on the
                    // frame the effect ends (missile impacts, the kamikaze vest).
                    com.shadowclangaming.warhead.ChargeSequence.tick(event.getServer());
                });

        // Mana has no natural regeneration: this is the once-a-second pass that pays out the passive
        // sources (amethyst proximity, blood pact). Circles pay out from their own block entity.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> {
                    if (event.getServer().getTickCount() % 20 != 0) {
                        return;
                    }
                    for (var player : event.getServer().getPlayerList().getPlayers()) {
                        com.shadowclangaming.magic.ManaSources.tickPlayer(player);
                        com.shadowclangaming.magic.CreepingEvil.tickPlayer(player);
                        com.shadowclangaming.armor.ArmorSets.tickPlayer(player);
                        com.shadowclangaming.chem.RadiationSystem.tickPlayer(player);
                    }
                });

        // Chronosplit's interaction locks. The freeze itself is a mob effect (so it survives a
        // restart and shows in the HUD); this registers the hooks that stop a frozen player breaking
        // blocks, eating, attacking or dropping things.
        com.shadowclangaming.spell.Chronosplit.register();

        // Holy Water is a cleric trade, not a recipe.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.shadowclangaming.magic.ClericTrades::onVillagerTrades);

        // The kamikaze vest: on death, blast, launch every carried missile somewhere random, and throw
        // every explosive clear. Registered separately from Sacrifices so neither can shadow the other.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.shadowclangaming.warhead.Kamikaze::onLivingDeath);

        // The sacrifice route: witch + baby villager + cat, full moon, totem in the offhand.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.shadowclangaming.magic.Sacrifices::onLivingDeath);

        // Chunk-load hook: this is what makes a 4096-radius scar actually stick. Any chunk that
        // loads inside a recorded scar is wiped when it enters memory, not up front.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.ChunkEvent.Load event) -> {
                    if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel
                            && event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) {
                        com.shadowclangaming.perf.StrategicBlasts.onChunkLoad(serverLevel, chunk);
                    }
                });

        // EconomyCraft price table: generated once on server start so every modded item is priced.
        // Disable with -Dshadowclangaming.economyAutoGenerate=false
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStartedEvent event) -> {
                    if (Boolean.parseBoolean(System.getProperty("shadowclangaming.economyAutoGenerate", "true"))) {
                        com.shadowclangaming.compat.economy.EconomyBridge.generate(event.getServer(), false);
                    }
                });

        // Chemistry combat and interaction: weapon chemical coatings, poisoned food, sneak-right-click gestures
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.shadowclangaming.chem.WeaponCoatingSystem::onDamage);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.shadowclangaming.chem.WeaponCoatingSystem::onTooltip);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.shadowclangaming.chem.FoodPoisoningSystem::onFinishUsingItem);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.shadowclangaming.chem.FoodPoisoningSystem::onTooltip);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.shadowclangaming.chem.ChemicalInteractionEvents::onRightClickItem);

        // Operator commands (/shadowclangaming scar ...)
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.shadowclangaming.command.SCGCommands::register);

        // /term <command> - the terminal's keyboard (see computer/TerminalShell).
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.shadowclangaming.command.TerminalCommand::register);

        // The singleton worker is closed on server stop, and reopened for the next integrated world.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStartingEvent event) ->
                        com.shadowclangaming.perf.BlastScheduler.INSTANCE.prepare());

        // Clean shutdown of the planner threads so a /reload or server stop never leaks them.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppingEvent event) -> {
                    // A late server tick must not keep advancing staged apex weapons after their worker
                    // planner has been shut down; discard their remaining cosmetic/destruction stages.
                    com.shadowclangaming.warhead.apex.ApexStages.clear();
                    com.shadowclangaming.warhead.apex.SoulFurnaceStages.clear();
                    com.shadowclangaming.warhead.ChargeSequence.clear();
                    com.shadowclangaming.perf.BlastScheduler.INSTANCE.shutdown();
                    com.shadowclangaming.perf.ClientSync.clear();
                });
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("ShadowClanGaming loading: silos armed, radar warming up.");
    }
}
