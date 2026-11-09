package com.sp.item;

import com.sp.DestroyingMinecraft;
import com.sp.item.custom.CameraShakeStick;
import com.sp.item.custom.DestructionTriggerItem;
import com.sp.item.custom.WalkieTalkie;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public class ModItems {
    public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(Registries.ITEM, DestroyingMinecraft.MOD_ID);

    public static final Supplier<Item> CAMERA_SHAKE_STICK = REGISTRY.register("camera_shake_stick", () -> new CameraShakeStick(new Item.Properties()));
    public static final Supplier<Item> WALKIE_TALKIE = REGISTRY.register("walkie_talkie", () -> new WalkieTalkie(new Item.Properties().stacksTo(1)));

    // Debug triggers — one per DestructionType, creative only
    public static final Supplier<Item> NUKE_TRIGGER = REGISTRY.register("nuke_trigger", () -> new DestructionTriggerItem(new Item.Properties(), com.sp.destruction.DestructionType.NUKE));
    public static final Supplier<Item> LASER_TRIGGER = REGISTRY.register("laser_trigger", () -> new DestructionTriggerItem(new Item.Properties(), com.sp.destruction.DestructionType.ORBITAL_LASER));
    public static final Supplier<Item> BLACK_HOLE_TRIGGER = REGISTRY.register("black_hole_trigger", () -> new DestructionTriggerItem(new Item.Properties(), com.sp.destruction.DestructionType.BLACK_HOLE));
    public static final Supplier<Item> PLANET_TRIGGER = REGISTRY.register("planet_trigger", () -> new DestructionTriggerItem(new Item.Properties(), com.sp.destruction.DestructionType.PLANET));
    public static final Supplier<Item> SUPERNOVA_TRIGGER = REGISTRY.register("supernova_trigger", () -> new DestructionTriggerItem(new Item.Properties(), com.sp.destruction.DestructionType.SUPERNOVA));

    public static void registerModItems() {}
}
