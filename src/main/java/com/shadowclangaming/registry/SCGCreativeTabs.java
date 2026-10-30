package com.shadowclangaming.registry;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.chem.Element;
import com.shadowclangaming.item.ElementVialItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SCGCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ShadowClanGaming.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.shadowclangaming"))
                    .icon(() -> new ItemStack(SCGItems.ANTIMATTER_MISSILE.get()))
                    .displayItems((params, output) -> {
                        // fire control
                        output.accept(SCGItems.LAUNCH_PAD.get());
                        output.accept(SCGItems.TARGET_DESIGNATOR.get());
                        output.accept(SCGItems.RTG_PELLET.get());
                        output.accept(SCGItems.WAR_BOND.get());
                        output.accept(SCGItems.BANK_CARD.get());
                        output.accept(SCGItems.AUTO_TRADER.get());
                        output.accept(SCGItems.POWER_CABLE.get());
                        output.accept(SCGItems.CHALK.get());
                        output.accept(SCGItems.BLOOD_CIRCLE.get());
                        output.accept(SCGItems.HOLY_NECKLACE.get());
                        output.accept(SCGItems.HOLY_WATER.get());
                        // warheads, weakest to strongest
                        output.accept(SCGItems.DECOY_MISSILE.get());
                        output.accept(SCGItems.EMP_MISSILE.get());
                        output.accept(SCGItems.SRBM.get());
                        output.accept(SCGItems.MRBM.get());
                        output.accept(SCGItems.ICBM.get());
                        output.accept(SCGItems.INCENDIARY_MISSILE.get());
                        output.accept(SCGItems.CLUSTER_MISSILE.get());
                        output.accept(SCGItems.FISSION_MISSILE.get());
                        output.accept(SCGItems.FUSION_MISSILE.get());
                        output.accept(SCGItems.NUMISMATIC_MISSILE.get());
                        output.accept(SCGItems.BULWARK_MISSILE.get());
                        output.accept(SCGItems.CONTAGION_MISSILE.get());
                        output.accept(SCGItems.SPELL_MISSILE.get());
                        output.accept(SCGItems.SPELL_BLUEPRINT.get());
                        // A filled example, so the item is not a mystery in the creative menu.
                        output.accept(blueprintExample());
                        output.accept(SCGItems.CARGO_MISSILE.get());
                        output.accept(SCGItems.CONSTRUCTOR_MISSILE.get());
                        output.accept(SCGItems.SPATIAL_MISSILE.get());
                        output.accept(SCGItems.CLUCKER_MISSILE.get());
                        output.accept(SCGItems.VIOLATOR_MISSILE.get());
                        // Modules: empty airframes, tiered by range.
                        output.accept(SCGItems.MISSILE_MODULE_T1.get());
                        output.accept(SCGItems.MISSILE_MODULE_T2.get());
                        output.accept(SCGItems.MISSILE_MODULE_T3.get());
                        output.accept(SCGItems.MISSILE_MODULE_T4.get());
                        output.accept(SCGItems.MISSILE_MODULE_T5.get());
                        for (var kind : SCGItems.MISSILE_MODULE_SPECIALS) {
                            for (var module : kind) {
                                output.accept(module.get());
                            }
                        }
                        output.accept(SCGItems.HAND_LAUNCHER.get());
                        // Packaged warheads: the same roster in three more delivery forms.
                        for (var block : SCGItems.EXPLOSIVE_BLOCK_ITEMS.values()) {
                            output.accept(block.get());
                        }
                        for (var grenade : SCGItems.EXPLOSIVE_GRENADES) {
                            output.accept(grenade.get());
                        }
                        for (var cart : SCGItems.EXPLOSIVE_MINECARTS.values()) {
                            output.accept(cart.get());
                        }
                        // A fitted one, so the fitting gesture is discoverable.
                        output.accept(fittedModuleExample());
                        output.accept(SCGItems.BEEF_MISSILE.get());
                        // Apex roster: the end of the ladder.
                        output.accept(SCGItems.WORLD_EATER_MISSILE.get());
                        output.accept(SCGItems.STELLAR_REQUIEM_MISSILE.get());
                        output.accept(SCGItems.REALITY_BREAKER_MISSILE.get());
                        output.accept(SCGItems.SPACETIME_HAMMER_MISSILE.get());
                        output.accept(SCGItems.UNIVERSAL_IMPLODER_MISSILE.get());
                        output.accept(SCGItems.RAGNAROK_MISSILE.get());
                        output.accept(SCGItems.WORLD_RUNE_MISSILE.get());
                        output.accept(SCGItems.ABYSSAL_COVENANT_MISSILE.get());
                        output.accept(SCGItems.SOUL_FURNACE_MISSILE.get());
                        output.accept(SCGItems.ARCANE_SINGULARITY_MISSILE.get());
                        output.accept(SCGItems.QUASAR_LANCE_MISSILE.get());
                        output.accept(SCGItems.STARFORGE_MISSILE.get());
                        output.accept(SCGItems.BIG_BANG_SEED_MISSILE.get());
                        output.accept(SCGItems.REALITY_CORRUPTOR_MISSILE.get());
                        output.accept(SCGItems.ABSOLUTE_ZERO_MISSILE.get());
                        output.accept(SCGItems.OMEGA_MISSILE.get());
                        output.accept(SCGItems.ABSOLUTE_ANNIHILATION_MISSILE.get());
                        // A loaded constructor missile, so the "fill a sphere" idea is visible in the
                        // creative menu instead of being something you have to be told about.
                        output.accept(loadedConstructorExample());
                        output.accept(SCGItems.MINEFIELD_MISSILE.get());
                        output.accept(SCGItems.TRACKING_MISSILE.get());
                        output.accept(SCGItems.BALLISTICS_CALCULATOR.get());
                        output.accept(SCGItems.TRACKER.get());
                        output.accept(SCGItems.UPGRADE_KIT.get());
                        output.accept(SCGItems.INTERDIMENSIONAL_MODULE.get());
                        output.accept(SCGItems.CONTROL_PANEL.get());
                        output.accept(SCGItems.SUPPORT_ROD.get());
                        output.accept(SCGItems.MINE.get());
                        output.accept(SCGItems.ANTIMATTER_MISSILE.get());
                        // computers
                        output.accept(SCGItems.TERMINAL.get());
                        output.accept(SCGItems.FREQUENCY_CHIP.get());
                        output.accept(SCGItems.TARGETING_CARD.get());
                        output.accept(SCGItems.MANA_CARD.get());
                        output.accept(SCGItems.NET_CARD.get());
                        output.accept(SCGItems.STORAGE_CARD.get());
                        output.accept(SCGItems.REDSTONE_CARD.get());
                        output.accept(SCGItems.NETWORK_CABLE.get());
                        output.accept(SCGItems.STORAGE_BRIDGE.get());
                        output.accept(SCGItems.RADAR_CARD.get());
                        output.accept(SCGItems.CANNON_CARD.get());
                        // hardware
                        output.accept(SCGItems.CPU_BASIC.get());
                        output.accept(SCGItems.CPU_ADVANCED.get());
                        output.accept(SCGItems.CPU_QUANTUM.get());
                        output.accept(SCGItems.CPU_PHOTONIC.get());
                        output.accept(SCGItems.GPU_BASIC.get());
                        output.accept(SCGItems.GPU_VECTOR.get());
                        output.accept(SCGItems.GPU_TENSOR.get());
                        output.accept(SCGItems.GPU_PHOTONIC.get());
                        output.accept(SCGItems.NETWORK_RACK.get());
                        output.accept(SCGItems.ORBITAL_NODE.get());
                        // sensors and fire control
                        output.accept(SCGItems.RADAR.get());
                        output.accept(SCGItems.CANNON_DIRECTOR.get());
                        // chemistry
                        output.accept(SCGItems.INSCRIPTION_TABLE.get());
                        output.accept(SCGItems.BLUEPRINT_TABLE.get());
                        output.accept(SCGItems.EMITTER.get());
                        output.accept(SCGItems.SPELL_RIFLE.get());
                        // guns and armour
                        output.accept(SCGItems.FABRICATOR.get());
                        output.accept(SCGItems.GUN_SCRAP.get());
                        output.accept(SCGItems.GUN_CELL.get());
                        output.accept(SCGItems.KAMIKAZE_VEST.get());
                        output.accept(SCGItems.INFAMY_HELMET.get());
                        output.accept(SCGItems.INFAMY_CHESTPLATE.get());
                        output.accept(SCGItems.INFAMY_LEGGINGS.get());
                        output.accept(SCGItems.INFAMY_BOOTS.get());
                        output.accept(SCGItems.SENTINEL_HELMET.get());
                        output.accept(SCGItems.SENTINEL_CHESTPLATE.get());
                        output.accept(SCGItems.SENTINEL_LEGGINGS.get());
                        output.accept(SCGItems.SENTINEL_BOOTS.get());
                        output.accept(SCGItems.RAIDER_HELMET.get());
                        output.accept(SCGItems.RAIDER_CHESTPLATE.get());
                        output.accept(SCGItems.RAIDER_LEGGINGS.get());
                        output.accept(SCGItems.RAIDER_BOOTS.get());
                        output.accept(SCGItems.DECOMPOSER.get());
                        output.accept(SCGItems.SYNTHESIZER.get());
                        output.accept(SCGItems.MICROSCOPE.get());
                        output.accept(SCGItems.CHEMICAL_REACTOR.get());
                        output.accept(SCGItems.CENTRIFUGE.get());
                        output.accept(SCGItems.POCKET_MICROSCOPE.get());
                        output.accept(SCGItems.CHEMIST_JOURNAL.get());
                        output.accept(SCGItems.URANIUM_ORE.get());
                        output.accept(SCGItems.RAW_URANIUM.get());
                        output.accept(SCGItems.ENRICHED_URANIUM.get());
                        output.accept(SCGItems.TRITIUM_CELL.get());
                        // one vial of every element
                        for (Element element : Element.values()) {
                            output.accept(ElementVialItem.create(element, 1));
                        }
                    })
                    .build());

    /** A constructor missile pre-loaded with a stone sphere, for the creative tab. */
    /** A T3 module with an antimatter warhead already fitted. */
    private static net.minecraft.world.item.ItemStack fittedModuleExample() {
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(
                SCGItems.MISSILE_MODULE_T3.get());
        stack.set(com.shadowclangaming.registry.SCGDataComponents.MODULE_WARHEAD.get(),
                com.shadowclangaming.warhead.WarheadType.ANTIMATTER.id());
        return stack;
    }

    private static net.minecraft.world.item.ItemStack loadedConstructorExample() {
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(
                SCGItems.CONSTRUCTOR_MISSILE.get());
        com.shadowclangaming.warhead.MissilePayload.load(stack,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 64));
        com.shadowclangaming.warhead.MissilePayload.load(stack,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MOSSY_COBBLESTONE, 64));
        com.shadowclangaming.warhead.MissilePayload.load(stack,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 64));
        com.shadowclangaming.warhead.MissilePayload.applyStacking(stack);
        return stack;
    }

    private SCGCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }

    /** A ready-made blueprint with a two-step spell, for the creative menu. */
    private static net.minecraft.world.item.ItemStack blueprintExample() {
        net.minecraft.world.item.ItemStack stack =
                new net.minecraft.world.item.ItemStack(SCGItems.SPELL_BLUEPRINT.get());
        stack.set(com.shadowclangaming.registry.SCGDataComponents.SPELL.get(),
                new com.shadowclangaming.spell.Spell(java.util.List.of(
                        new com.shadowclangaming.spell.Spell.Step(
                                com.shadowclangaming.spell.SpellShape.PROJECTILE,
                                java.util.List.of(com.shadowclangaming.spell.SpellComponent.LIGHTNING_DAMAGE), 0),
                        new com.shadowclangaming.spell.Spell.Step(
                                com.shadowclangaming.spell.SpellShape.ZONE,
                                java.util.List.of(com.shadowclangaming.spell.SpellComponent.LIGHTNING_DAMAGE), 0))));
        return stack;
    }
}
