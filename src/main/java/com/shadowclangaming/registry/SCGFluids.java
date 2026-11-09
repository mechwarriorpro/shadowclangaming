package com.shadowclangaming.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

import com.shadowclangaming.ShadowClanGaming;

/**
 * Megamod interconnect fluids: 6 full flowing fluids bridging the mod systems.
 * Fixed for Java 21 self-reference: fields are assigned in static{} so lambdas
 * capturing COOLANT/FLOWING_COOLANT etc. are not illegal forward references.
 */
public final class SCGFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, ShadowClanGaming.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, ShadowClanGaming.MOD_ID);

    public static DeferredHolder<FluidType, FluidType> COOLANT_TYPE;
    public static DeferredHolder<FluidType, FluidType> MOLTEN_UNOBTAINIUM_TYPE;
    public static DeferredHolder<FluidType, FluidType> MOLTEN_YELLORIUM_TYPE;
    public static DeferredHolder<FluidType, FluidType> ESSENTIA_FLUX_TYPE;
    public static DeferredHolder<FluidType, FluidType> SANGUINE_ICHOR_TYPE;
    public static DeferredHolder<FluidType, FluidType> MANA_ESSENCE_TYPE;

    public static DeferredHolder<Fluid, FlowingFluid> COOLANT;
    public static DeferredHolder<Fluid, FlowingFluid> FLOWING_COOLANT;
    public static DeferredHolder<Fluid, FlowingFluid> MOLTEN_UNOBTAINIUM;
    public static DeferredHolder<Fluid, FlowingFluid> FLOWING_MOLTEN_UNOBTAINIUM;
    public static DeferredHolder<Fluid, FlowingFluid> MOLTEN_YELLORIUM;
    public static DeferredHolder<Fluid, FlowingFluid> FLOWING_MOLTEN_YELLORIUM;
    public static DeferredHolder<Fluid, FlowingFluid> ESSENTIA_FLUX;
    public static DeferredHolder<Fluid, FlowingFluid> FLOWING_ESSENTIA_FLUX;
    public static DeferredHolder<Fluid, FlowingFluid> SANGUINE_ICHOR;
    public static DeferredHolder<Fluid, FlowingFluid> FLOWING_SANGUINE_ICHOR;
    public static DeferredHolder<Fluid, FlowingFluid> MANA_ESSENCE;
    public static DeferredHolder<Fluid, FlowingFluid> FLOWING_MANA_ESSENCE;

    static {
        COOLANT_TYPE = FLUID_TYPES.register("coolant",
                () -> new FluidType(FluidType.Properties.create()
                        .descriptionId("fluid.shadowclangaming.coolant")
                        .density(1200).viscosity(1500).temperature(280)
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

        MOLTEN_UNOBTAINIUM_TYPE = FLUID_TYPES.register("molten_unobtainium",
                () -> new FluidType(FluidType.Properties.create()
                        .descriptionId("fluid.shadowclangaming.molten_unobtainium")
                        .density(3000).viscosity(4000).temperature(3000).lightLevel(15)
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA)));

        MOLTEN_YELLORIUM_TYPE = FLUID_TYPES.register("molten_yellorium",
                () -> new FluidType(FluidType.Properties.create()
                        .descriptionId("fluid.shadowclangaming.molten_yellorium")
                        .density(2500).viscosity(3000).temperature(1800).lightLevel(12)
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA)));

        ESSENTIA_FLUX_TYPE = FLUID_TYPES.register("essentia_flux",
                () -> new FluidType(FluidType.Properties.create()
                        .descriptionId("fluid.shadowclangaming.essentia_flux")
                        .density(1100).viscosity(1200).temperature(300).lightLevel(10)
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

        SANGUINE_ICHOR_TYPE = FLUID_TYPES.register("sanguine_ichor",
                () -> new FluidType(FluidType.Properties.create()
                        .descriptionId("fluid.shadowclangaming.sanguine_ichor")
                        .density(1050).viscosity(1400).temperature(310).lightLevel(7)
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

        MANA_ESSENCE_TYPE = FLUID_TYPES.register("mana_essence",
                () -> new FluidType(FluidType.Properties.create()
                        .descriptionId("fluid.shadowclangaming.mana_essence")
                        .density(1000).viscosity(1000).temperature(300).lightLevel(12)
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

        COOLANT = FLUIDS.register("coolant",
                () -> new BaseFlowingFluid.Source(props(COOLANT_TYPE, COOLANT, FLOWING_COOLANT, SCGFluids::coolantBlock, SCGFluids::coolantBucket)));
        FLOWING_COOLANT = FLUIDS.register("flowing_coolant",
                () -> new BaseFlowingFluid.Flowing(props(COOLANT_TYPE, COOLANT, FLOWING_COOLANT, SCGFluids::coolantBlock, SCGFluids::coolantBucket)));

        MOLTEN_UNOBTAINIUM = FLUIDS.register("molten_unobtainium",
                () -> new BaseFlowingFluid.Source(props(MOLTEN_UNOBTAINIUM_TYPE, MOLTEN_UNOBTAINIUM, FLOWING_MOLTEN_UNOBTAINIUM, SCGFluids::moltenUnobtainiumBlock, SCGFluids::moltenUnobtainiumBucket)));
        FLOWING_MOLTEN_UNOBTAINIUM = FLUIDS.register("flowing_molten_unobtainium",
                () -> new BaseFlowingFluid.Flowing(props(MOLTEN_UNOBTAINIUM_TYPE, MOLTEN_UNOBTAINIUM, FLOWING_MOLTEN_UNOBTAINIUM, SCGFluids::moltenUnobtainiumBlock, SCGFluids::moltenUnobtainiumBucket)));

        MOLTEN_YELLORIUM = FLUIDS.register("molten_yellorium",
                () -> new BaseFlowingFluid.Source(props(MOLTEN_YELLORIUM_TYPE, MOLTEN_YELLORIUM, FLOWING_MOLTEN_YELLORIUM, SCGFluids::moltenYelloriumBlock, SCGFluids::moltenYelloriumBucket)));
        FLOWING_MOLTEN_YELLORIUM = FLUIDS.register("flowing_molten_yellorium",
                () -> new BaseFlowingFluid.Flowing(props(MOLTEN_YELLORIUM_TYPE, MOLTEN_YELLORIUM, FLOWING_MOLTEN_YELLORIUM, SCGFluids::moltenYelloriumBlock, SCGFluids::moltenYelloriumBucket)));

        ESSENTIA_FLUX = FLUIDS.register("essentia_flux",
                () -> new BaseFlowingFluid.Source(props(ESSENTIA_FLUX_TYPE, ESSENTIA_FLUX, FLOWING_ESSENTIA_FLUX, SCGFluids::essentiaFluxBlock, SCGFluids::essentiaFluxBucket)));
        FLOWING_ESSENTIA_FLUX = FLUIDS.register("flowing_essentia_flux",
                () -> new BaseFlowingFluid.Flowing(props(ESSENTIA_FLUX_TYPE, ESSENTIA_FLUX, FLOWING_ESSENTIA_FLUX, SCGFluids::essentiaFluxBlock, SCGFluids::essentiaFluxBucket)));

        SANGUINE_ICHOR = FLUIDS.register("sanguine_ichor",
                () -> new BaseFlowingFluid.Source(props(SANGUINE_ICHOR_TYPE, SANGUINE_ICHOR, FLOWING_SANGUINE_ICHOR, SCGFluids::sanguineIchorBlock, SCGFluids::sanguineIchorBucket)));
        FLOWING_SANGUINE_ICHOR = FLUIDS.register("flowing_sanguine_ichor",
                () -> new BaseFlowingFluid.Flowing(props(SANGUINE_ICHOR_TYPE, SANGUINE_ICHOR, FLOWING_SANGUINE_ICHOR, SCGFluids::sanguineIchorBlock, SCGFluids::sanguineIchorBucket)));

        MANA_ESSENCE = FLUIDS.register("mana_essence",
                () -> new BaseFlowingFluid.Source(props(MANA_ESSENCE_TYPE, MANA_ESSENCE, FLOWING_MANA_ESSENCE, SCGFluids::manaEssenceBlock, SCGFluids::manaEssenceBucket)));
        FLOWING_MANA_ESSENCE = FLUIDS.register("flowing_mana_essence",
                () -> new BaseFlowingFluid.Flowing(props(MANA_ESSENCE_TYPE, MANA_ESSENCE, FLOWING_MANA_ESSENCE, SCGFluids::manaEssenceBlock, SCGFluids::manaEssenceBucket)));
    }

    private static BaseFlowingFluid.Properties props(Supplier<FluidType> type,
            Supplier<FlowingFluid> still, Supplier<FlowingFluid> flowing,
            Supplier<LiquidBlock> block, Supplier<Item> bucket) {
        return new BaseFlowingFluid.Properties(type, still, flowing).block(block).bucket(bucket);
    }

    // --- Blocks & buckets (lazy suppliers to break circular DeferredRegister order)

    private static LiquidBlock coolantBlock() { return SCGBlocks.FLUID_COOLANT.get(); }
    private static Item coolantBucket() { return SCGItems.COOLANT_BUCKET.get(); }
    private static LiquidBlock moltenUnobtainiumBlock() { return SCGBlocks.FLUID_MOLTEN_UNOBTAINIUM.get(); }
    private static Item moltenUnobtainiumBucket() { return SCGItems.MOLTEN_UNOBTAINIUM_BUCKET_FLUID.get(); }
    private static LiquidBlock moltenYelloriumBlock() { return SCGBlocks.FLUID_MOLTEN_YELLORIUM.get(); }
    private static Item moltenYelloriumBucket() { return SCGItems.MOLTEN_YELLORIUM_BUCKET_FLUID.get(); }
    private static LiquidBlock essentiaFluxBlock() { return SCGBlocks.FLUID_ESSENTIA_FLUX.get(); }
    private static Item essentiaFluxBucket() { return SCGItems.ESSENTIA_FLUX_BUCKET.get(); }
    private static LiquidBlock sanguineIchorBlock() { return SCGBlocks.FLUID_SANGUINE_ICHOR.get(); }
    private static Item sanguineIchorBucket() { return SCGItems.SANGUINE_ICHOR_BUCKET.get(); }
    private static LiquidBlock manaEssenceBlock() { return SCGBlocks.FLUID_MANA_ESSENCE.get(); }
    private static Item manaEssenceBucket() { return SCGItems.MANA_ESSENCE_BUCKET.get(); }

    public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }

    private SCGFluids() {}
}
