package com.sp.entity;

import com.sp.DestroyingMinecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(Registries.ENTITY_TYPE, DestroyingMinecraft.MOD_ID);
    public static final Supplier<EntityType<?>> SPINNING_BLOCK = REGISTRY.register("spinning_block", () -> EntityType.Builder.<net.minecraft.world.entity.Entity>of((t,l)->null, net.minecraft.world.entity.MobCategory.MISC).sized(1,1).build("spinning_block"));
    public static final Supplier<EntityType<?>> BLOCK_PHYSICS_ENTITY = REGISTRY.register("block_physics", () -> EntityType.Builder.<net.minecraft.world.entity.Entity>of((t,l)->null, net.minecraft.world.entity.MobCategory.MISC).sized(1,1).build("block_physics"));
    public static final Supplier<EntityType<?>> METEOR_ENTITY = REGISTRY.register("meteor", () -> EntityType.Builder.<net.minecraft.world.entity.Entity>of((t,l)->null, net.minecraft.world.entity.MobCategory.MISC).sized(1,1).build("meteor"));
    public static final Supplier<EntityType<?>> STAR_PIERCER_ENTITY = REGISTRY.register("star_piercer", () -> EntityType.Builder.<net.minecraft.world.entity.Entity>of((t,l)->null, net.minecraft.world.entity.MobCategory.MISC).sized(1,1).build("star_piercer"));
    public static void registerEntities() {}
}
