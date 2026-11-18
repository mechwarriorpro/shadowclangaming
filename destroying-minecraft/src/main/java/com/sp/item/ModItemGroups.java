package com.sp.item;

import com.sp.DestroyingMinecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public class ModItemGroups {
    public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DestroyingMinecraft.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = REGISTRY.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.destroying_minecraft"))
        .icon(() -> new ItemStack(ModItems.NUKE_TRIGGER.get()))
        .displayItems((ctx, out) -> {
            out.accept(ModItems.CAMERA_SHAKE_STICK.get());
            out.accept(ModItems.WALKIE_TALKIE.get());
            out.accept(ModItems.NUKE_TRIGGER.get());
            out.accept(ModItems.LASER_TRIGGER.get());
            out.accept(ModItems.BLACK_HOLE_TRIGGER.get());
            out.accept(ModItems.PLANET_TRIGGER.get());
            out.accept(ModItems.SUPERNOVA_TRIGGER.get());
        }).build());

    public static void registerItemGroups() {}
}
