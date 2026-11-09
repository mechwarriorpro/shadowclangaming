package com.sp.block.entity;

import com.sp.DestroyingMinecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<?>> PHYSICS_DOOR_BE = REGISTRY.register("physics_door", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of((pos,state)->null, (net.minecraft.world.level.block.Block)null).build(null));
    public static final DeferredRegister<BlockEntityType<?>> REGISTRY = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DestroyingMinecraft.MOD_ID);
    public static void registerBlockEntities() {}
}
